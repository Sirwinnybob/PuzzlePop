package com.family.puzzlepop.library

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

// Thumbnails are ~512 px JPEGs; a small in-memory cache keeps scrolling the grid smooth.
private val thumbCache = object : LruCache<String, ImageBitmap>(24 * 1024) {
    override fun sizeOf(key: String, value: ImageBitmap): Int = value.asAndroidBitmap().byteCount / 1024
}

@Composable
internal fun rememberThumb(file: File): State<ImageBitmap?> {
    val key = file.path
    return produceState(thumbCache.get(key), key) {
        if (value == null) {
            value = withContext(Dispatchers.IO) {
                BitmapFactory.decodeFile(key)?.asImageBitmap()?.also { thumbCache.put(key, it) }
            }
        }
    }
}

/** A photo thumbnail, or a soft placeholder while it loads. The caller supplies size and clipping. */
@Composable
internal fun ThumbImage(
    file: File,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val bitmap by rememberThumb(file)
    val shown = bitmap
    if (shown != null) {
        Image(shown, contentDescription, modifier = modifier, contentScale = contentScale)
    } else {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant))
    }
}
