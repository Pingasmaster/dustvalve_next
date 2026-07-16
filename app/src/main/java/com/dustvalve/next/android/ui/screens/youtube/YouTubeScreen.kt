package com.dustvalve.next.android.ui.screens.youtube

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
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
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
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
import androidx.compose.ui.graphics.Brush
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
import com.dustvalve.next.android.ui.components.AppButtonGroup
import com.dustvalve.next.android.ui.components.PastedLinkChip
import com.dustvalve.next.android.ui.components.RecentSearchesList
import com.dustvalve.next.android.ui.components.lists.segmentedItemPadding
import com.dustvalve.next.android.ui.components.sheet.AddToPlaylistSheet
import com.dustvalve.next.android.ui.components.sheet.RemoteResultActionSheet
import com.dustvalve.next.android.ui.screens.player.PlayerViewModel
import com.dustvalve.next.android.ui.theme.AppShapes
import com.dustvalve.next.android.ui.theme.segmentedItemShape
import com.dustvalve.next.android.util.DeepLinkRouter
import com.dustvalve.next.android.util.openInBrowser
import com.dustvalve.next.android.util.shareUrl
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)
@Composable
fun YouTubeScreen(
    onPlaylistClick: (url: String, name: String) -> Unit,
    onArtistClick: (url: String, name: String, imageUrl: String?) -> Unit,
    onOpenLink: (String) -> Unit,
    modifier: Modifier = Modifier,
    onExpandPlayer: () -> Unit = {},
    playerViewModel: PlayerViewModel = hiltViewModel(),
    viewModel: YouTubeViewModel = hiltViewModel(),
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
    val loadingPlaylistMsg = stringResource(R.string.common_loading_playlist)
    val failedLoadMsg = stringResource(R.string.snackbar_failed_load)

    val searchBarState = rememberSearchBarState()
    val textFieldState = rememberTextFieldState()
    val detectedLink = remember(textFieldState.text.toString()) {
        DeepLinkRouter.detect(textFieldState.text.toString())
    }
    val searchListState = rememberLazyListState()

    // Always open the YouTube tab on the sub-tab configured in Settings. Fires once
    // per tab visit because the screen leaves/re-enters composition on navigation.
    LaunchedEffect(Unit) { viewModel.applyDefaultSource() }

    LaunchedEffect(textFieldState) {
        snapshotFlow { textFieldState.text.toString() }
            .collect { viewModel.onQueryChange(it) }
    }

    // Search pagination
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

    val errorText = state.error?.asString()
    LaunchedEffect(errorText) {
        val error = errorText ?: return@LaunchedEffect
        if (state.results.isNotEmpty()) {
            try {
                snackbarHostState.showSnackbar(error)
            } finally {
                viewModel.clearError()
            }
        }
    }

    val onPlayItem: (SearchResult) -> Unit = { item ->
        scope.launch {
            try {
                val track = viewModel.getTrackInfo(item.url)
                playerViewModel.playTrack(track)
                onExpandPlayer()
            } catch (_: Exception) {
                snackbarHostState.showSnackbar(failedToPlayMsg)
            }
        }
    }

    val onPlayVideoId: (String) -> Unit = { videoId ->
        scope.launch {
            try {
                val track = viewModel.getTrackInfo("https://www.youtube.com/watch?v=$videoId")
                playerViewModel.playTrack(track)
                onExpandPlayer()
            } catch (_: Exception) {
                snackbarHostState.showSnackbar(failedToPlayMsg)
            }
        }
    }

    val openPlaylistById: (String, String) -> Unit = { id, name ->
        val stripped = id.removePrefix("VL")
        onPlaylistClick("https://www.youtube.com/playlist?list=$stripped", name)
    }

    val inputField = @Composable {
        SearchBarDefaults.InputField(
            searchBarState = searchBarState,
            textFieldState = textFieldState,
            onSearch = {
                val q = textFieldState.text.toString()
                if (detectedLink != null || DeepLinkRouter.looksLikeUrl(q)) {
                    onOpenLink(q)
                } else {
                    viewModel.onSearch()
                }
            },
            placeholder = {
                Text(
                    text = stringResource(R.string.youtube_search_placeholder),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
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
        modifier = modifier,
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

                // YouTube / YouTube Music sub-tab toggle
                val ytSources = listOf(YouTubeSource.YouTube, YouTubeSource.YouTubeMusic)
                val ytSourceLabels = listOf(
                    stringResource(R.string.youtube_tab_source_yt),
                    stringResource(R.string.youtube_tab_source_ytm),
                )
                AppButtonGroup(
                    overflowIndicator = { _ -> },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    ytSources.forEachIndexed { index, src ->
                        customItem(
                            buttonGroupContent = {
                                ToggleButton(
                                    checked = state.activeSource == src,
                                    onCheckedChange = { isChecked ->
                                        if (isChecked) viewModel.setActiveSource(src)
                                    },
                                    modifier = Modifier.weight(1f),
                                    shapes = when (index) {
                                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                        ytSources.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                                    },
                                ) {
                                    Text(ytSourceLabels[index])
                                }
                            },
                            menuContent = {},
                        )
                    }
                }

                AnimatedContent(
                    targetState = state.activeSource,
                    label = "youtube_source_switch",
                    modifier = Modifier.fillMaxSize(),
                ) { source ->
                    when (source) {
                        YouTubeSource.YouTubeMusic -> YouTubeMusicHome(
                            state = state,
                            onChipSelected = viewModel::onYtmChipSelected,
                            onPlaySong = { song -> onPlayVideoId(song.videoId) },
                            onPlayHero = { hero ->
                                val videoId = hero.videoId
                                val playlistId = hero.playlistId
                                when {
                                    videoId != null -> onPlayVideoId(videoId)
                                    playlistId != null -> openPlaylistById(playlistId, hero.title)
                                    else -> scope.launch { snackbarHostState.showSnackbar(failedLoadMsg) }
                                }
                            },
                            onOpenTile = { tile ->
                                when (tile.kind) {
                                    com.dustvalve.next.android.domain.model.TileKind.SONG,
                                    com.dustvalve.next.android.domain.model.TileKind.VIDEO,
                                    ->
                                        onPlayVideoId(tile.id)

                                    com.dustvalve.next.android.domain.model.TileKind.ALBUM,
                                    com.dustvalve.next.android.domain.model.TileKind.PLAYLIST,
                                    ->
                                        openPlaylistById(tile.id, tile.title)
                                }
                            },
                            onOpenArtist = { artist ->
                                onArtistClick(
                                    "https://www.youtube.com/channel/${artist.browseId}",
                                    artist.name,
                                    artist.thumbnailUrl,
                                )
                            },
                            onRetry = { viewModel.retryYtmHome() },
                        )

                        YouTubeSource.YouTube -> YouTubeSourceContent(
                            state = state,
                            onMoodSelect = viewModel::onMoodSelected,
                            onPlayItem = onPlayItem,
                            onRetrySection = viewModel::retrySection,
                            onLoadMoreGenres = { viewModel.loadMoreGenres() },
                        )
                    }
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
                        label = { Text(stringResource(R.string.youtube_tab_all)) },
                    )
                    FilterChip(
                        selected = state.selectedFilter == "artists",
                        onClick = { viewModel.onFilterSelected("artists") },
                        label = { Text(stringResource(R.string.youtube_tab_artists)) },
                    )
                    FilterChip(
                        selected = state.selectedFilter == "playlists",
                        onClick = { viewModel.onFilterSelected("playlists") },
                        label = { Text(stringResource(R.string.youtube_tab_playlists)) },
                    )
                    FilterChip(
                        selected = state.selectedFilter == "songs",
                        onClick = { viewModel.onFilterSelected("songs") },
                        label = { Text(stringResource(R.string.youtube_tab_tracks)) },
                    )
                }

                // Inline "open this pasted link" affordance
                PastedLinkChip(
                    detected = detectedLink,
                    onClick = { onOpenLink(textFieldState.text.toString()) },
                )

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
                                    text = state.error?.asString() ?: stringResource(R.string.common_search_failed),
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
                                            .padding(segmentedItemPadding(index, state.results.size))
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
                                                        SearchResultType.YOUTUBE_TRACK -> {
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

                                                        SearchResultType.YOUTUBE_PLAYLIST,
                                                        SearchResultType.YOUTUBE_ALBUM,
                                                        -> {
                                                            scope.launch { searchBarState.animateToCollapsed() }
                                                            onPlaylistClick(result.url, result.name)
                                                        }

                                                        SearchResultType.YOUTUBE_ARTIST -> {
                                                            scope.launch { searchBarState.animateToCollapsed() }
                                                            onArtistClick(result.url, result.name, result.imageUrl)
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
                                            supportingContent = {
                                                result.artist?.let {
                                                    Text(
                                                        text = it,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                    )
                                                }
                                            },
                                            leadingContent = {
                                                val thumbnailShape = when (result.type) {
                                                    SearchResultType.YOUTUBE_ARTIST -> AppShapes.SearchResultArtist

                                                    SearchResultType.YOUTUBE_ALBUM,
                                                    SearchResultType.YOUTUBE_PLAYLIST,
                                                    -> AppShapes.SearchResultAlbum

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
                                                        SearchResultType.YOUTUBE_ARTIST -> R.drawable.ic_person

                                                        SearchResultType.YOUTUBE_ALBUM,
                                                        SearchResultType.YOUTUBE_PLAYLIST,
                                                        -> R.drawable.ic_album

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
                                                if (result.type == SearchResultType.YOUTUBE_PLAYLIST ||
                                                    result.type == SearchResultType.YOUTUBE_ALBUM
                                                ) {
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
                                        ) {
                                            Text(
                                                text = result.name,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
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
        RemoteResultActionSheet(
            result = result,
            onDismiss = { contextResult = null },
            onPlayNext = {
                contextResult = null
                scope.launch { snackbarHostState.showSnackbar(loadingTrackMsg) }
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
                scope.launch { snackbarHostState.showSnackbar(loadingTrackMsg) }
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
                scope.launch { snackbarHostState.showSnackbar(loadingTrackMsg) }
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
                scope.launch { snackbarHostState.showSnackbar(loadingPlaylistMsg) }
                scope.launch {
                    try {
                        val tracks = viewModel.resolvePlaylistTracks(result.url)
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
                scope.launch { snackbarHostState.showSnackbar(loadingPlaylistMsg) }
                scope.launch {
                    try {
                        val tracks = viewModel.resolvePlaylistTracks(result.url)
                        playerViewModel.addAllToQueue(tracks)
                    } catch (_: Exception) {
                        snackbarHostState.showSnackbar(failedLoadMsg)
                    }
                }
            },
            onShare = {
                contextResult = null
                context.shareUrl(result.url, result.name)
            },
            onOpenInBrowser = {
                contextResult = null
                context.openInBrowser(result.url)
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

// -- YouTube discover feed (Material 3 Expressive) -----------------------

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun YouTubeSourceContent(
    state: YouTubeUiState,
    onMoodSelect: (MoodChip?) -> Unit,
    onPlayItem: (SearchResult) -> Unit,
    onRetrySection: (String) -> Unit,
    onLoadMoreGenres: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        item(key = "yt_moods") {
            MoodToggleRow(
                selectedMood = state.selectedMood,
                onMoodSelect = onMoodSelect,
            )
        }

        if (state.selectedMood != null) {
            item(key = "yt_mood_header") {
                Text(
                    text = stringResource(state.selectedMood.labelRes),
                    style = MaterialTheme.typography.displaySmallEmphasized,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp)
                        .animateItem(),
                )
            }
            when {
                state.isMoodLoading -> item(key = "yt_mood_loading") {
                    FeedSkeletonRow(modifier = Modifier.animateItem())
                }

                state.moodError != null -> item(key = "yt_mood_error") {
                    FeedErrorCard(
                        message = state.moodError.asString(),
                        onRetry = { onMoodSelect(state.selectedMood) },
                        modifier = Modifier.animateItem(),
                    )
                }

                else -> itemsIndexed(
                    items = state.moodResults.chunked(2),
                    key = { index, _ -> "yt_mood_row_$index" },
                ) { _, pair ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .animateItem(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        pair.forEach { item ->
                            VideoGridCard(
                                item = item,
                                onClick = { onPlayItem(item) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (pair.size == 1) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        } else {
            val reco = state.recommendationsSection
            if (reco.isLoading || reco.items.isNotEmpty()) {
                item(key = "yt_reco") {
                    DiscoverShelf(
                        section = reco,
                        onItemClick = onPlayItem,
                        onRetry = { onRetrySection("recommendations") },
                        modifier = Modifier.animateItem(),
                    )
                }
            }

            item(key = "yt_trending") {
                DiscoverShelf(
                    section = state.trendingSection,
                    onItemClick = onPlayItem,
                    onRetry = { onRetrySection("trending") },
                    showHero = true,
                    modifier = Modifier.animateItem(),
                )
            }

            itemsIndexed(
                items = state.genreSections,
                key = { index, _ -> "yt_genre_$index" },
            ) { index, section ->
                DiscoverShelf(
                    section = section,
                    onItemClick = onPlayItem,
                    onRetry = { onRetrySection("genre_$index") },
                    modifier = Modifier.animateItem(),
                )
            }

            if (!state.genresExhausted) {
                item(key = "yt_genre_loader") {
                    LaunchedEffect(state.genreSections.size) {
                        onLoadMoreGenres()
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                            .animateItem(),
                        contentAlignment = Alignment.Center,
                    ) {
                        ContainedLoadingIndicator()
                    }
                }
            }
        }
    }
}

// -- Mood filter row -----------------------------------------------------

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MoodToggleRow(selectedMood: MoodChip?, onMoodSelect: (MoodChip?) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        moodChips.forEach { chip ->
            val isSelected = selectedMood == chip
            ToggleButton(
                checked = isSelected,
                onCheckedChange = { onMoodSelect(if (isSelected) null else chip) },
            ) {
                Text(stringResource(chip.labelRes), maxLines = 1)
            }
        }
    }
}

// -- Discover shelf: header + hero/carousel with load & error states ----

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DiscoverShelf(
    section: DiscoverSection,
    onItemClick: (SearchResult) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    showHero: Boolean = false,
) {
    Column(modifier = modifier) {
        section.title?.let { title ->
            Text(
                text = title.asString(),
                style = MaterialTheme.typography.titleLargeEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        when {
            section.isLoading -> FeedSkeletonRow()

            section.error != null -> FeedErrorCard(message = section.error, onRetry = onRetry)

            section.items.isNotEmpty() -> {
                if (showHero) {
                    VideoHeroCard(
                        item = section.items.first(),
                        onClick = { onItemClick(section.items.first()) },
                    )
                    if (section.items.size > 1) {
                        Spacer(Modifier.height(12.dp))
                        VideoCarousel(items = section.items.drop(1), onItemClick = onItemClick)
                    }
                } else {
                    VideoCarousel(items = section.items, onItemClick = onItemClick)
                }
            }
        }
    }
}

// -- Video cards ---------------------------------------------------------

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun VideoHeroCard(item: SearchResult, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .aspectRatio(16f / 9f)
            .clip(MaterialTheme.shapes.extraLarge)
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = item.imageUrl,
            contentDescription = item.name,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.35f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.8f),
                    ),
                ),
        )
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.headlineSmallEmphasized,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                item.artist?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            FilledTonalIconButton(
                onClick = onClick,
                shapes = IconButtonDefaults.shapes(),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_play_arrow),
                    contentDescription = stringResource(R.string.common_play),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun VideoCarousel(items: List<SearchResult>, onItemClick: (SearchResult) -> Unit) {
    val carouselState = rememberCarouselState { items.size }
    HorizontalMultiBrowseCarousel(
        state = carouselState,
        preferredItemWidth = 240.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        itemSpacing = 8.dp,
    ) { index ->
        val item = items[index]
        Box(
            modifier = Modifier
                .aspectRatio(16f / 9f)
                .maskClip(MaterialTheme.shapes.large)
                .clickable { onItemClick(item) },
        ) {
            AsyncImage(
                model = item.imageUrl,
                contentDescription = item.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.5f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.75f),
                        ),
                    ),
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp),
            ) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleSmallEmphasized,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                item.artist?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun VideoGridCard(item: SearchResult, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            AsyncImage(
                model = item.imageUrl,
                contentDescription = item.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = item.name,
            style = MaterialTheme.typography.titleSmallEmphasized,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        item.artist?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// -- Loading & error states ----------------------------------------------

@Composable
private fun FeedSkeletonRow(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "yt_skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "skeleton_alpha",
    )
    val bone = MaterialTheme.colorScheme.surfaceContainerHigh
    Row(
        modifier = modifier.padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(2) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(16f / 9f)
                    .graphicsLayer { this.alpha = alpha }
                    .background(bone, MaterialTheme.shapes.large),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FeedErrorCard(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(16.dp))
            FilledTonalButton(onClick = onRetry, shapes = ButtonDefaults.shapes()) {
                Icon(
                    painter = painterResource(R.drawable.ic_refresh),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.common_action_retry))
            }
        }
    }
}
