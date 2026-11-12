package com.family.puzzlepop.engine.options

import com.family.puzzlepop.engine.PieceStyle

object Presets {
    fun targetCount(p: Preset): Int = when (p) {
        Preset.TINY -> 12
        Preset.EASY -> 36
        Preset.MEDIUM -> 100
        Preset.HARD -> 300
        Preset.EXPERT -> 750
    }

    fun style(p: Preset): PieceStyle = when (p) {
        Preset.TINY, Preset.EASY -> PieceStyle.CHUNKY
        Preset.MEDIUM, Preset.HARD -> PieceStyle.CLASSIC
        Preset.EXPERT -> PieceStyle.WOBBLY
    }

    fun options(p: Preset): PlayOptions = when (p) {
        Preset.TINY -> PlayOptions(
            preset = p, ghost = Ghost.STRONG, snap = Snap.VERY_FORGIVING, helper = true,
        )
        Preset.EASY -> PlayOptions(preset = p, ghost = Ghost.STRONG, snap = Snap.FORGIVING)
        Preset.MEDIUM -> PlayOptions(preset = p, timer = true)
        Preset.HARD -> PlayOptions(
            preset = p, rotation = RotationMode.QUARTER, peek = PeekMode.LIMITED, peekLimit = 3,
            startInTray = true, boxPicture = false, timer = true,
        )
        Preset.EXPERT -> PlayOptions(
            preset = p, rotation = RotationMode.ANY, ghost = Ghost.OFF, peek = PeekMode.NEVER,
            snap = Snap.PRECISE, startInTray = true, boxPicture = false, hintButton = false, timer = true,
        )
    }
}
