package com.dustvalve.next.android.ui.screens.settings

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.dustvalve.next.android.R
import com.dustvalve.next.android.domain.model.AudioFormat
import com.dustvalve.next.android.ui.components.AppButtonGroup
import com.dustvalve.next.android.ui.components.StorageIndicator
import com.dustvalve.next.android.ui.components.update.AppUpdateDialog
import com.dustvalve.next.android.ui.theme.AppShapes
import com.dustvalve.next.android.ui.util.displayNameRes
import com.dustvalve.next.android.update.UpdateUiState
import kotlin.math.roundToInt
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi as M3E

// Shared left-padding for every child toggle that appears under a parent
// switch in the settings UI. Toggle rows go through SettingsToggleRow
// (subRow = true for indented children); dependent single toggles use
// SettingsSubToggle, which adds the canonical AnimatedVisibility reveal.
private val SUB_TOGGLE_INDENT = 16.dp

// Fixed gap between a toggle row's label column and its switch so
// descriptions wrap at the same width on every row.
private val TOGGLE_LABEL_END_GAP = 16.dp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(
    onBandcampLoginClick: () -> Unit,
    onYouTubeMusicLoginClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showRemoveDownloadsDialog by rememberSaveable { mutableStateOf(false) }
    var showFormatSheet by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    val bandcampDisconnectedMsg = stringResource(R.string.settings_bandcamp_disconnected)
    LaunchedEffect(state.bandcampSignOutSuccess) {
        if (state.bandcampSignOutSuccess) {
            try {
                snackbarHostState.showSnackbar(bandcampDisconnectedMsg)
            } finally {
                viewModel.clearSignOutSuccess()
            }
        }
    }

    val ytmDisconnectedMsg = stringResource(R.string.settings_youtube_disconnected)
    LaunchedEffect(state.ytmSignOutSuccess) {
        if (state.ytmSignOutSuccess) {
            try {
                snackbarHostState.showSnackbar(ytmDisconnectedMsg)
            } finally {
                viewModel.clearYtmSignOutSuccess()
            }
        }
    }

    val folderMigrationText = state.folderMigrationMessage?.asString()
    LaunchedEffect(folderMigrationText) {
        folderMigrationText?.let { message ->
            try {
                snackbarHostState.showSnackbar(message)
            } finally {
                viewModel.clearFolderMigrationMessage()
            }
        }
    }

    val folderMigrationErrorText = state.folderMigrationError?.asString()
    LaunchedEffect(folderMigrationErrorText) {
        folderMigrationErrorText?.let { message ->
            try {
                snackbarHostState.showSnackbar(message)
            } finally {
                viewModel.clearFolderMigrationError()
            }
        }
    }

    val historyClearedText = state.searchHistoryClearedMessage?.asString()
    LaunchedEffect(historyClearedText) {
        historyClearedText?.let { message ->
            try {
                snackbarHostState.showSnackbar(message)
            } finally {
                viewModel.clearSearchHistoryClearedMessage()
            }
        }
    }

    val updateText = state.updateMessage?.asString()
    LaunchedEffect(updateText) {
        updateText?.let { message ->
            try {
                snackbarHostState.showSnackbar(message)
            } finally {
                viewModel.clearUpdateMessage()
            }
        }
    }

    if (showRemoveDownloadsDialog) {
        AlertDialog(
            onDismissRequest = { showRemoveDownloadsDialog = false },
            title = { Text(stringResource(R.string.settings_remove_downloads_title)) },
            text = { Text(stringResource(R.string.settings_remove_downloads_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.removeAllDownloads()
                        showRemoveDownloadsDialog = false
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(R.string.common_action_remove))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveDownloadsDialog = false }, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.common_action_cancel))
                }
            },
        )
    }

    if (showFormatSheet) {
        val formatHaptic = LocalHapticFeedback.current
        ModalBottomSheet(
            onDismissRequest = { showFormatSheet = false },
            sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden),
        ) {
            Text(
                text = stringResource(R.string.settings_download_format),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            AudioFormat.DOWNLOADABLE.forEach { format ->
                val isSelected = format.key == state.downloadFormat
                ListItem(
                    trailingContent = {
                        if (isSelected) {
                            Icon(
                                painter = painterResource(R.drawable.ic_check),
                                contentDescription = stringResource(R.string.common_cd_selected),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            formatHaptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.setDownloadFormat(format.key)
                            showFormatSheet = false
                        },
                ) {
                    Text(stringResource(format.displayNameRes))
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0),
    ) { scaffoldPadding ->

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding)
                .testTag(com.dustvalve.next.android.ui.TestTags.SETTINGS_LIST),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 10.dp),
        ) {
            // Title
            item {
                Text(
                    text = stringResource(R.string.settings_title),
                    style = MaterialTheme.typography.headlineMediumEmphasized,
                    modifier = Modifier
                        .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp)
                        .animateItem(),
                )
            }

            // Sources section
            item {
                val localContext = LocalContext.current
                val audioPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission(),
                ) { granted: Boolean ->
                    if (granted) {
                        viewModel.setLocalMusicUseMediaStore(true)
                    }
                }
                val folderPickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocumentTree(),
                ) { uri: Uri? ->
                    if (uri != null) {
                        val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                        try {
                            localContext.contentResolver.takePersistableUriPermission(uri, takeFlags)
                        } catch (_: Exception) { /* Best effort */ }
                        viewModel.addLocalMusicFolder(uri.toString())
                    }
                }

                val scanText = state.scanMessage?.asString()
                LaunchedEffect(scanText) {
                    scanText?.let { message ->
                        try {
                            snackbarHostState.showSnackbar(message)
                        } finally {
                            viewModel.clearScanMessage()
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
                                    viewModel.setLocalMusicEnabled(enabled)
                                    if (enabled && state.localMusicUseMediaStore) {
                                        audioPermissionLauncher.launch(Manifest.permission.READ_MEDIA_AUDIO)
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
                                                viewModel.setLocalMusicUseMediaStore(false)
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
                                                } catch (_: Exception) {
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
                                                            onClick = { viewModel.removeLocalMusicFolder(uri) },
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
                                                    onClick = { viewModel.rescanLocalMusic() },
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
                                                onClick = { viewModel.rescanLocalMusic() },
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
                                        onCheckedChange = { viewModel.setKeepLocalSort(it) },
                                        subRow = true,
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    SettingsToggleRow(
                                        title = stringResource(R.string.settings_local_keep_filters),
                                        description = stringResource(R.string.settings_local_keep_filters_desc),
                                        checked = state.keepLocalFilters,
                                        onCheckedChange = { viewModel.setKeepLocalFilters(it) },
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
                                onCheckedChange = { viewModel.setBandcampEnabled(it) },
                                switchTag = com.dustvalve.next.android.ui.TestTags.settingsSwitch("bandcamp"),
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // YouTube
                            SettingsToggleRow(
                                title = stringResource(R.string.settings_source_youtube),
                                icon = R.drawable.ic_play_circle,
                                checked = state.youtubeEnabled,
                                onCheckedChange = { viewModel.setYoutubeEnabled(it) },
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
                                                            if (isChecked) viewModel.setYoutubeDefaultSource(value)
                                                        },
                                                        modifier = Modifier.weight(1f),
                                                        shapes = when (index) {
                                                            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                                            ytSourceOptions.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
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
                        }
                    }
                }
            }

            // Connections section
            item {
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
                                    onClick = { viewModel.signOutBandcamp() },
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
                                    onClick = { viewModel.signOutYouTubeMusic() },
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

            // Storage section
            item {
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
                        } catch (_: Exception) { /* Best effort */ }
                        viewModel.enableDedicatedFolder(uri.toString())
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
                                    viewModel.disableDedicatedFolder()
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
                                onValueChange = { sliderIndex = it.toInt() },
                                onValueChangeFinished = {
                                    viewModel.setStorageLimit(storageLimitSteps[sliderIndex])
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
                                            } catch (_: Exception) {
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
                                        onCheckedChange = { viewModel.setDedicatedFolderIncludeImageCache(it) },
                                        subRow = true,
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    SettingsToggleRow(
                                        title = stringResource(R.string.settings_dedicated_folder_metadata_cache),
                                        description = stringResource(R.string.settings_dedicated_folder_metadata_cache_desc),
                                        checked = state.dedicatedFolderIncludeMetadataCache,
                                        enabled = !state.folderMigrationInProgress,
                                        onCheckedChange = { viewModel.setDedicatedFolderIncludeMetadataCache(it) },
                                        subRow = true,
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            FilledTonalButton(
                                onClick = { showRemoveDownloadsDialog = true },
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
                                    onCheckedChange = { viewModel.setAutoDownloadCollection(it) },
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            SettingsToggleRow(
                                title = stringResource(R.string.settings_auto_download_future),
                                description = stringResource(R.string.settings_auto_download_future_desc),
                                checked = state.autoDownloadFutureContent,
                                onCheckedChange = { viewModel.setAutoDownloadFutureContent(it) },
                            )

                            // Sub-toggle: Auto-download favorites - only shown when
                            // the parent "future content" toggle is on.
                            SettingsSubToggle(
                                visible = state.autoDownloadFutureContent,
                                title = stringResource(R.string.settings_auto_download_favorites),
                                description = stringResource(R.string.settings_auto_download_favorites_desc),
                                checked = state.autoDownloadFavorites,
                                onCheckedChange = { viewModel.setAutoDownloadFavorites(it) },
                            )

                            Spacer(modifier = Modifier.height(16.dp))
                            SettingsToggleRow(
                                title = stringResource(R.string.settings_download_notifications),
                                description = stringResource(R.string.settings_download_notifications_desc),
                                checked = state.downloadNotificationsEnabled,
                                onCheckedChange = { viewModel.setDownloadNotificationsEnabled(it) },
                            )

                            // Live Updates (status-bar chip) is a per-app runtime
                            // permission the user must grant; prompt + deep-link when off.
                            LiveUpdatesPromptRow()
                        }
                    }
                }
            }

            // Audio Quality section
            item {
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
                                onClick = { showFormatSheet = true },
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
                                onCheckedChange = { viewModel.setSaveDataOnMetered(it) },
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            SettingsToggleRow(
                                title = stringResource(R.string.settings_progressive_download),
                                description = stringResource(R.string.settings_progressive_download_desc),
                                checked = state.progressiveDownload,
                                onCheckedChange = { viewModel.setProgressiveDownload(it) },
                            )

                            // Dependent on progressive download - indented sub-toggle
                            // like every other dependent setting.
                            SettingsSubToggle(
                                visible = state.progressiveDownload,
                                title = stringResource(R.string.settings_seamless_upgrade),
                                description = stringResource(R.string.settings_seamless_upgrade_desc),
                                checked = state.seamlessQualityUpgrade,
                                onCheckedChange = { viewModel.setSeamlessQualityUpgrade(it) },
                            )
                        }
                    }
                }
            }

            // Appearance section
            item {
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
                                                    if (isChecked) viewModel.setThemeMode(mode)
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
                                onCheckedChange = { viewModel.setDynamicColor(it) },
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            SettingsToggleRow(
                                title = stringResource(R.string.settings_album_art_colors),
                                description = stringResource(R.string.settings_album_art_colors_desc),
                                checked = state.albumArtTheme,
                                onCheckedChange = { viewModel.setAlbumArtTheme(it) },
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
                                onCheckedChange = { viewModel.setOledBlack(it) },
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
                                                    onCheckedChange = { if (it) viewModel.setProgressBarStyle(opt) },
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
                                        viewModel.setProgressBarSizeDp(sizeSteps[sizeIndex])
                                    },
                                    valueRange = 0f..(sizeSteps.lastIndex).toFloat(),
                                    steps = sizeSteps.size - 2,
                                )
                            }
                        }
                    }
                }
            }

            // Player section
            item {
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
                                onCheckedChange = { viewModel.setShowInlineVolumeSlider(it) },
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            SettingsToggleRow(
                                title = stringResource(R.string.settings_volume_button),
                                description = stringResource(R.string.settings_volume_button_desc),
                                checked = state.showVolumeButton,
                                onCheckedChange = { viewModel.setShowVolumeButton(it) },
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            SettingsToggleRow(
                                title = stringResource(R.string.settings_keep_screen_open),
                                description = stringResource(R.string.settings_keep_screen_open_desc),
                                checked = state.keepScreenOnInApp,
                                onCheckedChange = { viewModel.setKeepScreenOnInApp(it) },
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
                                onCheckedChange = { viewModel.setKeepScreenOnWhilePlaying(it) },
                            )
                        }
                    }
                }
            }

            // Search section
            item {
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
                                onCheckedChange = { viewModel.setSearchHistoryEnabled(it) },
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
                                            onCheckedChange = { viewModel.setSearchHistorySource("bandcamp", it) },
                                        )
                                    }
                                    if (state.youtubeEnabled) {
                                        SearchHistorySourceRow(
                                            labelRes = R.string.settings_search_history_source_youtube,
                                            checked = state.searchHistoryYoutube,
                                            onCheckedChange = { viewModel.setSearchHistorySource("youtube", it) },
                                        )
                                    }
                                    if (state.localMusicEnabled) {
                                        SearchHistorySourceRow(
                                            labelRes = R.string.settings_search_history_source_local,
                                            checked = state.searchHistoryLocal,
                                            onCheckedChange = { viewModel.setSearchHistorySource("local", it) },
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            FilledTonalButton(
                                onClick = { viewModel.clearAllSearchHistory() },
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

            // About section
            item {
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
                                onClick = { viewModel.checkForAppUpdate() },
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
                                onCheckedChange = { viewModel.setAutoUpdateCheckEnabled(it) },
                            )
                        }
                    }
                }
            }

            // Debug section - hidden behind a single explicit toggle, off by
            // default. Kept at the very bottom so it doesn't clutter day-to-day
            // settings.
            item {
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
                                checked = state.albumCoverLongPressCarousel,
                                onCheckedChange = { viewModel.setAlbumCoverLongPressCarousel(it) },
                            )
                        }
                    }
                }
            }
        }

        // Update flow: Available -> confirm dialog; Downloading -> progress dialog
        AppUpdateDialog(
            state = state.updateState,
            onConfirmDownload = { viewModel.confirmAppUpdate() },
            onDismiss = { viewModel.dismissAppUpdate() },
        )

        if (state.folderMigrationInProgress) {
            com.dustvalve.next.android.ui.components.LoadingOverlay(
                title = stringResource(R.string.settings_dedicated_folder_migrating),
                progress = state.folderMigrationProgress,
                message = state.folderMigrationMessage?.asString(),
            )
        }
    } // end Scaffold
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SettingsSection(title: String, icon: Int, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(AppShapes.SettingsIcon)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        content()
    }
}

