package com.dustvalve.next.android.ui.screens.player

import androidx.annotation.StringRes
import com.dustvalve.next.android.util.UiResult
import com.dustvalve.next.android.util.onFailure
import com.dustvalve.next.android.util.runCatchingUi
import com.dustvalve.next.android.util.runCatchingUiIgnore
import com.dustvalve.next.android.util.runCatchingUiOrNull

/**
 * Shared player UI-action catch. Delegates to [runCatchingUi] /
 * [runCatchingUiIgnore] / [runCatchingUiOrNull] so TooGenericExceptionCaught
 * stays confined to UiResult.kt.
 */
internal object PlayerUiActionCatch {
    suspend fun run(block: suspend () -> Unit) {
        runCatchingUiIgnore(block = block)
    }

    suspend fun <T> runOrNull(block: suspend () -> T): T? = runCatchingUiOrNull(block)

    suspend fun <T> runResult(
        @StringRes fallback: Int,
        block: suspend () -> T,
    ): UiResult<T> = runCatchingUi(fallback, block)
}

internal suspend fun runPlayerUiAction(block: suspend () -> Unit) = PlayerUiActionCatch.run(block)

internal suspend fun <T> runPlayerUiActionOrNull(block: suspend () -> T): T? =
    PlayerUiActionCatch.runOrNull(block)

internal suspend fun <T> runPlayerUiActionResult(
    @StringRes fallback: Int,
    block: suspend () -> T,
): UiResult<T> = PlayerUiActionCatch.runResult(fallback, block)
