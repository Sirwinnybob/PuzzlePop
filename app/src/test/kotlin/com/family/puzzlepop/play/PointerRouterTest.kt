package com.family.puzzlepop.play

import androidx.compose.ui.geometry.Offset
import com.family.puzzlepop.engine.options.RotationMode
import org.junit.Assert.assertEquals
import org.junit.Test

class PointerRouterTest {
    // Piece 3 lives around x=10, piece 7 around x=20; everything else is empty table.
    private val router = PointerRouter(touchSlop = SLOP) { world ->
        when {
            world.x in 5f..15f -> 3
            world.x in 15f..25f -> 7
            else -> null
        }
    }
    private val onPiece3 = Offset(10f, 0f)
    private val onPiece7 = Offset(20f, 0f)
    private val onTable = Offset(100f, 0f)
    private val alsoOnTable = Offset(200f, 0f)

    // Stage 1 cases: the second finger comes well after the pinch grace window.
    @Test
    fun twoFingersOnTwoPiecesDragBoth() {
        assertEquals(PointerRouter.Route.DragPiece(3), router.down(1, onPiece3, nowMs = 0))
        assertEquals(PointerRouter.Route.DragPiece(7), router.down(2, onPiece7, nowMs = 400))
        assertEquals(0, router.cameraPointerCount)
    }

    @Test
    fun fingerOnPiecePlusFingerOnTableDoesNotZoom() {
        assertEquals(PointerRouter.Route.DragPiece(3), router.down(1, onPiece3, nowMs = 0))
        assertEquals(PointerRouter.Route.Camera, router.down(2, onTable, nowMs = 400))
        // Only one pointer drives the camera, so it pans and never pinches.
        assertEquals(1, router.cameraPointerCount)
    }

    @Test
    fun twoFingersOnTableIsCamera() {
        assertEquals(PointerRouter.Route.Camera, router.down(1, onTable, nowMs = 0))
        assertEquals(PointerRouter.Route.Camera, router.down(2, alsoOnTable, nowMs = 400))
        assertEquals(2, router.cameraPointerCount)
    }

    // Pinch-friendly routing: a piece drag is tentative for PINCH_GRACE_MS or until it moves past slop.
    @Test
    fun nearSimultaneousPinchOnPieceBecomesCamera() {
        assertEquals(PointerRouter.Route.DragPiece(3), router.down(1, onPiece3, nowMs = 0))
        assertEquals(PointerRouter.Route.Camera, router.down(2, onTable, nowMs = 40))
        assertEquals(PointerRouter.Route.Camera, router.routeOf(1))
        assertEquals(2, router.cameraPointerCount)
        assertEquals(1, router.cancelledCount)
        assertEquals(1L, router.cancelledPointer(0))
        assertEquals(3, router.cancelledPiece(0))
    }

    @Test
    fun nearSimultaneousBothOnPiecesBecomesCamera() {
        router.down(1, onPiece3, nowMs = 0)
        assertEquals(PointerRouter.Route.Camera, router.down(2, onPiece7, nowMs = 60))
        assertEquals(PointerRouter.Route.Camera, router.routeOf(1))
        assertEquals(2, router.cameraPointerCount)
        assertEquals(1, router.cancelledCount)
        assertEquals(3, router.cancelledPiece(0))
    }

    @Test
    fun nearSimultaneousTableThenPieceBecomesCamera() {
        router.down(1, onTable, nowMs = 0)
        assertEquals(PointerRouter.Route.Camera, router.down(2, onPiece7, nowMs = 30))
        assertEquals(2, router.cameraPointerCount)
        assertEquals(0, router.cancelledCount)
    }

    @Test
    fun lateSecondFingerKeepsDrag() {
        router.down(1, onPiece3, nowMs = 0)
        assertEquals(PointerRouter.Route.Camera, router.down(2, onTable, nowMs = 200))
        assertEquals(PointerRouter.Route.DragPiece(3), router.routeOf(1))
        assertEquals(1, router.cameraPointerCount)
        assertEquals(0, router.cancelledCount)
    }

    @Test
    fun movedPastSlopCommits() {
        router.down(1, onPiece3, nowMs = 0)
        router.move(1, Offset(10f + 2 * SLOP, 0f), nowMs = 30)
        assertEquals(PointerRouter.Route.Camera, router.down(2, onTable, nowMs = 60))
        assertEquals(PointerRouter.Route.DragPiece(3), router.routeOf(1))
        assertEquals(1, router.cameraPointerCount)
        assertEquals(0, router.cancelledCount)
    }