/**
 * Standard settings toggle row: optional leading icon, label column
 * (title + optional description), Switch pinned to the card edge.
 * [subRow] switches to the indented dependent-setting variant with the
 * smaller type scale. When [enabled] is false the texts dim along with
 * the switch.
 */
@Composable
private fun SettingsToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    enabled: Boolean = true,
    icon: Int? = null,
    subRow: Boolean = false,
    switchTag: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (subRow) Modifier.padding(start = SUB_TOGGLE_INDENT) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(12.dp))
        }
        val disabledAlpha = 0.38f
        Column(modifier = Modifier.weight(1f).padding(end = TOGGLE_LABEL_END_GAP)) {
            Text(
                text = title,
                style = if (subRow) {
                    MaterialTheme.typography.bodyMedium
                } else {
                    MaterialTheme.typography.titleSmall
                },
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = disabledAlpha)
                },
            )
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = disabledAlpha)
                    },
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            modifier = if (switchTag != null) Modifier.testTag(switchTag) else Modifier,
        )
    }
}

/**
 * Canonical dependent setting: an indented toggle row that animates in
 * below its parent when [visible] flips on.
 */
@Composable
private fun SettingsSubToggle(
    visible: Boolean,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    description: String? = null,
    enabled: Boolean = true,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        SettingsToggleRow(
            title = title,
            checked = checked,
            onCheckedChange = onCheckedChange,
            description = description,
            enabled = enabled,
            subRow = true,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
private fun SearchHistorySourceRow(labelRes: Int, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/**
 * Sub-row under the download-notifications toggle that appears only when the
 * per-app "Live Updates" permission is off (so the status-bar download chip
 * cannot show). Tapping the button deep-links to the system toggle. Re-checks
 * on every ON_RESUME so it disappears as soon as the user grants it.
 */
@Composable
private fun LiveUpdatesPromptRow() {
    val context = LocalContext.current
    var canPost by remember { mutableStateOf(canPostPromotedNotifications(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                canPost = canPostPromotedNotifications(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    AnimatedVisibility(
        visible = !canPost,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, start = SUB_TOGGLE_INDENT),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = TOGGLE_LABEL_END_GAP)) {
                Text(
                    text = stringResource(R.string.settings_live_updates_title),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(R.string.settings_live_updates_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalButton(
                onClick = { openLiveUpdatesSettings(context) },
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(R.string.settings_live_updates_action))
            }
        }
    }
}

@Suppress("TooGenericExceptionCaught", "SwallowedException")
private fun canPostPromotedNotifications(context: Context): Boolean = try {
    context.getSystemService(NotificationManager::class.java).canPostPromotedNotifications()
} catch (e: Throwable) {
    // API absent (pre-QPR1) - don't nag with a prompt that leads nowhere.
    true
}

@Suppress("TooGenericExceptionCaught", "SwallowedException")
private fun openLiveUpdatesSettings(context: Context) {
    // There is no dedicated promoted-notifications settings action in API 37;
    // the per-app "Live Updates" toggle lives in the app's notification
    // settings. Fall back to the app details page if that screen is unavailable.
    try {
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    } catch (e: Exception) {
        context.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                "package:${context.packageName}".toUri(),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
