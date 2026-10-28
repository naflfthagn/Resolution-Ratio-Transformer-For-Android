package com.resizelab.app.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import androidx.core.graphics.createBitmap
import androidx.exifinterface.media.ExifInterface
import com.resizelab.app.data.ExportResult
import com.resizelab.app.data.ExportStage
import com.resizelab.app.data.ImageFormat
import com.resizelab.app.data.MediaInfo
import com.resizelab.app.data.MediaKind
import com.resizelab.app.data.ResizeMode
import com.resizelab.app.util.PixelSize
import com.resizelab.app.util.ResolutionMath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.coroutines.coroutineContext
import kotlin.math.max

data class ImageResizeRequest(
    val target: PixelSize,
    val mode: ResizeMode,
    val format: ImageFormat,
    val quality: Int
)

class ImageDecodeException(message: String, cause: Throwable? = null) : IOException(message, cause)

class ImageResizer(
    private val context: Context,
    private val saver: MediaStoreSaver
) {
    suspend fun resize(
        mediaInfo: MediaInfo,
        request: ImageResizeRequest,
        onProgress: (Float, ExportStage) -> Unit
    ): ExportResult = withContext(Dispatchers.IO) {
        require(mediaInfo.kind == MediaKind.IMAGE)
        var renderSource: Bitmap? = null
        var output: Bitmap? = null
        try {
            onProgress(0.05f, ExportStage.DECODING)
            val decoded = decodeSampled(mediaInfo.uri, request.target)
            val raw = decoded.bitmap
            renderSource = if (decoded.orientationApplied) {
                raw
            } else {
                val rotated = try {
                    applyOrientation(raw, mediaInfo.exifOrientation)
                } catch (error: Throwable) {
                    raw.recycle()
                    throw error
                }
                if (rotated !== raw) raw.recycle()
                rotated
            }

            onProgress(0.38f, ExportStage.DRAWING)
            coroutineContext.ensureActive()

            output = render(renderSource, request.target, request.mode, request.format)
            coroutineContext.ensureActive()

            onProgress(0.76f, ExportStage.SAVING)
            val result = saver.saveImage(
                bitmap = output,
                format = request.format,
                quality = request.quality,
                sourceName = mediaInfo.displayName,
                width = request.target.width,
                height = request.target.height
            )
            onProgress(1f, ExportStage.SAVING)
            result
        } finally {
            renderSource?.recycle()
            output?.recycle()
        }
    }

    private fun readBounds(uri: Uri): BitmapFactory.Options? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        openImageInputStream(context, uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        } ?: return null
        if (options.outWidth <= 0 || options.outHeight <= 0) return null
        return options
    }

    private fun decodeSampled(uri: Uri, target: PixelSize): DecodedBitmap {
        var bitmapFactoryError: Throwable? = null
        try {
            val bounds = readBounds(uri)
                ?: throw ImageDecodeException("Unable to read image bounds")
            var sample = 1
            while (sample < 32) {
                val next = sample * 2
                val nextWidth = bounds.outWidth / next
                val nextHeight = bounds.outHeight / next
                if (nextWidth < target.width || nextHeight < target.height) break
                sample = next
            }
            while (
                (bounds.outWidth.toLong() / sample) * (bounds.outHeight.toLong() / sample) >
                24_000_000L
            ) {
                sample *= 2
            }
            val options = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bitmap = openImageInputStream(context, uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            } ?: throw ImageDecodeException("Image stream unavailable")
            return DecodedBitmap(bitmap = bitmap, orientationApplied = false)
        } catch (error: OutOfMemoryError) {
            throw error
        } catch (error: Throwable) {
            bitmapFactoryError = error
        }

        // BitmapFactory can fail on valid gallery images (for example some
        // HEIC/AVIF/10-bit variants). ImageDecoder is a stronger fallback on
        // Android 10+ and applies EXIF orientation itself.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                ModernImageDecoder.decode(
                    context = context,
                    uri = uri,
                    targetLongEdge = max(target.width, target.height)
                )?.let { bitmap ->
                    return DecodedBitmap(bitmap = bitmap, orientationApplied = true)
                }
            } catch (error: OutOfMemoryError) {
                throw error
            } catch (error: Throwable) {
                bitmapFactoryError?.addSuppressed(error) ?: run { bitmapFactoryError = error }
            }
        }
        throw ImageDecodeException("Unable to decode image", bitmapFactoryError)
    }

    private fun applyOrientation(source: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
                matrix.setRotate(180f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate(-90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
            else -> return source
        }
        return try {
            Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        } catch (error: OutOfMemoryError) {
            throw error
        } catch (error: Throwable) {
            throw ImageDecodeException("Unable to apply image orientation", error)
        }
    }

    private fun render(
        source: Bitmap,
        target: PixelSize,
        mode: ResizeMode,
        format: ImageFormat
    ): Bitmap {
        val output = try {
            createBitmap(target.width, target.height, Bitmap.Config.ARGB_8888)
        } catch (error: OutOfMemoryError) {
            throw error
        } catch (error: Throwable) {
            throw ImageDecodeException("Unable to allocate output bitmap", error)
        }
        val canvas = Canvas(output)
        if (format != ImageFormat.PNG) {
            canvas.drawColor(Color.BLACK)
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        if (mode == ResizeMode.STRETCH) {
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

    private data class DecodedBitmap(
        val bitmap: Bitmap,
        val orientationApplied: Boolean
    )
}
