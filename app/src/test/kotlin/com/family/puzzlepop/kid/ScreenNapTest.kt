package com.family.puzzlepop.kid

import com.family.puzzlepop.kid.ScreenNap.Phase
import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenNapTest {
    private val min = 60_000L

    @Test fun awakeBeforeNapTime() {
        assertEquals(Phase.Awake, ScreenNap.phase(5 * min - 1, 0, 5, 30))
    }

    @Test fun napsAtNapTime() {
        assertEquals(Phase.Napping, ScreenNap.phase(5 * min, 0, 5, 30))
    }

    @Test fun touchResetsTimer() {
        // idle since 4 min ago: still awake at minute 8 when the last touch was at minute 4
        assertEquals(Phase.Awake, ScreenNap.phase(8 * min, 4 * min, 5, 30))
        assertEquals(Phase.Napping, ScreenNap.phase(9 * min, 4 * min, 5, 30))
    }

    @Test fun realSleepThirtyMinutesAfterNap() {
        assertEquals(Phase.Napping, ScreenNap.phase(35 * min - 1, 0, 5, 30))
        assertEquals(Phase.Sleeping, ScreenNap.phase(35 * min, 0, 5, 30))
    }

    @Test fun changedSettingsTakeEffect() {
        assertEquals(Phase.Awake, ScreenNap.phase(2 * min, 0, 5, 30))
        assertEquals(Phase.Napping, ScreenNap.phase(2 * min, 0, 1, 30))
        assertEquals(Phase.Sleeping, ScreenNap.phase(10 * min, 0, 1, 5))
    }

    @Test fun touchAtResumeEndsRealSleep() {
        assertEquals(Phase.Sleeping, ScreenNap.phase(60 * min, 0, 5, 30))
        assertEquals(Phase.Awake, ScreenNap.phase(60 * min, 60 * min, 5, 30))
    }
}
