package com.resizelab.app.media

import android.content.Context
import android.content.ContentResolver
import android.net.Uri
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

/**
 * Opens a source image regardless of whether it is a content URI or a private
 * cache file URI. Keeping this in one place also makes it harder for a
 * provider-specific stream quirk to break only one of the processing stages.
 */
internal fun openImageInputStream(context: Context, uri: Uri): InputStream? {
    if (uri.scheme == ContentResolver.SCHEME_FILE) {
        val path = uri.path ?: return null
        return FileInputStream(File(path))
    }
    return context.contentResolver.openInputStream(uri)
}
