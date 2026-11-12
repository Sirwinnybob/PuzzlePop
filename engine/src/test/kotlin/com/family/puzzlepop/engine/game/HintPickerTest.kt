package com.family.puzzlepop.engine.game

import com.family.puzzlepop.engine.Box
import com.family.puzzlepop.engine.PieceStyle
import com.family.puzzlepop.engine.Pt
import com.family.puzzlepop.engine.PuzzleGenerator
import com.family.puzzlepop.engine.PuzzleLayout
import com.family.puzzlepop.engine.PuzzleSpec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class HintPickerTest {
    private val layout: PuzzleLayout =
        PuzzleGenerator.generate(PuzzleSpec(1200, 1200, 16, PieceStyle.CLASSIC, 1L)).also {
            assertEquals(4, it.grid.rows)
            assertEquals(4, it.grid.cols)
        }
    private val table = Box(-1000f, -1000f, 2200f, 2200f)
    private val allVisible: (Int) -> Boolean = { true }
    private val noneCovered: (Int) -> Boolean = { false }

    private fun newGame() = PuzzleGame.newGame(layout, table, 1L)

    private fun home(id: Int): Pt = layout.pieces[id].bounds.let { Pt(it.left, it.top) }

    private fun place(game: PuzzleGame, id: Int, target: Pt) {
        val p = game.state.positions[id]
        game.moveGroup(id, target.x - p.x, target.y - p.y)
    }

    /** Locks piece [id] by moving it home and dropping it. */
    private fun lock(game: PuzzleGame, id: Int) {
        place(game, id, home(id))
        assertEquals(DropResult.LOCKED, game.drop(id, PuzzleGame.defaultTolerance(layout)))
    }

    @Test
    fun prefersNeighbourOfPlaced() {
        // Piece 5 is interior (row 1, col 1). Its neighbours are 1, 4, 6, 9. Piece 0 is a corner
        // with a lower id, so the result proves adjacency beats corner priority.
        val game = newGame()
        lock(game, 5)
        assertEquals(1, HintPicker.pick(game, allVisible, noneCovered))
    }

    @Test
    fun thenCornerThenEdge() {
        val game = newGame()
        // Nothing placed: the lowest-id corner (0) wins.
        assertEquals(0, HintPicker.pick(game, allVisible, noneCovered))
        // Corner 0 hidden: corner 3 beats edge 1, which has a lower id.
        assertEquals(3, HintPicker.pick(game, { it != 0 }, noneCovered))
        // All of row 0 and the bottom corners hidden: edge 4 beats interior 5.
        val hiddenRow0AndCorners = setOf(0, 1, 2, 3, 12, 15)
        assertEquals(4, HintPicker.pick(game, { it !in hiddenRow0AndCorners }, noneCovered))
        // Every edge piece hidden: only interior pieces 5, 6, 9, 10 remain, so the lowest (5) wins.
        val edges = setOf(0, 1, 2, 3, 4, 7, 8, 11, 12, 13, 14, 15)
        assertEquals(5, HintPicker.pick(game, { it !in edges }, noneCovered))
    }

    @Test
    fun skipsTrayLockedInvisibleCovered() {
        val game = newGame()
        assertEquals(true, game.storeInTray(0))
        lock(game, 15)
        val invisible = setOf(11, 14, 3)
        val covered = setOf(12)
        val isVisible: (Int) -> Boolean = { it !in invisible }
        val isCovered: (Int) -> Boolean = { it in covered }
        // Corners 0 (tray), 3 (invisible), 12 (covered), 15 (locked) are out; 11 and 14 are invisible
        // so nothing is adjacent to the locked piece. The lowest visible uncovered edge is 1.
        assertEquals(1, HintPicker.pick(game, isVisible, isCovered))
    }

    @Test
    fun nullWhenNothingLoose() {
        val game = newGame()
        assertNull(HintPicker.pick(game, { false }, noneCovered))
        assertNull(HintPicker.pick(game, allVisible, { true }))
    }
}
