package com.dustvalve.next.android.ui.screens.settings

import com.dustvalve.next.android.player.BluetoothStabilityMode

/** User actions for the Bluetooth stability settings section. */
sealed interface SettingsBluetoothStabilityAction {
    data class Enable(val mode: BluetoothStabilityMode) : SettingsBluetoothStabilityAction
    data object Disable : SettingsBluetoothStabilityAction
    data class SetProfile(val mode: BluetoothStabilityMode) : SettingsBluetoothStabilityAction
    data class SetPcmBufferMs(val ms: Int) : SettingsBluetoothStabilityAction
    data class SetExoBufferBoost(val enabled: Boolean) : SettingsBluetoothStabilityAction
    data class SetPauseDownloadsWhilePlaying(val enabled: Boolean) : SettingsBluetoothStabilityAction
    data class SetDisableFloatOutput(val enabled: Boolean) : SettingsBluetoothStabilityAction
}

internal fun handleSettingsBluetoothStabilityAction(viewModel: SettingsViewModel, action: SettingsBluetoothStabilityAction) {
    val prefs = viewModel.bluetoothStability
    when (action) {
        is SettingsBluetoothStabilityAction.Enable -> prefs.enableMode(action.mode)

        SettingsBluetoothStabilityAction.Disable -> prefs.disableMode()

        is SettingsBluetoothStabilityAction.SetProfile -> prefs.setProfile(action.mode)

        is SettingsBluetoothStabilityAction.SetPcmBufferMs -> prefs.setPcmBufferMs(action.ms)

        is SettingsBluetoothStabilityAction.SetExoBufferBoost -> prefs.setExoBufferBoost(action.enabled)

        is SettingsBluetoothStabilityAction.SetPauseDownloadsWhilePlaying ->
            prefs.setPauseDownloadsWhilePlaying(action.enabled)

        is SettingsBluetoothStabilityAction.SetDisableFloatOutput ->
            prefs.setDisableFloatOutput(action.enabled)
    }
}
