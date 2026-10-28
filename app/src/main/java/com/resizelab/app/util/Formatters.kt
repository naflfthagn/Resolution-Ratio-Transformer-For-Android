package com.resizelab.app.util

import com.resizelab.app.data.MediaInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "—"
    if (bytes < 1024L) return bytes.toString() + " B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes / 1024.0
    var index = 0
    while (value >= 1024.0 && index < units.lastIndex) {
        value /= 1024.0
        index++
    }
    val decimals = if (value >= 100.0) 0 else 1
    return String.format(Locale.US, "%." + decimals + "f %s", value, units[index])
}

fun formatDuration(durationMs: Long?): String {
    if (durationMs == null || durationMs <= 0L) return "—"
    val totalSeconds = durationMs / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

fun formatMegapixels(width: Int, height: Int): String =
    String.format(Locale.US, "%.1f", width.toLong() * height / 1_000_000.0)

fun formatTypeLabel(mediaInfo: MediaInfo): String {
    val extension = mediaInfo.displayName.substringAfterLast('.', "")
    if (extension.isNotBlank() && extension.length <= 5) return extension.uppercase(Locale.ROOT)
    return mediaInfo.mimeType.substringAfterLast('/').uppercase(Locale.ROOT)
}

fun sanitizeBaseName(displayName: String): String {
    val withoutExtension = displayName.substringBeforeLast('.', displayName)
    val cleaned = buildString {
        for (ch in withoutExtension) {
            append(
                if (ch.isLetterOrDigit() || ch == '_' || ch == '-' || ch == ' ') ch else '_'
            )
        }
    }.trim().trimEnd('.', ' ').take(48)
    return cleaned.ifBlank { "media" }
}

fun buildOutputName(sourceName: String, width: Int, height: Int, extension: String): String {
    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    return sanitizeBaseName(sourceName) + "_" + width + "x" + height + "_" + stamp + "." + extension
}
