package com.dustvalve.next.android.ui.screens.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.dustvalve.next.android.R
import com.dustvalve.next.android.domain.model.LibraryItem
import com.dustvalve.next.android.domain.model.Playlist
import com.dustvalve.next.android.ui.components.LoadingOverlay
import com.dustvalve.next.android.ui.components.PlaylistEditSheet
import com.dustvalve.next.android.ui.components.PlaylistListItem
import com.dustvalve.next.android.ui.components.ShapePickerSheet
import com.dustvalve.next.android.ui.components.lists.SegmentedListItem
import com.dustvalve.next.android.ui.theme.resolveLibraryItemShape

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LibraryScreen(
    onAlbumClick: (String) -> Unit,
    onPlaylistClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    onArtistClick: (String) -> Unit = {},
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    val errorText = state.error?.asString()
    LaunchedEffect(errorText) {
        errorText?.let { error ->
            try {
                snackbarHostState.showSnackbar(error)
            } finally {
                viewModel.clearError()
            }
        }
    }

    val messageText = state.message?.asString()
    LaunchedEffect(messageText) {
        messageText?.let { msg ->
            try {
                snackbarHostState.showSnackbar(msg)
            } finally {
                viewModel.clearMessage()
            }
        }
    }

    var fabExpanded by remember { mutableStateOf(false) }
    // Holds (playlist, offline) chosen in the export dialog until the SAF destination is picked.
    var pendingExport by remember { mutableStateOf<Pair<Playlist, Boolean>?>(null) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { viewModel.importPlaylist(it) } }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        val pending = pendingExport
        pendingExport = null
        if (uri != null && pending != null) {
            viewModel.exportPlaylist(pending.first, pending.second, uri)
        }
    }

    if (state.isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            ContainedLoadingIndicator()
        }
        return
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(bottom = 80.dp),
            )
        },
        floatingActionButton = {
            FloatingActionButtonMenu(
                expanded = fabExpanded,
                button = {
                    ToggleFloatingActionButton(
                        checked = fabExpanded,
                        onCheckedChange = { fabExpanded = it },
                    ) {
                        Icon(
                            painter = painterResource(if (fabExpanded) R.drawable.ic_close else R.drawable.ic_add),
                            contentDescription = stringResource(R.string.common_cd_create_playlist),
                        )
                    }
                },
            ) {
                FloatingActionButtonMenuItem(
                    onClick = {
                        fabExpanded = false
                        viewModel.showCreateDialog()
                    },
                    text = { Text(stringResource(R.string.library_new_playlist)) },
                    icon = {
                        Icon(painter = painterResource(R.drawable.ic_add), contentDescription = null)
                    },
                )
                FloatingActionButtonMenuItem(
                    onClick = {
                        fabExpanded = false
                        importLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
                    },
                    text = { Text(stringResource(R.string.library_import_playlist)) },
                    icon = {
                        Icon(painter = painterResource(R.drawable.ic_cloud_download), contentDescription = null)
                    },
                )
            }
        },
        contentWindowInsets = WindowInsets(0),
    ) { scaffoldPadding ->
        if (state.libraryItems.isEmpty()) {
            EmptyState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(scaffoldPadding),
            )
        } else {
            LibraryList(
                items = state.libraryItems,
                fullyDownloadedPlaylistIds = state.fullyDownloadedPlaylistIds,
                onPlaylistClick = onPlaylistClick,
                onAlbumClick = onAlbumClick,
                onArtistClick = onArtistClick,
                onPinPlaylist = { playlist ->
                    viewModel.pinPlaylist(playlist.id, !playlist.isPinned)
                },
                onPinFavorite = { item ->
                    val favoriteId = when (item) {
                        is LibraryItem.AlbumItem -> item.favoriteId
                        is LibraryItem.ArtistItem -> item.favoriteId
                        else -> return@LibraryList
                    }
                    viewModel.pinFavorite(favoriteId, !item.isPinned)
                },
                onRenameClick = { playlist -> viewModel.showRenameDialog(playlist) },
                onDeleteClick = { item -> viewModel.showDeleteDialog(item) },
                onChangeShapeClick = { item -> viewModel.showShapeDialog(item) },
                onExportClick = { playlist -> viewModel.requestExport(playlist) },
                modifier = Modifier.padding(scaffoldPadding),
            )
        }
    }

    // Create playlist sheet
    if (state.showCreateDialog) {
        PlaylistEditSheet(
            onDismiss = { viewModel.dismissCreateDialog() },
            onConfirm = { name, shapeKey, iconUrl ->
                viewModel.createPlaylist(name, shapeKey, iconUrl)
            },
            isCreate = true,
        )
    }

    // Edit playlist sheet (rename + shape + cover)
    state.renameTarget?.let { playlist ->
        PlaylistEditSheet(
            onDismiss = { viewModel.dismissRenameDialog() },
            onConfirm = { name, shapeKey, iconUrl ->
                viewModel.updatePlaylistAppearance(playlist.id, name, shapeKey, iconUrl)
            },
            initialName = playlist.name,
            initialShapeKey = playlist.shapeKey,
            initialIconUrl = playlist.iconUrl,
            tracks = state.renameTargetTracks,
            isCreate = false,
        )
    }

    // Delete / remove dialog
    state.deleteTarget?.let { item ->
        when (item) {
            is LibraryItem.PlaylistItem -> {
                DeleteLibraryItemDialog(
                    title = stringResource(R.string.library_delete_playlist_title),
                    message = stringResource(R.string.library_delete_playlist_text, item.name),
                    confirmText = stringResource(R.string.common_action_delete),
                    onDismiss = { viewModel.dismissDeleteDialog() },
                    onConfirm = { viewModel.deletePlaylist(item.playlist.id) },
                )
            }

            is LibraryItem.AlbumItem -> {
                DeleteLibraryItemDialog(
                    title = stringResource(R.string.library_remove_title),
                    message = stringResource(R.string.library_remove_text, item.name),
                    confirmText = stringResource(R.string.common_action_remove),
                    onDismiss = { viewModel.dismissDeleteDialog() },
                    onConfirm = { viewModel.deleteFavorite(item.favoriteId) },
                )
            }

            is LibraryItem.ArtistItem -> {
                DeleteLibraryItemDialog(
                    title = stringResource(R.string.library_remove_title),
                    message = stringResource(R.string.library_remove_text, item.name),
                    confirmText = stringResource(R.string.common_action_remove),
                    onDismiss = { viewModel.dismissDeleteDialog() },
                    onConfirm = { viewModel.deleteFavorite(item.favoriteId) },
                )
            }
        }
    }

    // Shape picker for albums/artists
    state.shapeTarget?.let { item ->
        val favoriteId = when (item) {
            is LibraryItem.AlbumItem -> item.favoriteId
            is LibraryItem.ArtistItem -> item.favoriteId
            else -> return@let
        }
        val defaultShapeKey = when (item) {
            is LibraryItem.AlbumItem -> "sunny"
            is LibraryItem.ArtistItem -> "arch"
        }
        ShapePickerSheet(
            onDismiss = { viewModel.dismissShapeDialog() },
            onConfirm = { shapeKey -> viewModel.updateFavoriteShape(favoriteId, shapeKey) },
            initialShapeKey = item.shapeKey ?: defaultShapeKey,
        )
    }

    // Export: choose offline (download everything) vs. lightweight (online references)
    state.exportTarget?.let { playlist ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissExport() },
            title = { Text(stringResource(R.string.library_export_title)) },
            text = { Text(stringResource(R.string.library_export_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingExport = playlist to true
                        viewModel.dismissExport()
                        exportLauncher.launch("${playlist.name}.dvplaylist")
                    },
                    shapes = ButtonDefaults.shapes(),
                ) { Text(stringResource(R.string.library_export_offline)) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        pendingExport = playlist to false
                        viewModel.dismissExport()
                        exportLauncher.launch("${playlist.name}.dvplaylist")
                    },
                    shapes = ButtonDefaults.shapes(),
                ) { Text(stringResource(R.string.library_export_lightweight)) }
            },
        )
    }

    // Export / import progress
    state.transfer?.let { progress ->
        val total = progress.total
        val fraction = if (total > 0) progress.done.toFloat() / total else 0f
        LoadingOverlay(
            title = stringResource(
                if (progress.importing) R.string.library_importing else R.string.library_exporting,
            ),
            progress = fraction,
            message = if (total > 0) stringResource(R.string.library_transfer_progress, progress.done, total) else null,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun LibraryList(
    items: List<LibraryItem>,
    fullyDownloadedPlaylistIds: Set<String>,
    onPlaylistClick: (String) -> Unit,
    onAlbumClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    onPinPlaylist: (Playlist) -> Unit,
    onPinFavorite: (LibraryItem) -> Unit,
    onRenameClick: (Playlist) -> Unit,
    onDeleteClick: (LibraryItem) -> Unit,
    onChangeShapeClick: (LibraryItem) -> Unit,
    onExportClick: (Playlist) -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuItem by remember { mutableStateOf<LibraryItem?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 0.dp,
            bottom = 160.dp,
        ),
    ) {
        item(key = "title") {
            Text(
                text = stringResource(R.string.library_title),
                style = MaterialTheme.typography.headlineMediumEmphasized,
                modifier = Modifier.padding(top = 16.dp, bottom = 12.dp),
            )
        }
        items(
            count = items.size,
            key = { items[it].id },
            contentType = { "library_item" },
        ) { index ->
            val item = items[index]
            SegmentedListItem(
                index = index,
                count = items.size,
                contentPadding = PaddingValues(bottom = 2.dp),
                modifier = Modifier.animateItem(
                    fadeInSpec = null,
                    fadeOutSpec = null,
                    placementSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                ),
            ) {
                when (item) {
                    is LibraryItem.PlaylistItem -> {
                        PlaylistListItem(
                            playlist = item.playlist,
                            onClick = { onPlaylistClick(item.playlist.id) },
                            isFullyDownloaded = item.playlist.id in fullyDownloadedPlaylistIds,
                            onLongClick = { menuItem = item },
                            onMoreClick = { menuItem = item },
                        )
                    }

                    is LibraryItem.AlbumItem -> {
                        LibraryAlbumListItem(
                            item = item,
                            onClick = { onAlbumClick(item.albumUrl) },
                            onLongClick = { menuItem = item },
                            onMoreClick = { menuItem = item },
                        )
                    }

                    is LibraryItem.ArtistItem -> {
                        LibraryArtistListItem(
                            item = item,
                            onClick = { onArtistClick(item.artistUrl) },
                            onLongClick = { menuItem = item },
                            onMoreClick = { menuItem = item },
                        )
                    }
                }
            }
        }
    }

    menuItem?.let { item ->
        ModalBottomSheet(
            onDismissRequest = { menuItem = null },
        ) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )

            // Pin/Unpin - available for all types
            ListItem(
                leadingContent = {
                    Icon(
                        painter = painterResource(R.drawable.ic_push_pin),
                        contentDescription = null,
                    )
                },
                modifier = Modifier.clickable {
                    menuItem = null
                    when (item) {
                        is LibraryItem.PlaylistItem -> onPinPlaylist(item.playlist)
                        else -> onPinFavorite(item)
                    }
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            ) {
                Text(if (item.isPinned) stringResource(R.string.library_unpin) else stringResource(R.string.library_pin))
            }

            when (item) {
                is LibraryItem.PlaylistItem -> {
                    val playlist = item.playlist
                    if (playlist.isEditable) {
                        ListItem(
                            leadingContent = {
                                Icon(
                                    painter = painterResource(R.drawable.ic_edit),
                                    contentDescription = null,
                                )
                            },
                            modifier = Modifier.clickable {
                                menuItem = null
                                onRenameClick(playlist)
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        ) {
                            Text(stringResource(R.string.library_modify))
                        }
                    }
                    ListItem(
                        leadingContent = {
                            Icon(
                                painter = painterResource(R.drawable.ic_cloud_download),
                                contentDescription = null,
                            )
                        },
                        modifier = Modifier.clickable {
                            menuItem = null
                            onExportClick(playlist)
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    ) {
                        Text(stringResource(R.string.library_export_playlist))
                    }
                    if (playlist.isDeletable) {
                        ListItem(
                            leadingContent = {
                                Icon(
                                    painter = painterResource(R.drawable.ic_delete),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            },
                            modifier = Modifier.clickable {
                                menuItem = null
                                onDeleteClick(item)
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        ) {
                            Text(
                                text = stringResource(R.string.common_action_delete),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }

                is LibraryItem.AlbumItem, is LibraryItem.ArtistItem -> {
                    ListItem(
                        leadingContent = {
                            Icon(
                                painter = painterResource(R.drawable.ic_palette),
                                contentDescription = null,
                            )
                        },
                        modifier = Modifier.clickable {
                            menuItem = null
                            onChangeShapeClick(item)
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    ) {
                        Text(stringResource(R.string.library_change_shape))
                    }
                    ListItem(
                        leadingContent = {
                            Icon(
                                painter = painterResource(R.drawable.ic_delete),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                        },
                        modifier = Modifier.clickable {
                            menuItem = null
                            onDeleteClick(item)
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    ) {
                        Text(
                            text = stringResource(R.string.library_remove_from_library),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LibraryAlbumListItem(item: LibraryItem.AlbumItem, onClick: () -> Unit, onLongClick: () -> Unit, onMoreClick: () -> Unit) {
    val hapticFeedback = LocalHapticFeedback.current
    val thumbnailShape = resolveLibraryItemShape(item.shapeKey, "album")

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "pressScale",
    )

    ListItem(
        modifier = Modifier
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick,
                onLongClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                },
            ),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(thumbnailShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = item.artUrl,
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(56.dp),
                )
            }
        },
        supportingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (item.isPinned) {
                    Icon(
                        painter = painterResource(R.drawable.ic_push_pin),
                        contentDescription = stringResource(R.string.playlist_cd_pinned),
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Icon(
                    painter = painterResource(R.drawable.ic_album),
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.library_album_label, item.artist),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        trailingContent = {
            IconButton(onClick = onMoreClick, shapes = IconButtonDefaults.shapes()) {
                Icon(
                    painter = painterResource(R.drawable.ic_more_vert),
                    contentDescription = stringResource(R.string.common_cd_more_options),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    ) {
        Text(
            text = item.name,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LibraryArtistListItem(item: LibraryItem.ArtistItem, onClick: () -> Unit, onLongClick: () -> Unit, onMoreClick: () -> Unit) {
    val hapticFeedback = LocalHapticFeedback.current
    val thumbnailShape = resolveLibraryItemShape(item.shapeKey, "artist")

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "pressScale",
    )

    ListItem(
        modifier = Modifier
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick,
                onLongClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                },
            ),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(thumbnailShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                if (item.imageUrl != null) {
                    AsyncImage(
                        model = item.imageUrl,
                        contentDescription = item.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(56.dp),
                    )
                } else {
                    Icon(
                        painter = painterResource(R.drawable.ic_person),
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        },
        supportingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (item.isPinned) {
                    Icon(
                        painter = painterResource(R.drawable.ic_push_pin),
                        contentDescription = stringResource(R.string.playlist_cd_pinned),
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Icon(
                    painter = painterResource(R.drawable.ic_person),
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.library_artist_label),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        },
        trailingContent = {
            IconButton(onClick = onMoreClick, shapes = IconButtonDefaults.shapes()) {
                Icon(
                    painter = painterResource(R.drawable.ic_more_vert),
                    contentDescription = stringResource(R.string.common_cd_more_options),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    ) {
        Text(
            text = item.name,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DeleteLibraryItemDialog(title: String, message: String, confirmText: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm, shapes = ButtonDefaults.shapes()) {
                Text(
                    text = confirmText,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.common_action_cancel))
            }
        },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        com.dustvalve.next.android.ui.components.EmptyState(
            icon = R.drawable.ic_music_note,
            title = stringResource(R.string.library_empty_title),
            subtitle = stringResource(R.string.library_empty_subtitle),
            modifier = Modifier.padding(horizontal = 32.dp),
        )
    }
}
