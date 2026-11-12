package com.family.puzzlepop.newpuzzle

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.cos
import kotlin.math.sin

/** The icon palette (res/values/icon_colors.xml) as Compose colours, for the face drawn on the slider thumb. */
internal class FaceColors(
    val ink: Color,
    val blush: Color,
    val coral: Color,
    val sun: Color,
    val sky: Color,
    val skyDark: Color,
    val leaf: Color,
    val grape: Color,
    val grapeLight: Color,
)

private fun smooth(a: Float, b: Float, x: Float): Float {
    val t = ((x - a) / (b - a)).coerceIn(0f, 1f)
    return t * t * (3 - 2 * t)
}

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

/**
 * Draws the mascot's face on the face-less body (ic_puzzle_piece_body), in that drawable's 24-unit
 * viewport scaled to this DrawScope. [shock] runs 0..1 with the slider: a calm smile at few pieces,
 * round eyes and a little "o" in the middle, huge eyes, raised brows, a big "O" and a sweat drop near the
 * top. [blast] (0..1, may overshoot) is the top step's "mind blown": the head bursts into a puff of
 * colour with sparks, and the pupils shrink to stunned dots.
 *
 * Reuses one [Path] for the smile; called every frame while the thumb moves.
 */
internal class ThumbFace(private val c: FaceColors) {
    private val smile = Path()
    private val drop = Path()
    private val sparkColors = arrayOf(c.coral, c.sun, c.sky, c.leaf, c.grape, c.sun, c.coral)

    fun DrawScope.draw(shock: Float, blast: Float) {
        val u = size.width / 24f
        fun o(x: Float, y: Float) = Offset(x * u, y * u)
        val s = shock.coerceIn(0f, 1f)
        val wide = smooth(0.12f, 0.9f, s)

        // Blush fades a little as the shock grows.
        val blushAlpha = 0.8f * (1f - 0.6f * smooth(0.4f, 1f, s))
        drawCircle(c.blush, 1.15f * u, o(7.6f - 0.3f * wide, 15.6f), alpha = blushAlpha)
        drawCircle(c.blush, 1.15f * u, o(14.8f + 0.3f * wide, 15.6f), alpha = blushAlpha)

        // Eyes: dots that grow whites and drift apart and up.
        val eyeY = 12.8f - 0.7f * wide
        val whites = 2.0f * smooth(0.05f, 0.75f, s)
        val pupil = (0.85f + 0.2f * s) * (1f - 0.45f * blast.coerceIn(0f, 1f))
        val leftX = 8.9f - 0.5f * wide
        val rightX = 13.5f + 0.5f * wide
        val ring = Stroke(0.55f * u)
        for (i in 0..1) {
            val ex = if (i == 0) leftX else rightX
            if (whites > 0.1f) {
                drawCircle(Color.White, whites * u, o(ex, eyeY))
                drawCircle(c.ink, whites * u, o(ex, eyeY), style = ring)
            }
            drawCircle(c.ink, pupil * u, o(ex, eyeY))
        }

        // Brows shoot up near the top.
        val brows = smooth(0.55f, 0.85f, s)
        if (brows > 0f) {
            val by = eyeY - whites - 1.0f - 0.6f * brows
            drawLine(c.ink, o(leftX - 1.5f, by + 0.5f), o(leftX + 1.2f, by - 0.2f), 1.0f * u, StrokeCap.Round, alpha = brows)
            drawLine(c.ink, o(rightX - 1.2f, by - 0.2f), o(rightX + 1.5f, by + 0.5f), 1.0f * u, StrokeCap.Round, alpha = brows)
        }

        // Mouth: the smile fades out as an "o" opens and grows into a big "O".
        val smileAlpha = 1f - smooth(0.08f, 0.28f, s)
        if (smileAlpha > 0f) {
            smile.reset()
            smile.moveTo(9.2f * u, 15.2f * u)
            smile.quadraticTo(11.2f * u, 17.4f * u, 13.2f * u, 15.2f * u)
            drawPath(smile, c.ink, alpha = smileAlpha, style = Stroke(1.2f * u, cap = StrokeCap.Round))
        }
        val open = smooth(0.15f, 0.35f, s)
        if (open > 0f) {
            val mw = lerp(1.3f, 3.0f, wide) * open
            val mh = lerp(1.6f, 3.8f, wide) * open
            val my = 16.2f + 0.3f * wide
            drawOval(c.ink, o(11.2f - mw / 2, my - mh / 2), Size(mw * u, mh * u))
            if (wide > 0.4f) {
                val tw = mw * 0.55f
                val th = mh * 0.3f
                drawOval(c.blush, o(11.2f - tw / 2, my + mh / 2 - th - 0.25f), Size(tw * u, th * u), alpha = smooth(0.4f, 0.8f, wide))
            }
        }

        // A sweat drop at the very top of the range.
        val sweat = smooth(0.62f, 0.8f, s) * (1f - smooth(0f, 0.6f, blast))
        if (sweat > 0f) {
            drop.reset()
            drop.moveTo(17.0f * u, 7.4f * u)
            drop.quadraticTo(18.5f * u, 9.4f * u, 18.1f * u, 10.3f * u)
            drop.quadraticTo(17.0f * u, 11.9f * u, 15.9f * u, 10.3f * u)
            drop.quadraticTo(15.5f * u, 9.4f * u, 17.0f * u, 7.4f * u)
            drop.close()
            drawPath(drop, c.sky, alpha = sweat)
            drawPath(drop, c.skyDark, alpha = sweat, style = Stroke(0.45f * u))
        }

        if (blast > 0.01f) drawBlast(u, blast)
    }

    /** Mind blown: sparks fanning up out of the head, then a puff cloud where the top tab was. */
    private fun DrawScope.drawBlast(u: Float, blast: Float) {
        val cx = 10.4f
        val cy = 3.2f
        for (i in 0..6) {
            val a = Math.toRadians(-160.0 + i * (140.0 / 6)).toFloat()
            val r0 = 5.2f * blast
            val r1 = 8.6f * blast
            drawLine(
                sparkColors[i],
                Offset((cx + cos(a) * r0) * u, (cy + sin(a) * r0) * u),
                Offset((cx + cos(a) * r1) * u, (cy + sin(a) * r1) * u),
                1.3f * u,
                StrokeCap.Round,
            )
        }
        val b = blast
        fun puff(dx: Float, dy: Float, r: Float, color: Color) =
            drawCircle(color, r * b * u, Offset((cx + dx * b) * u, (cy + dy * b) * u))
        // A white rim first, so the puffs read as one soft cloud.
        puff(-3.4f, 1.6f, 2.9f, Color.White)
        puff(3.4f, 1.6f, 2.9f, Color.White)
        puff(-1.8f, -1.3f, 3.1f, Color.White)
        puff(1.9f, -1.1f, 3.0f, Color.White)
        puff(-3.4f, 1.6f, 2.3f, c.coral)
        puff(3.4f, 1.6f, 2.3f, c.sky)
        puff(-1.8f, -1.3f, 2.5f, c.sun)
        puff(1.9f, -1.1f, 2.4f, c.leaf)
        puff(0f, 1.2f, 2.8f, c.grapeLight)
        // Little shine dots so the cloud reads as soft puffs.
        drawCircle(Color.White, 0.6f * b * u, Offset((cx - 2.4f * b) * u, (cy - 2.2f * b) * u), alpha = 0.85f)
        drawCircle(Color.White, 0.5f * b * u, Offset((cx + 0.9f * b) * u, (cy + 0.3f * b) * u), alpha = 0.85f)
    }
}
