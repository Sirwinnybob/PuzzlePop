package com.family.puzzlepop.engine.game

import com.family.puzzlepop.engine.Box
import com.family.puzzlepop.engine.PieceStyle
import com.family.puzzlepop.engine.Pt
import com.family.puzzlepop.engine.PuzzleGenerator
import com.family.puzzlepop.engine.PuzzleLayout
import com.family.puzzlepop.engine.PuzzleSpec
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PuzzleGameTrayTest {
    private val layout: PuzzleLayout =
        PuzzleGenerator.generate(PuzzleSpec(1200, 1200, 16, PieceStyle.CLASSIC, 1L))
    private val table = Box(-1000f, -1000f, 2200f, 2200f)

    private fun newGame() = PuzzleGame.newGame(layout, table, 1L)

    private fun home(id: Int): Pt = layout.pieces[id].bounds.let { Pt(it.left, it.top) }

    private fun lock(game: PuzzleGame, id: Int) {
        val h = home(id)
        val p = game.state.positions[id]
        game.moveGroup(id, h.x - p.x, h.y - p.y)
        assertEquals(DropResult.LOCKED, game.drop(id, 1f))
    }

    @Test
    fun storeAndTake() {
        val game = newGame()
        assertTrue(game.storeInTray(5))
        assertTrue(game.isInTray(5))
        game.takeFromTray(5, 10f, 20f)
        assertFalse(game.isInTray(5))
        assertEquals(Pt(10f, 20f), game.state.positions[5])
        assertEquals(5, game.zOrder.last())
    }

    @Test
    fun groupsCannotEnterTray() {
        val game = newGame()
        val tol = PuzzleGame.defaultTolerance(layout)
        val h0 = home(0)
        val h1 = home(1)
        val p0 = game.state.positions[0]
        game.moveGroup(0, h0.x + 500f - p0.x, h0.y + 500f - p0.y)
        val p1 = game.state.positions[1]
        game.moveGroup(1, h1.x + 500f - p1.x, h1.y + 500f - p1.y)
        assertEquals(DropResult.JOINED, game.drop(1, tol))
        assertFalse(game.canStoreInTray(0))
        assertFalse(game.storeInTray(0))
    }

    @Test
    fun lockedCannotEnterTray() {
        val game = newGame()
        lock(game, 3)
        assertFalse(game.storeInTray(3))
    }

    @Test
    fun trayPiecesAreNotPlaced() {
        val game = newGame()
        for (i in 0 until 16) if (i != 7) lock(game, i)
        assertTrue(game.storeInTray(7))
        assertFalse(game.isComplete)
        assertEquals(15, game.placedCount)
    }

    @Test
    fun neighboursIgnoreTrayPieces() {
        val game = newGame()
        val tol = PuzzleGame.defaultTolerance(layout)
        assertTrue(game.storeInTray(1))
        val t1 = game.state.positions[1]
        val h0 = home(0)
        val h1 = home(1)
        val p0 = game.state.positions[0]
        game.moveGroup(0, t1.x - (h1.x - h0.x) - p0.x, t1.y - (h1.y - h0.y) - p0.y)
        assertEquals(DropResult.NONE, game.drop(0, tol))
    }

    @Test
    fun traySurvivesSnapshot() {
        val game = newGame()
        game.storeInTray(2)
        game.storeInTray(9)
        assertEquals(listOf(2, 9), PuzzleGame(layout, game.state).trayPieces)
    }

    @Test
    fun startInTray() {
        val a = PuzzleGame.newGame(layout, table, 1L, startInTray = true)
        val b = PuzzleGame.newGame(layout, table, 1L, startInTray = true)
        assertEquals(16, a.trayPieces.size)
        assertEquals((0 until 16).toSet(), a.trayPieces.toSet())
        assertEquals(a.trayPieces.toList(), b.trayPieces.toList())
    }

    @Test
    fun oldSaveWithoutTrayLoads() {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val s = json.decodeFromString(
            GameState.serializer(),
            """{"positions":[{"x":1.0,"y":2.0}],"groupOf":[0],"locked":[false],"zOrder":[0]}""",
        )
        assertEquals(emptyList<Int>(), s.tray)
    }

    @Test
    fun magnetCheck() {
        val game = newGame()
        val r = PuzzleGame.kidMagnetRadius(layout)
        val h = home(5)
        val p = game.state.positions[5]
        game.moveGroup(5, h.x + r * 0.9f - p.x, h.y - p.y)
        assertTrue(game.magnetCheck(5, r))
        game.moveGroup(5, r * 0.3f, 0f)
        assertFalse(game.magnetCheck(5, r))
    }
}
