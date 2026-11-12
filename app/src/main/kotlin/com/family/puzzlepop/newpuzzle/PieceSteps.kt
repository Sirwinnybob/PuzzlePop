package com.family.puzzlepop.newpuzzle

import kotlin.math.roundToInt

/**
 * The piece counts the new-puzzle slider steps through, and the mapping between a step and its place
 * along the track (0 at the "few" end, 1 at the "many" end).
 */
internal object PieceSteps {
    /** Fine steps for small puzzles, wider steps above about 100, so a small count is as easy to hit as a big one. */
    val counts = listOf(
        10, 12, 15, 16, 20, 24, 30, 36, 40, 48, 54, 60, 70, 80, 100,
        120, 150, 200, 250, 300, 400, 500, 600, 750, 800, 1000,
    )
    const val DEFAULT_COUNT = 24

    val lastIndex: Int get() = counts.lastIndex

    /** Where step [index] sits along the track, 0..1. */
    fun fraction(index: Int): Float = index.coerceIn(0, lastIndex).toFloat() / lastIndex

    /** The step nearest to [fraction] along the track; values outside 0..1 pin to the ends. */
    fun indexAt(fraction: Float): Int =
        if (fraction.isNaN()) 0 else (fraction.coerceIn(0f, 1f) * lastIndex).roundToInt()

    /** The step holding [count], or -1 when the slider has no such step. */
    fun indexOf(count: Int): Int = counts.indexOf(count)
}
