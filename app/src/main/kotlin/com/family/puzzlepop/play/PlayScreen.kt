package com.family.puzzlepop.play

import androidx.compose.foundation.gestures.detectTapGestures
import com.family.puzzlepop.ui.isShortLandscape
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.TextUnit
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.family.puzzlepop.BuildConfig
import com.family.puzzlepop.PuzzlePopApp
import com.family.puzzlepop.engine.options.PeekMode
import com.family.puzzlepop.R
import com.family.puzzlepop.kid.CountdownBadge
import com.family.puzzlepop.kid.EnsureKidModeOn
import com.family.puzzlepop.kid.PlayTimeKeeper.Status
import com.family.puzzlepop.kid.rememberKidTimeViewModel
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.DisposableEffect
import kotlinx.coroutines.delay

/** @param kid kid mode: big simple toolbar (Peek, My pictures), toddler snapping, no tray, Back does nothing. */
@Composable
fun PlayScreen(
    puzzleId: String,
    onHome: () -> Unit,
    onPlayAgain: (String) -> Unit,
    onNewPhoto: () -> Unit,
    kid: Boolean = false,
) {
    val context = LocalContext.current
    val container = (context.applicationContext as PuzzlePopApp).container
    val vm: PlayViewModel = viewModel(
        key = "play-$puzzleId-$kid",
        factory = PlayViewModel.factory(puzzleId, container, context, kid),
    )
    val ui by vm.ui.collectAsState()
    val peeking = rememberUpdatedState(ui.peeking)
    val opts by vm.options.collectAsState()
    val peeksLeft by vm.peeksLeft.collectAsState()
    val boxShown by vm.boxShown.collectAsState()
    val boxPos by vm.boxPos.collectAsState()
    var avoid by remember { mutableStateOf<List<Rect>>(emptyList()) }

    val timeVm = rememberKidTimeViewModel()
    val timeStatus by timeVm.status.collectAsState()
    // Kid time: counts only while this kid puzzle is on screen and resumed.
    LifecycleResumeEffect(vm) {
        vm.onResume()
        if (kid) timeVm.setPlaying(true)
        onPauseOrDispose {
            vm.onPause()
            if (kid) timeVm.setPlaying(false)
        }
    }
    if (kid) {
        LaunchedEffect(ui.placed, ui.total, ui.rendered != null, ui.complete) {
            // A finished puzzle reports nothing, so its last-piece celebration is not cut short by a break.
            if (ui.rendered != null && !ui.complete) timeVm.piecesLeft(ui.total - ui.placed)
        }
        val activity = LocalActivity.current
        // Leaving the puzzle: forget its piece count (not on rotation, which also disposes this screen).
        DisposableEffect(Unit) { onDispose { if (activity?.isChangingConfigurations != true) timeVm.piecesLeft(Int.MAX_VALUE) } }
        // A finished puzzle may be enjoyed for a while; then (or as soon as she leaves it) 0 pieces left is
        // reported so a limit that was waiting for the finish takes effect.
        LaunchedEffect(ui.complete) {
            if (ui.complete) {
                delay(FINISHED_CARD_MS)
                timeVm.piecesLeft(0)
            }
        }
        val breakNow = timeStatus is Status.OnBreak || timeStatus is Status.DoneForToday
        val napNow by timeVm.napping.collectAsState()
        LaunchedEffect(napNow) { if (napNow) vm.saveNow() } // the screen is going black for a while: save first
        LaunchedEffect(breakNow) { if (breakNow) vm.saveNow() } // the break screen covers the puzzle: save first
    }
    // The helper's timer must not run (and a hint comes down) while a nap or break screen, or the done card, covers the puzzle.
    val napping by timeVm.napping.collectAsState()
    val helperHold = ui.complete || (kid && (napping || timeStatus is Status.OnBreak || timeStatus is Status.DoneForToday))
    LaunchedEffect(helperHold) { vm.setHelperPaused(helperHold) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.onStop() }
    val leaveFinished = { if (kid && ui.complete) timeVm.piecesLeft(0) }
    val currentLeaveFinished by rememberUpdatedState(leaveFinished)
    LaunchedEffect(vm) { vm.newPuzzle.collect { currentLeaveFinished(); onPlayAgain(it) } }
    val goHome = {
        traced("Home.click") {
            vm.saveIfChanged()
            leaveFinished()
            onHome()
        }
    }
    if (kid) {
        BackHandler {} // she can't back out of kid mode
        EnsureKidModeOn()
    } else {
        BackHandler(onBack = goHome)
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        PlayToolbar(
            kid = kid,
            // No "Finish your puzzle!" note on a puzzle that is already finished.
            timeStatus = if (ui.complete && timeStatus is Status.FinishThenBreak) Status.Playing else timeStatus,
            placed = ui.placed,
            total = ui.total,
            ready = ui.rendered != null,
            onHome = goHome,
            onPeek = { on -> if (!on) vm.setPeek(false) else if (vm.tryPeek()) vm.setPeek(true) },
            hintShown = !kid && opts.hintButton && !ui.complete,
            onHint = vm::requestHint,
            peekHidden = opts.peek == PeekMode.NEVER,
            peeksLeft = peeksLeft,
            // Grown-up mode: the Box button always shows, and shows or hides the box picture.
            boxButtonShown = !kid,
            boxShown = boxShown,
            onBox = { if (boxShown) vm.hideBox() else vm.showBox() },
            onDebugSolve = if (BuildConfig.DEBUG) vm::debugSolveStep else null,
            timerShown = !kid && opts.timer,
            elapsedNow = vm::elapsedNow,
            timerRunning = !ui.complete,
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            val rendered = ui.rendered
            when {
                ui.missing -> MissingPuzzle(onHome = onHome)
                rendered == null -> LoadingPuzzle(ui.loading ?: 0f)
                else -> PuzzleCanvas(vm, rendered, peeking = { peeking.value }, modifier = Modifier.fillMaxSize(), showTray = opts.showTray, kid = kid,
                    // Only the kid's fixed card keeps table out from under it; the grown-up's card goes anywhere.
                    boxCardAspect = if (boxShown && kid) rendered.ghost.let { it.width.toFloat() / it.height } else 0f,
                    onAvoid = { avoid = it })
            }
            if (rendered != null && boxShown) {
                BoxPicture(
                    image = rendered.ghost,
                    pos = boxPos?.takeIf { !kid }?.let { Offset(it.x, it.y) },
                    movable = opts.boxMovable,
                    onMove = { vm.moveBox(it.x, it.y) },
                    onHide = if (kid) null else vm::hideBox,
                    avoid = avoid,
                    free = !kid,
                )
            }
            if (ui.complete && rendered != null) {
                if (ui.celebrate) {
                    if (ui.bigParty) Celebration(Modifier.fillMaxSize()) else Confetti(Modifier.fillMaxSize())
                }
                var showCard by remember { mutableStateOf(!ui.celebrate) }
                LaunchedEffect(Unit) {
                    delay(700) // let the last snap and bounce land first
                    showCard = true
                }
                DoneOverlay(
                    visible = showCard,
                    elapsedMs = ui.elapsedMs,
                    moves = ui.moves,
                    newBest = ui.newBest,
                    creatingAgain = ui.creatingAgain,
                    againFailed = ui.againFailed,
                    onPlayAgain = vm::playAgain,
                    onNewPhoto = { leaveFinished(); onNewPhoto() },
                    onHome = goHome,
                    kid = kid,
                )
            }
        }
    }
}

