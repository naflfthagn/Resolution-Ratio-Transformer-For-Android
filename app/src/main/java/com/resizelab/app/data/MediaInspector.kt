package com.resizelab.app.data

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.io.IOException
import java.util.Locale

enum class MediaInspectionFailure {
    READ,
    DECODE,
    UNSUPPORTED
}

class MediaInspectionException(
    val failure: MediaInspectionFailure,
    cause: Throwable? = null
) : Exception(failure.name, cause)

class MediaInspector(private val context: Context) {

    suspend fun inspect(uri: Uri): MediaInfo = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val name = queryDisplayName(uri) ?: fallbackDisplayName(uri)
        val size = querySize(uri)

        // Metadata queries are best-effort. Some OEM content providers expose a
        // readable stream but reject OpenableColumns / getType queries.
        val providerMime = runCatching {
            resolver.getType(uri)?.lowercase(Locale.ROOT)
        }.getOrNull()
        val mime = resolveMimeType(providerMime, name)
        var kind = kindFromMimeOrName(mime, name)
        if (kind == null) {
            kind = sniffKind(uri)
        }
        if (kind == null) {
            throw MediaInspectionException(MediaInspectionFailure.UNSUPPORTED)
        }

        val isGif = kind == MediaKind.IMAGE &&
            (mime == "image/gif" || name.endsWith(".gif", ignoreCase = true))

