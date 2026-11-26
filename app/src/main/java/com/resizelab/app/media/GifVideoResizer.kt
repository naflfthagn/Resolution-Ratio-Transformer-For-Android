package com.resizelab.app.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageFormat
import android.graphics.Paint
import android.graphics.RectF
import android.media.Image
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaMuxer
import androidx.core.graphics.createBitmap
import com.resizelab.app.data.ExportResult
import com.resizelab.app.data.ExportStage
import com.resizelab.app.data.GifSpeed
import com.resizelab.app.data.MediaInfo
import com.resizelab.app.data.MediaKind
import com.resizelab.app.data.ResizeMode
import com.resizelab.app.util.GifTiming
import com.resizelab.app.util.ResolutionMath
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import pl.droidsonroids.gif.GifDecoder
import pl.droidsonroids.gif.InputSource
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.util.UUID
import kotlin.coroutines.coroutineContext

class GifVideoProcessingException(message: String, cause: Throwable? = null) :
    IOException(message, cause)

/**
 * Converts an animated GIF to a silent H.264 MP4. Every GIF frame is rendered
 * at the requested resolution and timestamped with its (speed-adjusted) delay.
 * A duplicate final frame is written at the total duration so the last frame's
 * display time survives in the MP4 timeline.
 */
class GifVideoResizer(
    private val context: Context,
    private val saver: MediaStoreSaver
) {
    suspend fun resize(
        mediaInfo: MediaInfo,
        request: ImageResizeRequest,
        speed: GifSpeed,
        onProgress: (Float, ExportStage) -> Unit
    ): ExportResult = withContext(Dispatchers.IO) {
        require(mediaInfo.kind == MediaKind.IMAGE && mediaInfo.isGif)
        val videoWidth = ResolutionMath.makeEven(request.target.width)
        val videoHeight = ResolutionMath.makeEven(request.target.height)
        val maxVideoDimension = ResolutionMath.MAX_VIDEO_DIMENSION
        if (videoWidth > maxVideoDimension || videoHeight > maxVideoDimension) {
            throw GifVideoProcessingException(
                "GIF video output must stay within " + maxVideoDimension + " px"
            )
        }

        val exportDirectory = File(context.cacheDir, "exports").apply { mkdirs() }
        val tempFile = File(
            exportDirectory,
            "gif_video_" + UUID.randomUUID().toString().replace("-", "") + ".mp4"
        )
        var decoder: GifDecoder? = null
        var sourceFrame: Bitmap? = null
        var encoder: H264Mp4Encoder? = null

        try {
            onProgress(0.02f, ExportStage.PREPARING)
            val inputSource = if (mediaInfo.uri.scheme == "file") {
                val path = mediaInfo.uri.path
                    ?: throw GifVideoProcessingException("GIF cache file has no path")
                InputSource.FileSource(File(path))
            } else {
                InputSource.UriSource(context.contentResolver, mediaInfo.uri)
            }
            val activeDecoder = GifDecoder(inputSource)
            decoder = activeDecoder
            if (activeDecoder.width <= 0 || activeDecoder.height <= 0 ||
                activeDecoder.numberOfFrames <= 0
            ) {
                throw GifVideoProcessingException("GIF has no decodable frames")
            }
            val activeSourceFrame = createBitmap(
                activeDecoder.width,
                activeDecoder.height,
                Bitmap.Config.ARGB_8888
            )
            sourceFrame = activeSourceFrame
            val frameCount = activeDecoder.numberOfFrames

            val delaysMs = IntArray(frameCount) { index ->
                GifTiming.scaleDelayMs(activeDecoder.getFrameDuration(index), speed.multiplier)
            }
            val totalDurationUs = delaysMs.sumOf { it.toLong() } * 1000L

            val activeEncoder = H264Mp4Encoder(tempFile, videoWidth, videoHeight)
            encoder = activeEncoder
            var presentationTimeUs = 0L
            for (index in 0 until frameCount) {
                coroutineContext.ensureActive()
                activeDecoder.seekToFrame(index, activeSourceFrame)
                val frame = renderFrame(
                    source = activeSourceFrame,
                    targetWidth = videoWidth,
                    targetHeight = videoHeight,
                    mode = request.mode
                )
                try {
                    activeEncoder.encodeFrame(frame, presentationTimeUs)
                } finally {
                    frame.recycle()
                }
                presentationTimeUs += delaysMs[index] * 1000L
                onProgress(
                    0.05f + ((index + 1).toFloat() / frameCount) * 0.82f,
                    ExportStage.ENCODING
                )
            }

            // A sample at the end timestamp makes the last real frame's delay
            // survive as a proper MP4 sample duration.
            coroutineContext.ensureActive()
            activeDecoder.seekToFrame(frameCount - 1, activeSourceFrame)
            val endFrame = renderFrame(
                source = activeSourceFrame,
                targetWidth = videoWidth,
                targetHeight = videoHeight,
                mode = request.mode
            )
            try {
                activeEncoder.encodeFrame(endFrame, totalDurationUs)
            } finally {
                endFrame.recycle()
            }
            activeEncoder.signalEndOfStream(totalDurationUs + 1L)
            activeEncoder.finish()
            encoder = null

            onProgress(0.9f, ExportStage.SAVING)
            val result = saver.saveVideo(
                tempFile = tempFile,
                sourceName = mediaInfo.displayName,
                width = videoWidth,
                height = videoHeight
            ) { copyProgress ->
                onProgress(0.9f + copyProgress * 0.09f, ExportStage.SAVING)
            }
            onProgress(1f, ExportStage.SAVING)
            result
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: OutOfMemoryError) {
            throw error
        } catch (error: GifVideoProcessingException) {
            throw error
        } catch (error: Throwable) {
            throw GifVideoProcessingException("Unable to convert GIF to MP4", error)
        } finally {
            runCatching { encoder?.close() }
            sourceFrame?.recycle()
            runCatching { decoder?.recycle() }
            runCatching { tempFile.delete() }
        }
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
}

private class H264Mp4Encoder(
    outputFile: File,
    private val width: Int,
    private val height: Int
) : Closeable {
    private val codec: MediaCodec
    private val muxer: MediaMuxer
    private val bufferInfo = MediaCodec.BufferInfo()
    private var trackIndex = -1
    private var muxerStarted = false
    private var outputEos = false
    private var closed = false

    init {
        if (width <= 0 || height <= 0 || width % 2 != 0 || height % 2 != 0) {
            throw GifVideoProcessingException("Invalid video dimensions")
        }
        val format = MediaFormat.createVideoFormat(
            MediaFormat.MIMETYPE_VIDEO_AVC,
            width,
            height
        ).apply {
            setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
            )
            setInteger(MediaFormat.KEY_BIT_RATE, estimatedBitrate(width, height))
            setInteger(MediaFormat.KEY_FRAME_RATE, 30)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, width * height * 3 / 2)
        }
        val encoderName = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            .findEncoderForFormat(format)
            ?: throw GifVideoProcessingException("No compatible H.264 encoder was found")

        val createdCodec = MediaCodec.createByCodecName(encoderName)
        val createdMuxer = MediaMuxer(
            outputFile.absolutePath,
            MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
        )
        try {
            createdCodec.configure(
                format,
                null,
                null,
                MediaCodec.CONFIGURE_FLAG_ENCODE
            )
            createdCodec.start()
        } catch (error: Throwable) {
            runCatching { createdCodec.release() }
            runCatching { createdMuxer.release() }
            throw GifVideoProcessingException("Unable to start H.264 encoder", error)
        }
        codec = createdCodec
        muxer = createdMuxer
    }

    fun encodeFrame(bitmap: Bitmap, presentationTimeUs: Long) {
        check(!closed) { "Encoder is closed" }
        val inputIndex = waitForInputBuffer()
        val inputCapacity = codec.getInputBuffer(inputIndex)?.capacity()
            ?.takeIf { it > 0 }
            ?: (width * height * 3 / 2)
        val image = codec.getInputImage(inputIndex)
            ?: throw GifVideoProcessingException("Encoder input image is unavailable")
        fillYuv(image, bitmap)
        codec.queueInputBuffer(inputIndex, 0, inputCapacity, presentationTimeUs, 0)
        drainAvailable()
    }

    fun signalEndOfStream(presentationTimeUs: Long) {
        if (closed || outputEos) return
        var attempts = 0
        while (true) {
            val inputIndex = codec.dequeueInputBuffer(INPUT_TIMEOUT_US)
            if (inputIndex >= 0) {
                codec.queueInputBuffer(
                    inputIndex,
                    0,
                    0,
                    presentationTimeUs,
                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                )
                drainAvailable()
                return
            }
            if (inputIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                drainAvailable()
                attempts++
                if (attempts > MAX_INPUT_WAIT_ATTEMPTS) {
                    throw GifVideoProcessingException("Video encoder stopped responding")
                }
            } else {
                throw GifVideoProcessingException("Unexpected encoder input state")
            }
        }
    }

    fun finish() {
        if (closed) return
        try {
            val deadline = System.nanoTime() + FINISH_TIMEOUT_NS
            while (!outputEos) {
                if (System.nanoTime() > deadline) {
                    throw GifVideoProcessingException("Timed out while finishing MP4")
                }
                val outputIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000L)
                when {
                    outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    else -> handleOutputBuffer(outputIndex)
                }
            }
        } finally {
            close()
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        if (muxerStarted) {
            runCatching { muxer.stop() }
        }
        runCatching { muxer.release() }
        runCatching { codec.stop() }
        runCatching { codec.release() }
    }

    private fun waitForInputBuffer(): Int {
        var attempts = 0
        while (true) {
            val index = codec.dequeueInputBuffer(INPUT_TIMEOUT_US)
            if (index >= 0) return index
            if (index == MediaCodec.INFO_TRY_AGAIN_LATER) {
                drainAvailable()
                attempts++
                if (attempts > MAX_INPUT_WAIT_ATTEMPTS) {
                    throw GifVideoProcessingException("Video encoder input timed out")
                }
            } else {
                throw GifVideoProcessingException("Unexpected encoder input state")
            }
        }
    }

    private fun drainAvailable() {
        while (true) {
            val outputIndex = codec.dequeueOutputBuffer(bufferInfo, 0L)
            if (outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER) return
            handleOutputBuffer(outputIndex)
            if (outputEos) return
        }
    }

    private fun handleOutputBuffer(outputIndex: Int) {
        when {
            outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                if (muxerStarted) {
                    throw GifVideoProcessingException("Encoder format changed twice")
                }
                trackIndex = muxer.addTrack(codec.outputFormat)
                muxer.start()
                muxerStarted = true
            }

            outputIndex >= 0 -> {
                val outputBuffer = codec.getOutputBuffer(outputIndex)
                val info = bufferInfo
                val isCodecConfig = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                if (info.size > 0 && !isCodecConfig) {
                    if (!muxerStarted) {
                        throw GifVideoProcessingException("Encoder produced data before format")
                    }
                    if (outputBuffer == null) {
                        throw GifVideoProcessingException("Encoder output buffer is unavailable")
                    }
                    outputBuffer.position(info.offset)
                    outputBuffer.limit(info.offset + info.size)
                    muxer.writeSampleData(trackIndex, outputBuffer, info)
                }
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    outputEos = true
                }
                codec.releaseOutputBuffer(outputIndex, false)
            }

            else -> throw GifVideoProcessingException("Unexpected encoder output state")
        }
    }

    private fun fillYuv(image: Image, bitmap: Bitmap) {
        if (image.format != ImageFormat.YUV_420_888) {
            throw GifVideoProcessingException("Unsupported encoder image format")
        }
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]
        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer

        var pixelIndex = 0
        for (row in 0 until height) {
            val yRow = row * yPlane.rowStride
            for (col in 0 until width) {
                val color = pixels[pixelIndex++]
                val red = (color shr 16) and 0xFF
                val green = (color shr 8) and 0xFF
                val blue = color and 0xFF
                val y = ((66 * red + 129 * green + 25 * blue + 128) shr 8) + 16
                yBuffer.put(yRow + col * yPlane.pixelStride, y.toByte())

                if ((row and 1) == 0 && (col and 1) == 0) {
                    val chromaRow = row / 2
                    val chromaCol = col / 2
                    val u = ((-38 * red - 74 * green + 112 * blue + 128) shr 8) + 128
                    val v = ((112 * red - 94 * green - 18 * blue + 128) shr 8) + 128
                    uBuffer.put(
                        chromaRow * uPlane.rowStride + chromaCol * uPlane.pixelStride,
                        u.toByte()
                    )
                    vBuffer.put(
                        chromaRow * vPlane.rowStride + chromaCol * vPlane.pixelStride,
                        v.toByte()
                    )
                }
            }
        }
    }

    private companion object {
        const val INPUT_TIMEOUT_US = 10_000L
        const val MAX_INPUT_WAIT_ATTEMPTS = 500
        const val FINISH_TIMEOUT_NS = 30_000_000_000L

        fun estimatedBitrate(width: Int, height: Int): Int {
            val pixels = width.toLong() * height
            return (pixels * 3L).coerceIn(1_000_000L, 12_000_000L).toInt()
        }
    }
}
