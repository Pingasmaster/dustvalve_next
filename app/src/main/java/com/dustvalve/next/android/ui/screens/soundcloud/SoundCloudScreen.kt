package com.dustvalve.next.android.ui.screens.soundcloud

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.dustvalve.next.android.R
import com.dustvalve.next.android.domain.model.SearchResult
import com.dustvalve.next.android.domain.model.SearchResultType
import com.dustvalve.next.android.domain.model.SoundCloudShelfItem
import com.dustvalve.next.android.domain.model.SoundCloudShelfKind
import com.dustvalve.next.android.domain.model.Track
import com.dustvalve.next.android.ui.adaptive.AdaptiveLayoutInfo
import com.dustvalve.next.android.ui.components.PastedLinkChip
import com.dustvalve.next.android.ui.components.RecentSearchesList
import com.dustvalve.next.android.ui.components.sheet.AddToPlaylistSheet
import com.dustvalve.next.android.ui.components.sheet.RemoteResultActionSheet
import com.dustvalve.next.android.ui.components.sheet.RemoteResultActions
import com.dustvalve.next.android.ui.screens.player.PlayerViewModel
import com.dustvalve.next.android.ui.screens.player.playTrack
import com.dustvalve.next.android.ui.screens.player.playAlbum
import com.dustvalve.next.android.ui.screens.player.playNext
import com.dustvalve.next.android.ui.screens.player.addToQueue
import com.dustvalve.next.android.ui.screens.player.addAllToQueue
import com.dustvalve.next.android.ui.screens.player.addTrackToPlaylist
import com.dustvalve.next.android.ui.screens.player.createPlaylistAndAddArbitraryTrack
import com.dustvalve.next.android.ui.theme.AppShapes
import com.dustvalve.next.android.util.DeepLinkRouter
import com.dustvalve.next.android.util.DetectedLink
import com.dustvalve.next.android.util.openInBrowser
import com.dustvalve.next.android.util.shareUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

private class SoundCloudNavActions(
    val onCollectionClick: (url: String, name: String, coverUrl: String?) -> Unit,
    val onArtistClick: (url: String, name: String, imageUrl: String?) -> Unit,
    val onOpenLink: (String) -> Unit,
    val onExpandPlayer: () -> Unit,
)

private class SoundCloudSearchHandlers(
    val onOpenPastedLink: () -> Unit,
    val onRecentSearchClick: (String) -> Unit,
    val onRemoveRecent: (String) -> Unit,
    val onClearRecent: () -> Unit,
    val onResultClick: (SearchResult) -> Unit,
    val onResultLongClick: (SearchResult) -> Unit,
)

private class SoundCloudScaffoldInputs(
    val state: SoundCloudUiState,
    val snackbarHostState: SnackbarHostState,
    val searchBarState: SearchBarState,
    val textFieldState: TextFieldState,
    val detectedLink: DetectedLink?,
    val recentSearches: List<String>,
    val searchHistoryEnabled: Boolean,
    val searchListState: LazyListState,
)

private class SoundCloudHomeHandlers(
    val onOpenLink: (String) -> Unit,
    val onSearch: () -> Unit,
    val onGenreSelect: (String) -> Unit,
    val onRetry: () -> Unit,
    val onPlayTrack: (Track) -> Unit,
    val onShelfItemClick: (SoundCloudShelfItem) -> Unit,
)

private class SoundCloudSheetActions(
    val getTrack: suspend (String) -> Track,
    val resolveCollectionTracks: suspend (String) -> List<Track>,
    val playNext: (Track) -> Unit,
    val addToQueue: (Track) -> Unit,
    val playAlbum: (List<Track>, Int) -> Unit,
    val addAllToQueue: (List<Track>) -> Unit,
    val addTrackToPlaylist: (String, String) -> Unit,
    val createPlaylistAndAdd: (String, String?, String?, String) -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)
