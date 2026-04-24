package com.dustvalve.next.android.ui.screens.detail

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ComponentOverrideApi
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.dustvalve.next.android.R
import com.dustvalve.next.android.ui.components.AlbumCard
import com.dustvalve.next.android.ui.components.detail.ExpandableText
import com.dustvalve.next.android.ui.components.lists.MusicRow
import com.dustvalve.next.android.ui.components.lists.SegmentedListItem
import com.dustvalve.next.android.ui.screens.player.PlayerViewModel
import com.dustvalve.next.android.ui.theme.AppShapes

/**
 * Source-agnostic artist detail screen.
 *
 * Renders two shapes based on what the source returns:
 *  - Bandcamp-style: `artist.albums` populated → album grid with "Buy full
 *    discography" split-button when the artist has that offer.
 *  - YouTube-style: `albums` empty, `tracks` populated → flat segmented track
 *    list with "Load more" pagination. No buy button.
 *
 * Replaces `ui/screens/artist/ArtistDetailScreen.kt` (Bandcamp) and
 * `ui/screens/youtube/YouTubeArtistDetailScreen.kt`.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ArtistDetailScreen(
    sourceId: String,
    artistUrl: String,
    artistNameHint: String?,
    artistImageHint: String?,
    onAlbumClick: (String) -> Unit,
    onBack: () -> Unit,
    playerViewModel: PlayerViewModel,
    viewModel: ArtistDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(sourceId, artistUrl) {
        viewModel.load(
            sourceId = sourceId,
            url = artistUrl,
            name = artistNameHint,
            imageUrl = artistImageHint,
        )
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
                ) { Text(stringResource(R.string.common_action_delete)) }
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
                        text = state.artist?.name ?: artistNameHint ?: "",
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                subtitle = state.artist?.location?.takeIf { it.isNotBlank() }?.let { location ->
                    { Text(text = location, maxLines = 1, overflow = TextOverflow.Ellipsis) }
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
            state.isLoading && state.artist == null -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) { ContainedLoadingIndicator() }
            }
            state.error != null && state.artist == null -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = state.error ?: stringResource(R.string.detail_error_load_artist),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.load(sourceId, artistUrl, artistNameHint, artistImageHint) },
                            shapes = ButtonDefaults.shapes(),
                        ) { Text(stringResource(R.string.common_action_retry)) }
                    }
                }
            }
            state.artist != null -> {
                val artist = state.artist!!
                val renderFlatTracks = artist.albums.isEmpty() &&
                    (state.tracks.isNotEmpty() || state.hasMore || sourceId != "bandcamp")
                if (renderFlatTracks) {
                    FlatTracksLayout(
                        state = state,
                        innerPadding = innerPadding,
                        onPlayAll = {
                            if (state.tracks.isNotEmpty()) {
                                playerViewModel.playAlbum(state.tracks, 0)
                            }
                        },
                        onToggleFavorite = viewModel::toggleFavorite,
                        onDownload = {
                            val allDownloaded = state.tracks.isNotEmpty() &&
                                state.tracks.all { it.id in state.downloadedTrackIds }
                            if (allDownloaded) showDeleteDialog = true
                            else viewModel.downloadAll()
                        },
                        onLoadMore = viewModel::loadMore,
                        onTrackClick = { idx -> playerViewModel.playAlbum(state.tracks, idx) },
                    )
                } else {
                    AlbumGridLayout(
                        state = state,
                        innerPadding = innerPadding,
                        onAlbumClick = onAlbumClick,
                        onPlayMix = {
                            viewModel.loadMixTracks { tracks ->
                                playerViewModel.playAlbum(tracks, 0)
                            }
                        },
                        onToggleFavorite = viewModel::toggleFavorite,
                        onDownload = {
                            val allDownloaded = artist.albums.isNotEmpty() &&
                                artist.albums.all { it.id in state.downloadedAlbumIds }
                            if (allDownloaded) showDeleteDialog = true
                            else viewModel.downloadAll()
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AlbumGridLayout(
    state: ArtistDetailUiState,
    innerPadding: PaddingValues,
    onAlbumClick: (String) -> Unit,
    onPlayMix: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDownload: () -> Unit,
) {
    val artist = state.artist ?: return
    val allAlbumsDownloaded = artist.albums.isNotEmpty() &&
        artist.albums.all { it.id in state.downloadedAlbumIds }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize().padding(innerPadding),
        contentPadding = PaddingValues(bottom = 10.dp),
    ) {
        item(key = "artist_hero", span = { GridItemSpan(2) }) {
            ArtistHero(imageUrl = artist.imageUrl, name = artist.name)
        }
        item(key = "actions", span = { GridItemSpan(2) }) {
            ActionBar(
                playPrimaryLabel = stringResource(R.string.common_play_mix),
                playPrimaryIconRes = R.drawable.ic_shuffle,
                playPrimaryEnabled = !state.isLoadingMix && artist.albums.isNotEmpty(),
                playPrimaryLoading = state.isLoadingMix,
                isFavorite = state.isFavorite,
                isDownloading = state.isDownloading,
                allDownloaded = allAlbumsDownloaded,
                downloadEnabled = !state.isDownloading && artist.albums.isNotEmpty(),
                onPlayPrimary = onPlayMix,
                onToggleFavorite = onToggleFavorite,
                onDownload = onDownload,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .offset(y = (-28).dp),
            )
        }
        if (!artist.bio.isNullOrBlank()) {
            item(key = "artist_bio", span = { GridItemSpan(2) }) {
                ExpandableText(text = artist.bio.orEmpty(), collapsedMaxLines = 3)
            }
        }
        if (artist.hasDiscographyOffer) {
            item(key = "buy_discography", span = { GridItemSpan(2) }) {
                val uriHandler = LocalUriHandler.current
                BuyDiscographySplitButton(
                    artistUrl = artist.url,
                    onOpen = { uriHandler.openUri(it) },
                )
            }
        }
        if (artist.albums.isNotEmpty()) {
            item(key = "discography_header", span = { GridItemSpan(2) }) {
                Text(
                    text = stringResource(R.string.detail_discography),
                    style = MaterialTheme.typography.titleLargeEmphasized,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 4.dp),
                )
            }
            items(items = artist.albums, key = { "album_${it.id}" }) { album ->
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
            item(key = "no_albums", span = { GridItemSpan(2) }) {
                EmptyState(message = stringResource(R.string.detail_no_releases))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FlatTracksLayout(
    state: ArtistDetailUiState,
    innerPadding: PaddingValues,
    onPlayAll: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDownload: () -> Unit,
    onLoadMore: () -> Unit,
    onTrackClick: (Int) -> Unit,
) {
    val artist = state.artist ?: return
    val listState = rememberLazyListState()
    val allDownloaded = state.tracks.isNotEmpty() &&
        state.tracks.all { it.id in state.downloadedTrackIds }

    // Trigger pagination when we're near the bottom.
    LaunchedEffect(listState, state.tracks.size, state.hasMore) {
        androidx.compose.runtime.snapshotFlow {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = listState.layoutInfo.totalItemsCount
            total > 0 && last >= total - 3
        }.collect { nearEnd ->
            if (nearEnd && state.hasMore && !state.isLoadingMore) {
                onLoadMore()
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(innerPadding),
        contentPadding = PaddingValues(bottom = 10.dp),
    ) {
        item(key = "artist_hero") {
            ArtistHero(imageUrl = artist.imageUrl, name = artist.name)
        }
        item(key = "actions") {
            ActionBar(
                playPrimaryLabel = stringResource(R.string.common_play_all),
                playPrimaryIconRes = R.drawable.ic_play_arrow,
                playPrimaryEnabled = state.tracks.isNotEmpty(),
                playPrimaryLoading = false,
                isFavorite = state.isFavorite,
                isDownloading = state.isDownloading,
                allDownloaded = allDownloaded,
                downloadEnabled = !state.isDownloading && state.tracks.isNotEmpty(),
                onPlayPrimary = onPlayAll,
                onToggleFavorite = onToggleFavorite,
                onDownload = onDownload,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .offset(y = (-28).dp),
            )
        }
        if (!artist.bio.isNullOrBlank()) {
            item(key = "artist_bio") {
                ExpandableText(text = artist.bio.orEmpty(), collapsedMaxLines = 3)
            }
        }
        if (state.tracks.isEmpty() && !state.hasMore && !state.isLoading) {
            item(key = "empty") { EmptyState(message = stringResource(R.string.detail_no_releases)) }
        } else {
            val count = state.tracks.size + if (state.hasMore) 1 else 0
            itemsIndexed(state.tracks, count) { index, track ->
                SegmentedListItem(index = index, count = count) {
                    MusicRow(
                        track = track,
                        onClick = { onTrackClick(index) },
                    )
                }
            }
            if (state.hasMore || state.isLoadingMore) {
                item(key = "loading_more") {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        contentAlignment = Alignment.Center,
                    ) { ContainedLoadingIndicator() }
                }
            }
        }
    }
}

private fun <T> androidx.compose.foundation.lazy.LazyListScope.itemsIndexed(
    items: List<T>,
    @Suppress("UNUSED_PARAMETER") count: Int,
    block: @Composable (index: Int, item: T) -> Unit,
) {
    items(items.size, key = { i -> "track_${items[i].hashCode()}" }) { i ->
        block(i, items[i])
    }
}

@Composable
private fun ArtistHero(imageUrl: String?, name: String) {
    Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = name,
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

@Composable
private fun EmptyState(message: String) {
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
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ActionBar(
    playPrimaryLabel: String,
    playPrimaryIconRes: Int,
    playPrimaryEnabled: Boolean,
    playPrimaryLoading: Boolean,
    isFavorite: Boolean,
    isDownloading: Boolean,
    allDownloaded: Boolean,
    downloadEnabled: Boolean,
    onPlayPrimary: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.heightIn(min = 56.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            onClick = onPlayPrimary,
            enabled = playPrimaryEnabled,
            shape = ButtonGroupDefaults.connectedLeadingButtonShapes().shape,
            modifier = Modifier.weight(1f).heightIn(min = 56.dp),
        ) {
            if (playPrimaryLoading) {
                CircularWavyProgressIndicator(modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.common_loading))
            } else {
                Icon(
                    painter = painterResource(playPrimaryIconRes),
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(playPrimaryLabel)
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
                    if (isFavorite) R.drawable.ic_favorite else R.drawable.ic_favorite_border,
                ),
                contentDescription = if (isFavorite) stringResource(R.string.detail_cd_remove_favorites)
                    else stringResource(R.string.detail_cd_add_favorites),
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
                        if (allDownloaded) R.drawable.ic_download_done else R.drawable.ic_download,
                    ),
                    contentDescription = if (allDownloaded) stringResource(R.string.detail_cd_delete_all_downloads)
                        else stringResource(R.string.detail_cd_download_all),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3ComponentOverrideApi::class)
@Composable
private fun BuyDiscographySplitButton(
    artistUrl: String,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box(
        modifier = modifier.fillMaxWidth().padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        SplitButtonLayout(
            leadingButton = {
                SplitButtonDefaults.LeadingButton(
                    onClick = { onOpen(artistUrl) },
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_shopping_bag),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.detail_buy_full_discography))
                }
            },
            trailingButton = {
                Box {
                    SplitButtonDefaults.TrailingButton(
                        checked = menuOpen,
                        onCheckedChange = { menuOpen = it },
                    ) {
                        val rotation by animateFloatAsState(
                            targetValue = if (menuOpen) 180f else 0f,
                            animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
                            label = "buy_discog_chevron",
                        )
                        Icon(
                            painter = painterResource(R.drawable.ic_expand_more),
                            contentDescription = stringResource(R.string.detail_buy_more_options),
                            modifier = Modifier.size(20.dp).rotate(rotation),
                        )
                    }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.detail_send_as_gift)) },
                            onClick = {
                                menuOpen = false
                                onOpen(artistUrl)
                            },
                        )
                    }
                }
            },
        )
    }
}
