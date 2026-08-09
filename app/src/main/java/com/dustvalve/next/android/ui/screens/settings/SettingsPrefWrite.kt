package com.dustvalve.next.android.ui.screens.settings

import kotlin.coroutines.cancellation.CancellationException

/**
 * Shared Settings preference-write catch. Matches the prior ViewModel style
 * (swallow non-cancellation failures); specificity rewrite is owned elsewhere.
 */
internal object SettingsPrefWrite {
    suspend fun run(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
        }
    }
}

internal suspend fun runSettingsPrefWrite(block: suspend () -> Unit) = SettingsPrefWrite.run(block)
