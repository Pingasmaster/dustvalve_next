package com.dustvalve.next.android.ui.screens.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import coil3.compose.AsyncImage
import com.dustvalve.next.android.R
import com.dustvalve.next.android.domain.model.AudioFormat
import com.dustvalve.next.android.ui.components.AppButtonGroup
import com.dustvalve.next.android.ui.components.StorageIndicator
import com.dustvalve.next.android.ui.theme.AppShapes
import com.dustvalve.next.android.ui.util.displayNameRes
import com.dustvalve.next.android.update.UpdateUiState
import kotlin.math.roundToInt

/** Actions emitted by [SettingsSourcesSection]. */
internal sealed interface SettingsSourcesAction {
    data class SetLocalMusicEnabled(val enabled: Boolean) : SettingsSourcesAction
    data class SetLocalMusicUseMediaStore(val use: Boolean) : SettingsSourcesAction
    data class AddLocalMusicFolder(val uri: String) : SettingsSourcesAction
    data class RemoveLocalMusicFolder(val uri: String) : SettingsSourcesAction
    data object RescanLocalMusic : SettingsSourcesAction
    data class SetKeepLocalSort(val keep: Boolean) : SettingsSourcesAction
    data class SetKeepLocalFilters(val keep: Boolean) : SettingsSourcesAction
    data class SetBandcampEnabled(val enabled: Boolean) : SettingsSourcesAction
    data class SetYoutubeEnabled(val enabled: Boolean) : SettingsSourcesAction
    data class SetYoutubeDefaultSource(val source: String) : SettingsSourcesAction
    data object ClearScanMessage : SettingsSourcesAction
}

