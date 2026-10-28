package com.resizelab.app.media

import android.content.Context
import android.util.Log
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.AudioEncoderSettings
import androidx.media3.transformer.Codec
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.resizelab.app.data.ExportStage
import com.resizelab.app.data.MediaInfo
import com.resizelab.app.data.MediaKind
import com.resizelab.app.data.ResizeMode
import com.resizelab.app.util.PixelSize
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

@androidx.annotation.OptIn(UnstableApi::class)
class VideoResizer(
    private val context: Context,
    private val saver: MediaStoreSaver
) {
    suspend fun resize(
        mediaInfo: MediaInfo,
        target: PixelSize,
        mode: ResizeMode,
        audioBitrate: Int?,
        onProgress: (Float, ExportStage) -> Unit
    ): com.resizelab.app.data.ExportResult = withContext(Dispatchers.Main.immediate) {
        require(mediaInfo.kind == MediaKind.VIDEO)
        val exportDirectory = File(context.cacheDir, "exports").apply { mkdirs() }
        val tempFile = File(exportDirectory, "video_" + UUID.randomUUID() + ".mp4")
        var transformer: Transformer? = null
        try {
            onProgress(0.02f, ExportStage.PREPARING)
            val presentation = Presentation.createForWidthAndHeight(
                target.width,
                target.height,
                presentationLayout(mode)
            )
            val editedMediaItem = EditedMediaItem.Builder(MediaItem.fromUri(mediaInfo.uri))
                .setEffects(Effects(emptyList(), listOf(presentation)))
                .build()
            val completed = CompletableDeferred<Unit>()
            val transformerBuilder = Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
            if (audioBitrate != null && audioBitrate > 0) {
                // DefaultEncoderFactory does not force audio transcoding on its own,
                // so wrap it to make sure the requested bitrate is actually applied.
                val encoderFactory = DefaultEncoderFactory.Builder(context)
                    .setRequestedAudioEncoderSettings(
                        AudioEncoderSettings.Builder()
                            .setBitrate(audioBitrate)
                            .build()
                    )
                    .build()
                transformerBuilder.setEncoderFactory(
                    ForcedAudioEncodingEncoderFactory(encoderFactory)
                )
            }
            val activeTransformer = transformerBuilder
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(
                        composition: Composition,
                        exportResult: ExportResult
                    ) {
                        completed.complete(Unit)
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException
                    ) {
                        Log.e(TAG, "Video transformation failed", exportException)
                        completed.completeExceptionally(exportException)
                    }
                })
                .build()
            transformer = activeTransformer

            onProgress(0.04f, ExportStage.ENCODING)
            activeTransformer.start(editedMediaItem, tempFile.absolutePath)

            val progressJob = launch {
                val holder = ProgressHolder()
                while (isActive) {
                    val state = activeTransformer.getProgress(holder)
                    if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                        val progress = 0.04f + holder.progress / 100f * 0.84f
                        onProgress(progress, ExportStage.ENCODING)
                    }
                    delay(200L)
                }
            }
            try {
                completed.await()
            } finally {
                progressJob.cancel()
            }

            onProgress(0.9f, ExportStage.SAVING)
            val result = withContext(Dispatchers.IO) {
                saver.saveVideo(
                    tempFile = tempFile,
                    sourceName = mediaInfo.displayName,
                    width = target.width,
                    height = target.height
                ) { copyProgress ->
                    onProgress(0.9f + copyProgress * 0.09f, ExportStage.SAVING)
                }
            }
            onProgress(1f, ExportStage.SAVING)
            result
        } finally {
            withContext(NonCancellable) {
                runCatching { transformer?.cancel() }
                withContext(Dispatchers.IO) {
                    runCatching { tempFile.delete() }
                }
            }
        }
    }

    private fun presentationLayout(mode: ResizeMode): Int {
        return when (mode) {
            ResizeMode.FIT -> Presentation.LAYOUT_SCALE_TO_FIT
            ResizeMode.CROP -> Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP
            ResizeMode.STRETCH -> Presentation.LAYOUT_STRETCH_TO_FIT
        }
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    private class ForcedAudioEncodingEncoderFactory(
        private val delegate: Codec.EncoderFactory
    ) : Codec.EncoderFactory {
        override fun createForAudioEncoding(format: Format): Codec =
            delegate.createForAudioEncoding(format)

        override fun createForVideoEncoding(format: Format): Codec =
            delegate.createForVideoEncoding(format)

        override fun audioNeedsEncoding(): Boolean = true

        override fun videoNeedsEncoding(): Boolean = delegate.videoNeedsEncoding()
    }

    private companion object {
        const val TAG = "VideoResizer"
    }
}