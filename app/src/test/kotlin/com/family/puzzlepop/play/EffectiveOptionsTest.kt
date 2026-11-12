package com.family.puzzlepop.play

import com.family.puzzlepop.engine.PieceStyle
import com.family.puzzlepop.engine.PuzzleGenerator
import com.family.puzzlepop.engine.PuzzleSpec
import com.family.puzzlepop.engine.game.PuzzleGame
import com.family.puzzlepop.engine.options.Preset
import com.family.puzzlepop.engine.options.PeekMode
import com.family.puzzlepop.engine.options.PlayOptions
import com.family.puzzlepop.engine.options.Presets
import com.family.puzzlepop.engine.options.RotationMode
import com.family.puzzlepop.settings.KidSettings
import kotlin.math.min
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

class EffectiveOptionsTest {
    private val layout = PuzzleGenerator.generate(PuzzleSpec(800, 600, 16, PieceStyle.CLASSIC, 1L))
    private val hard = Presets.options(Preset.HARD)

    @Test
    fun effectiveOptionsKidOverride() {
        val e = effectiveOptions(hard, true, KidSettings(helperOn = false, helperAfterSec = 30), layout)
        assertEquals(RotationMode.OFF, e.rotation)
        assertFalse(e.showTray)
        assertEquals(0.45f, e.ghostAlpha, 0f)
        assertTrue(e.magnetRadius > 0f)
        assertEquals(PuzzleGame.kidTolerance(layout), e.tolerance, 0f)
        assertTrue(e.boxPicture)
        assertFalse(e.boxMovable)
        assertFalse(e.helper)
        assertEquals(30_000L, e.helperDelayMs)
        assertFalse(e.timer)
        assertFalse(e.hintButton)
        assertEquals(PeekMode.UNLIMITED, e.peek)
    }

    @Test
    fun grownUpUsesSaved() {
        val e = effectiveOptions(hard, false, KidSettings(), layout)
        assertEquals(RotationMode.QUARTER, e.rotation)
        assertTrue(e.showTray)
        assertEquals(0.30f, e.ghostAlpha, 0f)
        assertEquals(0.25f * min(layout.cellWidth, layout.cellHeight), e.tolerance, 0f)
        assertEquals(PeekMode.LIMITED, e.peek)
        assertEquals(3, e.peekLimit)
        assertEquals(-1f, e.magnetRadius, 0f)
    }

    @Test
    fun legacyOptions() {
        val e = effectiveOptions(PlayOptions(), false, KidSettings(), layout)
        assertEquals(RotationMode.OFF, e.rotation)
        assertEquals(0.30f, e.ghostAlpha, 0f)
        assertEquals(PeekMode.UNLIMITED, e.peek)
        assertEquals(PuzzleGame.defaultTolerance(layout), e.tolerance, 0f)
        assertTrue(e.boxPicture)
        assertFalse(e.helper)
        assertTrue(e.hintButton)
        assertFalse(e.timer)
    }
}
