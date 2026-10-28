package com.resizelab.app.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import kotlin.math.max

/**
 * ImageDecoder fallback for images that BitmapFactory cannot fully decode
 * (for example some HEIC/AVIF/10-bit variants). ImageDecoder applies EXIF
 * orientation on Android 10+; callers must not rotate its result again.
 */
@RequiresApi(Build.VERSION_CODES.Q)
internal object ModernImageDecoder {
    private const val TAG = "ModernImageDecoder"

    fun decode(context: Context, uri: Uri, targetLongEdge: Int): Bitmap? {
        val source = try {
            if (uri.scheme == "file") {
                val path = uri.path ?: return null
                ImageDecoder.createSource(java.io.File(path))
            } else {
                ImageDecoder.createSource(context.contentResolver, uri)
            }
        } catch (error: Throwable) {
            Log.w(TAG, "Unable to create ImageDecoder source", error)
            return null
        }

        return try {
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val sourceLongEdge = max(info.size.width, info.size.height)
                val desiredLongEdge = targetLongEdge.coerceAtLeast(1)
                var sample = 1
                while (sample < 64 && sourceLongEdge / (sample * 2) >= desiredLongEdge) {
                    sample *= 2
                }
                decoder.setTargetSampleSize(sample)
                decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE)
                decoder.setMemorySizePolicy(ImageDecoder.MEMORY_POLICY_LOW_RAM)
            }
        } catch (error: OutOfMemoryError) {
            throw error
        } catch (error: Throwable) {
            Log.w(TAG, "ImageDecoder fallback failed", error)
            null
        }
    }
}
