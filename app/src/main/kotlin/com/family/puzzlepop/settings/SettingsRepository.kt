package com.family.puzzlepop.settings

import android.content.Context
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.family.puzzlepop.kid.kidShapeFromName
import java.security.MessageDigest
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

// A corrupt settings file is replaced by empty preferences (defaults) instead of crashing; the PIN and kid
// mode flag are then restored from the tiny mirror store below (see recoverSafety).
private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(
    name = "puzzlepop_settings",
    corruptionHandler = ReplaceFileCorruptionHandler { CorruptionMarker.set(); emptyPreferences() },
)

/** A marker file left when either store was found corrupt, so "PIN lost" can be told from "never had a PIN". */
private object CorruptionMarker {
    @Volatile var dir: java.io.File? = null
    fun file() = dir?.let { java.io.File(it, "settings_corruption_detected") }
    fun set() { try { file()?.createNewFile() } catch (e: java.io.IOException) { /* best effort */ } }
    fun isSet() = file()?.exists() == true
    fun clear() { file()?.delete() }
}

/** What startup learned about the PIN; input to [com.family.puzzlepop.nav.startDecision]. */
data class SafetyState(val pinPresent: Boolean, val pinEverSet: Boolean, val corruptionDetected: Boolean)

/** Mirror of the PIN hash + salt + kidModeActive only, so a corrupt main file cannot lose the lock. */
private val Context.safetyStore: DataStore<Preferences> by preferencesDataStore(
    name = "puzzlepop_safety",
    corruptionHandler = ReplaceFileCorruptionHandler { CorruptionMarker.set(); emptyPreferences() },
)

class SettingsRepository(context: Context) {
    init { CorruptionMarker.dir = context.applicationContext.filesDir }
    private val store = context.applicationContext.settingsStore
    private val safety = context.applicationContext.safetyStore

    private object K {
        val minPieces = intPreferencesKey("kid_min_pieces")
        val maxPieces = intPreferencesKey("kid_max_pieces")
        val sittingLimit = intPreferencesKey("kid_sitting_limit_min")
        val dailyLimit = intPreferencesKey("kid_daily_limit_min")
        val limitsTouched = booleanPreferencesKey("kid_limits_touched")
        val breakMin = intPreferencesKey("kid_break_min")
        val finishAllowance = intPreferencesKey("kid_finish_allowance")
        val napAfter = intPreferencesKey("kid_nap_after_min")
        val realSleepAfter = intPreferencesKey("kid_real_sleep_after_min")
        val alwaysKid = booleanPreferencesKey("kid_always_open")
        val kidModeActive = booleanPreferencesKey("kid_mode_active")
        val pendingKidExit = booleanPreferencesKey("kid_pending_exit")
        val pinHash = stringPreferencesKey("pin_hash")
        val pinSalt = stringPreferencesKey("pin_salt")
        val pinEverSet = booleanPreferencesKey("pin_ever_set")
        val dayKey = stringPreferencesKey("usage_day_key")
        val dailyUsed = longPreferencesKey("usage_daily_ms")
        val sittingUsed = longPreferencesKey("usage_sitting_ms")
        val breakEnds = longPreferencesKey("usage_break_ends")
        val doneForToday = booleanPreferencesKey("usage_done_for_today")
        val lastStop = longPreferencesKey("usage_last_stop")
        val trayCollapsed = booleanPreferencesKey("tray_collapsed")
        val fastClock = booleanPreferencesKey("debug_fast_clock")
        val helperOn = booleanPreferencesKey("kid_helper_on")
        val helperAfter = intPreferencesKey("kid_helper_after_sec")
        val kidShape = stringPreferencesKey("kid_shape")
        val lastCustom = stringPreferencesKey("last_custom_options")
        val lastPreset = stringPreferencesKey("last_preset")
        val pinningGuideSeen = booleanPreferencesKey("pinning_guide_seen")
        val bigCelebration = booleanPreferencesKey("big_celebration")
    }

    val kidSettings: Flow<KidSettings> = store.data.map { readSettings(it) }

