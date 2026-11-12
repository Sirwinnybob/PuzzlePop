package com.family.puzzlepop.engine.game

import com.family.puzzlepop.engine.Box
import com.family.puzzlepop.engine.PieceStyle
import com.family.puzzlepop.engine.Pt
import com.family.puzzlepop.engine.PuzzleGenerator
import com.family.puzzlepop.engine.PuzzleLayout
import com.family.puzzlepop.engine.PuzzleSpec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PuzzleGameTest {
    private val layout: PuzzleLayout =
        PuzzleGenerator.generate(PuzzleSpec(1200, 1200, 16, PieceStyle.CLASSIC, 1L)).also {
            assertEquals(4, it.grid.rows)
            assertEquals(4, it.grid.cols)
        }
    private val table = Box(-1000f, -1000f, 2200f, 2200f)

    private fun newGame() = PuzzleGame.newGame(layout, table, 1L)

    private fun home(id: Int): Pt = layout.pieces[id].bounds.let { Pt(it.left, it.top) }

    /** Moves the group of [id] so that piece [id] sits at [target]. */
    private fun place(game: PuzzleGame, id: Int, target: Pt) {
        val p = game.state.positions[id]
        game.moveGroup(id, target.x - p.x, target.y - p.y)
    }

    @Test
    fun dropNearHomeLocks() {
        val game = newGame()
        val tol = PuzzleGame.defaultTolerance(layout)
        val h = home(5)
        place(game, 5, Pt(h.x + tol * 0.9f, h.y))
        assertEquals(DropResult.LOCKED, game.drop(5, tol))
        assertEquals(h, game.state.positions[5])
        assertTrue(game.state.locked[5])
    }

    @Test
    fun dropFarAwayDoesNothing() {
        val game = newGame()
        val tol = PuzzleGame.defaultTolerance(layout)
        val h = home(5)
        place(game, 5, Pt(h.x + tol * 1.5f, h.y))
        assertEquals(DropResult.NONE, game.drop(5, tol))
        assertFalse(game.state.locked[5])
    }

    /** Puts pieces 0 and 1 off-board at their correct relative offset plus (3, 2) px. */
    private fun joinZeroAndOne(game: PuzzleGame, tol: Float): DropResult {
        val h0 = home(0)
        val h1 = home(1)
        place(game, 0, Pt(h0.x + 500f, h0.y + 500f))
        place(game, 1, Pt(h1.x + 500f + 3f, h1.y + 500f + 2f))
        return game.drop(1, tol)
    }

    @Test
    fun neighboursJoinOffBoard() {
        val game = newGame()
        val tol = PuzzleGame.defaultTolerance(layout)
        assertEquals(DropResult.JOINED, joinZeroAndOne(game, tol))
        assertEquals(game.state.groupOf[0], game.state.groupOf[1])
        val before = game.state.positions
        game.moveGroup(0, 10f, 0f)
        val after = game.state.positions
        assertEquals(before[0].x + 10f, after[0].x)
        assertEquals(before[1].x + 10f, after[1].x)
        assertEquals(before[1].y, after[1].y)
    }

    @Test
    fun lockingAGroupLocksAll() {
        val game = newGame()
        val tol = PuzzleGame.defaultTolerance(layout)
        joinZeroAndOne(game, tol)
        val h0 = home(0)
        place(game, 0, Pt(h0.x + 5f, h0.y + 5f))
        assertEquals(DropResult.LOCKED, game.drop(0, tol))
        assertTrue(game.state.locked[0])
        assertTrue(game.state.locked[1])
        assertEquals(home(1), game.state.positions[1])
    }

    @Test
    fun completeWhenAllLocked() {
        val game = newGame()
        val tol = PuzzleGame.defaultTolerance(layout)
        assertFalse(game.isComplete)
        for (id in 0 until 16) {
            place(game, id, home(id))
            game.drop(id, tol)
        }
        assertTrue(game.isComplete)
        assertEquals(16, game.placedCount)
    }

    @Test
    fun newGameScattersOffBoard() {
        val game = newGame()
        val board = Box(0f, 0f, layout.spec.imageWidth.toFloat(), layout.spec.imageHeight.toFloat())
        layout.pieces.forEach { piece ->
            val p = game.state.positions[piece.id]
            val r = Box(p.x, p.y, p.x + piece.bounds.width, p.y + piece.bounds.height)
            val overlaps = r.left < board.right && r.right > board.left && r.top < board.bottom && r.bottom > board.top
            assertFalse(overlaps, "piece ${piece.id} overlaps board")
            assertTrue(r.left >= table.left && r.right <= table.right && r.top >= table.top && r.bottom <= table.bottom)
        }
        assertEquals(game.state.positions, newGame().state.positions)
    }
}
