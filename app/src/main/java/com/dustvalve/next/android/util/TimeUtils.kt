package com.dustvalve.next.android.util

import java.util.concurrent.TimeUnit

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

    /**
     * Formats a duration given in milliseconds as "m:ss".
     * For example, 225000L becomes "3:45".
     */
    fun formatDurationMs(ms: Long): String {
        val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
        val minutes = totalSeconds / 60
        val remainingSeconds = totalSeconds % 60
        return "$minutes:${remainingSeconds.toString().padStart(2, '0')}"
    }

    /**
     * Formats a timestamp as a human-readable relative time string.
     *
     * Returns strings like "just now", "5m ago", "2h ago", "3d ago",
     * "2w ago", "1mo ago", "1y ago".
     */
    fun formatRelativeTime(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diffMs = (now - timestamp).coerceAtLeast(0L)

        val seconds = TimeUnit.MILLISECONDS.toSeconds(diffMs)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(diffMs)
        val hours = TimeUnit.MILLISECONDS.toHours(diffMs)
        val days = TimeUnit.MILLISECONDS.toDays(diffMs)

        return when {
            seconds < 60 -> "just now"
            minutes < 60 -> "${minutes}m ago"
            hours < 24 -> "${hours}h ago"
            days < 7 -> "${days}d ago"
            days < 30 -> "${days / 7}w ago"
            days < 365 -> "${days / 30}mo ago"
            else -> "${days / 365}y ago"
        }
    }
}
