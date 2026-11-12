package com.family.puzzlepop.play

import android.os.SystemClock
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.util.Log
import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.family.puzzlepop.R
import com.family.puzzlepop.render.ColorGroup
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.roundToInt
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.ui.unit.toSize
import com.family.puzzlepop.BuildConfig
import com.family.puzzlepop.engine.game.DropResult
import com.family.puzzlepop.engine.game.PuzzleGame
import com.family.puzzlepop.render.RenderedPuzzle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlin.math.max

private const val TABLE_COLOR = 0xFFE6E2DC.toInt()
private const val BOARD_COLOR = 0xFFF4F1EC.toInt()
private const val BOARD_OUTLINE_COLOR = 0x40000000
private const val GHOST_ALPHA = 0.30f
private const val KID_GHOST_ALPHA = 0.45f
private const val PEEK_ALPHA = 0.90f
private const val SHADOW_NEAR_ALPHA = 0.30f
private const val SHADOW_FAR_ALPHA = 0.30f
/** Shadow offset (down-right) at full lift, as a fraction of the smaller cell side. */
private const val SHADOW_OFFSET_FRACTION = 0.015f
private const val GLOW_COLOR_RGB = 0x00FFC040
private const val FIT_ANIM_NANOS = 220_000_000L
private const val MAX_REST_ZOOM_DRIFT = 0.25f
private const val OVERSCAN_FRACTION = 0.25f
/** A touch whose contact is longer than this (dp, MotionEvent touchMajor) at touch-down is a palm. */
private const val PALM_TOUCH_MAJOR_DP = 80f
/** Screen space left around the table when the camera fits it. */
private const val FIT_PADDING_DP = 16f
/** Kid mode: how far a two-finger pinch may zoom in, as a multiple of the fitted zoom (never out past fit). */
private const val KID_MAX_ZOOM = 2f

/** Camera and viewport, shared between the gesture handler and the draw lambdas. */
private class TableView {
    /** The live camera. Read by the moving layer's draw and by the resting layer's layer properties. */
    var camera by mutableStateOf<Camera?>(null)

    /**
     * The camera the cached resting layer was last drawn with. During a camera gesture only the layer's
     * scale/translation follow [camera]; its content is re-drawn only when this changes ([rebase]).
     */
    var rest by mutableStateOf<Camera?>(null)

    /** Extra screen pixels the resting layer draws on every side, so a pan doesn't reveal its edge at once. */
    var overscan = 0
    var viewport = Size.Zero
    var fitScale = 1f
    var originInWindow = Offset.Zero
    var cameraAnim: Job? = null
    var liftAnim: Job? = null

    /** Frame time (ns) while a hint shows, so the overlay animates; read only in its draw lambda. */
    var hintClock by mutableLongStateOf(0L)
    /** [hintClock] value at the first frame of the current hint (0 = not started). */
    var hintStart = 0L

    /** Where the tray is (screen space; null until the first size). Changes only with the view size. */
    var tray by mutableStateOf<TrayLayout?>(null)
    /** How far the tray's slots are scrolled (px); clamp with the shown count before use. */
    var trayScroll by mutableFloatStateOf(0f)
    /** The colour dots show in the tray header (it is longer then). */
    var trayColorsOpen by mutableStateOf(false)
    /** A piece that could go in the tray is held over it (outline the tray). */
    var trayHover by mutableStateOf(false)
    /** Mirrors [PlayViewModel.trayCollapsed] as snapshot state, so draw lambdas follow it without recomposing. */
    var trayCollapsed by mutableStateOf(false)

    /**
     * The table pieces are kept on and the camera fits: [PlayViewModel.table] grown to hold every loose piece
     * and to fill the visible table area (see [tableToFill]). Set on each fit; [tableBase] is what it grew from.
     */
    var table = com.family.puzzlepop.engine.Box(0f, 0f, 1f, 1f)
    var tableBase: com.family.puzzlepop.engine.Box? = null

    /** Re-draws the resting layer at the live camera (one full re-record). */
    fun rebase() {
        val c = camera
        if (c != null && c != rest) traced("rebase") { rest = c }
    }

    /**
     * End of a camera gesture: re-draw the resting layer only if the zoom changed or the cached layer no
     * longer covers the view. After a plain pan the cached texture is still exact (it is drawn at a
     * whole-pixel offset), and re-drawing it would stall RenderThread for ~25 ms at 1000 pieces.
     */
    fun rebaseIfNeeded() {
        if (restScale() != 1f || restIsStale()) rebase()
    }

    /** k = live scale / rest scale; the layer is scaled by k about the overscanned node's top-left. */
    fun restScale(): Float {
        val c = camera ?: return 1f
        val r = rest ?: return 1f
        return c.scale / r.scale
    }

    fun restTranslationX(): Float {
        val c = camera ?: return 0f
        val r = rest ?: return 0f
        return c.offsetX + overscan - restScale() * (r.offsetX + overscan)
    }

    fun restTranslationY(): Float {
        val c = camera ?: return 0f
        val r = rest ?: return 0f
        return c.offsetY + overscan - restScale() * (r.offsetY + overscan)
    }

    /**
     * True when the transformed resting layer no longer covers the viewport, or is scaled so far that the
     * cached texture would look soft (or wasteful), so it should be re-drawn at the live camera.
     */
    fun restIsStale(): Boolean {
        val k = restScale()
        if (abs(k - 1f) > MAX_REST_ZOOM_DRIFT) return true
        val m = overscan.toFloat()
        val left = -m + restTranslationX()
        val top = -m + restTranslationY()
        val right = left + k * (viewport.width + 2 * m)
        val bottom = top + k * (viewport.height + 2 * m)
        return left > 0f || top > 0f || right < viewport.width || bottom < viewport.height
    }
}

/** What the speed test can do to a running [PuzzleCanvas] without touch events. */
interface CanvasDriver {
    val camera: Camera?
    val viewport: Size
    fun setCamera(c: Camera)
    /** Lifts [p]'s group as a finger would (call vm.onDragStart first, then vm.onDrag each frame). */
    fun lift(p: Int, grabX: Float, grabY: Float)
    /** Puts the group down where it is, without resolving a drop (no join/lock, nothing saved). */
    fun release(p: Int)
}

/**
 * The play table. Two stacked layers, each with its own render node:
 *  - the resting layer (table, board outline, ghost, locked pieces, then resting pieces by z-order), a
 *    cached GPU texture redrawn only on [PlayViewModel.boardTick] or a camera rebase; camera gestures just
 *    scale/translate the texture;
 *  - the peek overlay, drawn only while peeking;
 *  - the piece tray in screen space (it never moves with the camera; see Tray.kt), redrawn only on
 *    [PlayViewModel.trayTick], its scroll, hover or collapse — never per drag frame;
 *  - the moving layer (lifted groups with shadows, lock press-in and glow), redrawn on
 *    [PlayViewModel.moveTick]. It is above the tray, so a piece dragged to or from the tray stays visible.
 * The counters are read only inside draw lambdas, so moving a piece never recomposes anything.
 * Pieces in the tray keep a stale table position in the z-order: the table layers and hit test skip them.
 *
 * @param showTray false hides the tray altogether (kid mode).
 */