    @Test
    fun smallWiggleStaysTentative() {
        router.down(1, onPiece3, nowMs = 0)
        router.move(1, Offset(10f + SLOP / 2, 0f), nowMs = 30)
        assertEquals(PointerRouter.Route.Camera, router.down(2, onTable, nowMs = 60))
        assertEquals(1, router.cancelledCount)
    }

    @Test
    fun thirdFingerIgnoredDuringPinch() {
        router.down(1, onTable, nowMs = 0)
        router.down(2, alsoOnTable, nowMs = 20)
        assertEquals(PointerRouter.Route.Ignored, router.down(3, onPiece3, nowMs = 300))
        assertEquals(PointerRouter.Route.Ignored, router.down(4, onTable, nowMs = 310))
        assertEquals(2, router.cameraPointerCount)
        assertEquals(PointerRouter.Route.Ignored, router.up(3))
    }

    @Test
    fun palmIgnored() {
        assertEquals(PointerRouter.Route.Ignored, router.down(1, onPiece3, nowMs = 0, palm = true))
        assertEquals(0, router.cameraPointerCount)
        // The palm doesn't make a later finger's drag tentative-cancellable either.
        assertEquals(PointerRouter.Route.DragPiece(7), router.down(2, onPiece7, nowMs = 20))
    }

    @Test
    fun stylusSuppressesTouch() {
        assertEquals(PointerRouter.Route.DragPiece(3), router.down(1, onPiece3, nowMs = 0, stylus = true))
        // A hand resting while the pen is down does nothing, on a piece or on the table.
        assertEquals(PointerRouter.Route.Ignored, router.down(2, onPiece7, nowMs = 20))
        assertEquals(PointerRouter.Route.Ignored, router.down(3, onTable, nowMs = 400))
        assertEquals(PointerRouter.Route.DragPiece(3), router.routeOf(1))
        assertEquals(0, router.cameraPointerCount)
        assertEquals(0, router.cancelledCount)
        router.up(1)
        router.up(2)
        router.up(3)
        assertEquals(PointerRouter.Route.Camera, router.down(4, onTable, nowMs = 500))
    }

    @Test
    fun stylusTakesOverFromRestingHand() {
        router.down(1, onTable, nowMs = 0) // a palm-ish touch panning
        router.down(2, onPiece7, nowMs = 400) // a tentative touch drag
        assertEquals(PointerRouter.Route.DragPiece(3), router.down(3, onPiece3, nowMs = 450, stylus = true))
        assertEquals(PointerRouter.Route.Ignored, router.routeOf(1))
        assertEquals(PointerRouter.Route.Ignored, router.routeOf(2))
        assertEquals(0, router.cameraPointerCount)
        assertEquals(1, router.cancelledCount)
        assertEquals(7, router.cancelledPiece(0))
    }

    @Test
    fun kidModeTableFingersDriveCamera() {
        assertEquals(PointerRouter.Route.Camera, router.down(1, onTable, nowMs = 0, kid = true))
        assertEquals(PointerRouter.Route.Camera, router.down(2, alsoOnTable, nowMs = 20, kid = true))
        assertEquals(2, router.cameraPointerCount)
        // A third finger is ignored.
        assertEquals(PointerRouter.Route.Ignored, router.down(3, onTable, nowMs = 40, kid = true))
    }

    @Test
    fun kidModePieceDragNeverBecomesPinch() {
        assertEquals(PointerRouter.Route.DragPiece(3), router.down(1, onPiece3, nowMs = 0, kid = true))
        assertEquals(PointerRouter.Route.Camera, router.down(2, onTable, nowMs = 20, kid = true))
        assertEquals(PointerRouter.Route.DragPiece(3), router.routeOf(1))
        assertEquals(0, router.cancelledCount)
    }

    @Test
    fun kidModeImmediateDrag() {
        assertEquals(PointerRouter.Route.DragPiece(3), router.down(1, onPiece3, nowMs = 0, kid = true))
        assertEquals(PointerRouter.Route.DragPiece(7), router.down(2, onPiece7, nowMs = 20, kid = true))
        assertEquals(0, router.cameraPointerCount)
        assertEquals(0, router.cancelledCount)
    }

