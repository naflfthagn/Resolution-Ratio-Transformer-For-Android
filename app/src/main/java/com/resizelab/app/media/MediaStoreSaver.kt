package com.resizelab.app.media

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.resizelab.app.data.ExportResult
import com.resizelab.app.data.ImageFormat
import com.resizelab.app.data.MediaKind
import com.resizelab.app.util.buildOutputName
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.OutputStream
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

class MediaStoreSaver(private val context: Context) {

    fun saveImage(
        bitmap: Bitmap,
        format: ImageFormat,
        quality: Int,
        sourceName: String,
        width: Int,
        height: Int
    ): ExportResult {
        val fileName = buildOutputName(sourceName, width, height, format.extension)
        val entry = createEntry(MediaKind.IMAGE, format.mimeType, fileName)
        try {
            val stream = context.contentResolver.openOutputStream(entry.uri, "w")
                ?: throw IOException("Unable to open MediaStore output")
            stream.use { output ->
                val compressed = bitmap.compress(compressFormat(format), quality.coerceIn(0, 100), output)
                if (!compressed) throw IOException("Bitmap compression failed")
            }
            publish(entry, format.mimeType)
            val saved = querySaved(entry)
            val savedSize = saved.second.takeIf { it > 0L }
                ?: entry.absolutePath?.let { path -> File(path).length() }
                ?: 0L
            return ExportResult(
                uri = entry.uri,
                kind = MediaKind.IMAGE,
                mimeType = format.mimeType,
                displayName = saved.first ?: fileName,
                width = width,
                height = height,
                sizeBytes = savedSize
            )
        } catch (throwable: Throwable) {
            discard(entry)
            throw throwable
        }
    }

    suspend fun saveGif(
        sourceName: String,
        width: Int,
        height: Int,
        write: suspend (OutputStream) -> Unit
    ): ExportResult {
        val fileName = buildOutputName(sourceName, width, height, "gif")
        val entry = createEntry(MediaKind.IMAGE, "image/gif", fileName)
        try {
            val output = context.contentResolver.openOutputStream(entry.uri, "w")
                ?: throw IOException("Unable to open MediaStore output")
            try {
                write(output)
            } finally {
                runCatching { output.close() }
            }
            publish(entry, "image/gif")
            val saved = querySaved(entry)
            val savedSize = saved.second.takeIf { it > 0L }
                ?: entry.absolutePath?.let { path -> File(path).length() }
                ?: 0L
            return ExportResult(
                uri = entry.uri,
                kind = MediaKind.IMAGE,
                mimeType = "image/gif",
                displayName = saved.first ?: fileName,
                width = width,
                height = height,
                sizeBytes = savedSize
            )
        } catch (throwable: Throwable) {
            discard(entry)
            throw throwable
        }
    }

    suspend fun saveVideo(
        tempFile: File,
        sourceName: String,
        width: Int,
        height: Int,
        onProgress: (Float) -> Unit
    ): ExportResult {
        if (!tempFile.isFile || tempFile.length() <= 0L) {
            throw IOException("Video output is empty")
        }
        val fileName = buildOutputName(sourceName, width, height, "mp4")
        val entry = createEntry(MediaKind.VIDEO, "video/mp4", fileName)
        try {
            val output = context.contentResolver.openOutputStream(entry.uri, "w")
                ?: throw IOException("Unable to open MediaStore output")
            val totalBytes = tempFile.length().coerceAtLeast(1L)
            var copiedBytes = 0L
            var lastPercent = -1
            FileInputStream(tempFile).use { input ->
                output.use { out: OutputStream ->
                    val buffer = ByteArray(1024 * 1024)
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        out.write(buffer, 0, read)
                        copiedBytes += read
                        val percent = (copiedBytes * 100L / totalBytes).toInt()
                        if (percent != lastPercent) {
                            lastPercent = percent
                            onProgress((copiedBytes.toFloat() / totalBytes).coerceIn(0f, 1f))
                        }
                    }
                    out.flush()
                }
            }
            publish(entry, "video/mp4")
            val saved = querySaved(entry)
            return ExportResult(
                uri = entry.uri,
                kind = MediaKind.VIDEO,
                mimeType = "video/mp4",
                displayName = saved.first ?: fileName,
                width = width,
                height = height,
                sizeBytes = saved.second.takeIf { it > 0L } ?: tempFile.length()
            )
        } catch (throwable: Throwable) {
            discard(entry)
            throw throwable
        }
    }

    private fun createEntry(kind: MediaKind, mimeType: String, fileName: String): Entry {
        val isImage = kind == MediaKind.IMAGE
        val collection = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                if (isImage) {
                    MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                }
            }
            isImage -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            else -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }
        val relativeDirectory = if (isImage) {
            Environment.DIRECTORY_PICTURES + "/ResizeLab"
        } else {
            Environment.DIRECTORY_MOVIES + "/ResizeLab"
        }
        var absolutePath: String? = null
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativeDirectory)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            } else {
                @Suppress("DEPRECATION")
                val publicDirectory = Environment.getExternalStoragePublicDirectory(
                    if (isImage) Environment.DIRECTORY_PICTURES else Environment.DIRECTORY_MOVIES
                )
                val targetDirectory = File(publicDirectory, "ResizeLab")
                if (!targetDirectory.exists() && !targetDirectory.mkdirs()) {
                    throw IOException("Unable to create output directory")
                }
                absolutePath = File(targetDirectory, fileName).absolutePath
                @Suppress("DEPRECATION")
                put(MediaStore.MediaColumns.DATA, absolutePath)
            }
        }
        val uri = context.contentResolver.insert(collection, values)
            ?: throw IOException("Unable to create MediaStore entry")
        return Entry(uri, absolutePath)
    }

    private fun publish(entry: Entry, mimeType: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }
            context.contentResolver.update(entry.uri, values, null, null)
        } else {
            val path = entry.absolutePath
            if (!path.isNullOrBlank()) {
                MediaScannerConnection.scanFile(context, arrayOf(path), arrayOf(mimeType), null)
            }
        }
    }

    private fun querySaved(entry: Entry): Pair<String?, Long> {
        return runCatching {
            context.contentResolver.query(
                entry.uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val name = cursor.getString(0)
                    val size = if (!cursor.isNull(1)) cursor.getLong(1) else 0L
                    name to size
                } else {
                    null to 0L
                }
            } ?: (null to 0L)
        }.getOrDefault(null to 0L)
    }

    private fun discard(entry: Entry) {
        runCatching { context.contentResolver.delete(entry.uri, null, null) }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            entry.absolutePath?.let { path -> runCatching { File(path).delete() } }
        }
    }

    private fun compressFormat(format: ImageFormat): Bitmap.CompressFormat {
        return when (format) {
            ImageFormat.JPEG -> Bitmap.CompressFormat.JPEG
            ImageFormat.PNG -> Bitmap.CompressFormat.PNG
            ImageFormat.WEBP -> webpFormat()
            ImageFormat.GIF -> throw IllegalArgumentException(
                "Animated GIFs are written by GifResizer"
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun webpFormat(): Bitmap.CompressFormat {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            Bitmap.CompressFormat.WEBP
        }
    }

    private data class Entry(val uri: Uri, val absolutePath: String?)
}
