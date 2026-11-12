package com.family.puzzlepop.play

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Paint
import java.util.concurrent.atomic.AtomicReferenceArray
import kotlin.math.max
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/**
 * Soft shadow/glow masks of one piece, in world units: [near] (tight contact shadow, glow rim) and [far]
 * (wide lift shadow). Each mask is an ALPHA_8 bitmap blurred once; it covers more than its piece, so
 * `*OffX/Y` is where its top-left sits relative to the piece's top-left and `*W/H` its world size.
 * Immutable, so it can be handed from a background thread to the draw thread.
 */
internal class PieceMask(
    val near: Bitmap, val nearOffX: Float, val nearOffY: Float, val nearW: Float, val nearH: Float,
    val far: Bitmap, val farOffX: Float, val farOffY: Float, val farW: Float, val farH: Float,
)

/**
 * Cache of [PieceMask]s, one per piece. Masks are built from a [DOWNSCALE]x smaller copy of the piece's
 * alpha (blur cost and memory drop ~9x; the blur is soft anyway, and the mask is drawn scaled back up).
 *
 * They are all built on a background dispatcher right after the puzzle loads ([warmAll]). Any mask not
 * ready when the draw thread needs it is built on demand, within a small per-frame time budget
 * ([beginFrame]); past the budget [get] returns null and the caller falls back to something cheap.
 *
 * Publication: masks sit in an [AtomicReferenceArray] (volatile set/get) as immutable objects, so the
 * background builder and the draw thread never see a half-built mask. Both may build the same piece; the
 * result is identical, so the last write simply wins.
 */
internal class PieceMasks(
    private val bitmaps: Array<Bitmap>,
    private val renderScale: Float,
    cellMinWorld: Float,
) {
    private val nearRadius = max(1f, (NEAR_FRACTION * cellMinWorld * renderScale).coerceIn(1.5f, 12f) / DOWNSCALE)
    private val farRadius = max(1f, (FAR_FRACTION * cellMinWorld * renderScale).coerceIn(3f, 28f) / DOWNSCALE)
    private val cache = AtomicReferenceArray<PieceMask?>(bitmaps.size)
    private val uiBuilder = Builder()
    private var frameStart = 0L

    /** Call once per draw: starts the time budget for on-demand builds on the draw thread. */
    fun beginFrame() { frameStart = System.nanoTime() }

    /** The mask for [id], or null if it is not built yet and this frame's on-demand budget is spent. */
    fun get(id: Int): PieceMask? {
        cache.get(id)?.let { return it }
        if (System.nanoTime() - frameStart > ON_DEMAND_BUDGET_NANOS) return null
        return traced("Mask.buildOnDemand") { uiBuilder.build(id) }
    }

    /** Builds every missing mask; run it on a background dispatcher (it checks for cancellation per piece). */
    suspend fun warmAll() {
        val builder = Builder() // its own Paint: Paints are not thread-safe
        for (id in 0 until bitmaps.size) {
            coroutineContext.ensureActive()
            if (cache.get(id) == null) builder.build(id)
        }
    }

    private inner class Builder {
        private val paint = Paint()
        private val nearFilter = BlurMaskFilter(nearRadius, BlurMaskFilter.Blur.NORMAL)
        private val farFilter = BlurMaskFilter(farRadius, BlurMaskFilter.Blur.NORMAL)
        private val offset = IntArray(2)

        fun build(id: Int): PieceMask {
            val src = bitmaps[id]
            val small = Bitmap.createScaledBitmap(
                src, max(1, src.width / DOWNSCALE), max(1, src.height / DOWNSCALE), true,
            )
            paint.maskFilter = nearFilter
            val near = small.extractAlpha(paint, offset)
            val nox = offset[0] * UP / renderScale
            val noy = offset[1] * UP / renderScale
            paint.maskFilter = farFilter
            val far = small.extractAlpha(paint, offset)
            val fox = offset[0] * UP / renderScale
            val foy = offset[1] * UP / renderScale
            if (small !== src) small.recycle()
            val m = PieceMask(
                near, nox, noy, near.width * UP / renderScale, near.height * UP / renderScale,
                far, fox, foy, far.width * UP / renderScale, far.height * UP / renderScale,
            )
            cache.set(id, m)
            return m
        }
    }

    private companion object {
        const val DOWNSCALE = 3
        const val UP = DOWNSCALE.toFloat()
        const val NEAR_FRACTION = 0.03f
        const val FAR_FRACTION = 0.10f
        const val ON_DEMAND_BUDGET_NANOS = 2_000_000L
    }
}