    @Test
    fun upReleasesRoute() {
        assertEquals(PointerRouter.Route.DragPiece(3), router.down(1, onPiece3, nowMs = 0))
        assertEquals(PointerRouter.Route.DragPiece(3), router.up(1))
        assertEquals(null, router.up(1))
        assertEquals(PointerRouter.Route.Camera, router.down(1, onTable, nowMs = 400))
        assertEquals(1, router.cameraPointerCount)
    }

    // A router with a tray at screen x >= 1000: piece 42 in the slot at y < 50, empty tray space below.
    private val trayRouter = PointerRouter(
        trayTest = { screen ->
            when {
                screen.x < 1000f -> PointerRouter.NOT_IN_TRAY
                screen.y < 50f -> 42
                else -> PointerRouter.TRAY_EMPTY
            }
        },
    ) { world -> if (world.x in 5f..15f) 3 else null }

    @Test
    fun downOnTrayPieceIsTrayPiece() {
        // The table under the tray has piece 3, but the tray wins.
        assertEquals(PointerRouter.Route.TrayPiece(42), trayRouter.down(1, world = onPiece3, screen = Offset(1100f, 10f)))
        assertEquals(0, trayRouter.cameraPointerCount)
    }

    @Test
    fun downOnEmptyTrayScrollsAndNeverPans() {
        assertEquals(PointerRouter.Route.TrayScroll, trayRouter.down(1, world = onTable, screen = Offset(1100f, 300f)))
        assertEquals(0, trayRouter.cameraPointerCount)
        assertEquals(PointerRouter.Route.Camera, trayRouter.down(2, world = onTable, screen = Offset(500f, 300f)))
        assertEquals(1, trayRouter.cameraPointerCount)
    }

    @Test
    fun outsideTrayRoutesAsBefore() {
        assertEquals(PointerRouter.Route.DragPiece(3), trayRouter.down(1, world = onPiece3, screen = Offset(10f, 0f)))
    }

    @Test
    fun trayPieceRerouteToDrag() {
        trayRouter.down(1, world = onTable, screen = Offset(1100f, 10f))
        trayRouter.reroute(1, PointerRouter.Route.DragPiece(42))
        assertEquals(PointerRouter.Route.DragPiece(42), trayRouter.routeOf(1))
        assertEquals(PointerRouter.Route.DragPiece(42), trayRouter.up(1))
        assertEquals(0, trayRouter.cameraPointerCount)
    }

    // Rotation. Piece 9 (x 30..40) stands in for a locked piece: the router may hit it, but it can't turn.
    private val rotRouter = PointerRouter(touchSlop = SLOP, canRotate = { it != 9 }) { world ->
        when {
            world.x in 5f..15f -> 3
            world.x in 15f..25f -> 7
            world.x in 30f..40f -> 9
            else -> null
        }
    }

    @Test
    fun shortTapRotates() {
        rotRouter.rotation = RotationMode.QUARTER
        rotRouter.down(1, onPiece3, nowMs = 0)
        rotRouter.move(1, Offset(10f + SLOP / 2, 0f), nowMs = 50)
        assertEquals(3, rotRouter.tapPiece(1, Offset(10f + SLOP / 2, 0f), nowMs = 150))
        rotRouter.rotation = RotationMode.ANY
        rotRouter.down(2, onPiece7, nowMs = 1000)
        assertEquals(7, rotRouter.tapPiece(2, onPiece7, nowMs = 1000 + PointerRouter.TAP_MAX_MS))
    }

    @Test
    fun dragNeverRotates() {
        rotRouter.rotation = RotationMode.ANY
        rotRouter.down(1, onPiece3, nowMs = 0)
        rotRouter.move(1, Offset(10f + 2 * SLOP, 0f), nowMs = 30)
        // Back where it started, and quick: still a drag, not a tap.
        rotRouter.move(1, onPiece3, nowMs = 60)
        assertEquals(-1, rotRouter.tapPiece(1, onPiece3, nowMs = 90))
        // Moved past slop only in the lifting event.
        rotRouter.down(2, onPiece7, nowMs = 1000)
        assertEquals(-1, rotRouter.tapPiece(2, Offset(20f + 2 * SLOP, 0f), nowMs = 1050))
        // Rotation OFF: a tap is just a pick-up and drop.
        rotRouter.rotation = RotationMode.OFF
        rotRouter.down(3, onPiece3, nowMs = 2000)
        assertEquals(-1, rotRouter.tapPiece(3, onPiece3, nowMs = 2050))
    }

