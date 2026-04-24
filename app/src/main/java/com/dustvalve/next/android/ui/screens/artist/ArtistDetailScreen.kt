package com.dustvalve.next.android.ui.screens.artist

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.res.stringResource
import com.dustvalve.next.android.R
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
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
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButtonColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.detail_delete_downloads_title)) },
            text = { Text(stringResource(R.string.detail_delete_artist_downloads_text, state.artist?.name ?: "")) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteAllDownloads()
                        showDeleteDialog = false
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(R.string.common_action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.common_action_cancel))
                }
            },
        )
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(
                        text = state.artist?.name ?: "",
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                subtitle = state.artist?.location?.takeIf { it.isNotBlank() }?.let { location ->
                    {
                        Text(
                            text = location,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
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
                            text = state.error ?: stringResource(R.string.detail_error_load_artist),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.loadArtist(artistUrl) }, shapes = ButtonDefaults.shapes()) {
                            Text(stringResource(R.string.common_action_retry))
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
                    contentPadding = PaddingValues(bottom = 10.dp),
                ) {
                    // Hero image — name + location are already in the LargeFlexibleTopAppBar
                    // so we drop the legacy bottom-overlay text and the bottom-fade gradient.
                    // The connected action button group below sits half over / half under
                    // the cover edge (negative top padding on the next item).
                    item(key = "artist_hero", span = { GridItemSpan(2) }) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .animateItem(),
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
                        }
                    }

                    // Connected M3E button group: Play mix · Favorite · Download.
                    // Negative top padding pulls the row up so its vertical centre
                    // sits on the cover's bottom edge ("half inside, half outside").
                    item(key = "actions", span = { GridItemSpan(2) }) {
                        val allAlbumsDownloaded = artist.albums.isNotEmpty() &&
                            artist.albums.all { it.id in state.downloadedAlbumIds }
                        ArtistActionBar(
                            isFavorite = artist.isFavorite,
                            isDownloading = state.isDownloading,
                            isLoadingMix = state.isLoadingMix,
                            allAlbumsDownloaded = allAlbumsDownloaded,
                            playMixEnabled = !state.isLoadingMix && artist.albums.isNotEmpty(),
                            downloadEnabled = !state.isDownloading && artist.albums.isNotEmpty(),
                            onPlayMix = {
                                viewModel.loadMixTracks { tracks ->
                                    playerViewModel.playAlbum(tracks, 0)
                                }
                            },
                            onToggleFavorite = { viewModel.toggleFavorite() },
                            onDownload = {
                                if (allAlbumsDownloaded) {
                                    showDeleteDialog = true
                                } else {
                                    viewModel.downloadAll()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp)
                                .offset(y = (-28).dp)
                                .animateItem(),
                        )
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
                                text = stringResource(R.string.detail_discography),
                                style = MaterialTheme.typography.titleLargeEmphasized,
                                modifier = Modifier
                                    .padding(
                                        start = 20.dp,
                                        end = 20.dp,
                                        top = 20.dp,
                                        bottom = 4.dp,
                                    )
                                    .animateItem(),
                            )
                        }

                        // Album grid in contained surface
                        items(
                            items = artist.albums,
                            key = { "album_${it.id}" },
                        ) { album ->
                            Surface(
                                modifier = Modifier
                                    .padding(6.dp)
                                    .animateItem(
                                        fadeInSpec = null,
                                        fadeOutSpec = null,
                                        placementSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                                    ),
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
                                    .padding(horizontal = 32.dp, vertical = 48.dp)
                                    .animateItem(),
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
                                    text = stringResource(R.string.detail_no_releases),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }

                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ExpandableBio(bio: String) {
    com.dustvalve.next.android.ui.screens.album.ExpandableDescription(
        text = bio,
        collapsedMaxLines = 3,
    )
}

/**
 * Connected M3E button-group action bar for artist detail. Layout:
 * [Play mix (weighted, primary filled)] · [Favorite (toggle)] · [Download].
 * Sits half-overlapping the cover via a negative offset on the call site.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ArtistActionBar(
    isFavorite: Boolean,
    isDownloading: Boolean,
    isLoadingMix: Boolean,
    allAlbumsDownloaded: Boolean,
    playMixEnabled: Boolean,
    downloadEnabled: Boolean,
    onPlayMix: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.heightIn(min = 56.dp),
        // 8 dp matches the album action bar — wider than the 2 dp default so
        // the connected-shape press morph reads clearly between buttons.
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            onClick = onPlayMix,
            enabled = playMixEnabled,
            shape = ButtonGroupDefaults.connectedLeadingButtonShapes().shape,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 56.dp),
        ) {
            if (isLoadingMix) {
                CircularWavyProgressIndicator(modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.common_loading))
            } else {
                Icon(
                    painter = painterResource(R.drawable.ic_shuffle),
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.common_play_mix))
            }
        }

        ToggleButton(
            checked = isFavorite,
            onCheckedChange = { onToggleFavorite() },
            shapes = ButtonGroupDefaults.connectedMiddleButtonShapes(),
            colors = ToggleButtonDefaults.tonalToggleButtonColors(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            modifier = Modifier.heightIn(min = 56.dp),
        ) {
            Icon(
                painter = painterResource(
                    if (isFavorite) R.drawable.ic_favorite else R.drawable.ic_favorite_border
                ),
                contentDescription = if (isFavorite) {
                    stringResource(R.string.detail_cd_remove_favorites)
                } else {
                    stringResource(R.string.detail_cd_add_favorites)
                },
            )
        }

        FilledTonalButton(
            onClick = onDownload,
            enabled = downloadEnabled,
            shape = ButtonGroupDefaults.connectedTrailingButtonShapes().shape,
            contentPadding = PaddingValues(horizontal = 16.dp),
            modifier = Modifier.heightIn(min = 56.dp),
        ) {
            if (isDownloading) {
                CircularWavyProgressIndicator(modifier = Modifier.size(20.dp))
            } else {
                Icon(
                    painter = painterResource(
                        if (allAlbumsDownloaded) R.drawable.ic_download_done
                        else R.drawable.ic_download
                    ),
                    contentDescription = if (allAlbumsDownloaded) {
                        stringResource(R.string.detail_cd_delete_all_downloads)
                    } else {
                        stringResource(R.string.detail_cd_download_all)
                    },
                )
            }
        }
    }
}
