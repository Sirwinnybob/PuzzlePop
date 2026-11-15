package com.family.puzzlepop.play

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.family.puzzlepop.AppContainer
import com.family.puzzlepop.BuildConfig
import com.family.puzzlepop.data.PhotoRepository
import com.family.puzzlepop.data.PuzzleRepository
import com.family.puzzlepop.data.decodeSampled
import com.family.puzzlepop.engine.Box
import com.family.puzzlepop.engine.Pt
import com.family.puzzlepop.engine.PuzzleGenerator
import com.family.puzzlepop.engine.PuzzleLayout
import com.family.puzzlepop.engine.PuzzleSpec
import com.family.puzzlepop.engine.game.DropResult
import com.family.puzzlepop.engine.game.HintPicker
import com.family.puzzlepop.engine.game.PuzzleGame
import com.family.puzzlepop.engine.options.PeekMode
import com.family.puzzlepop.engine.options.PlayOptions
import com.family.puzzlepop.engine.options.RotationMode
import com.family.puzzlepop.engine.save.SavedPuzzle
import com.family.puzzlepop.kid.resolve
import com.family.puzzlepop.render.ColorGroup
import com.family.puzzlepop.render.RenderedPuzzle
import com.family.puzzlepop.render.renderPuzzle
import com.family.puzzlepop.settings.BestsStore
import com.family.puzzlepop.settings.SettingsRepository
import kotlin.math.max
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * What the play screen shows. Changes only on coarse events (load progress, drops, peek), never per
 * touch move — moves go through [PlayViewModel.moveTick] so they redraw without recomposing.
 */
data class PlayUi(
    /** Load progress 0..1 while preparing; null once ready (or missing). */
    val loading: Float? = 0f,
    /** Bumped whenever the game changes structurally (a drop). */
    val version: Int = 0,
    val rendered: RenderedPuzzle? = null,
    val complete: Boolean = false,
    val peeking: Boolean = false,
    val placed: Int = 0,
    val total: Int = 0,
    val elapsedMs: Long = 0,
    /** True when the puzzle was finished during this visit (play the celebration). */
    val celebrate: Boolean = false,
    /** The celebration is the big one (trumpets, fireworks) rather than the simple confetti. */
    val bigParty: Boolean = false,
    /** True when the save or its photo could not be read. Nothing is deleted because of this. */
    val missing: Boolean = false,
    /** True while "Play again" is making the next puzzle (further taps are ignored). */
    val creatingAgain: Boolean = false,
    /** True when the last "Play again" failed; the done card shows a friendly message. */
    val againFailed: Boolean = false,
    /** Drops made, shown on the done card. */
    val moves: Int = 0,
    /** True when this visit's finish beat the saved best (only for the five presets, never in kid mode). */
    val newBest: Boolean = false,
)

/**
 * The piece the helper (or the Hint button) is showing. [needsTurnDeg] is the signed clockwise turn that
 * would stand it upright (0 when it is already upright, or turning is off).
 */
data class HintTarget(val pieceId: Int, val needsTurnDeg: Float, val serial: Int = 0)