    @Test
    fun longPressNoRotate() {
        rotRouter.rotation = RotationMode.QUARTER
        rotRouter.down(1, onPiece3, nowMs = 0)
        assertEquals(-1, rotRouter.tapPiece(1, onPiece3, nowMs = PointerRouter.TAP_MAX_MS + 1))
    }

    @Test
    fun tapOnLockedNoRotate() {
        rotRouter.rotation = RotationMode.ANY
        assertEquals(PointerRouter.Route.DragPiece(9), rotRouter.down(1, Offset(35f, 0f), nowMs = 0))
        assertEquals(-1, rotRouter.tapPiece(1, Offset(35f, 0f), nowMs = 50))
        // A tap on the table (where a real locked piece routes, as the hit test skips locked pieces) is no piece tap.
        rotRouter.down(2, onTable, nowMs = 1000)
        assertEquals(-1, rotRouter.tapPiece(2, onTable, nowMs = 1050))
    }

    @Test
    fun kidModeTapNoRotate() {
        rotRouter.rotation = RotationMode.ANY
        rotRouter.down(1, onPiece3, nowMs = 0, kid = true)
        assertEquals(-1, rotRouter.tapPiece(1, onPiece3, nowMs = 50))
        // Nor a twist: two kid fingers on one piece just both hold it.
        assertEquals(PointerRouter.Route.DragPiece(3), rotRouter.down(2, Offset(12f, 0f), nowMs = 40, kid = true))
        assertEquals(PointerRouter.Route.DragPiece(3), rotRouter.routeOf(1))
    }

    @Test
    fun twoFingersSamePieceTwist() {
        rotRouter.rotation = RotationMode.ANY
        assertEquals(PointerRouter.Route.DragPiece(3), rotRouter.down(1, Offset(8f, 0f), nowMs = 0))
        assertEquals(PointerRouter.Route.Twist(3), rotRouter.down(2, Offset(12f, 0f), nowMs = 60))
        assertEquals(PointerRouter.Route.Twist(3), rotRouter.routeOf(1))
        assertEquals(0, rotRouter.cameraPointerCount)
        // The tentative drag is cancelled (the piece goes back before it turns).
        assertEquals(1, rotRouter.cancelledCount)
        assertEquals(3, rotRouter.cancelledPiece(0))
        // A third finger on the twisted piece does nothing; one finger lifting ends the twist for both.
        assertEquals(PointerRouter.Route.Ignored, rotRouter.down(3, Offset(10f, 0f), nowMs = 300))
        assertEquals(PointerRouter.Route.Twist(3), rotRouter.up(2))
        assertEquals(PointerRouter.Route.Ignored, rotRouter.routeOf(1))
        assertEquals(-1, rotRouter.tapPiece(1, Offset(8f, 0f), nowMs = 310))

        // QUARTER: the same two fingers pinch, as in stage 2.
        rotRouter.clear()
        rotRouter.rotation = RotationMode.QUARTER
        rotRouter.down(1, Offset(8f, 0f), nowMs = 1000)
        assertEquals(PointerRouter.Route.Camera, rotRouter.down(2, Offset(12f, 0f), nowMs = 1060))
        assertEquals(2, rotRouter.cameraPointerCount)
    }

    @Test
    fun twoFingersDifferentPiecesUnchanged() {
        rotRouter.rotation = RotationMode.ANY
        rotRouter.down(1, onPiece3, nowMs = 0)
        assertEquals(PointerRouter.Route.Camera, rotRouter.down(2, onPiece7, nowMs = 60))
        assertEquals(PointerRouter.Route.Camera, rotRouter.routeOf(1))
        assertEquals(2, rotRouter.cameraPointerCount)
        rotRouter.clear()
        rotRouter.down(1, onPiece3, nowMs = 1000)
        assertEquals(PointerRouter.Route.Camera, rotRouter.down(2, onTable, nowMs = 1060))
        assertEquals(PointerRouter.Route.Camera, rotRouter.routeOf(1))
        // After the grace window, a second finger on the same piece is a second hold, not a twist.
        rotRouter.clear()
        rotRouter.down(1, Offset(8f, 0f), nowMs = 2000)
        assertEquals(PointerRouter.Route.DragPiece(3), rotRouter.down(2, Offset(12f, 0f), nowMs = 2400))
    }

    private companion object {
        const val SLOP = 8f
    }
}
