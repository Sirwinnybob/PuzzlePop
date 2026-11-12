package com.family.puzzlepop.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID

class PhotoRepository(
    context: Context,
    private val db: AppDatabase,
    private val puzzles: PuzzleRepository,
) {
    private val appContext = context.applicationContext
    private val photosDir = File(appContext.filesDir, "photos")
    private val thumbsDir = File(appContext.filesDir, "thumbs")

    fun observePhotos(): Flow<List<PhotoEntity>> = db.photoDao().observeAll()

    /** True when the library holds at least one photo (used to tell a fresh install from lost settings). */
    suspend fun hasAnyPhoto(): Boolean = db.photoDao().observeAll().first().isNotEmpty()

    /** Only the photos a grown-up allowed in kid mode, newest first. */
    fun observeKidPhotos(): Flow<List<PhotoEntity>> = db.photoDao().observeKidApproved()

    suspend fun setKidApproved(id: String, approved: Boolean) = db.photoDao().setKidApproved(id, approved)

    fun photoFile(photo: PhotoEntity): File = File(photosDir, photo.fileName)

    fun thumbFile(photo: PhotoEntity): File = File(thumbsDir, photo.fileName)

    suspend fun get(id: String): PhotoEntity? = db.photoDao().get(id)

    /** Decodes the picked image (long side <= 4096, EXIF applied), stores it as a JPEG plus a thumbnail. */
    suspend fun importFromUri(uri: Uri): PhotoEntity = withContext(Dispatchers.IO) {
        val resolver = appContext.contentResolver
        val id = UUID.randomUUID().toString()
        val fileName = "$id.jpg"
        photosDir.mkdirs()
        thumbsDir.mkdirs()
        val photoFile = File(photosDir, fileName)
        val thumbFile = File(thumbsDir, fileName)
        try {
            val bitmap = decodeSampled({ resolver.openInputStream(uri) }, MAX_LONG_SIDE)
            try {
                writeJpeg(bitmap, photoFile)
                val scale = THUMB_LONG_SIDE.toFloat() / maxOf(bitmap.width, bitmap.height)
                val thumb = if (scale < 1f) {
                    Bitmap.createScaledBitmap(
                        bitmap,
                        maxOf(1, Math.round(bitmap.width * scale)),
                        maxOf(1, Math.round(bitmap.height * scale)),
                        true,
                    )
                } else bitmap
                try {
                    writeJpeg(thumb, thumbFile)
                } finally {
                    if (thumb !== bitmap) thumb.recycle()
                }
                val photo = PhotoEntity(
                    id = id,
                    fileName = fileName,
                    width = bitmap.width,
                    height = bitmap.height,
                    addedAt = System.currentTimeMillis(),
                )
                db.photoDao().insert(photo)
                photo
            } finally {
                bitmap.recycle()
            }
        } catch (e: Throwable) {
            photoFile.delete()
            thumbFile.delete()
            throw e
        }
    }

    suspend fun setFavorite(id: String, favorite: Boolean) = db.photoDao().setFavorite(id, favorite)

    /** Deletes the photo, its files and every puzzle (database row and save file) made from it. */
    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        val photo = db.photoDao().get(id)
        puzzles.deleteForPhoto(id)
        db.photoDao().delete(id)
        if (photo != null) {
            photoFile(photo).delete()
            thumbFile(photo).delete()
        }
    }

    private fun writeJpeg(bitmap: Bitmap, file: File) {
        file.outputStream().use { out ->
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)) throw IOException("JPEG encode failed")
        }
    }

    private companion object {
        const val MAX_LONG_SIDE = 4096
        const val THUMB_LONG_SIDE = 512
        const val JPEG_QUALITY = 92
    }
}
