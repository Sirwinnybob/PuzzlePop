package com.family.puzzlepop.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.family.puzzlepop.PuzzlePopApp
import androidx.activity.compose.LocalActivity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay
import com.family.puzzlepop.kid.ExitDecision
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.NavDestination.Companion.hasRoute
import com.family.puzzlepop.kid.BreakScreen
import com.family.puzzlepop.kid.DoneForTodayScreen
import com.family.puzzlepop.kid.PlayTimeKeeper.Status
import com.family.puzzlepop.kid.KidModeExit
import com.family.puzzlepop.kid.NapState
import com.family.puzzlepop.kid.ScreenNapHost
import com.family.puzzlepop.kid.napTouchTracker
import com.family.puzzlepop.settings.KidSettings
import com.family.puzzlepop.kid.rememberKidExitController
import com.family.puzzlepop.kid.rememberKidTimeViewModel
import com.family.puzzlepop.settings.GrownUpSettingsScreen
import com.family.puzzlepop.settings.SpeedTestScreen
import kotlinx.coroutines.launch
import com.family.puzzlepop.kid.KidHomeScreen
import com.family.puzzlepop.kid.PinSetupScreen
import kotlinx.coroutines.flow.first
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.family.puzzlepop.library.LibraryScreen
import com.family.puzzlepop.play.PlayScreen

private const val START_SETUP = 0
private const val START_LIBRARY = 1
private const val START_KID = 2

/**
 * Library -> (new-puzzle sheet, part of the library screen) -> Play. Home in Play pops back to the
 * library. Each Play entry owns its own PlayViewModel (it lives as long as the back-stack entry, so it
 * survives rotation and is cleared when the puzzle is left).
 *
 * Kid mode is a second branch: KidHome -> Play(kid = true) -> back to KidHome, never to the library
 * (that needs the grown-up gate on KidHome). The app opens straight into it while it is active.
 */
