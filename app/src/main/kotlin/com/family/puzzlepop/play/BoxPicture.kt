package com.family.puzzlepop.play

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.family.puzzlepop.R
import kotlin.math.abs
import kotlin.math.roundToInt

private const val CARD_FRACTION = 0.18f
private const val ENLARGED_FRACTION = 0.60f

/**
 * The picture on the puzzle box: a small card that shows the whole photo, like a propped-up box lid. Tap to
 * see it big. It sits above the canvas and consumes its own touches.
 *
 * With [free] (grown-up mode) it stays exactly where it is dragged, over the board or anywhere, only kept fully
 * on screen; it never moves by itself. Otherwise (kid mode) it takes the first corner clear of the [avoid]
 * rects (board, tray) and is pushed out of them.
 *
 * @param pos top-left as 0..1 of the screen area it can move in; null (or negative) = the default corner.
 * @param onHide null = no hide button (kid mode).
 * @param avoid screen-px rects (relative to this composable) the card must stay out of; ignored when [free].
 */
@Composable
fun BoxPicture(
    image: ImageBitmap,
    pos: Offset?,
    movable: Boolean,
    onMove: (Offset) -> Unit,
    onHide: (() -> Unit)?,
    avoid: List<Rect>,
    free: Boolean = false,
) {
    val density = LocalDensity.current
    var enlarged by remember { mutableStateOf(false) }
    var dragTopLeft by remember { mutableStateOf<Offset?>(null) }
    val avoidNow by rememberUpdatedState(avoid)
    val onMoveNow by rememberUpdatedState(onMove)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val short = minOf(w, h)
        val aspect = image.width.toFloat() / image.height.toFloat()
        val margin = with(density) { 8.dp.toPx() }
        val frame = with(density) { 4.dp.toPx() }
        val card = boxCardSize(w, h, aspect, density.density)
        val cardW = card.width
        val cardH = card.height
        val bounds = Rect(0f, 0f, maxOf(0f, w - cardW), maxOf(0f, h - cardH))
        val keepOut = if (free) emptyList() else avoid.map { it.inflate(margin / 2) }

        fun rectAt(tl: Offset) = Rect(tl.x, tl.y, tl.x + cardW, tl.y + cardH)
        fun clampIn(tl: Offset) = Offset(tl.x.coerceIn(bounds.left, bounds.right), tl.y.coerceIn(bounds.top, bounds.bottom))
        fun resolved(start: Offset): Offset = resolveOutOfRects(clampIn(start), cardW, cardH, bounds, keepOut)

        val base: Offset = if (pos != null && pos.x >= 0f && pos.y >= 0f) {
            Offset(pos.x * bounds.right, pos.y * bounds.bottom)
        } else if (free) {
            clampIn(Offset(margin, margin)) // the default corner, as in kid mode
        } else {
            val corners = listOf(
                Offset(margin, margin),
                Offset(bounds.right - margin, margin),
                Offset(margin, bounds.bottom - margin),
                Offset(bounds.right - margin, bounds.bottom - margin),
            ).map { clampIn(it) }
            corners.firstOrNull { c -> keepOut.none { it.overlaps(rectAt(c)) } } ?: corners.first()
        }
        val shown = resolved(dragTopLeft ?: base)
        // The drag handler below is keyed only on `movable`, so it reads the live geometry through this.
        val geo = rememberUpdatedState(BoxGeo(shown, bounds, cardW, cardH, keepOut))

        val colors = MaterialTheme.colorScheme
        val desc = stringResource(R.string.cd_box_picture)
        Box(
            Modifier
                .offset { IntOffset(shown.x.roundToInt(), shown.y.roundToInt()) }
                .size(with(density) { cardW.toDp() }, with(density) { cardH.toDp() })
                .shadow(8.dp, RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
                .background(colors.primaryContainer)
                .semantics { contentDescription = desc }
                .pointerInput(Unit) { detectTapGestures(onTap = { enlarged = true }) }
                .pointerInput(movable) {
                    if (movable) {
                        detectDragGestures(
                            onDragStart = { dragTopLeft = geo.value.shown },
                            onDragEnd = {
                                val g = geo.value
                                val end = dragTopLeft?.let {
                                    val inside = Offset(it.x.coerceIn(g.bounds.left, g.bounds.right), it.y.coerceIn(g.bounds.top, g.bounds.bottom))
                                    resolveOutOfRects(inside, g.w, g.h, g.bounds, g.keepOut)
                                }
                                dragTopLeft = null
                                if (end != null) {
                                    onMoveNow(Offset(
                                        if (g.bounds.right > 0f) end.x / g.bounds.right else 0f,
                                        if (g.bounds.bottom > 0f) end.y / g.bounds.bottom else 0f,
                                    ))
                                }
                            },
                            onDragCancel = { dragTopLeft = null },
                        ) { change, drag ->
                            change.consume()
                            val g = geo.value
                            val next = (dragTopLeft ?: g.shown) + drag
                            dragTopLeft = Offset(next.x.coerceIn(g.bounds.left, g.bounds.right), next.y.coerceIn(g.bounds.top, g.bounds.bottom))
                        }
                    }
                }
                .padding(with(density) { frame.toDp() }),
        ) {
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                filterQuality = FilterQuality.Medium,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
            )
            if (onHide != null) {
                val hideDesc = stringResource(R.string.cd_box_hide)
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.85f))
                        .semantics { contentDescription = hideDesc }
                        .clickable { onHide() },
                    contentAlignment = Alignment.Center,
                ) {
                    val ink = colors.onPrimaryContainer
                    Canvas(Modifier.size(12.dp)) {
                        val sw = 2.5.dp.toPx()
                        drawLine(ink, Offset(0f, 0f), Offset(size.width, size.height), sw, StrokeCap.Round)
                        drawLine(ink, Offset(size.width, 0f), Offset(0f, size.height), sw, StrokeCap.Round)
                    }
                }
            }
        }

        if (enlarged) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { enlarged = false },
                contentAlignment = Alignment.Center,
            ) {
                val maxW = ENLARGED_FRACTION * w
                val maxH = ENLARGED_FRACTION * h
                val fit = minOf(maxW / image.width, maxH / image.height)
                val bw = image.width * fit
                val bh = image.height * fit
                Box(
                    Modifier
                        .size(with(density) { (bw + 2 * frame).toDp() }, with(density) { (bh + 2 * frame).toDp() })
                        .shadow(16.dp, RoundedCornerShape(16.dp))
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.primaryContainer)
                        .padding(with(density) { frame.toDp() })
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { enlarged = false },
                ) {
                    Image(
                        bitmap = image,
                        contentDescription = desc,
                        contentScale = ContentScale.Fit,
                        filterQuality = FilterQuality.Medium,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
                    )
                }
            }
        }
    }
}

