package com.dustvalve.next.android.ui.screens.settings

import com.dustvalve.next.android.data.local.datastore.SettingsDataStore
import com.dustvalve.next.android.player.BluetoothStabilityMode
import kotlinx.coroutines.CoroutineScope

/** Bluetooth stability preference writes for Settings. */
internal class SettingsBluetoothStabilityPrefsCoordinator(
    private val scope: CoroutineScope,
    private val settingsDataStore: SettingsDataStore,
) {
    fun enableMode(mode: BluetoothStabilityMode) {
        if (!mode.isEnabled) {
            disableMode()
            return
        }
        scope.launchSettingsPref {
            // Buffer-friendly defaults when turning the feature on; quality
            // tradeoffs stay off until the user opts in under Extreme.
            settingsDataStore.setBluetoothPcmBufferMs(BluetoothStabilityMode.DEFAULT_ACTIVE_PCM_BUFFER_MS)
            settingsDataStore.setBluetoothExoBufferBoost(true)
            settingsDataStore.setBluetoothPauseDownloadsWhilePlaying(true)
            settingsDataStore.setBluetoothDisableFloatOutput(false)
            settingsDataStore.setBluetoothStabilityMode(mode.toStorage())
        }
    }

    fun disableMode() = scope.launchSettingsPref {
        settingsDataStore.setBluetoothStabilityMode(BluetoothStabilityMode.OFF.toStorage())
        settingsDataStore.setBluetoothDisableFloatOutput(false)
    }

    fun setProfile(mode: BluetoothStabilityMode) {
        if (!mode.isEnabled) {
            disableMode()
            return
        }
        scope.launchSettingsPref {
            settingsDataStore.setBluetoothStabilityMode(mode.toStorage())
            if (!mode.isExtreme) {
                settingsDataStore.setBluetoothDisableFloatOutput(false)
            }
        }
    }

    fun setPcmBufferMs(ms: Int) = scope.launchSettingsPref {
        settingsDataStore.setBluetoothPcmBufferMs(ms)
    }

    fun setExoBufferBoost(enabled: Boolean) = scope.launchSettingsPref {
        settingsDataStore.setBluetoothExoBufferBoost(enabled)
    }

    fun setPauseDownloadsWhilePlaying(enabled: Boolean) = scope.launchSettingsPref {
        settingsDataStore.setBluetoothPauseDownloadsWhilePlaying(enabled)
    }

    fun setDisableFloatOutput(enabled: Boolean) = scope.launchSettingsPref {
        settingsDataStore.setBluetoothDisableFloatOutput(enabled)
    }
}
