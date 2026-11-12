package com.family.puzzlepop.play

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

// Times (s) in finish.wav, mirrored from FANFARE / BIG_HITS / HORNS in tools/make_sounds.py.
/** Every trumpet note onset. */
private val FANFARE_NOTES_S = floatArrayOf(0.55f, 0.68f, 0.81f, 0.94f, 1.42f, 1.55f, 1.68f, 1.81f)
/** The long notes (start, end): the trumpets shake while they are held. */
private val FANFARE_HELD_S = floatArrayOf(0.94f, 1.38f, 1.81f, 2.76f)
/** The big hits (timpani; the last also has the cymbal): confetti cannons fire. */
private val FANFARE_BIG_S = floatArrayOf(0.94f, 1.81f)
/** Fireworks burst on the big hits, between them, and on the two party horns. */
private val FIREWORK_S = floatArrayOf(0.94f, 1.30f, 1.81f, 2.12f, 2.45f, 2.86f, 3.06f)
private const val SHOW_S = 4.2f

private val PARTY_COLORS = arrayOf(
    Color(0xFFFF5A5F), Color(0xFFFFB400), Color(0xFF00A699), Color(0xFF7B61FF),
    Color(0xFF3DA5FF), Color(0xFFFF7EB6), Color(0xFF8BD346),
)
private val GOLD = Color(0xFFFFC93C)
private val GOLD_SHADE = Color(0xFFE39B14)
private val GOLD_LIGHT = Color(0xFFFFF0A8)
private val SPARK_WHITE = Color(0xFFFFF8E1)

/**
 * The puzzle-finished party, played once in step with the fanfare in finish.wav: a confetti shower,
 * two trumpets that slide in and toot each note (musical notes float out of the bells), fireworks,
 * and confetti cannons from the bottom corners on the big notes. Draws nothing once it is over.
 */
@Composable
fun Celebration(modifier: Modifier = Modifier) {
    Box(modifier) {
        Confetti(Modifier.fillMaxSize(), durationMs = 4000, count = 200)
        FanfareShow(Modifier.fillMaxSize())
    }
}

@Composable
private fun FanfareShow(modifier: Modifier) {
    val density = LocalDensity.current.density
    val parts = remember { ShowParts(Random(System.nanoTime())) }
    val bell = remember { Path() }
    val flag = remember { Path() }
    val elapsedNanos = remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        var start = -1L
        while (true) {
            val now = withFrameNanos { it }
            if (start < 0) start = now
            elapsedNanos.longValue = now - start
            if (now - start > (SHOW_S * 1e9f).toLong()) break
        }
    }
    Canvas(modifier) {
        val t = elapsedNanos.longValue / 1e9f
        if (t > SHOW_S) return@Canvas
        fireworks(t, parts, density)
        cannons(t, parts, density)
        trumpets(t, parts, bell, flag)
    }
}

// ---- Fireworks --------------------------------------------------------------------------------------

private const val SPARKS_PER_BURST = 44
private const val ROCKET_S = 0.38f
private const val SPARK_LIFE_S = 1.25f

private fun DrawScope.fireworks(t: Float, p: ShowParts, density: Float) {
    val w = size.width
    val h = size.height
    val reach = min(w, h) * 0.2f
    for (b in FIREWORK_S.indices) {
        val burstAt = FIREWORK_S[b]
        val cx = p.burstX[b] * w
        val cy = p.burstY[b] * h
        val color = PARTY_COLORS[p.burstColor[b]]
        // The rocket climbing to its burst point.
        val r = (t - (burstAt - ROCKET_S)) / ROCKET_S
        if (r in 0f..1f) {
            val e = 1 - (1 - r) * (1 - r) * (1 - r)
            val y = h + 20f * density - (h + 20f * density - cy) * e
            drawLine(SPARK_WHITE, Offset(cx, y), Offset(cx, y + 36f * density * (1 - e * 0.6f)), 3f * density, StrokeCap.Round, alpha = 0.7f)
            drawCircle(SPARK_WHITE, 3.5f * density, Offset(cx, y))
        }
        val a = t - burstAt
        if (a < 0f || a > SPARK_LIFE_S) continue
        // A soft flash at the burst point.
        if (a < 0.22f) drawCircle(color, reach * 0.35f * (a / 0.22f), Offset(cx, cy), alpha = 0.35f * (1 - a / 0.22f))
        val life = a / SPARK_LIFE_S
        val fade = 1 - life * life
        for (k in 0 until SPARKS_PER_BURST) {
            val i = b * SPARKS_PER_BURST + k
            val dist = reach * p.sparkSpeed[i]
            val ang = p.sparkAngle[i]
            val x = cx + cos(ang) * dist * (1 - exp(-3.5f * a))
            val y = cy + sin(ang) * dist * (1 - exp(-3.5f * a)) + reach * 0.35f * a * a
            val a0 = max(0f, a - 0.09f)
            val x0 = cx + cos(ang) * dist * (1 - exp(-3.5f * a0))
            val y0 = cy + sin(ang) * dist * (1 - exp(-3.5f * a0)) + reach * 0.35f * a0 * a0
            val twinkle = if (a > 0.5f) 0.55f + 0.45f * sin(a * 32f + p.sparkPhase[i]) else 1f
            val c = if (p.sparkWhite[i]) SPARK_WHITE else color
            drawLine(c, Offset(x0, y0), Offset(x, y), 2.5f * density, StrokeCap.Round, alpha = 0.5f * fade)
            drawCircle(c, 3.5f * density * (1 - 0.5f * life), Offset(x, y), alpha = fade * twinkle)
        }
    }
}