    suspend fun updateKidSettings(transform: (KidSettings) -> KidSettings) {
        store.edit { p ->
            val cur = readSettings(p)
            val n = transform(cur)
            p[K.minPieces] = n.minPieces
            p[K.maxPieces] = n.maxPieces
            n.sittingLimitMin?.let { p[K.sittingLimit] = it } ?: p.remove(K.sittingLimit)
            n.dailyLimitMin?.let { p[K.dailyLimit] = it } ?: p.remove(K.dailyLimit)
            p[K.limitsTouched] = true
            p[K.breakMin] = n.breakMin
            p[K.finishAllowance] = n.finishAllowance
            p[K.alwaysKid] = n.alwaysOpenInKidMode
            p[K.napAfter] = n.napAfterMin
            p[K.realSleepAfter] = n.realSleepAfterMin
            p[K.helperOn] = n.helperOn
            p[K.helperAfter] = n.helperAfterSec
            p[K.kidShape] = n.kidShape.name
        }
    }

    private fun readSettings(p: Preferences): KidSettings {
        val d = KidSettings()
        val touched = p[K.limitsTouched] == true
        return KidSettings(
            minPieces = p[K.minPieces] ?: d.minPieces,
            maxPieces = p[K.maxPieces] ?: d.maxPieces,
            sittingLimitMin = if (touched) p[K.sittingLimit] else d.sittingLimitMin,
            dailyLimitMin = if (touched) p[K.dailyLimit] else d.dailyLimitMin,
            breakMin = p[K.breakMin] ?: d.breakMin,
            finishAllowance = p[K.finishAllowance] ?: d.finishAllowance,
            alwaysOpenInKidMode = p[K.alwaysKid] ?: d.alwaysOpenInKidMode,
            napAfterMin = p[K.napAfter] ?: d.napAfterMin,
            realSleepAfterMin = p[K.realSleepAfter] ?: d.realSleepAfterMin,
            helperOn = p[K.helperOn] ?: d.helperOn,
            helperAfterSec = p[K.helperAfter] ?: d.helperAfterSec,
            kidShape = kidShapeFromName(p[K.kidShape]),
        )
    }

    val kidModeActive: Flow<Boolean> = store.data.map { it[K.kidModeActive] ?: false }
    suspend fun setKidModeActive(on: Boolean) {
        store.edit { it[K.kidModeActive] = on }
        mirror { it[K.kidModeActive] = on }
    }

    /** Writes to the safety mirror; a mirror failure must never break the real write. */
    private suspend fun mirror(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        try {
            safety.edit { block(it) }
        } catch (e: java.io.IOException) {
            android.util.Log.w("PuzzlePopSettings", "safety mirror write failed", e)
        }
    }

    /**
     * Call once at startup, before reading [hasPin]. Keeps the mirror equal to the main store while the main
     * store is healthy (also seeds the mirror on upgrade), and restores PIN + kidModeActive from the mirror
     * when the main store lost the PIN (corruption). Returns true when a PIN exists afterwards.
     */
    suspend fun recoverSafety(): SafetyState {
        val main = store.data.first()
        val mir = try { safety.data.first() } catch (e: java.io.IOException) { null }
        val corrupt = CorruptionMarker.isSet()
        val mainHas = main[K.pinHash] != null && main[K.pinSalt] != null
        val mirHas = mir != null && mir[K.pinHash] != null && mir[K.pinSalt] != null
        val everSet = mir?.get(K.pinEverSet) == true
        if (mainHas) {
            if (!mirHas || !everSet || mir!![K.pinHash] != main[K.pinHash] || mir[K.pinSalt] != main[K.pinSalt]) {
                mirror {
                    it[K.pinHash] = main[K.pinHash]!!
                    it[K.pinSalt] = main[K.pinSalt]!!
                    it[K.kidModeActive] = main[K.kidModeActive] ?: false
                    it[K.pinEverSet] = true
                }
            }
            return SafetyState(pinPresent = true, pinEverSet = true, corruptionDetected = corrupt)
        }
        if (mirHas) {
            store.edit {
                it[K.pinHash] = mir!![K.pinHash]!!
                it[K.pinSalt] = mir[K.pinSalt]!!
                it[K.kidModeActive] = mir[K.kidModeActive] ?: false
            }
            return SafetyState(pinPresent = true, pinEverSet = true, corruptionDetected = corrupt)
        }
        return SafetyState(pinPresent = false, pinEverSet = everSet, corruptionDetected = corrupt)
    }

