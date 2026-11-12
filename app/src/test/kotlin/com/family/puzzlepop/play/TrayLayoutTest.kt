package com.family.puzzlepop.play

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrayLayoutTest {
    private val tablet = TrayLayout.forViewport(Size(2800f, 1752f), landscape = true, density = 2f)
    private val phone = TrayLayout.forViewport(Size(1440f, 3000f), landscape = false, density = 3f)

    @Test
    fun landscapeTrayOnRight() {
        val r = tablet.trayRect
        assertEquals(2800f, r.right, 0.01f)
        assertEquals(132f * 2, r.width, 1f)
        assertEquals(0f, r.top, 0.01f)
        assertEquals(1752f, r.bottom, 0.01f)
        assertTrue(tablet.vertical)
    }

    @Test
    fun portraitTrayAtBottom() {
        val r = phone.trayRect
        assertEquals(3000f, r.bottom, 0.01f)
        assertEquals(132f * 3, r.height, 1f)
        assertEquals(0f, r.left, 0.01f)
        assertEquals(1440f, r.right, 0.01f)
        assertTrue(!phone.vertical)
    }

    @Test
    fun indexAtRoundTrips() {
        for (layout in listOf(tablet, phone)) {
            for (s in listOf(0f, 150f)) {
                for (i in 0..20) {
                    val slot = layout.slotRect(i, s)
                    assertEquals(slot.width, slot.height, 0.01f) // square
                    assertEquals("slot $i scroll $s", i, layout.indexAt(slot.center, s))
                }
            }
        }
    }

    @Test
    fun indexAtOutsideTrayIsNull() {
        assertNull(tablet.indexAt(Offset(100f, 100f), 0f))
        assertNull(phone.indexAt(Offset(100f, 100f), 0f))
    }

    @Test
    fun maxScrollFitsAll() {
        for (layout in listOf(tablet, phone)) {
            val view = layout.slotsViewport
            assertEquals(0f, layout.maxScroll(0), 0f)
            assertEquals(0f, layout.maxScroll(2), 0f)
            for (count in listOf(1, 7, 40, 300, 1000)) {
                val max = layout.maxScroll(count)
                val first = layout.slotRect(0, 0f)
                val last = layout.slotRect(count - 1, max)
                assertTrue(first.left >= view.left - 0.5f && first.top >= view.top - 0.5f)
                assertTrue("count $count", last.right <= view.right + 0.5f && last.bottom <= view.bottom + 0.5f)
                // And no more scroll than needed: one step less would cut the last slot off (or there is none).
                if (max > 0f) {
                    val cut = layout.slotRect(count - 1, max - layout.slotSize)
                    assertTrue(cut.right > view.right + 0.5f || cut.bottom > view.bottom + 0.5f)
                }
            }
        }
    }

    @Test
    fun phonePortraitShowsAboutSixSlots() {
        // A 360 dp and a 411 dp wide phone; colour dots closed.
        for (widthDp in listOf(360f, 411f)) {
            val d = 2.625f
            val l = TrayLayout.forViewport(Size(widthDp * d, 800f * d), landscape = false, density = d)
            val vp = l.slotsViewport
            val full = (0 until 40).count { val r = l.slotRect(it, 0f); r.left >= vp.left - 0.5f && r.right <= vp.right + 0.5f }
            assertTrue("$widthDp dp: $full slots", full >= 6)
        }
    }

    @Test
    fun openDotsLengthenHeader() {
        val open = TrayLayout.forViewport(Size(2800f, 1752f), landscape = true, density = 2f, colorsOpen = true)
        assertTrue(open.headerRect.height > tablet.headerRect.height)
    }
}
