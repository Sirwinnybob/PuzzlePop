package com.family.puzzlepop.play

import com.family.puzzlepop.engine.PuzzleLayout
import com.family.puzzlepop.engine.game.PuzzleGame
import com.family.puzzlepop.engine.options.PeekMode
import com.family.puzzlepop.engine.options.PlayOptions
import com.family.puzzlepop.engine.options.RotationMode
import com.family.puzzlepop.settings.KidSettings

/** What actually applies while playing: the saved [PlayOptions] after kid mode has had its say. */
data class EffectiveOptions(
    val rotation: RotationMode,
    val ghostAlpha: Float,
    val peek: PeekMode,
    val peekLimit: Int,
    val tolerance: Float,
    /** World distance for the kid magnet; -1 = none. */
    val magnetRadius: Float,
    val showTray: Boolean,
    val boxPicture: Boolean,
    val boxMovable: Boolean,
    val helper: Boolean,
    val helperDelayMs: Long,
    val hintButton: Boolean,
    val timer: Boolean,
)

const val KID_GHOST_ALPHA_VALUE = 0.45f

/** Kid mode always wins: no rotation, strong ghost, box fixed, no tray, no timer, kid snapping plus magnet. */
fun effectiveOptions(saved: PlayOptions, kid: Boolean, kidSettings: KidSettings, layout: PuzzleLayout): EffectiveOptions =
    if (kid) {
        EffectiveOptions(
            rotation = RotationMode.OFF,
            ghostAlpha = KID_GHOST_ALPHA_VALUE,
            peek = PeekMode.UNLIMITED,
            peekLimit = saved.peekLimit,
            tolerance = PuzzleGame.kidTolerance(layout),
            magnetRadius = PuzzleGame.kidMagnetRadius(layout),
            showTray = false,
            boxPicture = true,
            boxMovable = false,
            helper = kidSettings.helperOn,
            helperDelayMs = kidSettings.helperAfterSec * 1000L,
            hintButton = false,
            timer = false,
        )
    } else {
        EffectiveOptions(
            rotation = saved.rotation,
            ghostAlpha = saved.ghost.alpha,
            peek = saved.peek,
            peekLimit = saved.peekLimit,
            tolerance = PuzzleGame.tolerance(layout, saved.snap),
            magnetRadius = -1f,
            showTray = true,
            boxPicture = saved.boxPicture,
            boxMovable = true,
            helper = saved.helper,
            helperDelayMs = 20_000L,
            hintButton = saved.hintButton,
            timer = saved.timer,
        )
    }
