package com.dustvalve.next.android.util

import java.text.NumberFormat

object TimeUtils {

    /**
     * Formats a duration given in seconds as "m:ss".
     * For example, 225.0f becomes "3:45".
     *
     * Digits are rendered with the default locale's number system (e.g.
     * Arabic-Indic digits under an ar locale); the ":" separator is the
     * script-neutral timer convention.
     */
    fun formatDuration(seconds: Float): String {
        val totalSeconds = seconds.toLong().coerceAtLeast(0L)
        val minutes = NumberFormat.getIntegerInstance().apply { isGroupingUsed = false }
        val paddedSeconds = NumberFormat.getIntegerInstance().apply { minimumIntegerDigits = 2 }
        return "${minutes.format(totalSeconds / 60)}:${paddedSeconds.format(totalSeconds % 60)}"
    }
}