@Composable
fun PuzzleCanvas(
    vm: PlayViewModel,
    rendered: RenderedPuzzle,
    peeking: () -> Boolean,
    modifier: Modifier = Modifier,
    showTray: Boolean = true,
    kid: Boolean = false,
    /** Aspect (w/h) of the box card when it sits at its default corner, else 0: that corner is kept clear of table. */
    boxCardAspect: Float = 0f,
    /** Screen rects the box picture must stay out of (board, tray); updated when the camera settles. */
    onAvoid: (List<androidx.compose.ui.geometry.Rect>) -> Unit = {},
    /** Speed test only: receives a handle that moves the camera and lifts pieces from code (null again on dispose). */
    onDriver: ((CanvasDriver?) -> Unit)? = null,
) {
    val game = vm.game
    val layout = game.layout
    val n = layout.pieces.size
    val density = LocalDensity.current.density
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val view = remember {
        TableView().also {
            it.trayCollapsed = vm.trayCollapsed.value
            it.trayColorsOpen = vm.trayFilter.value is TrayFilter.Color
        }
    }
    val bitmaps: Array<Bitmap> = remember(rendered) { Array(n) { rendered.pieces[it].asAndroidBitmap() } }
    val ghost: Bitmap = remember(rendered) { rendered.ghost.asAndroidBitmap() }
    // World size of each bitmap (it covers its piece's bounds at rendered.scale, rounded up).
    val drawW = remember(rendered) { FloatArray(n) { bitmaps[it].width / rendered.scale } }
    val drawH = remember(rendered) { FloatArray(n) { bitmaps[it].height / rendered.scale } }
    val boundsW = remember(layout) { FloatArray(n) { layout.pieces[it].bounds.width } }
    val boundsH = remember(layout) { FloatArray(n) { layout.pieces[it].bounds.height } }
    val boardW = layout.spec.imageWidth.toFloat()
    val boardH = layout.spec.imageHeight.toFloat()
    if (view.tableBase != vm.table) {
        view.tableBase = vm.table
        view.table = vm.table
    }

    // Pieces drawn on the moving layer instead of the resting one.
    val floating = remember(n) { BooleanArray(n) }
    val floatingPrev = remember(n) { BooleanArray(n) }
    // Pieces of the hinted group: drawn live by the hint overlay instead of the resting layer.
    val hintHide = remember(n) { BooleanArray(n) }
    val turnIcon = remember { ContextCompat.getDrawable(context, R.drawable.ic_turn)?.mutate() }
    val lifts = remember(n) { ArrayList<Lift>() }
    val liftOf = remember(n) { arrayOfNulls<Lift>(n) }
    val cellMin = minOf(layout.cellWidth, layout.cellHeight)
    val masks = remember(rendered) { PieceMasks(bitmaps, rendered.scale, cellMin) }
    val drags = remember { DragSlots() }
    // Pointers that went down on a tray piece and haven't moved yet: grabX/grabY hold the down position (screen).
    val pendingTray = remember { DragSlots() }
    val trayList = remember(n) { TrayList(n) }

    val opts = vm.options.value
    val paints = remember(opts.ghostAlpha) { Paints().also { it.ghost.alpha = (opts.ghostAlpha * 255).toInt() } }
    // Kid mode magnet: a held group that rests (slowly, for a moment) within this world distance of home locks.
    val magnetRadius = opts.magnetRadius
    val rect = remember { RectF() }
    val trayPaints = remember(density) { TrayPaints(density) }
    val localDensity = LocalDensity.current
    val navInsets = WindowInsets.navigationBars
    val navBottom = navInsets.getBottom(localDensity).toFloat()
    val navRight = navInsets.getRight(localDensity, LocalLayoutDirection.current).toFloat()
    /** The tray for the current view size, clear of the navigation bar. */
    fun trayFor(s: Size) = TrayLayout.forViewport(
        s, landscape = s.width > s.height, density = density, bottomInset = navBottom, rightInset = navRight,
        colorsOpen = view.trayColorsOpen,
    )
    LaunchedEffect(navBottom, navRight) { if (view.viewport != Size.Zero) view.tray = trayFor(view.viewport) }
    val colors = MaterialTheme.colorScheme
    val panelColor = colors.surfaceContainer.toArgb()
    val slotColor = colors.surfaceContainerHighest.toArgb()
    val hoverColor = colors.primary.toArgb()


    val onAvoidNow = androidx.compose.runtime.rememberUpdatedState(onAvoid)
    LaunchedEffect(view, showTray) {
        var first = true
        snapshotFlow { Triple(view.camera, view.tray, view.trayCollapsed) }.collectLatest { (cam, tray, collapsed) ->
            if (!first) kotlinx.coroutines.delay(150) // after the first, only once the camera has settled
            if (cam != null) first = false
            if (cam == null) return@collectLatest
            val tl = cam.toScreen(Offset(0f, 0f))
            val br = cam.toScreen(Offset(boardW, boardH))
            val list = ArrayList<androidx.compose.ui.geometry.Rect>(2)
            list.add(androidx.compose.ui.geometry.Rect(tl.x, tl.y, br.x, br.y))
            if (showTray && tray != null) list.add(if (collapsed) tray.handleRect else tray.trayRect)
            onAvoidNow.value(list)
        }
    }

    /** Rebuilds the shown tray list (contents through the filter and sort) if the tray changed since. */
    fun syncShown() {
        val t = vm.trayTick.intValue
        if (trayList.builtFor == t) return
        trayList.builtFor = t
        val filter = vm.trayFilter.value
        val groups = vm.pieceColorGroup
        val edges = vm.pieceIsEdge
        var c = 0
        if (vm.traySortByColor.value && vm.colorOrder.size == n) {
            val order = vm.colorOrder
            for (k in 0 until n) {
                val id = order[k]
                if (game.isInTray(id) && filter.accepts(edges[id], ColorGroup.entries[groups[id]])) trayList.ids[c++] = id
            }
        } else {
            val all = game.trayPieces
            for (k in 0 until all.size) {
                val id = all[k]
                if (filter.accepts(edges[id], ColorGroup.entries[groups[id]])) trayList.ids[c++] = id
            }
        }
        trayList.count = c
    }

    fun trayScrollNow(tl: TrayLayout): Float = view.trayScroll.coerceIn(0f, tl.maxScroll(trayList.count))

    fun scrollTrayBy(d: Float) {
        val tl = view.tray ?: return
        syncShown()
        val next = (trayScrollNow(tl) + d).coerceIn(0f, tl.maxScroll(trayList.count))
        if (next != view.trayScroll) view.trayScroll = next
    }

    /** What a pointer at screen [p] lands on in the tray, for [PointerRouter]. */
    fun trayTest(p: Offset): Int {
        val tl = view.tray
        if (!showTray || tl == null) return PointerRouter.NOT_IN_TRAY
        if (view.trayCollapsed) return if (tl.handleRect.contains(p)) PointerRouter.TRAY_EMPTY else PointerRouter.NOT_IN_TRAY
        if (!tl.trayRect.contains(p)) return PointerRouter.NOT_IN_TRAY
        if (!tl.slotsViewport.contains(p)) return PointerRouter.TRAY_EMPTY
        syncShown()
        val i = tl.indexAt(p, trayScrollNow(tl)) ?: return PointerRouter.TRAY_EMPTY
        return if (i < trayList.count) trayList.ids[i] else PointerRouter.TRAY_EMPTY
    }

    /** True when a finger at screen [p] would drop into the tray (or onto its collapsed handle). */
    fun overTray(p: Offset): Boolean {
        val tl = view.tray
        if (!showTray || tl == null) return false
        return if (view.trayCollapsed) tl.handleRect.contains(p) else tl.trayRect.contains(p)
    }

    val routerSlop = LocalViewConfiguration.current.touchSlop
    val router = remember(vm, showTray, routerSlop) {
        PointerRouter(
            touchSlop = routerSlop,
            trayTest = { trayTest(it) },
            canRotate = { !game.isLocked(it) && !game.isInTray(it) },
            groupOf = { game.groupId(it) },
        ) { world -> hitTest(vm, view, world, boundsW, boundsH, 48f * density) }
    }

    // Build every piece's shadow masks in the background once the puzzle is up.
    LaunchedEffect(masks) { withContext(Dispatchers.Default) { masks.warmAll() } }

    /** Re-syncs [floating] with the lifts; true if any piece moved between the layers. */
    fun refreshFloating(): Boolean {
        System.arraycopy(floating, 0, floatingPrev, 0, n)
        java.util.Arrays.fill(floating, false)
        for (i in 0 until lifts.size) {
            val lf = lifts[i]
            lf.wasFloating = lf.isFloating
            if (lf.wasFloating) for (m in lf.members) floating[m] = true
        }
        return !java.util.Arrays.equals(floating, floatingPrev)
    }

    /** One frame-clock loop steps every animating lift; it ends when nothing is left to animate. */
    fun ensureLiftAnim() {
        if (view.liftAnim?.isActive == true) return
        view.liftAnim = scope.launch {
            while (true) {
                var any = false
                var restChanged = false
                withFrameNanos { now ->
                    var i = lifts.size - 1
                    while (i >= 0) {
                        val lf = lifts[i]
                        if (lf.step(now)) any = true
                        if (lf.isDone) {
                            for (m in lf.members) if (liftOf[m] === lf) liftOf[m] = null
                            lifts.removeAt(i)
                            restChanged = true
                        } else if (lf.isFloating != lf.wasFloating) {
                            restChanged = true
                        }
                        i--
                    }
                }
                if (restChanged) {
                    // Something settled (or started floating): move it between the two layers.
                    refreshFloating()
                    vm.boardTick.intValue++
                }
                vm.moveTick.intValue++
                if (!any) break
            }
        }
    }

    /**
     * The lift state for the group holding [p], created if needed and lifted. Reuses a lift that is still
     * settling from a drop; merges lifts when the group has changed since (a join), carrying the highest value.
     */
    fun liftGroup(p: Int, grabX: Float, grabY: Float) {
        if (game.isInTray(p)) return // never lift a tray piece onto the table layers
        val gm = game.groupMembers(p)
        val existing = liftOf[p]
        val lf: Lift
        if (existing != null && existing.members.size == gm.size && !existing.lockActive) {
            lf = existing
            lf.refocus(p, grabX, grabY)
            lf.liftUp(lf.value)
        } else {
            var v = 0f
            for (m in gm) {
                val old = liftOf[m] ?: continue
                if (old.value > v) v = old.value
                for (om in old.members) if (liftOf[om] === old) liftOf[om] = null
                lifts.remove(old)
            }
            lf = Lift(gm.toIntArray(), p, grabX, grabY)
            lf.value = v
            lf.liftUp(v)
            lifts += lf
            for (m in lf.members) liftOf[m] = lf
        }
        if (refreshFloating()) vm.boardTick.intValue++
        ensureLiftAnim()
    }

    fun dropLift(p: Int, result: DropResult) {
        val lf = liftOf[p] ?: return
        lf.held = false
        if (result == DropResult.LOCKED) {
            var l = Float.MAX_VALUE; var t = Float.MAX_VALUE; var r = -Float.MAX_VALUE; var b = -Float.MAX_VALUE
            for (m in lf.members) {
                l = minOf(l, game.x(m)); t = minOf(t, game.y(m))
                r = maxOf(r, game.x(m) + boundsW[m]); b = maxOf(b, game.y(m) + boundsH[m])
            }
            lf.lock((l + r) / 2, (t + b) / 2)
        } else {
            lf.drop()
        }
        // A quick tap can release a lift before it ever animated: the piece then goes back to the resting
        // layer here, and onDrop doesn't re-record it, so do it now.
        if (refreshFloating()) vm.boardTick.intValue++
        ensureLiftAnim()
    }

    val pivot = remember { FloatArray(2) }

    /** The engine's rotation pivot of [p]'s group (the centre of the bounding box of the members' centres), into [pivot]. */
    fun groupPivot(p: Int) {
        var l = Float.MAX_VALUE; var t = Float.MAX_VALUE; var r = -Float.MAX_VALUE; var b = -Float.MAX_VALUE
        for (m in game.groupMembers(p)) {
            val cx = game.x(m) + boundsW[m] / 2
            val cy = game.y(m) + boundsH[m] / 2
            l = minOf(l, cx); t = minOf(t, cy); r = maxOf(r, cx); b = maxOf(b, cy)
        }
        pivot[0] = (l + r) / 2
        pivot[1] = (t + b) / 2
    }

    /** Tap to rotate: turn [p]'s group a quarter turn in the engine, and animate the turn on its lift. */
    fun tapRotate(p: Int) {
        groupPivot(p)
        vm.rotate(p, TAP_TURN_DEG)
        liftOf[p]?.rotateBy(TAP_TURN_DEG, pivot[0], pivot[1])
    }

    /** [p]'s group just locked from [beforeDeg] (a few degrees off upright): ease the snap to 0 instead of popping. */
    fun easeLockTurn(p: Int, beforeDeg: Float) {
        val signed = if (beforeDeg > 180f) beforeDeg - 360f else beforeDeg
        if (signed == 0f) return
        groupPivot(p)
        liftOf[p]?.rotateBy(-signed, pivot[0], pivot[1])
    }

    /**
     * Puts the held single piece [p] into the tray instead of dropping it, and takes it off the moving layer
     * (the resting layer never draws tray pieces, so it needs no re-record). Scrolls the tray to show it.
     */
    fun storeHeld(p: Int): Boolean {
        if (!vm.storeInTray(p)) return false
        val lf = liftOf[p]
        if (lf != null) {
            for (m in lf.members) if (liftOf[m] === lf) liftOf[m] = null
            lifts.remove(lf)
        }
        refreshFloating()
        val tl = view.tray
        if (tl != null) {
            syncShown()
            val i = trayList.count - 1
            if (i >= 0 && trayList.ids[i] == p) {
                val slot = tl.slotRect(i, trayScrollNow(tl))
                val vp = tl.slotsViewport
                if (slot.top < vp.top || slot.bottom > vp.bottom || slot.left < vp.left || slot.right > vp.right) {
                    view.trayScroll = tl.maxScroll(trayList.count)
                }
            }
        }
        return true
    }

    /**
     * A group (or locked-out piece) let go over the tray can't go in: slide it back across the tray's
     * inner edge so it isn't hidden under the panel. Runs before the drop resolves.
     */
    fun nudgeOutOfTray(p: Int) {
        val tl = view.tray ?: return
        val cam = view.camera ?: return
        if (!showTray || view.trayCollapsed) return
        val gap = 8f * density / cam.scale
        var r = -Float.MAX_VALUE
        var b = -Float.MAX_VALUE
        for (m in game.groupMembers(p)) {
            r = maxOf(r, game.x(m) + boundsW[m])
            b = maxOf(b, game.y(m) + boundsH[m])
        }
        if (tl.vertical) {
            val edge = cam.toWorld(Offset(tl.trayRect.left, 0f)).x - gap
            if (r > edge) vm.onDrag(p, edge - r, 0f)
        } else {
            val edge = cam.toWorld(Offset(0f, tl.trayRect.top)).y - gap
            if (b > edge) vm.onDrag(p, 0f, edge - b)
        }
    }

    /** The visible table: the view minus the open tray, so "fit" never puts the board under the tray. */
    fun tableArea(): Size {
        val tl = view.tray
        if (!showTray || tl == null || view.trayCollapsed) return view.viewport
        return if (tl.vertical) Size(tl.trayRect.left, view.viewport.height)
        else Size(view.viewport.width, tl.trayRect.top)
    }

    /** The box card's screen rect at its default corner, or null when it is hidden or has been moved. */
    fun cardRect(): androidx.compose.ui.geometry.Rect? {
        if (boxCardAspect <= 0f || view.viewport == Size.Zero) return null
        return boxCardDefaultRect(view.viewport.width, view.viewport.height, boxCardAspect, density)
    }

    /** The table area as a rect: the view minus the open tray and the box card. */
    fun tableRect(): androidx.compose.ui.geometry.Rect {
        val a = tableArea()
        return tableRectExcluding(androidx.compose.ui.geometry.Rect(0f, 0f, a.width, a.height), cardRect(), 8f * density)
    }

    /** A group let go under the box card slides the shortest way out from under it. */
    fun nudgeOutOfCard(p: Int) {
        val card = cardRect() ?: return
        val cam = view.camera ?: return
        var l = Float.MAX_VALUE
        var t = Float.MAX_VALUE
        var r = -Float.MAX_VALUE
        var b = -Float.MAX_VALUE
        for (m in game.groupMembers(p)) {
            l = minOf(l, game.x(m)); t = minOf(t, game.y(m))
            r = maxOf(r, game.x(m) + boundsW[m]); b = maxOf(b, game.y(m) + boundsH[m])
        }
        val tlS = cam.toScreen(Offset(l, t))
        val brS = cam.toScreen(Offset(r, b))
        val gap = 8f * density
        val c = androidx.compose.ui.geometry.Rect(card.left - gap, card.top - gap, card.right + gap, card.bottom + gap)
        if (brS.x <= c.left || tlS.x >= c.right || brS.y <= c.top || tlS.y >= c.bottom) return
        val vp = view.viewport
        val dl = c.left - brS.x   // move left (negative)
        val dr = c.right - tlS.x
        val du = c.top - brS.y
        val dd = c.bottom - tlS.y
        var bestX = 0f; var bestY = 0f; var best = Float.MAX_VALUE
        fun tryMove(dx: Float, dy: Float) {
            val fits = tlS.x + dx >= 0f && brS.x + dx <= vp.width && tlS.y + dy >= 0f && brS.y + dy <= vp.height
            val cost = abs(dx) + abs(dy) + if (fits) 0f else 1e6f
            if (cost < best) { best = cost; bestX = dx; bestY = dy }
        }
        tryMove(dl, 0f); tryMove(dr, 0f); tryMove(0f, du); tryMove(0f, dd)
        vm.onDrag(p, bestX / cam.scale, bestY / cam.scale)
    }

    val held = remember { FloatArray(2) }

    /**
     * Where a held piece's centre may be for a wanted centre ([cx], [cy]) (world), into [held]: the nearest point
     * on the table or, while the tray shows, over the tray (or its collapsed handle), so a piece follows the
     * finger all the way onto the tray and out of it, even where the tray covers no table.
     */
    fun clampHeld(cx: Float, cy: Float, cam: Camera) {
        val t = view.table
        var bx = cx.coerceIn(t.left, t.right)
        var by = cy.coerceIn(t.top, t.bottom)
        val tl = view.tray
        if (showTray && tl != null && (bx != cx || by != cy)) {
            val r = if (view.trayCollapsed) tl.handleRect else tl.trayRect
            val a = cam.toWorld(r.topLeft)
            val b = cam.toWorld(r.bottomRight)
            val ux = cx.coerceIn(a.x, b.x)
            val uy = cy.coerceIn(a.y, b.y)
            if ((ux - cx) * (ux - cx) + (uy - cy) * (uy - cy) < (bx - cx) * (bx - cx) + (by - cy) * (by - cy)) {
                bx = ux; by = uy
            }
        }
        held[0] = bx
        held[1] = by
    }

    /** A group let go off the table (over the tray, where it can't go in) slides back so [p]'s centre is on it. */
    fun keepOnTable(p: Int) {
        val t = view.table
        val cx = game.x(p) + boundsW[p] / 2
        val cy = game.y(p) + boundsH[p] / 2
        vm.onDrag(p, cx.coerceIn(t.left, t.right) - cx, cy.coerceIn(t.top, t.bottom) - cy)
    }

    /** Moves the camera to [target], eased over [FIT_ANIM_NANOS] (or at once). */
    fun animateCamera(target: Camera, animate: Boolean) {
        view.cameraAnim?.cancel()
        val from = view.camera
        if (!animate || from == null) {
            view.camera = target
            view.rebase()
            return
        }
        view.cameraAnim = scope.launch {
            var start = -1L
            while (true) {
                val now = withFrameNanos { it }
                if (start < 0) start = now
                val f = ((now - start).toFloat() / FIT_ANIM_NANOS).coerceIn(0f, 1f)
                val e = 1f - (1f - f) * (1f - f) * (1f - f) // ease-out cubic
                view.camera = Camera(
                    from.scale + (target.scale - from.scale) * e,
                    from.offsetX + (target.offsetX - from.offsetX) * e,
                    from.offsetY + (target.offsetY - from.offsetY) * e,
                )
                if (view.restIsStale()) view.rebase()
                if (f >= 1f) break
            }
            view.rebase()
        }
    }

    val pieceBox = remember { FloatArray(4) }

    /** Re-grows the table from the base: every loose piece's centre on it, and the shape of the visible table area. */
    fun refreshTable() {
        var l = Float.MAX_VALUE; var t = Float.MAX_VALUE; var r = -Float.MAX_VALUE; var b = -Float.MAX_VALUE
        for (id in 0 until n) {
            if (game.isInTray(id)) continue // tray pieces keep stale table positions
            val cx = game.x(id) + boundsW[id] / 2
            val cy = game.y(id) + boundsH[id] / 2
            l = minOf(l, cx); t = minOf(t, cy); r = maxOf(r, cx); b = maxOf(b, cy)
        }
        val any = r >= l
        if (any) { pieceBox[0] = l; pieceBox[1] = t; pieceBox[2] = r; pieceBox[3] = b }
        val area = tableRect()
        val pad = FIT_PADDING_DP * density
        view.table = tableToFill(vm.table, if (any) pieceBox else null, area.width - 2 * pad, area.height - 2 * pad)
    }

    /**
     * The open tray was just tucked away (or brought back): whatever table area is now on screen becomes table
     * too, so the strip the tray left isn't off limits. Only near the fitted zoom, so a zoomed-out view
     * doesn't make the table huge. Never shrinks the table.
     */
    fun growTableToView() {
        val cam = view.camera ?: return
        if (view.viewport == Size.Zero || cam.scale < view.fitScale * 0.98f) return
        val a = tableArea()
        val tl = cam.toWorld(Offset(0f, 0f))
        val br = cam.toWorld(Offset(a.width, a.height))
        val t = view.table
        view.table = com.family.puzzlepop.engine.Box(
            minOf(t.left, tl.x), minOf(t.top, tl.y), maxOf(t.right, br.x), maxOf(t.bottom, br.y),
        )
    }

    fun fitCamera(animate: Boolean) {
        if (view.viewport == Size.Zero) return
        refreshTable()
        val target = Camera.fitIn(view.table, tableRect(), FIT_PADDING_DP * density)
        view.fitScale = target.scale
        animateCamera(target, animate)
    }

    LaunchedEffect(vm) {
        vm.trayCollapsed.collect {
            if (view.trayCollapsed != it) {
                view.trayCollapsed = it
                if (showTray) growTableToView()
            }
        }
    }

    if (onDriver != null) {
        DisposableEffect(vm) {
            onDriver(object : CanvasDriver {
                override val camera: Camera? get() = view.camera
                override val viewport: Size get() = view.viewport
                override fun setCamera(c: Camera) {
                    view.cameraAnim?.cancel()
                    view.camera = c
                    if (view.restIsStale()) view.rebase()
                }
                override fun lift(p: Int, grabX: Float, grabY: Float) = liftGroup(p, grabX, grabY)
                override fun release(p: Int) = dropLift(p, DropResult.NONE)
            })
            onDispose { onDriver(null) }
        }
    }

    // The helper picks only pieces whose centre is on the visible table (not under the open tray). Kid mode
    // judges that at the fitted view, so a zoomed-in kid still gets a hint (the camera eases back to show it).
    DisposableEffect(vm) {
        vm.pieceVisible = { id ->
            val cam = if (kid && view.viewport != Size.Zero) Camera.fitIn(view.table, tableRect(), FIT_PADDING_DP * density) else view.camera
            if (cam == null) {
                true
            } else {
                val a = tableArea()
                val p = cam.toScreen(Offset(game.x(id) + boundsW[id] / 2, game.y(id) + boundsH[id] / 2))
                val card = cardRect()
                p.x >= 0f && p.y >= 0f && p.x <= a.width && p.y <= a.height &&
                    (card == null || !card.contains(p))
            }
        }
        vm.pieceBusy = { id -> liftOf[id]?.isFloating == true }
        onDispose { vm.pieceVisible = null; vm.pieceBusy = null }
    }

    // A new hint: restart its animation clock, and pan to show piece and spot together (kid mode: back to the
    // fitted view, if zoomed in and either is off screen).
    LaunchedEffect(vm, kid) {
        vm.hintNow.let { state ->
            snapshotFlow { state.value }.collectLatest { h ->
                if (h == null) return@collectLatest
                if (router.cameraPointerCount == 0) {
                    if (!kid) panToShow(h.pieceId, view, game, boundsW, boundsH, view.table, tableArea(), density, ::animateCamera)
                    else if (hintOffScreen(h.pieceId, view, game, boundsW, boundsH, tableArea(), density)) fitCamera(animate = true)
                }
                view.hintStart = 0L
                // The frame loop lives only while a hint shows; it ends when the hint is cleared or replaced.
                while (true) {
                    withFrameNanos { now ->
                        if (view.hintStart == 0L) view.hintStart = now
                        view.hintClock = now
                    }
                }
            }
        }
    }

    if (BuildConfig.DEBUG) {
        // Debug builds: log where each loose piece is and where its home is, in window pixels, so
        // `adb shell input swipe` can drag pieces home. Only while at most 60 pieces are on the table.
        LaunchedEffect(vm) {
            snapshotFlow { vm.boardTick.intValue to (view.camera != null) }.collect { (_, hasCamera) ->
                val cam = view.camera
                if (!hasCamera || cam == null || n - game.trayPieces.size > 60) return@collect
                val o = view.originInWindow
                for (id in 0 until n) {
                    if (game.isLocked(id) || game.isInTray(id)) continue
                    val now = cam.toScreen(Offset(game.x(id) + boundsW[id] / 2, game.y(id) + boundsH[id] / 2)) + o
                    val b = layout.pieces[id].bounds
                    val home = cam.toScreen(Offset(b.left + b.width / 2, b.top + b.height / 2)) + o
                    Log.d(
                        "PuzzlePopPieces",
                        "piece $id now ${now.x.toInt()} ${now.y.toInt()} home ${home.x.toInt()} ${home.y.toInt()}",
                    )
                }
            }
        }
        // And the first few tray slots, in window pixels.
        LaunchedEffect(vm) {
            snapshotFlow { Triple(vm.trayTick.intValue, view.trayScroll, view.tray) }.collect { (_, _, tl) ->
                if (tl == null) return@collect
                syncShown()
                val o = view.originInWindow
                val sc = trayScrollNow(tl)
                val sb = StringBuilder("tray ${trayList.count} shown, scroll ${sc.toInt()}:")
                for (i in 0 until minOf(trayList.count, 6)) {
                    val c = tl.slotRect(i, sc).center + o
                    sb.append(" [$i] piece ${trayList.ids[i]} at ${c.x.toInt()} ${c.y.toInt()}")
                }
                Log.d("PuzzlePopTray", sb.toString())
            }
        }
    }

    Box(modifier) {
    Box(
        Modifier
            .fillMaxSize()
            .clipToBounds()
            // Strips the resting layer hasn't drawn yet (mid-pan, before a rebase) show plain table.
            .background(Color(TABLE_COLOR))
            .systemGestureExclusion()
            .onSizeChanged {
                val s = it.toSize()
                if (s != view.viewport) {
                    view.viewport = s
                    view.tray = trayFor(s)
                    fitCamera(animate = false)
                }
            }
            .onGloballyPositioned { view.originInWindow = it.positionInWindow() }
            .pointerInput(vm, rendered, showTray, kid) {
                val touchSlop = viewConfiguration.touchSlop
                val palmPx = PALM_TOUCH_MAJOR_DP * density
                val doubleTapMs = viewConfiguration.doubleTapTimeoutMillis
                val doubleTapRadius = 64f * density
                var eventSerial = 0
                val movedStamp = IntArray(n) { -1 }
                // Tap tracking for double-tap-to-fit (camera pointers only, one finger, no movement).
                var tapCandidate = false
                var tapDownPos = Offset.Zero
                var tapDownTime = 0L
                var lastTapUpTime = -1_000_000L
                var lastTapPos = Offset.Zero
                // A single table finger only pans once it has moved past touch slop, so a resting palm
                // doesn't creep the board. Two or more table fingers pinch straight away.
                var panning = false
                var slopPointer = -1L
                var slopOrigin = Offset.Zero
                var magnetPointer = -1L
                // Kid magnet brake: only a finger that slows down near home gets pulled in; a flung piece never locks.
                val magnetGate = MagnetGate(MagnetGate.SLOW_DP_PER_MS * density, MagnetGate.FLING_DP_PER_MS * density)

                /**
                 * Ends a piece drag; [at] is where the finger lifted (screen), or null when cancelled. [tap]: it was
                 * a quick tap, so the group turns a quarter first (then drops: it may now lock or join). [flung]: let
                 * go while still moving fast (kid mode), so it lands loose: no lock, no join.
                 */
                fun endDrag(pointerId: Long, at: Offset?, tap: Boolean = false, flung: Boolean = false) {
                    magnetGate.remove(pointerId)
                    val k = drags.indexOf(pointerId)
                    if (k < 0) return
                    val piece = drags.piece[k]
                    drags.removeAt(k)
                    // Another finger still holds this group: keep it lifted, no drop yet.
                    val gid = game.groupId(piece)
                    for (j in 0 until drags.size) if (game.groupId(drags.piece[j]) == gid) return
                    if (tap) traced("tapRotate") { tapRotate(piece) }
                    // A single piece let go over the tray goes into it; anything else drops as usual.
                    if (at != null && overTray(at) && game.canStoreInTray(piece) && storeHeld(piece)) return
                    if (at != null && overTray(at)) nudgeOutOfTray(piece)
                    keepOnTable(piece)
                    nudgeOutOfCard(piece)
                    val before = game.rotation(piece)
                    val result = traced("onDrop") { vm.onDrop(piece, flung) }
                    when (result) {
                        DropResult.LOCKED -> haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        DropResult.JOINED -> haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        DropResult.NONE -> Unit
                    }
                    traced("dropLift") { dropLift(piece, result) }
                    if (result == DropResult.LOCKED) easeLockTurn(piece, before)
                    // A join can have merged this group into one another finger still holds: re-sync its lift.
                    for (j in 0 until drags.size) liftGroup(drags.piece[j], drags.grabX[j], drags.grabY[j])
                }

                /**
                 * A tentative drag became part of a pinch (or gave way to a stylus): put the group back where it
                 * was and let its lift settle. Not a move: no drop, no sound, no autosave.
                 */
                fun cancelDrag(pointerId: Long) {
                    magnetGate.remove(pointerId)
                    val k = drags.indexOf(pointerId)
                    if (k < 0) return
                    val piece = drags.piece[k]
                    val sx = drags.startX[k]
                    val sy = drags.startY[k]
                    drags.removeAt(k)
                    val gid = game.groupId(piece)
                    for (j in 0 until drags.size) if (game.groupId(drags.piece[j]) == gid) return
                    vm.cancelDrag(piece, sx, sy)
                    dropLift(piece, DropResult.NONE)
                }

                /**
                 * A finger that went down at [down] on tray piece [p] moved out to [at]: the piece leaves the tray
                 * at its table size, held at the same point of it the finger held in the slot, and is lifted.
                 */
                fun takeOut(pointerId: Long, p: Int, down: Offset, at: Offset, cam: Camera) {
                    val world = cam.toWorld(at)
                    // Where on the piece the finger is: its offset from the slot centre, at the slot's scale.
                    var gx = 0f
                    var gy = 0f
                    val tl = view.tray
                    if (tl != null) {
                        syncShown()
                        var i = 0
                        while (i < trayList.count && trayList.ids[i] != p) i++
                        if (i < trayList.count) {
                            val slot = tl.slotRect(i, trayScrollNow(tl))
                            val k = slotPieceScale(tl.slotSize, drawW[p], drawH[p], game.rotation(p))
                            gx = ((down.x - slot.center.x) / k).coerceIn(-boundsW[p] / 2, boundsW[p] / 2)
                            gy = ((down.y - slot.center.y) / k).coerceIn(-boundsH[p] / 2, boundsH[p] / 2)
                        }
                    }
                    clampHeld(world.x - gx, world.y - gy, cam)
                    val x = held[0] - boundsW[p] / 2
                    val y = held[1] - boundsH[p] / 2
                    vm.takeFromTray(p, x, y)
                    router.reroute(pointerId, PointerRouter.Route.DragPiece(p))
                    drags.add(pointerId, p, world.x - x, world.y - y)
                    traced("onDragStart") {
                        vm.onDragStart(p)
                        liftGroup(p, drags.grabX[drags.size - 1], drags.grabY[drags.size - 1])
                    }
                }

                // Twist (ANY rotation): two fingers on one group; it turns with the angle between them. Rotate only:
                // the group turns about its pivot and doesn't follow the fingers' midpoint.
                var twistPiece = -1
                var twistA = -1L
                var twistB = -1L
                var twistAngle = 0f
                // The group's pivot when the twist began: kept fixed, so an L-shaped group doesn't creep.
                var twistPx = 0f
                var twistPy = 0f
                val twistMinGap = TWIST_MIN_GAP_DP * density

                fun endTwist() {
                    val p = twistPiece
                    if (p < 0) return
                    twistPiece = -1
                    val before = game.rotation(p)
                    val result = traced("onDrop") { vm.onDrop(p) }
                    when (result) {
                        DropResult.LOCKED -> haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        DropResult.JOINED -> haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        DropResult.NONE -> Unit
                    }
                    dropLift(p, result)
                    if (result == DropResult.LOCKED) easeLockTurn(p, before)
                }

                fun handle(event: PointerEvent) {
                    val cam = view.camera ?: return
                    router.rotation = vm.options.value.rotation
                    val changes = event.changes
                    if (changes.isEmpty()) return
                    eventSerial++
                    // Palm check: Android sends each new pointer in its own DOWN / POINTER_DOWN event, so the
                    // action index is the pointer that just landed. Its contact size is only read here.
                    val me = event.motionEvent
                    val palmDown = me != null &&
                        (me.actionMasked == MotionEvent.ACTION_DOWN || me.actionMasked == MotionEvent.ACTION_POINTER_DOWN) &&
                        me.getTouchMajor(me.actionIndex) > palmPx
                    if (BuildConfig.DEBUG && me != null &&
                        (me.actionMasked == MotionEvent.ACTION_DOWN || me.actionMasked == MotionEvent.ACTION_POINTER_DOWN)
                    ) {
                        Log.d("PuzzlePopTouch", "down touchMajor=${me.getTouchMajor(me.actionIndex) / density}dp palm=$palmDown")
                    }
                    // Android's own palm rejection cancels a pointer (ACTION_CANCEL, or POINTER_UP with FLAG_CANCELED).
                    val cancelAll = me != null && me.actionMasked == MotionEvent.ACTION_CANCEL
                    val cancelId = if (me != null && me.actionMasked == MotionEvent.ACTION_POINTER_UP &&
                        (me.flags and MotionEvent.FLAG_CANCELED) != 0
                    ) me.getPointerId(me.actionIndex).toLong() else -1L

                    // 1. New pointers: route each one exactly once.
                    for (i in changes.indices) {
                        val ch = changes[i]
                        val id = ch.id.value
                        if (!ch.pressed || router.routeOf(id) != null) continue
                        view.cameraAnim?.cancel()
                        val world = cam.toWorld(ch.position)
                        val pen = ch.type == PointerType.Stylus || ch.type == PointerType.Eraser
                        val route = router.down(id, world, ch.position, ch.uptimeMillis, kid, pen, palmDown && !pen)
                        for (c in 0 until router.cancelledCount) cancelDrag(router.cancelledPointer(c))
                        if (route === PointerRouter.Route.Ignored) {
                            tapCandidate = false
                        } else if (route is PointerRouter.Route.TrayPiece) {
                            pendingTray.add(id, route.pieceId, ch.position.x, ch.position.y)
                            tapCandidate = false
                        } else if (route === PointerRouter.Route.TrayScroll) {
                            tapCandidate = false
                        } else if (route is PointerRouter.Route.Twist) {
                            // The first finger's tentative drag was just cancelled (put back); lift the group again.
                            var partner = -1L
                            for (k in 0 until router.size) {
                                if (router.pointerAt(k) != id && router.routeAt(k) is PointerRouter.Route.Twist) partner = router.pointerAt(k)
                            }
                            var pa = ch.position
                            for (j in changes.indices) if (changes[j].id.value == partner) pa = changes[j].position
                            val p = route.pieceId
                            twistPiece = p
                            twistA = partner
                            twistB = id
                            twistAngle = atan2(ch.position.y - pa.y, ch.position.x - pa.x)
                            groupPivot(p)
                            twistPx = pivot[0]
                            twistPy = pivot[1]
                            val wa = cam.toWorld(pa)
                            liftGroup(p, wa.x - game.x(p), wa.y - game.y(p))
                            tapCandidate = false
                        } else if (route is PointerRouter.Route.DragPiece) {
                            val p = route.pieceId
                            drags.add(id, p, world.x - game.x(p), world.y - game.y(p), game.x(p), game.y(p))
                            magnetGate.start(id, ch.position.x, ch.position.y, ch.uptimeMillis)
                            traced("onDragStart") {
                                vm.onDragStart(p)
                                liftGroup(p, drags.grabX[drags.size - 1], drags.grabY[drags.size - 1])
                            }
                            tapCandidate = false
                        } else {
                            tapCandidate = router.size == 1
                            tapDownPos = ch.position
                            tapDownTime = ch.uptimeMillis
                            if (router.cameraPointerCount == 1) {
                                panning = false
                                slopPointer = id
                                slopOrigin = ch.position
                            }
                        }
                        if (router.size > 1) tapCandidate = false
                    }

                    // 1b. Moves commit tentative piece drags (past slop or the pinch grace time).
                    for (i in changes.indices) {
                        val ch = changes[i]
                        if (ch.pressed && ch.previousPressed) router.move(ch.id.value, ch.position, ch.uptimeMillis)
                    }

                    // 2. Camera: pan with one table finger, pinch with two or more. Measured over pointers
                    //    that were down before and after this event, so fingers joining never jump it.
                    var count = 0
                    var pcx = 0f; var pcy = 0f; var ccx = 0f; var ccy = 0f
                    for (i in changes.indices) {
                        val ch = changes[i]
                        if (!ch.pressed || !ch.previousPressed) continue
                        if (router.routeOf(ch.id.value) !== PointerRouter.Route.Camera) continue
                        count++
                        pcx += ch.previousPosition.x; pcy += ch.previousPosition.y
                        ccx += ch.position.x; ccy += ch.position.y
                        if (tapCandidate && (ch.position - tapDownPos).getDistance() > touchSlop) tapCandidate = false
                        if (!panning && ch.id.value == slopPointer && (ch.position - slopOrigin).getDistance() > touchSlop) {
                            panning = true
                        }
                    }
                    if (count >= 2) panning = true
                    // Kid mode: only a two-finger gesture moves the camera; one finger on the table does nothing.
                    if (kid && count < 2) panning = false
                    var camNow = cam
                    if (count > 0 && panning) {
                        pcx /= count; pcy /= count; ccx /= count; ccy /= count
                        var zoom = 1f
                        if (count >= 2) {
                            var ps = 0f; var cs = 0f
                            for (i in changes.indices) {
                                val ch = changes[i]
                                if (!ch.pressed || !ch.previousPressed) continue
                                if (router.routeOf(ch.id.value) !== PointerRouter.Route.Camera) continue
                                ps += (ch.previousPosition - Offset(pcx, pcy)).getDistance()
                                cs += (ch.position - Offset(ccx, ccy)).getDistance()
                            }
                            if (ps > 1f) zoom = cs / ps
                        }
                        val panX = ccx - pcx
                        val panY = ccy - pcy
                        if (panX != 0f || panY != 0f || zoom != 1f) {
                            camNow = if (kid) {
                                cam.transformed(Offset(pcx, pcy), zoom, panX, panY, view.fitScale, KID_MAX_ZOOM * view.fitScale)
                                    .keepTableInView(view.table, tableRect())
                            } else {
                                cam.transformed(
                                    Offset(pcx, pcy), zoom, panX, panY,
                                    0.5f * view.fitScale, 8f * view.fitScale,
                                ).keepCentreInside(view.table, view.viewport)
                            }
                            view.camera = camNow
                            // The cached resting layer is only transformed; re-draw it if it has drifted too far.
                            if (view.restIsStale()) view.rebase()
                        }
                    }

                    // 2b. Tray pointers. A finger on a tray piece decides once it passes touch slop: along the
                    //     tray (and still inside it) it scrolls; otherwise the piece comes out under the finger.
                    if (pendingTray.size > 0 || router.size > drags.size + router.cameraPointerCount) {
                        val tl = view.tray
                        val vertical = tl?.vertical ?: true
                        for (i in changes.indices) {
                            val ch = changes[i]
                            if (!ch.pressed) continue
                            val id = ch.id.value
                            val r = router.routeOf(id) ?: continue
                            if (r === PointerRouter.Route.TrayScroll) {
                                if (ch.previousPressed) {
                                    scrollTrayBy(-(if (vertical) ch.position.y - ch.previousPosition.y else ch.position.x - ch.previousPosition.x))
                                }
                            } else if (r is PointerRouter.Route.TrayPiece) {
                                val k = pendingTray.indexOf(id)
                                if (k < 0) continue
                                val dx = ch.position.x - pendingTray.grabX[k]
                                val dy = ch.position.y - pendingTray.grabY[k]
                                if (dx * dx + dy * dy <= touchSlop * touchSlop) continue
                                val p = pendingTray.piece[k]
                                val down = Offset(pendingTray.grabX[k], pendingTray.grabY[k])
                                pendingTray.removeAt(k)
                                val along = abs(if (vertical) dy else dx)
                                val across = abs(if (vertical) dx else dy)
                                if (!game.isInTray(p) || (along > across && tl != null && tl.trayRect.contains(ch.position))) {
                                    router.reroute(id, PointerRouter.Route.TrayScroll)
                                    scrollTrayBy(-(if (vertical) dy else dx))
                                } else {
                                    takeOut(id, p, down, ch.position, camNow)
                                    magnetGate.start(id, ch.position.x, ch.position.y, ch.uptimeMillis)
                                }
                            }
                        }
                    }

                    // 3. Drags: each held group follows its finger 1:1 (absolute, so a camera move made by
                    //    another finger keeps the piece under this one). One finger per group moves it.
                    for (k in 0 until drags.size) {
                        val id = drags.pointer[k]
                        var ch = changes[0]
                        var found = false
                        for (i in changes.indices) if (changes[i].id.value == id) { ch = changes[i]; found = true; break }
                        if (!found || !ch.pressed) continue
                        magnetGate.move(id, ch.position.x, ch.position.y, ch.uptimeMillis)
                        val p = drags.piece[k]
                        val world = camNow.toWorld(ch.position)
                        val gid = game.groupId(p)
                        if (movedStamp[gid] == eventSerial) {
                            // A second finger on an already-moving group: just re-anchor it.
                            drags.grabX[k] = world.x - game.x(p)
                            drags.grabY[k] = world.y - game.y(p)
                            continue
                        }
                        movedStamp[gid] = eventSerial
                        // Keep the touched piece's centre on the table (or over the tray, to drop it in).
                        clampHeld(world.x - drags.grabX[k] + boundsW[p] / 2, world.y - drags.grabY[k] + boundsH[p] / 2, camNow)
                        val tx = held[0] - boundsW[p] / 2
                        val ty = held[1] - boundsH[p] / 2
                        vm.onDrag(p, tx - game.x(p), ty - game.y(p))
                        if (magnetRadius >= 0f && magnetGate.shouldPull(id, game.magnetCheck(p, magnetRadius), ch.uptimeMillis)) {
                            magnetPointer = id
                        }
                    }
                    // 3b. Twist: turn the group by the change in angle of the line between the two fingers.
                    if (twistPiece >= 0) {
                        var ax = 0f; var ay = 0f; var bx = 0f; var by = 0f; var seen = 0
                        for (i in changes.indices) {
                            val ch = changes[i]
                            if (!ch.pressed) continue
                            val cid = ch.id.value
                            if (cid == twistA) { ax = ch.position.x; ay = ch.position.y; seen++ }
                            else if (cid == twistB) { bx = ch.position.x; by = ch.position.y; seen++ }
                        }
                        // Fingers almost touching give a noisy angle: ignore them until they are apart again.
                        if (seen == 2 && hypot(bx - ax, by - ay) < twistMinGap) {
                            twistAngle = atan2(by - ay, bx - ax) // too close to turn; keep the baseline current
                        } else if (seen == 2) {
                            val a = atan2(by - ay, bx - ax)
                            var d = a - twistAngle
                            if (d > PI_F) d -= 2 * PI_F else if (d < -PI_F) d += 2 * PI_F
                            twistAngle = a
                            // Screen y points down, so a growing angle is clockwise: the engine's positive.
                            if (d != 0f) vm.rotateLive(twistPiece, d * RAD_TO_DEG, twistPx, twistPy)
                        }
                    }
                    // Magnet: end that drag as a normal drop (lock feedback, lift animation, autosave). The
                    // finger stays down but is dead until it lifts. One group per event; another waits its turn.
                    if (magnetPointer >= 0L) {
                        val mp = magnetPointer
                        magnetPointer = -1L
                        traced("magnet") { endDrag(mp, null) }
                    }

                    // Outline the tray while a piece that can go in is held over it (state only changes on enter/leave).
                    if (showTray) {
                        var over = false
                        for (k in 0 until drags.size) {
                            val pid = drags.pointer[k]
                            for (i in changes.indices) {
                                val ch = changes[i]
                                if (ch.id.value == pid && ch.pressed && overTray(ch.position) && game.canStoreInTray(drags.piece[k])) over = true
                            }
                        }
                        if (over != view.trayHover) view.trayHover = over
                    }

                    // 4. Lifted pointers.
                    for (i in changes.indices) {
                        val ch = changes[i]
                        if (ch.pressed) continue
                        val id = ch.id.value
                        val cancelled = (cancelAll || id == cancelId) && router.isTentativeDrag(id, ch.uptimeMillis)
                        val tap = !cancelled && !cancelAll && router.tapPiece(id, ch.position, ch.uptimeMillis) >= 0
                        val route = router.up(id) ?: continue
                        if (route is PointerRouter.Route.Twist) {
                            endTwist()
                        } else if (route is PointerRouter.Route.DragPiece) {
                            if (cancelled) {
                                cancelDrag(id)
                            } else {
                                magnetGate.move(id, ch.position.x, ch.position.y, ch.uptimeMillis)
                                endDrag(id, ch.position, tap, flung = kid && magnetGate.isFling(id, ch.uptimeMillis))
                            }
                        } else if (route is PointerRouter.Route.TrayPiece) {
                            val k = pendingTray.indexOf(id)
                            if (k >= 0) pendingTray.removeAt(k)
                        } else if (tapCandidate && router.size == 0) {
                            tapCandidate = false
                            val isQuick = ch.uptimeMillis - tapDownTime < doubleTapMs
                            if (isQuick && tapDownTime - lastTapUpTime < doubleTapMs &&
                                (ch.position - lastTapPos).getDistance() < doubleTapRadius
                            ) {
                                lastTapUpTime = -1_000_000L
                                fitCamera(animate = true)
                            } else if (isQuick) {
                                lastTapUpTime = ch.uptimeMillis
                                lastTapPos = ch.position
                            }
                        }
                    }
                    if (router.cameraPointerCount == 0) {
                        panning = false
                        slopPointer = -1L
                        view.rebaseIfNeeded() // camera gesture over: re-draw only if zoomed or out of cover
                    }
                    for (i in changes.indices) changes[i].consume()
                }

                /** No new event came, but a finger resting near home may have waited long enough: pull it in. */
                fun magnetTick(now: Long) {
                    if (magnetRadius < 0f) return
                    for (k in 0 until drags.size) {
                        val id = drags.pointer[k]
                        if (magnetGate.shouldPull(id, game.magnetCheck(drags.piece[k], magnetRadius), now)) {
                            traced("magnet") { endDrag(id, null) }
                            return // one group per tick, like the event path
                        }
                    }
                }

                awaitEachGesture {
                    try {
                        while (true) {
                            // A finger held perfectly still sends no events, so wake up when its dwell is over.
                            val wait = magnetGate.nextCheckIn(SystemClock.uptimeMillis())
                            val event = if (wait > 0L) withTimeoutOrNull(wait) { awaitPointerEvent() } else awaitPointerEvent()
                            if (event == null) {
                                magnetTick(SystemClock.uptimeMillis())
                                continue
                            }
                            traced("Canvas.input") { handle(event) }
                            var anyPressed = false
                            for (i in event.changes.indices) if (event.changes[i].pressed) { anyPressed = true; break }
                            if (!anyPressed) break
                        }
                    } finally {
                        // Cancelled mid-gesture (or a pointer we never saw lift): drop whatever is held.
                        while (drags.size > 0) endDrag(drags.pointer[drags.size - 1], null)
                        endTwist()
                        pendingTray.size = 0
                        view.trayHover = false
                        router.clear()
                        magnetGate.clear()
                        tapCandidate = false
                        panning = false
                        slopPointer = -1L
                        view.rebaseIfNeeded()
                    }
                }
            },
    ) {
        // Resting layer. Offscreen compositing makes the render node a cached GPU layer: while a piece is
        // dragged, each frame composites this one texture instead of replaying ~1000 bitmap draws. It is
        // re-rendered only when its content changes (a drop, a camera move). This is the "baked board".
        // During camera gestures the cached texture is re-composited with a scale/translation (layer
        // properties, read in the graphicsLayer block) instead of being re-drawn; see TableView.rebase.
        // The node is overscanned by `view.overscan` px on every side and placed at (-overscan, -overscan).
        Canvas(
            Modifier
                .layout { measurable, constraints ->
                    val w = constraints.maxWidth
                    val h = constraints.maxHeight
                    val m = (OVERSCAN_FRACTION * minOf(w, h)).roundToInt()
                    view.overscan = m
                    val placeable = measurable.measure(Constraints.fixed(w + 2 * m, h + 2 * m))
                    layout(w, h) { placeable.place(-m, -m) }
                }
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen
                    transformOrigin = TransformOrigin(0f, 0f)
                    val k = view.restScale()
                    scaleX = k
                    scaleY = k
                    // Unscaled (a pan): whole pixels, so the cached texture stays sharp without a re-draw.
                    translationX = if (k == 1f) view.restTranslationX().roundToInt().toFloat() else view.restTranslationX()
                    translationY = if (k == 1f) view.restTranslationY().roundToInt().toFloat() else view.restTranslationY()
                },
        ) {
            vm.boardTick.intValue // redraw trigger
            val rest = view.rest ?: return@Canvas // the only camera this layer's content reads
            val m = view.overscan.toFloat()
            traced("Rest.record") { withWorld(Camera(rest.scale, rest.offsetX + m, rest.offsetY + m)) { nc ->
                nc.drawColor(TABLE_COLOR)
                // drawColor fills the clip, so the board fill/outline come after it in world space.
                rect.set(0f, 0f, boardW, boardH)
                paints.boardFill.color = BOARD_COLOR
                nc.drawRect(rect, paints.boardFill)
                if (opts.ghostAlpha > 0f) nc.drawBitmap(ghost, null, rect, paints.ghost)
                paints.outline.strokeWidth = 2f * density / rest.scale
                nc.drawRect(rect, paints.outline)
                java.util.Arrays.fill(hintHide, false)
                val hinted = vm.hintNow.value // read here: showing/clearing a hint re-records this layer once
                if (hinted != null) {
                    val gm = game.groupMembers(hinted.pieceId)
                    if (gm.size <= GLOW_MAX_PIECES) for (m in gm) hintHide[m] = true
                }
                val z = game.zOrder
                // Locked pieces first, then loose ones bottom to top.
                for (pass in 0..1) {
                    val wantLocked = pass == 0
                    for (k in 0 until z.size) {
                        val id = z[k]
                        if (floating[id] || hintHide[id] || game.isLocked(id) != wantLocked || game.isInTray(id)) continue
                        rect.set(game.x(id), game.y(id), game.x(id) + drawW[id], game.y(id) + drawH[id])
                        // Locked pieces are always upright; a turned loose piece turns about its centre.
                        val rot = game.rotation(id)
                        if (rot != 0f) {
                            nc.save()
                            nc.rotate(rot, game.x(id) + boundsW[id] / 2, game.y(id) + boundsH[id] / 2)
                            nc.drawBitmap(bitmaps[id], null, rect, paints.piece)
                            nc.restore()
                        } else {
                            nc.drawBitmap(bitmaps[id], null, rect, paints.piece)
                        }
                    }
                }
            } }
        }
        // Peek overlay: the whole picture over the board while Peek is held (under the tray).
        Canvas(Modifier.fillMaxSize().graphicsLayer()) {
            if (!peeking()) return@Canvas
            val cam = view.camera ?: return@Canvas
            withWorld(cam) { nc ->
                rect.set(0f, 0f, boardW, boardH)
                nc.drawBitmap(ghost, null, rect, paints.peek)
            }
        }
        // Helper highlight: the hinted piece (live, wiggling), its glowing spot and a sparkle trail. Draws
        // nothing, and reads nothing but the hint, while no hint shows; the frame loop above stops then too.
        Canvas(Modifier.fillMaxSize().graphicsLayer()) {
            val hint = vm.hintNow.value ?: return@Canvas
            val clock = view.hintClock
            val cam = view.camera ?: return@Canvas
            val t = if (view.hintStart == 0L || clock < view.hintStart) 0f else (clock - view.hintStart) / 1e9f
            val fade = hintFade(if (view.hintStart == 0L) 0L else clock - view.hintStart)
            drawIntoCanvas { c ->
                traced("Hint.draw") {
                    drawHint(
                        c.nativeCanvas, cam, hint, t, fade, density, game, paints, masks, bitmaps, drawW, drawH,
                        boundsW, boundsH, rect, turnIcon,
                    )
                }
            }
        }
        // Tray layer, in screen space. Re-recorded only when the tray's contents, filter, scroll, hover or
        // collapse change; the collapsed tray is just the Compose handle in TrayControls.
        if (showTray) {
            Canvas(Modifier.fillMaxSize().graphicsLayer()) {
                vm.trayTick.intValue // redraw trigger
                val tl = view.tray ?: return@Canvas
                if (view.trayCollapsed) return@Canvas
                syncShown()
                val sc = trayScrollNow(tl)
                val hover = view.trayHover
                trayPaints.panel.color = panelColor
                trayPaints.slot.color = slotColor
                trayPaints.hover.color = hoverColor
                drawIntoCanvas { c ->
                    drawTray(c.nativeCanvas, tl, sc, trayList.ids, trayList.count, bitmaps, drawW, drawH, trayPaints, hover, game)
                }
            }
        }
        // Moving layer.
        Canvas(Modifier.fillMaxSize().graphicsLayer()) {
            vm.moveTick.intValue // redraw trigger
            val cam = view.camera ?: return@Canvas
            traced("Move.draw") { withWorld(cam) { nc ->
                val lc = lifts.size
                if (lc == 0) return@withWorld
                masks.beginFrame()
                val offUnit = SHADOW_OFFSET_FRACTION * cellMin
                // Shadows, then glows, under every lifted piece.
                for (i in 0 until lc) {
                    val lf = lifts[i]
                    val lift = lf.value
                    if (lift <= 0.001f && lf.glow <= 0f) continue
                    val fx = if (lf.fixedFocus) lf.cx else game.x(lf.ref) + lf.fx
                    val fy = if (lf.fixedFocus) lf.cy else game.y(lf.ref) + lf.fy
                    nc.save()
                    nc.scale(lf.scale, lf.scale, fx, fy)
                    if (lf.angleLag != 0f) nc.rotate(-lf.angleLag, lf.pivotX, lf.pivotY)
                    val mem = lf.members
                    for (k in 0 until mem.size) {
                        val id = mem[k]
                        val x = game.x(id)
                        val y = game.y(id)
                        // Masks turn with the piece, about the (offset) piece centre; the offset itself stays down-right.
                        val rot = game.rotation(id)
                        val hcx = boundsW[id] / 2
                        val hcy = boundsH[id] / 2
                        if (lift > 0.001f) {
                            val pm = masks.get(id)
                            if (pm != null) {
                                // Further from the table: shadow slides down-right, grows and softens.
                                val dx = offUnit * (0.4f + 1.6f * lift)
                                val dy = dx * 1.5f
                                val spread = 1f + 0.10f * lift
                                paints.shadow.alpha = (255 * SHADOW_FAR_ALPHA * lift * lift).toInt()
                                maskRect(rect, pm.farOffX, pm.farOffY, pm.farW, pm.farH, x + dx, y + dy, spread, boundsW[id], boundsH[id])
                                drawTurned(nc, pm.far, rect, paints.shadow, rot, x + dx + hcx, y + dy + hcy)
                                paints.shadow.alpha = (255 * SHADOW_NEAR_ALPHA * lift * (1f - 0.4f * lift)).toInt()
                                maskRect(rect, pm.nearOffX, pm.nearOffY, pm.nearW, pm.nearH, x + dx * 0.4f, y + dy * 0.4f, 1f, boundsW[id], boundsH[id])
                                drawTurned(nc, pm.near, rect, paints.shadow, rot, x + dx * 0.4f + hcx, y + dy * 0.4f + hcy)
                            } else {
                                // Mask not built yet (big group, budget spent): plain tinted silhouette.
                                val d = offUnit * 2f * lift
                                rect.set(x + d, y + d * 1.5f, x + d + drawW[id], y + d * 1.5f + drawH[id])
                                drawTurned(nc, bitmaps[id], rect, paints.shadowFallback, rot, x + d + hcx, y + d * 1.5f + hcy)
                            }
                        }
                        if (lf.glow > 0f && mem.size <= GLOW_MAX_PIECES) {
                            val pm = masks.get(id)
                            if (pm != null) {
                                paints.glow.alpha = (255 * 0.55f * lf.glow).toInt()
                                maskRect(rect, pm.farOffX, pm.farOffY, pm.farW, pm.farH, x, y, 1f, boundsW[id], boundsH[id])
                                drawTurned(nc, pm.far, rect, paints.glow, rot, x + hcx, y + hcy)
                                paints.glow.alpha = (255 * 0.9f * lf.glow).toInt()
                                maskRect(rect, pm.nearOffX, pm.nearOffY, pm.nearW, pm.nearH, x, y, 1f, boundsW[id], boundsH[id])
                                drawTurned(nc, pm.near, rect, paints.glow, rot, x + hcx, y + hcy)
                            }
                        }
                    }
                    nc.restore()
                }
                // The lifted pieces themselves, bottom to top, each group scaled about its focus point.
                val z = game.zOrder
                var cur: Lift? = null
                var saved = false
                for (k in 0 until z.size) {
                    val id = z[k]
                    if (!floating[id] || game.isInTray(id)) continue
                    val lf = liftOf[id]
                    if (lf !== cur) {
                        if (saved) nc.restore()
                        saved = false
                        cur = lf
                        if (lf != null && (lf.scale != 1f || lf.angleLag != 0f)) {
                            val fx = if (lf.fixedFocus) lf.cx else game.x(lf.ref) + lf.fx
                            val fy = if (lf.fixedFocus) lf.cy else game.y(lf.ref) + lf.fy
                            nc.save()
                            nc.scale(lf.scale, lf.scale, fx, fy)
                            if (lf.angleLag != 0f) nc.rotate(-lf.angleLag, lf.pivotX, lf.pivotY)
                            saved = true
                        }
                    }
                    rect.set(game.x(id), game.y(id), game.x(id) + drawW[id], game.y(id) + drawH[id])
                    drawTurned(nc, bitmaps[id], rect, paints.piece, game.rotation(id), game.x(id) + boundsW[id] / 2, game.y(id) + boundsH[id] / 2)
                }
                if (saved) nc.restore()
            } }
        }
    }
    // The tray's buttons sit over the canvas as siblings, so their taps never reach the gesture handler.
    val tl = view.tray
    if (showTray && tl != null) {
        TrayControls(
            layout = tl,
            collapsed = { view.trayCollapsed },
            hover = { view.trayHover },
            count = { vm.trayCount.intValue },
            filter = vm.trayFilter,
            onFilter = { f ->
                view.trayScroll = 0f // a new list: start at its top
                vm.setTrayFilter(f)
            },
            sortByColor = vm.traySortByColor,
            onSortByColor = { on ->
                view.trayScroll = 0f
                vm.setTraySortByColor(on)
            },
            groupMask = vm.trayGroupMask,
            plusOne = vm.plusOne,
            onCollapsed = vm::setTrayCollapsed,
            colorsOpen = { view.trayColorsOpen },
            onColorsOpen = { open ->
                if (open != view.trayColorsOpen) {
                    view.trayColorsOpen = open
                    if (view.viewport != Size.Zero) view.tray = trayFor(view.viewport)
                    vm.trayTick.intValue++ // re-draw the slots in the new viewport
                }
            },
        )
    }
    }
}

