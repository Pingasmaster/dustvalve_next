package com.dustvalve.next.android.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import kotlin.math.roundToInt

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

                            // Persistence sub-toggles for the Local tab's
                            // sort + filter chip selections. Independent so
                            // the user can keep one without the other.
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 36.dp, end = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.settings_local_keep_sort),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    Text(
                                        text = stringResource(R.string.settings_local_keep_sort_desc),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Switch(
                                    checked = state.keepLocalSort,
                                    onCheckedChange = { viewModel.setKeepLocalSort(it) },
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 36.dp, end = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.settings_local_keep_filters),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    Text(
                                        text = stringResource(R.string.settings_local_keep_filters_desc),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Switch(
                                    checked = state.keepLocalFilters,
                                    onCheckedChange = { viewModel.setKeepLocalFilters(it) },
                                )
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

                        if (state.youtubeEnabled) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Column(modifier = Modifier.padding(start = 36.dp)) {
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
                                ButtonGroup(
                                    overflowIndicator = {},
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
            // Tracks selected via the Export Tracks sheet; null while the user is
            // exporting the entire library (the "Export downloads" button).
            var pendingExportIds by remember { mutableStateOf<Set<String>?>(null) }
            var showExportSheet by remember { mutableStateOf(false) }
            val exportableTracks by viewModel.exportableTracks.collectAsStateWithLifecycle()
            val exportFolderPickerLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocumentTree(),
            ) { uri: Uri? ->
                if (uri != null) {
                    val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    try {
                        exportContext.contentResolver.takePersistableUriPermission(uri, takeFlags)
                    } catch (_: Exception) { /* Best effort */ }
                    val ids = pendingExportIds
                    if (ids != null) {
                        viewModel.exportSelectedDownloads(uri.toString(), ids)
                        pendingExportIds = null
                        showExportSheet = false
                    } else {
                        viewModel.exportDownloads(uri.toString())
                    }
                } else {
                    pendingExportIds = null
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
                            onClick = { showExportSheet = true },
                            shapes = ButtonDefaults.shapes(),
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.isExporting && exportableTracks.isNotEmpty(),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_folder_open),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.export_tracks_button))
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
                            Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
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

                        // Sub-toggle: Auto-download favorites — only shown when
                        // the parent "future content" toggle is on.
                        AnimatedVisibility(
                            visible = state.autoDownloadFutureContent,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically(),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp, start = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                                    Text(
                                        text = stringResource(R.string.settings_auto_download_favorites),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Text(
                                        text = stringResource(R.string.settings_auto_download_favorites_desc),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Switch(
                                    checked = state.autoDownloadFavorites,
                                    onCheckedChange = { viewModel.setAutoDownloadFavorites(it) },
                                )
                            }
                        }
                    }
                }
            }

            if (showExportSheet) {
                com.dustvalve.next.android.ui.components.sheet.ExportTracksSheet(
                    tracks = exportableTracks,
                    onDismiss = { showExportSheet = false },
                    onExport = { ids ->
                        if (ids.isNotEmpty()) {
                            pendingExportIds = ids
                            exportFolderPickerLauncher.launch(null)
                        }
                    },
                )
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
                            overflowIndicator = {},
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

                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = stringResource(R.string.settings_progress_bar_style),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            // Connected ButtonGroup [Wavy | Linear] — uses the same
                            // M3E ButtonGroup component as the theme selector above
                            // so the inter-button gap matches.
                            val styleOptions = listOf("wavy", "linear")
                            val styleLabels = listOf(
                                stringResource(R.string.settings_progress_bar_style_wavy),
                                stringResource(R.string.settings_progress_bar_style_linear),
                            )
                            ButtonGroup(
                                overflowIndicator = {},
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

                            // Size slider — discrete 4..32 dp in 4 dp steps.
                            val sizeSteps = listOf(4, 8, 12, 16, 20, 24, 28, 32)
                            var sizeIndex by remember(state.progressBarSizeDp) {
                                mutableIntStateOf(
                                    sizeSteps.indexOf(state.progressBarSizeDp).coerceAtLeast(0)
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
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

                        // Sub-toggle: only shown when the parent is on. When
                        // checked, restricts wake-lock to "app open AND
                        // playing". Defaults to true so the parent's
                        // first-flip yields the lower-impact behaviour.
                        AnimatedVisibility(
                            visible = state.keepScreenOnInApp,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically(),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp, start = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                                    Text(
                                        text = stringResource(R.string.settings_keep_screen_only_playing),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Text(
                                        text = stringResource(R.string.settings_keep_screen_only_playing_desc),
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

                        // Per-source sub-toggles, only when the global toggle is on
                        // and only for sources the user has enabled.
                        AnimatedVisibility(
                            visible = state.searchHistoryEnabled,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically(),
                        ) {
                            Column(modifier = Modifier.padding(top = 12.dp, start = 8.dp)) {
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
                                if (state.spotifyEnabled) {
                                    SearchHistorySourceRow(
                                        labelRes = R.string.settings_search_history_source_spotify,
                                        checked = state.searchHistorySpotify,
                                        onCheckedChange = { viewModel.setSearchHistorySource("spotify", it) },
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
                                    com.dustvalve.next.android.update.AppUpdateService.REPO_URL
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
                    }
                }
            }
        }

        // Debug section — hidden behind a single explicit toggle, off by
        // default. Kept at the very bottom so it doesn't clutter day-to-day
        // settings.
        item {
            SettingsSection(
                title = stringResource(R.string.settings_section_debug),
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
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.settings_show_debug_info),
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    text = stringResource(R.string.settings_show_debug_info_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = state.albumCoverLongPressCarousel,
                                onCheckedChange = { viewModel.setAlbumCoverLongPressCarousel(it) },
                            )
                        }
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

@Composable
private fun SearchHistorySourceRow(
    labelRes: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AppUpdateDialog(
    state: UpdateUiState,
    onConfirmDownload: () -> Unit,
    onDismiss: () -> Unit,
) {
    when (state) {
        is UpdateUiState.Available -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(stringResource(R.string.settings_update_available_title)) },
                text = {
                    Text(
                        stringResource(
                            R.string.settings_update_available_text,
                            state.versionName,
                        )
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = onConfirmDownload,
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Text(stringResource(R.string.settings_update_download_action))
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                        Text(stringResource(R.string.common_action_cancel))
                    }
                },
            )
        }
        is UpdateUiState.Downloading -> {
            AlertDialog(
                onDismissRequest = { /* not dismissable while downloading */ },
                title = {
                    Text(
                        stringResource(
                            R.string.settings_update_downloading,
                            state.versionName,
                        )
                    )
                },
                text = {
                    if (state.progress != null) {
                        LinearWavyProgressIndicator(
                            progress = { state.progress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        // Indeterminate when the server didn't send Content-Length.
                        LinearWavyProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                },
                confirmButton = {},
            )
        }
        else -> Unit
    }
}