class PlayViewModel(
    val puzzleId: String,
    private val photos: PhotoRepository,
    private val puzzles: PuzzleRepository,
    private val settings: SettingsRepository,
    private val bests: BestsStore,
    appContext: Context,
    /** Kid mode: toddler snapping, no tray (any tray pieces are put back on the table when it opens). */
    val kid: Boolean = false,
    /**
     * The hidden speed test's mode: nothing is loaded from or saved to disk. The game is handed over with
     * [attachEphemeral]; with no stored save, every save path ([saveNow] and friends) returns at once.
     */
    private val ephemeral: Boolean = false,
) : ViewModel() {

    private val _ui = MutableStateFlow(PlayUi())
    val ui: StateFlow<PlayUi> = _ui.asStateFlow()

    private var gameRef: PuzzleGame? = null
    private var saved: SavedPuzzle? = null
    private var tolerance = 0f

    private val _options = MutableStateFlow(LEGACY_EFFECTIVE)

    /** The options that apply to this visit (saved options after kid mode's override). Valid once loaded. */
    val options: StateFlow<EffectiveOptions> = _options.asStateFlow()

    private val _peeksLeft = MutableStateFlow<Int?>(null)

    /** Peeks still allowed when the puzzle limits them; null when there is no limit (or not loaded). */
    val peeksLeft: StateFlow<Int?> = _peeksLeft.asStateFlow()

    private val helperTimer = HelperTimer(20_000L)
    private var helperPaused = false
    private var helperJob: Job? = null
    private var hintExpiry: Job? = null
    private var hintSerial = 0

    private val _hint = MutableStateFlow<HintTarget?>(null)

    /** The piece being highlighted right now, or null. Stays through drags; cleared on any lock/join, a tray move, or a pause. */
    val hint: StateFlow<HintTarget?> = _hint.asStateFlow()

    /** The same as [hint], as snapshot state, so draw lambdas follow it without recomposing. */
    val hintNow = mutableStateOf<HintTarget?>(null)

    /** Set by the canvas: is this piece's centre on screen (grown-up and kid camera)? Null = treat all as visible. */
    var pieceVisible: ((Int) -> Boolean)? = null

    /** Set by the canvas: is this piece's group being held or still settling from a drop? Never hinted. */
    var pieceBusy: ((Int) -> Boolean)? = null

    /**
     * The helper timer must not count while a nap, break or done card covers the puzzle. Pausing also
     * takes down a hint that is showing.
     */
    fun setHelperPaused(paused: Boolean) {
        helperPaused = paused
        applyTimerPause()
        if (paused) clearHint()
    }

    private fun applyTimerPause() {
        val now = SystemClock.uptimeMillis()
        if (helperPaused || !isResumed) helperTimer.pause(now) else helperTimer.resume(now)
    }

    /** Every 500 ms: if the helper is on and the timer is due, show a hint. Runs for the life of the screen. */
    private fun startHelperLoop() {
        helperJob?.cancel()
        helperJob = viewModelScope.launch {
            while (true) {
                delay(HELPER_POLL_MS)
                val o = _options.value
                val g = gameRef ?: continue
                if (!o.helper || g.isComplete || _hint.value != null) continue
                helperTimer.delayMs = o.helperDelayMs
                val now = SystemClock.uptimeMillis()
                if (!helperTimer.isDue(now)) continue
                // Nothing suitable to show (all hidden or covered): wait another full delay before trying again.
                if (!showHint()) helperTimer.reset(now)
            }
        }
    }

    /** The Hint button: shows a hint now (whether or not the helper is on). */
    fun requestHint() {
        if (gameRef?.isComplete != false) return
        showHint()
    }

    /** Takes the highlight down (its piece was picked up, or something locked or joined). */
    fun clearHint() {
        hintExpiry?.cancel()
        hintExpiry = null
        // Taken down without progress (picked up, timed out, paused): the helper may help again after a full delay.
        if (_hint.value != null) helperTimer.reset(SystemClock.uptimeMillis())
        if (_hint.value != null) _hint.value = null
        if (hintNow.value != null) hintNow.value = null
    }

    /** Picks an easy piece and shows it; false if nothing qualifies. Plays the chime. */
    private fun showHint(): Boolean {
        val g = gameRef ?: return false
        val covered = coveredPieces(g)
        val visible = pieceVisible
        val busy = pieceBusy
        val blocked = { it: Int -> covered[it] || busy?.invoke(it) == true }
        var id = HintPicker.pick(g, { visible?.invoke(it) ?: true }, blocked)
        // Grown-up mode may pan to a piece that is off screen (kid mode already judges visibility at the fitted view).
        if (id == null && !kid) id = HintPicker.pick(g, { true }, blocked)
        if (id == null) return false
        val rotMode = _options.value.rotation
        val turn = if (rotMode == RotationMode.OFF) 0f else turnToUpright(g.rotation(id), clockwiseOnly = rotMode == RotationMode.QUARTER)
        val target = HintTarget(id, turn, ++hintSerial)
        _hint.value = target
        hintNow.value = target
        helperTimer.shown(SystemClock.uptimeMillis())
        sounds.hint()
        // The highlight stays until the piece is placed (locks or joins), or a pause / tray move takes it down.
        return true
    }

    /** True for a loose piece with another loose piece (of a different group) above it whose box overlaps. */
    private fun coveredPieces(g: PuzzleGame): BooleanArray {
        val pieces = g.layout.pieces
        val covered = BooleanArray(pieces.size)
        val z = g.zOrder
        for (a in 0 until z.size) {
            val lo = z[a]
            if (g.isLocked(lo) || g.isInTray(lo)) continue
            val lx = g.x(lo)
            val ly = g.y(lo)
            val lr = lx + pieces[lo].bounds.width
            val lb = ly + pieces[lo].bounds.height
            for (b in a + 1 until z.size) {
                val hi = z[b]
                if (g.isLocked(hi) || g.isInTray(hi) || g.groupId(hi) == g.groupId(lo)) continue
                if (g.x(hi) < lr && g.x(hi) + pieces[hi].bounds.width > lx &&
                    g.y(hi) < lb && g.y(hi) + pieces[hi].bounds.height > ly
                ) {
                    covered[lo] = true
                    break
                }
            }
        }
        return covered
    }

    /** Signed clockwise degrees (-180..180) that stand a piece turned [rot] upright; 0 within the lock tolerance. */
    private fun turnToUpright(rot: Float, clockwiseOnly: Boolean = false): Float {
        var d = (360f - rot) % 360f
        // A tap in quarter mode only ever turns clockwise, so the arrow shows the clockwise way round.
        if (d > 180f && !clockwiseOnly) d -= 360f
        if (clockwiseOnly && d > 360f - UPRIGHT_TOL_DEG) return 0f
        return if (kotlin.math.abs(d) <= UPRIGHT_TOL_DEG) 0f else d
    }

    /** The live game; only valid once [PlayUi.rendered] is non-null. Read by the canvas every frame. */
    val game: PuzzleGame get() = checkNotNull(gameRef) { "puzzle not loaded yet" }

    /** The base table ([tableFor]); the canvas grows it to fill the screen. Valid once loaded. */
    var table: Box = Box(0f, 0f, 1f, 1f)
        private set

    /** Bumped on every piece move; read only inside draw lambdas, so a move invalidates drawing only. */
    val moveTick = mutableIntStateOf(0)

    /** Bumped when locks, groups or z-order change (the resting board must be redrawn). */
    val boardTick = mutableIntStateOf(0)

    /** Bumped when the tray's contents or filter change (the tray layer must be redrawn). Draw-only read. */
    val trayTick = mutableIntStateOf(0)

    /** How many pieces are in the tray (all of them, whatever the filter); shown on the collapsed handle. */
    val trayCount = mutableIntStateOf(0)

    private val _trayFilter = MutableStateFlow<TrayFilter>(TrayFilter.All)

    /** Which tray pieces are shown. Kept for this visit only. */
    val trayFilter: StateFlow<TrayFilter> = _trayFilter.asStateFlow()

    private val _traySortByColor = MutableStateFlow(false)

    /** The tray lists pieces by colour instead of in the order they were stored. Kept for this visit only. */
    val traySortByColor: StateFlow<Boolean> = _traySortByColor.asStateFlow()

    private val _trayGroupMask = MutableStateFlow(0)

    /** Bit [ColorGroup.ordinal] is set when some piece in the tray has that colour group. */
    val trayGroupMask: StateFlow<Int> = _trayGroupMask.asStateFlow()

    private val _plusOne = MutableStateFlow(0)

    /** Counts pieces stored while the filter hides them; the active filter chip flashes "+1" on each change. */
    val plusOne: StateFlow<Int> = _plusOne.asStateFlow()

    /** Per-piece colour group ordinal and whether it is an edge, for the tray filter. Set once loaded. */
    var pieceColorGroup: IntArray = IntArray(0)
        private set
    var pieceIsEdge: BooleanArray = BooleanArray(0)
        private set

    /** Piece ids ordered by colour group, then hue: the tray order when sorting by colour. */
    var colorOrder: IntArray = IntArray(0)
        private set

    private val _trayCollapsed = MutableStateFlow(false)

    /** The tray is folded down to its handle. Remembered across puzzles and restarts. */
    val trayCollapsed: StateFlow<Boolean> = _trayCollapsed.asStateFlow()

    private val sounds = SoundFx(appContext)
    private var bigParty = true

    // Saves run outside viewModelScope so leaving the screen can't cancel a write half-way. At most
    // one write is in flight; requests that arrive meanwhile coalesce into one more write afterwards.
    private val saveScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var saveLoop: Job? = null
    private var savePending = false

    /** True when the game changed since the last snapshot was taken. */
    private var dirty = false
    private var debounceJob: Job? = null
    private var periodicJob: Job? = null

    /** Elapsed play time at the last snapshot, so [saveIfChanged] can tell whether anything new happened. */
    private var savedElapsed = 0L

    private val _newPuzzle = Channel<String>(Channel.BUFFERED)

    /** The id of a puzzle made by [playAgain]; the screen navigates to it. */
    val newPuzzle = _newPuzzle.receiveAsFlow()

    private var isResumed = false
    private var resumedAt = -1L

    init {
        if (!ephemeral) {
            viewModelScope.launch { load() }
            viewModelScope.launch { settings.trayCollapsed.collect { _trayCollapsed.value = it } }
            viewModelScope.launch { settings.bigCelebration.collect { bigParty = it } }
        }
    }

    /** Speed test only: lets go of the current pieces (before a rebuild) so two sets are never in memory. */
    fun clearEphemeral() {
        gameRef = null
        _ui.update { it.copy(rendered = null) }
    }

    /** Speed test only: shows an in-memory [game] with grown-up rules. Nothing is persisted (no [saved]). */
    fun attachEphemeral(game: PuzzleGame, rendered: RenderedPuzzle) {
        val layout = game.layout
        for (piece in rendered.pieces) piece.asAndroidBitmap().setHasMipMap(true)
        rendered.ghost.asAndroidBitmap().setHasMipMap(true)
        table = tableFor(layout)
        _options.value = effectiveOptions(PlayOptions(), false, com.family.puzzlepop.settings.KidSettings(), layout)
        pieceColorGroup = rendered.colorGroup
        pieceIsEdge = BooleanArray(layout.pieces.size) { layout.pieces[it].isEdge }
        colorOrder = (0 until layout.pieces.size)
            .sortedWith(compareBy({ rendered.colorGroup[it] }, { rendered.hue[it] })).toIntArray()
        gameRef = game
        trayChanged()
        _ui.update { it.copy(loading = null, rendered = rendered, total = layout.pieces.size, version = it.version + 1) }
    }

    private suspend fun load() {
        try {
            loadPuzzle()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failLoad(e)
        } catch (e: OutOfMemoryError) {
            failLoad(e)
        }
    }

    /** Anything unexpected while loading shows the friendly screen; nothing on disk is touched. */
    private fun failLoad(e: Throwable) {
        Log.e(TAG, "loading puzzle $puzzleId failed", e)
        gameRef = null
        _ui.update { it.copy(loading = null, rendered = null, missing = true) }
    }

    private suspend fun loadPuzzle() {
        val save = puzzles.load(puzzleId)
        val photo = save?.let { photos.get(it.photoId) }
        if (save == null || photo == null) {
            Log.w(TAG, "puzzle $puzzleId: save=${save != null} photo=${photo != null}")
            _ui.update { it.copy(loading = null, missing = true) }
            return
        }
        val bitmap = try {
            decodeSampled(photos.photoFile(photo), MAX_PHOTO_SIDE)
        } catch (e: Exception) {
            Log.w(TAG, "photo decode failed", e)
            _ui.update { it.copy(loading = null, missing = true) }
            return
        }
        _ui.update { it.copy(loading = 0.05f) }
        val layout: PuzzleLayout
        val game: PuzzleGame
        val rendered: RenderedPuzzle
        var renderMs = 0L
        try {
            layout = withContext(Dispatchers.Default) { PuzzleGenerator.generate(save.spec) }
            game = PuzzleGame(layout, save.state)
            _ui.update { it.copy(loading = 0.1f) }
            // onProgress arrives on worker threads, possibly out of order: update() is atomic, keep the max.
            val t0 = SystemClock.uptimeMillis()
            rendered = renderPuzzle(bitmap, layout) { p ->
                _ui.update { s -> s.copy(loading = max(s.loading ?: 0f, 0.1f + 0.9f * p)) }
            }
            renderMs = SystemClock.uptimeMillis() - t0
        } finally {
            bitmap.recycle()
        }
        // Mipmaps keep zoomed-out pieces from shimmering (the GPU builds them once on upload).
        for (piece in rendered.pieces) piece.asAndroidBitmap().setHasMipMap(true)
        rendered.ghost.asAndroidBitmap().setHasMipMap(true)

        saved = save
        savedElapsed = game.elapsed
        _boxPos.value = save.boxPos
        table = tableFor(layout, kid)
        val kidSettings = settings.kidSettings.first()
        val eff = effectiveOptions(save.options, kid, kidSettings, layout)
        _options.value = eff
        // Kid mode always shows the box. Otherwise a touched box (it has a saved position) follows its
        // saved hidden flag, and an untouched one follows the difficulty's default.
        _boxShown.value = kid || (if (save.boxPos != null) !save.boxHidden else eff.boxPicture)
        tolerance = eff.tolerance
        gameRef = game
        pieceColorGroup = rendered.colorGroup
        pieceIsEdge = BooleanArray(layout.pieces.size) { layout.pieces[it].isEdge }
        colorOrder = (0 until layout.pieces.size)
            .sortedWith(compareBy({ rendered.colorGroup[it] }, { rendered.hue[it] })).toIntArray()
        // Kid mode has no tray, so a puzzle that was started in grown-up mode with one would be
        // unfinishable: put every tray piece on the table, scattered by the same logic as a new game.
        var emptiedTray = false
        if (kid && game.trayPieces.isNotEmpty()) {
            val before = game.trayPieces.size
            val scattered = PuzzleGame.newGame(layout, table, save.spec.seed)
            for (id in game.trayPieces.toList()) game.takeFromTray(id, scattered.x(id), scattered.y(id))
            emptiedTray = game.trayPieces.size < before // only a real move needs a save
        }
        // A kid never sees rotated pieces: stand them all up (a real change is saved).
        if (kid && game.uprightAllForKid()) emptiedTray = true
        trayChanged()
        refreshPeeksLeft()
        if (isResumed && !game.isComplete) resumedAt = SystemClock.uptimeMillis()
        if (emptiedTray) markChanged()
        helperTimer.delayMs = eff.helperDelayMs
        helperTimer.reset(SystemClock.uptimeMillis())
        applyTimerPause()
        startHelperLoop()
        _ui.update {
            it.copy(
                loading = null,
                rendered = rendered,
                complete = game.isComplete,
                placed = game.placedCount,
                total = layout.pieces.size,
                elapsedMs = game.elapsed,
                moves = game.moveCount,
                version = it.version + 1,
            )
        }
        Log.i(TAG, "loaded $puzzleId: ${layout.pieces.size} pieces, render scale ${rendered.scale}, render ${renderMs} ms (colour sampling ${rendered.colorMillis} ms of worker time)")
    }

    fun onDragStart(pieceId: Int) {
        val g = gameRef ?: return
        if (g.isInTray(pieceId)) return
        g.bringToFront(pieceId)
        boardTick.intValue++
        moveTick.intValue++
    }

    fun onDrag(pieceId: Int, dxWorld: Float, dyWorld: Float) {
        val g = gameRef ?: return
        if (dxWorld == 0f && dyWorld == 0f) return
        g.moveGroup(pieceId, dxWorld, dyWorld)
        moveTick.intValue++
    }

    /** Turns the group of [pieceId] by [deltaDeg] (clockwise on screen) and marks the game changed. */
    fun rotate(pieceId: Int, deltaDeg: Float) {
        val g = gameRef ?: return
        g.rotateGroup(pieceId, deltaDeg)
        moveTick.intValue++
        markChanged()
    }

    /**
     * Turns the group of [pieceId] about the fixed world point ([pivotX], [pivotY]) during a twist, every touch
     * frame: no save bookkeeping here (the twist ends in [onDrop], which marks the change).
     */
    fun rotateLive(pieceId: Int, deltaDeg: Float, pivotX: Float, pivotY: Float) {
        val g = gameRef ?: return
        if (deltaDeg == 0f) return
        g.rotateGroupAbout(pieceId, deltaDeg, pivotX, pivotY)
        moveTick.intValue++
    }

    /**
     * A tentative drag turned into a pinch: move the group back so [pieceId]'s top-left is at ([startX],
     * [startY]) again. Not a move: no drop, no sound, and the save isn't marked dirty.
     */
    fun cancelDrag(pieceId: Int, startX: Float, startY: Float) {
        val g = gameRef ?: return
        if (g.isInTray(pieceId)) return
        onDrag(pieceId, startX - g.x(pieceId), startY - g.y(pieceId))
    }

    /** Resolves a drop and plays its sound. The caller adds haptics and the bounce. */
    fun onDrop(pieceId: Int, flung: Boolean = false): DropResult {
        val g = gameRef ?: return DropResult.NONE
        // A flung piece (kid mode: let go mid-swipe) just lands where it is: no lock, no join.
        val result = g.drop(pieceId, if (flung) 0f else tolerance)
        when (result) {
            DropResult.LOCKED -> sounds.snap()
            DropResult.JOINED -> sounds.join()
            DropResult.NONE -> Unit
        }
        // The dropped group stays on the moving layer until its drop animation ends (the canvas then
        // bumps boardTick), so only a join, which moves and raises the neighbours already resting,
        // changes the resting layer now. Re-rendering it costs RenderThread ~25 ms at 1000 pieces.
        if (result == DropResult.JOINED) boardTick.intValue++
        moveTick.intValue++
        markChanged()
        if (result != DropResult.NONE) {
            // Progress: take the highlight down and make the helper wait another full delay.
            clearHint()
            helperTimer.reset(SystemClock.uptimeMillis())
            afterStructuralChange()
        }
        return result
    }

    /**
     * Puts a held single piece into the tray (the canvas has already ended its drag). Returns false, and
     * changes nothing, if it can't go in (locked, or part of a group).
     */
    fun storeInTray(pieceId: Int): Boolean {
        val g = gameRef ?: return false
        if (!g.storeInTray(pieceId)) return false
        if (_hint.value?.pieceId == pieceId) clearHint()
        if (pieceId < pieceColorGroup.size &&
            !_trayFilter.value.accepts(pieceIsEdge[pieceId], ColorGroup.entries[pieceColorGroup[pieceId]])
        ) _plusOne.value++
        sounds.tray()
        trayChanged()
        moveTick.intValue++
        markChanged()
        return true
    }

    /** Takes a piece out of the tray onto the table at ([worldX], [worldY]) (its top-left), on top. */
    fun takeFromTray(pieceId: Int, worldX: Float, worldY: Float) {
        val g = gameRef ?: return
        if (!g.isInTray(pieceId)) return
        g.takeFromTray(pieceId, worldX, worldY)
        trayChanged()
        markChanged()
    }

    fun setTrayFilter(f: TrayFilter) {
        if (_trayFilter.value == f) return
        _trayFilter.value = f
        trayTick.intValue++
    }

    fun setTraySortByColor(v: Boolean) {
        if (_traySortByColor.value == v) return
        _traySortByColor.value = v
        trayTick.intValue++
    }

    fun setTrayCollapsed(v: Boolean) {
        _trayCollapsed.value = v
        viewModelScope.launch { settings.setTrayCollapsed(v) }
    }

    private fun trayChanged() {
        trayTick.intValue++
        val tray = gameRef?.trayPieces
        trayCount.intValue = tray?.size ?: 0
        var mask = 0
        if (tray != null) for (k in 0 until tray.size) {
            val id = tray[k]
            if (id < pieceColorGroup.size) mask = mask or (1 shl pieceColorGroup[id])
        }
        _trayGroupMask.value = mask
    }

    private fun afterStructuralChange() {
        val g = gameRef ?: return
        val finishedNow = g.isComplete && !_ui.value.complete
        if (finishedNow) {
            flushElapsed()
            resumedAt = -1L
            if (bigParty) sounds.fanfare() else sounds.finish()
        }
        _ui.update {
            it.copy(
                version = it.version + 1,
                placed = g.placedCount,
                complete = g.isComplete,
                elapsedMs = g.elapsed,
                celebrate = it.celebrate || finishedNow,
                bigParty = if (finishedNow) bigParty else it.bigParty,
            )
        }
        if (finishedNow) {
            saveNow()
            recordBest(g)
        }
    }

    /** Called once, only from the moment a puzzle becomes complete (never on load of an already-finished one). */
    private fun recordBest(g: PuzzleGame) {
        val s = saved ?: return
        val ms = g.elapsed
        val moves = g.moveCount
        _ui.update { it.copy(moves = moves) }
        if (kid) return
        val key = Bests.key(s.photoId, g.layout.pieces.size, s.options.preset) ?: return
        viewModelScope.launch {
            val isNew = bests.record(key, ms, s.photoId)
            _ui.update { it.copy(newBest = isNew) }
        }
    }

    /** Whole play time so far, for the toolbar timer (read once a second; includes the running stretch). */
    fun elapsedNow(): Long {
        val g = gameRef ?: return 0L
        val extra = if (resumedAt >= 0 && !g.isComplete) SystemClock.uptimeMillis() - resumedAt else 0L
        return g.elapsed + extra
    }

    /**
     * A Peek press. Returns false (and counts nothing) if this puzzle's Peek is NEVER or its limit is used up;
     * a LIMITED peek counts one use, saved with the puzzle.
     */
    fun tryPeek(): Boolean {
        val g = gameRef ?: return false
        val o = _options.value
        when (o.peek) {
            PeekMode.NEVER -> return false
            PeekMode.UNLIMITED -> return true
            PeekMode.LIMITED -> {
                if (g.peeksUsedCount >= o.peekLimit) return false
                g.recordPeek()
                refreshPeeksLeft()
                markChanged()
                return true
            }
        }
    }

    private fun refreshPeeksLeft() {
        val g = gameRef ?: return
        val o = _options.value
        _peeksLeft.value = if (o.peek == PeekMode.LIMITED) (o.peekLimit - g.peeksUsedCount).coerceAtLeast(0) else null
    }

    fun setPeek(on: Boolean) {
        if (_ui.value.peeking != on) _ui.update { it.copy(peeking = on) }
    }

    /** Call when the screen is resumed / paused so play time only counts while she's playing. */
    fun onResume() {
        isResumed = true
        applyTimerPause()
        periodicJob?.cancel()
        periodicJob = viewModelScope.launch {
            while (true) {
                delay(PERIODIC_SAVE_MS)
                if (dirty) saveNow()
            }
        }
        val g = gameRef ?: return
        if (resumedAt < 0 && !g.isComplete) resumedAt = SystemClock.uptimeMillis()
    }

    fun onPause() {
        isResumed = false
        applyTimerPause()
        clearHint()
        periodicJob?.cancel()
        periodicJob = null
        flushElapsed()
        resumedAt = -1L
    }

    private fun flushElapsed() {
        val g = gameRef ?: return
        if (resumedAt >= 0) {
            val now = SystemClock.uptimeMillis()
            g.addElapsed(now - resumedAt)
            resumedAt = now
        }
    }

    private val _boxPos = MutableStateFlow<Pt?>(null)
    /** Where the user dragged the box picture (0..1 of the free table area); null = default corner. */
    val boxPos: StateFlow<Pt?> = _boxPos.asStateFlow()
    private val _boxShown = MutableStateFlow(false)
    val boxShown: StateFlow<Boolean> = _boxShown.asStateFlow()

    fun moveBox(x: Float, y: Float) {
        _boxPos.value = Pt(x, y)
        markChanged()
    }

    fun hideBox() {
        if (_boxPos.value == null) _boxPos.value = Pt(-1f, -1f) // touched, default spot: its hidden flag now decides
        _boxShown.value = false
        markChanged()
    }

    fun showBox() {
        if (_boxPos.value == null) _boxPos.value = Pt(-1f, -1f)
        _boxShown.value = true
        markChanged()
    }

    /** A drop happened: save 1 s after the last one (each drop restarts the wait). */
    private fun markChanged() {
        dirty = true
        debounceJob?.cancel()
        debounceJob = viewModelScope.launch {
            delay(DEBOUNCE_SAVE_MS)
            saveNow()
        }
    }

    /** The screen is no longer visible: save, unless nothing has changed (a bare rotation must not touch the save). */
    fun onStop() = saveIfChanged()

    /**
     * Saves when a piece moved since the last save or a few seconds of play time are unsaved. Merely
     * opening a puzzle and leaving (or rotating) writes nothing, so "Keep playing" doesn't reshuffle.
     */
    fun saveIfChanged() = traced("saveIfChanged") {
        val g = gameRef ?: return@traced
        flushElapsed()
        if (dirty || g.elapsed - savedElapsed >= UNSAVED_ELAPSED_MS) saveNow()
    }

    /**
     * Saves the current game now. The snapshot is taken here on the main thread (once per request, never
     * per frame); serialising and writing happen on the IO dispatcher inside [PuzzleRepository.save].
     */
    fun saveNow() {
        if (gameRef == null || saved == null) return
        debounceJob?.cancel()
        flushElapsed()
        savePending = true
        if (saveLoop?.isActive == true) return
        saveLoop = saveScope.launch {
            while (savePending) {
                savePending = false
                val g = gameRef ?: break
                val base = saved ?: break
                val snapshot = traced("Save.snapshot") { base.copy(state = g.state, boxPos = _boxPos.value, boxHidden = if (kid) base.boxHidden else !_boxShown.value) }
                dirty = false
                savedElapsed = g.elapsed
                try {
                    puzzles.save(snapshot)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    dirty = true // try again on the next tick
                    Log.e(TAG, "save failed", e)
                }
            }
        }
    }

    /**
     * Makes a new puzzle from the same photo and settings with a new seed and reports its id on
     * [newPuzzle]. Only one can be in flight (double taps are ignored); a failure just shows a message.
     */
    fun playAgain() {
        val s = saved ?: return
        if (_ui.value.creatingAgain) return
        _ui.update { it.copy(creatingAgain = true, againFailed = false) }
        viewModelScope.launch {
            try {
                val seed = System.nanoTime()
                val id = if (kid) {
                    // Kid mode: the grown-up's chosen shape (surprise resolved once, here), within the grown-up's piece range, all pieces on the table.
                    val k = settings.kidSettings.first()
                    val count = s.spec.targetCount.coerceIn(k.minPieces, max(k.minPieces, k.maxPieces))
                    val spec = PuzzleSpec(s.spec.imageWidth, s.spec.imageHeight, count, k.kidShape.resolve(Random.Default), seed)
                    createPuzzle(puzzles, s.photoId, spec, PlayOptions(helper = true, boxPicture = true), kid = true)
                } else {
                    createPuzzle(puzzles, s.photoId, s.spec.copy(seed = seed), s.options)
                }
                _newPuzzle.send(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.e(TAG, "play again failed", e)
                _ui.update { it.copy(againFailed = true) }
            } finally {
                _ui.update { it.copy(creatingAgain = false) }
            }
        }
    }

    /** Debug builds only: the first use solves the top half, the next all but one (see the play toolbar). */
    fun debugSolveStep() {
        val g = gameRef ?: return
        if (g.placedCount < g.layout.pieces.size / 2) debugSolveTopHalf() else debugSolveAllButOne()
    }

    /** Debug builds only: locks every piece except one group, so the last drop can be tested by hand. */
    fun debugSolveAllButOne() {
        if (!BuildConfig.DEBUG) return
        val g = gameRef ?: return
        val z = g.zOrder
        val keep = (z.size - 1 downTo 0).map { z[it] }.firstOrNull { !g.isLocked(it) && !g.isInTray(it) }
            ?: g.trayPieces.firstOrNull() ?: return
        val keepGroup = g.groupId(keep)
        debugLock { id -> g.groupId(id) != keepGroup }
        Log.i(TAG, "debugSolveAllButOne: left piece $keep (group of ${g.groupMembers(keep).size})")
    }

    /** Debug builds only: locks the top half of the picture, so a puzzle looks well under way (store screenshots). */
    fun debugSolveTopHalf() {
        if (!BuildConfig.DEBUG) return
        val g = gameRef ?: return
        val pieces = g.layout.pieces
        val middle = pieces.map { it.bounds.top }.sorted().let { it[it.size / 2] }
        debugLock { id -> pieces[id].bounds.top < middle }
    }

    /** Moves each unlocked piece that [which] picks to its home and drops it there. */
    private fun debugLock(which: (Int) -> Boolean) {
        val g = gameRef ?: return
        val layout = g.layout
        for (id in layout.pieces.indices) {
            if (g.isLocked(id) || !which(id)) continue
            val home = layout.pieces[id].bounds
            if (g.isInTray(id)) g.takeFromTray(id, home.left, home.top)
            // Locking needs an upright group: stand it up first (rotation puzzles).
            val rot = g.rotation(id)
            if (rot != 0f) g.rotateGroup(id, -rot)
            g.moveGroup(id, home.left - g.x(id), home.top - g.y(id))
            g.drop(id, tolerance)
        }
        trayChanged()
        boardTick.intValue++
        moveTick.intValue++
        _ui.update { it.copy(version = it.version + 1, placed = g.placedCount) }
    }

    override fun onCleared() {
        if (dirty) saveNow() // runs in saveScope, which outlives this ViewModel
        sounds.release()
    }

    companion object {
        private const val TAG = "PuzzlePopPlay"

        /** Placeholder until the save is loaded: the legacy options. */
        private val LEGACY_EFFECTIVE = EffectiveOptions(
            rotation = RotationMode.OFF, ghostAlpha = 0.30f, peek = PeekMode.UNLIMITED, peekLimit = 3,
            tolerance = 0f, magnetRadius = -1f, showTray = true, boxPicture = true, boxMovable = true,
            helper = false, helperDelayMs = 20_000L, hintButton = true, timer = false,
        )
        private const val MAX_PHOTO_SIDE = 4096
        private const val HELPER_POLL_MS = 500L
        private const val UPRIGHT_TOL_DEG = 10f
        private const val DEBOUNCE_SAVE_MS = 1000L
        private const val PERIODIC_SAVE_MS = 10_000L
        private const val UNSAVED_ELAPSED_MS = 3_000L

        fun ephemeralFactory(container: AppContainer, context: Context): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    PlayViewModel(
                        "speedtest", container.photoRepository, container.puzzleRepository, container.settings, container.bests,
                        context.applicationContext, kid = false, ephemeral = true,
                    )
                }
            }

        fun factory(puzzleId: String, container: AppContainer, context: Context, kid: Boolean = false): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    PlayViewModel(
                        puzzleId, container.photoRepository, container.puzzleRepository, container.settings, container.bests,
                        context.applicationContext, kid,
                    )
                }
            }
    }
}
