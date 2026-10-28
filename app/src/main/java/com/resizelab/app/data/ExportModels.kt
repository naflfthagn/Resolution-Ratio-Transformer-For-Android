package com.resizelab.app.data

import android.net.Uri

enum class ExportStage {
    PREPARING,
    DECODING,
    DRAWING,
    ENCODING,
    SAVING
}

data class ExportResult(
    val uri: Uri,
    val kind: MediaKind,
    val mimeType: String,
    val displayName: String,
    val width: Int,
    val height: Int,
    val sizeBytes: Long
)
