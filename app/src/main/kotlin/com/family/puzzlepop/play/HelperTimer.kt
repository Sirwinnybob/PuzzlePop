package com.family.puzzlepop.play

/**
 * Tracks unpaused time since the last [reset] and reports when the helper should appear. The timer is not
 * due until the first [reset]. After [shown] it is not due again until the next [reset], and then a full
 * delay must pass. Pausing and then resetting keeps the timer paused: time counts again only after [resume].
 */
class HelperTimer(var delayMs: Long) {
    private var started = false
    private var accumulatedMs = 0L
    private var segmentStartMs = 0L
    private var paused = false
    private var shownSinceReset = false

    fun reset(nowMs: Long) {
        started = true
        accumulatedMs = 0L
        segmentStartMs = nowMs
        shownSinceReset = false
    }

    fun pause(nowMs: Long) {
        if (paused) return
        accumulatedMs += nowMs - segmentStartMs
        paused = true
    }

    fun resume(nowMs: Long) {
        if (!paused) return
        segmentStartMs = nowMs
        paused = false
    }

    fun isDue(nowMs: Long): Boolean {
        if (!started || shownSinceReset) return false
        return elapsedMs(nowMs) >= delayMs
    }

    fun shown(nowMs: Long) {
        shownSinceReset = true
    }

    private fun elapsedMs(nowMs: Long): Long =
        accumulatedMs + if (paused) 0L else nowMs - segmentStartMs
}
