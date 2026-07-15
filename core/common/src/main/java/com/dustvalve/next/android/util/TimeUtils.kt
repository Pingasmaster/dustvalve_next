package com.dustvalve.next.android.util

object TimeUtils {

    /**
     * Formats a duration given in seconds as "m:ss".
     * For example, 225.0f becomes "3:45".
     */
    fun formatDuration(seconds: Float): String {
        val totalSeconds = seconds.toLong().coerceAtLeast(0L)
        val minutes = totalSeconds / 60
        val remainingSeconds = totalSeconds % 60
        return "$minutes:${remainingSeconds.toString().padStart(2, '0')}"
    }
}
