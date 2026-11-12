package com.family.puzzlepop.kid

import android.app.Activity
import android.app.ActivityManager
import android.app.KeyguardManager
import android.content.Context

/**
 * Android "screen pinning" (lock task for a non-allowlisted app). The system asks the user to confirm
 * the first time; whether leaving then needs the device PIN depends on the "Ask for PIN before unpinning"
 * setting, which the grown-up turns on once (see [PinningGuideSheet]). No device-owner APIs are used.
 */
object ScreenPinning {
    private var attempts = 0

    /** Forget earlier attempts, so a fresh kid mode start can ask again. */
    fun resetAttempts() {
        attempts = 0
    }

    /**
     * Pins the app unless it already is. Called each time kid mode comes to the front, because a request made
     * while the lock screen or another dialog is up is silently ignored. Capped at 3 asks, so a grown-up who
     * says "No thanks" to the system prompt isn't nagged forever.
     */
    fun ensurePinned(activity: Activity) {
        if (isPinned(activity) || attempts >= MAX_ATTEMPTS) return
        attempts++
        start(activity)
    }

    private const val MAX_ATTEMPTS = 3

    /** Asks the system to pin this app. Harmless if it is already pinned or the system refuses. */
    fun start(activity: Activity) {
        try {
            activity.startLockTask()
        } catch (_: IllegalStateException) {
            // Not in a state to be pinned right now (e.g. finishing); kid mode still works unpinned.
        } catch (_: SecurityException) {
        }
    }

    fun stop(activity: Activity) {
        attempts = 0
        try {
            activity.stopLockTask()
        } catch (_: IllegalStateException) {
            // Not pinned: nothing to undo.
        } catch (_: SecurityException) {
        }
    }

    fun isPinned(context: Context): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return am.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
    }

    fun hasScreenLock(context: Context): Boolean {
        val km = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        return km.isDeviceSecure
    }
}
