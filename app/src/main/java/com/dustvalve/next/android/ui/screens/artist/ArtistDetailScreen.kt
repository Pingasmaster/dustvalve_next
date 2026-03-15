package com.dustvalve.next.android.ui.screens.artist

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.ui.res.painterResource
import com.dustvalve.next.android.R
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButtonColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.dustvalve.next.android.ui.components.AlbumCard
import com.dustvalve.next.android.ui.screens.player.PlayerViewModel
import com.dustvalve.next.android.ui.theme.AppShapes

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ArtistDetailScreen(
    artistUrl: String,
    onAlbumClick: (String) -> Unit,
    onBack: () -> Unit,
    playerViewModel: PlayerViewModel,
    viewModel: ArtistDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(artistUrl) {
        viewModel.loadArtist(artistUrl)
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

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Downloads") },
            text = { Text("Remove all downloaded tracks by ${state.artist?.name}?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteAllDownloads()
                        showDeleteDialog = false
                    },
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
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
                            text = state.error ?: "Failed to load artist",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.loadArtist(artistUrl) }) {
                            Text("Retry")
                        }
                    }
                }
            }
            state.artist != null -> {
                val artist = state.artist!!

                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                ) {
                    // Hero image with gradient and artist name overlay
                    item(key = "artist_hero", span = { GridItemSpan(2) }) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f),
                        ) {
                            if (artist.imageUrl != null) {
                                AsyncImage(
                                    model = artist.imageUrl,
                                    contentDescription = artist.name,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                )
                            }
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
                            // Bottom gradient scrim
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .align(Alignment.BottomCenter)
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                MaterialTheme.colorScheme.surface.copy(alpha = 0f),
                                                MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                                MaterialTheme.colorScheme.surface,
                                            ),
                                        ),
                                    ),
                            )
                            // Artist name and location overlaid
                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(horizontal = 20.dp, vertical = 16.dp),
                            ) {
                                Text(
                                    text = artist.name,
                                    style = MaterialTheme.typography.displaySmallEmphasized,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (artist.location != null) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = artist.location,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }

                    // Action row: Favorite + Play Mix — contained in a surface
                    item(key = "actions", span = { GridItemSpan(2) }) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                FilledTonalButton(
                                    onClick = {
                                        viewModel.loadMixTracks { tracks ->
                                            playerViewModel.playAlbum(tracks, 0)
                                        }
                                    },
                                    enabled = !state.isLoadingMix && artist.albums.isNotEmpty(),
                                    modifier = Modifier.weight(1f),
                                ) {
                                    if (state.isLoadingMix) {
                                        CircularWavyProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Loading...")
                                    } else {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_shuffle),
                                            contentDescription = null,
                                            modifier = Modifier.size(ButtonDefaults.IconSize),
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Play Mix")
                                    }
                                }

                                FilledTonalIconToggleButton(
                                    checked = artist.isFavorite,
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
                                        painter = painterResource(if (artist.isFavorite) R.drawable.ic_favorite
                                            else R.drawable.ic_favorite_border),
                                        contentDescription = if (artist.isFavorite) "Remove from favorites"
                                            else "Add to favorites",
                                    )
                                }

                                val allAlbumsDownloaded = artist.albums.isNotEmpty() &&
                                    artist.albums.all { it.id in state.downloadedAlbumIds }
                                FilledTonalIconButton(
                                    onClick = {
                                        if (allAlbumsDownloaded) {
                                            showDeleteDialog = true
                                        } else {
                                            viewModel.downloadAll()
                                        }
                                    },
                                    enabled = !state.isDownloading && artist.albums.isNotEmpty(),
                                ) {
                                    if (state.isDownloading) {
                                        CircularWavyProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                        )
                                    } else {
                                        Icon(
                                            painter = painterResource(if (allAlbumsDownloaded) R.drawable.ic_download_done
                                                else R.drawable.ic_download),
                                            contentDescription = if (allAlbumsDownloaded) "Delete all downloads"
                                                else "Download all",
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Bio with expandable content
                    if (!artist.bio.isNullOrBlank()) {
                        item(key = "artist_bio", span = { GridItemSpan(2) }) {
                            ExpandableBio(bio = artist.bio)
                        }
                    }

                    if (artist.albums.isNotEmpty()) {
                        // Discography header
                        item(key = "discography_header", span = { GridItemSpan(2) }) {
                            Text(
                                text = "Discography",
                                style = MaterialTheme.typography.titleLargeEmphasized,
                                modifier = Modifier.padding(
                                    start = 20.dp,
                                    end = 20.dp,
                                    top = 20.dp,
                                    bottom = 4.dp,
                                ),
                            )
                        }

                        // Album grid in contained surface
                        items(
                            items = artist.albums,
                            key = { "album_${it.id}" },
                        ) { album ->
                            Surface(
                                modifier = Modifier.padding(6.dp),
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surfaceContainer,
                            ) {
                                AlbumCard(
                                    album = album,
                                    onClick = { onAlbumClick(album.url) },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    } else {
                        // Empty state
                        item(key = "no_albums", span = { GridItemSpan(2) }) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 32.dp, vertical = 48.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(96.dp)
                                        .clip(AppShapes.EmptyStateIcon)
                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_album),
                                        contentDescription = null,
                                        modifier = Modifier.size(48.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    )
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "No releases yet",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }

                    // Bottom spacer
                    item(key = "bottom_spacer", span = { GridItemSpan(2) }) {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ExpandableBio(bio: String) {
    var expanded by rememberSaveable(bio) { mutableStateOf(false) }
    var hasOverflow by remember(bio) { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .animateContentSize(
                animationSpec = MaterialTheme.motionScheme.slowSpatialSpec(),
            ),
    ) {
        Text(
            text = bio,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (expanded) Int.MAX_VALUE else 3,
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
