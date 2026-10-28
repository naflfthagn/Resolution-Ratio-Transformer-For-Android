package com.resizelab.app.ui

import com.resizelab.app.data.AudioQuality
import com.resizelab.app.data.ExportResult
import com.resizelab.app.data.ExportStage
import com.resizelab.app.data.ImageFormat
import com.resizelab.app.data.MediaInfo
import com.resizelab.app.data.MediaKind
import com.resizelab.app.data.ResolutionPreset
import com.resizelab.app.data.ResizeMode
import com.resizelab.app.util.PixelSize

sealed interface ExportStatus {
    data object Idle : ExportStatus
    data class Running(val progress: Float, val stage: ExportStage) : ExportStatus
    data class Success(val result: ExportResult) : ExportStatus
}

data class EditorUiState(
    val isInspecting: Boolean = false,
    val media: MediaInfo? = null,
    val widthText: String = "",
    val heightText: String = "",
    val preset: ResolutionPreset = ResolutionPreset.ORIGINAL,
    val lockAspect: Boolean = true,
    val mode: ResizeMode = ResizeMode.FIT,
    val imageFormat: ImageFormat = ImageFormat.JPEG,
    val audioQuality: AudioQuality = AudioQuality.AUTO,
    val quality: Int = 92,
    val exportStatus: ExportStatus = ExportStatus.Idle
) {
    val width: Int?
        get() = widthText.toIntOrNull()
    val height: Int?
        get() = heightText.toIntOrNull()

    val targetSize: PixelSize?
        get() {
            val w = width ?: return null
            val h = height ?: return null
            if (w <= 0 || h <= 0) return null
            return PixelSize(w, h)
        }

    val isVideo: Boolean
        get() = media?.kind == MediaKind.VIDEO

    val isExporting: Boolean
        get() = exportStatus is ExportStatus.Running
}
