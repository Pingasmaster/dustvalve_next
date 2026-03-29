package com.dustvalve.next.android.ui.screens.youtube

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.dustvalve.next.android.R
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.dustvalve.next.android.domain.model.SearchResult
import com.dustvalve.next.android.domain.model.SearchResultType
import com.dustvalve.next.android.ui.components.RecentSearchesList
import com.dustvalve.next.android.ui.screens.player.PlayerViewModel
import com.dustvalve.next.android.ui.theme.AppShapes
import com.dustvalve.next.android.ui.theme.segmentedItemShape
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun YouTubeScreen(
    playerViewModel: PlayerViewModel,
    onPlaylistClick: (url: String, name: String) -> Unit,
    onArtistClick: (url: String, name: String, imageUrl: String?) -> Unit,
    onExpandPlayer: () -> Unit = {},
    viewModel: YouTubeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val recentSearches by viewModel.recentSearches.collectAsStateWithLifecycle()
    val searchHistoryEnabled by viewModel.searchHistoryEnabled.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val failedToPlayMsg = stringResource(R.string.common_failed_to_play)

    val searchBarState = rememberSearchBarState()
    val textFieldState = rememberTextFieldState()
    val searchListState = rememberLazyListState()

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

    val inputField = @Composable {
        SearchBarDefaults.InputField(
            searchBarState = searchBarState,
            textFieldState = textFieldState,
            onSearch = { viewModel.onSearch() },
            placeholder = { Text(stringResource(R.string.youtube_search_placeholder)) },
            leadingIcon = {
                Icon(
                    painter = painterResource(R.drawable.ic_search),
                    contentDescription = stringResource(R.string.common_cd_search),
                )
            },
            trailingIcon = {
                if (textFieldState.text.isNotEmpty()) {
                    IconButton(onClick = {
                        textFieldState.setTextAndPlaceCursorAtEnd("")
                    }) {
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

                // Mood chips — pinned below search bar
                MoodChipRow(
                    selectedMood = state.selectedMood,
                    onMoodSelected = viewModel::onMoodSelected,
                )

                // Discovery feed
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 80.dp),
                ) {
                    if (state.selectedMood != null) {
                        // ── Mood results ────────────────────────────────
                        item(key = "mood_section") {
                            val moodSection = DiscoverSection(
                                title = "${state.selectedMood} music",
                                items = state.moodResults,
                                isLoading = state.isMoodLoading,
                                error = state.moodError,
                            )
                            DiscoverCarouselSection(
                                section = moodSection,
                                onItemClick = onPlayItem,
                                onRetry = { viewModel.onMoodSelected(state.selectedMood) },
                            )
                        }
                    } else {
                        // ── Default discovery feed ──────────────────────

                        // Recommendations (skip if no history)
                        val recoSection = state.recommendationsSection
                        if (recoSection.isLoading || recoSection.items.isNotEmpty()) {
                            item(key = "reco_section") {
                                DiscoverCarouselSection(
                                    section = recoSection,
                                    onItemClick = onPlayItem,
                                    onRetry = { viewModel.retrySection("recommendations") },
                                )
                            }
                        }

                        // Trending
                        item(key = "trending_section") {
                            DiscoverCarouselSection(
                                section = state.trendingSection,
                                onItemClick = onPlayItem,
                                onRetry = { viewModel.retrySection("trending") },
                            )
                        }

                        // Genre sections
                        itemsIndexed(
                            items = state.genreSections,
                            key = { index, _ -> "genre_$index" },
                        ) { index, section ->
                            DiscoverCarouselSection(
                                section = section,
                                onItemClick = onPlayItem,
                                onRetry = { viewModel.retrySection("genre_$index") },
                            )
                        }

                        // Infinite scroll trigger
                        if (!state.genresExhausted) {
                            item(key = "genre_loader") {
                                LaunchedEffect(state.genreSections.size) {
                                    viewModel.loadMoreGenres()
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularWavyProgressIndicator(modifier = Modifier.size(24.dp))
                                }
                            }
                        }
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
                                contentPadding = PaddingValues(bottom = 80.dp),
                                modifier = Modifier.fillMaxSize(),
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
                                                SearchResultType.YOUTUBE_ALBUM -> {
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
                                        interactionSource = interactionSource,
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
                                            },
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
                                                    SearchResultType.YOUTUBE_PLAYLIST -> AppShapes.SearchResultAlbum
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
                                                        SearchResultType.YOUTUBE_PLAYLIST -> R.drawable.ic_album
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
                                                    result.type == SearchResultType.YOUTUBE_ALBUM) {
                                                    val importedMsg = stringResource(R.string.common_playlist_imported, result.name)
                                                    IconButton(
                                                        onClick = {
                                                            scope.launch {
                                                                viewModel.importPlaylist(result.url, result.name)
                                                                snackbarHostState.showSnackbar(importedMsg)
                                                            }
                                                        },
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

                                // Loading indicator at bottom
                                if (state.isLoading && state.results.isNotEmpty()) {
                                    item {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            CircularWavyProgressIndicator(modifier = Modifier.size(24.dp))
                                        }
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
}

// ── Reusable composables ────────────────────────────────────────────────

@Composable
private fun MoodChipRow(
    selectedMood: String?,
    onMoodSelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        moodChips.forEach { chip ->
            val isSelected = selectedMood == chip.label
            FilterChip(
                selected = isSelected,
                onClick = { onMoodSelected(if (isSelected) null else chip.label) },
                label = { Text(chip.label) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DiscoverCarouselSection(
    section: DiscoverSection,
    onItemClick: (SearchResult) -> Unit,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        if (section.title.isNotBlank()) {
            Text(
                text = section.title,
                style = MaterialTheme.typography.headlineMediumEmphasized,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        when {
            section.isLoading -> {
                ShimmerCarouselPlaceholder()
            }
            section.error != null -> {
                SectionErrorState(
                    message = section.error,
                    onRetry = onRetry,
                )
            }
            section.items.isNotEmpty() -> {
                val carouselState = rememberCarouselState { section.items.size }
                HorizontalMultiBrowseCarousel(
                    state = carouselState,
                    preferredItemWidth = 240.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    itemSpacing = 8.dp,
                ) { index ->
                    val item = section.items[index]
                    Box(
                        modifier = Modifier
                            .aspectRatio(16f / 9f)
                            .maskClip(AppShapes.SearchResultTrack)
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
                                .fillMaxWidth()
                                .height(64.dp)
                                .align(Alignment.BottomCenter)
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)),
                                    )
                                ),
                        )
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                        ) {
                            Text(
                                text = item.name,
                                style = MaterialTheme.typography.titleSmall,
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
        }
    }
}

@Composable
private fun ShimmerCarouselPlaceholder(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "shimmerAlpha",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(3) {
            Box(
                modifier = Modifier
                    .width(240.dp)
                    .aspectRatio(16f / 9f)
                    .clip(MaterialTheme.shapes.medium)
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = alpha)
                    ),
            )
        }
    }
}

@Composable
private fun SectionErrorState(
    message: String,
    onRetry: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (onRetry != null) {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onRetry) {
                    Text(stringResource(R.string.common_action_retry))
                }
            }
        }
    }
}
