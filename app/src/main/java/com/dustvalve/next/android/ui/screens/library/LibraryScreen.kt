package com.dustvalve.next.android.ui.screens.library

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
import com.dustvalve.next.android.ui.theme.segmentedItemShape
import com.dustvalve.next.android.ui.components.PlaylistEditSheet
import com.dustvalve.next.android.ui.components.ShapePickerSheet
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import com.dustvalve.next.android.R
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.dustvalve.next.android.domain.model.LibraryItem
import com.dustvalve.next.android.domain.model.Playlist
import com.dustvalve.next.android.ui.components.PlaylistListItem
import com.dustvalve.next.android.ui.screens.player.PlayerViewModel
import com.dustvalve.next.android.ui.theme.AppShapes
import com.dustvalve.next.android.ui.theme.resolveLibraryItemShape

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LibraryScreen(
    onAlbumClick: (String) -> Unit,
    onArtistClick: (String) -> Unit = {},
    onPlaylistClick: (String) -> Unit,
    playerViewModel: PlayerViewModel,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.error) {
        state.error?.let { error ->
            try {
                snackbarHostState.showSnackbar(error)
            } finally {
                viewModel.clearError()
            }
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
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(bottom = 80.dp),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.showCreateDialog() },
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_add),
                    contentDescription = "Create playlist",
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
                    title = "Delete Playlist",
                    message = "Delete \"${item.name}\"? This cannot be undone.",
                    confirmText = "Delete",
                    onDismiss = { viewModel.dismissDeleteDialog() },
                    onConfirm = { viewModel.deletePlaylist(item.playlist.id) },
                )
            }
            is LibraryItem.AlbumItem -> {
                DeleteLibraryItemDialog(
                    title = "Remove from Library",
                    message = "Remove \"${item.name}\" from library? This will also unfavorite it.",
                    confirmText = "Remove",
                    onDismiss = { viewModel.dismissDeleteDialog() },
                    onConfirm = { viewModel.deleteFavorite(item.favoriteId) },
                )
            }
            is LibraryItem.ArtistItem -> {
                DeleteLibraryItemDialog(
                    title = "Remove from Library",
                    message = "Remove \"${item.name}\" from library? This will also unfavorite it.",
                    confirmText = "Remove",
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
                text = "Library",
                style = MaterialTheme.typography.headlineMediumEmphasized,
                modifier = Modifier.padding(top = 16.dp, bottom = 12.dp),
            )
        }
        items(
            count = items.size,
            key = { items[it].id },
        ) { index ->
            val item = items[index]
            Surface(
                shape = segmentedItemShape(index, items.size),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.padding(bottom = 2.dp),
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

            // Pin/Unpin — available for all types
            ListItem(
                headlineContent = { Text(if (item.isPinned) "Unpin" else "Pin") },
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
            )

            when (item) {
                is LibraryItem.PlaylistItem -> {
                    val playlist = item.playlist
                    if (playlist.isEditable) {
                        ListItem(
                            headlineContent = { Text("Modify") },
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
                        )
                    }
                    if (playlist.isDeletable) {
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = "Delete",
                                    color = MaterialTheme.colorScheme.error,
                                )
                            },
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
                        )
                    }
                }
                is LibraryItem.AlbumItem, is LibraryItem.ArtistItem -> {
                    ListItem(
                        headlineContent = { Text("Change Shape") },
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
                    )
                    ListItem(
                        headlineContent = {
                            Text(
                                text = "Remove from Library",
                                color = MaterialTheme.colorScheme.error,
                            )
                        },
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
                    )
                }
            }
            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LibraryAlbumListItem(
    item: LibraryItem.AlbumItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMoreClick: () -> Unit,
) {
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
                onLongClick = onLongClick,
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
        headlineContent = {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (item.isPinned) {
                    Icon(
                        painter = painterResource(R.drawable.ic_push_pin),
                        contentDescription = "Pinned",
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
                    text = "Album · ${item.artist}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        trailingContent = {
            IconButton(onClick = onMoreClick) {
                Icon(
                    painter = painterResource(R.drawable.ic_more_vert),
                    contentDescription = "More options",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LibraryArtistListItem(
    item: LibraryItem.ArtistItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMoreClick: () -> Unit,
) {
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
                onLongClick = onLongClick,
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
        headlineContent = {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (item.isPinned) {
                    Icon(
                        painter = painterResource(R.drawable.ic_push_pin),
                        contentDescription = "Pinned",
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
                    text = "Artist",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        },
        trailingContent = {
            IconButton(onClick = onMoreClick) {
                Icon(
                    painter = painterResource(R.drawable.ic_more_vert),
                    contentDescription = "More options",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

@Composable
private fun DeleteLibraryItemDialog(
    title: String,
    message: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = confirmText,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EmptyState(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(AppShapes.EmptyStateIcon)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_music_note),
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Your library is empty",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Your playlists and favorites will appear here",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
            )
        }
    }
}
