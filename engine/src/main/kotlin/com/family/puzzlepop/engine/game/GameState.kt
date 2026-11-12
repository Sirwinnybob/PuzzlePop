package com.family.puzzlepop.engine.game

import com.family.puzzlepop.engine.Pt
import kotlinx.serialization.Serializable

/**
 * Immutable snapshot of a puzzle in progress.
 *
 * Positions are the top-left of each piece's bounds in table coordinates (same space as the image).
 * [groupOf] maps a piece id to its group id; [zOrder] lists piece ids from bottom to top.
 */
@Serializable
data class GameState(
    val positions: List<Pt>,
    val groupOf: List<Int>,
    val locked: List<Boolean>,
    val zOrder: List<Int>,
    val elapsedMs: Long = 0,
    val moves: Int = 0,
    /** Piece ids currently in the tray, in tray display order. */
    val tray: List<Int> = emptyList(),
    /** Piece rotations in degrees; empty means all 0. */
    val rotations: List<Float> = emptyList(),
    val peeksUsed: Int = 0,
)

/** Outcome of dropping a piece or group. */
enum class DropResult { NONE, JOINED, LOCKED }
