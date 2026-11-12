package com.family.puzzlepop.newpuzzle

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.family.puzzlepop.R
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.math.sign

private val ThumbSize = 64.dp
private val TrackHeight = 64.dp
private val BubbleWidth = 72.dp
private val BubbleHeight = 38.dp

/** Unit steps around a circle, for stamping an even outline. */
private val OutlineDirections: List<Pair<Float, Float>> = List(12) { k ->
    val a = k * Math.PI / 6
    kotlin.math.cos(a).toFloat() to kotlin.math.sin(a).toFloat()
}

/**
 * The piece-count card: a big "154 pieces" label with the grid under it, then the slider between a
 * few-big-pieces button and a many-little-pieces button (each nudges one step). Every part has a fixed
 * height, so nothing moves while the slider is dragged.
 */
@Composable
internal fun PieceCountPicker(stepIndex: Int, onStepChange: (Int) -> Unit, pieces: Int, rows: Int, cols: Int) {
    val dark = isDarkSheet()
    val countText = stringResource(R.string.sheet_pieces_count, pieces)
    val stateText = stringResource(R.string.sheet_pieces_exact, pieces, rows, cols)
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth().height(64.dp),
        ) {
            Icon(painterResource(R.drawable.ic_pieces), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(40.dp))
            Column {
                Text(
                    countText,
                    fontSize = 30.sp,
                    lineHeight = 34.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = SheetColors.Custom.onSoft(dark),
                    maxLines = 1,
                )
                Text(
                    stringResource(R.string.sheet_grid, rows, cols),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        // Room for the count bubble that floats over the thumb while dragging.
        Spacer(Modifier.height(36.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            EndButton(R.drawable.ic_size_small, stringResource(R.string.sheet_fewer), SheetColors.Tiny, stepIndex > 0) {
                onStepChange((stepIndex - 1).coerceAtLeast(0))
            }
            PieceSlider(
                stepIndex = stepIndex,
                onStepChange = onStepChange,
                bubbleText = pieces.toString(),
                a11yLabel = stringResource(R.string.sheet_slider_a11y),
                stateText = stateText,
                modifier = Modifier.weight(1f).zIndex(1f),
            )
            EndButton(R.drawable.ic_size_big, stringResource(R.string.sheet_more), SheetColors.Expert, stepIndex < PieceSteps.lastIndex) {
                onStepChange((stepIndex + 1).coerceAtMost(PieceSteps.lastIndex))
            }
        }
    }
}

/**
 * A chunky slider over [PieceSteps]: a thick gradient track with a notch per step, and a puzzle-piece
 * thumb that wiggles as it moves. Pressing anywhere on the track jumps there; dragging slides, snapping
 * to steps. A bubble over the thumb shows the count while dragging. TalkBack sees a stepped slider.
 */
@Composable
private fun PieceSlider(
    stepIndex: Int,
    onStepChange: (Int) -> Unit,
    bubbleText: String,
    a11yLabel: String,
    stateText: String,
    modifier: Modifier = Modifier,
) {
    val current by rememberUpdatedState(stepIndex)
    val change by rememberUpdatedState(onStepChange)
    var dragging by remember { mutableStateOf(false) }
    val fraction = animateFloatAsState(
        PieceSteps.fraction(stepIndex),
        spring(dampingRatio = 0.72f, stiffness = 900f),
        label = "thumb",
    )
    val thumbScale = animateFloatAsState(if (dragging) 1.16f else 1f, spring(dampingRatio = 0.45f, stiffness = 600f), label = "lift")
    val bubble = animateFloatAsState(if (dragging) 1f else 0f, spring(dampingRatio = 0.6f, stiffness = 700f), label = "bubble")
    val last = PieceSteps.lastIndex
    // Tip the piece toward each new step, then let it wobble back upright. At the top step its mind is blown:
    // the burst pops out with a spring while the piece shakes.
    val tilt = remember { Animatable(0f) }
    val blast = remember { Animatable(if (stepIndex == last) 1f else 0f) }
    val lastStep = remember { IntArray(1) { stepIndex } }
    LaunchedEffect(stepIndex) {
        val dir = sign((stepIndex - lastStep[0]).toFloat())
        lastStep[0] = stepIndex
        if (dir == 0f) return@LaunchedEffect
        if (stepIndex == last) {
            launch {
                blast.snapTo(0f)
                blast.animateTo(1f, spring(dampingRatio = 0.3f, stiffness = 320f))
            }
            for (a in floatArrayOf(-12f, 11f, -9f, 7f, -4f)) tilt.animateTo(a, tween(55))
            tilt.animateTo(0f, spring(dampingRatio = 0.4f, stiffness = 400f))
        } else {
            if (blast.value != 0f) launch { blast.animateTo(0f, tween(160)) }
            tilt.animateTo(dir * 16f, spring(stiffness = 1400f))
            tilt.animateTo(0f, spring(dampingRatio = 0.28f, stiffness = 260f))
        }
    }
    val face = rememberThumbFace()
    val density = LocalDensity.current
    val thumbPx = with(density) { ThumbSize.toPx() }
    BoxWithConstraints(
        modifier
            .height(TrackHeight)
            .clearAndSetSemantics {
                contentDescription = a11yLabel
                stateDescription = stateText
                progressBarRangeInfo = ProgressBarRangeInfo(stepIndex.toFloat(), 0f..last.toFloat(), steps = last - 1)
                setProgress { target ->
                    val i = target.roundToInt().coerceIn(0, last)
                    if (i != current) change(i)
                    true
                }
            }
            .pointerInput(Unit) {
                val half = ThumbSize.toPx() / 2
                fun moveTo(x: Float) {
                    val span = (size.width - 2 * half).coerceAtLeast(1f)
                    val i = PieceSteps.indexAt((x - half) / span)
                    if (i != current) change(i)
                }
                awaitEachGesture {
                    // Take the whole gesture, so the sheet never drags or scrolls under the finger.
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    dragging = true
                    try {
                        moveTo(down.position.x)
                        while (true) {
                            val event = awaitPointerEvent()
                            val c = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!c.pressed) {
                                c.consume()
                                break
                            }
                            if (c.positionChange() != Offset.Zero) {
                                moveTo(c.position.x)
                                c.consume()
                            }
                        }
                    } finally {
                        dragging = false
                    }
                }
            },
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        val half = thumbPx / 2
        val span = (widthPx - thumbPx).coerceAtLeast(1f)
        // Track and notches; only redrawn (not recomposed) as the thumb moves.
        Spacer(
            Modifier.matchParentSize().drawWithCache {
                val trackH = 20.dp.toPx()
                val top = size.height / 2 - trackH / 2
                val left = half - trackH / 2
                val brush = Brush.horizontalGradient(SheetColors.Track, startX = left, endX = size.width - left)
                val corner = CornerRadius(trackH / 2)
                val notch = 2.2.dp.toPx()
                onDrawBehind {
                    val x = half + fraction.value * span
                    drawRoundRect(brush, Offset(left, top), Size(size.width - 2 * left, trackH), corner, alpha = 0.34f)
                    drawRoundRect(brush, Offset(left, top), Size(x - left + trackH / 2, trackH), corner)
                    for (i in 0..last) {
                        val nx = half + span * i / last
                        drawCircle(Color.White, notch, Offset(nx, size.height / 2), alpha = if (nx <= x) 0.75f else 0.9f)
                    }
                }
            },
        )
        // The thumb: the mascot's body with an even white "sticker" outline and a live face. The outline is the
        // same body, at the same size, stamped a few dp out all round, so it hugs the shape exactly (a scaled-up
        // copy would sit off-centre, because the piece's tabs make it lopsided inside its viewport).
        val body = painterResource(R.drawable.ic_puzzle_piece_body)
        val white = remember { ColorFilter.tint(Color.White) }
        Spacer(
            Modifier
                .offset { IntOffset((fraction.value * span).roundToInt(), ((heightPx - thumbPx) / 2).roundToInt()) }
                .size(ThumbSize)
                .graphicsLayer {
                    rotationZ = tilt.value
                    scaleX = thumbScale.value
                    scaleY = thumbScale.value
                }
                .drawBehind {
                    val ring = 2.5.dp.toPx()
                    inset(ring) {
                        for ((dx, dy) in OutlineDirections) {
                            translate(dx * ring, dy * ring) { with(body) { draw(size, colorFilter = white) } }
                        }
                        with(body) { draw(size) }
                        with(face) { draw(shock = fraction.value, blast = blast.value) }
                    }
                },
        )
        // The count bubble, floating above the thumb.
        val bubbleW = with(density) { BubbleWidth.toPx() }
        val bubbleH = with(density) { BubbleHeight.toPx() }
        Box(
            Modifier
                .offset { IntOffset((half + fraction.value * span - bubbleW / 2).roundToInt(), (-bubbleH - 2.dp.toPx() - 16.dp.toPx() * blast.value.coerceIn(0f, 1f)).roundToInt()) }
                .size(BubbleWidth, BubbleHeight)
                .graphicsLayer {
                    val b = bubble.value
                    alpha = b.coerceIn(0f, 1f)
                    scaleX = 0.5f + 0.5f * b
                    scaleY = 0.5f + 0.5f * b
                    transformOrigin = TransformOrigin(0.5f, 1f)
                }
                .drawWithCache {
                    val r = 16.dp.toPx()
                    val tipH = 8.dp.toPx()
                    val bodyH = size.height - tipH
                    val tip = Path().apply {
                        moveTo(size.width / 2 - 8.dp.toPx(), bodyH - 1f)
                        lineTo(size.width / 2, size.height)
                        lineTo(size.width / 2 + 8.dp.toPx(), bodyH - 1f)
                        close()
                    }
                    onDrawBehind {
                        drawRoundRect(SheetColors.Custom.deep, size = Size(size.width, bodyH), cornerRadius = CornerRadius(r))
                        drawPath(tip, SheetColors.Custom.deep)
                    }
                },
        ) {
            Text(
                bubbleText,
                color = Color.White,
                fontSize = 18.sp,
                lineHeight = 21.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 4.dp),
            )
        }
    }
}