@Composable
private fun PlayToolbar(
    kid: Boolean,
    timeStatus: Status,
    placed: Int,
    total: Int,
    ready: Boolean,
    onHome: () -> Unit,
    onPeek: (Boolean) -> Unit,
    hintShown: Boolean,
    onHint: () -> Unit,
    peekHidden: Boolean,
    peeksLeft: Int?,
    boxButtonShown: Boolean,
    boxShown: Boolean,
    onBox: () -> Unit,
    onDebugSolve: (() -> Unit)?,
    timerShown: Boolean,
    elapsedNow: () -> Long,
    timerRunning: Boolean,
) {
    Surface(tonalElevation = 2.dp, shadowElevation = 2.dp) {
      // A narrow screen (a tablet in portrait, a phone) gets compact buttons: icon with a small label under it.
      BoxWithConstraints(Modifier.fillMaxWidth()) {
       // A phone held sideways: a slim bar (small icon beside its label) so the board keeps its height.
       val short = isShortLandscape()
       val compact = !kid && !short && maxWidth < COMPACT_TOOLBAR_BELOW
       // A phone held upright: not even icon-over-label buttons fit, so the grown-up buttons show icons only.
       val iconOnly = !kid && maxWidth < ICON_ONLY_TOOLBAR_BELOW
       val barButton = if (short) 44.dp else 56.dp
       CompositionLocalProvider(LocalCompactToolbar provides compact, LocalShortToolbar provides short, LocalIconOnlyToolbar provides iconOnly) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (iconOnly) 4.dp else if (compact) 6.dp else 12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.displayCutout))
                .padding(horizontal = 12.dp, vertical = if (short) 2.dp else 6.dp),
        ) {
            if (kid) {
                // Kid mode: a big friendly "My pictures" (no gate) and Peek; nothing else to tap.
                FilledTonalButton(onClick = onHome, modifier = Modifier.heightIn(min = if (short) 52.dp else 72.dp)) {
                    IconLabel(R.drawable.ic_gallery, stringResource(R.string.kid_my_pictures), if (short) 20.sp else 24.sp)
                }
            } else {
                FilledTonalButton(onClick = onHome, modifier = Modifier.heightIn(min = barButton).then(iconOnlyWidth()), contentPadding = toolbarPadding()) {
                    IconLabel(R.drawable.ic_home, stringResource(R.string.play_home), 18.sp)
                }
            }
            Spacer(Modifier.weight(1f))
            if (kid) CountdownBadge(timeStatus)
            if (ready && !kid) {
                val a11y = stringResource(R.string.play_pieces_placed_a11y, placed, total)
                Text(
                    text = stringResource(R.string.play_pieces_placed, placed, total),
                    style = if (iconOnly) MaterialTheme.typography.titleMedium else if (compact || short) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
                    maxLines = 1,
                    softWrap = false,
                    fontWeight = FontWeight.Bold,
                    // Debug builds: long-press the count to solve the top half, then again for all but one.
                    // A hidden gesture rather than a button, so debug screens look like release (store screenshots).
                    modifier = Modifier.semantics { contentDescription = a11y }.then(
                        if (onDebugSolve != null) {
                            Modifier.pointerInput(Unit) {
                                detectTapGestures(onLongPress = { onDebugSolve() })
                            }
                        } else Modifier,
                    ),
                )
            }
            if (ready && timerShown) ToolbarTimer(elapsedNow, timerRunning)
            Spacer(Modifier.weight(1f))
            if (ready && hintShown) {
                FilledTonalButton(onClick = onHint, modifier = Modifier.heightIn(min = barButton).then(iconOnlyWidth()), contentPadding = toolbarPadding()) {
                    IconLabel(R.drawable.ic_hint, stringResource(R.string.play_hint), 18.sp)
                }
            }
            if (ready && boxButtonShown) {
                // The icon shows what is now: a closed box (picture hidden) or its lid propped up (showing).
                val cd = stringResource(if (boxShown) R.string.cd_box_hide else R.string.cd_box_show)
                FilledTonalButton(
                    onClick = onBox,
                    modifier = Modifier.heightIn(min = barButton).then(iconOnlyWidth()).semantics { contentDescription = cd },
                    contentPadding = toolbarPadding(),
                ) {
                    IconLabel(if (boxShown) R.drawable.ic_box_open else R.drawable.ic_box_closed, stringResource(R.string.play_box), 18.sp)
                }
            }
            if (ready && !peekHidden) PeekButton(onPeek, peeksLeft)
        }
       }
      }
    }
}

