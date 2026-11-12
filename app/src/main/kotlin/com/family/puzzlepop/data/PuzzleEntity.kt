package com.family.puzzlepop.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity("puzzles")
data class PuzzleEntity(
    @PrimaryKey val id: String,
    val photoId: String,
    val pieceCount: Int,
    val placedCount: Int,
    val finished: Boolean,
    val bestTimeMs: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
)
