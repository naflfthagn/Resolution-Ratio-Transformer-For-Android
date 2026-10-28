package com.resizelab.app

import android.app.Application
import java.io.File
import java.util.concurrent.TimeUnit

class ResizeLabApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // No editor session survives a process restart, so cached source copies
        // from a previous process can be removed immediately.
        cleanDirectory(File(cacheDir, "source_cache"), maxAgeMillis = 0L)
        cleanDirectory(
            File(cacheDir, "exports"),
            maxAgeMillis = TimeUnit.DAYS.toMillis(1)
        )
    }

    private fun cleanDirectory(directory: File, maxAgeMillis: Long) {
        if (!directory.isDirectory) return
        val cutoff = System.currentTimeMillis() - maxAgeMillis
        directory.listFiles()?.forEach { file ->
            if (!file.isFile || file.lastModified() <= cutoff) {
                runCatching { file.delete() }
            }
        }
    }
}
