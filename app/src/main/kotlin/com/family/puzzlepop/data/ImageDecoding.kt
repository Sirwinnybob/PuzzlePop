package com.family.puzzlepop.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.IOException
import java.io.InputStream

/** Smallest power-of-two sample size so that the long side of a [w]x[h] image is at most [maxLongSide]. */
internal fun sampleSizeFor(w: Int, h: Int, maxLongSide: Int): Int {
    var sample = 1
    while (maxOf(w, h) / sample > maxLongSide) sample *= 2
    return sample
}

/** Reads the EXIF orientation from [input] (closed by the caller). */
internal fun readExifOrientation(input: InputStream): Int =
    try {
        ExifInterface(input).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    } catch (_: IOException) {
        ExifInterface.ORIENTATION_NORMAL
    }

/** Returns [src] with the EXIF [orientation] applied (recycling [src] when a new bitmap is made). */
internal fun applyExifOrientation(src: Bitmap, orientation: Int): Bitmap {
    val m = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
        ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
        ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
        ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(270f); m.postScale(-1f, 1f) }
        ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
        else -> return src
    }
    val out = Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    if (out !== src) src.recycle()
    return out
}

/**
 * Decodes a bitmap from an input stream opened by [open] (called more than once: bounds, pixels, EXIF),
 * sampled so the long side is at most [maxLongSide], with EXIF orientation applied.
 */
internal fun decodeSampled(open: () -> InputStream?, maxLongSide: Int): Bitmap {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    // decodeStream returns null in bounds-only mode, so use a separate flag to detect a missing stream.
    val stream = open() ?: throw IOException("Cannot open image")
    stream.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Not an image")
    val opts = BitmapFactory.Options().apply {
        inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, maxLongSide)
    }
    val raw = open()?.use { BitmapFactory.decodeStream(it, null, opts) } ?: throw IOException("Cannot decode image")
    val orientation = open()?.use { readExifOrientation(it) } ?: ExifInterface.ORIENTATION_NORMAL
    return applyExifOrientation(raw, orientation)
}

/** Decodes [file] sampled so the long side is at most [maxLongSide], with EXIF orientation applied. */
suspend fun decodeSampled(file: File, maxLongSide: Int): Bitmap =
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        decodeSampled({ file.inputStream() }, maxLongSide)
    }
