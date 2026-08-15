package com.dustvalve.next.android.data.asset

import android.content.Context
import java.io.File

/**
 * Centralized filesystem paths for persisted assets. Audio, Coil images, and
 * ExoPlayer's SimpleCache all live under [filesDir] so the OS cannot reclaim
 * them as it can with [Context.getCacheDir]. SimpleCache still gets its own
 * directory and lock, just not inside `cacheDir`.
 */
object StoragePaths {

    /** Top-level downloads pool. Audio + Coil image subdirs live here. */
    fun downloadsDir(context: Context): File = File(context.filesDir, "downloads").also { it.mkdirs() }

    /** Coil's persistent image disk cache. Never LRU-evicted by the app. */
    fun imagesDir(context: Context): File = File(downloadsDir(context), "images").also { it.mkdirs() }

    /**
     * Durable covers that are not Coil journal entries (playlist-import art).
     * Kept outside [imagesDir] so Coil cannot treat them as orphans.
     */
    fun coversDir(context: Context): File = File(context.filesDir, "covers").also { it.mkdirs() }

    /** ExoPlayer's media cache directory (managed by Media3 SimpleCache). */
    fun mediaCacheDir(context: Context): File = File(context.filesDir, "media_cache")

    /** Legacy SimpleCache location; deleted on first launch after the move. */
    fun legacyMediaCacheDir(context: Context): File = File(context.cacheDir, "media_cache")

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
