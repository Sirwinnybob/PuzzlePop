package com.family.puzzlepop.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.family.puzzlepop.play.Bests
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.bestsStore: DataStore<Preferences> by preferencesDataStore(
    name = "puzzlepop_bests",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/**
 * Personal best times (ms) per [Bests.key], plus, per photo, the key of the puzzle finished most recently
 * (so the library tile can show the best for the most recent preset without reading any save file).
 */
class BestsStore(context: Context) {
    private val store = context.applicationContext.bestsStore

    suspend fun get(key: String): Long? = store.data.first()[longPreferencesKey(BEST + key)]

    /** Records a finished run; returns true when it is a new best. Always remembers [key] as the photo's latest. */
    suspend fun record(key: String, ms: Long, photoId: String? = null): Boolean {
        var isNew = false
        store.edit { p ->
            val k = longPreferencesKey(BEST + key)
            isNew = Bests.isNewBest(p[k], ms)
            if (isNew) p[k] = ms
            if (photoId != null) p[stringPreferencesKey(LAST + photoId)] = key
        }
        return isNew
    }

    /** Every best, key -> ms. */
    fun observeAll(): Flow<Map<String, Long>> = store.data.map { p ->
        p.asMap().mapNotNull { (k, v) -> if (k.name.startsWith(BEST) && v is Long) k.name.removePrefix(BEST) to v else null }.toMap()
    }

    /** photoId -> key of the most recently finished preset puzzle. */
    fun observeLatest(): Flow<Map<String, String>> = store.data.map { p ->
        p.asMap().mapNotNull { (k, v) -> if (k.name.startsWith(LAST) && v is String) k.name.removePrefix(LAST) to v else null }.toMap()
    }

    private companion object {
        const val BEST = "best_"
        const val LAST = "last_"
    }
}
