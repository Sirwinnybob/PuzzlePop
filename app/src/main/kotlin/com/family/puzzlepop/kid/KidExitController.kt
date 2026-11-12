package com.family.puzzlepop.kid

import android.app.Activity
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.family.puzzlepop.PuzzlePopApp
import com.family.puzzlepop.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Runs "leave kid mode" for a screen. Pinned: unpin and let the device lock be the check (the nav host
 * finishes the exit on the next unlock). Not pinned, or pinned but the device did not lock: the app gate.
 */
class KidExitController internal constructor(
    private val activity: Activity?,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope,
    private val leaveNow: suspend () -> Unit,
) {
    private var showGate by mutableStateOf(false)
    private var unpinnedByUs = false
    private var busy = false

    /** [alreadyGated]: the grown-up already passed the app gate (break/done screens), so never ask it again. */
    fun request(alreadyGated: Boolean) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                val act = activity
                val pinned = act != null && ScreenPinning.isPinned(act)
                val step = if (pinned) KidModeExit.beginPinnedExit(act!!, settings) else ExitDecision.next(false, false)
                unpinnedByUs = pinned
                when {
                    step == ExitStep.WaitForDeviceUnlock -> Unit // the unlock is the check; AppNavHost finishes on resume
                    alreadyGated -> leaveNow()
                    else -> {
                        // Pinned but the device didn't lock: pin again before asking, so Home/Recents can't be used.
                        if (pinned) act?.let { ScreenPinning.start(it) }
                        showGate = true
                    }
                }
            } finally {
                busy = false
            }
        }
    }

    @Composable
    fun Gate() {
        if (!showGate) return
        GrownUpGateDialog(
            onDismiss = {
                showGate = false
                if (unpinnedByUs) activity?.let { ScreenPinning.start(it) }
                unpinnedByUs = false
            },
            onUnlocked = {
                showGate = false
                unpinnedByUs = false
                scope.launch { leaveNow() }
            },
        )
    }
}

@Composable
fun rememberKidExitController(onLeft: () -> Unit): KidExitController {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val settings = (context.applicationContext as PuzzlePopApp).container.settings
    val scope = rememberCoroutineScope()
    val left by rememberUpdatedState(onLeft)
    return remember(activity) {
        KidExitController(activity, settings, scope) { KidModeExit.leave(activity, settings) { left() } }
    }
}
