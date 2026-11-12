package com.family.puzzlepop.engine.save

import com.family.puzzlepop.engine.PieceStyle
import com.family.puzzlepop.engine.Pt
import com.family.puzzlepop.engine.PuzzleSpec
import com.family.puzzlepop.engine.game.GameState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class SaveFileStoreTest {
    @TempDir
    lateinit var dir: File

    private fun saved(id: String = "p1", pieces: Int = 1000, moves: Int = 7) = SavedPuzzle(
        puzzleId = id,
        photoId = "photo-1",
        spec = PuzzleSpec(4000, 3000, pieces, PieceStyle.WOBBLY, 99L),
        state = GameState(
            positions = List(pieces) { Pt(it * 1.5f, it * -2.25f) },
            groupOf = List(pieces) { it / 3 },
            locked = List(pieces) { it % 5 == 0 },
            zOrder = List(pieces) { pieces - 1 - it },
            elapsedMs = 123_456L,
            moves = moves,
        ),
        createdAt = 1000L,
        updatedAt = 2000L,
    )

    @Test
    fun roundTrip() {
        val store = SaveFileStore(dir)
        val s = saved()
        store.write(s)
        assertEquals(s, store.read("p1"))
        val text = File(dir, "p1.json").readText()
        assertTrue(text.contains("\"version\":1"), text.take(200))
        assertTrue(text.contains("\"generatorVersion\":1"), text.take(400))
    }

    @Test
    fun missingReturnsNull() {
        assertNull(SaveFileStore(dir).read("nope"))
    }

    @Test
    fun leftoverTempFileIsIgnored() {
        val store = SaveFileStore(dir)
        val s = saved()
        store.write(s)
        File(dir, "p1.json.tmp").writeText("{ garbage not json")
        assertEquals(s, store.read("p1"))
    }

    @Test
    fun overwriteKeepsLatest() {
        val store = SaveFileStore(dir)
        store.write(saved(pieces = 10, moves = 1))
        val latest = saved(pieces = 10, moves = 2)
        store.write(latest)
        assertEquals(latest, store.read("p1"))
    }
}