@Composable
fun SoundCloudScreen(
    adaptiveInfo: AdaptiveLayoutInfo,
    onCollectionClick: (url: String, name: String, coverUrl: String?) -> Unit,
    onArtistClick: (url: String, name: String, imageUrl: String?) -> Unit,
    onOpenLink: (String) -> Unit,
    modifier: Modifier = Modifier,
    onExpandPlayer: () -> Unit = {},
    playerViewModel: PlayerViewModel = hiltViewModel(),
    viewModel: SoundCloudViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val playerState by playerViewModel.uiState.collectAsStateWithLifecycle()
    val recentSearches by viewModel.recentSearches.collectAsStateWithLifecycle()
    val searchHistoryEnabled by viewModel.searchHistoryEnabled.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val hapticFeedback = LocalHapticFeedback.current
    val nav = remember(onCollectionClick, onArtistClick, onOpenLink, onExpandPlayer) {
        SoundCloudNavActions(onCollectionClick, onArtistClick, onOpenLink, onExpandPlayer)
    }
    val sheetActions = SoundCloudSheetActions(
        getTrack = viewModel::getTrack,
        resolveCollectionTracks = viewModel::resolveCollectionTracks,
        playNext = playerViewModel::playNext,
        addToQueue = playerViewModel::addToQueue,
        playAlbum = playerViewModel::playAlbum,
        addAllToQueue = playerViewModel::addAllToQueue,
        addTrackToPlaylist = playerViewModel::addTrackToPlaylist,
        createPlaylistAndAdd = playerViewModel::createPlaylistAndAddArbitraryTrack,
    )

    var contextResult by remember { mutableStateOf<SearchResult?>(null) }
    var addToPlaylistTrackId by remember { mutableStateOf<String?>(null) }

    val failedToPlayMsg = stringResource(R.string.common_failed_to_play)
    val searchBarState = rememberSearchBarState()
    val textFieldState = rememberTextFieldState()
    val detectedLink = remember(textFieldState.text.toString()) {
        DeepLinkRouter.detect(textFieldState.text.toString())
    }
    val searchListState = rememberLazyListState()

    LaunchedEffect(textFieldState) {
        snapshotFlow { textFieldState.text.toString() }.collect { viewModel.onQueryChange(it) }
    }
    SoundCloudSearchErrorEffect(state, snackbarHostState, viewModel::clearSearchError)

    val playTrackUrl: (String) -> Unit = { url ->
        scope.launch {
            runCatchingPlayback(snackbarHostState, failedToPlayMsg) {
                playerViewModel.playTrack(viewModel.getTrack(url))
                nav.onExpandPlayer()
            }
        }
    }

    val searchHandlers = SoundCloudSearchHandlers(
        onOpenPastedLink = { nav.onOpenLink(textFieldState.text.toString()) },
        onRecentSearchClick = { query ->
            textFieldState.setTextAndPlaceCursorAtEnd(query)
            viewModel.onSearch()
        },
        onRemoveRecent = viewModel::removeRecentSearch,
        onClearRecent = viewModel::clearRecentSearches,
        onResultClick = { result ->
            scope.playSearchResult(
                result = result,
                searchBarState = searchBarState,
                snackbarHostState = snackbarHostState,
                failedToPlayMsg = failedToPlayMsg,
                getTrack = viewModel::getTrack,
                playTrack = playerViewModel::playTrack,
                nav = nav,
            )
        },
        onResultLongClick = {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
            contextResult = it
        },
    )

    SoundCloudScreenScaffold(
        carouselItemWidth = adaptiveInfo.carouselItemWidth,
        inputs = SoundCloudScaffoldInputs(
            state = state,
            snackbarHostState = snackbarHostState,
            searchBarState = searchBarState,
            textFieldState = textFieldState,
            detectedLink = detectedLink,
            recentSearches = recentSearches,
            searchHistoryEnabled = searchHistoryEnabled,
            searchListState = searchListState,
        ),
        home = SoundCloudHomeHandlers(
            onOpenLink = nav.onOpenLink,
            onSearch = viewModel::onSearch,
            onGenreSelect = viewModel::selectGenre,
            onRetry = viewModel::retryHome,
            onPlayTrack = { track ->
                playerViewModel.playTrack(track)
                nav.onExpandPlayer()
            },
            onShelfItemClick = { item ->
                when (item.kind) {
                    SoundCloudShelfKind.TRACK -> playTrackUrl(item.url)

                    SoundCloudShelfKind.USER -> nav.onArtistClick(item.url, item.title, item.artUrl)

                    SoundCloudShelfKind.PLAYLIST,
                    SoundCloudShelfKind.ALBUM,
                    -> nav.onCollectionClick(item.url, item.title, item.artUrl)
                }
            },
        ),
        searchHandlers = searchHandlers,
        modifier = modifier,
    )

    SoundCloudResultSheets(
        contextResult = contextResult,
        addToPlaylistTrackId = addToPlaylistTrackId,
        playlists = playerState.playlists,
        snackbarHostState = snackbarHostState,
        actions = sheetActions,
        onExpandPlayer = nav.onExpandPlayer,
        onDismissContext = { contextResult = null },
        onAddToPlaylistTrackId = { addToPlaylistTrackId = it },
    )
}

