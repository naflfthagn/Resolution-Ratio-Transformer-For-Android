package com.resizelab.app.media

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.coroutineContext

/**
 * Copies a picked image into the app's private cache before preview/export.
 * Some providers only expose a transient content URI; the gallery may still be
 * able to open the original while the app's own second open fails.
 */
class MediaSourceCache(private val context: Context) {
    private val cacheDirectory = File(context.cacheDir, DIRECTORY_NAME)
    private val resolver = context.contentResolver

    suspend fun copyImage(uri: Uri, displayName: String, mimeType: String): File =
        withContext(Dispatchers.IO) {
            val directory = cacheDirectory.apply { mkdirs() }
            if (!directory.isDirectory) {
                throw IOException("Unable to create image cache directory")
            }
            cleanupStaleFiles()
            val extension = extensionFor(displayName, mimeType)
            val targetFile = File(
                directory,
                "source_" + UUID.randomUUID().toString().replace("-", "") + "." + extension
            )

            var lastError: Throwable? = null
            for (attempt in 0..1) {
                targetFile.delete()
                val copied = try {
                    val success = if (attempt == 0) {
                        copyViaInputStream(uri, targetFile)
                    } else {
                        copyViaFileDescriptor(uri, targetFile)
                    }
                    success && targetFile.isFile && targetFile.length() > 0L
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    targetFile.delete()
                    throw cancelled
                } catch (error: Throwable) {
                    lastError = error
                    false
                }
                if (copied) return@withContext targetFile
            }
            targetFile.delete()
            throw IOException("Unable to cache selected image", lastError)
        }

    fun delete(file: File?) {
        if (file == null) return
        runCatching { file.delete() }
    }

    fun cleanupStaleFiles(maxAgeMillis: Long = DEFAULT_MAX_AGE_MILLIS) {
        val cutoff = System.currentTimeMillis() - maxAgeMillis
        cacheDirectory.listFiles()?.forEach { file ->
            if (!file.isFile || file.lastModified() < cutoff) {
                runCatching { file.delete() }
            }
        }
    }

    private suspend fun copyViaInputStream(uri: Uri, targetFile: File): Boolean {
        val input = resolver.openInputStream(uri) ?: return false
        input.use { source ->
            FileOutputStream(targetFile).use { output ->
                copyStream(source, output)
            }
        }
        return targetFile.length() > 0L
    }

    private suspend fun copyViaFileDescriptor(uri: Uri, targetFile: File): Boolean {
        val descriptor = resolver.openFileDescriptor(uri, "r") ?: return false
        ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { source ->
            FileOutputStream(targetFile).use { output ->
                copyStream(source, output)
            }
        }
        return targetFile.length() > 0L
    }

    private suspend fun copyStream(
        source: java.io.InputStream,
        target: java.io.OutputStream
    ) {
        val buffer = ByteArray(256 * 1024)
        while (true) {
            coroutineContext.ensureActive()
            val read = source.read(buffer)
            if (read < 0) break
            target.write(buffer, 0, read)
        }
        target.flush()
    }

    private fun extensionFor(displayName: String, mimeType: String): String {
        val fromName = displayName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        if (fromName.matches(Regex("[a-z0-9]{2,5}"))) return fromName
        return when (mimeType.lowercase(Locale.ROOT)) {
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/heic" -> "heic"
            "image/heif" -> "heif"
            "image/gif" -> "gif"
            "image/bmp" -> "bmp"
            "image/avif" -> "avif"
            else -> "img"
        }
    }

    private companion object {
        const val DIRECTORY_NAME = "source_cache"
        const val DEFAULT_MAX_AGE_MILLIS = 24L * 60L * 60L * 1000L
    }
}