/** 0..1 fade-in at the start of a hint; it then stays until the piece is placed. */
private fun hintFade(sinceStartNanos: Long): Float {
    if (sinceStartNanos < 0) return 0f
    val fin = (sinceStartNanos.toFloat() / HINT_FADE_NANOS).coerceIn(0f, 1f)
    return fin
}

/**
 * Grown-up mode: if the hinted piece or its spot is (nearly) off screen, eases the camera to show both,
 * never zooming in past the current zoom nor out past the gesture limit. Kid mode never calls this.
 */
/** Is the hinted piece's centre, or its home's, off screen (within a small margin) at the live camera? */
private fun hintOffScreen(
    pieceId: Int, view: TableView, game: PuzzleGame, boundsW: FloatArray, boundsH: FloatArray, area: Size, density: Float,
): Boolean {
    val cam = view.camera ?: return false
    val home = game.layout.pieces[pieceId].bounds
    val m = 32f * density
    fun off(wx: Float, wy: Float): Boolean {
        val s = cam.toScreen(Offset(wx, wy))
        return s.x < m || s.y < m || s.x > area.width - m || s.y > area.height - m
    }
    return off(game.x(pieceId) + boundsW[pieceId] / 2, game.y(pieceId) + boundsH[pieceId] / 2) ||
        off(home.left + home.width / 2, home.top + home.height / 2)
}

