package com.family.puzzlepop.kid

/** What leaving kid mode does next. Pure, so the rules can be unit-tested. */
enum class ExitStep { AppGate, WaitForDeviceUnlock }

object ExitDecision {
    /**
     * [pinned]: the screen is pinned right now (before unpinning). [locked]: the device keyguard locked as a
     * result of unpinning. Pinned and locked means the device unlock is the grown-up check; anything else
     * falls back to the app's own gate.
     */
    fun next(pinned: Boolean, locked: Boolean): ExitStep =
        if (pinned && locked) ExitStep.WaitForDeviceUnlock else ExitStep.AppGate

    /** On resume / app start: finish a pending exit only once the device is unlocked and no exit is mid-flight. */
    fun shouldFinishPending(pending: Boolean, keyguardLocked: Boolean, inFlight: Boolean): Boolean =
        pending && !keyguardLocked && !inFlight
}
