package com.dustvalve.next.android.ui.screens.player

import kotlin.coroutines.cancellation.CancellationException

/**
 * Shared player UI-action catch. Matches the prior ViewModel style (swallow
 * non-cancellation failures); specificity rewrite is owned elsewhere.
 */
internal object PlayerUiActionCatch {
    suspend fun run(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
        }
    }

    suspend fun <T> runOrNull(block: suspend () -> T): T? {
        return try {
            block()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }
    }

    suspend fun <T> runResult(block: suspend () -> T): Result<T> {
        return try {
            Result.success(block())
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }
}

internal suspend fun runPlayerUiAction(block: suspend () -> Unit) = PlayerUiActionCatch.run(block)

internal suspend fun <T> runPlayerUiActionOrNull(block: suspend () -> T): T? =
    PlayerUiActionCatch.runOrNull(block)

internal suspend fun <T> runPlayerUiActionResult(block: suspend () -> T): Result<T> =
    PlayerUiActionCatch.runResult(block)
