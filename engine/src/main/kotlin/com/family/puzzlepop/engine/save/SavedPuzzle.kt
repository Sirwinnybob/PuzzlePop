package com.family.puzzlepop.engine.save

import com.family.puzzlepop.engine.Pt
import com.family.puzzlepop.engine.PuzzleSpec
import com.family.puzzlepop.engine.game.GameState
import com.family.puzzlepop.engine.options.PlayOptions
import kotlinx.serialization.Serializable

/** Everything persisted for one puzzle: its spec (to regenerate the pieces) and the game progress. */
@Serializable
data class SavedPuzzle(
    val version: Int = 1,
    val puzzleId: String,
    val photoId: String,
    val spec: PuzzleSpec,
    val state: GameState,
    val createdAt: Long,
    val updatedAt: Long,
    val options: PlayOptions = PlayOptions(),
    /** Box picture position as 0..1 screen fractions; null means default. */
    val boxPos: Pt? = null,
    /** The box picture was hidden by the user (only meaningful once [boxPos] is set). */
    val boxHidden: Boolean = false,
)
