package com.dustvalve.next.android.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.dustvalve.next.android.R
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi as M3E
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import coil3.compose.AsyncImage
import com.dustvalve.next.android.domain.model.AudioFormat
import androidx.compose.ui.platform.LocalContext
import com.dustvalve.next.android.ui.components.StorageIndicator
import com.dustvalve.next.android.ui.theme.AppShapes

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(
    onBandcampLoginClick: () -> Unit,
    onYouTubeMusicLoginClick: () -> Unit,
    onSpotifyLoginClick: () -> Unit = {},
    onDownloadsClick: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showClearCacheDialog by rememberSaveable { mutableStateOf(false) }
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

    val exportText = state.exportMessage?.asString()
    LaunchedEffect(exportText) {
        exportText?.let { message ->
            try {
                snackbarHostState.showSnackbar(message)
            } finally {
                viewModel.clearExportMessage()
            }
        }
    }

    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            title = { Text(stringResource(R.string.settings_clear_cache_title)) },
            text = { Text(stringResource(R.string.settings_clear_cache_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearCache()
                        showClearCacheDialog = false
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(R.string.common_action_clear))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheDialog = false }, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.common_action_cancel))
                }
            },
        )
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
            sheetState = rememberModalBottomSheetState(),
        ) {
            Text(
                text = stringResource(R.string.settings_download_format),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            AudioFormat.DOWNLOADABLE.forEach { format ->
                val isSelected = format.key == state.downloadFormat
                ListItem(
                    headlineContent = { Text(stringResource(format.displayNameRes)) },
                    trailingContent = {
                        if (isSelected) {
                            Icon(
                                painter = painterResource(R.drawable.ic_check),
                                contentDescription = stringResource(R.string.common_cd_selected),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    },
                    colors = if (isSelected) {
                        ListItemDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                        )
                    } else {
                        ListItemDefaults.colors()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .clickable {
                            formatHaptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.setDownloadFormat(format.key)
                            showFormatSheet = false
                        },
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0),
    ) { scaffoldPadding ->

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(scaffoldPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
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

            var showSpotifySourceWarning by rememberSaveable { mutableStateOf(false) }

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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_phone_android),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.settings_source_local),
                                    style = MaterialTheme.typography.titleSmall,
                                )
                            }
                            Switch(
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
                        }

                        if (state.localMusicEnabled) {
                            // "Scan all audio" toggle
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 36.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.settings_use_individual_folders),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    Text(
                                        text = stringResource(R.string.settings_use_individual_folders_desc),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Switch(
                                    checked = !state.localMusicUseMediaStore,
                                    onCheckedChange = { useIndividualFolders ->
                                        if (useIndividualFolders) {
                                            viewModel.setLocalMusicUseMediaStore(false)
                                        } else {
                                            audioPermissionLauncher.launch(Manifest.permission.READ_MEDIA_AUDIO)
                                        }
                                    },
                                )
                            }

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
                                            headlineContent = {
                                                Text(
                                                    text = folderName,
                                                    style = MaterialTheme.typography.bodySmall,
                                                )
                                            },
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
                                            modifier = Modifier.padding(start = 20.dp),
                                            colors = ListItemDefaults.colors(
                                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                            ),
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 36.dp),
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
                                // MediaStore mode — just show Rescan button
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 36.dp),
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

                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Bandcamp
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
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.settings_source_bandcamp),
                                    style = MaterialTheme.typography.titleSmall,
                                )
                            }
                            Switch(
                                checked = state.bandcampEnabled,
                                onCheckedChange = { viewModel.setBandcampEnabled(it) },
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // YouTube
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
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.settings_source_youtube),
                                    style = MaterialTheme.typography.titleSmall,
                                )
                            }
                            Switch(
                                checked = state.youtubeEnabled,
                                onCheckedChange = { viewModel.setYoutubeEnabled(it) },
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_spotify),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.settings_source_spotify),
                                    style = MaterialTheme.typography.titleSmall,
                                )
                            }
                            Switch(
                                checked = state.spotifyEnabled,
                                onCheckedChange = { enabled ->
                                    if (enabled) {
                                        showSpotifySourceWarning = true
                                    } else {
                                        viewModel.setSpotifyEnabled(false)
                                    }
                                },
                            )
                        }
                    }
                }
            }

            if (showSpotifySourceWarning) {
                AlertDialog(
                    onDismissRequest = { showSpotifySourceWarning = false },
                    title = { Text(stringResource(R.string.settings_spotify_premium_title)) },
                    text = {
                        Text(stringResource(R.string.settings_spotify_premium_text))
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showSpotifySourceWarning = false
                                viewModel.setSpotifyEnabled(true)
                            },
                            shapes = ButtonDefaults.shapes(),
                        ) {
                            Text(stringResource(R.string.common_action_enable))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showSpotifySourceWarning = false }, shapes = ButtonDefaults.shapes()) {
                            Text(stringResource(R.string.common_action_cancel))
                        }
                    },
                )
            }
        }

        // Connections section
        item {
            var showSpotifyWarning by rememberSaveable { mutableStateOf(false) }

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

                        Spacer(modifier = Modifier.height(20.dp))

                        // Spotify connection
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_spotify),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = stringResource(R.string.settings_source_spotify),
                                style = MaterialTheme.typography.titleSmall,
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        if (state.spotifyConnected) {
                            Text(
                                text = stringResource(R.string.settings_spotify_connected),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = { viewModel.disconnectSpotify() },
                                shapes = ButtonDefaults.shapes(),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(stringResource(R.string.common_action_disconnect))
                            }
                        } else {
                            Text(
                                text = stringResource(R.string.settings_connect_spotify_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { showSpotifyWarning = true },
                                shapes = ButtonDefaults.shapes(),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(stringResource(R.string.settings_connect_spotify))
                            }
                        }
                    }
                }
            }

            if (showSpotifyWarning) {
                AlertDialog(
                    onDismissRequest = { showSpotifyWarning = false },
                    title = { Text(stringResource(R.string.settings_spotify_warning_title)) },
                    text = {
                        Text(stringResource(R.string.settings_spotify_warning_text))
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showSpotifyWarning = false
                                onSpotifyLoginClick()
                            },
                            shapes = ButtonDefaults.shapes(),
                        ) {
                            Text(stringResource(R.string.settings_i_understand))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showSpotifyWarning = false }, shapes = ButtonDefaults.shapes()) {
                            Text(stringResource(R.string.common_action_cancel))
                        }
                    },
                )
            }
        }

        // Storage section
        item {
            val exportContext = LocalContext.current
            val exportFolderPickerLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocumentTree(),
            ) { uri: Uri? ->
                if (uri != null) {
                    val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    try {
                        exportContext.contentResolver.takePersistableUriPermission(uri, takeFlags)
                    } catch (_: Exception) { /* Best effort */ }
                    viewModel.exportDownloads(uri.toString())
                }
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

                        Spacer(modifier = Modifier.height(8.dp))
                        FilledTonalButton(
                            onClick = onDownloadsClick,
                            shapes = ButtonDefaults.shapes(),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_cloud_download),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.settings_manage_downloads))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        FilledTonalButton(
                            onClick = { exportFolderPickerLauncher.launch(null) },
                            shapes = ButtonDefaults.shapes(),
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.isExporting,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_folder_open),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.settings_export_downloads))
                        }
                        if (state.isExporting) {
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearWavyProgressIndicator(
                                progress = { state.exportProgress },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
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
                        Spacer(modifier = Modifier.height(8.dp))
                        FilledTonalButton(
                            onClick = { showClearCacheDialog = true },
                            shapes = ButtonDefaults.shapes(),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_delete_sweep),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.settings_clear_cache))
                        }

                        if (state.accountState.isLoggedIn) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.settings_auto_download_purchases),
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    Text(
                                        text = stringResource(R.string.settings_auto_download_purchases_desc),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Switch(
                                    checked = state.autoDownloadCollection,
                                    onCheckedChange = { viewModel.setAutoDownloadCollection(it) },
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.settings_auto_download_future),
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    text = stringResource(R.string.settings_auto_download_future_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = state.autoDownloadFutureContent,
                                onCheckedChange = { viewModel.setAutoDownloadFutureContent(it) },
                            )
                        }
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

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.settings_mp3_on_metered),
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    Text(
                                        text = stringResource(R.string.settings_mp3_on_metered_desc),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Switch(
                                    checked = state.saveDataOnMetered,
                                    onCheckedChange = { viewModel.setSaveDataOnMetered(it) },
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.settings_progressive_download),
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    Text(
                                        text = stringResource(R.string.settings_progressive_download_desc),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Switch(
                                    checked = state.progressiveDownload,
                                    onCheckedChange = { viewModel.setProgressiveDownload(it) },
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.settings_seamless_upgrade),
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    Text(
                                        text = stringResource(R.string.settings_seamless_upgrade_desc),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Switch(
                                    checked = state.seamlessQualityUpgrade,
                                    onCheckedChange = { viewModel.setSeamlessQualityUpgrade(it) },
                                    enabled = state.progressiveDownload,
                                )
                            }
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

                        ButtonGroup(
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            themeOptions.forEachIndexed { index, mode ->
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
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.settings_dynamic_color),
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    text = stringResource(R.string.settings_dynamic_color_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = state.dynamicColor,
                                onCheckedChange = { viewModel.setDynamicColor(it) },
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.settings_album_art_colors),
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    text = stringResource(R.string.settings_album_art_colors_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = state.albumArtTheme,
                                onCheckedChange = { viewModel.setAlbumArtTheme(it) },
                            )
                        }

                        val isDarkEffective = when (state.themeMode) {
                            "dark" -> true
                            "light" -> false
                            else -> isSystemInDarkTheme()
                        }

                        if (isDarkEffective) {
                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.settings_oled_black),
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    Text(
                                        text = stringResource(R.string.settings_oled_black_desc),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Switch(
                                    checked = state.oledBlack,
                                    onCheckedChange = { viewModel.setOledBlack(it) },
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.settings_wavy_progress),
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    text = stringResource(R.string.settings_wavy_progress_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = state.wavyProgressBar,
                                onCheckedChange = { viewModel.setWavyProgressBar(it) },
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.settings_volume_slider),
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    text = stringResource(R.string.settings_volume_slider_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = state.showInlineVolumeSlider,
                                onCheckedChange = { viewModel.setShowInlineVolumeSlider(it) },
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.settings_volume_button),
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    text = stringResource(R.string.settings_volume_button_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = state.showVolumeButton,
                                onCheckedChange = { viewModel.setShowVolumeButton(it) },
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.settings_cover_carousel),
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    text = stringResource(R.string.settings_cover_carousel_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = state.albumCoverLongPressCarousel,
                                onCheckedChange = { viewModel.setAlbumCoverLongPressCarousel(it) },
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.settings_keep_screen_open),
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    text = stringResource(R.string.settings_keep_screen_open_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = state.keepScreenOnInApp,
                                onCheckedChange = { viewModel.setKeepScreenOnInApp(it) },
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.settings_keep_screen_playing),
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    text = stringResource(R.string.settings_keep_screen_playing_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = state.keepScreenOnWhilePlaying,
                                onCheckedChange = { viewModel.setKeepScreenOnWhilePlaying(it) },
                            )
                        }
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.settings_search_history),
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    text = stringResource(R.string.settings_search_history_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = state.searchHistoryEnabled,
                                onCheckedChange = { viewModel.setSearchHistoryEnabled(it) },
                            )
                        }
                    }
                }
            }
        }

        // About section
        item {
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
                        Spacer(modifier = Modifier.height(8.dp))
                        val uriHandler = LocalUriHandler.current
                        Text(
                            text = stringResource(R.string.settings_github),
                            style = MaterialTheme.typography.bodySmall.copy(
                                textDecoration = TextDecoration.Underline,
                            ),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable {
                                uriHandler.openUri("https://github.com/Pingasmaster/dustvalve_next")
                            },
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(80.dp))
        }

    }

    } // end Scaffold
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SettingsSection(
    title: String,
    icon: Int,
    content: @Composable () -> Unit,
) {
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