        when (kind) {
            MediaKind.IMAGE -> inspectImage(uri, name, mime, size, isGif)
            MediaKind.VIDEO -> inspectVideo(uri, name, mime, size)
        }
    }

    private fun inspectImage(
        uri: Uri,
        name: String,
        mime: String?,
        size: Long,
        isGif: Boolean
    ): MediaInfo {
        val bounds = readBitmapBounds(uri)
        val orientation = readOrientation(uri)
        val swapped = orientation == ExifInterface.ORIENTATION_TRANSPOSE ||
            orientation == ExifInterface.ORIENTATION_ROTATE_90 ||
            orientation == ExifInterface.ORIENTATION_TRANSVERSE ||
            orientation == ExifInterface.ORIENTATION_ROTATE_270
        val width = if (swapped) bounds.outHeight else bounds.outWidth
        val height = if (swapped) bounds.outWidth else bounds.outHeight

        return MediaInfo(
            uri = uri,
            kind = MediaKind.IMAGE,
            displayName = name,
            mimeType = mime ?: mimeFromName(name) ?: "image/*",
            width = width,
            height = height,
            sizeBytes = size,
            exifOrientation = orientation,
            isGif = isGif
        )
    }

    private fun inspectVideo(uri: Uri, name: String, mime: String?, size: Long): MediaInfo {
        val retriever = MediaMetadataRetriever()
        try {
            try {
                retriever.setDataSource(context, uri)
            } catch (error: SecurityException) {
                throw MediaInspectionException(MediaInspectionFailure.READ, error)
            } catch (error: FileNotFoundException) {
                throw MediaInspectionException(MediaInspectionFailure.READ, error)
            } catch (error: IOException) {
                throw MediaInspectionException(MediaInspectionFailure.READ, error)
            } catch (error: IllegalArgumentException) {
                throw MediaInspectionException(MediaInspectionFailure.DECODE, error)
            }

            val rawWidth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: 0
            val rawHeight = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: 0
            if (rawWidth <= 0 || rawHeight <= 0) {
                throw MediaInspectionException(MediaInspectionFailure.DECODE)
            }
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toIntOrNull() ?: 0
            val swapped = rotation == 90 || rotation == 270
            val width = if (swapped) rawHeight else rawWidth
            val height = if (swapped) rawWidth else rawHeight
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
            val hasAudio = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)
                ?.equals("yes", ignoreCase = true) == true
            return MediaInfo(
                uri = uri,
                kind = MediaKind.VIDEO,
                displayName = name,
                mimeType = mime ?: "video/*",
                width = width,
                height = height,
                sizeBytes = size,
                durationMs = duration,
                rotationDegrees = rotation,
                hasAudio = hasAudio
            )
        } finally {
            runCatching { retriever.release() }
        }
    }

    /**
     * Tries multiple provider access paths. A picker URI can be readable through
     * one route while another route returns a broken or empty stream.
     */
    private fun readBitmapBounds(uri: Uri): BitmapFactory.Options {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        var streamOpened = false
        var lastError: Throwable? = null

        try {
            val stream = context.contentResolver.openInputStream(uri)
            if (stream == null) {
                lastError = FileNotFoundException("openInputStream returned null")
            } else {
                streamOpened = true
                stream.use { BitmapFactory.decodeStream(it, null, options) }
            }
        } catch (error: Throwable) {
            lastError = error
        }
        if (options.outWidth > 0 && options.outHeight > 0) return options

        // Some providers only expose a usable file descriptor.
        try {
            val descriptor = context.contentResolver.openAssetFileDescriptor(uri, "r")
            if (descriptor != null) {
                descriptor.use {
                    streamOpened = true
                    BitmapFactory.decodeFileDescriptor(it.fileDescriptor, null, options)
                }
            } else if (lastError == null) {
                lastError = FileNotFoundException("openAssetFileDescriptor returned null")
            }
        } catch (error: Throwable) {
            if (lastError == null) lastError = error
        }
        if (options.outWidth > 0 && options.outHeight > 0) return options

        if (!streamOpened) {
            throw MediaInspectionException(MediaInspectionFailure.READ, lastError)
        }
        throw MediaInspectionException(MediaInspectionFailure.DECODE, lastError)
    }

    private fun readOrientation(uri: Uri): Int {
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    }

    private fun queryDisplayName(uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0)?.takeIf { it.isNotBlank() } else null
            }
        }.getOrNull()
    }

    private fun querySize(uri: Uri): Long {
        return runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.SIZE),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else 0L
            } ?: 0L
        }.getOrDefault(0L)
    }

    private fun fallbackDisplayName(uri: Uri): String {
        val candidate = runCatching { uri.lastPathSegment }.getOrNull()
            ?.substringAfterLast('/')
            ?.takeIf { it.isNotBlank() }
        return candidate ?: context.getString(com.resizelab.app.R.string.unnamed_media)
    }

    private fun resolveMimeType(providerMime: String?, name: String): String? {
        val namedMime = mimeFromName(name)
        if (providerMime.isNullOrBlank() ||
            providerMime == "application/octet-stream" ||
            providerMime == "binary/octet-stream"
        ) {
            return namedMime
        }
        if (providerMime == "image/*" || providerMime == "video/*") {
            return namedMime ?: providerMime
        }
        return providerMime
    }

    private fun kindFromMimeOrName(mime: String?, name: String): MediaKind? {
        return when {
            mime?.startsWith("image/") == true -> MediaKind.IMAGE
            mime?.startsWith("video/") == true -> MediaKind.VIDEO
            else -> inferKindFromName(name)
        }
    }

    private fun sniffKind(uri: Uri): MediaKind? {
        val header = ByteArray(32)
        val read = try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.read(header)
            } ?: throw MediaInspectionException(MediaInspectionFailure.READ)
        } catch (error: MediaInspectionException) {
            throw error
        } catch (error: SecurityException) {
            throw MediaInspectionException(MediaInspectionFailure.READ, error)
        } catch (error: FileNotFoundException) {
            throw MediaInspectionException(MediaInspectionFailure.READ, error)
        } catch (error: IOException) {
            throw MediaInspectionException(MediaInspectionFailure.READ, error)
        }
        if (read <= 0) {
            throw MediaInspectionException(MediaInspectionFailure.DECODE)
        }
        return sniffKindFromHeader(header, read)
    }

    private fun sniffKindFromHeader(header: ByteArray, length: Int): MediaKind? {
        if (length >= 3 && header[0] == 0xFF.toByte() &&
            header[1] == 0xD8.toByte() && header[2] == 0xFF.toByte()
        ) {
            return MediaKind.IMAGE
        }
        if (length >= 8 && header[0] == 0x89.toByte() &&
            header[1] == 0x50.toByte() && header[2] == 0x4E.toByte() &&
            header[3] == 0x47.toByte()
        ) {
            return MediaKind.IMAGE
        }
        if (length >= 6) {
            val six = String(header, 0, 6, Charsets.US_ASCII)
            if (six.startsWith("GIF8") || six.startsWith("BM")) return MediaKind.IMAGE
        }
        if (length >= 12) {
            val riff = String(header, 0, 4, Charsets.US_ASCII)
            val kind = String(header, 8, 4, Charsets.US_ASCII)
            if (riff == "RIFF" && kind == "WEBP") return MediaKind.IMAGE
            if (riff == "RIFF" && kind == "AVI ") return MediaKind.VIDEO
            if (String(header, 4, 4, Charsets.US_ASCII) == "ftyp") {
                val brand = String(header, 8, 4, Charsets.US_ASCII)
                return if (brand.startsWith("heic") || brand.startsWith("heix") ||
                    brand.startsWith("mif1") || brand.startsWith("avif")
                ) {
                    MediaKind.IMAGE
                } else {
                    MediaKind.VIDEO
                }
            }
        }
        if (length >= 4 && header[0] == 0x1A.toByte() && header[1] == 0x45.toByte() &&
            header[2] == 0xDF.toByte() && header[3] == 0xA3.toByte()
        ) {
            return MediaKind.VIDEO
        }
        return null
    }

    private fun inferKindFromName(name: String): MediaKind? {
        return when (name.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
            "jpg", "jpeg", "png", "webp", "heic", "heif", "bmp", "gif", "avif" -> MediaKind.IMAGE
            "mp4", "mov", "m4v", "mkv", "webm", "3gp", "ts", "mts", "m2ts", "avi" -> MediaKind.VIDEO
            else -> null
        }
    }

    private fun mimeFromName(name: String): String? {
        return when (name.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "heic" -> "image/heic"
            "heif" -> "image/heif"
            "bmp" -> "image/bmp"
            "gif" -> "image/gif"
            "avif" -> "image/avif"
            "mp4" -> "video/mp4"
            "mov" -> "video/quicktime"
            "m4v" -> "video/x-m4v"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "3gp" -> "video/3gpp"
            "ts" -> "video/mp2t"
            "mts", "m2ts" -> "video/mp2t"
            "avi" -> "video/x-msvideo"
            else -> null
        }
    }
}
