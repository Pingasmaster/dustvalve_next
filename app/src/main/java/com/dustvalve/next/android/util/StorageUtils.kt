package com.dustvalve.next.android.util

import java.util.Locale

object StorageUtils {

    /**
     * Formats a byte count into a human-readable string.
     * Examples: "1.2 MB", "3.4 GB", "512 B", "15.0 KB".
     */
    fun formatFileSize(bytes: Long): String {
        val absBytes = if (bytes < 0) 0L else bytes
        return when {
            absBytes < 1024L -> "$absBytes B"
            absBytes < 1024L * 1024L -> {
                val kb = absBytes / 1024.0
                String.format(Locale.US, "%.1f KB", kb)
            }
            absBytes < 1024L * 1024L * 1024L -> {
                val mb = absBytes / (1024.0 * 1024.0)
                String.format(Locale.US, "%.1f MB", mb)
            }
            else -> {
                val gb = absBytes / (1024.0 * 1024.0 * 1024.0)
                String.format(Locale.US, "%.1f GB", gb)
            }
        }
    }

    /**
     * Converts bytes to megabytes.
     */
    fun bytesToMB(bytes: Long): Float {
        return bytes / (1024f * 1024f)
    }

    /**
     * Converts megabytes to bytes.
     */
    fun mbToBytes(mb: Float): Long {
        return (mb * 1024f * 1024f).toLong()
    }

    /**
     * Converts gigabytes to bytes.
     */
    fun gbToBytes(gb: Float): Long {
        return (gb * 1024f * 1024f * 1024f).toLong()
    }
}