@Composable
fun AppNavHost() {
    val nav = rememberNavController()
    val activity = LocalActivity.current
    val settings = (LocalContext.current.applicationContext as PuzzlePopApp).container.settings
    val scope = rememberCoroutineScope()
    val timeVm = rememberKidTimeViewModel()
    val timeStatus by timeVm.status.collectAsState()
    LifecycleEventEffect(Lifecycle.Event.ON_START) { timeVm.setForeground(true) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { timeVm.setForeground(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { timeVm.persistNow() }
    // Decided once, from the first values of the flags: no PIN yet -> PIN setup (can't be skipped); kid mode
    // on (or "always open in kid mode") -> kid home; else the library. Saved across rotation, so it neither
    // flashes the library nor re-decides after the PIN is set or kid mode is left.
    var start by rememberSaveable { mutableStateOf<Int?>(null) }
    LaunchedEffect(Unit) {
        // Always run: keeps the safety mirror in step and restores a PIN lost to a corrupt settings file.
        val safety = settings.recoverSafety()
        if (start == null) {
            val container = (activity?.applicationContext as? PuzzlePopApp)?.container
            val hasPhotos = !safety.pinPresent && container?.photoRepository?.hasAnyPhoto() == true
            val unlockedAfterPending = settings.pendingKidExit.first() && activity?.let { !KidModeExit.isKeyguardLocked(it) } == true
            val decision = startDecision(
                pinPresent = safety.pinPresent,
                pinEverSet = safety.pinEverSet,
                corruptionDetected = safety.corruptionDetected,
                hasPhotos = hasPhotos,
                kidModeActive = settings.kidModeActive.first(),
                alwaysKid = settings.kidSettings.first().alwaysOpenInKidMode,
            )
            start = when {
                !safety.pinPresent && decision == Start.KID -> {
                    // PIN lost but the library has content: never open it unprotected. Kid mode; leaving needs
                    // the device unlock, then a new PIN is set (see leaveToLibrary).
                    if (unlockedAfterPending) {
                        settings.setKidModeActive(false)
                        settings.setPendingKidExit(false)
                        START_SETUP
                    } else {
                        settings.setKidModeActive(true)
                        START_KID
                    }
                }
                decision == Start.SETUP -> START_SETUP
                unlockedAfterPending -> {
                    // Killed while locked, now unlocked: finish the exit.
                    settings.setKidModeActive(false)
                    settings.setPendingKidExit(false)
                    START_LIBRARY
                }
                decision == Start.KID -> START_KID
                else -> START_LIBRARY
            }
        }
    }
    // After leaving kid mode (a grown-up check was passed): the library, or PIN setup when the PIN was lost.
    val leaveToLibrary: () -> Unit = {
        scope.launch {
            val dest: Any = if (settings.hasPin.first()) LibraryRoute else PinSetupRoute
            nav.navigate(dest) { popUpTo(nav.graph.id) { inclusive = true } }
        }
    }
    // Pinned exit waiting for the device unlock: finish on the first resume with the keyguard gone. Polls a
    // few seconds in case the keyguard flag lags the resume.
    var resumeTick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumeTick++ }
    // The parent may take a long time to unlock: ACTION_USER_PRESENT re-runs the check whenever they do.
    val appContext = LocalContext.current.applicationContext
    DisposableEffect(appContext) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) { resumeTick++ }
        }
        ContextCompat.registerReceiver(appContext, receiver, IntentFilter(Intent.ACTION_USER_PRESENT), ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { appContext.unregisterReceiver(receiver) }
    }
    LaunchedEffect(resumeTick, start) {
        if (start == null || activity == null) return@LaunchedEffect
        repeat(12) {
            if (!settings.pendingKidExit.first()) return@LaunchedEffect
            if (ExitDecision.shouldFinishPending(true, KidModeExit.isKeyguardLocked(activity), KidModeExit.inFlight)) {
                KidModeExit.leave(activity, settings) { leaveToLibrary() }
                return@LaunchedEffect
            }
            delay(250)
        }
    }
    val startKind = start
    if (startKind == null) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) // until the first values arrive
        return
    }
    val startDestination: Any = when (startKind) {
        START_SETUP -> PinSetupRoute
        START_KID -> KidHomeRoute
        else -> LibraryRoute
    }
    val exit = rememberKidExitController { leaveToLibrary() }
    val leaveKidMode: () -> Unit = { exit.request(alreadyGated = true) }
    val backEntry by nav.currentBackStackEntryAsState()
    val inKidScreen = backEntry?.let { e ->
        e.destination.hasRoute<KidHomeRoute>() || (e.destination.hasRoute<PlayRoute>() && e.toRoute<PlayRoute>().kid)
    } ?: false
    val napState = remember { NapState(System.currentTimeMillis()) }
    val napCfg by settings.kidSettings.collectAsState(initial = KidSettings())
    Box(Modifier.fillMaxSize().napTouchTracker(napState)) {
    NavHost(navController = nav, startDestination = startDestination) {
        composable<PinSetupRoute> {
            PinSetupScreen(onDone = {
                nav.navigate(LibraryRoute) { popUpTo<PinSetupRoute> { inclusive = true } }
            })
        }
        composable<LibraryRoute> {
            // launchSingleTop: a fast double tap must not stack two Play screens for the same puzzle.
            LibraryScreen(
                onOpenPuzzle = { id -> nav.navigate(PlayRoute(id)) { launchSingleTop = true } },
                onStartKidMode = {
                    nav.navigate(KidHomeRoute) { popUpTo(nav.graph.id) { inclusive = true } }
                },
                onOpenSettings = { nav.navigate(GrownUpSettingsRoute) { launchSingleTop = true } },
            )
        }
        composable<GrownUpSettingsRoute> {
            GrownUpSettingsScreen(
                onDone = { nav.popBackStack() },
                onSpeedTest = { nav.navigate(SpeedTestRoute) { launchSingleTop = true } },
            )
        }
        composable<SpeedTestRoute> {
            SpeedTestScreen(onDone = { nav.popBackStack() })
        }
        composable<KidHomeRoute> {
            KidHomeScreen(
                onOpenPuzzle = { id -> nav.navigate(PlayRoute(id, kid = true)) { launchSingleTop = true } },
                onLeave = leaveToLibrary,
            )
        }
        composable<PlayRoute> { entry ->
            val route = entry.toRoute<PlayRoute>()
            val toLibrary = { nav.popBackStack<LibraryRoute>(inclusive = false); Unit }
            val toKidHome = { nav.popBackStack<KidHomeRoute>(inclusive = false); Unit }
            PlayScreen(
                puzzleId = route.puzzleId,
                kid = route.kid,
                onHome = if (route.kid) toKidHome else toLibrary,
                onPlayAgain = { id ->
                    // Replace this puzzle with the new one; Home from there still lands on the library (or kid home).
                    nav.navigate(PlayRoute(id, route.kid)) {
                        if (route.kid) popUpTo<KidHomeRoute>() else popUpTo<LibraryRoute>()
                        launchSingleTop = true
                    }
                },
                onNewPhoto = if (route.kid) toKidHome else toLibrary,
            )
        }
    }
    if (inKidScreen) {
        when (val st = timeStatus) {
            is Status.OnBreak -> BreakScreen(
                endsAtEpochMs = st.endsAtEpochMs,
                now = timeVm.now,
                onAddTime = timeVm::grownUpAddTime,
                onEndBreak = timeVm::grownUpEndBreak,
                onLeaveKidMode = leaveKidMode,
            )
            Status.DoneForToday -> DoneForTodayScreen(onAddTime = timeVm::grownUpAddTime, onLeaveKidMode = leaveKidMode)
            else -> Unit
        }
    }
    // Last, so it covers (and swallows touches for) everything, break and done screens included.
    ScreenNapHost(
        state = napState,
        active = inKidScreen,
        napAfterMin = napCfg.napAfterMin,
        realSleepAfterMin = napCfg.realSleepAfterMin,
        onNapping = timeVm::setNapping,
    )
    }
}
