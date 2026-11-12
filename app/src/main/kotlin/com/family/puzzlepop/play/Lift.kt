package com.family.puzzlepop.play

import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

internal const val LIFT_SCALE = 0.06f
private const val LIFT_UP_NANOS = 120_000_000L
private const val DROP_NANOS = 180_000_000L
private const val LOCK_SETTLE_NANOS = 80_000_000L
private const val PRESS_NANOS = 160_000_000L
private const val GLOW_NANOS = 400_000_000L
private const val PRESS_AMOUNT = 0.03f
/** Drop dip: scale reaches 1 - 0.06 * 0.33 = ~0.98 at the bottom of the spring. */
private const val DROP_UNDERSHOOT = 0.33f
private const val IDLE = Long.MIN_VALUE
/** Tap-to-rotate: the drawn group catches up with the engine's new angle over this long. */
private const val ROTATE_NANOS = 150_000_000L

/**
 * Animation state of one lifted group (plain fields, driven by the frame clock, read by the moving
 * layer's draw lambda). [value] is 0 at rest and 1 fully lifted; it dips below 0 briefly on a drop.
 *
 * While [isFloating] the group is drawn live in the moving layer; once [isDone] it is removed and
 * falls back into the cached resting layer.
 */
internal class Lift(val members: IntArray, ref: Int, fx: Float, fy: Float) {
    /** Reference piece and the finger point on it (world offset from its top-left): the scale centre while held. */
    var ref = ref
        private set
    var fx = fx
        private set
    var fy = fy
        private set

    /** A re-grab: scale about the new finger point from now on. */
    fun refocus(piece: Int, grabX: Float, grabY: Float) {
        ref = piece; fx = grabX; fy = grabY; fixedFocus = false
    }

    /** A finger is holding the group. */
    var held = false
    var value = 0f
    /** Combined draw scale: lift (1.06x) times the lock press-in. */
    var scale = 1f
    /** Warm glow strength 0..1 after a lock. */
    var glow = 0f
    var wasFloating = false

    /** Fixed scale centre (world) for a locked group; otherwise the finger point on [ref]. */
    var fixedFocus = false
    var cx = 0f
    var cy = 0f

    private var mode = MODE_UP
    private var from = 0f
    private var t0 = IDLE
    private var dur = LIFT_UP_NANOS
    private var lockT0 = IDLE

    /**
     * Tap-to-rotate animation: the engine already holds the new angle; the group is drawn turned back by
     * [angleLag] degrees about ([pivotX], [pivotY]) (the engine's group pivot), easing to 0.
     */
    var angleLag = 0f
        private set
    var pivotX = 0f
        private set
    var pivotY = 0f
        private set
    private var lagFrom = 0f
    private var rotT0 = IDLE

    val lockActive: Boolean get() = lockT0 != IDLE
    val isFloating: Boolean get() = held || value != 0f || lockActive || rotT0 != IDLE
    val isDone: Boolean get() = !held && value == 0f && t0 == IDLE && lockT0 == IDLE && rotT0 == IDLE

    /** The engine just turned the group by [deltaDeg] about ([px], [py]): animate the turn on screen. */
    fun rotateBy(deltaDeg: Float, px: Float, py: Float) {
        pivotX = px; pivotY = py
        lagFrom = angleLag + deltaDeg // a second tap mid-turn carries on from where the picture is
        angleLag = lagFrom
        rotT0 = -1L
    }

    fun liftUp(startValue: Float) {
        held = true
        mode = MODE_UP; from = startValue; t0 = -1L; dur = LIFT_UP_NANOS
    }

    /** Dropped without locking: spring back to rest. */
    fun drop() {
        held = false
        mode = MODE_DROP; from = value; t0 = -1L; dur = DROP_NANOS
    }

    /** Dropped onto its home: settle quickly, then press in and glow. */
    fun lock(centreX: Float, centreY: Float) {
        held = false
        fixedFocus = true; cx = centreX; cy = centreY
        mode = MODE_LOCK; from = value; t0 = -1L; dur = LOCK_SETTLE_NANOS
        lockT0 = -1L
    }

    /** Advances to frame time [now]; true while something is still animating. */
    fun step(now: Long): Boolean {
        if (t0 != IDLE) {
            if (t0 < 0) t0 = now
            val f = ((now - t0).toFloat() / dur).coerceIn(0f, 1f)
            value = when (mode) {
                MODE_UP -> from + (1f - from) * (1f - (1f - f).pow(3))
                MODE_DROP -> from * spring(f)
                else -> from * (1f - smooth(f))
            }
            if (f >= 1f) {
                value = if (mode == MODE_UP) 1f else 0f
                t0 = IDLE
            }
        }
        var press = 1f
        glow = 0f
        if (lockT0 != IDLE) {
            if (lockT0 < 0) lockT0 = now
            val t = now - lockT0
            if (t >= GLOW_NANOS) {
                lockT0 = IDLE
            } else {
                if (t < PRESS_NANOS) press = 1f - PRESS_AMOUNT * sin(PI.toFloat() * t / PRESS_NANOS)
                glow = (1f - t.toFloat() / GLOW_NANOS).pow(1.5f)
            }
        }
        scale = (1f + LIFT_SCALE * value) * press
        if (rotT0 != IDLE) {
            if (rotT0 < 0) rotT0 = now
            val f = ((now - rotT0).toFloat() / ROTATE_NANOS).coerceIn(0f, 1f)
            angleLag = lagFrom * (1f - f).pow(3) // ease-out cubic
            if (f >= 1f) {
                angleLag = 0f
                rotT0 = IDLE
            }
        }
        return t0 != IDLE || lockT0 != IDLE || rotT0 != IDLE
    }

    private fun smooth(q: Float) = q * q * (3f - 2f * q)

    /** 1 -> -DROP_UNDERSHOOT -> 0: down past rest, then settles back. */
    private fun spring(f: Float): Float {
        val split = 0.55f
        return if (f < split) {
            1f - (1f + DROP_UNDERSHOOT) * sin(f / split * (PI.toFloat() / 2))
        } else {
            -DROP_UNDERSHOOT * (1f - smooth((f - split) / (1f - split)))
        }
    }

    private companion object {
        const val MODE_UP = 0
        const val MODE_DROP = 1
        const val MODE_LOCK = 2
    }
}
