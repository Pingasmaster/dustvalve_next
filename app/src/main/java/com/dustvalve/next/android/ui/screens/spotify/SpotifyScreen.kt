package com.dustvalve.next.android.ui.screens.spotify

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.dustvalve.next.android.R
import com.dustvalve.next.android.domain.model.SearchResult
import com.dustvalve.next.android.domain.model.SearchResultType
import com.dustvalve.next.android.ui.components.RecentSearchesList
import com.dustvalve.next.android.ui.components.sheet.AddToPlaylistSheet
import com.dustvalve.next.android.ui.components.sheet.RemoteResultActionSheet
import com.dustvalve.next.android.ui.screens.player.PlayerViewModel
import com.dustvalve.next.android.util.openInBrowser
import com.dustvalve.next.android.util.shareUrl
import com.dustvalve.next.android.ui.theme.AppShapes
import com.dustvalve.next.android.ui.theme.segmentedItemShape
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)
@Composable
fun SpotifyScreen(
    playerViewModel: PlayerViewModel,
    onAlbumClick: (uri: String, name: String, imageUrl: String?) -> Unit,
    onArtistClick: (uri: String, name: String, imageUrl: String?) -> Unit,
    onPlaylistClick: (uri: String, name: String) -> Unit,
    onExpandPlayer: () -> Unit = {},
    viewModel: SpotifyViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val playerState by playerViewModel.uiState.collectAsStateWithLifecycle()
    val recentSearches by viewModel.recentSearches.collectAsStateWithLifecycle()
    val searchHistoryEnabled by viewModel.searchHistoryEnabled.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current

    var contextResult by remember { mutableStateOf<SearchResult?>(null) }
    var addToPlaylistTrackId by remember { mutableStateOf<String?>(null) }

    val failedToPlayMsg = stringResource(R.string.common_failed_to_play)
    val loadingTrackMsg = stringResource(R.string.common_loading_track)
    val loadingAlbumMsg = stringResource(R.string.common_loading_album)
    val loadingPlaylistMsg = stringResource(R.string.common_loading_playlist)
    val failedLoadMsg = stringResource(R.string.snackbar_failed_load)

    val searchBarState = rememberSearchBarState()
    val textFieldState = rememberTextFieldState()
    val searchListState = rememberLazyListState()

    LaunchedEffect(textFieldState) {
        snapshotFlow { textFieldState.text.toString() }
            .collect { viewModel.onQueryChange(it) }
    }

    // Pagination
    LaunchedEffect(searchListState) {
        snapshotFlow {
            val last = searchListState.layoutInfo.visibleItemsInfo.lastOrNull()
            val totalCount = searchListState.layoutInfo.totalItemsCount
            last != null && totalCount > 0 && last.index >= totalCount - 3
        }.collect { nearEnd ->
            if (nearEnd && state.hasMore && !state.isLoading && state.results.isNotEmpty()) {
                viewModel.loadMore()
            }
        }
    }

    LaunchedEffect(state.error) {
        val error = state.error ?: return@LaunchedEffect
        if (state.results.isNotEmpty()) {
            try {
                snackbarHostState.showSnackbar(error)
            } finally {
                viewModel.clearError()
            }
        }
    }

    val inputField = @Composable {
        SearchBarDefaults.InputField(
            searchBarState = searchBarState,
            textFieldState = textFieldState,
            onSearch = { viewModel.onSearch() },
            placeholder = { Text(stringResource(R.string.spotify_search_placeholder)) },
            leadingIcon = {
                Icon(
                    painter = painterResource(R.drawable.ic_search),
                    contentDescription = stringResource(R.string.common_cd_search),
                )
            },
            trailingIcon = {
                if (textFieldState.text.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            textFieldState.setTextAndPlaceCursorAtEnd("")
                        },
                        shapes = IconButtonDefaults.shapes(),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_clear),
                            contentDescription = stringResource(R.string.common_cd_clear),
                        )
                    }
                }
            },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0),
    ) { scaffoldPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                SearchBar(
                    state = searchBarState,
                    inputField = inputField,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )

                // Empty state when no search is active
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.spotify_empty_state),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp),
                    )
                }
            }

            // Expanded search overlay
            ExpandedFullScreenSearchBar(
                state = searchBarState,
                inputField = inputField,
            ) {
                // Filter chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = state.selectedFilter == null,
                        onClick = { viewModel.onFilterSelected(null) },
                        label = { Text(stringResource(R.string.spotify_tab_all)) },
                    )
                    FilterChip(
                        selected = state.selectedFilter == "tracks",
                        onClick = { viewModel.onFilterSelected("tracks") },
                        label = { Text(stringResource(R.string.spotify_tab_tracks)) },
                    )
                    FilterChip(
                        selected = state.selectedFilter == "albums",
                        onClick = { viewModel.onFilterSelected("albums") },
                        label = { Text(stringResource(R.string.spotify_tab_albums)) },
                    )
                    FilterChip(
                        selected = state.selectedFilter == "artists",
                        onClick = { viewModel.onFilterSelected("artists") },
                        label = { Text(stringResource(R.string.spotify_tab_artists)) },
                    )
                    FilterChip(
                        selected = state.selectedFilter == "playlists",
                        onClick = { viewModel.onFilterSelected("playlists") },
                        label = { Text(stringResource(R.string.spotify_tab_playlists)) },
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    when {
                        state.query.isBlank() -> {
                            if (searchHistoryEnabled && recentSearches.isNotEmpty()) {
                                RecentSearchesList(
                                    recentSearches = recentSearches,
                                    onSearchClick = { query ->
                                        textFieldState.setTextAndPlaceCursorAtEnd(query)
                                        viewModel.onSearch()
                                    },
                                    onRemoveClick = { viewModel.removeRecentSearch(it) },
                                    onClearAllClick = { viewModel.clearRecentSearches() },
                                )
                            }
                        }
                        state.isLoading && state.results.isEmpty() -> {
                            ContainedLoadingIndicator(
                                modifier = Modifier.align(Alignment.Center),
                            )
                        }
                        state.error != null && state.results.isEmpty() -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .padding(horizontal = 32.dp),
                            ) {
                                Text(
                                    text = state.error ?: stringResource(R.string.common_search_failed),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                        state.results.isEmpty() && state.query.isNotBlank() && !state.isLoading -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .padding(horizontal = 32.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.common_no_results_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    textAlign = TextAlign.Center,
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.common_no_results_subtitle),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                        else -> {
                            LazyColumn(
                                state = searchListState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 10.dp),
                            ) {
                                itemsIndexed(
                                    items = state.results,
                                    key = { _, result -> result.url },
                                ) { index, result ->
                                    val interactionSource = remember { MutableInteractionSource() }
                                    val isPressed by interactionSource.collectIsPressedAsState()
                                    val pressScale by animateFloatAsState(
                                        targetValue = if (isPressed) 0.97f else 1f,
                                        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                                        label = "pressScale",
                                    )

                                    Surface(
                                        shape = segmentedItemShape(index, state.results.size),
                                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                                        modifier = Modifier
                                            .padding(
                                                start = 16.dp,
                                                end = 16.dp,
                                                top = if (index == 0) 8.dp else 1.dp,
                                                bottom = if (index == state.results.lastIndex) 0.dp else 1.dp,
                                            )
                                            .animateItem(
                                                fadeInSpec = null,
                                                fadeOutSpec = null,
                                                placementSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                                            )
                                            .graphicsLayer {
                                                scaleX = pressScale
                                                scaleY = pressScale
                                            }
                                            .combinedClickable(
                                                interactionSource = interactionSource,
                                                indication = LocalIndication.current,
                                                onClick = {
                                                    when (result.type) {
                                                        SearchResultType.SPOTIFY_TRACK -> {
                                                            scope.launch {
                                                                try {
                                                                    val track = viewModel.getTrackInfo(result.url)
                                                                    searchBarState.animateToCollapsed()
                                                                    playerViewModel.playTrack(track)
                                                                    onExpandPlayer()
                                                                } catch (_: Exception) {
                                                                    snackbarHostState.showSnackbar(failedToPlayMsg)
                                                                }
                                                            }
                                                        }
                                                        SearchResultType.SPOTIFY_ALBUM -> {
                                                            scope.launch { searchBarState.animateToCollapsed() }
                                                            onAlbumClick(result.url, result.name, result.imageUrl)
                                                        }
                                                        SearchResultType.SPOTIFY_ARTIST -> {
                                                            scope.launch { searchBarState.animateToCollapsed() }
                                                            onArtistClick(result.url, result.name, result.imageUrl)
                                                        }
                                                        SearchResultType.SPOTIFY_PLAYLIST -> {
                                                            scope.launch { searchBarState.animateToCollapsed() }
                                                            onPlaylistClick(result.url, result.name)
                                                        }
                                                        else -> { /* not applicable */ }
                                                    }
                                                },
                                                onLongClick = {
                                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    contextResult = result
                                                },
                                            ),
                                    ) {
                                        ListItem(
                                            headlineContent = {
                                                Text(
                                                    text = result.name,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            },
                                            supportingContent = {
                                                val subtitle = when (result.type) {
                                                    SearchResultType.SPOTIFY_TRACK -> listOfNotNull(
                                                        result.artist, result.album,
                                                    ).joinToString(" - ").ifBlank { null }
                                                    SearchResultType.SPOTIFY_ALBUM -> result.artist
                                                    SearchResultType.SPOTIFY_PLAYLIST -> result.artist
                                                    else -> result.artist
                                                }
                                                subtitle?.let {
                                                    Text(
                                                        text = it,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                    )
                                                }
                                            },
                                            leadingContent = {
                                                val thumbnailShape = when (result.type) {
                                                    SearchResultType.SPOTIFY_ARTIST -> AppShapes.SearchResultArtist
                                                    SearchResultType.SPOTIFY_ALBUM -> AppShapes.SearchResultAlbum
                                                    SearchResultType.SPOTIFY_PLAYLIST -> AppShapes.SearchResultAlbum
                                                    else -> AppShapes.SearchResultTrack
                                                }
                                                if (result.imageUrl != null) {
                                                    AsyncImage(
                                                        model = result.imageUrl,
                                                        contentDescription = result.name,
                                                        modifier = Modifier
                                                            .size(48.dp)
                                                            .clip(thumbnailShape),
                                                        contentScale = ContentScale.Crop,
                                                    )
                                                } else {
                                                    val iconRes = when (result.type) {
                                                        SearchResultType.SPOTIFY_ARTIST -> R.drawable.ic_person
                                                        SearchResultType.SPOTIFY_ALBUM -> R.drawable.ic_album
                                                        SearchResultType.SPOTIFY_PLAYLIST -> R.drawable.ic_queue_music
                                                        else -> R.drawable.ic_music_note
                                                    }
                                                    Icon(
                                                        painter = painterResource(iconRes),
                                                        contentDescription = null,
                                                        modifier = Modifier.size(48.dp),
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                }
                                            },
                                            trailingContent = {
                                                if (result.type == SearchResultType.SPOTIFY_PLAYLIST) {
                                                    val importedMsg = stringResource(R.string.common_playlist_imported, result.name)
                                                    IconButton(
                                                        onClick = {
                                                            scope.launch {
                                                                viewModel.importPlaylist(result.url, result.name)
                                                                snackbarHostState.showSnackbar(importedMsg)
                                                            }
                                                        },
                                                        shapes = IconButtonDefaults.shapes(),
                                                    ) {
                                                        Icon(
                                                            painter = painterResource(R.drawable.ic_playlist_add),
                                                            contentDescription = stringResource(R.string.common_cd_import_playlist),
                                                        )
                                                    }
                                                }
                                            },
                                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                        )
                                    }
                                }

                                // Pagination footer (M3E wavy linear indicator)
                                if (state.isLoading && state.results.isNotEmpty()) {
                                    item {
                                        LinearWavyProgressIndicator(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 12.dp)
                                                .animateItem(),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    SnackbarHost(
                        hostState = snackbarHostState,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
        }
    }

    contextResult?.let { result ->
        val webUrl = remember(result.url) { spotifyUriToWebUrl(result.url) }
        val loadingMsg = when (result.type) {
            SearchResultType.SPOTIFY_TRACK -> loadingTrackMsg
            SearchResultType.SPOTIFY_ALBUM -> loadingAlbumMsg
            SearchResultType.SPOTIFY_PLAYLIST -> loadingPlaylistMsg
            else -> loadingTrackMsg
        }
        RemoteResultActionSheet(
            result = result,
            onDismiss = { contextResult = null },
            onPlayNext = {
                contextResult = null
                scope.launch { snackbarHostState.showSnackbar(loadingMsg) }
                scope.launch {
                    try {
                        val track = viewModel.getTrackInfo(result.url)
                        playerViewModel.playNext(track)
                    } catch (_: Exception) {
                        snackbarHostState.showSnackbar(failedLoadMsg)
                    }
                }
            },
            onAddToQueue = {
                contextResult = null
                scope.launch { snackbarHostState.showSnackbar(loadingMsg) }
                scope.launch {
                    try {
                        val track = viewModel.getTrackInfo(result.url)
                        playerViewModel.addToQueue(track)
                    } catch (_: Exception) {
                        snackbarHostState.showSnackbar(failedLoadMsg)
                    }
                }
            },
            onAddToPlaylist = {
                val ctx = result
                contextResult = null
                scope.launch { snackbarHostState.showSnackbar(loadingMsg) }
                scope.launch {
                    try {
                        val track = viewModel.getTrackInfo(ctx.url)
                        addToPlaylistTrackId = track.id
                    } catch (_: Exception) {
                        snackbarHostState.showSnackbar(failedLoadMsg)
                    }
                }
            },
            onPlayAll = {
                contextResult = null
                scope.launch { snackbarHostState.showSnackbar(loadingMsg) }
                scope.launch {
                    try {
                        val tracks = when (result.type) {
                            SearchResultType.SPOTIFY_ALBUM -> viewModel.resolveAlbumTracks(result.url)
                            SearchResultType.SPOTIFY_PLAYLIST -> viewModel.resolvePlaylistTracks(result.url)
                            else -> emptyList()
                        }
                        if (tracks.isNotEmpty()) {
                            playerViewModel.playAlbum(tracks, 0)
                            onExpandPlayer()
                        }
                    } catch (_: Exception) {
                        snackbarHostState.showSnackbar(failedLoadMsg)
                    }
                }
            },
            onEnqueueAll = {
                contextResult = null
                scope.launch { snackbarHostState.showSnackbar(loadingMsg) }
                scope.launch {
                    try {
                        val tracks = when (result.type) {
                            SearchResultType.SPOTIFY_ALBUM -> viewModel.resolveAlbumTracks(result.url)
                            SearchResultType.SPOTIFY_PLAYLIST -> viewModel.resolvePlaylistTracks(result.url)
                            else -> emptyList()
                        }
                        playerViewModel.addAllToQueue(tracks)
                    } catch (_: Exception) {
                        snackbarHostState.showSnackbar(failedLoadMsg)
                    }
                }
            },
            onShare = {
                contextResult = null
                context.shareUrl(webUrl, result.name)
            },
            onOpenInBrowser = {
                contextResult = null
                context.openInBrowser(webUrl)
            },
        )
    }

    addToPlaylistTrackId?.let { trackId ->
        AddToPlaylistSheet(
            playlists = playerState.playlists,
            onDismiss = { addToPlaylistTrackId = null },
            onPlaylistSelected = { playlistId ->
                playerViewModel.addTrackToPlaylist(playlistId, trackId)
                addToPlaylistTrackId = null
            },
            onCreatePlaylist = { name, shapeKey, iconUrl ->
                playerViewModel.createPlaylistAndAddArbitraryTrack(name, shapeKey, iconUrl, trackId)
                addToPlaylistTrackId = null
            },
        )
    }
}

private fun spotifyUriToWebUrl(uri: String): String {
    if (uri.startsWith("https://")) return uri
    val parts = uri.removePrefix("spotify:").split(":")
    if (parts.size < 2) return uri
    val kind = parts[0]
    val id = parts[1]
    return "https://open.spotify.com/$kind/$id"
}