private fun panToShow(
    pieceId: Int, view: TableView, game: PuzzleGame, boundsW: FloatArray, boundsH: FloatArray,
    table: com.family.puzzlepop.engine.Box, area: Size, density: Float, animate: (Camera, Boolean) -> Unit,
) {
    val cam = view.camera ?: return
    if (!hintOffScreen(pieceId, view, game, boundsW, boundsH, area, density)) return
    val home = game.layout.pieces[pieceId].bounds
    val l = min(game.x(pieceId), home.left)
    val t = min(game.y(pieceId), home.top)
    val r = max(game.x(pieceId) + boundsW[pieceId], home.right)
    val b = max(game.y(pieceId) + boundsH[pieceId], home.bottom)
    val avail = Size(max(1f, area.width - 2 * 48f * density), max(1f, area.height - 2 * 48f * density))
    val fitScale = min(avail.width / (r - l), avail.height / (b - t))
    val scale = min(fitScale, cam.scale).coerceAtLeast(0.5f * view.fitScale)
    val target = Camera(scale, area.width / 2 - (l + r) / 2 * scale, area.height / 2 - (t + b) / 2 * scale)
        .keepCentreInside(table, view.viewport)
    animate(target, true)
}

/** The helper highlight. Allocation-free: shared paints and rect, only primitives per frame. */
private fun drawHint(
    nc: android.graphics.Canvas, cam: Camera, hint: HintTarget, t: Float, fade: Float, density: Float,
    game: PuzzleGame, paints: Paints, masks: PieceMasks, bitmaps: Array<Bitmap>, drawW: FloatArray, drawH: FloatArray,
    boundsW: FloatArray, boundsH: FloatArray, rect: RectF, turnIcon: android.graphics.drawable.Drawable?,
) {
    val id = hint.pieceId
    val home = game.layout.pieces[id].bounds
    val pcx = game.x(id) + boundsW[id] / 2
    val pcy = game.y(id) + boundsH[id] / 2
    val hcx = home.left + home.width / 2
    val hcy = home.top + home.height / 2
    val phase = 2f * PI_F * t / HINT_LOOP_SEC
    val pulse = 0.5f + 0.5f * sin(phase)
    masks.beginFrame()

    nc.save()
    nc.translate(cam.offsetX, cam.offsetY)
    nc.scale(cam.scale, cam.scale)

    // Its spot: a warm silhouette of the piece at home with a soft pulsing rim.
    paints.hintFill.alpha = (255 * (0.12f + 0.16f * pulse) * fade).toInt()
    rect.set(home.left, home.top, home.left + drawW[id], home.top + drawH[id])
    nc.drawBitmap(bitmaps[id], null, rect, paints.hintFill)
    val spot = masks.get(id)
    if (spot != null) {
        paints.hintGlow.alpha = (255 * (0.22f + 0.20f * pulse) * fade).toInt()
        maskRect(rect, spot.nearOffX, spot.nearOffY, spot.nearW, spot.nearH, home.left, home.top, 1f, boundsW[id], boundsH[id])
        nc.drawBitmap(spot.near, null, rect, paints.hintGlow)
    }

    // The trail: dots marching from the piece towards its spot, twinkling and fading at both ends.
    val dx = hcx - pcx
    val dy = hcy - pcy
    val lenPx = hypot(dx, dy) * cam.scale
    if (lenPx > 24f * density) {
        val n = (lenPx / (26f * density)).toInt().coerceIn(2, 60)
        val shift = (t * 0.6f) % 1f
        val unit = density / cam.scale // one dp in world units
        for (i in 0 until n) {
            val f = (i + shift) / n
            val edge = min(f, 1f - f) * 6f
            val a = min(1f, edge) * fade
            val r = (3.2f + 1.6f * sin(t * 7f + i * 1.7f)) * unit
            val x = pcx + dx * f
            val y = pcy + dy * f
            paints.hintDot.alpha = (255 * 0.85f * a).toInt()
            nc.drawCircle(x, y, r * 1.8f, paints.hintDot)
            paints.hintDotCore.alpha = (255 * a).toInt()
            nc.drawCircle(x, y, r * 0.8f, paints.hintDotCore)
        }
    }

    // The piece (and its group) glows, swells a little and wiggles about the hinted piece's centre.
    val gm = game.groupMembers(id)
    if (gm.size <= GLOW_MAX_PIECES) {
        val swell = 1f + 0.03f * pulse * fade
        nc.save()
        nc.rotate(HINT_WIGGLE_DEG * sin(phase) * fade, pcx, pcy)
        nc.scale(swell, swell, pcx, pcy)
        for (k in 0 until gm.size) {
            val m = gm[k]
            val pm = masks.get(m) ?: continue
            val rot = game.rotation(m)
            val cx = game.x(m) + boundsW[m] / 2
            val cy = game.y(m) + boundsH[m] / 2
            paints.hintGlow.alpha = (255 * 0.55f * fade).toInt()
            maskRect(rect, pm.farOffX, pm.farOffY, pm.farW, pm.farH, game.x(m), game.y(m), 1f, boundsW[m], boundsH[m])
            drawTurned(nc, pm.far, rect, paints.hintGlow, rot, cx, cy)
            paints.hintGlow.alpha = (255 * (0.65f + 0.30f * pulse) * fade).toInt()
            maskRect(rect, pm.nearOffX, pm.nearOffY, pm.nearW, pm.nearH, game.x(m), game.y(m), 1f, boundsW[m], boundsH[m])
            drawTurned(nc, pm.near, rect, paints.hintGlow, rot, cx, cy)
        }
        for (k in 0 until gm.size) {
            val m = gm[k]
            rect.set(game.x(m), game.y(m), game.x(m) + drawW[m], game.y(m) + drawH[m])
            drawTurned(nc, bitmaps[m], rect, paints.piece, game.rotation(m), game.x(m) + boundsW[m] / 2, game.y(m) + boundsH[m] / 2)
        }
        nc.restore()
    }
    nc.restore()

    // The turn arrow, in screen space just above the piece (grown-up rotation only).
    if (hint.needsTurnDeg != 0f && turnIcon != null) {
        val s = cam.toScreen(Offset(pcx, pcy))
        val size = 44f * density
        val bob = 3f * density * sin(phase)
        val cx = s.x
        val cy = s.y - boundsH[id] * cam.scale / 2 - size * 0.7f + bob
        paints.hintBadge.alpha = (255 * 0.9f * fade).toInt()
        nc.drawCircle(cx, cy, size * 0.62f, paints.hintBadge)
        nc.save()
        if (hint.needsTurnDeg < 0f) nc.scale(-1f, 1f, cx, cy) // counter-clockwise: mirror the arrow
        turnIcon.setBounds((cx - size / 2).toInt(), (cy - size / 2).toInt(), (cx + size / 2).toInt(), (cy + size / 2).toInt())
        turnIcon.alpha = (255 * fade).toInt()
        turnIcon.draw(nc)
        nc.restore()
    }
}