// ---- Confetti cannons -------------------------------------------------------------------------------

private const val CANNON_BITS = 55
private const val CANNON_LIFE_S = 2.6f

private fun DrawScope.cannons(t: Float, p: ShowParts, density: Float) {
    val w = size.width
    val h = size.height
    val drag = 1.6f
    val gravity = 0.55f * h
    val bitW = 9f * density
    val bitH = 14f * density
    for (hit in FANFARE_BIG_S.indices) {
        val a = t - FANFARE_BIG_S[hit]
        if (a < 0f || a > CANNON_LIFE_S) continue
        val alpha = ((CANNON_LIFE_S - a) / 0.6f).coerceIn(0f, 1f)
        val slow = (1 - exp(-drag * a)) / drag
        for (side in 0..1) {
            for (k in 0 until CANNON_BITS) {
                val i = (hit * 2 + side) * CANNON_BITS + k
                val speed = p.bitSpeed[i] * h
                val vx = cos(p.bitAngle[i]) * speed
                val vy = sin(p.bitAngle[i]) * speed
                val dx = vx * slow + sin(a * 5f + p.bitPhase[i]) * 10f * density
                val x = if (side == 0) dx else w - dx
                val y = h + vy * slow + 0.5f * gravity * a * a
                if (y > h + bitH || x < -bitW || x > w + bitW) continue
                rotate(p.bitSpin[i] * a + p.bitPhase[i] * 57f, Offset(x, y)) {
                    drawRect(
                        PARTY_COLORS[p.bitColor[i]],
                        topLeft = Offset(x - bitW / 2, y - bitH / 2),
                        size = Size(bitW, bitH * (0.5f + 0.5f * sin(a * 9f + p.bitPhase[i]))),
                        alpha = alpha,
                    )
                }
            }
        }
    }
}

// ---- Trumpets and their musical notes ---------------------------------------------------------------

private const val TRUMPET_TILT_DEG = 28f
private const val SLIDE_IN_S = 0.5f
private const val SLIDE_OUT_START_S = 3.55f
private const val SLIDE_OUT_S = 0.5f
private const val NOTE_LIFE_S = 1.5f

