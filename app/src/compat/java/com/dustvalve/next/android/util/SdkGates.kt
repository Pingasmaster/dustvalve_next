package com.dustvalve.next.android.util

import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast

/**
 * Flavor-safe SDK gates for shared main sources.
 *
 * Compat (minSdk 26) performs real [Build.VERSION.SDK_INT] checks.
 * Future (minSdk 37) always returns true so ObsoleteSdkInt stays quiet.
 * [ChecksSdkIntAtLeast] lets NewApi lint treat the true-branch as the
 * annotated API level on both flavors.
 */
@ChecksSdkIntAtLeast(api = Build.VERSION_CODES.P)
fun isAtLeastP(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

@ChecksSdkIntAtLeast(api = Build.VERSION_CODES.Q)
fun isAtLeastQ(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

@ChecksSdkIntAtLeast(api = Build.VERSION_CODES.R)
fun isAtLeastR(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

@ChecksSdkIntAtLeast(api = Build.VERSION_CODES.S)
fun isAtLeastS(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

@ChecksSdkIntAtLeast(api = Build.VERSION_CODES.TIRAMISU)
fun isAtLeastTiramisu(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

@ChecksSdkIntAtLeast(api = Build.VERSION_CODES.BAKLAVA)
fun isAtLeastBaklava(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA
