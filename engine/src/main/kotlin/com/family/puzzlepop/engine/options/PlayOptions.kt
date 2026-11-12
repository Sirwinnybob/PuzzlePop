package com.family.puzzlepop.engine.options

import kotlinx.serialization.Serializable

@Serializable
enum class Preset { TINY, EASY, MEDIUM, HARD, EXPERT }

@Serializable
enum class RotationMode { OFF, QUARTER, ANY }

@Serializable
enum class Ghost(val alpha: Float) { STRONG(0.45f), FAINT(0.30f), OFF(0f) }

@Serializable
enum class PeekMode { UNLIMITED, LIMITED, NEVER }

@Serializable
enum class Snap(val factor: Float) { VERY_FORGIVING(0.40f), FORGIVING(0.30f), NORMAL(0.25f), PRECISE(0.15f) }

/** Per-puzzle play options. The defaults are the legacy (stage 1-2) options. */
@Serializable
data class PlayOptions(
    val preset: Preset? = null,
    val rotation: RotationMode = RotationMode.OFF,
    val ghost: Ghost = Ghost.FAINT,
    val peek: PeekMode = PeekMode.UNLIMITED,
    val peekLimit: Int = 3,
    val snap: Snap = Snap.NORMAL,
    val startInTray: Boolean = false,
    val boxPicture: Boolean = true,
    val helper: Boolean = false,
    val hintButton: Boolean = true,
    val timer: Boolean = false,
)
