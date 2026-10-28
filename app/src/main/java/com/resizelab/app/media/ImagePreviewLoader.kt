package com.resizelab.app.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

object ImagePreviewLoader {
    private const val TAG = "ImagePreviewLoader"

    suspend fun load(context: Context, uri: Uri, maxDimension: Int = 1600): Bitmap? =
        withContext(Dispatchers.IO) {
            try {
                var bitmap: Bitmap? = null
                var orientationApplied = false

                val bounds = readBounds(context, uri)
                if (bounds != null) {
                    var sample = 1
                    while (
                        max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxDimension
                    ) {
                        sample *= 2
                    }
                    val options = BitmapFactory.Options().apply {
                        inSampleSize = sample
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    }
                    bitmap = openImageInputStream(context, uri)?.use { stream ->
                        BitmapFactory.decodeStream(stream, null, options)
                    }
                }

                if (bitmap == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    bitmap = ModernImageDecoder.decode(context, uri, maxDimension)
                    orientationApplied = bitmap != null
                }

                val decoded = bitmap ?: return@withContext null
                if (orientationApplied) {
                    decoded
                } else {
                    val orientation = readOrientation(context, uri)
                    applyOrientation(decoded, orientation)
                }
            } catch (error: OutOfMemoryError) {
                Log.w(TAG, "Preview bitmap is too large", error)
                null
            } catch (error: Throwable) {
                Log.w(TAG, "Unable to load image preview", error)
                null
            }
        }

    private fun readBounds(context: Context, uri: Uri): BitmapFactory.Options? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        openImageInputStream(context, uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        }
        return if (bounds.outWidth > 0 && bounds.outHeight > 0) bounds else null
    }

    private fun readOrientation(context: Context, uri: Uri): Int {
        return runCatching {
            openImageInputStream(context, uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
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
        } catch (error: Throwable) {
            source.recycle()
            throw error
        }
    }
}
