package com.resizelab.app.ui

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.transformer.ExportException
import com.resizelab.app.R
import com.resizelab.app.data.AudioQuality
import com.resizelab.app.data.ExportStage
import com.resizelab.app.data.ImageFormat
import com.resizelab.app.data.MediaInfo
import com.resizelab.app.data.MediaInspectionException
import com.resizelab.app.data.MediaInspectionFailure
import com.resizelab.app.data.MediaInspector
import com.resizelab.app.data.MediaKind
import com.resizelab.app.data.ResolutionPreset
import com.resizelab.app.data.ResizeMode
import com.resizelab.app.media.GifProcessingException
import com.resizelab.app.media.GifResizer
import com.resizelab.app.media.ImageDecodeException
import com.resizelab.app.media.ImageResizeRequest
import com.resizelab.app.media.ImageResizer
import com.resizelab.app.media.MediaSourceCache
import com.resizelab.app.media.MediaStoreSaver
import com.resizelab.app.media.VideoResizer
import com.resizelab.app.util.PixelSize
import com.resizelab.app.util.ResolutionMath
import com.resizelab.app.util.ResolutionMath.MAX_IMAGE_PIXELS
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException

class EditorViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application
    private val inspector = MediaInspector(application)
    private val saver = MediaStoreSaver(application)
    private val sourceCache = MediaSourceCache(application)
    private val imageResizer = ImageResizer(application, saver)
    private val gifResizer = GifResizer(application, saver)
    private val videoResizer = VideoResizer(application, saver)
    private var cachedSourceFile: File? = null

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState = _uiState.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private var inspectJob: Job? = null
    private var exportJob: Job? = null

    fun selectMedia(uri: Uri) {
        if (_uiState.value.isExporting) return
        inspectJob?.cancel()
        sourceCache.delete(cachedSourceFile)
        cachedSourceFile = null
        _uiState.value = EditorUiState(isInspecting = true)
        inspectJob = viewModelScope.launch {
            try {
                var info = inspector.inspect(uri)
                if (info.kind == MediaKind.IMAGE) {
                    val cached = sourceCache.copyImage(
                        uri = uri,
                        displayName = info.displayName,
                        mimeType = info.mimeType
                    )
                    cachedSourceFile = cached
                    info = info.copy(
                        uri = Uri.fromFile(cached),
                        sizeBytes = cached.length()
                    )
                }
                val isVideo = info.kind == MediaKind.VIDEO
                val initialPreset = if (maxOf(info.width, info.height) > 1920) {
                    ResolutionPreset.FHD_1080
                } else {
                    ResolutionPreset.ORIGINAL
                }
                val rawTarget = if (initialPreset == ResolutionPreset.ORIGINAL) {
                    info.sourceSize
                } else {
                    ResolutionMath.sizeForLongEdge(
                        info.sourceSize,
                        initialPreset.longEdge ?: 1920,
                        isVideo
                    )
                }
                var target = ResolutionMath.clampToMax(rawTarget, isVideo)
                if (isVideo) target = target.toEven()
                val initialFormat = when {
                    isVideo -> ImageFormat.JPEG
                    info.isGif -> ImageFormat.GIF
                    info.mimeType == "image/png" -> ImageFormat.PNG
                    info.mimeType == "image/webp" -> ImageFormat.WEBP
                    else -> ImageFormat.JPEG
                }
                _uiState.value = EditorUiState(
                    media = info,
                    widthText = target.width.toString(),
                    heightText = target.height.toString(),
                    preset = initialPreset,
                    imageFormat = initialFormat
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                Log.e(TAG, "Unable to inspect selected media: $uri", error)
                sourceCache.delete(cachedSourceFile)
                cachedSourceFile = null
                _uiState.value = EditorUiState()
                _messages.trySend(inspectionErrorMessage(error))
            }
        }
    }

    fun clearMedia() {
        if (_uiState.value.isExporting) return
        inspectJob?.cancel()
        sourceCache.delete(cachedSourceFile)
        cachedSourceFile = null
        _uiState.value = EditorUiState()
    }

    fun onWidthChanged(rawValue: String) {
        val current = _uiState.value
        val media = current.media ?: return
        if (current.isExporting) return
        val isVideo = current.isVideo
        val maxDimension = ResolutionMath.maxDimension(isVideo)
        val digits = rawValue.filter { it.isDigit() }.take(5)
        val parsed = digits.toIntOrNull()
        val widthText = if (parsed != null && parsed > maxDimension) {
            maxDimension.toString()
        } else {
            digits
        }
        var next = current.copy(
            widthText = widthText,
            preset = ResolutionPreset.CUSTOM,
            exportStatus = current.clearedFeedback()
        )
        if (next.lockAspect) {
            val width = widthText.toIntOrNull()
            if (width != null && width > 0) {
                val derived = ResolutionMath.sizeForWidth(media.sourceSize, width, isVideo)
                if (derived.height > maxDimension) {
                    val clamped = ResolutionMath.clampToMax(derived, isVideo)
                    next = next.copy(
                        widthText = clamped.width.toString(),
                        heightText = clamped.height.toString()
                    )
                } else {
                    next = next.copy(heightText = derived.height.toString())
                }
            }
        }
        _uiState.value = next
    }

    fun onHeightChanged(rawValue: String) {
        val current = _uiState.value
        val media = current.media ?: return
        if (current.isExporting) return
        val isVideo = current.isVideo
        val maxDimension = ResolutionMath.maxDimension(isVideo)
        val digits = rawValue.filter { it.isDigit() }.take(5)
        val parsed = digits.toIntOrNull()
        val heightText = if (parsed != null && parsed > maxDimension) {
            maxDimension.toString()
        } else {
            digits
        }
        var next = current.copy(
            heightText = heightText,
            preset = ResolutionPreset.CUSTOM,
            exportStatus = current.clearedFeedback()
        )
        if (next.lockAspect) {
            val height = heightText.toIntOrNull()
            if (height != null && height > 0) {
                val derived = ResolutionMath.sizeForHeight(media.sourceSize, height, isVideo)
                if (derived.width > maxDimension) {
                    val clamped = ResolutionMath.clampToMax(derived, isVideo)
                    next = next.copy(
                        widthText = clamped.width.toString(),
                        heightText = clamped.height.toString()
                    )
                } else {
                    next = next.copy(widthText = derived.width.toString())
                }
            }
        }
        _uiState.value = next
    }

    fun applyPreset(preset: ResolutionPreset) {
        val current = _uiState.value
        val media = current.media ?: return
        if (current.isExporting || preset == ResolutionPreset.CUSTOM) return
        val isVideo = current.isVideo
        val rawTarget = when (preset) {
            ResolutionPreset.ORIGINAL -> media.sourceSize
            ResolutionPreset.CUSTOM -> return
            else -> ResolutionMath.sizeForLongEdge(
                media.sourceSize,
                preset.longEdge ?: return,
                isVideo
            )
        }
        var target = ResolutionMath.clampToMax(rawTarget, isVideo)
        if (isVideo) target = target.toEven()
        _uiState.value = current.copy(
            widthText = target.width.toString(),
            heightText = target.height.toString(),
            preset = preset,
            exportStatus = current.clearedFeedback()
        )
    }

    fun toggleAspectLock() {
        val current = _uiState.value
        val media = current.media ?: return
        if (current.isExporting) return
        if (!current.lockAspect) {
            var next = current.copy(
                lockAspect = true,
                exportStatus = current.clearedFeedback()
            )
            val width = current.width
            val height = current.height
            val isVideo = current.isVideo
            val maxDimension = ResolutionMath.maxDimension(isVideo)
            if (width != null && width > 0) {
                val derived = ResolutionMath.sizeForWidth(media.sourceSize, width, isVideo)
                if (derived.height > maxDimension) {
                    val clamped = ResolutionMath.clampToMax(derived, isVideo)
                    next = next.copy(
                        widthText = clamped.width.toString(),
                        heightText = clamped.height.toString()
                    )
                } else {
                    next = next.copy(heightText = derived.height.toString())
                }
            } else if (height != null && height > 0) {
                val derived = ResolutionMath.sizeForHeight(media.sourceSize, height, isVideo)
                next = next.copy(widthText = derived.width.toString())
            }
            _uiState.value = next
        } else {
            _uiState.value = current.copy(
                lockAspect = false,
                exportStatus = current.clearedFeedback()
            )
        }
    }

    fun setMode(mode: ResizeMode) {
        val current = _uiState.value
        if (current.isExporting) return
        _uiState.value = current.copy(mode = mode, exportStatus = current.clearedFeedback())
    }

    fun setImageFormat(format: ImageFormat) {
        val current = _uiState.value
        if (current.isExporting) return
        val isGif = current.media?.isGif == true
        if ((isGif && format != ImageFormat.GIF) || (!isGif && format == ImageFormat.GIF)) {
            return
        }
        _uiState.value = current.copy(
            imageFormat = format,
            exportStatus = current.clearedFeedback()
        )
    }

    fun setQuality(quality: Int) {
        val current = _uiState.value
        if (current.isExporting) return
        _uiState.value = current.copy(
            quality = quality.coerceIn(40, 100),
            exportStatus = current.clearedFeedback()
        )
    }

    fun setAudioQuality(quality: AudioQuality) {
        val current = _uiState.value
        if (current.isExporting) return
        _uiState.value = current.copy(
            audioQuality = quality,
            exportStatus = current.clearedFeedback()
        )
    }

    fun startExport() {
        val current = _uiState.value
        val media = current.media ?: return
        if (current.isExporting) return

        val width = current.width
        val height = current.height
        if (width == null || height == null || width <= 0 || height <= 0) {
            _messages.trySend(app.getString(R.string.error_empty_size))
            return
        }

        val isVideo = current.isVideo
        var target = PixelSize(width, height)
        val maxDimension = ResolutionMath.maxDimension(isVideo)
        if (target.width < ResolutionMath.MIN_DIMENSION ||
            target.height < ResolutionMath.MIN_DIMENSION ||
            target.width > maxDimension ||
            target.height > maxDimension
        ) {
            _messages.trySend(
                app.getString(
                    R.string.error_size_range,
                    ResolutionMath.MIN_DIMENSION,
                    maxDimension
                )
            )
            return
        }
        if (!isVideo && target.width.toLong() * target.height > MAX_IMAGE_PIXELS) {
            _messages.trySend(
                app.getString(
                    R.string.error_pixel_limit,
                    (MAX_IMAGE_PIXELS / 1_000_000L).toInt()
                )
            )
            return
        }
        if (isVideo) {
            target = target.toEven()
        }

        val startedState = current.copy(
            widthText = target.width.toString(),
            heightText = target.height.toString(),
            exportStatus = ExportStatus.Running(0.02f, ExportStage.PREPARING)
        )
        _uiState.value = startedState

        val format = current.imageFormat
        val quality = current.quality
        val mode = current.mode
        val audioQuality = current.audioQuality

        exportJob = viewModelScope.launch {
            try {
                val result = if (isVideo) {
                    videoResizer.resize(
                        mediaInfo = media,
                        target = target,
                        mode = mode,
                        audioBitrate = audioQuality.bitrate
                    ) { progress, stage ->
                        updateProgress(progress, stage)
                    }
                } else if (media.isGif) {
                    gifResizer.resize(
                        media,
                        ImageResizeRequest(
                            target = target,
                            mode = mode,
                            format = ImageFormat.GIF,
                            quality = quality
                        )
                    ) { progress, stage ->
                        updateProgress(progress, stage)
                    }
                } else {
                    imageResizer.resize(
                        media,
                        ImageResizeRequest(
                            target = target,
                            mode = mode,
                            format = format,
                            quality = quality
                        )
                    ) { progress, stage ->
                        updateProgress(progress, stage)
                    }
                }
                _uiState.update { state ->
                    if (state.exportStatus is ExportStatus.Running) {
                        state.copy(exportStatus = ExportStatus.Success(result))
                    } else {
                        state
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                Log.e(TAG, "Export failed", error)
                _uiState.update { state ->
                    if (state.exportStatus is ExportStatus.Running) {
                        state.copy(exportStatus = ExportStatus.Idle)
                    } else {
                        state
                    }
                }
                _messages.trySend(exportErrorMessage(error, isVideo))
            }
        }
    }

    fun cancelExport() {
        if (!_uiState.value.isExporting) return
        _uiState.update { it.copy(exportStatus = ExportStatus.Idle) }
        exportJob?.cancel()
        _messages.trySend(app.getString(R.string.msg_export_cancelled))
    }

    fun notifyStoragePermissionDenied() {
        _messages.trySend(app.getString(R.string.msg_storage_permission_denied))
    }

    private fun updateProgress(progress: Float, stage: ExportStage) {
        _uiState.update { state ->
            if (state.exportStatus is ExportStatus.Running) {
                state.copy(
                    exportStatus = ExportStatus.Running(
                        progress.coerceIn(0f, 1f),
                        stage
                    )
                )
            } else {
                state
            }
        }
    }

    private fun EditorUiState.clearedFeedback(): ExportStatus =
        if (exportStatus is ExportStatus.Success) ExportStatus.Idle else exportStatus

    private fun inspectionErrorMessage(error: Throwable): String {
        return when (error) {
            is OutOfMemoryError -> app.getString(R.string.error_image_too_large)
            is SecurityException -> app.getString(R.string.error_read_media)
            is MediaInspectionException -> when (error.failure) {
                MediaInspectionFailure.UNSUPPORTED ->
                    app.getString(R.string.error_unsupported_media)
                MediaInspectionFailure.DECODE ->
                    app.getString(R.string.error_decode_image)
                MediaInspectionFailure.READ ->
                    app.getString(R.string.error_read_media)
            }
            else -> app.getString(R.string.error_read_media)
        }
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private fun exportErrorMessage(error: Throwable, isVideo: Boolean): String {
        return when (error) {
            is OutOfMemoryError -> app.getString(R.string.error_out_of_memory)
            is GifProcessingException -> app.getString(R.string.error_gif_export)
            is ImageDecodeException -> app.getString(R.string.error_decode_image)
            is ExportException -> app.getString(R.string.error_video_export)
            is SecurityException -> app.getString(R.string.error_save)
            is IOException -> if (isVideo) {
                app.getString(R.string.error_video_export)
            } else {
                app.getString(R.string.error_save)
            }
            else -> if (isVideo) {
                app.getString(R.string.error_video_export)
            } else {
                app.getString(R.string.error_export_generic)
            }
        }
    }

    override fun onCleared() {
        sourceCache.delete(cachedSourceFile)
        cachedSourceFile = null
        super.onCleared()
    }

    private companion object {
        const val TAG = "EditorViewModel"
    }
}
