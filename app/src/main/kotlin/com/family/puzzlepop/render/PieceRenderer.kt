package com.family.puzzlepop.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.family.puzzlepop.engine.PieceShape
import com.family.puzzlepop.engine.PuzzleLayout
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive

/**
 * All pieces of a puzzle as small bitmaps. Bitmap `i` covers `layout.pieces[i].bounds` at [scale]
 * (its top-left is the piece's home position times [scale]); [ghost] is the whole photo at [scale].
 */
class RenderedPuzzle(
    val scale: Float,
    val pieces: List<ImageBitmap>,
    val ghost: ImageBitmap,
    /** Per piece: the [ColorGroup] ordinal of its average opaque-pixel colour. */
    val colorGroup: IntArray,
    /** Per piece: that average colour's hue (0..360), to sort by colour. */
    val hue: FloatArray,
    /** Total time workers spent sampling colours (ms), for the log. */
    val colorMillis: Long = 0,
)

/** Scale (<= 1) at which piece bitmaps plus the ghost fit in [maxBytes] of ARGB_8888 pixels. */
internal fun renderScale(layout: PuzzleLayout, maxBytes: Long): Float {
    var boundsArea = 0.0
    for (p in layout.pieces) boundsArea += p.bounds.width.toDouble() * p.bounds.height
    val imageArea = layout.spec.imageWidth.toDouble() * layout.spec.imageHeight
    val bytesAtFullScale = 4.0 * boundsArea + 4.0 * imageArea
    if (bytesAtFullScale <= 0.0) return 1f
    return min(1.0, sqrt(maxBytes / bytesAtFullScale)).toFloat()
}

/**
 * Renders every piece of [layout] out of [photo]. The photo is stretched to the layout's image size
 * (normally identical). [onProgress] is called with 0..1 roughly every 5%, from worker threads.
 */
suspend fun renderPuzzle(
    photo: Bitmap,
    layout: PuzzleLayout,
    maxBytes: Long = 128L * 1024 * 1024,
    onProgress: (Float) -> Unit = {},
): RenderedPuzzle = coroutineScope {
    val scale = renderScale(layout, maxBytes)
    val total = layout.pieces.size
    val workers = Runtime.getRuntime().availableProcessors().coerceIn(1, 6)
    val done = AtomicInteger(0)
    val lastStep = AtomicInteger(0)
    val results = arrayOfNulls<Bitmap>(total)
    val groups = IntArray(total)
    val hues = FloatArray(total)
    val colorNanos = AtomicLong(0)
    val imgW = layout.spec.imageWidth.toFloat()
    val imgH = layout.spec.imageHeight.toFloat()

    val jobs = (0 until workers).map { w ->
        async(Dispatchers.Default) {
            val ctx = PieceDrawer(photo, imgW, imgH, scale, min(layout.cellWidth, layout.cellHeight))
            var i = w
            while (i < total) {
                ensureActive()
                val bmp = ctx.draw(layout.pieces[i])
                val t0 = System.nanoTime()
                sampleColor(bmp, i, groups, hues)
                colorNanos.addAndGet(System.nanoTime() - t0)
                results[i] = bmp.frozen()
                val n = done.incrementAndGet()
                val step = n * 20 / total // 5% steps
                var prev = lastStep.get()
                while (step > prev) {
                    if (lastStep.compareAndSet(prev, step)) {
                        onProgress(step / 20f)
                        break
                    }
                    prev = lastStep.get()
                }
                i += workers
            }
        }
    }
    val ghostJob = async(Dispatchers.Default) {
        val gw = max(1, ceil(imgW * scale).toInt())
        val gh = max(1, ceil(imgH * scale).toInt())
        // Always a fresh bitmap, so the caller can recycle [photo] afterwards.
        Bitmap.createScaledBitmap(photo, gw, gh, true).let { if (it === photo) photo.copy(Bitmap.Config.ARGB_8888, false) else it.frozen() }
    }
    jobs.awaitAll()
    val ghost = ghostJob.await()
    RenderedPuzzle(scale, results.map { it!!.asImageBitmap() }, ghost.asImageBitmap(), groups, hues, colorNanos.get() / 1_000_000)
}

/**
 * An immutable copy of a finished bitmap (this one is recycled). The renderer re-pins every *mutable* bitmap
 * of the view tree as a GPU texture on every frame; once ~1000 pieces from a big photo outgrow its texture
 * cache (Android 13, Galaxy Tab S7+), that re-uploads all of them each frame: ~90 ms frames. Immutable ones
 * are uploaded only when a layer that draws them is re-rendered.
 */