/** Below this toolbar width the grown-up buttons stack their label under the icon. */
private val COMPACT_TOOLBAR_BELOW = 840.dp
private val LocalCompactToolbar = staticCompositionLocalOf { false }
private val LocalShortToolbar = staticCompositionLocalOf { false }
private val LocalIconOnlyToolbar = staticCompositionLocalOf { false }

/** Below this toolbar width (a phone held upright) the grown-up buttons drop their labels. */
private val ICON_ONLY_TOOLBAR_BELOW = 480.dp
private val CompactButtonPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)

/** A small m:ss clock. It ticks once a second in its own composable, so the canvas never recomposes. */
@Composable
private fun ToolbarTimer(elapsedNow: () -> Long, running: Boolean) {
    var text by remember { mutableStateOf(formatElapsed(elapsedNow())) }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    LaunchedEffect(running, lifecycleOwner) {
        // The loop only runs while the screen is visible (started); it stops when the app is in the background.
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                text = formatElapsed(elapsedNow())
                if (!running) break
                delay(1000)
            }
        }
    }
    val a11y = stringResource(R.string.play_timer_a11y)
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.semantics { contentDescription = "$a11y $text" },
    )
}

/** Hold to see the whole picture; let go to hide it again. */
@Composable
private fun PeekButton(onPeek: (Boolean) -> Unit, peeksLeft: Int?) {
    val used = peeksLeft == 0
    // Read inside the gesture: the last peek drops this to 0 on press and must not restart the gesture.
    val usedNow = rememberUpdatedState(used)
    var pressed by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = if (pressed) colors.secondary else colors.secondaryContainer,
        contentColor = if (pressed) colors.onSecondary else colors.onSecondaryContainer,
        modifier = Modifier
            .heightIn(min = if (LocalShortToolbar.current) 44.dp else 56.dp)
            .widthIn(min = if (LocalIconOnlyToolbar.current) 56.dp else if (LocalCompactToolbar.current) 72.dp else if (LocalShortToolbar.current) 96.dp else 120.dp)
            .alpha(if (used && !pressed) 0.5f else 1f)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    if (usedNow.value) return@awaitEachGesture
                    down.consume()
                    pressed = true
                    onPeek(true)
                    try {
                        // Hold means hold: keep peeking until this finger is really up, even if it
                        // drifts off the button (toddlers wander). Cancellation ends it via the finally.
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            change.consume()
                        }
                    } finally {
                        pressed = false
                        onPeek(false)
                    }
                }
            },
    ) {
        val compact = LocalCompactToolbar.current
        val short = LocalShortToolbar.current
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = if (compact || short) 12.dp else 20.dp, vertical = if (compact) 4.dp else if (short) 8.dp else 14.dp)) {
            val label = when {
                pressed -> stringResource(R.string.play_peek)
                used -> stringResource(R.string.play_peek_none_left)
                peeksLeft != null -> stringResource(R.string.play_peek_left, peeksLeft)
                else -> stringResource(R.string.play_peek)
            }
            IconLabel(if (pressed) R.drawable.ic_peek else R.drawable.ic_peek_hidden, label, 18.sp)
        }
    }
}

