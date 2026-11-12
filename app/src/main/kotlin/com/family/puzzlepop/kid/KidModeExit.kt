package com.family.puzzlepop.kid

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import com.family.puzzlepop.settings.SettingsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/**
 * The single way out of kid mode (kid home's grown-up button, the break screen, the done-for-today
 * screen). Change the exit behaviour here only.
 *
 * When pinned, the tablet's own unlock is the grown-up check: [beginPinnedExit] unpins, and if Android then
 * locks the device we wait for the parent to unlock it (see AppNavHost's resume check). Otherwise the caller
 * shows the app's PIN gate.
 */
object KidModeExit {
    /** True while [beginPinnedExit] is watching for the lock; also stops kid mode from re-pinning itself. */
    @Volatile
    var inFlight = false
        private set

    private const val LOCK_WINDOW_MS = 1500L
    private const val POLL_MS = 50L

    fun isKeyguardLocked(context: Context): Boolean =
        (context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isKeyguardLocked

    /**
     * Pinned exit: remember that an exit is pending, unpin, and watch up to ~1.5 s for the device to lock.
     * Returns [ExitStep.WaitForDeviceUnlock] (pending flag stays set until the unlock) or [ExitStep.AppGate]
     * (flag cleared; the app is now unpinned).
     */
    suspend fun beginPinnedExit(activity: Activity, settings: SettingsRepository): ExitStep {
        inFlight = true
        try {
            settings.setPendingKidExit(true)
            ScreenPinning.stop(activity)
            var waited = 0L
            var locked = isKeyguardLocked(activity)
            while (!locked && waited < LOCK_WINDOW_MS) {
                delay(POLL_MS)
                waited += POLL_MS
                locked = isKeyguardLocked(activity)
            }
            val step = ExitDecision.next(pinned = true, locked = locked)
            if (step == ExitStep.AppGate) settings.setPendingKidExit(false)
            return step
        } finally {
            inFlight = false
        }
    }

    /** True when kid mode shouldn't pin itself right now (an exit is under way or waiting for the unlock). */
    suspend fun pinSuppressed(settings: SettingsRepository): Boolean = inFlight || settings.pendingKidExit.first()

    /** Unpins the screen, switches kid mode off, clears any pending exit, then runs [navigate] (to the library). */
    suspend fun leave(activity: Activity?, settings: SettingsRepository, navigate: () -> Unit) {
        activity?.let { ScreenPinning.stop(it) }
        settings.setKidModeActive(false)
        navigate()
        settings.setPendingKidExit(false)
    }
}
