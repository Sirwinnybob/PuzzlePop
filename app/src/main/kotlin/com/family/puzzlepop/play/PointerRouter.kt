package com.family.puzzlepop.play

import androidx.compose.ui.geometry.Offset
import com.family.puzzlepop.engine.options.RotationMode

/**
 * Decides what each pointer controls. A pointer that lands on a piece drags that piece; a pointer on empty
 * table drives the camera (one camera pointer pans, two pinch-zoom and pan).
 *
 * **Pinch grace.** Two fingers of a pinch never land at exactly the same moment, so a piece drag starts
 * *tentative*: until it has moved past [touchSlop] or is [PINCH_GRACE_MS] old, a second finger landing on
 * the table or on a piece turns both into camera pointers and the drag is cancelled (the canvas reads
 * [cancelledCount] / [cancelledPointer] / [cancelledPiece] right after [down] and puts the piece back).
 * The same holds the other way round: a finger on a piece just after a still-fresh table finger joins
 * the pinch. A committed drag is never taken over: a later finger on the table pans, and on another
 * piece it drags that piece (two people playing at once).
 *
 * **Rejection** ([Route.Ignored], kept until the pointer lifts): a contact the caller flags as a palm,
 * a touch while a stylus is down (a stylus landing also silences touches already down), and a third or
 * later finger while two camera pointers are active.
 *
 * **Kid mode** (`kid = true` on [down]): table fingers are [Route.Camera] (the canvas only moves the camera
 * while two of them are down, so one missed tap never scrolls the board) and a third is ignored; piece
 * drags are committed at once (no grace, so a finger on a piece never turns into a pinch), and several
 * fingers can drag separate pieces.
 *
 * **Rotation** ([rotation], off in kid mode and for pieces [canRotate] refuses):
 *  - a piece pointer that lifts within [TAP_MAX_MS] and never passed [touchSlop] is a tap ([tapPiece]);
 *    the canvas turns the group a quarter turn (QUARTER or ANY);
 *  - with ANY, a second finger landing on the **same group** as a still-tentative drag (inside the pinch
 *    grace) makes both [Route.Twist] instead of a pinch; the tentative drag is reported cancelled like a
 *    pinch's. A later finger on the twisted group is ignored, and when either twist finger lifts the
 *    other becomes [Route.Ignored]. Different pieces, or the table, keep the stage 2 pinch.
 *
 * Storage is a few small arrays (no boxing), because [routeOf] is called for every pointer on every
 * touch frame.
 *
 * The piece tray is checked first, in screen space (it never moves with the camera): a pointer on a tray
 * piece is [Route.TrayPiece] until the canvas decides, once it moves, whether it takes the piece out
 * ([reroute] to [Route.DragPiece]) or scrolls the tray ([Route.TrayScroll]); a pointer on empty tray space
 * scrolls it. Tray pointers never drive the camera.
 *
 * @param touchSlop screen distance a tentative piece pointer must move to commit its drag.
 * @param trayTest for a screen point: a tray piece id, [TRAY_EMPTY] (in the tray, no piece), or
 *   [NOT_IN_TRAY].
 * @param canRotate false for a piece that can't turn (locked, in the tray). Checked at tap and twist time only.
 * @param groupOf a piece's group id (two fingers on one group twist it).
 * @param hitTest returns the topmost unlocked piece id under a world point, or null for empty table.
 */
