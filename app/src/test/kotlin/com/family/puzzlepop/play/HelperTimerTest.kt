package com.family.puzzlepop.play

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HelperTimerTest {
    @Test
    fun notDueBeforeReset() {
        val t = HelperTimer(20_000L)
        assertFalse(t.isDue(1_000_000L))
    }

    @Test
    fun dueAfterDelay() {
        val t = HelperTimer(20_000L)
        t.reset(0L)
        assertFalse(t.isDue(19_999L))
        assertTrue(t.isDue(20_000L))
    }

    @Test
    fun resetRestarts() {
        val t = HelperTimer(20_000L)
        t.reset(0L)
        t.reset(10_000L)
        assertFalse(t.isDue(25_000L))
        assertTrue(t.isDue(30_000L))
    }

    @Test
    fun pausedTimeDoesNotCount() {
        val t = HelperTimer(20_000L)
        t.reset(0L)
        t.pause(5_000L)          // 5 s counted
        t.resume(100_000L)       // 95 s paused, not counted
        assertFalse(t.isDue(114_999L))
        assertTrue(t.isDue(115_000L))
    }

    @Test
    fun notDueAgainUntilResetAfterShown() {
        val t = HelperTimer(20_000L)
        t.reset(0L)
        assertTrue(t.isDue(20_000L))
        t.shown(20_000L)
        assertFalse(t.isDue(60_000L))
        t.reset(60_000L)
        assertFalse(t.isDue(79_999L))
        assertTrue(t.isDue(80_000L))
    }
}
