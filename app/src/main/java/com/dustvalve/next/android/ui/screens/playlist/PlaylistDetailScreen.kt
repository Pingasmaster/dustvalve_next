package com.dustvalve.next.android.ui.screens.playlist

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import com.dustvalve.next.android.ui.components.getPlaylistIconRes
import com.dustvalve.next.android.ui.theme.segmentedItemShape
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.dustvalve.next.android.R
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.dustvalve.next.android.domain.model.Playlist
import com.dustvalve.next.android.domain.model.Track
import com.dustvalve.next.android.ui.components.TrackArtPlaceholder
import com.dustvalve.next.android.ui.screens.player.PlayerViewModel
import kotlinx.coroutines.launch
import com.dustvalve.next.android.ui.theme.AppShapes
import com.dustvalve.next.android.ui.theme.resolvePlaylistShape
import com.dustvalve.next.android.util.TimeUtils

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlaylistDetailScreen(
    playlistId: String,
    onBack: () -> Unit,
    onAlbumClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    playerViewModel: PlayerViewModel,
    viewModel: PlaylistDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val playerState by playerViewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    LaunchedEffect(playlistId) {
        viewModel.loadPlaylist(playlistId)
    }

    LaunchedEffect(state.error) {
        state.error?.let { error ->
            try {
                snackbarHostState.showSnackbar(error)
            } finally {
                viewModel.clearError()
            }
        }
    }

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let { message ->
            try {
                val result = snackbarHostState.showSnackbar(
                    message = message,
                    actionLabel = if (state.isSnackbarError) "Retry" else null,
                )
                if (result == SnackbarResult.ActionPerformed) {
                    viewModel.retryAction?.invoke()
                }
            } finally {
                viewModel.clearSnackbar()
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.playlist?.name ?: "Playlist",
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = "Back",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                ),
                windowInsets = WindowInsets(0),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0),
    ) { paddingValues ->
        when {
            state.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.material3.ContainedLoadingIndicator()
                }
            }
            state.error != null && state.playlist == null -> {
                ErrorState(
                    message = state.error ?: "Failed to load playlist",
                    onRetry = { viewModel.refreshPlaylist() },
                    modifier = Modifier.padding(paddingValues),
                )
            }
            state.playlist != null -> {
                PlaylistContent(
                    playlist = state.playlist!!,
                    tracks = state.tracks,
                    currentTrackId = playerState.currentTrack?.id,
                    isPlaying = playerState.isPlaying,
                    isDownloading = state.isDownloading,
                    downloadedTrackIds = state.downloadedTrackIds,
                    onTrackClick = { tracks, index ->
                        playerViewModel.playTrackInList(tracks, index)
                    },
                    onMoveTrack = { from, to ->
                        viewModel.moveTrack(from, to)
                    },
                    onPlayAll = {
                        if (state.tracks.isNotEmpty()) {
                            playerViewModel.playTrackInList(state.tracks, 0)
                        }
                    },
                    onShufflePlay = {
                        if (state.tracks.isNotEmpty()) {
                            playerViewModel.playTrackInList(state.tracks.shuffled(), 0)
                        }
                    },
                    onDownloadAll = { viewModel.downloadAll() },
                    onRemoveTrack = { trackId -> viewModel.removeTrack(trackId) },
                    listState = listState,
                    modifier = Modifier.padding(paddingValues),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PlaylistContent(
    playlist: Playlist,
    tracks: List<Track>,
    currentTrackId: String?,
    isPlaying: Boolean,
    isDownloading: Boolean,
    downloadedTrackIds: Set<String>,
    onTrackClick: (List<Track>, Int) -> Unit,
    onMoveTrack: (Int, Int) -> Unit,
    onPlayAll: () -> Unit,
    onShufflePlay: () -> Unit,
    onDownloadAll: () -> Unit,
    onRemoveTrack: (String) -> Unit,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    val hapticFeedback = LocalHapticFeedback.current
    var draggedIndex by remember { mutableIntStateOf(-1) }
    var dragStartIndex by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val itemHeights = remember { mutableMapOf<Int, Float>() }
    var droppingItemKey by remember { mutableStateOf<String?>(null) }
    val dropAnimOffset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val dropSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    // Only update from Flow when not actively dragging to prevent state corruption
    val reorderableTracks = remember { tracks.toMutableStateList() }
    LaunchedEffect(tracks) {
        if (draggedIndex == -1) {
            reorderableTracks.clear()
            reorderableTracks.addAll(tracks)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 80.dp),
    ) {
        // Header with playlist icon and actions
        item(key = "header") {
            PlaylistHeader(
                playlist = playlist,
                tracks = reorderableTracks,
                isDownloading = isDownloading,
                downloadedTrackIds = downloadedTrackIds,
                onPlayAll = onPlayAll,
                onShufflePlay = onShufflePlay,
                onDownloadAll = onDownloadAll,
            )
        }

        // Tracks
        if (reorderableTracks.isEmpty()) {
            item(key = "empty") {
                EmptyPlaylistState(
                    isSystem = playlist.isSystem,
                    modifier = Modifier.fillParentMaxSize(),
                )
            }
        } else {
            items(
                count = reorderableTracks.size,
                key = { reorderableTracks[it].id },
            ) { index ->
                val track = reorderableTracks[index]
                val isCurrentTrack = currentTrackId == track.id
                val isTrackPlaying = isCurrentTrack && isPlaying
                val isDragging = draggedIndex == index

                val interactionSource = remember { MutableInteractionSource() }
                val isPressed by interactionSource.collectIsPressedAsState()
                val pressScale by animateFloatAsState(
                    targetValue = if (isPressed) 0.97f else 1f,
                    animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                    label = "pressScale",
                )

                val elevation by animateDpAsState(
                    targetValue = if (isDragging) 2.dp else 0.dp,
                    animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                    label = "dragElevation",
                )
                val containerColor by animateColorAsState(
                    targetValue = if (isDragging) {
                        MaterialTheme.colorScheme.surfaceContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    },
                    animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                    label = "dragColor",
                )
                val itemShape = if (isDragging) {
                    MaterialTheme.shapes.large
                } else {
                    segmentedItemShape(index, reorderableTracks.size)
                }

                val dismissState = rememberSwipeToDismissBoxState(
                    confirmValueChange = { value ->
                        if (value == SwipeToDismissBoxValue.EndToStart) {
                            onRemoveTrack(track.id)
                            true
                        } else false
                    },
                )
                SwipeToDismissBox(
                    state = dismissState,
                    modifier = Modifier
                        .padding(
                            start = 16.dp,
                            end = 16.dp,
                            top = if (index == 0) 8.dp else 1.dp,
                            bottom = if (index == reorderableTracks.lastIndex) 0.dp else 1.dp,
                        )
                        .animateItem(
                            fadeInSpec = null,
                            fadeOutSpec = null,
                            placementSpec = if (isDragging || track.id == droppingItemKey) null
                                else MaterialTheme.motionScheme.defaultSpatialSpec(),
                        )
                        .zIndex(if (isDragging || track.id == droppingItemKey) 1f else 0f),
                    backgroundContent = {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(itemShape)
                                .background(MaterialTheme.colorScheme.errorContainer)
                                .padding(horizontal = 20.dp),
                            contentAlignment = Alignment.CenterEnd,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_delete),
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    },
                    enableDismissFromStartToEnd = false,
                ) {
                    Surface(
                        onClick = { onTrackClick(reorderableTracks.toList(), index) },
                        interactionSource = interactionSource,
                        modifier = Modifier
                            .graphicsLayer {
                                scaleX = pressScale
                                scaleY = pressScale
                                if (isDragging) {
                                    translationY = dragOffset
                                    shadowElevation = elevation.toPx()
                                } else if (track.id == droppingItemKey) {
                                    translationY = dropAnimOffset.value
                                }
                            }
                            .onGloballyPositioned { coords ->
                                itemHeights[index] = coords.size.height.toFloat()
                            },
                        shadowElevation = if (!isDragging) elevation else 0.dp,
                        shape = itemShape,
                        color = containerColor,
                    ) {
                        TrackListItem(
                            track = track,
                            isPlaying = isTrackPlaying,
                            isCurrentTrack = isCurrentTrack,
                            onDragStart = {
                                droppingItemKey = null
                                scope.launch { dropAnimOffset.snapTo(0f) }
                                draggedIndex = index
                                dragStartIndex = index
                                dragOffset = 0f
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onDrag = { delta ->
                                dragOffset += delta
                                val ci = draggedIndex
                                if (ci < 0) return@TrackListItem
                                val h = itemHeights[ci] ?: return@TrackListItem
                                if (dragOffset > h * 0.6f && ci < reorderableTracks.lastIndex) {
                                    val item = reorderableTracks.removeAt(ci)
                                    reorderableTracks.add(ci + 1, item)
                                    draggedIndex = ci + 1
                                    dragOffset -= h
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                } else if (dragOffset < -h * 0.6f && ci > 0) {
                                    val item = reorderableTracks.removeAt(ci)
                                    reorderableTracks.add(ci - 1, item)
                                    draggedIndex = ci - 1
                                    dragOffset += h
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                            },
                            onDragEnd = {
                                val from = dragStartIndex
                                val to = draggedIndex
                                val finalOffset = dragOffset
                                val droppedKey = if (to in reorderableTracks.indices) reorderableTracks[to].id else null

                                draggedIndex = -1
                                dragStartIndex = -1
                                dragOffset = 0f

                                if (from >= 0 && to >= 0 && from != to) {
                                    onMoveTrack(from, to)
                                }

                                if (droppedKey != null && kotlin.math.abs(finalOffset) > 1f) {
                                    droppingItemKey = droppedKey
                                    scope.launch {
                                        dropAnimOffset.snapTo(finalOffset)
                                        dropAnimOffset.animateTo(0f, dropSpec)
                                        droppingItemKey = null
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PlaylistHeader(
    playlist: Playlist,
    tracks: List<Track>,
    isDownloading: Boolean,
    downloadedTrackIds: Set<String>,
    onPlayAll: () -> Unit,
    onShufflePlay: () -> Unit,
    onDownloadAll: () -> Unit,
) {
    val trackCount = tracks.size
    val allTracksLocal = tracks.isNotEmpty() && tracks.all { it.isLocal }
    val allTracksDownloaded = tracks.isNotEmpty() &&
        tracks.all { it.id in downloadedTrackIds || it.isLocal }
    val showDownloadButton = playlist.systemType != Playlist.SystemPlaylistType.DOWNLOADS &&
        playlist.systemType != Playlist.SystemPlaylistType.RECENT &&
        playlist.systemType != Playlist.SystemPlaylistType.LOCAL

    val thumbnailShape = when (playlist.systemType) {
        Playlist.SystemPlaylistType.FAVORITES -> AppShapes.PlaylistFavorites
        Playlist.SystemPlaylistType.DOWNLOADS -> AppShapes.PlaylistDownloads
        Playlist.SystemPlaylistType.RECENT -> AppShapes.PlaylistRecent
        Playlist.SystemPlaylistType.COLLECTION -> AppShapes.PlaylistCollection
        Playlist.SystemPlaylistType.LOCAL -> AppShapes.PlaylistLocal
        else -> resolvePlaylistShape(playlist.shapeKey)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        // Playlist icon
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(thumbnailShape)
                .background(
                    when {
                        playlist.isSystem -> MaterialTheme.colorScheme.primaryContainer
                        else -> MaterialTheme.colorScheme.secondaryContainer
                    }
                )
                .align(Alignment.CenterHorizontally),
            contentAlignment = Alignment.Center,
        ) {
            if (playlist.iconUrl != null) {
                AsyncImage(
                    model = playlist.iconUrl,
                    contentDescription = playlist.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(120.dp),
                )
            } else {
                Icon(
                    painter = painterResource(getPlaylistIconRes(playlist)),
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = when {
                        playlist.isSystem -> MaterialTheme.colorScheme.onPrimaryContainer
                        else -> MaterialTheme.colorScheme.onSecondaryContainer
                    },
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Playlist name
        Text(
            text = playlist.name,
            style = MaterialTheme.typography.headlineMediumEmphasized,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        // Subtitle
        val subtitle = when {
            playlist.isSystem -> "Auto playlist \u00B7 $trackCount ${if (trackCount == 1) "song" else "songs"}"
            else -> "$trackCount ${if (trackCount == 1) "song" else "songs"}"
        }
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        ) {
            FilledIconButton(
                onClick = onPlayAll,
                shapes = IconButtonDefaults.shapes(),
                enabled = trackCount > 0,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_play_arrow),
                    contentDescription = "Play all",
                )
            }

            FilledTonalIconButton(
                onClick = onShufflePlay,
                shapes = IconButtonDefaults.shapes(),
                enabled = trackCount > 0,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_shuffle),
                    contentDescription = "Shuffle play",
                )
            }

            if (showDownloadButton) {
                FilledTonalIconButton(
                    onClick = onDownloadAll,
                    shapes = IconButtonDefaults.shapes(),
                    enabled = trackCount > 0 && !isDownloading && !allTracksDownloaded,
                ) {
                    if (isDownloading) {
                        CircularWavyProgressIndicator(
                            modifier = Modifier.size(20.dp),
                        )
                    } else {
                        Icon(
                            painter = painterResource(if (allTracksDownloaded) R.drawable.ic_download_done
                                else R.drawable.ic_download),
                            contentDescription = if (allTracksDownloaded) "All downloaded"
                                else "Download all",
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TrackListItem(
    track: Track,
    isPlaying: Boolean,
    isCurrentTrack: Boolean,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)

    ListItem(
        modifier = Modifier
            .fillMaxWidth(),
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
        ),
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(MaterialTheme.shapes.small),
            ) {
                if (track.artUrl.isNotBlank()) {
                    AsyncImage(
                        model = track.artUrl,
                        contentDescription = track.albumTitle,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    TrackArtPlaceholder(modifier = Modifier.fillMaxSize())
                }
                if (isPlaying) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_graphic_eq),
                            contentDescription = "Now playing",
                            modifier = Modifier.size(22.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        },
        headlineContent = {
            Text(
                text = track.title,
                style = if (isCurrentTrack) {
                    MaterialTheme.typography.titleMediumEmphasized
                } else {
                    MaterialTheme.typography.titleMedium
                },
                color = if (isCurrentTrack) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Text(
                text = "${track.artist} · ${track.albumTitle}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .pointerInput(Unit) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { currentOnDragStart() },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                currentOnDrag(dragAmount.y)
                            },
                            onDragEnd = { currentOnDragEnd() },
                            onDragCancel = { currentOnDragEnd() },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_drag_handle),
                    contentDescription = "Reorder",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
            }
        },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EmptyPlaylistState(
    isSystem: Boolean,
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
                text = if (isSystem) "No tracks yet" else "Empty playlist",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (isSystem) {
                    "Tracks will appear here automatically"
                } else {
                    "Add songs to get started"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp),
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(modifier = Modifier.height(16.dp))
            androidx.compose.material3.TextButton(onClick = onRetry) {
                Text("Retry")
            }
        }
    }
}