@Composable
private fun LoadingPuzzle(progress: Float) {
    val wiggle by rememberInfiniteTransition(label = "loading").animateFloat(
        initialValue = -12f,
        targetValue = 12f,
        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
        label = "wiggle",
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
        modifier = Modifier.fillMaxSize(),
    ) {
        Icon(
            painterResource(R.drawable.ic_puzzle_piece),
            contentDescription = stringResource(R.string.cd_loading_puzzle),
            tint = Color.Unspecified,
            modifier = Modifier.size(96.dp).graphicsLayer { rotationZ = wiggle },
        )
        Text(stringResource(R.string.play_loading), style = MaterialTheme.typography.headlineSmall)
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.width(320.dp))
    }
}

@Composable
private fun MissingPuzzle(onHome: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
        modifier = Modifier.fillMaxSize().padding(24.dp),
    ) {
        Icon(
            painterResource(R.drawable.ic_peek_hidden),
            contentDescription = stringResource(R.string.cd_puzzle_hiding),
            tint = Color.Unspecified,
            modifier = Modifier.size(96.dp),
        )
        Text(
            stringResource(R.string.play_missing),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onHome, modifier = Modifier.heightIn(min = 56.dp)) {
            Text(stringResource(R.string.play_home), fontSize = 18.sp)
        }
    }
}

/**
 * The "You did it!" card, docked at the bottom so the finished picture stays visible above it (kept out
 * of the Column so the plain AnimatedVisibility applies).
 */
