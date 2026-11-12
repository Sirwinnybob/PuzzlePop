package com.family.puzzlepop.data

import android.content.Context
import com.family.puzzlepop.engine.PuzzleSpec
import com.family.puzzlepop.engine.game.GameState
import com.family.puzzlepop.engine.options.PlayOptions
import com.family.puzzlepop.engine.save.SaveFileStore
import com.family.puzzlepop.engine.save.SavedPuzzle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class PuzzleRepository(
    context: Context,
    private val db: AppDatabase,
) {
    private val store = SaveFileStore(File(context.applicationContext.filesDir, "saves"))

    // SaveFileStore uses one shared .tmp file per puzzle id, so writes for the same id must not overlap.
    private val locks = ConcurrentHashMap<String, Mutex>()
    private fun lockFor(id: String): Mutex = locks.getOrPut(id) { Mutex() }

    fun observeInProgress(): Flow<List<PuzzleEntity>> = db.puzzleDao().observeInProgress()

    suspend fun create(photoId: String, spec: PuzzleSpec, state: GameState, options: PlayOptions = PlayOptions()): String = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        lockFor(id).withLock {
            store.write(SavedPuzzle(puzzleId = id, photoId = photoId, spec = spec, state = state, options = options, createdAt = now, updatedAt = now))
            db.puzzleDao().insert(
                PuzzleEntity(
                    id = id,
                    photoId = photoId,
                    pieceCount = state.locked.size,
                    placedCount = state.locked.count { it },
                    finished = state.locked.all { it },
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }
        id
    }

    /**
     * Writes the save file first, then updates the database row's progress. Does nothing when the
     * puzzle's row is gone (deleted with its photo): a late save must not recreate an orphan file.
     */
    suspend fun save(saved: SavedPuzzle) = withContext(Dispatchers.IO) {
        lockFor(saved.puzzleId).withLock {
            if (db.puzzleDao().get(saved.puzzleId) == null) return@withLock
            val now = System.currentTimeMillis()
            store.write(saved.copy(updatedAt = now))
            val placed = saved.state.locked.count { it }
            db.puzzleDao().updateProgress(saved.puzzleId, placed, saved.state.locked.all { it }, now)
        }
    }

    /** Returns null when there is no readable save; callers must not treat that as a reason to delete anything. */
    suspend fun load(id: String): SavedPuzzle? = withContext(Dispatchers.IO) {
        lockFor(id).withLock { store.read(id) }
    }

    /**
     * Removes one puzzle: its save file and its row, under the same per-id lock as [save], so a late
     * autosave finds the row gone and writes nothing.
     */
    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        lockFor(id).withLock {
            store.delete(id)
            db.puzzleDao().delete(id)
        }
    }

    internal suspend fun deleteForPhoto(photoId: String) = withContext(Dispatchers.IO) {
        for (p in db.puzzleDao().forPhoto(photoId)) {
            lockFor(p.id).withLock {
                store.delete(p.id)
                db.puzzleDao().delete(p.id)
            }
        }
    }
}