/** World rect of a blur mask for a piece drawn at ([x], [y]), optionally grown by [spread] about the piece's centre. */
private fun maskRect(
    out: RectF, offX: Float, offY: Float, w: Float, h: Float,
    x: Float, y: Float, spread: Float, pieceW: Float, pieceH: Float,
) {
    val l = x + offX
    val t = y + offY
    if (spread == 1f) {
        out.set(l, t, l + w, t + h)
    } else {
        val cx = x + pieceW / 2
        val cy = y + pieceH / 2
        out.set(cx + (l - cx) * spread, cy + (t - cy) * spread, cx + (l + w - cx) * spread, cy + (t + h - cy) * spread)
    }
}

private const val GLOW_MAX_PIECES = 40
private const val TAP_TURN_DEG = 90f
/** Two twisting fingers closer than this (dp) don't turn the group. */
private const val TWIST_MIN_GAP_DP = 24f
private const val HINT_FADE_NANOS = 400_000_000L
private const val HINT_WIGGLE_DEG = 4f
private const val HINT_LOOP_SEC = 1.2f
private const val PI_F = Math.PI.toFloat()
private const val RAD_TO_DEG = (180.0 / Math.PI).toFloat()

/** Draws [bmp] into [r], turned [deg] degrees (clockwise) about ([cx], [cy]); a plain draw when upright. */
private fun drawTurned(nc: android.graphics.Canvas, bmp: Bitmap, r: RectF, paint: Paint, deg: Float, cx: Float, cy: Float) {
    if (deg == 0f) {
        nc.drawBitmap(bmp, null, r, paint)
        return
    }
    nc.save()
    nc.rotate(deg, cx, cy)
    nc.drawBitmap(bmp, null, r, paint)
    nc.restore()
}

