package com.family.puzzlepop.kid

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.delay

/** Pure idle-timer rules for the kid-mode screen nap. Times are milliseconds on any monotonic-ish clock. */
object ScreenNap {
    enum class Phase { Awake, Napping, Sleeping }

    /**
     * Awake until [napAfterMin] minutes without a touch; then Napping (black screen, tablet kept awake);
     * after [realSleepAfterMin] more minutes of napping, Sleeping (keep-awake released so the tablet locks).
     */
    fun phase(nowMs: Long, lastTouchMs: Long, napAfterMin: Int, realSleepAfterMin: Int): Phase {
        val idle = nowMs - lastTouchMs
        val napAt = napAfterMin * 60_000L
        return when {
            idle < napAt -> Phase.Awake
            idle < napAt + realSleepAfterMin * 60_000L -> Phase.Napping
            else -> Phase.Sleeping
        }
    }
}

/** Idle state shared by the touch tracker and the overlay. */
class NapState(startMs: Long) {
    /** Plain field: written on every pointer event, never read in composition. */
    @Volatile var lastTouchMs: Long = startMs
    var phase by mutableStateOf(ScreenNap.Phase.Awake)
    val napping: Boolean get() = phase != ScreenNap.Phase.Awake

    fun touch(nowMs: Long) { lastTouchMs = nowMs }
}

/** Notes every touch in this subtree (without consuming it) as activity. Ignored while napping: the overlay wakes. */
fun Modifier.napTouchTracker(state: NapState): Modifier = pointerInput(state) {
    awaitPointerEventScope {
        while (true) {
            val e = awaitPointerEvent(PointerEventPass.Initial)
            if (!state.napping && e.changes.any { it.pressed || e.type == PointerEventType.Release }) {
                state.touch(System.currentTimeMillis())
            }
        }
    }
}

/**
 * Kid-mode screen nap. While [active]: keeps the screen on, and after [napAfterMin] idle minutes shows a black
 * overlay at minimum brightness; the first tap wakes it and is swallowed. After [realSleepAfterMin] more
 * minutes the keep-awake flag is dropped so the tablet can lock normally. Nothing is left set when [active]
 * turns false.
 */
@Composable
fun ScreenNapHost(
    state: NapState,
    active: Boolean,
    napAfterMin: Int,
    realSleepAfterMin: Int,
    onNapping: (Boolean) -> Unit,
) {
    val activity = androidx.activity.compose.LocalActivity.current
    val napAfter by androidx.compose.runtime.rememberUpdatedState(napAfterMin)
    val sleepAfter by androidx.compose.runtime.rememberUpdatedState(realSleepAfterMin)

    // Resuming (e.g. after the tablet really slept and was unlocked) starts a fresh idle period.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        state.touch(System.currentTimeMillis())
        // Any nap or real sleep ends here: brightness and keep-awake come back, and the idle timer restarts.
        state.phase = ScreenNap.Phase.Awake
    }

    LaunchedEffect(active) {
        if (!active) { state.phase = ScreenNap.Phase.Awake; return@LaunchedEffect }
        state.touch(System.currentTimeMillis())
        while (true) {
            val p = ScreenNap.phase(System.currentTimeMillis(), state.lastTouchMs, napAfter, sleepAfter)
            // Waking is the overlay's job (after the finger lifts); here the phase only moves deeper.
            if (state.phase == ScreenNap.Phase.Awake || p != ScreenNap.Phase.Awake) state.phase = p
            delay(1_000)
        }
    }
    LaunchedEffect(state.napping, active) { onNapping(active && state.napping) }

    val keepOn = active && state.phase != ScreenNap.Phase.Sleeping
    DisposableEffect(activity, keepOn) {
        if (keepOn) activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    val dim = active && state.napping
    DisposableEffect(activity, dim) {
        if (dim && activity != null) setDim(activity, true)
        onDispose { if (dim && activity != null) setDim(activity, false) }
    }

    if (active && state.napping) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(state) {
                    awaitPointerEventScope {
                        while (true) {
                            val first = awaitPointerEvent(PointerEventPass.Initial)
                            first.changes.forEach { it.consume() }
                            // Swallow the whole gesture, so the finger lifting does not land on the puzzle.
                            var anyDown = first.changes.any { it.pressed }
                            while (anyDown) {
                                val e = awaitPointerEvent(PointerEventPass.Initial)
                                e.changes.forEach { it.consume() }
                                anyDown = e.changes.any { it.pressed }
                            }
                            state.touch(System.currentTimeMillis())
                            state.phase = ScreenNap.Phase.Awake
                        }
                    }
                },
        )
    }
}

private fun setDim(activity: Activity, dim: Boolean) {
    val w = activity.window
    val lp = w.attributes
    lp.screenBrightness = if (dim) 0.01f else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
    w.attributes = lp
    val c = WindowCompat.getInsetsController(w, w.decorView)
    if (dim) {
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        c.hide(WindowInsetsCompat.Type.systemBars())
    } else {
        c.show(WindowInsetsCompat.Type.systemBars())
    }
}
