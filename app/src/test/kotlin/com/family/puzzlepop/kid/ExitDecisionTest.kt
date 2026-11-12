package com.family.puzzlepop.kid

import org.junit.Assert.assertEquals
import org.junit.Test

class ExitDecisionTest {
    @Test fun pinnedAndLockedWaitsForDeviceUnlock() =
        assertEquals(ExitStep.WaitForDeviceUnlock, ExitDecision.next(pinned = true, locked = true))

    @Test fun pinnedButNotLockedUsesAppGate() =
        assertEquals(ExitStep.AppGate, ExitDecision.next(pinned = true, locked = false))

    @Test fun notPinnedUsesAppGate() {
        assertEquals(ExitStep.AppGate, ExitDecision.next(pinned = false, locked = false))
        assertEquals(ExitStep.AppGate, ExitDecision.next(pinned = false, locked = true))
    }

    @Test fun finishesOnlyWhenPendingUnlockedAndIdle() {
        assertEquals(true, ExitDecision.shouldFinishPending(true, false, false))
        assertEquals(false, ExitDecision.shouldFinishPending(true, true, false))
        assertEquals(false, ExitDecision.shouldFinishPending(true, false, true))
        assertEquals(false, ExitDecision.shouldFinishPending(false, false, false))
    }
}
