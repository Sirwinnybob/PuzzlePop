package com.family.puzzlepop.play

import androidx.compose.ui.geometry.Rect
import com.family.puzzlepop.engine.Box
import org.junit.Assert.assertEquals
import org.junit.Test

class CameraTest {
    private val table = Box(0f, 0f, 1000f, 500f)
    private val area = Rect(0f, 0f, 1000f, 500f)

    @Test
    fun atFitTheBoardStaysCentred() {
        val fit = Camera.fitIn(table, area, 10f)
        val pushed = Camera(fit.scale, fit.offsetX + 200f, fit.offsetY - 80f)
        val c = pushed.keepTableInView(table, area)
        assertEquals(fit.offsetX, c.offsetX, 0.01f)
        assertEquals(fit.offsetY, c.offsetY, 0.01f)
    }

    @Test
    fun zoomedInPanStopsAtTableEdges() {
        // 2x: the table is 2000 x 1000 on screen, so it may slide by up to 1000 x 500.
        assertEquals(Camera(2f, 0f, 0f), Camera(2f, 300f, 50f).keepTableInView(table, area))
        assertEquals(Camera(2f, -1000f, -500f), Camera(2f, -1500f, -900f).keepTableInView(table, area))
        val inside = Camera(2f, -400f, -200f)
        assertEquals(inside, inside.keepTableInView(table, area))
    }
}
