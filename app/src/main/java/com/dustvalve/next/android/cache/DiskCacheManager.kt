package com.dustvalve.next.android.cache

import android.content.Context
import com.dustvalve.next.android.util.NetworkUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DiskCacheManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    companion object {
        private const val CACHE_BASE = "cache"
        private const val AUDIO_DIR = "cache/audio"
        private const val IMAGE_ALBUM_DIR = "cache/images/album"
        private const val IMAGE_ARTIST_DIR = "cache/images/artist"
        private const val DOWNLOADS_DIR = "downloads"
    }

    private fun ensureDir(path: String): File {
        val dir = File(context.cacheDir, path)
        if (!dir.exists()) {
            if (!dir.mkdirs() && !dir.exists()) {
                throw IOException("Failed to create directory: ${dir.absolutePath}")
            }
        }
        return dir
    }

    fun writeAudioCache(key: String, data: ByteArray, extension: String = "mp3"): File {
        val safeKey = NetworkUtils.sanitizeFileName(key)
        val dir = ensureDir(AUDIO_DIR)
        val file = File(dir, "$safeKey.$extension")
        val tempFile = File(dir, "$safeKey.$extension.${System.nanoTime()}.tmp")
        try {
            tempFile.writeBytes(data)
            if (!tempFile.renameTo(file)) {
                tempFile.copyTo(file, overwrite = true)
                tempFile.delete()
            }
        } catch (e: IOException) {
            tempFile.delete()
            throw e
        }
        return file
    }

    fun writeImageCache(key: String, data: ByteArray): File {
        val safeKey = NetworkUtils.sanitizeFileName(key)
        val dir = ensureDir(IMAGE_ALBUM_DIR)
        val file = File(dir, "$safeKey.webp")
        val tempFile = File(dir, "$safeKey.webp.${System.nanoTime()}.tmp")
        try {
            tempFile.writeBytes(data)
            if (!tempFile.renameTo(file)) {
                tempFile.copyTo(file, overwrite = true)
                tempFile.delete()
            }
        } catch (e: IOException) {
            tempFile.delete()
            throw e
        }
        return file
    }

    fun readFile(path: String): File? {
        val file = File(path)
        if (!validateFilePath(file)) return null
        return if (file.exists() && file.isFile) file else null
    }

    fun deleteFile(path: String): Boolean {
        val file = File(path)
        if (!validateFilePath(file)) return false
        return if (file.exists()) file.delete() else true  // File already gone = success
    }

    fun getAudioCacheFile(trackId: String, extension: String = "mp3"): File {
        val safeKey = NetworkUtils.sanitizeFileName(trackId)
        val dir = ensureDir(AUDIO_DIR)
        return File(dir, "$safeKey.$extension")
    }

    fun getImageCacheFile(key: String): File {
        val safeKey = NetworkUtils.sanitizeFileName(key)
        val dir = ensureDir(IMAGE_ALBUM_DIR)
        return File(dir, "$safeKey.webp")
    }

    fun getCacheDir(): File {
        return ensureDir(CACHE_BASE)
    }

    fun getDownloadDir(): File {
        val dir = File(context.filesDir, DOWNLOADS_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getMediaCacheDir(): File = File(context.cacheDir, "media_cache")

    fun clearCacheDir() {
        val cacheBase = File(context.cacheDir, CACHE_BASE)
        if (cacheBase.exists()) {
            cacheBase.deleteRecursively()
        }
        // Do NOT delete ExoPlayer's media_cache directory here — it is managed by
        // SimpleCache which holds a database lock on it. Deleting it out from under
        // SimpleCache corrupts its internal database. Use SimpleCache.removeResource()
        // via CacheManager instead.

        // Recreate the directory structure so it's ready for future use
        ensureDir(AUDIO_DIR)
        ensureDir(IMAGE_ALBUM_DIR)
        ensureDir(IMAGE_ARTIST_DIR)
    }

    fun calculateDirSize(dir: File): Long {
        if (!dir.exists()) return 0L
        if (dir.isFile) return dir.length()
        var totalSize = 0L
        val files = dir.listFiles() ?: return 0L
        for (file in files) {
            totalSize += if (file.isDirectory) {
                calculateDirSize(file)
            } else {
                file.length()
            }
        }
        return totalSize
    }

    /**
     * Validates that a file path is within the expected base directories (cacheDir or filesDir)
     * to prevent path traversal attacks.
     */
    private fun validateFilePath(file: File): Boolean {
        val canonical = file.canonicalPath
        return canonical.startsWith(context.cacheDir.canonicalPath) ||
            canonical.startsWith(context.filesDir.canonicalPath)
    }
}
