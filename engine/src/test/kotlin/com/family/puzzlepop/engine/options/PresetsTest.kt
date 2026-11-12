package com.family.puzzlepop.engine.options

import com.family.puzzlepop.engine.PieceStyle
import com.family.puzzlepop.engine.PuzzleGenerator
import com.family.puzzlepop.engine.PuzzleSpec
import com.family.puzzlepop.engine.Pt
import com.family.puzzlepop.engine.game.GameState
import com.family.puzzlepop.engine.game.PuzzleGame
import com.family.puzzlepop.engine.save.SaveFileStore
import com.family.puzzlepop.engine.save.SavedPuzzle
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class PresetsTest {
    @TempDir
    lateinit var dir: File

    private fun check(
        p: Preset, target: Int, style: PieceStyle, rot: RotationMode, ghost: Ghost, peek: PeekMode,
        snap: Snap, tray: Boolean, box: Boolean, helper: Boolean, hint: Boolean, timer: Boolean,
    ) {
        assertEquals(target, Presets.targetCount(p))
        assertEquals(style, Presets.style(p))
        val o = Presets.options(p)
        assertEquals(p, o.preset)
        assertEquals(rot, o.rotation)
        assertEquals(ghost, o.ghost)
        assertEquals(peek, o.peek)
        assertEquals(snap, o.snap)
        assertEquals(tray, o.startInTray)
        assertEquals(box, o.boxPicture)
        assertEquals(helper, o.helper)
        assertEquals(hint, o.hintButton)
        assertEquals(timer, o.timer)
    }

    @Test
    fun presetTableMatches() {
        check(Preset.TINY, 12, PieceStyle.CHUNKY, RotationMode.OFF, Ghost.STRONG, PeekMode.UNLIMITED, Snap.VERY_FORGIVING, false, true, true, true, false)
        check(Preset.EASY, 36, PieceStyle.CHUNKY, RotationMode.OFF, Ghost.STRONG, PeekMode.UNLIMITED, Snap.FORGIVING, false, true, false, true, false)
        check(Preset.MEDIUM, 100, PieceStyle.CLASSIC, RotationMode.OFF, Ghost.FAINT, PeekMode.UNLIMITED, Snap.NORMAL, false, true, false, true, true)
        check(Preset.HARD, 300, PieceStyle.CLASSIC, RotationMode.QUARTER, Ghost.FAINT, PeekMode.LIMITED, Snap.NORMAL, true, false, false, true, true)
        check(Preset.EXPERT, 750, PieceStyle.WOBBLY, RotationMode.ANY, Ghost.OFF, PeekMode.NEVER, Snap.PRECISE, true, false, false, false, true)
        assertEquals(3, Presets.options(Preset.HARD).peekLimit)
    }

    private val spec = PuzzleSpec(400, 400, 4, PieceStyle.CLASSIC, 5L)

    @Test
    fun legacySaveLoadsWithDefaults() {
        val text = """{"version":1,"puzzleId":"p","photoId":"ph",
            "spec":{"imageWidth":400,"imageHeight":400,"targetCount":4,"style":"CLASSIC","seed":5},
            "state":{"positions":[{"x":0,"y":0}],"groupOf":[0],"locked":[false],"zOrder":[0],"elapsedMs":5,"moves":1},
            "createdAt":1,"updatedAt":2,"someOldField":3}"""
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val s = json.decodeFromString(SavedPuzzle.serializer(), text)
        assertEquals(PlayOptions(), s.options)
        assertNull(s.boxPos)
        assertTrue(s.state.rotations.isEmpty())
        assertEquals(0, s.state.peeksUsed)
    }

    @Test
    fun optionsRoundTrip() {
        val s = SavedPuzzle(
            puzzleId = "p", photoId = "ph", spec = spec,
            state = GameState(listOf(Pt(1f, 2f)), listOf(0), listOf(false), listOf(0), rotations = listOf(90f), peeksUsed = 2),
            createdAt = 1, updatedAt = 2,
            options = Presets.options(Preset.HARD), boxPos = Pt(0.5f, 0.25f),
        )
        val store = SaveFileStore(dir)
        store.write(s)
        assertEquals(s, store.read("p"))
    }

    @Test
    fun toleranceFactors() {
        val layout = PuzzleGenerator.generate(PuzzleSpec(400, 400, 16, PieceStyle.CLASSIC, 1L))
        assertEquals(4, layout.grid.cols)
        assertEquals(15f, PuzzleGame.tolerance(layout, Snap.PRECISE), 0.001f)
        assertEquals(40f, PuzzleGame.tolerance(layout, Snap.VERY_FORGIVING), 0.001f)
    }
}
