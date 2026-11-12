package com.family.puzzlepop.play

/**
 * The kid-mode magnet's brake. A held group only pulls itself home once the finger has stayed near home,
 * moving slowly, for [dwellMs]: a piece swiped wildly across the board just passes over its spot. It also
 * tells a fling (let go while moving fast) from a real drop, so a flung piece doesn't lock either.
 *
 * Speeds are in screen px per ms; times are uptime ms (the same clock as pointer events).
 */
class MagnetGate(
    private val slowPxPerMs: Float,
    private val flingPxPerMs: Float,
    private val dwellMs: Long = DWELL_MS,
) {
    private class Track(var x: Float, var y: Float, var t: Long) {
        var speed = 0f
        /** When the finger settled (near home and slow), or -1 while it isn't. */
        var settledSince = -1L
        /** Near home at the last check, so a fast arrival that then stops dead still gets checked again. */
        var nearHome = false
    }

    private val tracks = HashMap<Long, Track>()

    fun start(pointerId: Long, x: Float, y: Float, t: Long) {
        tracks[pointerId] = Track(x, y, t)
    }

    fun remove(pointerId: Long) {
        tracks.remove(pointerId)
    }

    fun clear() = tracks.clear()

    /** Records where the finger is now. Call on every move. */
    fun move(pointerId: Long, x: Float, y: Float, t: Long) {
        val tr = tracks[pointerId] ?: return
        val dt = t - tr.t
        if (dt <= 0L) {
            // Same timestamp: keep the distance for the next sample instead of dividing by zero.
            return
        }
        val dx = x - tr.x
        val dy = y - tr.y
        val now = kotlin.math.sqrt(dx * dx + dy * dy) / dt
        // Smooth over ~SMOOTH_MS so one quiet frame at the turn of a swipe doesn't read as "stopped".
        val a = dt.toFloat() / (dt + SMOOTH_MS)
        tr.speed += a * (now - tr.speed)
        tr.x = x; tr.y = y; tr.t = t
    }

    /**
     * True when the magnet should pull [pointerId]'s group home now. [nearHome] is whether the group is within
     * the magnet radius; [t] is the current time (an event's, or the clock's when no event came).
     */
    fun shouldPull(pointerId: Long, nearHome: Boolean, t: Long): Boolean {
        val tr = tracks[pointerId] ?: return false
        tr.nearHome = nearHome
        if (!nearHome || speedAt(tr, t) > slowPxPerMs) {
            tr.settledSince = -1L
            return false
        }
        if (tr.settledSince < 0L) tr.settledSince = t
        return t - tr.settledSince >= dwellMs
    }

    /**
     * How long until a finger near home could change state with no new event (a settled one finishes its
     * dwell; a moving one that stopped dead counts as still), so the caller can check again; -1 for never.
     */
    fun nextCheckIn(t: Long): Long {
        var best = -1L
        for (tr in tracks.values) {
            val at = when {
                tr.settledSince >= 0L -> tr.settledSince + dwellMs
                tr.nearHome -> tr.t + STILL_AFTER_MS + 1
                else -> continue
            }
            val left = (at - t).coerceAtLeast(1L)
            if (best < 0L || left < best) best = left
        }
        return best
    }

    /** True if [pointerId] was still moving fast when it lifted at [t]: a fling, not a placement. */
    fun isFling(pointerId: Long, t: Long): Boolean {
        val tr = tracks[pointerId] ?: return false
        return speedAt(tr, t) > flingPxPerMs
    }

    /** A finger that sent no move for a while has stopped, whatever its last speed was. */
    private fun speedAt(tr: Track, t: Long): Float = if (t - tr.t > STILL_AFTER_MS) 0f else tr.speed

    companion object {
        const val DWELL_MS = 150L
        const val SMOOTH_MS = 40f
        const val STILL_AFTER_MS = 100L
        /** Finger speeds in dp per ms (1 = 1000 dp a second). */
        const val SLOW_DP_PER_MS = 0.3f
        const val FLING_DP_PER_MS = 1.5f
    }
}
