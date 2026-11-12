package com.family.puzzlepop.engine

import kotlinx.serialization.Serializable

/**
 * How the pieces are cut. Saved by name, so new values are only ever appended. The first three are the
 * original styles cut by [PuzzleGenerator]'s legacy path; the rest are cut by [StyleCuts].
 */
@Serializable
enum class PieceStyle { CHUNKY, CLASSIC, WOBBLY, RIBBON, WAVY_RIBBON, FREEFORM, HEARTS, PARTY }

/** True for the original styles whose version 1 output is pinned bit-exact. */
val PieceStyle.isLegacy: Boolean
    get() = this == PieceStyle.CHUNKY || this == PieceStyle.CLASSIC || this == PieceStyle.WOBBLY

/** Everything needed to deterministically regenerate a puzzle's piece layout. */
@Serializable
data class PuzzleSpec(
    val imageWidth: Int,
    val imageHeight: Int,
    val targetCount: Int,
    val style: PieceStyle,
    val seed: Long,
    /**
     * Which generator algorithm produced (and must regenerate) this puzzle's shapes. Saves store only the
     * seed, so any change to GridSizer, TabShapes, corner jitter or the PRNG that alters output for an
     * existing spec must bump this and keep the old path in [PuzzleGenerator].
     */
    val generatorVersion: Int = 1,
)

/** The four sides of a piece; each runs clockwise (top L->R, right T->B, bottom R->L, left B->T). */
data class Sides(val top: EdgeCurve, val right: EdgeCurve, val bottom: EdgeCurve, val left: EdgeCurve)

/** One jigsaw piece. Coordinates are image pixels; [bounds] top-left is the piece's home position. */
data class PieceShape(
    val id: Int,
    val row: Int,
    val col: Int,
    val sides: Sides,
    val bounds: Box,
) {
    /** The closed clockwise outline: top, right, bottom, left. */
    val outline: List<Cubic> get() = sides.top + sides.right + sides.bottom + sides.left

    /**
     * True when the piece sits on the puzzle border. Border sides are the generator's single straight
     * cubic, whereas interior sides always carry a multi-cubic tab.
     */
    val isEdge: Boolean get() = sides.top.size == 1 || sides.right.size == 1 || sides.bottom.size == 1 || sides.left.size == 1
}

/** A generated puzzle: the grid, cell size and all pieces in row-major order (id = row * cols + col). */
data class PuzzleLayout(
    val spec: PuzzleSpec,
    val grid: Grid,
    val cellWidth: Float,
    val cellHeight: Float,
    val pieces: List<PieceShape>,
) {
    fun piece(row: Int, col: Int): PieceShape = pieces[row * grid.cols + col]
}
