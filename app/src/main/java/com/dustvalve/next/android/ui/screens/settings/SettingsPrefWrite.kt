package com.dustvalve.next.android.ui.screens.settings

import com.dustvalve.next.android.util.runCatchingUiIgnore

/**
 * Shared Settings preference-write catch. Delegates to [runCatchingUiIgnore]
 * so TooGenericExceptionCaught stays confined to UiResult.kt.
 */
internal object SettingsPrefWrite {
    suspend fun run(block: suspend () -> Unit) {
        runCatchingUiIgnore(block = block)
    }
}

internal suspend fun runSettingsPrefWrite(block: suspend () -> Unit) = SettingsPrefWrite.run(block)