private fun DrawScope.trumpets(t: Float, p: ShowParts, bell: Path, flag: Path) {
    val w = size.width
    val h = size.height
    val len = min(min(w, h) * 0.32f, w * 0.24f)
    // How far (0 = in place, 1 = fully off screen) the trumpets are slid out.
    val out = when {
        t < SLIDE_IN_S -> 1 - easeOutBack(t / SLIDE_IN_S)
        t > SLIDE_OUT_START_S -> ((t - SLIDE_OUT_START_S) / SLIDE_OUT_S).coerceIn(0f, 1f).let { it * it }
        else -> 0f
    }
    var toot = 0f
    for (n in FANFARE_NOTES_S) if (t >= n) toot = max(toot, exp(-(t - n) / 0.09f))
    var held = false
    for (k in FANFARE_HELD_S.indices step 2) if (t in FANFARE_HELD_S[k]..FANFARE_HELD_S[k + 1]) held = true
    val shake = if (held) 2.2f * sin(t * 42f) else 0f
    val tilt = TRUMPET_TILT_DEG + 5f * toot + shake
    val ax = w * 0.03f - out * len * 1.3f
    val ay = h * 0.62f
    for (side in 0..1) {
        withTransform({
            translate(if (side == 0) ax else w - ax, ay)
            rotate(if (side == 0) -tilt else tilt, pivot = Offset.Zero)
            scale(if (side == 0) 1f else -1f, 1f, pivot = Offset.Zero)
            translate(-0.04f * len * toot, 0f) // a little recoil on each toot
        }) {
            trumpet(len, flare = 1f + 0.2f * toot, bell = bell)
        }
    }
    // Notes fly out of the bells (spawned from the resting bell position).
    val rad = Math.toRadians(TRUMPET_TILT_DEG.toDouble()).toFloat()
    val dirX = cos(rad)
    val dirY = -sin(rad)
    val noteSize = len * 0.085f
    for (e in p.noteAt.indices) {
        val a = t - p.noteAt[e]
        if (a < 0f || a > NOTE_LIFE_S) continue
        val pop = (a / 0.12f).coerceIn(0f, 1f)
        val alpha = ((NOTE_LIFE_S - a) / 0.45f).coerceIn(0f, 1f)
        val travel = len * p.noteSpeed[e] * (1 - exp(-2.2f * a))
        val rise = len * 0.35f * a
        val wobble = sin(a * 6f + p.notePhase[e]) * len * 0.05f
        for (side in 0..1) {
            val sx = if (side == 0) 1f else -1f
            val bx = w * 0.03f + dirX * len * 1.02f
            val by = ay + dirY * len * 1.02f
            val x = (if (side == 0) bx else w - bx) + sx * (dirX * travel - dirY * wobble)
            val y = by + dirY * travel + dirX * wobble - rise
            musicNote(Offset(x, y), noteSize * pop, PARTY_COLORS[(p.noteColor[e] + side * 3) % PARTY_COLORS.size], alpha, sx * p.noteTilt[e], flag)
        }
    }
}

/** A cartoon trumpet, mouthpiece at the origin and the bell at x = [len]; [flare] puffs the bell out. */
private fun DrawScope.trumpet(len: Float, flare: Float, bell: Path) {
    fun o(x: Float, y: Float) = Offset(x * len, y * len)
    val pipe = 0.05f * len
    // The lower loop of tubing, then the lead pipe over it.
    drawRoundRect(GOLD_SHADE, topLeft = o(0.18f, 0f), size = Size(0.46f * len, 0.17f * len),
        cornerRadius = CornerRadius(0.085f * len), style = Stroke(width = 0.045f * len))
    drawLine(GOLD, o(0.04f, 0f), o(0.62f, 0f), pipe, StrokeCap.Round)
    drawLine(GOLD_LIGHT, o(0.08f, -0.012f), o(0.6f, -0.012f), 0.012f * len, StrokeCap.Round)
    // Mouthpiece.
    drawCircle(GOLD_SHADE, 0.04f * len, o(0f, 0f))
    drawCircle(GOLD, 0.028f * len, o(0f, 0f))
    // Three valves with their caps and buttons.
    for (vx in floatArrayOf(0.34f, 0.42f, 0.5f)) {
        drawRect(GOLD_SHADE, topLeft = o(vx - 0.026f, -0.11f), size = Size(0.052f * len, 0.3f * len))
        drawRect(GOLD, topLeft = o(vx - 0.016f, -0.11f), size = Size(0.02f * len, 0.3f * len))
        drawRoundRect(GOLD, topLeft = o(vx - 0.036f, -0.15f), size = Size(0.072f * len, 0.045f * len),
            cornerRadius = CornerRadius(0.015f * len))
        drawCircle(GOLD_LIGHT, 0.024f * len, o(vx, -0.17f))
    }
    // The bell.
    val r = 0.2f * flare
    bell.reset()
    bell.moveTo(0.58f * len, -0.028f * len)
    bell.cubicTo(0.8f * len, -0.03f * len, 0.92f * len, -0.07f * len, len, -r * len)
    bell.lineTo(len, r * len)
    bell.cubicTo(0.92f * len, 0.07f * len, 0.8f * len, 0.03f * len, 0.58f * len, 0.028f * len)
    bell.close()
    drawPath(bell, GOLD)
    drawLine(GOLD_LIGHT, o(0.66f, -0.036f), o(0.96f, -r * 0.72f), 0.014f * len, StrokeCap.Round)
    drawOval(GOLD_LIGHT, topLeft = o(1f - 0.035f, -r), size = Size(0.07f * len, 2 * r * len))
    drawOval(GOLD_SHADE, topLeft = o(1f - 0.018f, -r * 0.8f), size = Size(0.036f * len, 1.6f * r * len))
}

