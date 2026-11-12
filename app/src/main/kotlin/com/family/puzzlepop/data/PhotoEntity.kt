package com.family.puzzlepop.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity("photos")
data class PhotoEntity(
    @PrimaryKey val id: String,
    val fileName: String,
    val width: Int,
    val height: Int,
    val addedAt: Long,
    val name: String? = null,
    val favorite: Boolean = false,
    val kidApproved: Boolean = true,
)