@Composable
private fun DoneOverlay(
    visible: Boolean,
    elapsedMs: Long,
    moves: Int,
    newBest: Boolean,
    creatingAgain: Boolean,
    againFailed: Boolean,
    onPlayAgain: () -> Unit,
    onNewPhoto: () -> Unit,
    onHome: () -> Unit,
    kid: Boolean,
) {
    Box(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars).padding(16.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn() + slideInVertically { it / 2 } + scaleIn(initialScale = 0.9f),
        ) {
            DoneCard(elapsedMs, moves, newBest, creatingAgain, againFailed, onPlayAgain, onNewPhoto, onHome, kid)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DoneCard(
    elapsedMs: Long,
    moves: Int,
    newBest: Boolean,
    creatingAgain: Boolean,
    againFailed: Boolean,
    onPlayAgain: () -> Unit,
    onNewPhoto: () -> Unit,
    onHome: () -> Unit,
    kid: Boolean,
) {
    Card(
        shape = RoundedCornerShape(32.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        // Title and time sit above the buttons; the buttons wrap onto a second line when the card is narrow.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Image(
                    painterResource(R.drawable.ic_celebrate),
                    contentDescription = stringResource(R.string.cd_celebrate),
                    modifier = Modifier.size(56.dp),
                )
                Column {
                    Text(
                        stringResource(R.string.play_done_title),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (againFailed) {
                        Text(
                            stringResource(R.string.play_again_failed),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.widthIn(max = 260.dp),
                        )
                    } else if (!kid) {
                        Text(
                            stringResource(R.string.play_done_time, formatElapsed(elapsedMs)) + "\n" +
                                stringResource(R.string.play_done_moves, moves),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (newBest) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(painterResource(R.drawable.ic_trophy), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(28.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    stringResource(R.string.play_new_best),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.tertiary,
                                )
                            }
                        }
                    }
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = onPlayAgain, enabled = !creatingAgain, modifier = Modifier.heightIn(min = 64.dp)) {
                    IconLabel(R.drawable.ic_play_again, stringResource(R.string.play_again), 20.sp)
                }
                if (kid) {
                    // Kid mode: only "again" and "My pictures"; no time, no new-photo step.
                    FilledTonalButton(onClick = onHome, modifier = Modifier.heightIn(min = 72.dp)) {
                        IconLabel(R.drawable.ic_gallery, stringResource(R.string.kid_my_pictures), 24.sp)
                    }
                } else {
                    FilledTonalButton(onClick = onNewPhoto, modifier = Modifier.heightIn(min = 64.dp)) {
                        IconLabel(R.drawable.ic_new_photo, stringResource(R.string.play_new_photo), 20.sp)
                    }
                    FilledTonalButton(onClick = onHome, modifier = Modifier.heightIn(min = 64.dp)) {
                        IconLabel(R.drawable.ic_home, stringResource(R.string.play_home), 20.sp)
                    }
                }
            }
        }
    }
}

/** A button label: icon then text. The icon is decorative; the text already names the action. */
@Composable
private fun IconLabel(@DrawableRes icon: Int, text: String, fontSize: TextUnit) {
    if (LocalIconOnlyToolbar.current) {
        // No room for words: the label is still read out by screen readers.
        Icon(painterResource(icon), contentDescription = text, tint = Color.Unspecified, modifier = Modifier.size(28.dp))
    } else if (LocalCompactToolbar.current) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(painterResource(icon), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(26.dp))
            Text(text, fontSize = 12.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
        }
    } else if (LocalShortToolbar.current) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(icon), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(6.dp))
            Text(text, fontSize = minOf(fontSize.value, 15f).sp, maxLines = 1, softWrap = false)
        }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(icon), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(10.dp))
            Text(text, fontSize = fontSize, maxLines = 1, softWrap = false)
        }
    }
}

@Composable
private fun toolbarPadding(): PaddingValues =
    if (LocalIconOnlyToolbar.current) PaddingValues(horizontal = 10.dp, vertical = 4.dp)
    else if (LocalCompactToolbar.current || LocalShortToolbar.current) CompactButtonPadding
    else ButtonDefaults.ContentPadding

/** Icon-only buttons drop the button's default 58dp minimum width so four of them fit on a phone. */
@Composable
private fun iconOnlyWidth(): Modifier = if (LocalIconOnlyToolbar.current) Modifier.widthIn(min = 48.dp) else Modifier

/** "m:ss", or "h:mm:ss" from an hour on. */
internal fun formatElapsed(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** How long a finished kid puzzle may sit on its "You did it" card before a waiting limit applies. */
private const val FINISHED_CARD_MS = 20_000L
