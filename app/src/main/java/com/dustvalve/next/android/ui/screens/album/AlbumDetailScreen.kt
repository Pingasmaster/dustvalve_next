package com.dustvalve.next.android.ui.screens.album

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.res.painterResource
import com.dustvalve.next.android.R
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButtonColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.dustvalve.next.android.ui.components.TrackRow
import com.dustvalve.next.android.ui.screens.player.PlayerViewModel
import com.dustvalve.next.android.ui.theme.AppShapes
import com.dustvalve.next.android.ui.theme.segmentedItemShape

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AlbumDetailScreen(
    albumUrl: String,
    onArtistClick: (String) -> Unit,
    onBack: () -> Unit,
    playerViewModel: PlayerViewModel,
    viewModel: AlbumDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val playerState by playerViewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showDeleteAlbumDialog by rememberSaveable { mutableStateOf(false) }
    var trackToDelete by remember { mutableStateOf<com.dustvalve.next.android.domain.model.Track?>(null) }

    LaunchedEffect(albumUrl) {
        viewModel.loadAlbum(albumUrl)
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

    if (showDeleteAlbumDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAlbumDialog = false },
            title = { Text("Delete Downloads") },
            text = { Text("Remove all downloaded tracks for '${state.album?.title}'?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteAlbumDownloads()
                        showDeleteAlbumDialog = false
                    },
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAlbumDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    trackToDelete?.let { track ->
        AlertDialog(
            onDismissRequest = { trackToDelete = null },
            title = { Text("Delete Download") },
            text = { Text("Remove downloaded file for '${track.title}'?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteTrackDownload(track)
                        trackToDelete = null
                    },
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { trackToDelete = null }) {
                    Text("Cancel")
                }
            },
        )
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = "Back",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.0f),
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                ),
                scrollBehavior = scrollBehavior,
                windowInsets = WindowInsets(0),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0),
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    ) { innerPadding ->
        when {
            state.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    ContainedLoadingIndicator()
                }
            }
            state.error != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = state.error ?: "Failed to load album",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.loadAlbum(albumUrl) }) {
                            Text("Retry")
                        }
                    }
                }
            }
            state.album != null -> {
                val album = state.album!!

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                ) {
                    // Hero album art with gradient overlay
                    item(key = "album_art") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f),
                        ) {
                            AsyncImage(
                                model = album.artUrl,
                                contentDescription = album.title,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                            // Top scrim so status bar icons remain readable
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(80.dp)
                                    .align(Alignment.TopCenter)
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                MaterialTheme.colorScheme.scrim.copy(alpha = 0.35f),
                                                Color.Transparent,
                                            ),
                                        ),
                                    ),
                            )
                            // Bottom gradient into surface
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                                    .align(Alignment.BottomCenter)
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                MaterialTheme.colorScheme.surface.copy(alpha = 0f),
                                                MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                                                MaterialTheme.colorScheme.surface,
                                            ),
                                        ),
                                    ),
                            )
                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(horizontal = 20.dp, vertical = 16.dp),
                            ) {
                                Text(
                                    text = album.title,
                                    style = MaterialTheme.typography.headlineLargeEmphasized,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }

                    // Artist + release date
                    item(key = "album_meta") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp),
                        ) {
                            Text(
                                text = album.artist,
                                style = MaterialTheme.typography.titleLarge,
                                color = if (album.artistUrl.isNotBlank()) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .clickable(enabled = album.artistUrl.isNotBlank()) { onArtistClick(album.artistUrl) }
                                    .padding(vertical = 4.dp),
                            )
                            album.releaseDate?.let { date ->
                                Text(
                                    text = date,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    // Action bar — contained surface with all actions
                    item(key = "actions") {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                // Play All — primary filled button, takes remaining space
                                Button(
                                    onClick = {
                                        if (album.tracks.isNotEmpty()) {
                                            playerViewModel.playAlbum(album.tracks, 0)
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_play_arrow),
                                        contentDescription = null,
                                        modifier = Modifier.size(ButtonDefaults.IconSize),
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Play All")
                                }

                                // Shuffle
                                FilledTonalIconButton(
                                    onClick = {
                                        if (album.tracks.isNotEmpty()) {
                                            playerViewModel.playAlbum(album.tracks.shuffled(), 0)
                                        }
                                    },
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_shuffle),
                                        contentDescription = "Shuffle play",
                                    )
                                }

                                // Favorite album
                                FilledTonalIconToggleButton(
                                    checked = album.isFavorite,
                                    onCheckedChange = { viewModel.toggleFavorite() },
                                    colors = IconToggleButtonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        checkedContentColor = MaterialTheme.colorScheme.primary,
                                    ),
                                ) {
                                    Icon(
                                        painter = painterResource(if (album.isFavorite) R.drawable.ic_favorite
                                            else R.drawable.ic_favorite_border),
                                        contentDescription = if (album.isFavorite) "Remove from favorites"
                                            else "Add to favorites",
                                    )
                                }

                                // Download / Delete album
                                val allTracksDownloaded = album.tracks.isNotEmpty() &&
                                    album.tracks.all { it.id in state.downloadedTrackIds }
                                FilledTonalIconButton(
                                    onClick = {
                                        if (allTracksDownloaded) {
                                            showDeleteAlbumDialog = true
                                        } else {
                                            viewModel.downloadAlbum()
                                        }
                                    },
                                    enabled = !state.isDownloading,
                                ) {
                                    if (state.isDownloading) {
                                        CircularWavyProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                        )
                                    } else {
                                        Icon(
                                            painter = painterResource(if (allTracksDownloaded) R.drawable.ic_download_done
                                                else R.drawable.ic_download),
                                            contentDescription = if (allTracksDownloaded) "Delete album downloads"
                                                else "Download album",
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // About section (expandable)
                    album.about?.let { about ->
                        if (about.isNotBlank()) {
                            item(key = "about") {
                                ExpandableAbout(about = about)
                            }
                        }
                    }

                    // Track list header + segmented items
                    if (album.tracks.isNotEmpty()) {
                        item(key = "tracks_header") {
                            Text(
                                text = "Tracks",
                                style = MaterialTheme.typography.titleMediumEmphasized,
                                modifier = Modifier.padding(
                                    start = 20.dp, end = 20.dp,
                                    top = 16.dp, bottom = 4.dp,
                                ),
                            )
                        }

                        items(
                            count = album.tracks.size,
                            key = { album.tracks[it].id },
                        ) { index ->
                            val track = album.tracks[index]
                            val isCurrentTrack = playerState.currentTrack?.id == track.id
                            val isTrackDownloaded = track.id in state.downloadedTrackIds

                            Surface(
                                modifier = Modifier
                                    .padding(
                                        start = 16.dp,
                                        end = 16.dp,
                                        top = if (index == 0) 8.dp else 1.dp,
                                        bottom = if (index == album.tracks.lastIndex) 0.dp else 1.dp,
                                    )
                                    .animateItem(
                                        fadeInSpec = null,
                                        fadeOutSpec = null,
                                        placementSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                                    ),
                                shape = segmentedItemShape(index, album.tracks.size),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                            ) {
                                TrackRow(
                                    track = track,
                                    isPlaying = isCurrentTrack && playerState.isPlaying,
                                    onClick = {
                                        playerViewModel.playAlbum(album.tracks, index)
                                    },
                                    onFavoriteClick = {
                                        viewModel.toggleTrackFavorite(track.id)
                                    },
                                    onDownloadClick = {
                                        if (isTrackDownloaded) {
                                            trackToDelete = track
                                        } else {
                                            viewModel.downloadTrack(track)
                                        }
                                    },
                                    isDownloading = track.id in state.downloadingTrackIds,
                                    isDownloaded = isTrackDownloaded,
                                )
                            }
                        }
                    }

                    // Tags in contained surface
                    if (album.tags.isNotEmpty()) {
                        item(key = "tags") {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                shape = MaterialTheme.shapes.extraLarge,
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                            ) {
                                Column(
                                    modifier = Modifier.padding(20.dp),
                                ) {
                                    Text(
                                        text = "Tags",
                                        style = MaterialTheme.typography.titleMediumEmphasized,
                                        modifier = Modifier.padding(bottom = 12.dp),
                                    )
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        album.tags.forEach { tag ->
                                            Surface(
                                                shape = AppShapes.Tag,
                                                color = MaterialTheme.colorScheme.secondaryContainer,
                                            ) {
                                                Text(
                                                    text = tag,
                                                    style = MaterialTheme.typography.labelLarge,
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Bottom spacer for mini player clearance
                    item(key = "bottom_spacer") {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ExpandableAbout(about: String) {
    var expanded by rememberSaveable(about) { mutableStateOf(false) }
    var hasOverflow by remember(about) { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .animateContentSize(
                animationSpec = MaterialTheme.motionScheme.slowSpatialSpec(),
            ),
    ) {
        Text(
            text = about,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (expanded) Int.MAX_VALUE else 4,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { result ->
                if (!expanded) {
                    hasOverflow = result.hasVisualOverflow
                }
            },
        )
        if (hasOverflow || expanded) {
            TextButton(
                onClick = { expanded = !expanded },
            ) {
                Text(if (expanded) "Show less" else "Show more")
            }
        }
    }
}
