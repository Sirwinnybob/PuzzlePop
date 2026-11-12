package com.family.puzzlepop.play

import com.family.puzzlepop.engine.Box
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TableSizeTest {
    @Test
    fun smallPuzzleGetsCellMargin() {
        // 12 pieces, 4 x 3 on a 1200 x 900 board: cells 300 x 300.
        val t = tableBox(1200f, 900f, 300f, 300f, kid = false)
        assertEquals(-3.5f * 300f, t.left, 0.01f)
        assertEquals(-3.5f * 300f, t.top, 0.01f)
        assertEquals(1200f + 3.5f * 300f, t.right, 0.01f)
        assertEquals(900f + 3.5f * 300f, t.bottom, 0.01f)
    }

    @Test
    fun bigPuzzleKeepsSixtyPercent() {
        // 100 pieces, 10 x 10.
        val t = tableBox(1000f, 1000f, 100f, 100f, kid = false)
        assertEquals(Box(-600f, -600f, 1600f, 1600f), t)
    }

    @Test
    fun kidKeepsSixtyPercent() {
        val t = tableBox(1200f, 900f, 400f, 450f, kid = true)
        assertEquals(Box(-720f, -540f, 1920f, 1440f), t)
    }

    @Test
    fun neverSmallerThanBefore() {
        for (cells in listOf(2, 3, 4, 6, 10, 30)) {
            for (kid in listOf(false, true)) {
                val t = tableBox(1000f, 750f, 1000f / cells, 750f / cells, kid)
                assertTrue(t.left <= -600f && t.top <= -450f && t.right >= 1600f && t.bottom >= 1200f)
            }
        }
    }

    @Test
    fun fillWidensToScreenAspect() {
        val base = Box(-600f, -450f, 1600f, 1200f) // 2200 x 1650
        val t = tableToFill(base, null, 2000f, 1000f) // a 2:1 area
        assertEquals(2f, t.width / t.height, 0.001f)
        assertEquals(1650f, t.height, 0.01f)
        assertEquals(500f, (t.left + t.right) / 2, 0.01f) // centred
    }

    @Test
    fun fillHeightensForTallArea() {
        val base = Box(-600f, -450f, 1600f, 1200f)
        val t = tableToFill(base, null, 1000f, 2000f)
        assertEquals(0.5f, t.width / t.height, 0.001f)
        assertEquals(2200f, t.width, 0.01f)
    }

    @Test
    fun fillTakesInPiecesOutside() {
        val base = Box(0f, 0f, 100f, 100f)
        val t = tableToFill(base, floatArrayOf(-50f, 10f, 120f, 300f), 1f, 1f)
        assertTrue(t.left <= -50f && t.right >= 120f && t.top <= 0f && t.bottom >= 300f)
        assertEquals(1f, t.width / t.height, 0.001f)
    }
}
