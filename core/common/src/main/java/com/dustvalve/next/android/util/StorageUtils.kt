package com.dustvalve.next.android.util

import android.content.Context
import android.text.format.Formatter

object StorageUtils {

    /**
     * Formats a byte count into a human-readable, locale-aware string via the
     * platform [Formatter], matching the system Settings storage UI (SI units,
     * localized unit names and digits). Examples: "1.2 MB", "3.4 GB", "512 B".
     */
    fun formatFileSize(context: Context, bytes: Long): String = Formatter.formatFileSize(context, bytes.coerceAtLeast(0L))
}
