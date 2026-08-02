package com.dustvalve.next.android.util

import android.Manifest

/**
 * Audio library permission for the future flavor (minSdk 37).
 *
 * Always READ_MEDIA_AUDIO so call sites shared with compat can compile.
 */
fun legacyAudioPermission(): String = Manifest.permission.READ_MEDIA_AUDIO
