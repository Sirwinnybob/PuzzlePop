package com.family.puzzlepop.play

import com.family.puzzlepop.engine.options.Preset

/** Pure rules for personal bests: only the five presets keep one; Custom and legacy puzzles never do. */
object Bests {
    /** The best-time key for a photo at a real piece count and preset, or null for Custom/legacy (no preset). */
    fun key(photoId: String, pieceCount: Int, preset: Preset?): String? =
        if (preset == null) null else "${photoId}_${pieceCount}_${preset.name}"

    /** True when [new] beats [old] (strictly faster), or there is no old best. */
    fun isNewBest(old: Long?, new: Long): Boolean = old == null || new < old
}
