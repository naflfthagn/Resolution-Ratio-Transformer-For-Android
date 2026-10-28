package com.resizelab.app.data

import android.net.Uri
import com.resizelab.app.util.PixelSize

enum class MediaKind {
    IMAGE,
    VIDEO
}

data class MediaInfo(
    val uri: Uri,
    val kind: MediaKind,
    val displayName: String,
    val mimeType: String,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
    val durationMs: Long? = null,
    val rotationDegrees: Int = 0,
    val hasAudio: Boolean = false,
    val exifOrientation: Int = 1
) {
    val aspectRatio: Float
        get() = if (height > 0) width.toFloat() / height else 1f

    val sourceSize: PixelSize
        get() = PixelSize(width, height)
}