/** An eighth note whose head sits at [at]; [s] is the head's half-width-ish unit. */
private fun DrawScope.musicNote(at: Offset, s: Float, color: Color, alpha: Float, tiltDeg: Float, flag: Path) {
    if (s <= 0f || alpha <= 0f) return
    rotate(tiltDeg, at) {
        drawOval(color, topLeft = Offset(at.x - 0.6f * s, at.y - 0.42f * s), size = Size(1.2f * s, 0.84f * s), alpha = alpha)
        drawRect(color, topLeft = Offset(at.x + 0.42f * s, at.y - 2.3f * s), size = Size(0.2f * s, 2.3f * s), alpha = alpha)
        flag.reset()
        flag.moveTo(at.x + 0.62f * s, at.y - 2.3f * s)
        flag.cubicTo(at.x + 1.4f * s, at.y - 1.9f * s, at.x + 1.35f * s, at.y - 1.3f * s, at.x + 1.0f * s, at.y - 0.9f * s)
        flag.cubicTo(at.x + 1.1f * s, at.y - 1.35f * s, at.x + 0.95f * s, at.y - 1.65f * s, at.x + 0.62f * s, at.y - 1.75f * s)
        flag.close()
        drawPath(flag, color, alpha = alpha)
    }
}

private fun easeOutBack(x: Float): Float {
    val c1 = 1.70158f
    val c3 = c1 + 1
    val u = x - 1
    return 1 + c3 * u * u * u + c1 * u * u
}

/** Every random choice for one show, made once up front so frames only do arithmetic. */
private class ShowParts(rng: Random) {
    // Fireworks.
    val burstX = floatArrayOf(0.5f, 0.27f, 0.73f, 0.38f, 0.62f, 0.3f, 0.7f)
    val burstY = floatArrayOf(0.2f, 0.3f, 0.22f, 0.34f, 0.26f, 0.28f, 0.24f)
    val burstColor = IntArray(FIREWORK_S.size) { (it * 3 + rng.nextInt(2)) % PARTY_COLORS.size }
    private val sparks = FIREWORK_S.size * SPARKS_PER_BURST
    val sparkAngle = FloatArray(sparks) { (it % SPARKS_PER_BURST) * 6.2832f / SPARKS_PER_BURST + rng.nextFloat() * 0.12f }
    val sparkSpeed = FloatArray(sparks) { 0.65f + rng.nextFloat() * 0.4f }
    val sparkPhase = FloatArray(sparks) { rng.nextFloat() * 6.28f }
    val sparkWhite = BooleanArray(sparks) { rng.nextFloat() < 0.3f }

    // Cannon confetti: up and inward from each bottom corner (angle in radians, screen y is down).
    private val bits = FANFARE_BIG_S.size * 2 * CANNON_BITS
    val bitAngle = FloatArray(bits) { Math.toRadians(-82.0 + rng.nextDouble() * 44.0).toFloat() }
    val bitSpeed = FloatArray(bits) { 1.0f + rng.nextFloat() * 0.9f }
    val bitSpin = FloatArray(bits) { (rng.nextFloat() - 0.5f) * 900f }
    val bitPhase = FloatArray(bits) { rng.nextFloat() * 6.28f }
    val bitColor = IntArray(bits) { rng.nextInt(PARTY_COLORS.size) }

    // Musical notes: one per toot, plus extra ones while the long notes are held.
    val noteAt = (FANFARE_NOTES_S.toList() + listOf(1.12f, 1.28f, 2.0f, 2.2f, 2.42f, 2.62f)).sorted().toFloatArray()
    val noteSpeed = FloatArray(noteAt.size) { 0.9f + rng.nextFloat() * 0.5f }
    val notePhase = FloatArray(noteAt.size) { rng.nextFloat() * 6.28f }
    val noteColor = IntArray(noteAt.size) { rng.nextInt(PARTY_COLORS.size) }
    val noteTilt = FloatArray(noteAt.size) { (rng.nextFloat() - 0.5f) * 40f }
}