class PointerRouter(
    private val touchSlop: Float = 8f,
    private val trayTest: (screen: Offset) -> Int = { NOT_IN_TRAY },
    private val canRotate: (pieceId: Int) -> Boolean = { true },
    private val groupOf: (pieceId: Int) -> Int = { it },
    private val hitTest: (world: Offset) -> Int?,
) {

    sealed interface Route {
        data class DragPiece(val pieceId: Int) : Route
        data object Camera : Route
        /** Down on a piece in the tray; not yet a drag or a scroll. */
        data class TrayPiece(val pieceId: Int) : Route
        /** Scrolls the tray. */
        data object TrayScroll : Route
        /** One of the two fingers twisting the group of [pieceId] (ANY rotation). */
        data class Twist(val pieceId: Int) : Route
        /** Rejected (palm, extra finger, touch under a stylus): does nothing until it lifts. */
        data object Ignored : Route
    }

    private var ids = LongArray(INITIAL)
    private var routes = arrayOfNulls<Route>(INITIAL)
    private var downAt = LongArray(INITIAL)
    private var downX = FloatArray(INITIAL)
    private var downY = FloatArray(INITIAL)
    /** Still inside the pinch grace (not moved past slop); also check the age with [isFresh]. */
    private var fresh = BooleanArray(INITIAL)
    private var stylus = BooleanArray(INITIAL)
    /** Passed [touchSlop] at some point (so it can't be a tap). */
    private var moved = BooleanArray(INITIAL)
    private var kidPtr = BooleanArray(INITIAL)
    private var count = 0
    /** Group being twisted, or -1. */
    private var twistGroup = -1

    /** Which rotation gestures apply (the canvas keeps it in sync with the options). */
    var rotation: RotationMode = RotationMode.OFF

    private var cancelledIds = LongArray(INITIAL)
    private var cancelledPieces = IntArray(INITIAL)

    /** How many active pointers drive the camera (1 = pan, 2 = pinch). */
    var cameraPointerCount: Int = 0
        private set

    /** Number of active pointers. */
    val size: Int get() = count

    /** How many tentative drags the last [down] cancelled (their pointers are now camera or ignored). */
    var cancelledCount: Int = 0
        private set

    /** The pointer of cancelled drag [index] (0 until [cancelledCount]). */
    fun cancelledPointer(index: Int): Long = cancelledIds[index]

    /** The piece of cancelled drag [index] (0 until [cancelledCount]). */
    fun cancelledPiece(index: Int): Int = cancelledPieces[index]

    /**
     * Routes a new pointer at [world] (table) / [screen] (canvas px), landing at [nowMs] (uptime). An
     * active pointer id is re-routed. Afterwards, [cancelledCount] says which tentative drags this cancelled.
     *
     * @param kid kid mode: no pinch grace (table fingers still drive the camera).
     * @param stylus the pointer is a pen.
     * @param palm the contact is too big to be a fingertip.
     */
    fun down(
        pointerId: Long,
        world: Offset,
        screen: Offset = world,
        nowMs: Long = 0L,
        kid: Boolean = false,
        stylus: Boolean = false,
        palm: Boolean = false,
    ): Route {
        up(pointerId)
        cancelledCount = 0
        val route = routeNew(world, screen, nowMs, kid, stylus, palm)
        if (count == ids.size) grow()
        ids[count] = pointerId
        routes[count] = route
        downAt[count] = nowMs
        downX[count] = screen.x
        downY[count] = screen.y
        // Only touch drags and table pointers on the grown-up table can still join a pinch.
        fresh[count] = !kid && !stylus && (route is Route.DragPiece || route === Route.Camera)
        this.stylus[count] = stylus
        moved[count] = false
        kidPtr[count] = kid
        count++
        if (route === Route.Camera) cameraPointerCount++
        return route
    }

    private fun routeNew(world: Offset, screen: Offset, nowMs: Long, kid: Boolean, isStylus: Boolean, palm: Boolean): Route {
        if (isStylus) {
            // The pen wins: the hand that is resting on the screen stops doing anything.
            for (k in 0 until count) {
                if (stylus[k]) continue
                val r = routes[k]
                if (r is Route.DragPiece && isFresh(k, nowMs)) {
                    cancel(k, r.pieceId)
                    setRoute(k, Route.Ignored)
                } else if (r === Route.Camera) {
                    setRoute(k, Route.Ignored)
                }
            }
        } else {
            if (palm) return Route.Ignored
            for (k in 0 until count) if (stylus[k]) return Route.Ignored
        }
        val tray = trayTest(screen)
        if (tray >= 0) return Route.TrayPiece(tray)
        if (tray == TRAY_EMPTY) return Route.TrayScroll
        val piece = hitTest(world)
        // Hands off a group that two fingers are already twisting.
        if (piece != null && twistGroup >= 0 && groupOf(piece) == twistGroup) return Route.Ignored
        if (kid) return if (piece != null) Route.DragPiece(piece) else if (cameraPointerCount >= 2) Route.Ignored else Route.Camera
        if (cameraPointerCount >= 2) return Route.Ignored
        if (!isStylus) {
            if (cameraPointerCount == 0) {
                // The first finger of a pinch landed on a piece a moment ago: both fingers are the camera.
                val k = latestFresh(nowMs, drag = true)
                if (k >= 0) {
                    val first = (routes[k] as Route.DragPiece).pieceId
                    if (rotation == RotationMode.ANY && piece != null && groupOf(piece) == groupOf(first) && canRotate(first)) {
                        // Both fingers on one group: twist it instead of pinching.
                        cancel(k, first)
                        val twist = Route.Twist(first)
                        setRoute(k, twist)
                        twistGroup = groupOf(first)
                        return twist
                    }
                    cancel(k, first)
                    setRoute(k, Route.Camera)
                    return Route.Camera
                }
            } else if (piece != null && latestFresh(nowMs, drag = false) >= 0) {
                // The first finger of a pinch landed on the table a moment ago: this one joins it.
                return Route.Camera
            }
        }
        return if (piece != null) Route.DragPiece(piece) else Route.Camera
    }

    /**
     * An active pointer moved to [screen] at [nowMs]: past [touchSlop] (or after the grace time) a
     * tentative drag is committed and can no longer become part of a pinch.
     */
    fun move(pointerId: Long, screen: Offset, nowMs: Long) {
        val i = indexOf(pointerId)
        if (i < 0 || moved[i]) return
        val dx = screen.x - downX[i]
        val dy = screen.y - downY[i]
        if (dx * dx + dy * dy > touchSlop * touchSlop) {
            moved[i] = true
            fresh[i] = false
        } else if (fresh[i] && nowMs - downAt[i] >= PINCH_GRACE_MS) {
            fresh[i] = false
        }
    }

    /**
     * Call just before [up] with the lift position and time: the piece a quick tap turns (a piece drag that
     * never passed [touchSlop] and lasted at most [TAP_MAX_MS], with rotation on and outside kid mode), or -1.
     */
    fun tapPiece(pointerId: Long, screen: Offset, nowMs: Long): Int {
        val i = indexOf(pointerId)
        if (i < 0 || rotation == RotationMode.OFF || kidPtr[i] || moved[i]) return -1
        val r = routes[i] as? Route.DragPiece ?: return -1
        if (nowMs - downAt[i] > TAP_MAX_MS) return -1
        val dx = screen.x - downX[i]
        val dy = screen.y - downY[i]
        if (dx * dx + dy * dy > touchSlop * touchSlop) return -1
        return if (canRotate(r.pieceId)) r.pieceId else -1
    }

    /** Releases a pointer and returns the route it had, or null if it was not active. */
    fun up(pointerId: Long): Route? {
        val i = indexOf(pointerId)
        if (i < 0) return null
        val route = routes[i]!!
        // Keep the remaining pointers in down order.
        for (k in i until count - 1) {
            ids[k] = ids[k + 1]
            routes[k] = routes[k + 1]
            downAt[k] = downAt[k + 1]
            downX[k] = downX[k + 1]
            downY[k] = downY[k + 1]
            fresh[k] = fresh[k + 1]
            stylus[k] = stylus[k + 1]
            moved[k] = moved[k + 1]
            kidPtr[k] = kidPtr[k + 1]
        }
        count--
        routes[count] = null
        if (route === Route.Camera) cameraPointerCount--
        if (route is Route.Twist) {
            // The twist ends with either finger; the other does nothing until it lifts.
            for (k in 0 until count) if (routes[k] is Route.Twist) routes[k] = Route.Ignored
            twistGroup = -1
        }
        return route
    }

    /** Changes an active pointer's route (a tray piece becoming a drag or a scroll). It is committed. */
    fun reroute(pointerId: Long, route: Route) {
        val i = indexOf(pointerId)
        if (i < 0) return
        setRoute(i, route)
        fresh[i] = false
    }

    /** True while [pointerId] is a piece drag that could still turn into part of a pinch. */
    fun isTentativeDrag(pointerId: Long, nowMs: Long): Boolean {
        val i = indexOf(pointerId)
        return i >= 0 && routes[i] is Route.DragPiece && isFresh(i, nowMs)
    }

    /** The route of an active pointer, or null. */
    fun routeOf(pointerId: Long): Route? {
        val i = indexOf(pointerId)
        return if (i < 0) null else routes[i]
    }

    /** The pointer id at [index] (0 until [size]), in down order. */
    fun pointerAt(index: Int): Long = ids[index]

    /** The route at [index] (0 until [size]), in down order. */
    fun routeAt(index: Int): Route = routes[index]!!

    /** Forgets every pointer. */
    fun clear() {
        for (k in 0 until count) routes[k] = null
        count = 0
        cameraPointerCount = 0
        cancelledCount = 0
        twistGroup = -1
    }

    private fun setRoute(i: Int, route: Route) {
        if (routes[i] === Route.Camera) cameraPointerCount--
        routes[i] = route
        if (route === Route.Camera) cameraPointerCount++
    }

    private fun isFresh(i: Int, nowMs: Long): Boolean = fresh[i] && nowMs - downAt[i] < PINCH_GRACE_MS

    /** The most recent fresh pointer that is a tentative drag ([drag]) or a table finger, or -1. */
    private fun latestFresh(nowMs: Long, drag: Boolean): Int {
        for (k in count - 1 downTo 0) {
            if (!isFresh(k, nowMs)) continue
            val r = routes[k]
            if (if (drag) r is Route.DragPiece else r === Route.Camera) return k
        }
        return -1
    }

    private fun cancel(i: Int, pieceId: Int) {
        if (cancelledCount == cancelledIds.size) {
            cancelledIds = cancelledIds.copyOf(cancelledCount * 2)
            cancelledPieces = cancelledPieces.copyOf(cancelledCount * 2)
        }
        cancelledIds[cancelledCount] = ids[i]
        cancelledPieces[cancelledCount] = pieceId
        cancelledCount++
        fresh[i] = false
    }

    private fun grow() {
        val n = count * 2
        ids = ids.copyOf(n)
        routes = routes.copyOf(n)
        downAt = downAt.copyOf(n)
        downX = downX.copyOf(n)
        downY = downY.copyOf(n)
        fresh = fresh.copyOf(n)
        stylus = stylus.copyOf(n)
        moved = moved.copyOf(n)
        kidPtr = kidPtr.copyOf(n)
    }

    private fun indexOf(pointerId: Long): Int {
        for (k in 0 until count) if (ids[k] == pointerId) return k
        return -1
    }

    companion object {
        /** [trayTest] result: the point is not on the tray. */
        const val NOT_IN_TRAY = -1
        /** [trayTest] result: on the tray, but not on a piece. */
        const val TRAY_EMPTY = -2
        /** How long a piece touch that hasn't moved can still turn into the first finger of a pinch. */
        const val PINCH_GRACE_MS = 150L
        /** A piece touch that lifts within this long, without passing slop, is a tap (turns the piece). */
        const val TAP_MAX_MS = 200L
        private const val INITIAL = 10
    }
}
