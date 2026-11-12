package com.family.puzzlepop.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GridSizerTest {

    private fun pieceAspect(grid: Grid, imageWidth: Int, imageHeight: Int): Double =
        (imageWidth.toDouble() / grid.cols) / (imageHeight.toDouble() / grid.rows)

    @Test
    fun fiftyOnFourByThree() {
        assertEquals(Grid(rows = 6, cols = 8), GridSizer.choose(50, 4000, 3000))
    }

    @Test
    fun fiftyOnPortrait() {
        assertEquals(Grid(rows = 8, cols = 6), GridSizer.choose(50, 3000, 4000))
    }

    @Test
    fun tenOnSquare() {
        assertEquals(Grid(3, 3), GridSizer.choose(10, 1000, 1000))
    }

    @Test
    fun targetIsClamped() {
        assertTrue(GridSizer.choose(5, 4000, 3000).count >= 9)
        assertTrue(GridSizer.choose(5000, 4000, 3000).count <= 1100)
    }

    @Test
    fun countsStayClose() {
        val targets = listOf(10, 24, 50, 100, 300, 500, 1000)
        val images = listOf(
            Pair(4000, 3000),
            Pair(3000, 4000),
            Pair(4000, 4000),
        )
        for ((w, h) in images) {
            for (target in targets) {
                val grid = GridSizer.choose(target, w, h)
                val tolerance = if (target <= 24) 0.25 else 0.12
                val relError = Math.abs(grid.count - target).toDouble() / target
                assertTrue(
                    relError <= tolerance,
                    "target=$target image=${w}x$h grid=$grid count=${grid.count} relError=$relError",
                )
                val aspect = pieceAspect(grid, w, h)
                assertTrue(
                    aspect in 0.8..1.25,
                    "target=$target image=${w}x$h grid=$grid pieceAspect=$aspect",
                )
            }
        }
    }

    @Test
    fun extremeAspectsStayNearSquare() {
        val images = listOf(
            Triple(8000, 1000, "8000x1000"),
            Triple(1000, 3000, "1000x3000"),
            Triple(300, 300, "300x300"),
        )
        for ((w, h, label) in images) {
            for (target in listOf(10, 100, 1000)) {
                val grid = GridSizer.choose(target, w, h)
                assertTrue(grid.rows >= 2, "$label target=$target grid=$grid")
                assertTrue(grid.cols >= 2, "$label target=$target grid=$grid")
                val aspect = pieceAspect(grid, w, h)
                assertTrue(
                    aspect in 0.7..1.43,
                    "$label target=$target grid=$grid pieceAspect=$aspect",
                )
            }
        }
    }
}
