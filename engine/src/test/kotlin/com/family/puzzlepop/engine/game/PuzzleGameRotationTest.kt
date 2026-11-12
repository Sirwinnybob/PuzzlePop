package com.family.puzzlepop.engine.game

import com.family.puzzlepop.engine.Box
import com.family.puzzlepop.engine.PieceStyle
import com.family.puzzlepop.engine.Pt
import com.family.puzzlepop.engine.PuzzleGenerator
import com.family.puzzlepop.engine.PuzzleSpec
import com.family.puzzlepop.engine.options.RotationMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PuzzleGameRotationTest {
    private val layout = PuzzleGenerator.generate(PuzzleSpec(1200, 1200, 16, PieceStyle.CLASSIC, 1L))
    private val table = Box(-1000f, -1000f, 2200f, 2200f)
    private val tol = 30f

    private fun newGame(rotation: RotationMode = RotationMode.OFF) =
        PuzzleGame.newGame(layout, table, 1L, rotation = rotation)

    private fun halfW(id: Int) = layout.pieces[id].bounds.width / 2f
    private fun halfH(id: Int) = layout.pieces[id].bounds.height / 2f
    private fun cx(g: PuzzleGame, id: Int) = g.x(id) + halfW(id)
    private fun cy(g: PuzzleGame, id: Int) = g.y(id) + halfH(id)
    private fun homeCx(id: Int) = layout.pieces[id].bounds.let { it.left + it.width / 2f }
    private fun homeCy(id: Int) = layout.pieces[id].bounds.let { it.top + it.height / 2f }

    private fun placeCentre(g: PuzzleGame, id: Int, x: Float, y: Float) =
        g.moveGroup(id, x - halfW(id) - g.x(id), y - halfH(id) - g.y(id))

    private fun placeHome(g: PuzzleGame, id: Int) = placeCentre(g, id, homeCx(id), homeCy(id))

    @Test
    fun rotateQuarterTurn() {
        val g = newGame()
        val (x, y) = cx(g, 5) to cy(g, 5)
        g.rotateGroup(5, 90f)
        assertEquals(90f, g.rotation(5))
        assertEquals(x, cx(g, 5), 0.01f)
        assertEquals(y, cy(g, 5), 0.01f)
    }

    @Test
    fun rotateAboutFixedPointOrbitsAndReturns() {
        val g = newGame()
        val (x, y) = cx(g, 5) to cy(g, 5)
        g.rotateGroupAbout(5, 90f, x + 100f, y)
        assertEquals(90f, g.rotation(5))
        assertEquals(x + 100f, cx(g, 5), 0.01f) // (-100, 0) from the pivot turns to (0, -100)
        assertEquals(y - 100f, cy(g, 5), 0.01f)
        g.rotateGroupAbout(5, -90f, x + 100f, y)
        assertEquals(0f, g.rotation(5))
        assertEquals(x, cx(g, 5), 0.01f)
        assertEquals(y, cy(g, 5), 0.01f)
    }

    @Test
    fun lockRequiresUpright() {
        val g = newGame()
        placeHome(g, 5)
        g.rotateGroup(5, 90f)
        assertEquals(DropResult.NONE, g.drop(5, tol))
        g.rotateGroup(5, 270f)
        assertEquals(DropResult.LOCKED, g.drop(5, tol))

        val h = newGame()
        placeHome(h, 7)
        h.rotateGroup(7, 7f)
        assertEquals(DropResult.LOCKED, h.drop(7, tol))
        assertEquals(0f, h.rotation(7))
    }

    @Test
    fun joinRequiresSameRotation() {
        val g = newGame()
        placeCentre(g, 0, 400f, 400f)
        placeCentre(g, 1, 400f + homeCx(1) - homeCx(0), 400f + homeCy(1) - homeCy(0))
        g.rotateGroup(1, 90f)
        // rotating moved nothing (single piece, own centre), so offset is still the unrotated one
        assertEquals(DropResult.NONE, g.drop(0, tol))
    }

    @Test
    fun joinRotatedNeighbours() {
        val g = newGame()
        placeCentre(g, 0, 400f, 400f)
        g.rotateGroup(0, 90f)
        g.rotateGroup(1, 90f)
        // R(90): (x, y) -> (-y, x)
        val vx = homeCx(1) - homeCx(0)
        val vy = homeCy(1) - homeCy(0)
        placeCentre(g, 1, 400f - vy, 400f + vx)
        assertEquals(DropResult.JOINED, g.drop(0, tol))
        assertEquals(g.groupId(0), g.groupId(1))
        assertEquals(90f, g.rotation(1))
    }

    @Test
    fun groupRotatesAroundCenter() {
        val g = newGame()
        placeCentre(g, 0, 400f, 400f)
        placeCentre(g, 1, 400f + homeCx(1) - homeCx(0), 400f + homeCy(1) - homeCy(0))
        assertEquals(DropResult.JOINED, g.drop(0, tol))
        val c0 = Pt(cx(g, 0), cy(g, 0))
        val c1 = Pt(cx(g, 1), cy(g, 1))
        val px = (c0.x + c1.x) / 2f
        val py = (c0.y + c1.y) / 2f
        g.rotateGroup(0, 90f)
        assertEquals(px - (c0.y - py), cx(g, 0), 0.01f)
        assertEquals(py + (c0.x - px), cy(g, 0), 0.01f)
        assertEquals(px - (c1.y - py), cx(g, 1), 0.01f)
        assertEquals(py + (c1.x - px), cy(g, 1), 0.01f)
        assertEquals(90f, g.rotation(0))
        assertEquals(90f, g.rotation(1))
    }

    @Test
    fun startRotationsDeterministic() {
        val a = newGame(RotationMode.QUARTER)
        val b = newGame(RotationMode.QUARTER)
        assertEquals(a.state.rotations, b.state.rotations)
        for (i in 0 until 16) assertEquals(0f, a.rotation(i) % 90f)
        assertTrue((0 until 16).any { a.rotation(it) != 0f })
        val off = newGame()
        for (i in 0 until 16) assertEquals(0f, off.rotation(i))
        assertEquals(off.state.positions, a.state.positions)
        assertEquals(off.state.zOrder, a.state.zOrder)
    }

    @Test
    fun uprightAllForKid() {
        val g = newGame(RotationMode.QUARTER)
        g.rotateGroup(2, 90f)
        placeHome(g, 3); g.rotateGroup(3, 0f)
        g.rotateGroup(3, 360f - g.rotation(3))
        assertEquals(DropResult.LOCKED, g.drop(3, tol))
        g.storeInTray(4)
        val trayRot = g.rotation(4)
        val before = (0 until 16).map { Pt(cx(g, it), cy(g, it)) }
        val lockedBefore = g.state.locked
        val changed = g.uprightAllForKid()
        assertTrue(changed)
        for (i in 0 until 16) {
            if (i == 4) assertEquals(trayRot, g.rotation(i)) else assertEquals(0f, g.rotation(i))
            assertEquals(before[i].x, cx(g, i), 0.01f)
            assertEquals(before[i].y, cy(g, i), 0.01f)
        }
        assertEquals(lockedBefore, g.state.locked)
        assertFalse(newGame().uprightAllForKid())
    }

    @Test
    fun rotationsRoundTrip() {
        val g = newGame(RotationMode.ANY)
        val copy = PuzzleGame(layout, g.state)
        for (i in 0 until 16) assertEquals(g.rotation(i), copy.rotation(i))
    }
}
