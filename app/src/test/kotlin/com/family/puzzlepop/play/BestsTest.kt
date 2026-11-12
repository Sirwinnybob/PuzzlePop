package com.family.puzzlepop.play

import com.family.puzzlepop.engine.options.Preset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BestsTest {
    @Test fun customHasNoKey() {
        assertNull(Bests.key("photo", 24, null))
    }

    @Test fun keyIncludesCountAndPreset() {
        val k = Bests.key("photo", 24, Preset.EASY)!!
        assertTrue(k.contains("photo") && k.contains("24") && k.contains("EASY"))
        assertNotEquals(k, Bests.key("photo", 48, Preset.EASY))
        assertNotEquals(k, Bests.key("photo", 24, Preset.HARD))
        assertNotEquals(k, Bests.key("other", 24, Preset.EASY))
    }

    @Test fun fasterIsNewBest() = assertTrue(Bests.isNewBest(90_000L, 60_000L))

    @Test fun slowerIsNot() {
        assertFalse(Bests.isNewBest(60_000L, 90_000L))
        assertFalse(Bests.isNewBest(60_000L, 60_000L))
    }

    @Test fun firstIsNewBest() = assertTrue(Bests.isNewBest(null, 60_000L))
}
