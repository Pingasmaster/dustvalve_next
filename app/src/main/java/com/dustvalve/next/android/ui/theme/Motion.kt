package com.dustvalve.next.android.ui.theme

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable

/**
 * Convenience accessors for the M3 Expressive motion scheme.
 * Components should use MaterialTheme.motionScheme directly where possible.
 */
object AppMotion {
    /** Stagger delay for sequenced list animations in milliseconds */
    const val staggerDelay: Long = 50L
}
