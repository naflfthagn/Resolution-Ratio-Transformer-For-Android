package com.resizelab.app.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.core.graphics.createBitmap
import com.resizelab.app.data.ExportResult
import com.resizelab.app.data.ExportStage
import com.resizelab.app.data.MediaInfo
import com.resizelab.app.data.MediaKind
import com.resizelab.app.data.ResizeMode
import com.resizelab.app.gifencoder.AnimatedGifEncoder
import com.resizelab.app.util.ResolutionMath
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import pl.droidsonroids.gif.GifDecoder
import pl.droidsonroids.gif.InputSource
import java.io.File
import java.io.IOException
import kotlin.coroutines.coroutineContext

class GifProcessingException(message: String, cause: Throwable? = null) :
    IOException(message, cause)

class GifResizer(
    private val context: Context,
    private val saver: MediaStoreSaver
) {
    suspend fun resize(
        mediaInfo: MediaInfo,
        request: ImageResizeRequest,
        onProgress: (Float, ExportStage) -> Unit
    ): ExportResult = withContext(Dispatchers.IO) {
        require(mediaInfo.kind == MediaKind.IMAGE && mediaInfo.isGif)
        var decoder: GifDecoder? = null
        var sourceFrame: Bitmap? = null
        try {
            onProgress(0.02f, ExportStage.PREPARING)
            val inputSource = if (mediaInfo.uri.scheme == "file") {
                val path = mediaInfo.uri.path
                    ?: throw GifProcessingException("GIF cache file has no path")
                InputSource.FileSource(File(path))
            } else {
                InputSource.UriSource(context.contentResolver, mediaInfo.uri)
            }
            val activeDecoder = GifDecoder(inputSource)
            decoder = activeDecoder
            val sourceWidth = activeDecoder.width
            val sourceHeight = activeDecoder.height
            val frameCount = activeDecoder.numberOfFrames
            if (sourceWidth <= 0 || sourceHeight <= 0 || frameCount <= 0) {
                throw GifProcessingException("GIF has no decodable frames")
            }
            val activeSourceFrame = createBitmap(
                sourceWidth,
                sourceHeight,
                Bitmap.Config.ARGB_8888
            )
            sourceFrame = activeSourceFrame

            val encoder = AnimatedGifEncoder().apply {
                setSize(request.target.width, request.target.height)
                setQuality(GIF_COLOR_QUALITY)
                setRepeat(activeDecoder.loopCount.coerceAtLeast(0))
            }

            onProgress(0.06f, ExportStage.DRAWING)
            val result = saver.saveGif(
                sourceName = mediaInfo.displayName,
                width = request.target.width,
                height = request.target.height
            ) { output ->
                if (!encoder.start(output)) {
                    throw GifProcessingException("Unable to start GIF encoder")
                }
                for (index in 0 until frameCount) {
                    coroutineContext.ensureActive()
                    activeDecoder.seekToFrame(index, activeSourceFrame)
                    val resizedFrame = renderFrame(activeSourceFrame, request)
                    try {
                        encoder.setDelay(normalizeDelay(activeDecoder.getFrameDuration(index)))
                        if (!encoder.addFrame(resizedFrame)) {
                            throw GifProcessingException("Unable to encode GIF frame")
                        }
                    } finally {
                        resizedFrame.recycle()
                    }
                    onProgress(
                        0.08f + ((index + 1).toFloat() / frameCount) * 0.84f,
                        ExportStage.ENCODING
                    )
                }
                if (!encoder.finish()) {
                    throw GifProcessingException("Unable to finish GIF encoder")
                }
            }
            onProgress(1f, ExportStage.SAVING)
            result
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: OutOfMemoryError) {
            throw error
        } catch (error: GifProcessingException) {
            throw error
        } catch (error: Throwable) {
            throw GifProcessingException("Unable to export animated GIF", error)
        } finally {
            sourceFrame?.recycle()
            runCatching { decoder?.recycle() }
        }
    }

    private fun renderFrame(source: Bitmap, request: ImageResizeRequest): Bitmap {
        val target = request.target
        val output = createBitmap(target.width, target.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        if (request.mode == ResizeMode.STRETCH) {
            canvas.drawBitmap(
                source,
                null,
                RectF(0f, 0f, target.width.toFloat(), target.height.toFloat()),
                paint
            )
        } else {
            val rect = ResolutionMath.fittingRect(
                source.width,
                source.height,
                target.width,
                target.height,
                request.mode
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

    private fun normalizeDelay(durationMs: Int): Int {
        return if (durationMs <= 0) 100 else durationMs.coerceIn(10, 60_000)
    }

    private companion object {
        const val GIF_COLOR_QUALITY = 10
    }
}
