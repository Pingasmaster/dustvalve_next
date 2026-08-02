package com.dustvalve.next.android.ui.screens.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.dustvalve.next.android.R
import com.dustvalve.next.android.ui.components.StorageIndicator
import kotlin.math.roundToInt

/** Actions emitted by [SettingsStorageSection]. */
internal sealed interface SettingsStorageAction {
    data class SetStorageLimit(val limitGb: Float) : SettingsStorageAction
    data class EnableDedicatedFolder(val uri: String) : SettingsStorageAction
    data object DisableDedicatedFolder : SettingsStorageAction
    data class SetDedicatedFolderIncludeImageCache(val include: Boolean) : SettingsStorageAction
    data class SetDedicatedFolderIncludeMetadataCache(val include: Boolean) : SettingsStorageAction
    data class SetAutoDownloadCollection(val enabled: Boolean) : SettingsStorageAction
    data class SetAutoDownloadFutureContent(val enabled: Boolean) : SettingsStorageAction
    data class SetAutoDownloadFavorites(val enabled: Boolean) : SettingsStorageAction
    data class SetDownloadNotificationsEnabled(val enabled: Boolean) : SettingsStorageAction
}

internal fun handleSettingsStorageAction(viewModel: SettingsViewModel, action: SettingsStorageAction) {
    when (action) {
        is SettingsStorageAction.SetStorageLimit ->
            viewModel.setStorageLimit(action.limitGb)

        is SettingsStorageAction.EnableDedicatedFolder ->
            viewModel.enableDedicatedFolder(action.uri)

        SettingsStorageAction.DisableDedicatedFolder ->
            viewModel.disableDedicatedFolder()

        is SettingsStorageAction.SetDedicatedFolderIncludeImageCache ->
            viewModel.setDedicatedFolderIncludeImageCache(action.include)

        is SettingsStorageAction.SetDedicatedFolderIncludeMetadataCache ->
            viewModel.setDedicatedFolderIncludeMetadataCache(action.include)

        is SettingsStorageAction.SetAutoDownloadCollection ->
            viewModel.setAutoDownloadCollection(action.enabled)

        is SettingsStorageAction.SetAutoDownloadFutureContent ->
            viewModel.setAutoDownloadFutureContent(action.enabled)

        is SettingsStorageAction.SetAutoDownloadFavorites ->
            viewModel.setAutoDownloadFavorites(action.enabled)

        is SettingsStorageAction.SetDownloadNotificationsEnabled ->
            viewModel.setDownloadNotificationsEnabled(action.enabled)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SettingsStorageSection(
    state: SettingsUiState,
    onRemoveAllDownloadsClick: () -> Unit,
    onAction: (SettingsStorageAction) -> Unit,
) {
    val storageContext = LocalContext.current
    var showDisableFolderDialog by rememberSaveable { mutableStateOf(false) }
    val folderPickerForDedicatedFolder = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri: Uri? ->
        if (uri != null) {
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            try {
                storageContext.contentResolver.takePersistableUriPermission(uri, takeFlags)
            } catch (_: SecurityException) {
                // Best effort
            } catch (_: IllegalArgumentException) {
                // Best effort
            }
            onAction(SettingsStorageAction.EnableDedicatedFolder(uri.toString()))
        }
    }

    if (showDisableFolderDialog) {
        DedicatedFolderDisableDialog(
            onDismiss = { showDisableFolderDialog = false },
            onConfirm = {
                showDisableFolderDialog = false
                onAction(SettingsStorageAction.DisableDedicatedFolder)
            },
        )
    }

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

                SettingsToggleRow(
                    title = stringResource(R.string.settings_dedicated_folder_title),
                    description = stringResource(R.string.settings_dedicated_folder_desc),
                    checked = state.dedicatedFolderEnabled,
                    enabled = !state.folderMigrationInProgress,
                    onCheckedChange = { enable ->
                        if (enable) {
                            folderPickerForDedicatedFolder.launch(null)
                        } else {
                            showDisableFolderDialog = true
                        }
                    },
                )

                AnimatedVisibility(
                    visible = state.dedicatedFolderEnabled,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    DedicatedFolderDetails(
                        state = state,
                        onAction = onAction,
                        onChangeFolder = { folderPickerForDedicatedFolder.launch(null) },
                    )
                }

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
private fun DedicatedFolderDisableDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_dedicated_folder_disable_title)) },
        text = { Text(stringResource(R.string.settings_dedicated_folder_disable_text)) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(R.string.settings_dedicated_folder_disable_confirm))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(R.string.common_action_cancel))
            }
        },
    )
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
private fun DedicatedFolderDetails(state: SettingsUiState, onAction: (SettingsStorageAction) -> Unit, onChangeFolder: () -> Unit) {
    Column(modifier = Modifier.padding(top = 12.dp)) {
        val folderLabel = remember(state.dedicatedFolderTreeUri) {
            val uriStr = state.dedicatedFolderTreeUri
            if (uriStr.isNullOrBlank()) {
                null
            } else {
                try {
                    uriStr.toUri().lastPathSegment?.substringAfterLast(':')
                } catch (_: IllegalArgumentException) {
                    null
                } catch (_: SecurityException) {
                    null
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = SUB_TOGGLE_INDENT),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_folder_open),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = folderLabel ?: stringResource(R.string.common_selected_folder),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = onChangeFolder,
                shapes = ButtonDefaults.shapes(),
                enabled = !state.folderMigrationInProgress,
            ) {
                Text(stringResource(R.string.settings_dedicated_folder_change))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        SettingsToggleRow(
            title = stringResource(R.string.settings_dedicated_folder_image_cache),
            description = stringResource(R.string.settings_dedicated_folder_image_cache_desc),
            checked = state.dedicatedFolderIncludeImageCache,
            enabled = !state.folderMigrationInProgress,
            onCheckedChange = {
                onAction(SettingsStorageAction.SetDedicatedFolderIncludeImageCache(it))
            },
            subRow = true,
        )
        Spacer(modifier = Modifier.height(8.dp))
        SettingsToggleRow(
            title = stringResource(R.string.settings_dedicated_folder_metadata_cache),
            description = stringResource(R.string.settings_dedicated_folder_metadata_cache_desc),
            checked = state.dedicatedFolderIncludeMetadataCache,
            enabled = !state.folderMigrationInProgress,
            onCheckedChange = {
                onAction(SettingsStorageAction.SetDedicatedFolderIncludeMetadataCache(it))
            },
            subRow = true,
        )
    }
}

@Composable
private fun StorageAutoDownloadControls(state: SettingsUiState, onAction: (SettingsStorageAction) -> Unit) {
    Column {
        if (state.accountState.isLoggedIn) {
            Spacer(modifier = Modifier.height(16.dp))
            SettingsToggleRow(
                title = stringResource(R.string.settings_auto_download_purchases),
                description = stringResource(R.string.settings_auto_download_purchases_desc),
                checked = state.autoDownloadCollection,
                onCheckedChange = {
                    onAction(SettingsStorageAction.SetAutoDownloadCollection(it))
                },
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        SettingsToggleRow(
            title = stringResource(R.string.settings_auto_download_future),
            description = stringResource(R.string.settings_auto_download_future_desc),
            checked = state.autoDownloadFutureContent,
            onCheckedChange = {
                onAction(SettingsStorageAction.SetAutoDownloadFutureContent(it))
            },
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
            description = stringResource(R.string.settings_download_notifications_desc),
            checked = state.downloadNotificationsEnabled,
            onCheckedChange = {
                onAction(SettingsStorageAction.SetDownloadNotificationsEnabled(it))
            },
        )
    }
}
