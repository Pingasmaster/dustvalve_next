package com.dustvalve.next.android.data.asset

import android.content.Context
import java.io.File

/**
 * Centralized filesystem paths for the unified downloads pool. All persisted
 * assets live under [downloadsDir]; ExoPlayer's `media_cache` stays in
 * `cacheDir/media_cache` because Media3's SimpleCache demands its own
 * directory and lock.
 */
object StoragePaths {

    /** Top-level downloads pool. Audio + image subdirs live here. */
    fun downloadsDir(context: Context): File =
        File(context.filesDir, "downloads").also { it.mkdirs() }

    /** Coil's persistent image disk cache. */
    fun imagesDir(context: Context): File =
        File(downloadsDir(context), "images").also { it.mkdirs() }

    /** ExoPlayer's media cache directory (managed by Media3 SimpleCache). */
    fun mediaCacheDir(context: Context): File =
        File(context.cacheDir, "media_cache")

    /** Recursive directory size in bytes. Returns 0 for missing dirs. */
    fun calculateDirSize(dir: File): Long {
        if (!dir.exists()) return 0L
        if (dir.isFile) return dir.length()
        var total = 0L
        val children = dir.listFiles() ?: return 0L
        for (child in children) {
            total += if (child.isDirectory) calculateDirSize(child) else child.length()
        }
        return total
    }
}
