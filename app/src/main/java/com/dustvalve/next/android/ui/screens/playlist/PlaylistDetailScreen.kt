package com.dustvalve.next.android.ui.screens.playlist

import androidx.compose.foundation.background
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
import com.dustvalve.next.android.ui.components.lists.MusicRow
import com.dustvalve.next.android.ui.components.lists.ReorderableMusicList
import com.dustvalve.next.android.ui.components.lists.SegmentedListItem
import com.dustvalve.next.android.ui.theme.segmentedItemShape
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ContainedLoadingIndicator
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dustvalve.next.android.R
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.dustvalve.next.android.domain.model.Playlist
import com.dustvalve.next.android.domain.model.Track
import com.dustvalve.next.android.ui.screens.player.PlayerViewModel
import com.dustvalve.next.android.ui.theme.AppShapes
import com.dustvalve.next.android.ui.theme.resolvePlaylistShape

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

    val snackbarText = state.snackbarMessage?.asString()
    LaunchedEffect(snackbarText) {
        snackbarText?.let { message ->
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

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val trackCount = state.tracks.size
    val trackCountText = pluralStringResource(R.plurals.track_count, trackCount, trackCount)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(
                        text = state.playlist?.name ?: stringResource(R.string.playlist_default_title),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                subtitle = {
                    Text(
                        text = trackCountText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.common_cd_back),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                windowInsets = WindowInsets(0),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0),
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    ) { paddingValues ->
        when {
            state.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center,
                ) {
                    ContainedLoadingIndicator()
                }
            }
            state.error != null && state.playlist == null -> {
                ErrorState(
                    message = state.error ?: stringResource(R.string.playlist_error_load),
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
                    autoDownloadFavorites = state.autoDownloadFavorites,
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun PlaylistContent(
    playlist: Playlist,
    tracks: List<Track>,
    currentTrackId: String?,
    isPlaying: Boolean,
    isDownloading: Boolean,
    downloadedTrackIds: Set<String>,
    autoDownloadFavorites: Boolean,
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
    val trackCount = tracks.size

    val headerBlock: @Composable () -> Unit = {
        PlaylistHeader(
            playlist = playlist,
            tracks = tracks,
            isDownloading = isDownloading,
            downloadedTrackIds = downloadedTrackIds,
            autoDownloadFavorites = autoDownloadFavorites,
            onPlayAll = onPlayAll,
            onShufflePlay = onShufflePlay,
            onDownloadAll = onDownloadAll,
        )
    }

    if (tracks.isEmpty()) {
        LazyColumn(
            state = listState,
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 10.dp),
        ) {
            item(key = "header") { headerBlock() }
            item(key = "empty") {
                EmptyPlaylistState(
                    isSystem = playlist.isSystem,
                    modifier = Modifier.fillParentMaxSize(),
                )
            }
        }
        return
    }

    ReorderableMusicList(
        items = tracks,
        keyFn = { it.id },
        onMove = onMoveTrack,
        lazyListState = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 10.dp),
        header = { item(key = "header") { headerBlock() } },
    ) { index, track, isDragging, dragHandleModifier ->
        val isCurrentTrack = currentTrackId == track.id
        val isTrackPlaying = isCurrentTrack && isPlaying
        val canSwipeToDelete = !playlist.isSystem

        val body: @Composable () -> Unit = {
            SegmentedListItem(
                index = index,
                count = trackCount,
                isDragging = isDragging,
            ) {
                MusicRow(
                    track = track,
                    onClick = { onTrackClick(tracks, index) },
                    isPlaying = isTrackPlaying,
                    isCurrentTrack = isCurrentTrack,
                    dragHandle = {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .then(dragHandleModifier),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_drag_handle),
                                contentDescription = stringResource(R.string.common_cd_reorder),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    },
                )
            }
        }

        if (canSwipeToDelete) {
            val dismissState = rememberSwipeToDismissBoxState()
            LaunchedEffect(dismissState.currentValue) {
                if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                    onRemoveTrack(track.id)
                }
            }
            SwipeToDismissBox(
                state = dismissState,
                backgroundContent = {
                    if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp, vertical = 1.dp)
                                .clip(segmentedItemShape(index, trackCount))
                                .background(MaterialTheme.colorScheme.errorContainer)
                                .padding(horizontal = 20.dp),
                            contentAlignment = Alignment.CenterEnd,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_delete),
                                contentDescription = stringResource(R.string.common_cd_delete),
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                },
                enableDismissFromStartToEnd = false,
            ) {
                body()
            }
        } else {
            body()
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
    autoDownloadFavorites: Boolean,
    onPlayAll: () -> Unit,
    onShufflePlay: () -> Unit,
    onDownloadAll: () -> Unit,
) {
    val trackCount = tracks.size
    val allTracksLocal = tracks.isNotEmpty() && tracks.all { it.isLocal }
    val allTracksDownloaded = tracks.isNotEmpty() &&
        tracks.all { it.id in downloadedTrackIds || it.isLocal }
    // Hide the download button on playlists where it doesn't apply, and on
    // Favorites when the auto-download-favorites toggle is on (it'd be a no-op).
    val showDownloadButton = playlist.systemType != Playlist.SystemPlaylistType.DOWNLOADS &&
        playlist.systemType != Playlist.SystemPlaylistType.RECENT &&
        !(playlist.systemType == Playlist.SystemPlaylistType.FAVORITES && autoDownloadFavorites)

    val thumbnailShape = when (playlist.systemType) {
        Playlist.SystemPlaylistType.FAVORITES -> AppShapes.PlaylistFavorites
        Playlist.SystemPlaylistType.DOWNLOADS -> AppShapes.PlaylistDownloads
        Playlist.SystemPlaylistType.RECENT -> AppShapes.PlaylistRecent
        Playlist.SystemPlaylistType.COLLECTION -> AppShapes.PlaylistCollection
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
        val songCountText = pluralStringResource(R.plurals.song_count, trackCount, trackCount)
        val subtitle = when {
            playlist.isSystem -> "${stringResource(R.string.playlist_auto)} \u00B7 $songCountText"
            else -> songCountText
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
                    contentDescription = stringResource(R.string.playlist_cd_play_all),
                )
            }

            FilledTonalIconButton(
                onClick = onShufflePlay,
                shapes = IconButtonDefaults.shapes(),
                enabled = trackCount > 0,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_shuffle),
                    contentDescription = stringResource(R.string.common_cd_shuffle_play),
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
                            contentDescription = if (allTracksDownloaded) stringResource(R.string.playlist_cd_all_downloaded)
                                else stringResource(R.string.playlist_cd_download_all),
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
                text = if (isSystem) stringResource(R.string.playlist_empty_system_title) else stringResource(R.string.playlist_empty_custom_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (isSystem) {
                    stringResource(R.string.playlist_empty_system_subtitle)
                } else {
                    stringResource(R.string.playlist_empty_custom_subtitle)
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
            androidx.compose.material3.TextButton(onClick = onRetry, shapes = androidx.compose.material3.ButtonDefaults.shapes()) {
                Text(stringResource(R.string.common_action_retry))
            }
        }
    }
}