internal fun handleSettingsSourcesAction(viewModel: SettingsViewModel, action: SettingsSourcesAction) {
    when (action) {
        is SettingsSourcesAction.SetLocalMusicEnabled ->
            viewModel.setLocalMusicEnabled(action.enabled)

        is SettingsSourcesAction.SetLocalMusicUseMediaStore ->
            viewModel.setLocalMusicUseMediaStore(action.use)

        is SettingsSourcesAction.AddLocalMusicFolder ->
            viewModel.addLocalMusicFolder(action.uri)

        is SettingsSourcesAction.RemoveLocalMusicFolder ->
            viewModel.removeLocalMusicFolder(action.uri)

        SettingsSourcesAction.RescanLocalMusic ->
            viewModel.rescanLocalMusic()

        is SettingsSourcesAction.SetKeepLocalSort ->
            viewModel.setKeepLocalSort(action.keep)

        is SettingsSourcesAction.SetKeepLocalFilters ->
            viewModel.setKeepLocalFilters(action.keep)

        is SettingsSourcesAction.SetBandcampEnabled ->
            viewModel.setBandcampEnabled(action.enabled)

        is SettingsSourcesAction.SetYoutubeEnabled ->
            viewModel.setYoutubeEnabled(action.enabled)

        is SettingsSourcesAction.SetYoutubeDefaultSource ->
            viewModel.setYoutubeDefaultSource(action.source)

        SettingsSourcesAction.ClearScanMessage ->
            viewModel.clearScanMessage()
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SettingsSourcesSection(
    state: SettingsUiState,
    snackbarHostState: SnackbarHostState,
    onAction: (SettingsSourcesAction) -> Unit,
) {
    val localContext = LocalContext.current
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted: Boolean ->
        if (granted) {
            onAction(SettingsSourcesAction.SetLocalMusicUseMediaStore(true))
        }
    }
    // Used only when the permission request came from flipping the
    // Local source ON: a denial rolls the just-persisted enable
    // back (mirrors LocalViewModel.onAudioPermissionDenied), so
    // the toggle doesn't stay on with no way to scan anything.
    val localEnableAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted: Boolean ->
        if (granted) {
            onAction(SettingsSourcesAction.SetLocalMusicUseMediaStore(true))
        } else {
            onAction(SettingsSourcesAction.SetLocalMusicEnabled(false))
        }
    }
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri: Uri? ->
        if (uri != null) {
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            try {
                localContext.contentResolver.takePersistableUriPermission(uri, takeFlags)
            } catch (_: SecurityException) {
                // Best effort
            } catch (_: IllegalArgumentException) {
                // Best effort
            }
            onAction(SettingsSourcesAction.AddLocalMusicFolder(uri.toString()))
        }
    }

    val scanText = state.scanMessage?.asString()
    val sourcesAction by rememberUpdatedState(onAction)
    LaunchedEffect(scanText) {
        scanText?.let { message ->
            try {
                snackbarHostState.showSnackbar(message)
            } finally {
                sourcesAction(SettingsSourcesAction.ClearScanMessage)
            }
        }
    }

    SettingsSection(
        title = stringResource(R.string.settings_section_sources),
        icon = R.drawable.ic_tune,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Local
                SettingsToggleRow(
                    title = stringResource(R.string.settings_source_local),
                    icon = R.drawable.ic_phone_android,
                    checked = state.localMusicEnabled,
                    onCheckedChange = { enabled ->
                        onAction(SettingsSourcesAction.SetLocalMusicEnabled(enabled))
                        if (enabled && state.localMusicUseMediaStore) {
                            localEnableAudioPermissionLauncher.launch(Manifest.permission.READ_MEDIA_AUDIO)
                        } else if (enabled && !state.localMusicUseMediaStore && state.localMusicFolderUris.isEmpty()) {
                            folderPickerLauncher.launch(null)
                        }
                    },
                )

                AnimatedVisibility(
                    visible = state.localMusicEnabled,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    Column(modifier = Modifier.padding(top = 12.dp)) {
                        // "Scan all audio" toggle
                        SettingsToggleRow(
                            title = stringResource(R.string.settings_use_individual_folders),
                            description = stringResource(R.string.settings_use_individual_folders_desc),
                            checked = !state.localMusicUseMediaStore,
                            onCheckedChange = { useIndividualFolders ->
                                if (useIndividualFolders) {
                                    onAction(SettingsSourcesAction.SetLocalMusicUseMediaStore(false))
                                } else {
                                    audioPermissionLauncher.launch(Manifest.permission.READ_MEDIA_AUDIO)
                                }
                            },
                            subRow = true,
                        )

                        if (!state.localMusicUseMediaStore) {
                            // SAF folder mode
                            if (state.localMusicFolderUris.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                state.localMusicFolderUris.forEach { uri ->
                                    val selectedFolderFallback = stringResource(R.string.common_selected_folder)
                                    val folderName = try {
                                        uri.toUri().lastPathSegment
                                            ?.substringAfterLast(':')
                                            ?: selectedFolderFallback
                                    } catch (_: IllegalArgumentException) {
                                        selectedFolderFallback
                                    } catch (_: SecurityException) {
                                        selectedFolderFallback
                                    }
                                    ListItem(
                                        leadingContent = {
                                            Icon(
                                                painter = painterResource(R.drawable.ic_folder_open),
                                                contentDescription = null,
                                                modifier = Modifier.size(20.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        },
                                        trailingContent = {
                                            IconButton(
                                                onClick = {
                                                    onAction(SettingsSourcesAction.RemoveLocalMusicFolder(uri))
                                                },
                                                shapes = IconButtonDefaults.shapes(),
                                            ) {
                                                Icon(
                                                    painter = painterResource(R.drawable.ic_close),
                                                    contentDescription = stringResource(R.string.settings_cd_remove_folder),
                                                    modifier = Modifier.size(20.dp),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        },
                                        modifier = Modifier.padding(start = SUB_TOGGLE_INDENT),
                                        colors = ListItemDefaults.colors(
                                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                        ),
                                    ) {
                                        Text(
                                            text = folderName,
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = SUB_TOGGLE_INDENT),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                FilledTonalButton(
                                    onClick = { folderPickerLauncher.launch(null) },
                                    shapes = ButtonDefaults.shapes(),
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_folder_open),
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.settings_add_folder))
                                }

                                if (state.localMusicFolderUris.isNotEmpty()) {
                                    FilledTonalButton(
                                        onClick = { onAction(SettingsSourcesAction.RescanLocalMusic) },
                                        shapes = ButtonDefaults.shapes(),
                                        enabled = !state.isScanning,
                                    ) {
                                        if (state.isScanning) {
                                            CircularWavyProgressIndicator(modifier = Modifier.size(18.dp))
                                        } else {
                                            Icon(
                                                painter = painterResource(R.drawable.ic_refresh),
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(stringResource(R.string.settings_rescan))
                                    }
                                }
                            }
                        } else {
                            // MediaStore mode - just show Rescan button
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = SUB_TOGGLE_INDENT),
                            ) {
                                FilledTonalButton(
                                    onClick = { onAction(SettingsSourcesAction.RescanLocalMusic) },
                                    shapes = ButtonDefaults.shapes(),
                                    enabled = !state.isScanning,
                                ) {
                                    if (state.isScanning) {
                                        CircularWavyProgressIndicator(modifier = Modifier.size(18.dp))
                                    } else {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_refresh),
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.settings_rescan))
                                }
                            }
                        }

                        // Persistence sub-toggles for the Local tab's
                        // sort + filter chip selections. Independent so
                        // the user can keep one without the other.
                        Spacer(modifier = Modifier.height(8.dp))
                        SettingsToggleRow(
                            title = stringResource(R.string.settings_local_keep_sort),
                            description = stringResource(R.string.settings_local_keep_sort_desc),
                            checked = state.keepLocalSort,
                            onCheckedChange = {
                                onAction(SettingsSourcesAction.SetKeepLocalSort(it))
                            },
                            subRow = true,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        SettingsToggleRow(
                            title = stringResource(R.string.settings_local_keep_filters),
                            description = stringResource(R.string.settings_local_keep_filters_desc),
                            checked = state.keepLocalFilters,
                            onCheckedChange = {
                                onAction(SettingsSourcesAction.SetKeepLocalFilters(it))
                            },
                            subRow = true,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Bandcamp
                SettingsToggleRow(
                    title = stringResource(R.string.settings_source_bandcamp),
                    icon = R.drawable.ic_cloud,
                    checked = state.bandcampEnabled,
                    onCheckedChange = {
                        onAction(SettingsSourcesAction.SetBandcampEnabled(it))
                    },
                    switchTag = com.dustvalve.next.android.ui.TestTags.settingsSwitch("bandcamp"),
                )

                Spacer(modifier = Modifier.height(16.dp))

                // YouTube
                SettingsToggleRow(
                    title = stringResource(R.string.settings_source_youtube),
                    icon = R.drawable.ic_play_circle,
                    checked = state.youtubeEnabled,
                    onCheckedChange = {
                        onAction(SettingsSourcesAction.SetYoutubeEnabled(it))
                    },
                )

                AnimatedVisibility(
                    visible = state.youtubeEnabled,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    Column(modifier = Modifier.padding(top = 12.dp, start = SUB_TOGGLE_INDENT)) {
                        Text(
                            text = stringResource(R.string.settings_youtube_default_source_title),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = stringResource(R.string.settings_youtube_default_source_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        val ytSourceOptions = listOf("youtube", "youtube_music")
                        val ytSourceLabels = listOf(
                            stringResource(R.string.settings_source_youtube),
                            stringResource(R.string.settings_youtube_music),
                        )
                        val ytSourceSelected = ytSourceOptions.indexOf(state.youtubeDefaultSource).coerceAtLeast(0)
                        AppButtonGroup(
                            overflowIndicator = { _ -> },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            ytSourceOptions.forEachIndexed { index, value ->
                                customItem(
                                    buttonGroupContent = {
                                        ToggleButton(
                                            checked = index == ytSourceSelected,
                                            onCheckedChange = { isChecked ->
                                                if (isChecked) {
                                                    onAction(
                                                        SettingsSourcesAction.SetYoutubeDefaultSource(value),
                                                    )
                                                }
                                            },
                                            modifier = Modifier.weight(1f),
                                            shapes = when (index) {
                                                0 ->
                                                    ButtonGroupDefaults
                                                        .connectedLeadingButtonShapes()

                                                ytSourceOptions.lastIndex ->
                                                    ButtonGroupDefaults
                                                        .connectedTrailingButtonShapes()

                                                else ->
                                                    ButtonGroupDefaults
                                                        .connectedMiddleButtonShapes()
                                            },
                                        ) {
                                            Text(ytSourceLabels[index])
                                        }
                                    },
                                    menuContent = {},
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

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
        AlertDialog(
            onDismissRequest = { showDisableFolderDialog = false },
            title = { Text(stringResource(R.string.settings_dedicated_folder_disable_title)) },
            text = { Text(stringResource(R.string.settings_dedicated_folder_disable_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDisableFolderDialog = false
                        onAction(SettingsStorageAction.DisableDedicatedFolder)
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(R.string.settings_dedicated_folder_disable_confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDisableFolderDialog = false },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(R.string.common_action_cancel))
                }
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

                val storageLimitSteps = listOf(0.1f, 0.5f, 1f, 2f, 5f, 10f, -1f)
                var sliderIndex by remember(state.storageLimitIndex) {
                    mutableIntStateOf(state.storageLimitIndex)
                }
                val label = when (sliderIndex) {
                    storageLimitSteps.lastIndex -> stringResource(R.string.settings_storage_limit_unlimited)
                    0 -> stringResource(R.string.settings_storage_limit_mb, 100)
                    1 -> stringResource(R.string.settings_storage_limit_mb, 500)
                    else -> stringResource(R.string.settings_storage_limit_gb, storageLimitSteps[sliderIndex].toInt())
                }
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

                Spacer(modifier = Modifier.height(16.dp))

                // Dedicated folder: store all user data in a folder of
                // the user's choice instead of app-internal memory.
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
                    Column(modifier = Modifier.padding(top = 12.dp)) {
                        // Current folder path + change button
                        val folderLabel = remember(state.dedicatedFolderTreeUri) {
                            val uriStr = state.dedicatedFolderTreeUri
                            if (uriStr.isNullOrBlank()) {
                                null
                            } else {
                                // toUri() / lastPathSegment can't throw, but be defensive
                                // against a future surprise (e.g. SecurityException from
                                // a custom Uri implementation).
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
                                text = folderLabel
                                    ?: stringResource(R.string.common_selected_folder),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(
                                onClick = { folderPickerForDedicatedFolder.launch(null) },
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

                // Sub-toggle: Auto-download favorites - only shown when
                // the parent "future content" toggle is on.
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

                // Live Updates (status-bar chip) is a per-app runtime
                // permission the user must grant; prompt + deep-link when off.
                LiveUpdatesPromptRow()
            }
        }
    }
}

/** Actions emitted by [SettingsAppearanceSection]. */
internal sealed interface SettingsAppearanceAction {
    data class SetThemeMode(val mode: String) : SettingsAppearanceAction
    data class SetDynamicColor(val enabled: Boolean) : SettingsAppearanceAction
    data class SetAlbumArtTheme(val enabled: Boolean) : SettingsAppearanceAction
    data class SetOledBlack(val enabled: Boolean) : SettingsAppearanceAction
    data class SetProgressBarStyle(val style: String) : SettingsAppearanceAction
    data class SetProgressBarSizeDp(val sizeDp: Int) : SettingsAppearanceAction
}

internal fun handleSettingsAppearanceAction(viewModel: SettingsViewModel, action: SettingsAppearanceAction) {
    when (action) {
        is SettingsAppearanceAction.SetThemeMode ->
            viewModel.setThemeMode(action.mode)

        is SettingsAppearanceAction.SetDynamicColor ->
            viewModel.setDynamicColor(action.enabled)

        is SettingsAppearanceAction.SetAlbumArtTheme ->
            viewModel.setAlbumArtTheme(action.enabled)

        is SettingsAppearanceAction.SetOledBlack ->
            viewModel.setOledBlack(action.enabled)

        is SettingsAppearanceAction.SetProgressBarStyle ->
            viewModel.setProgressBarStyle(action.style)

        is SettingsAppearanceAction.SetProgressBarSizeDp ->
            viewModel.setProgressBarSizeDp(action.sizeDp)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SettingsAppearanceSection(state: SettingsUiState, onAction: (SettingsAppearanceAction) -> Unit) {
    SettingsSection(
        title = stringResource(R.string.settings_section_appearance),
        icon = R.drawable.ic_palette,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.settings_theme),
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(modifier = Modifier.height(8.dp))

                val themeOptions = listOf("light", "dark", "system")
                val themeLabels = listOf(
                    stringResource(R.string.settings_theme_light),
                    stringResource(R.string.settings_theme_dark),
                    stringResource(R.string.settings_theme_system),
                )
                val selectedIndex = themeOptions.indexOf(state.themeMode).coerceAtLeast(0)

                AppButtonGroup(
                    overflowIndicator = { _ -> },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    themeOptions.forEachIndexed { index, mode ->
                        customItem(
                            buttonGroupContent = {
                                ToggleButton(
                                    checked = index == selectedIndex,
                                    onCheckedChange = { isChecked ->
                                        if (isChecked) {
                                            onAction(SettingsAppearanceAction.SetThemeMode(mode))
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    shapes = when (index) {
                                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                        themeOptions.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                                    },
                                ) {
                                    Text(themeLabels[index])
                                }
                            },
                            menuContent = {},
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                SettingsToggleRow(
                    title = stringResource(R.string.settings_dynamic_color),
                    description = stringResource(R.string.settings_dynamic_color_desc),
                    checked = state.dynamicColor,
                    onCheckedChange = {
                        onAction(SettingsAppearanceAction.SetDynamicColor(it))
                    },
                )

                Spacer(modifier = Modifier.height(16.dp))

                SettingsToggleRow(
                    title = stringResource(R.string.settings_album_art_colors),
                    description = stringResource(R.string.settings_album_art_colors_desc),
                    checked = state.albumArtTheme,
                    onCheckedChange = {
                        onAction(SettingsAppearanceAction.SetAlbumArtTheme(it))
                    },
                )

                val isDarkEffective = when (state.themeMode) {
                    "dark" -> true
                    "light" -> false
                    else -> isSystemInDarkTheme()
                }

                SettingsSubToggle(
                    visible = isDarkEffective,
                    title = stringResource(R.string.settings_oled_black),
                    description = stringResource(R.string.settings_oled_black_desc),
                    checked = state.oledBlack,
                    onCheckedChange = {
                        onAction(SettingsAppearanceAction.SetOledBlack(it))
                    },
                )

                Spacer(modifier = Modifier.height(16.dp))

                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.settings_progress_bar_style),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Connected ButtonGroup [Wavy | Linear] - uses the same
                    // M3E ButtonGroup component as the theme selector above
                    // so the inter-button gap matches.
                    val styleOptions = listOf("wavy", "linear")
                    val styleLabels = listOf(
                        stringResource(R.string.settings_progress_bar_style_wavy),
                        stringResource(R.string.settings_progress_bar_style_linear),
                    )
                    AppButtonGroup(
                        overflowIndicator = { _ -> },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        styleOptions.forEachIndexed { i, opt ->
                            customItem(
                                buttonGroupContent = {
                                    ToggleButton(
                                        checked = state.progressBarStyle == opt,
                                        onCheckedChange = {
                                            if (it) {
                                                onAction(SettingsAppearanceAction.SetProgressBarStyle(opt))
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                        shapes = when (i) {
                                            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                            styleOptions.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                                        },
                                    ) {
                                        Text(styleLabels[i])
                                    }
                                },
                                menuContent = {},
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Size slider - discrete 4..32 dp in 4 dp steps.
                    val sizeSteps = listOf(4, 8, 12, 16, 20, 24, 28, 32)
                    var sizeIndex by remember(state.progressBarSizeDp) {
                        mutableIntStateOf(
                            sizeSteps.indexOf(state.progressBarSizeDp).coerceAtLeast(0),
                        )
                    }
                    Text(
                        text = stringResource(
                            R.string.settings_progress_bar_size,
                            sizeSteps[sizeIndex],
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Slider(
                        value = sizeIndex.toFloat(),
                        onValueChange = {
                            sizeIndex = it.roundToInt().coerceIn(0, sizeSteps.lastIndex)
                        },
                        onValueChangeFinished = {
                            onAction(SettingsAppearanceAction.SetProgressBarSizeDp(sizeSteps[sizeIndex]))
                        },
                        valueRange = 0f..(sizeSteps.lastIndex).toFloat(),
                        steps = sizeSteps.size - 2,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SettingsConnectionsSection(
    state: SettingsUiState,
    onBandcampLoginClick: () -> Unit,
    onYouTubeMusicLoginClick: () -> Unit,
    onSignOutBandcamp: () -> Unit,
    onSignOutYouTubeMusic: () -> Unit,
) {
    SettingsSection(
        title = stringResource(R.string.settings_section_connections),
        icon = R.drawable.ic_account_circle,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Bandcamp connection
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_cloud),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.settings_source_bandcamp),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (state.accountState.isLoggedIn) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (state.accountState.avatarUrl != null) {
                            AsyncImage(
                                model = state.accountState.avatarUrl,
                                contentDescription = stringResource(R.string.settings_cd_avatar),
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(AppShapes.Avatar),
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = state.accountState.username ?: stringResource(R.string.common_connected),
                                style = MaterialTheme.typography.bodyMedium,
                                // Primary accent - matches the YouTube
                                // Music connected state below.
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onSignOutBandcamp,
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.common_action_disconnect))
                    }
                } else {
                    Text(
                        text = stringResource(R.string.settings_connect_bandcamp_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onBandcampLoginClick,
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.settings_connect_bandcamp))
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // YouTube Music connection
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_play_circle),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.settings_youtube_music),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (state.ytmAccountState.isLoggedIn) {
                    Text(
                        text = stringResource(R.string.common_connected),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onSignOutYouTubeMusic,
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.common_action_disconnect))
                    }
                } else {
                    Text(
                        text = stringResource(R.string.settings_connect_youtube_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onYouTubeMusicLoginClick,
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.settings_connect_youtube))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SettingsAudioQualitySection(
    state: SettingsUiState,
    onShowFormatSheet: () -> Unit,
    onSetSaveDataOnMetered: (Boolean) -> Unit,
    onSetProgressiveDownload: (Boolean) -> Unit,
    onSetSeamlessQualityUpgrade: (Boolean) -> Unit,
) {
    SettingsSection(
        title = stringResource(R.string.settings_section_audio_quality),
        icon = R.drawable.ic_high_quality,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.settings_download_format),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = stringResource(R.string.settings_download_format_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                val currentFormat = AudioFormat.fromKey(state.downloadFormat)
                FilledTonalButton(
                    onClick = onShowFormatSheet,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(currentFormat?.displayNameRes ?: R.string.audio_format_flac))
                }

                Spacer(modifier = Modifier.height(16.dp))

                SettingsToggleRow(
                    title = stringResource(R.string.settings_mp3_on_metered),
                    description = stringResource(R.string.settings_mp3_on_metered_desc),
                    checked = state.saveDataOnMetered,
                    onCheckedChange = onSetSaveDataOnMetered,
                )

                Spacer(modifier = Modifier.height(16.dp))

                SettingsToggleRow(
                    title = stringResource(R.string.settings_progressive_download),
                    description = stringResource(R.string.settings_progressive_download_desc),
                    checked = state.progressiveDownload,
                    onCheckedChange = onSetProgressiveDownload,
                )

                // Dependent on progressive download - indented sub-toggle
                // like every other dependent setting.
                SettingsSubToggle(
                    visible = state.progressiveDownload,
                    title = stringResource(R.string.settings_seamless_upgrade),
                    description = stringResource(R.string.settings_seamless_upgrade_desc),
                    checked = state.seamlessQualityUpgrade,
                    onCheckedChange = onSetSeamlessQualityUpgrade,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SettingsPlayerSection(
    state: SettingsUiState,
    onSetShowInlineVolumeSlider: (Boolean) -> Unit,
    onSetShowVolumeButton: (Boolean) -> Unit,
    onSetKeepScreenOnInApp: (Boolean) -> Unit,
    onSetKeepScreenOnWhilePlaying: (Boolean) -> Unit,
) {
    SettingsSection(
        title = stringResource(R.string.settings_section_player),
        icon = R.drawable.ic_volume_up,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                SettingsToggleRow(
                    title = stringResource(R.string.settings_volume_slider),
                    description = stringResource(R.string.settings_volume_slider_desc),
                    checked = state.showInlineVolumeSlider,
                    onCheckedChange = onSetShowInlineVolumeSlider,
                )

                Spacer(modifier = Modifier.height(16.dp))

                SettingsToggleRow(
                    title = stringResource(R.string.settings_volume_button),
                    description = stringResource(R.string.settings_volume_button_desc),
                    checked = state.showVolumeButton,
                    onCheckedChange = onSetShowVolumeButton,
                )

                Spacer(modifier = Modifier.height(16.dp))

                SettingsToggleRow(
                    title = stringResource(R.string.settings_keep_screen_open),
                    description = stringResource(R.string.settings_keep_screen_open_desc),
                    checked = state.keepScreenOnInApp,
                    onCheckedChange = onSetKeepScreenOnInApp,
                )

                // Sub-toggle: only shown when the parent is on. When
                // checked, restricts wake-lock to "app open AND
                // playing". Defaults to true so the parent's
                // first-flip yields the lower-impact behaviour.
                SettingsSubToggle(
                    visible = state.keepScreenOnInApp,
                    title = stringResource(R.string.settings_keep_screen_only_playing),
                    description = stringResource(R.string.settings_keep_screen_only_playing_desc),
                    checked = state.keepScreenOnWhilePlaying,
                    onCheckedChange = onSetKeepScreenOnWhilePlaying,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SettingsSearchSection(
    state: SettingsUiState,
    onSetSearchHistoryEnabled: (Boolean) -> Unit,
    onSetSearchHistorySource: (String, Boolean) -> Unit,
    onClearAllSearchHistory: () -> Unit,
) {
    SettingsSection(
        title = stringResource(R.string.settings_section_search),
        icon = R.drawable.ic_search,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                SettingsToggleRow(
                    title = stringResource(R.string.settings_search_history),
                    description = stringResource(R.string.settings_search_history_desc),
                    checked = state.searchHistoryEnabled,
                    onCheckedChange = onSetSearchHistoryEnabled,
                )

                // Per-source sub-toggles, only when the global toggle is on
                // and only for sources the user has enabled.
                AnimatedVisibility(
                    visible = state.searchHistoryEnabled,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    Column(modifier = Modifier.padding(top = 12.dp, start = SUB_TOGGLE_INDENT)) {
                        Text(
                            text = stringResource(R.string.settings_search_history_per_source),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        if (state.bandcampEnabled) {
                            SearchHistorySourceRow(
                                labelRes = R.string.settings_search_history_source_bandcamp,
                                checked = state.searchHistoryBandcamp,
                                onCheckedChange = { onSetSearchHistorySource("bandcamp", it) },
                            )
                        }
                        if (state.youtubeEnabled) {
                            SearchHistorySourceRow(
                                labelRes = R.string.settings_search_history_source_youtube,
                                checked = state.searchHistoryYoutube,
                                onCheckedChange = { onSetSearchHistorySource("youtube", it) },
                            )
                        }
                        if (state.localMusicEnabled) {
                            SearchHistorySourceRow(
                                labelRes = R.string.settings_search_history_source_local,
                                checked = state.searchHistoryLocal,
                                onCheckedChange = { onSetSearchHistorySource("local", it) },
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                FilledTonalButton(
                    onClick = onClearAllSearchHistory,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_delete_sweep),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_search_history_clear_all))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SettingsAboutSection(
    state: SettingsUiState,
    onCheckForAppUpdate: () -> Unit,
    onSetAutoUpdateCheckEnabled: (Boolean) -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    SettingsSection(
        title = stringResource(R.string.settings_section_about),
        icon = R.drawable.ic_info,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(R.string.settings_version),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = com.dustvalve.next.android.BuildConfig.VERSION_NAME,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.settings_license),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
                Spacer(modifier = Modifier.height(12.dp))
                FilledTonalButton(
                    onClick = {
                        uriHandler.openUri(
                            com.dustvalve.next.android.update.AppUpdateService.REPO_URL,
                        )
                    },
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_info),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_open_repository))
                }
                Spacer(modifier = Modifier.height(8.dp))
                FilledTonalButton(
                    onClick = onCheckForAppUpdate,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state.updateState !is UpdateUiState.Checking &&
                        state.updateState !is UpdateUiState.Downloading,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_cloud_download),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_check_for_updates))
                }
                Spacer(modifier = Modifier.height(8.dp))
                FilledTonalButton(
                    onClick = {
                        uriHandler.openUri(
                            com.dustvalve.next.android.update.AppUpdateService.REPO_URL + "/issues",
                        )
                    },
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_bug_report),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_report_issue))
                }
                Spacer(modifier = Modifier.height(16.dp))
                SettingsToggleRow(
                    title = stringResource(R.string.settings_auto_update_title),
                    description = stringResource(R.string.settings_auto_update_desc),
                    checked = state.autoUpdateCheckEnabled,
                    onCheckedChange = onSetAutoUpdateCheckEnabled,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SettingsDebugSection(albumCoverLongPressCarousel: Boolean, onSetAlbumCoverLongPressCarousel: (Boolean) -> Unit) {
    SettingsSection(
        title = stringResource(R.string.settings_section_debug),
        icon = R.drawable.ic_bug_report,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                SettingsToggleRow(
                    title = stringResource(R.string.settings_show_debug_info),
                    description = stringResource(R.string.settings_show_debug_info_desc),
                    checked = albumCoverLongPressCarousel,
                    onCheckedChange = onSetAlbumCoverLongPressCarousel,
                )
            }
        }
    }
}
