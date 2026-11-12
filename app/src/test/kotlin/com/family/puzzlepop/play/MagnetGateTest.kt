package com.family.puzzlepop.play

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MagnetGateTest {
    // 1 px = 1 dp here: slow under 0.3 px/ms, fling over 1.5 px/ms.
    private fun gate() = MagnetGate(slowPxPerMs = 0.3f, flingPxPerMs = 1.5f)

    @Test
    fun fastSwipeOverHomeNeverPulls() {
        val g = gate()
        g.start(1, 0f, 0f, 0)
        // 3 px/ms back and forth over home for two seconds, always "near home".
        var x = 0f
        var dir = 1f
        for (t in 8L..2000L step 8L) {
            x += dir * 24f
            if (x > 600f || x < 0f) dir = -dir
            g.move(1, x, 0f, t)
            assertFalse("pulled at t=$t", g.shouldPull(1, nearHome = true, t = t))
        }
        assertTrue(g.isFling(1, 2000))
    }

    @Test
    fun slowingDownNearHomePullsAfterDwell() {
        val g = gate()
        g.start(1, 0f, 0f, 0)
        // Arrive quickly, then creep.
        var t = 0L
        var x = 0f
        repeat(10) { t += 8; x += 16f; g.move(1, x, 0f, t); g.shouldPull(1, false, t) }
        var pulledAt = -1L
        repeat(60) {
            t += 8; x += 0.5f
            g.move(1, x, 0f, t)
            if (pulledAt < 0 && g.shouldPull(1, nearHome = true, t = t)) pulledAt = t
        }
        assertTrue(pulledAt > 0)
        assertFalse(g.isFling(1, t))
    }

    @Test
    fun stillFingerIsPulledByTheTimerPath() {
        val g = gate()
        g.start(1, 0f, 0f, 0)
        g.move(1, 1f, 0f, 10)
        assertFalse(g.shouldPull(1, true, 10)) // settles now
        assertEquals(MagnetGate.DWELL_MS, g.nextCheckIn(10))
        // No events for a while: the wake-up check pulls it.
        assertTrue(g.shouldPull(1, true, 10 + MagnetGate.DWELL_MS))
    }

    @Test
    fun fastArrivalThatStopsDeadIsPulledByTheTimerPath() {
        val g = gate()
        g.start(1, 0f, 0f, 0)
        g.move(1, 300f, 0f, 16) // one big jump into the spot, then no more events
        assertFalse(g.shouldPull(1, true, 16))
        val wake = 16 + g.nextCheckIn(16)
        assertTrue(wake > 16 + MagnetGate.STILL_AFTER_MS)
        assertFalse(g.shouldPull(1, true, wake)) // counts as still now: the dwell starts
        assertTrue(g.shouldPull(1, true, wake + g.nextCheckIn(wake)))
    }

    @Test
    fun leavingHomeResetsTheDwell() {
        val g = gate()
        g.start(1, 0f, 0f, 0)
        g.shouldPull(1, true, 0)
        g.shouldPull(1, false, 100)
        assertFalse(g.shouldPull(1, true, 120))
        assertEquals(-1L, gate().nextCheckIn(0))
    }

    @Test
    fun stoppingBeforeLettingGoIsNotAFling() {
        val g = gate()
        g.start(1, 0f, 0f, 0)
        var t = 0L
        var x = 0f
        repeat(20) { t += 8; x += 24f; g.move(1, x, 0f, t) }
        assertTrue(g.isFling(1, t))
        // Held still for a moment (no move events), then lifted.
        assertFalse(g.isFling(1, t + 200))
    }
}