    /** A pinned exit from kid mode was started and is waiting for the device unlock. */
    val pendingKidExit: Flow<Boolean> = store.data.map { it[K.pendingKidExit] ?: false }
    suspend fun setPendingKidExit(on: Boolean) {
        store.edit { it[K.pendingKidExit] = on }
    }

    val hasPin: Flow<Boolean> = store.data.map { it[K.pinHash] != null && it[K.pinSalt] != null }

    suspend fun setPin(pin: String) {
        require(pin.length in 4..6 && pin.all { it in '0'..'9' }) { "PIN must be 4 to 6 digits" }
        val salt = PinHasher.newSalt()
        val hash = PinHasher.hash(pin, salt)
        store.edit {
            it[K.pinSalt] = Base64.encodeToString(salt, Base64.NO_WRAP)
            it[K.pinHash] = Base64.encodeToString(hash, Base64.NO_WRAP)
        }
        val saved = store.data.first()
        mirror {
            it[K.pinSalt] = saved[K.pinSalt]!!
            it[K.pinHash] = saved[K.pinHash]!!
            it[K.kidModeActive] = saved[K.kidModeActive] ?: false
            it[K.pinEverSet] = true
        }
        CorruptionMarker.clear()
    }

    suspend fun checkPin(pin: String): Boolean {
        val p = store.data.first()
        val saltB64 = p[K.pinSalt] ?: return false
        val hashB64 = p[K.pinHash] ?: return false
        val expected = Base64.decode(hashB64, Base64.NO_WRAP)
        val actual = PinHasher.hash(pin, Base64.decode(saltB64, Base64.NO_WRAP))
        return MessageDigest.isEqual(expected, actual)
    }

    val usage: Flow<Usage> = store.data.map { p ->
        Usage(
            dayKey = p[K.dayKey] ?: LocalDate.now().toString(),
            dailyUsedMs = p[K.dailyUsed] ?: 0L,
            sittingUsedMs = p[K.sittingUsed] ?: 0L,
            breakEndsAtEpochMs = p[K.breakEnds],
            doneForToday = p[K.doneForToday] ?: false,
            lastStopEpochMs = p[K.lastStop],
        )
    }

    suspend fun saveUsage(u: Usage) {
        store.edit { p ->
            p[K.dayKey] = u.dayKey
            p[K.dailyUsed] = u.dailyUsedMs
            p[K.sittingUsed] = u.sittingUsedMs
            u.breakEndsAtEpochMs?.let { p[K.breakEnds] = it } ?: p.remove(K.breakEnds)
            p[K.doneForToday] = u.doneForToday
            u.lastStopEpochMs?.let { p[K.lastStop] = it } ?: p.remove(K.lastStop)
        }
    }

    /** True once the "how to set up screen pinning" guide has been shown before the first kid mode start. */
    val pinningGuideSeen: Flow<Boolean> = store.data.map { it[K.pinningGuideSeen] ?: false }
    suspend fun setPinningGuideSeen(v: Boolean) {
        store.edit { it[K.pinningGuideSeen] = v }
    }

    /** The finished-puzzle party: true = trumpets and fireworks (the default), false = chime and confetti. */
    val bigCelebration: Flow<Boolean> = store.data.map { it[K.bigCelebration] ?: true }
    suspend fun setBigCelebration(v: Boolean) {
        store.edit { it[K.bigCelebration] = v }
    }

    val trayCollapsed: Flow<Boolean> = store.data.map { it[K.trayCollapsed] ?: false }
    suspend fun setTrayCollapsed(v: Boolean) {
        store.edit { it[K.trayCollapsed] = v }
    }

    /** Debug builds only: 1 real second counts as 1 minute of play time. Never read in release. */
    val fastClock: Flow<Boolean> = store.data.map { it[K.fastClock] ?: false }
    suspend fun setFastClock(v: Boolean) {
        store.edit { it[K.fastClock] = v }
    }

    /** The preset chip last used in the new-puzzle sheet ("CUSTOM" or a Preset name); null = never. */
    val lastPreset: Flow<String?> = store.data.map { it[K.lastPreset] }
    suspend fun setLastPreset(name: String) {
        store.edit { it[K.lastPreset] = name }
    }

    /** The custom options last used, as JSON written by the new-puzzle sheet; null = never. */
    val lastCustom: Flow<String?> = store.data.map { it[K.lastCustom] }
    suspend fun setLastCustom(json: String) {
        store.edit { it[K.lastCustom] = json }
    }
}