/** Runs [block] on the native canvas with the camera transform applied (world coordinates). */
private inline fun DrawScope.withWorld(cam: Camera, block: (android.graphics.Canvas) -> Unit) {
    drawIntoCanvas { c ->
        val nc = c.nativeCanvas
        nc.save()
        nc.translate(cam.offsetX, cam.offsetY)
        nc.scale(cam.scale, cam.scale)
        block(nc)
        nc.restore()
    }
}

/** Topmost loose table piece (not in the tray) under [world]: exact bounds first, then bounds grown to at least [minGrabPx] on screen. */
private fun hitTest(
    vm: PlayViewModel,
    view: TableView,
    world: Offset,
    boundsW: FloatArray,
    boundsH: FloatArray,
    minGrabPx: Float,
): Int? {
    val game = vm.game
    val cam = view.camera ?: return null
    val z = game.zOrder
    for (k in z.size - 1 downTo 0) {
        val id = z[k]
        if (game.isLocked(id) || game.isInTray(id)) continue // tray pieces keep stale table positions
        if (insideTurned(game, id, world, boundsW[id] / 2, boundsH[id] / 2)) return id
    }
    val minWorld = minGrabPx / cam.scale
    for (k in z.size - 1 downTo 0) {
        val id = z[k]
        if (game.isLocked(id) || game.isInTray(id)) continue // tray pieces keep stale table positions
        val gx = max(0f, (minWorld - boundsW[id]) / 2)
        val gy = max(0f, (minWorld - boundsH[id]) / 2)
        if (gx == 0f && gy == 0f) continue
        if (insideTurned(game, id, world, boundsW[id] / 2 + gx, boundsH[id] / 2 + gy)) return id
    }
    return null
}

