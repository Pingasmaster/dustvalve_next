package com.dustvalve.next.android.ui.screens.youtube

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.ui.res.painterResource
import com.dustvalve.next.android.R
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.dustvalve.next.android.domain.model.SearchResultType
import com.dustvalve.next.android.ui.screens.player.PlayerViewModel
import com.dustvalve.next.android.ui.theme.AppShapes
import com.dustvalve.next.android.ui.theme.segmentedItemShape
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun YouTubeScreen(
    playerViewModel: PlayerViewModel,
    viewModel: YouTubeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

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
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    val inputField = @Composable {
        SearchBarDefaults.InputField(
            searchBarState = searchBarState,
            textFieldState = textFieldState,
            onSearch = { viewModel.onSearch() },
            placeholder = { Text("Search YouTube...") },
            leadingIcon = {
                Icon(
                    painter = painterResource(R.drawable.ic_search),
                    contentDescription = "Search",
                )
            },
            trailingIcon = {
                if (textFieldState.text.isNotEmpty()) {
                    IconButton(onClick = {
                        textFieldState.setTextAndPlaceCursorAtEnd("")
                    }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_clear),
                            contentDescription = "Clear",
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

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 80.dp),
                ) {
                    // Recommendations carousel
                    if (state.recommendations.isNotEmpty()) {
                        item(key = "reco_header") {
                            Text(
                                text = "Recommendations",
                                style = MaterialTheme.typography.headlineMediumEmphasized,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                        item(key = "reco_carousel") {
                            val carouselState = rememberCarouselState { state.recommendations.size }
                            HorizontalMultiBrowseCarousel(
                                state = carouselState,
                                preferredItemWidth = 200.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                itemSpacing = 8.dp,
                            ) { index ->
                                val item = state.recommendations[index]
                                Box(
                                    modifier = Modifier
                                        .aspectRatio(16f / 9f)
                                        .maskClip(AppShapes.SearchResultTrack)
                                        .clickable {
                                            scope.launch {
                                                try {
                                                    val track = viewModel.getTrackInfo(item.url)
                                                    playerViewModel.playTrack(track)
                                                } catch (_: Exception) {
                                                    snackbarHostState.showSnackbar("Failed to play")
                                                }
                                            }
                                        },
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
                                            .graphicsLayer {
                                                // Gradient scrim
                                                alpha = 0.7f
                                            },
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

                    // Categories section
                    item(key = "categories_header") {
                        Text(
                            text = "Browse",
                            style = MaterialTheme.typography.headlineMediumEmphasized,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }

                    item(key = "categories") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(
                                items = viewModel.categories,
                                key = { it.id },
                            ) { category ->
                                Card(
                                    onClick = { /* TODO: Browse category */ },
                                    modifier = Modifier
                                        .width(160.dp)
                                        .height(100.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    ),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(16.dp),
                                        contentAlignment = Alignment.BottomStart,
                                    ) {
                                        Text(
                                            text = category.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        )
                                    }
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
                        label = { Text("All") },
                    )
                    FilterChip(
                        selected = state.selectedFilter == "songs",
                        onClick = { viewModel.onFilterSelected("songs") },
                        label = { Text("Songs") },
                    )
                    FilterChip(
                        selected = state.selectedFilter == "playlists",
                        onClick = { viewModel.onFilterSelected("playlists") },
                        label = { Text("Playlists") },
                    )
                    FilterChip(
                        selected = state.selectedFilter == "albums",
                        onClick = { viewModel.onFilterSelected("albums") },
                        label = { Text("Albums") },
                    )
                    FilterChip(
                        selected = state.selectedFilter == "artists",
                        onClick = { viewModel.onFilterSelected("artists") },
                        label = { Text("Artists") },
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    when {
                        state.query.isBlank() -> {
                            // Empty search state
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
                                    text = state.error ?: "Search failed",
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
                                    text = "No results found",
                                    style = MaterialTheme.typography.titleMedium,
                                    textAlign = TextAlign.Center,
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Try a different search term",
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
                                                        } catch (_: Exception) {
                                                            snackbarHostState.showSnackbar("Failed to play")
                                                        }
                                                    }
                                                }
                                                SearchResultType.YOUTUBE_PLAYLIST -> {
                                                    scope.launch {
                                                        viewModel.importPlaylist(result.url, result.name)
                                                        snackbarHostState.showSnackbar("Playlist imported: ${result.name}")
                                                    }
                                                }
                                                else -> { /* no-op for other types */ }
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
                                                if (result.imageUrl != null) {
                                                    AsyncImage(
                                                        model = result.imageUrl,
                                                        contentDescription = result.name,
                                                        modifier = Modifier
                                                            .size(48.dp)
                                                            .clip(AppShapes.SearchResultTrack),
                                                        contentScale = ContentScale.Crop,
                                                    )
                                                } else {
                                                    val iconRes = when (result.type) {
                                                        SearchResultType.YOUTUBE_PLAYLIST -> R.drawable.ic_queue_music
                                                        SearchResultType.YOUTUBE_ARTIST -> R.drawable.ic_person
                                                        else -> R.drawable.ic_play_circle
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
                                                if (result.type == SearchResultType.YOUTUBE_PLAYLIST) {
                                                    IconButton(
                                                        onClick = {
                                                            scope.launch {
                                                                viewModel.importPlaylist(result.url, result.name)
                                                                snackbarHostState.showSnackbar("Playlist imported: ${result.name}")
                                                            }
                                                        },
                                                    ) {
                                                        Icon(
                                                            painter = painterResource(R.drawable.ic_playlist_add),
                                                            contentDescription = "Import playlist",
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
