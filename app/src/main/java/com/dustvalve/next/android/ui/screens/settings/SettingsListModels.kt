package com.dustvalve.next.android.ui.screens.settings

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp

@Immutable
internal data class SettingsToggleExtras(
    val description: String? = null,
    val enabled: Boolean = true,
    val icon: Int? = null,
    val subRow: Boolean = false,
    val switchTag: String? = null,
)

@Immutable
internal data class SettingsListHost(val contentMaxWidth: Dp, val state: SettingsUiState, val snackbarHostState: SnackbarHostState)

@Immutable
internal data class SettingsListDialogActions(
    val onShowRemoveDownloads: () -> Unit,
    val onSourcesAction: (SettingsSourcesAction) -> Unit,
    val onStorageAction: (SettingsStorageAction) -> Unit,
    val onAppearanceAction: (SettingsAppearanceAction) -> Unit,
)

@Immutable
internal data class SettingsListToggleActions(
    val onSetProgressiveDownload: (Boolean) -> Unit,
    val onSetSeamlessQualityUpgrade: (Boolean) -> Unit,
    val onSetShowInlineVolumeSlider: (Boolean) -> Unit,
    val onSetShowVolumeButton: (Boolean) -> Unit,
    val onSetKeepScreenOnInApp: (Boolean) -> Unit,
    val onSetKeepScreenOnWhilePlaying: (Boolean) -> Unit,
    val onSetSearchHistoryEnabled: (Boolean) -> Unit,
    val onBluetoothStabilityAction: (SettingsBluetoothStabilityAction) -> Unit,
)

@Immutable
internal data class SettingsListMiscActions(
    val onSetSearchHistorySource: (String, Boolean) -> Unit,
    val onClearAllSearchHistory: () -> Unit,
    val onCheckForAppUpdate: () -> Unit,
    val onSetAutoUpdateCheckEnabled: (Boolean) -> Unit,
    val onSetPlayerDebugOverlay: (Boolean) -> Unit,
)

@Immutable
internal data class SettingsListActions(
    val dialogs: SettingsListDialogActions,
    val toggles: SettingsListToggleActions,
    val misc: SettingsListMiscActions,
)
