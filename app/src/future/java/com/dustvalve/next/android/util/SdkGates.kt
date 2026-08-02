package com.dustvalve.next.android.util

import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast

/**
 * Flavor-safe SDK gates for shared main sources.
 *
 * Future flavor minSdk is 37, so every gate below that is always true.
 * Keep the [ChecksSdkIntAtLeast] mirrors so NewApi still understands
 * version-gated call sites shared with the compat flavor.
 */
@ChecksSdkIntAtLeast(api = Build.VERSION_CODES.Q)
fun isAtLeastQ(): Boolean = true

@ChecksSdkIntAtLeast(api = Build.VERSION_CODES.R)
fun isAtLeastR(): Boolean = true

@ChecksSdkIntAtLeast(api = Build.VERSION_CODES.S)
fun isAtLeastS(): Boolean = true

@ChecksSdkIntAtLeast(api = Build.VERSION_CODES.TIRAMISU)
fun isAtLeastTiramisu(): Boolean = true

@ChecksSdkIntAtLeast(api = Build.VERSION_CODES.BAKLAVA)
fun isAtLeastBaklava(): Boolean = true