/**
 * True if [world] is inside piece [id]'s bounds rect grown to half-size ([hw], [hh]) about its centre, turned
 * with the piece: the point is turned back into the piece's own frame (exact, and only run at touch-down).
 */
private fun insideTurned(game: PuzzleGame, id: Int, world: Offset, hw: Float, hh: Float): Boolean {
    val bw = game.layout.pieces[id].bounds.width
    val bh = game.layout.pieces[id].bounds.height
    var dx = world.x - (game.x(id) + bw / 2)
    var dy = world.y - (game.y(id) + bh / 2)
    val rot = game.rotation(id)
    if (rot != 0f) {
        val rad = Math.toRadians(rot.toDouble())
        val c = kotlin.math.cos(rad).toFloat()
        val s = kotlin.math.sin(rad).toFloat()
        val lx = dx * c + dy * s
        val ly = -dx * s + dy * c
        dx = lx; dy = ly
    }
    return abs(dx) <= hw && abs(dy) <= hh
}

private class Paints {
    val piece = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    val ghost = Paint(Paint.FILTER_BITMAP_FLAG).apply { alpha = (GHOST_ALPHA * 255).toInt() }
    val peek = Paint(Paint.FILTER_BITMAP_FLAG).apply { alpha = (PEEK_ALPHA * 255).toInt() }
    val boardFill = Paint().apply { style = Paint.Style.FILL }
    val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = BOARD_OUTLINE_COLOR
    }
    /** Tints ALPHA_8 blur masks: the paint colour is the shadow colour, its alpha set per draw. */
    val shadow = Paint(Paint.FILTER_BITMAP_FLAG).apply { color = android.graphics.Color.BLACK }
    val glow = Paint(Paint.FILTER_BITMAP_FLAG).apply { color = GLOW_COLOR_RGB or 0xFF000000.toInt() }
    /** The helper's warm colour: a tinted piece silhouette, and a tint for the blur masks. */
    val hintFill = Paint(Paint.FILTER_BITMAP_FLAG).apply {
        colorFilter = PorterDuffColorFilter(0xFFFFB020.toInt(), PorterDuff.Mode.SRC_IN)
    }
    val hintGlow = Paint(Paint.FILTER_BITMAP_FLAG).apply { color = 0xFFFFB020.toInt() }
    val hintDot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFC040.toInt() }
    val hintDotCore = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE }
    val hintBadge = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE }
    val shadowFallback = Paint(Paint.FILTER_BITMAP_FLAG).apply {
        colorFilter = PorterDuffColorFilter(android.graphics.Color.argb(60, 0, 0, 0), PorterDuff.Mode.SRC_IN)
    }
}