private fun Bitmap.frozen(): Bitmap {
    if (!isMutable) return this
    val copy = copy(config ?: Bitmap.Config.ARGB_8888, false)
    recycle()
    return copy
}

/**
 * Average colour of the opaque pixels (alpha > 128) on a coarse grid (about 10 x 10 samples) of [bmp],
 * stored as piece [i]'s colour group and hue. Each index is written by one worker only.
 */
private fun sampleColor(bmp: Bitmap, i: Int, groups: IntArray, hues: FloatArray) {
    val w = bmp.width
    val h = bmp.height
    val step = max(1, max(w, h) / 10)
    var r = 0L
    var g = 0L
    var b = 0L
    var n = 0
    var y = step / 2
    while (y < h) {
        var x = step / 2
        while (x < w) {
            val c = bmp.getPixel(x, y) // un-premultiplied
            if ((c ushr 24) > 128) {
                r += (c shr 16) and 0xFF
                g += (c shr 8) and 0xFF
                b += c and 0xFF
                n++
            }
            x += step
        }
        y += step
    }
    if (n == 0) {
        groups[i] = ColorGroup.DARK.ordinal
        hues[i] = 0f
        return
    }
    val ar = (r / n).toInt()
    val ag = (g / n).toInt()
    val ab = (b / n).toInt()
    groups[i] = ColorGroups.of(ar, ag, ab).ordinal
    hues[i] = ColorGroups.hue(ar, ag, ab)
}

/** Per-thread drawing state (Paints are not shared across threads). */
private class PieceDrawer(
    private val photo: Bitmap,
    private val imgW: Float,
    private val imgH: Float,
    private val scale: Float,
    cellMin: Float,
) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.BLACK }
    private val photoPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
    }

    // Rounded bevel, about 4% of the cell, 2..10 px at render scale. It is painted as several inset strokes
    // of the outline, widest and faintest first, so the effect fades from the edge inwards. Each pass
    // shifts the outline towards the light (light, top-left) or away from it (shade, bottom-right) by 0.7
    // of its half-width: the stroke then reaches ~1.7 half-widths into edges facing that way, and
    // almost nothing into edges facing the other way. SRC_ATOP paints only over pixels the piece already
    // covers and keeps its alpha, so edges stay anti-aliased and nothing bleeds outside the piece.
    private val bevel = (BEVEL_FRACTION * cellMin * scale).coerceIn(2f, 10f)
    private val passes = ceil(bevel / 1.5f).toInt().coerceIn(2, 5)
    private val halfWidth = FloatArray(passes) { bevel / 1.7f * (passes - it) / passes }
    private val light = Array(passes) { bevelPaint(halfWidth[it], 255, LIGHT_ALPHA) }
    private val shade = Array(passes) { bevelPaint(halfWidth[it], 0, SHADE_ALPHA) }
    private val seam = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f // centred on the outline: 1 px of it lands inside the piece
        strokeJoin = Paint.Join.ROUND
        color = Color.argb((SEAM_ALPHA * 255).toInt(), 0, 0, 0)
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
    }
    private val dst = RectF()

    private fun bevelPaint(half: Float, gray: Int, totalAlpha: Float): Paint {
        // N stacked passes of alpha a give 1 - (1 - a)^N = totalAlpha at the very edge.
        val a = 1f - Math.pow((1f - totalAlpha).toDouble(), 1.0 / passes).toFloat()
        return Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f * half
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
            color = Color.argb((a * 255).toInt(), gray, gray, gray)
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
        }
    }

    fun draw(piece: PieceShape): Bitmap {
        val b = piece.bounds
        val w = max(1, ceil(b.width * scale).toInt())
        val h = max(1, ceil(b.height * scale).toInt())
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val path = piece.outline.toAndroidPath(scale, b.left, b.top) // outline allocates: read once

        canvas.drawPath(path, fill)
        dst.set(-b.left * scale, -b.top * scale, (imgW - b.left) * scale, (imgH - b.top) * scale)
        canvas.drawBitmap(photo, null, dst, photoPaint)

        for (k in 0 until passes) {
            val s = 0.7f * halfWidth[k]
            canvas.save()
            canvas.translate(s, s)
            canvas.drawPath(path, light[k])
            canvas.restore()
            canvas.save()
            canvas.translate(-s, -s)
            canvas.drawPath(path, shade[k])
            canvas.restore()
        }
        canvas.drawPath(path, seam)
        return bmp
    }
}

private const val BEVEL_FRACTION = 0.04f
private const val LIGHT_ALPHA = 0.45f
private const val SHADE_ALPHA = 0.35f
private const val SEAM_ALPHA = 0.35f
