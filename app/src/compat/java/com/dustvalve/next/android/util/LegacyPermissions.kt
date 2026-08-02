package com.dustvalve.next.android.util

import android.Manifest
import android.os.Build

/**
 * Audio library permission for the current device SDK.
 *
 * Compat flavor: READ_MEDIA_AUDIO on API 33+, READ_EXTERNAL_STORAGE below.
 */
fun legacyAudioPermission(): String = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    Manifest.permission.READ_MEDIA_AUDIO
} else {
    Manifest.permission.READ_EXTERNAL_STORAGE
}
