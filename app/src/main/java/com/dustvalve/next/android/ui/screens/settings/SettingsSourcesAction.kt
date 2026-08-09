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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.dustvalve.next.android.R
import com.dustvalve.next.android.ui.components.AppButtonGroup
import com.dustvalve.next.android.util.legacyAudioPermission

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
    data class SetSoundcloudEnabled(val enabled: Boolean) : SettingsSourcesAction
    data class SetYoutubeDefaultSource(val source: String) : SettingsSourcesAction
    data object ClearScanMessage : SettingsSourcesAction
}

internal fun handleSettingsSourcesAction(viewModel: SettingsViewModel, action: SettingsSourcesAction) {
    val localMusic = viewModel.localMusic
    val storageSources = viewModel.storageSources
    when (action) {
        is SettingsSourcesAction.SetLocalMusicEnabled ->
            localMusic.setEnabled(action.enabled)

        is SettingsSourcesAction.SetLocalMusicUseMediaStore ->
            localMusic.setUseMediaStore(action.use)

        is SettingsSourcesAction.AddLocalMusicFolder ->
            localMusic.addFolder(action.uri)

        is SettingsSourcesAction.RemoveLocalMusicFolder ->
            localMusic.removeFolder(action.uri)

        SettingsSourcesAction.RescanLocalMusic ->
            localMusic.rescan()

        is SettingsSourcesAction.SetKeepLocalSort ->
            storageSources.setKeepLocalSort(action.keep)

        is SettingsSourcesAction.SetKeepLocalFilters ->
            storageSources.setKeepLocalFilters(action.keep)

        is SettingsSourcesAction.SetBandcampEnabled ->
            storageSources.setBandcampEnabled(action.enabled)

        is SettingsSourcesAction.SetYoutubeEnabled ->
            storageSources.setYoutubeEnabled(action.enabled)

        is SettingsSourcesAction.SetSoundcloudEnabled ->
            storageSources.setSoundcloudEnabled(action.enabled)

        is SettingsSourcesAction.SetYoutubeDefaultSource ->
            storageSources.setYoutubeDefaultSource(action.source)

        SettingsSourcesAction.ClearScanMessage ->
            localMusic.clearScanMessage()
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
            SourcesCardContent(
                state = state,
                onAction = onAction,
                onLocalEnableNeedsAudioPermission = {
                    localEnableAudioPermissionLauncher.launch(legacyAudioPermission())
                },
                onLocalEnableNeedsFolder = { folderPickerLauncher.launch(null) },
                onRequestAudioPermission = {
                    audioPermissionLauncher.launch(legacyAudioPermission())
                },
                onPickFolder = { folderPickerLauncher.launch(null) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SourcesCardContent(
    state: SettingsUiState,
    onAction: (SettingsSourcesAction) -> Unit,
    onLocalEnableNeedsAudioPermission: () -> Unit,
    onLocalEnableNeedsFolder: () -> Unit,
    onRequestAudioPermission: () -> Unit,
    onPickFolder: () -> Unit,
) {
    Column(modifier = Modifier.padding(16.dp)) {
        SettingsToggleRow(
            title = stringResource(R.string.settings_source_local),
            icon = R.drawable.ic_phone_android,
            checked = state.localMusicEnabled,
            onCheckedChange = { enabled ->
                onAction(SettingsSourcesAction.SetLocalMusicEnabled(enabled))
                if (enabled && state.localMusicUseMediaStore) {
                    onLocalEnableNeedsAudioPermission()
                } else if (enabled && !state.localMusicUseMediaStore && state.localMusicFolderUris.isEmpty()) {
                    onLocalEnableNeedsFolder()
                }
            },
        )

        AnimatedVisibility(
            visible = state.localMusicEnabled,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            LocalMusicSourceDetails(
                state = state,
                onAction = onAction,
                onRequestAudioPermission = onRequestAudioPermission,
                onPickFolder = onPickFolder,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

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
            YoutubeDefaultSourcePicker(
                youtubeDefaultSource = state.youtubeDefaultSource,
                onAction = onAction,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        SettingsToggleRow(
            title = stringResource(R.string.settings_source_soundcloud),
            icon = R.drawable.ic_graphic_eq,
            checked = state.soundcloudEnabled,
            onCheckedChange = {
                onAction(SettingsSourcesAction.SetSoundcloudEnabled(it))
            },
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LocalMusicSourceDetails(
    state: SettingsUiState,
    onAction: (SettingsSourcesAction) -> Unit,
    onRequestAudioPermission: () -> Unit,
    onPickFolder: () -> Unit,
) {
    Column(modifier = Modifier.padding(top = 12.dp)) {
        SettingsToggleRow(
            title = stringResource(R.string.settings_use_individual_folders),
            description = stringResource(R.string.settings_use_individual_folders_desc),
            checked = !state.localMusicUseMediaStore,
            onCheckedChange = { useIndividualFolders ->
                if (useIndividualFolders) {
                    onAction(SettingsSourcesAction.SetLocalMusicUseMediaStore(false))
                } else {
                    onRequestAudioPermission()
                }
            },
            subRow = true,
        )

        if (!state.localMusicUseMediaStore) {
            LocalMusicSafFolderControls(
                folderUris = state.localMusicFolderUris,
                isScanning = state.isScanning,
                onAction = onAction,
                onPickFolder = onPickFolder,
            )
        } else {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = SUB_TOGGLE_INDENT),
            ) {
                LocalMusicRescanButton(
                    isScanning = state.isScanning,
                    onRescan = { onAction(SettingsSourcesAction.RescanLocalMusic) },
                )
            }
        }

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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LocalMusicSafFolderControls(
    folderUris: List<String>,
    isScanning: Boolean,
    onAction: (SettingsSourcesAction) -> Unit,
    onPickFolder: () -> Unit,
) {
    Column {
        if (folderUris.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            folderUris.forEach { uri ->
                LocalMusicFolderRow(uri = uri, onAction = onAction)
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
                onClick = onPickFolder,
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

            if (folderUris.isNotEmpty()) {
                LocalMusicRescanButton(
                    isScanning = isScanning,
                    onRescan = { onAction(SettingsSourcesAction.RescanLocalMusic) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LocalMusicFolderRow(uri: String, onAction: (SettingsSourcesAction) -> Unit) {
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LocalMusicRescanButton(isScanning: Boolean, onRescan: () -> Unit) {
    FilledTonalButton(
        onClick = onRescan,
        shapes = ButtonDefaults.shapes(),
        enabled = !isScanning,
    ) {
        if (isScanning) {
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun YoutubeDefaultSourcePicker(youtubeDefaultSource: String, onAction: (SettingsSourcesAction) -> Unit) {
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
        val ytSourceSelected = ytSourceOptions.indexOf(youtubeDefaultSource).coerceAtLeast(0)
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
                                    onAction(SettingsSourcesAction.SetYoutubeDefaultSource(value))
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shapes = when (index) {
                                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()

                                ytSourceOptions.lastIndex ->
                                    ButtonGroupDefaults.connectedTrailingButtonShapes()

                                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
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
