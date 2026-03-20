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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import android.os.Build
import androidx.core.net.toUri
import androidx.compose.foundation.clickable
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
    onLoginClick: () -> Unit,
    onDownloadsClick: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showClearCacheDialog by rememberSaveable { mutableStateOf(false) }
    var showRemoveDownloadsDialog by rememberSaveable { mutableStateOf(false) }
    var showFormatSheet by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.signOutSuccess) {
        if (state.signOutSuccess) {
            try {
                snackbarHostState.showSnackbar("Successfully disconnected")
            } finally {
                viewModel.clearSignOutSuccess()
            }
        }
    }

    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            title = { Text("Clear cache") },
            text = { Text("This will remove all cached audio and images. Downloaded tracks will not be affected.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearCache()
                        showClearCacheDialog = false
                    },
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (showRemoveDownloadsDialog) {
        AlertDialog(
            onDismissRequest = { showRemoveDownloadsDialog = false },
            title = { Text("Remove all downloads") },
            text = { Text("This will remove all downloaded tracks. Cached audio and images will not be affected.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.removeAllDownloads()
                        showRemoveDownloadsDialog = false
                    },
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveDownloadsDialog = false }) {
                    Text("Cancel")
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
                text = "Download Format",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            AudioFormat.DOWNLOADABLE.forEach { format ->
                val isSelected = format.key == state.downloadFormat
                ListItem(
                    headlineContent = { Text(format.displayName) },
                    trailingContent = {
                        if (isSelected) {
                            Icon(
                                painter = painterResource(R.drawable.ic_check),
                                contentDescription = "Selected",
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
                text = "Settings",
                style = MaterialTheme.typography.headlineMediumEmphasized,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp),
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

            LaunchedEffect(state.scanMessage) {
                val message = state.scanMessage
                if (message != null) {
                    try {
                        snackbarHostState.showSnackbar(message)
                    } finally {
                        viewModel.clearScanMessage()
                    }
                }
            }

            SettingsSection(
                title = "Sources",
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
                                    text = "Local",
                                    style = MaterialTheme.typography.titleSmall,
                                )
                            }
                            Switch(
                                checked = state.localMusicEnabled,
                                onCheckedChange = { enabled ->
                                    viewModel.setLocalMusicEnabled(enabled)
                                    if (enabled && state.localMusicUseMediaStore) {
                                        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            Manifest.permission.READ_MEDIA_AUDIO
                                        } else {
                                            Manifest.permission.READ_EXTERNAL_STORAGE
                                        }
                                        audioPermissionLauncher.launch(permission)
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
                                        text = "Use individual folders",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    Text(
                                        text = "Pick specific folders instead of scanning all audio",
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
                                            val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                                Manifest.permission.READ_MEDIA_AUDIO
                                            } else {
                                                Manifest.permission.READ_EXTERNAL_STORAGE
                                            }
                                            audioPermissionLauncher.launch(permission)
                                        }
                                    },
                                )
                            }

                            if (!state.localMusicUseMediaStore) {
                                // SAF folder mode
                                if (state.localMusicFolderUris.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    state.localMusicFolderUris.forEach { uri ->
                                        val folderName = try {
                                            uri.toUri().lastPathSegment
                                                ?.substringAfterLast(':')
                                                ?: "Selected folder"
                                        } catch (_: Exception) {
                                            "Selected folder"
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
                                                IconButton(onClick = { viewModel.removeLocalMusicFolder(uri) }) {
                                                    Icon(
                                                        painter = painterResource(R.drawable.ic_close),
                                                        contentDescription = "Remove folder",
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
                                        Text("Add folder")
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
                                            Text("Rescan")
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
                                        Text("Rescan")
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
                                    text = "Bandcamp",
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
                                    text = "YouTube",
                                    style = MaterialTheme.typography.titleSmall,
                                )
                            }
                            Switch(
                                checked = state.youtubeEnabled,
                                onCheckedChange = { viewModel.setYoutubeEnabled(it) },
                            )
                        }
                    }
                }
            }
        }

        // Account section
        item {
            SettingsSection(
                title = "Account",
                icon = R.drawable.ic_account_circle,
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        if (state.accountState.isLoggedIn) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (state.accountState.avatarUrl != null) {
                                    AsyncImage(
                                        model = state.accountState.avatarUrl,
                                        contentDescription = "Avatar",
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(AppShapes.Avatar),
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Connected as",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        text = state.accountState.username ?: "Unknown",
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedButton(
                                onClick = { viewModel.signOut() },
                                shapes = ButtonDefaults.shapes(),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Log out")
                            }
                        } else {
                            Text(
                                text = "Sign in to access your Dustvalve collection and purchases.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = onLoginClick,
                                shapes = ButtonDefaults.shapes(),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Sign in")
                            }
                        }
                    }
                }
            }
        }

        // Storage section
        item {
            SettingsSection(
                title = "Storage",
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
                            storageLimitSteps.lastIndex -> "Storage limit: Unlimited"
                            0 -> "Storage limit: 100 MB"
                            1 -> "Storage limit: 500 MB"
                            else -> "Storage limit: ${storageLimitSteps[sliderIndex].toInt()} GB"
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
                            Text("Manage downloads")
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
                            Text("Remove all downloads")
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
                            Text("Clear cache")
                        }

                        if (state.accountState.isLoggedIn) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Auto-download Collection",
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    Text(
                                        text = "Automatically download your Dustvalve purchases",
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
                                    text = "Auto-download future content",
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    text = "Automatically download new content for downloaded playlists, albums, and artists",
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
                    title = "Audio Quality",
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
                                text = "Download Format",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                text = "Format for downloaded collection items",
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
                                Text(currentFormat?.displayName ?: "FLAC (Lossless)")
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "MP3 only on metered",
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    Text(
                                        text = "Download MP3 instead of lossless on mobile data",
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
                                        text = "Progressive download",
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    Text(
                                        text = "Stream preview quality, then seamlessly upgrade to full quality",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Switch(
                                    checked = state.progressiveDownload,
                                    onCheckedChange = { viewModel.setProgressiveDownload(it) },
                                )
                            }
                        }
                    }
                }
            }

        // Appearance section
        item {
            SettingsSection(
                title = "Appearance",
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
                            text = "Theme",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        val themeOptions = listOf("light", "dark", "system")
                        val themeLabels = listOf("Light", "Dark", "System")
                        val selectedIndex = themeOptions.indexOf(state.themeMode).coerceAtLeast(0)

                        SingleChoiceSegmentedButtonRow(
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            themeOptions.forEachIndexed { index, mode ->
                                SegmentedButton(
                                    selected = index == selectedIndex,
                                    onClick = { viewModel.setThemeMode(mode) },
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = index,
                                        count = themeOptions.size,
                                    ),
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
                                    text = "Dynamic Color",
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    text = "Use colors from your wallpaper",
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
                                    text = "Album art colors",
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    text = "Theme the app using colors from the playing track's album art",
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
                                        text = "OLED Black",
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    Text(
                                        text = "Use pure black backgrounds for OLED screens",
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
                                    text = "Wavy progress bar",
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    text = "Animated wavy seek bar in the music player",
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
                title = "Player",
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
                                    text = "Volume slider",
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    text = "Show a volume slider next to album art in the music player",
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
                                    text = "Volume button",
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    text = "Show a volume button that opens a full-screen volume control",
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
                                    text = "Cover carousel",
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    text = "Long-press album cover to browse upcoming tracks (disabling shows debug info instead)",
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

        // Search section
        item {
            SettingsSection(
                title = "Search",
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
                                    text = "Search history",
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    text = "Save recent searches for quick access",
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
                title = "About",
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
                                text = "Version",
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
                            text = "Licensed under GPLv3",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
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
