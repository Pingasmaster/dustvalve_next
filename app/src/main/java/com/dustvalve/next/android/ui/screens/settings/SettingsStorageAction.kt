package com.dustvalve.next.android.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dustvalve.next.android.R
import com.dustvalve.next.android.ui.components.StorageIndicator
import kotlin.math.roundToInt

/** Actions emitted by [SettingsStorageSection]. */
internal sealed interface SettingsStorageAction {
    data class SetStorageLimit(val limitGb: Float) : SettingsStorageAction
    data class SetAutoDownloadFutureContent(val enabled: Boolean) : SettingsStorageAction
    data class SetAutoDownloadFavorites(val enabled: Boolean) : SettingsStorageAction
    data class SetDownloadNotificationsEnabled(val enabled: Boolean) : SettingsStorageAction
}

internal fun handleSettingsStorageAction(viewModel: SettingsViewModel, action: SettingsStorageAction) {
    val storage = viewModel.storageSources
    when (action) {
        is SettingsStorageAction.SetStorageLimit ->
            storage.setStorageLimit(action.limitGb)

        is SettingsStorageAction.SetAutoDownloadFutureContent ->
            storage.setAutoDownloadFutureContent(action.enabled)

        is SettingsStorageAction.SetAutoDownloadFavorites ->
            storage.setAutoDownloadFavorites(action.enabled)

        is SettingsStorageAction.SetDownloadNotificationsEnabled ->
            storage.setDownloadNotificationsEnabled(action.enabled)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SettingsStorageSection(
    state: SettingsUiState,
    onRemoveAllDownloadsClick: () -> Unit,
    onAction: (SettingsStorageAction) -> Unit,
) {
    SettingsSection(
        title = stringResource(R.string.settings_section_storage),
        icon = R.drawable.ic_storage,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                state.cacheInfo?.let { cacheInfo ->
                    StorageIndicator(cacheInfo = cacheInfo)
                    Spacer(modifier = Modifier.height(16.dp))
                }

                StorageLimitSlider(
                    storageLimitIndex = state.storageLimitIndex,
                    onAction = onAction,
                )

                Spacer(modifier = Modifier.height(16.dp))
                FilledTonalButton(
                    onClick = onRemoveAllDownloadsClick,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_delete),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_remove_all_downloads))
                }

                StorageAutoDownloadControls(state = state, onAction = onAction)

                // Live Updates (status-bar chip) is a per-app runtime
                // permission the user must grant; prompt + deep-link when off.
                LiveUpdatesPromptRow()
            }
        }
    }
}

@Composable
private fun StorageLimitSlider(storageLimitIndex: Int, onAction: (SettingsStorageAction) -> Unit) {
    val storageLimitSteps = listOf(0.1f, 0.5f, 1f, 2f, 5f, 10f, -1f)
    var sliderIndex by remember(storageLimitIndex) {
        mutableIntStateOf(storageLimitIndex)
    }
    val label = when (sliderIndex) {
        storageLimitSteps.lastIndex -> stringResource(R.string.settings_storage_limit_unlimited)
        0 -> stringResource(R.string.settings_storage_limit_mb, 100)
        1 -> stringResource(R.string.settings_storage_limit_mb, 500)
        else -> stringResource(R.string.settings_storage_limit_gb, storageLimitSteps[sliderIndex].toInt())
    }
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Slider(
            value = sliderIndex.toFloat(),
            // roundToInt, not toInt: tick values are float-lerped
            // (4.9999 truncates to 4 and lands on the wrong step).
            onValueChange = {
                sliderIndex = it.roundToInt().coerceIn(0, storageLimitSteps.lastIndex)
            },
            onValueChangeFinished = {
                onAction(SettingsStorageAction.SetStorageLimit(storageLimitSteps[sliderIndex]))
            },
            valueRange = 0f..(storageLimitSteps.lastIndex).toFloat(),
            steps = storageLimitSteps.size - 2,
        )
    }
}

@Composable
private fun StorageAutoDownloadControls(state: SettingsUiState, onAction: (SettingsStorageAction) -> Unit) {
    Column {
        Spacer(modifier = Modifier.height(16.dp))
        SettingsToggleRow(
            title = stringResource(R.string.settings_auto_download_future),
            checked = state.autoDownloadFutureContent,
            onCheckedChange = {
                onAction(SettingsStorageAction.SetAutoDownloadFutureContent(it))
            },
            extras = SettingsToggleExtras(
                description = stringResource(R.string.settings_auto_download_future_desc),
            ),
        )

        SettingsSubToggle(
            visible = state.autoDownloadFutureContent,
            title = stringResource(R.string.settings_auto_download_favorites),
            description = stringResource(R.string.settings_auto_download_favorites_desc),
            checked = state.autoDownloadFavorites,
            onCheckedChange = {
                onAction(SettingsStorageAction.SetAutoDownloadFavorites(it))
            },
        )

        Spacer(modifier = Modifier.height(16.dp))
        SettingsToggleRow(
            title = stringResource(R.string.settings_download_notifications),
            checked = state.downloadNotificationsEnabled,
            onCheckedChange = {
                onAction(SettingsStorageAction.SetDownloadNotificationsEnabled(it))
            },
            extras = SettingsToggleExtras(
                description = stringResource(R.string.settings_download_notifications_desc),
            ),
        )
    }
}
