package com.family.puzzlepop.play

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.sin
import kotlin.random.Random

private val CONFETTI_COLORS = arrayOf(
    Color(0xFFFF5A5F), Color(0xFFFFB400), Color(0xFF00A699), Color(0xFF7B61FF),
    Color(0xFF3DA5FF), Color(0xFFFF7EB6), Color(0xFF8BD346),
)

/**
 * A burst of falling confetti that plays once for [durationMs] and then draws nothing. Particle state is
 * preallocated; each frame only bumps a clock read inside the draw lambda (no recomposition).
 */
@Composable
fun Confetti(modifier: Modifier = Modifier, durationMs: Long = 3000, count: Int = 180) {
    val density = LocalDensity.current.density
    val p = remember(count) { Particles(count, Random(System.nanoTime())) }
    val elapsedNanos = remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        var start = -1L
        while (true) {
            val now = withFrameNanos { it }
            if (start < 0) start = now
            elapsedNanos.longValue = now - start
            if (now - start > durationMs * 1_000_000) break
        }
    }
    Canvas(modifier) {
        val tNanos = elapsedNanos.longValue
        val t = tNanos / 1e9f
        if (tNanos > durationMs * 1_000_000) return@Canvas
        // Fade out over the last half second.
        val alpha = ((durationMs / 1000f - t) / 0.5f).coerceIn(0f, 1f)
        val w = size.width
        val h = size.height
        val pieceW = 10f * density
        val pieceH = 16f * density
        for (i in 0 until p.n) {
            val x = p.x[i] * w + p.vx[i] * density * t + sin(t * p.wobble[i] + p.phase[i]) * 18f * density
            // Starts above the screen (staggered so the shower lasts) and drifts down at its own speed.
            val y = -p.startY[i] * h + p.vy[i] * t * density
            if (y > h + pieceH || y < -pieceH * 2) continue
            rotate(p.spin[i] * t + p.phase[i] * 57f, Offset(x, y)) {
                drawRect(
                    color = CONFETTI_COLORS[p.color[i]],
                    topLeft = Offset(x - pieceW / 2, y - pieceH / 2),
                    size = Size(pieceW, pieceH * (0.5f + 0.5f * sin(t * p.wobble[i] * 2f + p.phase[i]))),
                    alpha = alpha,
                )
            }
        }
    }
}

private class Particles(val n: Int, rng: Random) {
    val x = FloatArray(n) { rng.nextFloat() }
    val startY = FloatArray(n) { rng.nextFloat() * 1.05f + 0.02f }
    val vx = FloatArray(n) { (rng.nextFloat() - 0.5f) * 120f }
    val vy = FloatArray(n) { 220f + rng.nextFloat() * 300f }
    val spin = FloatArray(n) { (rng.nextFloat() - 0.5f) * 720f }
    val wobble = FloatArray(n) { 2f + rng.nextFloat() * 4f }
    val phase = FloatArray(n) { rng.nextFloat() * 6.28f }
    val color = IntArray(n) { rng.nextInt(CONFETTI_COLORS.size) }
}