/** The box card's outer size (picture plus frame) in px for a w x h view: 18 % of the short side, 100 dp floor. */
internal fun boxCardSize(w: Float, h: Float, aspect: Float, density: Float): Size {
    val longSide = maxOf(CARD_FRACTION * minOf(w, h), 100f * density)
    val cw = if (aspect >= 1f) longSide else longSide * aspect
    val ch = if (aspect >= 1f) longSide / aspect else longSide
    val frame = 4f * density
    return Size(cw + 2 * frame, ch + 2 * frame)
}

/** The default (top-left) card rect: where the card sits in kid mode and when never moved. */
internal fun boxCardDefaultRect(w: Float, h: Float, aspect: Float, density: Float): Rect {
    val s = boxCardSize(w, h, aspect, density)
    val m = 8f * density
    return Rect(m, m, m + s.width, m + s.height)
}

/**
 * The largest sub-rectangle of [area] that does not touch [card] (cut as a full-height or full-width band),
 * so a camera fitted into it never puts table under the card. [card] null = the whole area.
 */
internal fun tableRectExcluding(area: Rect, card: Rect?, gap: Float): Rect {
    if (card == null || !card.overlaps(area)) return area
    val c = card.inflate(gap)
    val cands = listOf(
        Rect(maxOf(area.left, c.right), area.top, area.right, area.bottom),
        Rect(area.left, area.top, minOf(area.right, c.left), area.bottom),
        Rect(area.left, maxOf(area.top, c.bottom), area.right, area.bottom),
        Rect(area.left, area.top, area.right, minOf(area.bottom, c.top)),
    ).filter { it.width > 0f && it.height > 0f }
    return cands.maxByOrNull { it.width * it.height } ?: area
}

private class BoxGeo(val shown: Offset, val bounds: Rect, val w: Float, val h: Float, val keepOut: List<Rect>)

private fun Rect.inflate(d: Float) = Rect(left - d, top - d, right + d, bottom + d)

/** Moves a w x h card whose top-left is [start] the shortest way out of every rect, staying inside [bounds]. */
internal fun resolveOutOfRects(start: Offset, w: Float, h: Float, bounds: Rect, keepOut: List<Rect>): Offset {
    var p = start
    repeat(keepOut.size + 2) {
        val card = Rect(p.x, p.y, p.x + w, p.y + h)
        val hit = keepOut.firstOrNull { it.overlaps(card) } ?: return p
        val options = listOf(
            Offset(hit.left - w, p.y),
            Offset(hit.right, p.y),
            Offset(p.x, hit.top - h),
            Offset(p.x, hit.bottom),
        ).filter { it.x >= bounds.left - 0.5f && it.x <= bounds.right + 0.5f && it.y >= bounds.top - 0.5f && it.y <= bounds.bottom + 0.5f }
        val free = options.filter { o -> keepOut.none { it.overlaps(Rect(o.x, o.y, o.x + w, o.y + h)) } }
        val best = (free.ifEmpty { options }).minByOrNull { abs(it.x - p.x) + abs(it.y - p.y) } ?: return p
        p = best
    }
    return p
}
