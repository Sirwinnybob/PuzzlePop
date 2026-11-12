package com.family.puzzlepop.engine.save

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Stores one JSON file per puzzle in [dir]. Writes go to a temp file that is synced and then atomically
 * moved over the real file, so a crash mid-write leaves the previous save intact.
 */
class SaveFileStore(private val dir: File) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true // so "version" and "generatorVersion" always reach disk
    }

    private fun file(puzzleId: String) = File(dir, "$puzzleId.json")
    private fun tmpFile(puzzleId: String) = File(dir, "$puzzleId.json.tmp")

    fun write(saved: SavedPuzzle) {
        dir.mkdirs()
        val tmp = tmpFile(saved.puzzleId)
        val bytes = json.encodeToString(SavedPuzzle.serializer(), saved).toByteArray(Charsets.UTF_8)
        FileOutputStream(tmp).use { out ->
            out.write(bytes)
            out.flush()
            out.fd.sync()
        }
        Files.move(
            tmp.toPath(),
            file(saved.puzzleId).toPath(),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING,
        )
    }

    /** Returns the saved puzzle, or null when there is none or the file cannot be read or parsed. */
    fun read(puzzleId: String): SavedPuzzle? {
        val f = file(puzzleId)
        if (!f.isFile) return null
        return try {
            json.decodeFromString(SavedPuzzle.serializer(), f.readText(Charsets.UTF_8))
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: IOException) {
            null
        }
    }

    fun delete(puzzleId: String) {
        file(puzzleId).delete()
        tmpFile(puzzleId).delete()
    }
}
