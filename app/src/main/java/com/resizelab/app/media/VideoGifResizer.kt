package com.resizelab.app.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.media.MediaMetadataRetriever
import androidx.core.graphics.createBitmap
import com.resizelab.app.data.ExportResult
import com.resizelab.app.data.ExportStage
import com.resizelab.app.data.MediaInfo
import com.resizelab.app.data.MediaKind
import com.resizelab.app.data.ResizeMode
import com.resizelab.app.gifencoder.AnimatedGifEncoder
import com.resizelab.app.util.ResolutionMath
import com.resizelab.app.util.VideoGifTimeline
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.coroutines.coroutineContext

class VideoGifProcessingException(message: String, cause: Throwable? = null) :
    IOException(message, cause)

/**
 * Converts a video to a looping GIF. Frames are sampled across the whole
 * duration at roughly 10 fps; longer videos are sampled at a lower rate so
 * the frame count stays bounded instead of truncating the clip.
 */
class VideoGifResizer(
    private val context: Context,
    private val saver: MediaStoreSaver
) {
    suspend fun resize(
        mediaInfo: MediaInfo,
        request: ImageResizeRequest,
        onProgress: (Float, ExportStage) -> Unit
    ): ExportResult = withContext(Dispatchers.IO) {
        require(mediaInfo.kind == MediaKind.VIDEO)
        val targetWidth = ResolutionMath.makeEven(request.target.width)
        val targetHeight = ResolutionMath.makeEven(request.target.height)
        var retriever: MediaMetadataRetriever? = null

        try {
            onProgress(0.02f, ExportStage.PREPARING)
            val activeRetriever = MediaMetadataRetriever()
            retriever = activeRetriever
            if (mediaInfo.uri.scheme == "file") {
                val path = mediaInfo.uri.path
                    ?: throw VideoGifProcessingException("Video cache file has no path")
                activeRetriever.setDataSource(path)
            } else {
                activeRetriever.setDataSource(context, mediaInfo.uri)
            }

            val durationMs = activeRetriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?: throw VideoGifProcessingException("Unable to read video duration")
            if (durationMs <= 0L) {
                throw VideoGifProcessingException("Video has no playable duration")
            }
            val rotation = activeRetriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toIntOrNull()
                ?: 0
            val plan = VideoGifTimeline.plan(durationMs)

            val result = saver.saveGif(
                sourceName = mediaInfo.displayName,
                width = targetWidth,
                height = targetHeight
            ) { output ->
                val encoder = AnimatedGifEncoder().apply {
                    setSize(targetWidth, targetHeight)
                    setQuality(GIF_COLOR_QUALITY)
                    setRepeat(0)
                }
                if (!encoder.start(output)) {
                    throw VideoGifProcessingException("Unable to start GIF encoder")
                }
                for (index in 0 until plan.frameCount) {
                    coroutineContext.ensureActive()
                    val timeUs = VideoGifTimeline.frameTimeUs(
                        index,
                        durationMs,
                        plan.frameCount
                    )
                    var sourceFrame = activeRetriever.getFrameAtTime(
                        timeUs,
                        MediaMetadataRetriever.OPTION_CLOSEST
                    ) ?: throw VideoGifProcessingException(
                        "Unable to decode video frame at " + timeUs + " us"
                    )
                    sourceFrame = applyRotation(sourceFrame, rotation)
                    val rendered = renderFrame(
                        source = sourceFrame,
                        targetWidth = targetWidth,
                        targetHeight = targetHeight,
                        mode = request.mode
                    )
                    sourceFrame.recycle()
                    try {
                        encoder.setDelay(plan.frameDelayMs)
                        if (!encoder.addFrame(rendered)) {
                            throw VideoGifProcessingException("Unable to encode GIF frame")
                        }
                    } finally {
                        rendered.recycle()
                    }
                    onProgress(
                        0.05f + ((index + 1).toFloat() / plan.frameCount) * 0.85f,
                        ExportStage.ENCODING
                    )
                }
                if (!encoder.finish()) {
                    throw VideoGifProcessingException("Unable to finish GIF encoder")
                }
            }
            onProgress(1f, ExportStage.SAVING)
            result
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: OutOfMemoryError) {
            throw error
        } catch (error: VideoGifProcessingException) {
            throw error
        } catch (error: Throwable) {
            throw VideoGifProcessingException("Unable to convert video to GIF", error)
        } finally {
            runCatching { retriever?.release() }
        }
    }

    private fun applyRotation(source: Bitmap, rotationDegrees: Int): Bitmap {
        val normalized = ((rotationDegrees % 360) + 360) % 360
        if (normalized == 0) return source
        val matrix = Matrix().apply { postRotate(normalized.toFloat()) }
        val rotated = Bitmap.createBitmap(
            source,
            0,
            0,
            source.width,
            source.height,
            matrix,
            true
        )
        if (rotated !== source) source.recycle()
        return rotated
    }

    private fun renderFrame(
        source: Bitmap,
        targetWidth: Int,
        targetHeight: Int,
        mode: ResizeMode
    ): Bitmap {
        val output = createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(Color.BLACK)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        if (mode == ResizeMode.STRETCH) {
            canvas.drawBitmap(
                source,
                null,
                RectF(0f, 0f, targetWidth.toFloat(), targetHeight.toFloat()),
                paint
            )
        } else {
            val rect = ResolutionMath.fittingRect(
                source.width,
                source.height,
                targetWidth,
                targetHeight,
                mode
            )
            canvas.drawBitmap(
                source,
                null,
                RectF(rect[0], rect[1], rect[0] + rect[2], rect[1] + rect[3]),
                paint
            )
        }
        return output
    }

    private companion object {
        const val GIF_COLOR_QUALITY = 10
    }
}
