package com.family.puzzlepop.engine.game

/**
 * Chooses one easy loose piece to highlight for a stuck child. Priority: a piece next to a locked piece,
 * then a corner, then an edge, then any piece. Ties go to the lowest id.
 */
object HintPicker {
    /**
     * Returns the id of the piece to hint, or null when no loose piece is visible and uncovered. Only pieces
     * that are not locked, not in the tray, [isVisible] and not [isCovered] qualify.
     */
    fun pick(game: PuzzleGame, isVisible: (pieceId: Int) -> Boolean, isCovered: (pieceId: Int) -> Boolean): Int? {
        val layout = game.layout
        val rows = layout.grid.rows
        val cols = layout.grid.cols
        val loose = layout.pieces.indices.filter {
            !game.isLocked(it) && !game.isInTray(it) && isVisible(it) && !isCovered(it)
        }
        if (loose.isEmpty()) return null

        fun adjacentToLocked(id: Int): Boolean {
            val r = layout.pieces[id].row
            val c = layout.pieces[id].col
            return NEIGHBOURS.any { (dr, dc) ->
                val nr = r + dr
                val nc = c + dc
                nr in 0 until rows && nc in 0 until cols && game.isLocked(nr * cols + nc)
            }
        }

        fun isCorner(id: Int): Boolean {
            val r = layout.pieces[id].row
            val c = layout.pieces[id].col
            return r in setOf(0, rows - 1) && c in setOf(0, cols - 1)
        }

        return loose.firstOrNull { adjacentToLocked(it) }
            ?: loose.firstOrNull { isCorner(it) }
            ?: loose.firstOrNull { layout.pieces[it].isEdge }
            ?: loose.first()
    }

    private val NEIGHBOURS = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)
}
