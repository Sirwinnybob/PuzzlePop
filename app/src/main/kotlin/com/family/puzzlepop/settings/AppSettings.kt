package com.family.puzzlepop.settings

import com.family.puzzlepop.kid.KidShape

/** Grown-up configured kid-mode options. A null limit means off. */
data class KidSettings(
    val minPieces: Int = 12,
    val maxPieces: Int = 48,
    val sittingLimitMin: Int? = 20,
    val dailyLimitMin: Int? = 60,
    val breakMin: Int = 30,
    val finishAllowance: Int = 5,
    val alwaysOpenInKidMode: Boolean = false,
    val napAfterMin: Int = 5,
    val realSleepAfterMin: Int = 30,
    val helperOn: Boolean = true,
    val helperAfterSec: Int = 20,
    val kidShape: KidShape = KidShape.CHUNKY,
)

/** Persisted play-time counters; lets a [com.family.puzzlepop.kid.PlayTimeKeeper] resume after a restart. */
data class Usage(
    val dayKey: String,
    val dailyUsedMs: Long,
    val sittingUsedMs: Long,
    val breakEndsAtEpochMs: Long?,
    val doneForToday: Boolean,
    /** When kid play last stopped (epoch ms); lets a long gap start a new sitting. Null = playing / unknown. */
    val lastStopEpochMs: Long? = null,
)