@Composable
private fun SoundCloudSearchErrorEffect(state: SoundCloudUiState, snackbarHostState: SnackbarHostState, clearSearchError: () -> Unit) {
    val latestClear by rememberUpdatedState(clearSearchError)
    val searchErrorText = state.searchError?.asString()
    LaunchedEffect(searchErrorText) {
        val error = searchErrorText ?: return@LaunchedEffect
        if (state.results.isNotEmpty()) {
            try {
                snackbarHostState.showSnackbar(error)
            } finally {
                latestClear()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)
@Composable
private fun SoundCloudScreenScaffold(
    carouselItemWidth: Dp,
    inputs: SoundCloudScaffoldInputs,
    home: SoundCloudHomeHandlers,
    searchHandlers: SoundCloudSearchHandlers,
    modifier: Modifier = Modifier,
) {
    val inputField = soundCloudSearchInputField(
        searchBarState = inputs.searchBarState,
        textFieldState = inputs.textFieldState,
        detectedLink = inputs.detectedLink,
        onOpenLink = home.onOpenLink,
        onSearch = home.onSearch,
    )

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(inputs.snackbarHostState) },
        contentWindowInsets = WindowInsets(0),
    ) { scaffoldPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                SearchBar(
                    state = inputs.searchBarState,
                    inputField = inputField,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
                SoundCloudHomeContent(
                    carouselItemWidth = carouselItemWidth,
                    state = inputs.state,
                    onGenreSelect = home.onGenreSelect,
                    onRetry = home.onRetry,
                    onPlayTrack = home.onPlayTrack,
                    onShelfItemClick = home.onShelfItemClick,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            ExpandedFullScreenSearchBar(
                state = inputs.searchBarState,
                inputField = inputField,
            ) {
                SoundCloudSearchOverlay(
                    state = inputs.state,
                    recentSearches = inputs.recentSearches,
                    searchHistoryEnabled = inputs.searchHistoryEnabled,
                    searchListState = inputs.searchListState,
                    snackbarHostState = inputs.snackbarHostState,
                    detectedLink = inputs.detectedLink,
                    handlers = searchHandlers,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun soundCloudSearchInputField(
    searchBarState: SearchBarState,
    textFieldState: TextFieldState,
    detectedLink: DetectedLink?,
    onOpenLink: (String) -> Unit,
    onSearch: () -> Unit,
): @Composable () -> Unit = {
    SearchBarDefaults.InputField(
        searchBarState = searchBarState,
        textFieldState = textFieldState,
        onSearch = {
            val q = textFieldState.text.toString()
            if (detectedLink != null || hasExplicitWebScheme(q)) onOpenLink(q) else onSearch()
        },
        placeholder = {
            Text(
                text = stringResource(R.string.soundcloud_search_hint),
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
                    onClick = { textFieldState.setTextAndPlaceCursorAtEnd("") },
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ColumnScope.SoundCloudSearchOverlay(
    state: SoundCloudUiState,
    recentSearches: List<String>,
    searchHistoryEnabled: Boolean,
    searchListState: LazyListState,
    snackbarHostState: SnackbarHostState,
    detectedLink: DetectedLink?,
    handlers: SoundCloudSearchHandlers,
) {
    PastedLinkChip(detected = detectedLink, onClick = handlers.onOpenPastedLink)

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
                        onSearchClick = handlers.onRecentSearchClick,
                        onRemoveClick = handlers.onRemoveRecent,
                        onClearAllClick = handlers.onClearRecent,
                    )
                }
            }

            state.isSearching && state.results.isEmpty() -> {
                ContainedLoadingIndicator(modifier = Modifier.align(Alignment.Center))
            }

            state.searchError != null && state.results.isEmpty() -> {
                SoundCloudCenteredMessage(
                    text = state.searchError.asString(),
                )
            }

            state.results.isEmpty() && state.query.isNotBlank() && !state.isSearching -> {
                SoundCloudNoResults()
            }

            else -> {
                SoundCloudSearchResults(
                    results = state.results,
                    isSearching = state.isSearching,
                    listState = searchListState,
                    onResultClick = handlers.onResultClick,
                    onResultLongClick = handlers.onResultLongClick,
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SoundCloudSearchResults(
    results: List<SearchResult>,
    isSearching: Boolean,
    listState: LazyListState,
    onResultClick: (SearchResult) -> Unit,
    onResultLongClick: (SearchResult) -> Unit,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        itemsIndexed(
            items = results,
            key = { index, item -> "${item.url}-$index" },
        ) { _, result ->
            val interactionSource = remember { MutableInteractionSource() }
            ListItem(
                supportingContent = {
                    result.artist?.let {
                        Text(text = it, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                leadingContent = { SoundCloudResultThumbnail(result) },
                modifier = Modifier.combinedClickable(
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    onClick = { onResultClick(result) },
                    onLongClick = { onResultLongClick(result) },
                ),
            ) {
                Text(text = result.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        if (isSearching && results.isNotEmpty()) {
            item {
                LinearWavyProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun SoundCloudCenteredMessage(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp),
        )
    }
}

@Composable
private fun SoundCloudNoResults() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
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

@OptIn(ExperimentalMaterial3Api::class)
private fun CoroutineScope.playSearchResult(
    result: SearchResult,
    searchBarState: SearchBarState,
    snackbarHostState: SnackbarHostState,
    failedToPlayMsg: String,
    getTrack: suspend (String) -> Track,
    playTrack: (Track) -> Unit,
    nav: SoundCloudNavActions,
) {
    when (result.type) {
        SearchResultType.SOUNDCLOUD_TRACK -> launch {
            runCatchingPlayback(snackbarHostState, failedToPlayMsg) {
                val track = getTrack(result.url)
                searchBarState.animateToCollapsed()
                playTrack(track)
                nav.onExpandPlayer()
            }
        }

        SearchResultType.SOUNDCLOUD_PLAYLIST,
        SearchResultType.SOUNDCLOUD_ALBUM,
        -> {
            launch { searchBarState.animateToCollapsed() }
            nav.onCollectionClick(result.url, result.name, result.imageUrl)
        }

        SearchResultType.SOUNDCLOUD_ARTIST -> {
            launch { searchBarState.animateToCollapsed() }
            nav.onArtistClick(result.url, result.name, result.imageUrl)
        }

        else -> Unit
    }
}

@Composable
private fun SoundCloudResultSheets(
    contextResult: SearchResult?,
    addToPlaylistTrackId: String?,
    playlists: List<com.dustvalve.next.android.domain.model.Playlist>,
    snackbarHostState: SnackbarHostState,
    actions: SoundCloudSheetActions,
    onExpandPlayer: () -> Unit,
    onDismissContext: () -> Unit,
    onAddToPlaylistTrackId: (String?) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val loadingTrackMsg = stringResource(R.string.common_loading_track)
    val loadingPlaylistMsg = stringResource(R.string.common_loading_playlist)
    val failedLoadMsg = stringResource(R.string.snackbar_failed_load)

    contextResult?.let { result ->
        RemoteResultActionSheet(
            result = result,
            actions = RemoteResultActions(
                onDismiss = onDismissContext,
                onPlayNext = {
                onDismissContext()
                scope.launch { snackbarHostState.showSnackbar(loadingTrackMsg) }
                scope.launch {
                    runCatchingPlayback(snackbarHostState, failedLoadMsg) {
                        actions.playNext(actions.getTrack(result.url))
                    }
                }
            },
                onAddToQueue = {
                onDismissContext()
                scope.launch { snackbarHostState.showSnackbar(loadingTrackMsg) }
                scope.launch {
                    runCatchingPlayback(snackbarHostState, failedLoadMsg) {
                        actions.addToQueue(actions.getTrack(result.url))
                    }
                }
            },
                onAddToPlaylist = {
                val ctx = result
                onDismissContext()
                scope.launch { snackbarHostState.showSnackbar(loadingTrackMsg) }
                scope.launch {
                    runCatchingPlayback(snackbarHostState, failedLoadMsg) {
                        onAddToPlaylistTrackId(actions.getTrack(ctx.url).id)
                    }
                }
            },
                onPlayAll = {
                onDismissContext()
                scope.launch { snackbarHostState.showSnackbar(loadingPlaylistMsg) }
                scope.launch {
                    runCatchingPlayback(snackbarHostState, failedLoadMsg) {
                        val tracks = actions.resolveCollectionTracks(result.url)
                        if (tracks.isNotEmpty()) {
                            actions.playAlbum(tracks, 0)
                            onExpandPlayer()
                        }
                    }
                }
            },
                onEnqueueAll = {
                onDismissContext()
                scope.launch { snackbarHostState.showSnackbar(loadingPlaylistMsg) }
                scope.launch {
                    runCatchingPlayback(snackbarHostState, failedLoadMsg) {
                        actions.addAllToQueue(actions.resolveCollectionTracks(result.url))
                    }
                }
            },
                onShare = {
                onDismissContext()
                context.shareUrl(result.url, result.name)
            },
                onOpenInBrowser = {
                onDismissContext()
                context.openInBrowser(result.url)
            },
            ),
        )
    }

    addToPlaylistTrackId?.let { trackId ->
        AddToPlaylistSheet(
            playlists = playlists,
            onDismiss = { onAddToPlaylistTrackId(null) },
            onPlaylistSelected = { playlistId ->
                actions.addTrackToPlaylist(playlistId, trackId)
                onAddToPlaylistTrackId(null)
            },
            onCreatePlaylist = { name, shapeKey, iconUrl ->
                actions.createPlaylistAndAdd(name, shapeKey, iconUrl, trackId)
                onAddToPlaylistTrackId(null)
            },
        )
    }
}

@Composable
private fun SoundCloudResultThumbnail(result: SearchResult) {
    val shape = when (result.type) {
        SearchResultType.SOUNDCLOUD_ARTIST -> AppShapes.SearchResultArtist

        SearchResultType.SOUNDCLOUD_ALBUM,
        SearchResultType.SOUNDCLOUD_PLAYLIST,
        -> AppShapes.SearchResultAlbum

        else -> AppShapes.SearchResultTrack
    }
    if (result.imageUrl != null) {
        AsyncImage(
            model = result.imageUrl,
            contentDescription = result.name,
            modifier = Modifier
                .size(48.dp)
                .clip(shape),
            contentScale = ContentScale.Crop,
        )
    } else {
        Icon(
            painter = painterResource(
                when (result.type) {
                    SearchResultType.SOUNDCLOUD_ARTIST -> R.drawable.ic_person

                    SearchResultType.SOUNDCLOUD_ALBUM,
                    SearchResultType.SOUNDCLOUD_PLAYLIST,
                    -> R.drawable.ic_album

                    else -> R.drawable.ic_music_note
                },
            ),
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private suspend fun runCatchingPlayback(snackbarHostState: SnackbarHostState, failedMsg: String, block: suspend () -> Unit) {
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (_: IOException) {
        snackbarHostState.showSnackbar(failedMsg)
    } catch (_: IllegalStateException) {
        snackbarHostState.showSnackbar(failedMsg)
    } catch (_: IllegalArgumentException) {
        snackbarHostState.showSnackbar(failedMsg)
    }
}

private fun hasExplicitWebScheme(raw: String): Boolean {
    val trimmed = raw.trim()
    return trimmed.startsWith("http://", ignoreCase = true) ||
        trimmed.startsWith("https://", ignoreCase = true)
}
