package com.family.puzzlepop.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PhotoDao {
    @Query("SELECT * FROM photos ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<PhotoEntity>>

    @Query("SELECT * FROM photos WHERE id = :id")
    suspend fun get(id: String): PhotoEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(photo: PhotoEntity)

    @Query("SELECT * FROM photos WHERE kidApproved = 1 ORDER BY addedAt DESC")
    fun observeKidApproved(): Flow<List<PhotoEntity>>

    @Query("UPDATE photos SET kidApproved = :approved WHERE id = :id")
    suspend fun setKidApproved(id: String, approved: Boolean)

    @Query("UPDATE photos SET favorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: String, favorite: Boolean)

    @Query("DELETE FROM photos WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface PuzzleDao {
    @Query("SELECT * FROM puzzles WHERE finished = 0 ORDER BY updatedAt DESC")
    fun observeInProgress(): Flow<List<PuzzleEntity>>

    @Query("SELECT * FROM puzzles WHERE photoId = :photoId")
    suspend fun forPhoto(photoId: String): List<PuzzleEntity>

    @Query("SELECT * FROM puzzles WHERE id = :id")
    suspend fun get(id: String): PuzzleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(puzzle: PuzzleEntity)

    @Query("UPDATE puzzles SET placedCount = :placedCount, finished = :finished, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateProgress(id: String, placedCount: Int, finished: Boolean, updatedAt: Long)

    @Query("DELETE FROM puzzles WHERE id = :id")
    suspend fun delete(id: String)
}
