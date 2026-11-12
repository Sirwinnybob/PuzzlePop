package com.family.puzzlepop.kid

/** Pure timing for the grown-up button: a press only counts once it has been held for [holdMs]. */
class HoldToOpen(val holdMs: Long = 2000) {
    private var downAt: Long? = null

    fun down(nowMs: Long) {
        downAt = nowMs
    }

    /** Ends the press. True only if it lasted at least [holdMs]. */
    fun up(nowMs: Long): Boolean {
        val start = downAt ?: return false
        downAt = null
        return nowMs - start >= holdMs
    }

    /** True while a press is down and has already lasted [holdMs]: the ring is full. */
    fun isFull(nowMs: Long): Boolean {
        val start = downAt ?: return false
        return nowMs - start >= holdMs
    }

    /** 0..1 fill of the ring; 0 when nothing is pressed. */
    fun progress(nowMs: Long): Float {
        val start = downAt ?: return 0f
        return ((nowMs - start).toFloat() / holdMs).coerceIn(0f, 1f)
    }
}
