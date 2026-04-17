package com.dustvalve.next.android.ui.screens.bandcamp

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.dustvalve.next.android.R
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.dustvalve.next.android.domain.model.Album
import com.dustvalve.next.android.domain.model.SearchResult
import com.dustvalve.next.android.domain.model.SearchResultType
import com.dustvalve.next.android.ui.components.RecentSearchesList
import com.dustvalve.next.android.ui.components.sheet.AddToPlaylistSheet
import com.dustvalve.next.android.ui.components.sheet.RemoteResultActionSheet
import com.dustvalve.next.android.ui.screens.player.PlayerViewModel
import com.dustvalve.next.android.ui.screens.search.SearchViewModel
import com.dustvalve.next.android.util.openInBrowser
import com.dustvalve.next.android.util.shareUrl
import com.dustvalve.next.android.ui.theme.AppMotion
import com.dustvalve.next.android.ui.theme.AppShapes
import com.dustvalve.next.android.ui.theme.segmentedItemShape
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class GenreCategory(
    val name: String,
    val tag: String,
    val color: Color,
)

private val discoverCategories = listOf(
    GenreCategory("best selling", "", Color(0xFFF7A664)),
    GenreCategory("rock", "rock", Color(0xFFF67356)),
    GenreCategory("hip-hop/rap", "hip-hop-rap", Color(0xFFDF2535)),
    GenreCategory("alternative", "alternative", Color(0xFFD4356D)),
    GenreCategory("electronic", "electronic", Color(0xFFC13EA2)),
    GenreCategory("metal", "metal", Color(0xFF985BBE)),
    GenreCategory("experimental", "experimental", Color(0xFF8171B1)),
    GenreCategory("pop", "pop", Color(0xFF8690CB)),
    GenreCategory("jazz", "jazz", Color(0xFF87A8C4)),
    GenreCategory("blues", "blues", Color(0xFF88BFBC)),
    GenreCategory("punk", "punk", Color(0xFF83D048)),
    GenreCategory("r&b/soul", "r-b-soul", Color(0xFFB0C846)),
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun BandcampScreen(
    onAlbumClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    playerViewModel: PlayerViewModel,
    onExpandPlayer: () -> Unit = {},
    viewModel: BandcampViewModel = hiltViewModel(),
    searchViewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val searchState by searchViewModel.uiState.collectAsStateWithLifecycle()
    val playerState by playerViewModel.uiState.collectAsStateWithLifecycle()
    val recentSearches by searchViewModel.recentSearches.collectAsStateWithLifecycle()
    val searchHistoryEnabled by searchViewModel.searchHistoryEnabled.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedCategoryColor by remember { mutableStateOf(Color.Unspecified) }

    val searchBarState = rememberSearchBarState()
    val textFieldState = rememberTextFieldState()
    val searchListState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current

    var contextResult by remember { mutableStateOf<SearchResult?>(null) }
    var addToPlaylistTrackId by remember { mutableStateOf<String?>(null) }
    val loadingTrackMsg = stringResource(R.string.common_loading_track)
    val loadingAlbumMsg = stringResource(R.string.common_loading_album)
    val failedLoadMsg = stringResource(R.string.snackbar_failed_load)

    // Bridge TextFieldState changes to SearchViewModel
    LaunchedEffect(textFieldState) {
        snapshotFlow { textFieldState.text.toString() }
            .collect { searchViewModel.onQueryChange(it) }
    }

    // Reset search scroll when results change
    LaunchedEffect(searchState.searchGeneration) {
        searchListState.scrollToItem(0)
    }

    // Show search pagination errors via snackbar
    LaunchedEffect(searchState.error) {
        val error = searchState.error ?: return@LaunchedEffect
        try {
            if (searchState.results.isNotEmpty()) {
                snackbarHostState.showSnackbar(error)
            }
        } finally {
            searchViewModel.clearError()
        }
    }

    // Detect scroll to end for search pagination
    LaunchedEffect(searchListState) {
        snapshotFlow {
            val last = searchListState.layoutInfo.visibleItemsInfo.lastOrNull()
            val totalCount = searchListState.layoutInfo.totalItemsCount
            val nearEnd = last != null && totalCount > 0 && last.index >= totalCount - 3
            nearEnd to totalCount
        }.collect { (nearEnd, _) ->
            val currentState = searchViewModel.uiState.value
            if (nearEnd && currentState.hasMore && !currentState.isLoading && currentState.results.isNotEmpty()) {
                searchViewModel.loadMore()
            }
        }
    }

    // Category albums bottom sheet
    if (state.showCategorySheet) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.dismissCategory() },
            containerColor = selectedCategoryColor,
            contentColor = Color.White,
        ) {
            Text(
                text = state.selectedGenreName,
                style = MaterialTheme.typography.headlineMediumEmphasized,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )

            // Sub-tag filter chips
            if (state.availableSubTags.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val chipColors = FilterChipDefaults.filterChipColors(
                        containerColor = Color.White.copy(alpha = 0.15f),
                        labelColor = Color.White,
                        selectedContainerColor = Color.White.copy(alpha = 0.35f),
                        selectedLabelColor = Color.White,
                    )
                    FilterChip(
                        selected = state.selectedSubTag == null,
                        onClick = { viewModel.selectSubTag(null) },
                        label = { Text(stringResource(R.string.bandcamp_tab_all)) },
                        colors = chipColors,
                    )
                    state.availableSubTags.forEach { subTag ->
                        FilterChip(
                            selected = state.selectedSubTag == subTag.slug,
                            onClick = { viewModel.selectSubTag(subTag.slug) },
                            label = { Text(subTag.label) },
                            colors = chipColors,
                        )
                    }
                }
            }

            if (state.isCategoryLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    ContainedLoadingIndicator()
                }
            } else if (state.categoryError != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = state.categoryError ?: stringResource(R.string.bandcamp_something_went_wrong),
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White.copy(alpha = 0.7f),
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.retryCategory() }, shapes = ButtonDefaults.shapes()) {
                            Text(stringResource(R.string.common_action_retry))
                        }
                    }
                }
            } else {
                CategorySheetContent(
                    albums = state.categoryAlbums,
                    onAlbumClick = { url ->
                        viewModel.dismissCategory()
                        onAlbumClick(url)
                    },
                )
            }
        }
    }

    val inputField = @Composable {
        SearchBarDefaults.InputField(
            searchBarState = searchBarState,
            textFieldState = textFieldState,
            onSearch = { searchViewModel.onSearch() },
            placeholder = { Text(stringResource(R.string.bandcamp_search_placeholder)) },
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
            // Main content: search bar + discover categories
            Column(modifier = Modifier.fillMaxSize()) {
                // Collapsed search bar at the top
                SearchBar(
                    state = searchBarState,
                    inputField = inputField,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )

                // Discover category rows
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp),
                ) {
                    item(key = "discover_header") {
                        StaggeredAnimatedItem(index = 0) {
                            Text(
                                text = stringResource(R.string.bandcamp_discover),
                                style = MaterialTheme.typography.headlineMediumEmphasized,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                        }
                    }

                    itemsIndexed(
                        items = discoverCategories,
                        key = { _, cat -> "cat_${cat.name}" },
                    ) { index, category ->
                        val previews = state.categoryPreviews[category.tag] ?: emptyList()
                        StaggeredAnimatedItem(index = index + 1) {
                            Surface(
                                onClick = {
                                    selectedCategoryColor = category.color
                                    viewModel.selectCategory(category.tag, category.name)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(72.dp),
                                color = category.color,
                                contentColor = Color.White,
                            ) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    Text(
                                        text = category.name,
                                        style = MaterialTheme.typography.titleMediumEmphasized,
                                        modifier = Modifier
                                            .align(Alignment.CenterStart)
                                            .padding(horizontal = 20.dp),
                                    )
                                    // Fan of 3 tilted album covers on the right
                                    if (previews.isNotEmpty()) {
                                        val artSize = 52.dp
                                        val tilts = listOf(-8f, 0f, 8f)
                                        val offsets = listOf((-6).dp, 0.dp, 6.dp)
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.CenterEnd)
                                                .padding(end = 12.dp),
                                        ) {
                                            previews.take(3).forEachIndexed { i, album ->
                                                AsyncImage(
                                                    model = album.artUrl,
                                                    contentDescription = null,
                                                    modifier = Modifier
                                                        .size(artSize)
                                                        .offset(x = ((i - 1) * 30).dp, y = offsets[i])
                                                        .graphicsLayer { rotationZ = tilts[i] }
                                                        .clip(MaterialTheme.shapes.small),
                                                    contentScale = ContentScale.Crop,
                                                    error = painterResource(R.drawable.ic_album),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // "view more" row
                    item(key = "view_more") {
                        StaggeredAnimatedItem(index = discoverCategories.size + 1) {
                            Surface(
                                onClick = { /* TODO */ },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                                color = Color(0xFF818285),
                                contentColor = Color.White,
                            ) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    Text(
                                        text = stringResource(R.string.bandcamp_view_more),
                                        style = MaterialTheme.typography.titleMediumEmphasized,
                                        modifier = Modifier
                                            .align(Alignment.CenterStart)
                                            .padding(horizontal = 20.dp),
                                    )
                                    Icon(
                                        painter = painterResource(R.drawable.ic_chevron_right),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .align(Alignment.CenterEnd)
                                            .padding(end = 16.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Expanded full-screen search overlay
            ExpandedFullScreenSearchBar(
                state = searchBarState,
                inputField = inputField,
            ) {
                // Type filter chips
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = searchState.selectedType == null,
                        onClick = { searchViewModel.onTypeSelected(null) },
                        label = { Text(stringResource(R.string.bandcamp_tab_all)) },
                    )
                    FilterChip(
                        selected = searchState.selectedType == SearchResultType.ARTIST,
                        onClick = { searchViewModel.onTypeSelected(SearchResultType.ARTIST) },
                        label = { Text(stringResource(R.string.bandcamp_tab_artists)) },
                    )
                    FilterChip(
                        selected = searchState.selectedType == SearchResultType.ALBUM,
                        onClick = { searchViewModel.onTypeSelected(SearchResultType.ALBUM) },
                        label = { Text(stringResource(R.string.bandcamp_tab_albums)) },
                    )
                    FilterChip(
                        selected = searchState.selectedType == SearchResultType.TRACK,
                        onClick = { searchViewModel.onTypeSelected(SearchResultType.TRACK) },
                        label = { Text(stringResource(R.string.bandcamp_tab_tracks)) },
                    )
                }

                // Results area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    when {
                        searchState.query.isBlank() -> {
                            if (searchHistoryEnabled && recentSearches.isNotEmpty()) {
                                RecentSearchesList(
                                    recentSearches = recentSearches,
                                    onSearchClick = { query ->
                                        textFieldState.setTextAndPlaceCursorAtEnd(query)
                                        searchViewModel.onSearch()
                                    },
                                    onRemoveClick = { searchViewModel.removeRecentSearch(it) },
                                    onClearAllClick = { searchViewModel.clearRecentSearches() },
                                )
                            }
                        }
                        searchState.isLoading && searchState.results.isEmpty() -> {
                            ContainedLoadingIndicator(
                                modifier = Modifier.align(Alignment.Center),
                            )
                        }
                        searchState.error != null && searchState.results.isEmpty() -> {
                            Text(
                                text = searchState.error ?: stringResource(R.string.common_search_failed),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.align(Alignment.Center),
                            )
                        }
                        searchState.results.isEmpty() && searchState.query.isNotBlank() && !searchState.isLoading -> {
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
                                contentPadding = PaddingValues(bottom = 80.dp),
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                itemsIndexed(
                                    items = searchState.results,
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
                                        shape = segmentedItemShape(index, searchState.results.size),
                                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                                        modifier = Modifier
                                            .padding(
                                                start = 16.dp,
                                                end = 16.dp,
                                                top = if (index == 0) 8.dp else 1.dp,
                                                bottom = if (index == searchState.results.lastIndex) 0.dp else 1.dp,
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
                                                    scope.launch { searchBarState.animateToCollapsed() }
                                                    when (result.type) {
                                                        SearchResultType.ALBUM -> onAlbumClick(result.url)
                                                        SearchResultType.ARTIST -> onArtistClick(result.url)
                                                        SearchResultType.TRACK -> {
                                                            searchViewModel.playBandcampTrack(result.url, result.name, playerViewModel)
                                                            onExpandPlayer()
                                                        }
                                                        SearchResultType.LOCAL_TRACK -> {
                                                            val trackId = result.url.removePrefix("local://")
                                                            searchViewModel.playLocalTrack(trackId, playerViewModel)
                                                            onExpandPlayer()
                                                        }
                                                        SearchResultType.YOUTUBE_TRACK, SearchResultType.YOUTUBE_ALBUM,
                                                        SearchResultType.YOUTUBE_ARTIST, SearchResultType.YOUTUBE_PLAYLIST,
                                                        SearchResultType.SPOTIFY_TRACK, SearchResultType.SPOTIFY_ALBUM,
                                                        SearchResultType.SPOTIFY_ARTIST, SearchResultType.SPOTIFY_PLAYLIST -> { /* not applicable */ }
                                                    }
                                                },
                                                onLongClick = {
                                                    if (result.type != SearchResultType.LOCAL_TRACK) {
                                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        contextResult = result
                                                    }
                                                },
                                            ),
                                    ) {
                                        SearchResultItem(result = result)
                                    }
                                }

                                // Loading indicator at bottom
                                if (searchState.isLoading && searchState.results.isNotEmpty()) {
                                    item {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp)
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

                    // Snackbar for search errors
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
                scope.launch {
                    snackbarHostState.showSnackbar(loadingTrackMsg)
                }
                scope.launch {
                    try {
                        val track = searchViewModel.resolveBandcampTrack(result.url, result.name)
                        if (track != null) playerViewModel.playNext(track)
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
                        val track = searchViewModel.resolveBandcampTrack(result.url, result.name)
                        if (track != null) playerViewModel.addToQueue(track)
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
                        val track = searchViewModel.resolveBandcampTrack(ctx.url, ctx.name)
                        if (track != null) addToPlaylistTrackId = track.id
                    } catch (_: Exception) {
                        snackbarHostState.showSnackbar(failedLoadMsg)
                    }
                }
            },
            onPlayAll = {
                contextResult = null
                scope.launch { snackbarHostState.showSnackbar(loadingAlbumMsg) }
                scope.launch {
                    try {
                        val tracks = searchViewModel.resolveBandcampAlbumTracks(result.url)
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
                scope.launch { snackbarHostState.showSnackbar(loadingAlbumMsg) }
                scope.launch {
                    try {
                        val tracks = searchViewModel.resolveBandcampAlbumTracks(result.url)
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

@Composable
private fun SearchResultItem(
    result: SearchResult,
) {
    val thumbnailShape = when (result.type) {
        SearchResultType.ARTIST -> AppShapes.SearchResultArtist
        SearchResultType.ALBUM -> AppShapes.SearchResultAlbum
        SearchResultType.TRACK -> AppShapes.SearchResultTrack
        SearchResultType.LOCAL_TRACK -> AppShapes.SearchResultTrack
        SearchResultType.YOUTUBE_TRACK -> AppShapes.SearchResultTrack
        SearchResultType.YOUTUBE_ALBUM, SearchResultType.YOUTUBE_PLAYLIST -> AppShapes.SearchResultAlbum
        SearchResultType.YOUTUBE_ARTIST -> AppShapes.SearchResultArtist
        SearchResultType.SPOTIFY_TRACK -> AppShapes.SearchResultTrack
        SearchResultType.SPOTIFY_ALBUM, SearchResultType.SPOTIFY_PLAYLIST -> AppShapes.SearchResultAlbum
        SearchResultType.SPOTIFY_ARTIST -> AppShapes.SearchResultArtist
    }

    val artistLabel = stringResource(R.string.bandcamp_type_artist)
    val localLabel = stringResource(R.string.bandcamp_type_local)
    ListItem(
        headlineContent = {
            Text(
                text = result.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            val supporting = buildString {
                when (result.type) {
                    SearchResultType.ARTIST -> append(artistLabel)
                    SearchResultType.ALBUM -> {
                        result.artist?.let { append(it) }
                    }
                    SearchResultType.TRACK -> {
                        result.artist?.let { append(it) }
                        result.album?.let {
                            if (isNotEmpty()) append(" - ")
                            append(it)
                        }
                    }
                    SearchResultType.LOCAL_TRACK -> {
                        append(localLabel)
                        result.artist?.let {
                            append(" \u00B7 ")
                            append(it)
                        }
                        result.album?.let {
                            if (result.artist != null) append(" - ")
                            else append(" \u00B7 ")
                            append(it)
                        }
                    }
                    SearchResultType.YOUTUBE_TRACK, SearchResultType.YOUTUBE_ALBUM,
                    SearchResultType.YOUTUBE_ARTIST, SearchResultType.YOUTUBE_PLAYLIST,
                    SearchResultType.SPOTIFY_TRACK, SearchResultType.SPOTIFY_ALBUM,
                    SearchResultType.SPOTIFY_ARTIST, SearchResultType.SPOTIFY_PLAYLIST -> {
                        result.artist?.let { append(it) }
                    }
                }
                result.genre?.let {
                    if (isNotEmpty()) append(" \u00B7 ")
                    append(it)
                }
            }
            if (supporting.isNotEmpty()) {
                Text(
                    text = supporting,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        leadingContent = {
            val iconRes = when (result.type) {
                SearchResultType.ARTIST -> R.drawable.ic_person
                SearchResultType.ALBUM -> R.drawable.ic_album
                SearchResultType.TRACK -> R.drawable.ic_music_note
                SearchResultType.LOCAL_TRACK -> R.drawable.ic_phone_android
                SearchResultType.YOUTUBE_TRACK -> R.drawable.ic_music_note
                SearchResultType.YOUTUBE_ALBUM, SearchResultType.YOUTUBE_PLAYLIST -> R.drawable.ic_album
                SearchResultType.YOUTUBE_ARTIST -> R.drawable.ic_person
                SearchResultType.SPOTIFY_TRACK -> R.drawable.ic_music_note
                SearchResultType.SPOTIFY_ALBUM, SearchResultType.SPOTIFY_PLAYLIST -> R.drawable.ic_album
                SearchResultType.SPOTIFY_ARTIST -> R.drawable.ic_person
            }
            if (result.imageUrl != null) {
                AsyncImage(
                    model = result.imageUrl,
                    contentDescription = result.name,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(thumbnailShape),
                    error = painterResource(iconRes),
                )
            } else {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CategorySheetContent(
    albums: List<Album>,
    onAlbumClick: (String) -> Unit,
) {
    val carouselAlbums = albums.take(10)
    val listAlbums = albums.drop(10)

    LazyColumn(
        contentPadding = PaddingValues(bottom = 80.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        // Top 10 carousel
        if (carouselAlbums.isNotEmpty()) {
            item(key = "carousel") {
                val carouselState = rememberCarouselState { carouselAlbums.size }
                HorizontalMultiBrowseCarousel(
                    state = carouselState,
                    preferredItemWidth = 200.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .animateItem(),
                    itemSpacing = 8.dp,
                ) { index ->
                    val album = carouselAlbums[index]
                    CarouselAlbumItem(
                        album = album,
                        onClick = { onAlbumClick(album.url) },
                        modifier = Modifier.maskClip(AppShapes.SearchResultTrack),
                    )
                }
            }
        }

        // Section header
        if (listAlbums.isNotEmpty()) {
            item(key = "more_header") {
                Text(
                    text = stringResource(R.string.bandcamp_more),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier
                        .padding(
                            start = 20.dp, end = 20.dp,
                            top = 16.dp, bottom = 8.dp,
                        )
                        .animateItem(),
                )
            }
        }

        // Segmented list (same style as search results)
        itemsIndexed(
            items = listAlbums,
            key = { _, album -> album.id },
        ) { index, album ->
            val interactionSource = remember { MutableInteractionSource() }
            val isPressed by interactionSource.collectIsPressedAsState()
            val pressScale by animateFloatAsState(
                targetValue = if (isPressed) 0.97f else 1f,
                animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                label = "pressScale",
            )

            Surface(
                onClick = { onAlbumClick(album.url) },
                interactionSource = interactionSource,
                shape = segmentedItemShape(index, listAlbums.size),
                color = Color.White.copy(alpha = 0.12f),
                modifier = Modifier
                    .padding(
                        start = 16.dp,
                        end = 16.dp,
                        top = if (index == 0) 0.dp else 1.dp,
                        bottom = if (index == listAlbums.lastIndex) 0.dp else 1.dp,
                    )
                    .animateItem(
                        fadeInSpec = null,
                        fadeOutSpec = null,
                        placementSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                    )
                    .graphicsLayer {
                        scaleX = pressScale
                        scaleY = pressScale
                    },
            ) {
                ListItem(
                    headlineContent = {
                        Text(
                            text = album.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = Color.White,
                        )
                    },
                    supportingContent = {
                        Text(
                            text = album.artist,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = Color.White.copy(alpha = 0.7f),
                        )
                    },
                    leadingContent = {
                        AsyncImage(
                            model = album.artUrl,
                            contentDescription = album.title,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(AppShapes.SearchResultAlbum),
                            contentScale = ContentScale.Crop,
                            error = painterResource(R.drawable.ic_album),
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }
    }
}

@Composable
private fun CarouselAlbumItem(
    album: Album,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = album.artUrl,
            contentDescription = album.title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            error = painterResource(R.drawable.ic_album),
        )
        // Gradient scrim at bottom
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)),
                    )
                ),
        )
        // Text overlay -- extra bottom padding to clear rounded corner clip
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, end = 16.dp, bottom = 20.dp, top = 12.dp),
        ) {
            Text(
                text = album.title,
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = album.artist,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StaggeredAnimatedItem(
    index: Int,
    content: @Composable () -> Unit,
) {
    val progress = remember { Animatable(0f) }
    val spec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    LaunchedEffect(Unit) {
        delay(index * AppMotion.staggerDelay)
        progress.animateTo(1f, spec)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = progress.value
                translationY = (1f - progress.value) * size.height / 4f
            },
    ) {
        content()
    }
}