/** The tray pieces currently shown (through the filter and sort), rebuilt when [PlayViewModel.trayTick] changes. */
private class TrayList(n: Int) {
    val ids = IntArray(n)
    var count = 0
    var builtFor = -1
}

/** Per-pointer drag info: which piece, and where on it the finger grabbed (world offset from its top-left). */
private class DragSlots {
    var pointer = LongArray(10)
    var piece = IntArray(10)
    var grabX = FloatArray(10)
    var grabY = FloatArray(10)
    /** The touched piece's table position when the drag started, to put it back if the drag is cancelled. */
    var startX = FloatArray(10)
    var startY = FloatArray(10)
    var size = 0

    fun add(pointerId: Long, pieceId: Int, gx: Float, gy: Float, sx: Float = 0f, sy: Float = 0f) {
        if (size == pointer.size) {
            pointer = pointer.copyOf(size * 2); piece = piece.copyOf(size * 2)
            grabX = grabX.copyOf(size * 2); grabY = grabY.copyOf(size * 2)
            startX = startX.copyOf(size * 2); startY = startY.copyOf(size * 2)
        }
        pointer[size] = pointerId; piece[size] = pieceId; grabX[size] = gx; grabY[size] = gy
        startX[size] = sx; startY[size] = sy
        size++
    }

    fun indexOf(pointerId: Long): Int {
        for (k in 0 until size) if (pointer[k] == pointerId) return k
        return -1
    }

    fun removeAt(k: Int) {
        for (j in k until size - 1) {
            pointer[j] = pointer[j + 1]; piece[j] = piece[j + 1]; grabX[j] = grabX[j + 1]; grabY[j] = grabY[j + 1]
            startX[j] = startX[j + 1]; startY[j] = startY[j + 1]
        }
        size--
    }
}
