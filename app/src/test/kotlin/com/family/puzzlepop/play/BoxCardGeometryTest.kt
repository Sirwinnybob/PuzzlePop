package com.family.puzzlepop.play

import androidx.compose.ui.geometry.Rect
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

class BoxCardGeometryTest {
    @Test fun cardSizeUsesShortSideWithFloor() {
        val s = boxCardSize(2000f, 1000f, 1f, 1f)
        assertEquals(0.18f * 1000f + 8f, s.width, 0.01f)
        val small = boxCardSize(400f, 300f, 2f, 2f) // floor 200 px long side
        assertEquals(200f + 16f, small.width, 0.01f)
        assertEquals(100f + 16f, small.height, 0.01f)
    }

    @Test fun tableRectNeverTouchesCard() {
        val area = Rect(0f, 0f, 1600f, 900f)
        val card = boxCardDefaultRect(1600f, 900f, 16f / 9f, 2f)
        val t = tableRectExcluding(area, card, 16f)
        assertFalse(t.overlaps(card))
        assertTrue(t.width * t.height > 0.7f * area.width * area.height)
    }

    @Test fun noCardLeavesAreaAlone() {
        val area = Rect(0f, 0f, 800f, 600f)
        assertEquals(area, tableRectExcluding(area, null, 8f))
    }
}
