package com.family.puzzlepop.engine

import kotlin.math.abs
import kotlin.math.ln

/** Chooses a puzzle grid whose piece count and piece shape suit the source image. */
object GridSizer {
    const val MIN_PIECES = 10
    const val MAX_PIECES = 1000

    private const val MIN_ROWS_OR_COLS = 2
    private const val MAX_ROWS = 200
    private const val MAX_COLS = 400
    private const val MIN_ASPECT = 0.7
    private const val MAX_ASPECT = 1.43
    private const val ASPECT_WEIGHT = 0.5

    /**
     * Picks rows and cols so that rows * cols is close to [targetCount] (clamped to
     * [MIN_PIECES]..[MAX_PIECES]) and each piece is close to square.
     *
     * Only grids whose piece aspect lies in [MIN_ASPECT]..[MAX_ASPECT] are considered, unless none
     * qualify, in which case every candidate is considered. Among the candidates the lowest
     * score wins, where `score = |rows*cols - target| / target + 0.5 * |ln(pieceAspect)|`.
     * Ties keep the first candidate in row-major search order.
     */
    fun choose(targetCount: Int, imageWidth: Int, imageHeight: Int): Grid {
        val target = targetCount.coerceIn(MIN_PIECES, MAX_PIECES).toDouble()
        val imageAspect = imageWidth.toDouble() / imageHeight.toDouble()

        var best: Grid? = null
        var bestScore = Double.MAX_VALUE
        var bestQualifies = false

        for (rows in MIN_ROWS_OR_COLS..MAX_ROWS) {
            for (cols in MIN_ROWS_OR_COLS..MAX_COLS) {
                val pieceAspect = imageAspect * rows / cols
                val qualifies = pieceAspect in MIN_ASPECT..MAX_ASPECT
                // Once a qualifying grid is known, non-qualifying grids are never preferred.
                if (bestQualifies && !qualifies) continue

                val countError = abs(rows * cols - target) / target
                val score = countError + ASPECT_WEIGHT * abs(ln(pieceAspect))

                val better = when {
                    best == null -> true
                    qualifies && !bestQualifies -> true
                    qualifies == bestQualifies -> score < bestScore
                    else -> false
                }
                if (better) {
                    best = Grid(rows, cols)
                    bestScore = score
                    bestQualifies = qualifies
                }
            }
        }
        return best ?: error("no grid candidates")
    }
}