/** A round button at an end of the track showing what that end means; a tap nudges one step. */
@Composable
private fun EndButton(@DrawableRes icon: Int, label: String, tint: Tint, enabled: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale = animateFloatAsState(if (pressed) 0.84f else 1f, spring(dampingRatio = 0.4f, stiffness = 700f), label = "end")
    val faded = !enabled
    Box(
        Modifier
            .size(52.dp)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                alpha = if (faded) 0.45f else 1f
            }
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .border(2.5.dp, tint.fill.copy(alpha = 0.45f), CircleShape)
            .clickable(interaction, null, enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(32.dp))
    }
}

@Composable
private fun rememberThumbFace(): ThumbFace {
    val colors = FaceColors(
        ink = colorResource(R.color.icon_ink),
        blush = colorResource(R.color.icon_blush),
        coral = colorResource(R.color.icon_coral),
        sun = colorResource(R.color.icon_sun),
        sky = colorResource(R.color.icon_sky),
        skyDark = colorResource(R.color.icon_sky_dark),
        leaf = colorResource(R.color.icon_leaf),
        grape = colorResource(R.color.icon_grape),
        grapeLight = colorResource(R.color.icon_grape_light),
    )
    return remember(colors.ink, colors.skyDark) { ThumbFace(colors) }
}
