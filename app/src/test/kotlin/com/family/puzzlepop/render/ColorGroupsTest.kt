package com.family.puzzlepop.render

import org.junit.Assert.assertEquals
import org.junit.Test

class ColorGroupsTest {
    @Test
    fun primaries() {
        assertEquals(ColorGroup.RED, ColorGroups.of(255, 0, 0))
        assertEquals(ColorGroup.GREEN, ColorGroups.of(0, 255, 0))
        assertEquals(ColorGroup.BLUE, ColorGroups.of(0, 0, 255))
        assertEquals(ColorGroup.PURPLE, ColorGroups.of(160, 40, 200))
    }

    @Test
    fun greysByValue() {
        assertEquals(ColorGroup.LIGHT, ColorGroups.of(230, 230, 230))
        assertEquals(ColorGroup.LIGHT, ColorGroups.of(200, 190, 195)) // barely tinted, bright
        assertEquals(ColorGroup.DARK, ColorGroups.of(30, 30, 30))
        assertEquals(ColorGroup.DARK, ColorGroups.of(100, 98, 100))
    }

    @Test
    fun orangeAndYellowBoundaries() {
        assertEquals(ColorGroup.ORANGE, ColorGroups.of(255, 128, 0)) // hue 30
        assertEquals(ColorGroup.YELLOW, ColorGroups.of(255, 255, 0)) // hue 60
        assertEquals(ColorGroup.ORANGE, ColorGroups.of(255, 190, 0)) // hue ~44.7
        assertEquals(ColorGroup.YELLOW, ColorGroups.of(255, 192, 0)) // hue ~45.2
        assertEquals(ColorGroup.RED, ColorGroups.of(255, 0, 60))     // hue 352
        assertEquals(ColorGroup.RED, ColorGroups.of(255, 50, 0))     // hue ~11.8
        assertEquals(ColorGroup.ORANGE, ColorGroups.of(255, 80, 0))  // hue ~18.8
    }
}
