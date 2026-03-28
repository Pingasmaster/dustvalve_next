package com.dustvalve.next.android.ui.screens.local

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.ui.res.painterResource
import com.dustvalve.next.android.R
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.dustvalve.next.android.domain.model.Track
import com.dustvalve.next.android.ui.components.FastScrollbar
import com.dustvalve.next.android.ui.components.PlaylistEditSheet
import com.dustvalve.next.android.ui.components.PlaylistListItem
import com.dustvalve.next.android.ui.components.TrackArtPlaceholder
import com.dustvalve.next.android.ui.components.RecentSearchesList
import com.dustvalve.next.android.ui.screens.player.PlayerViewModel
import com.dustvalve.next.android.ui.theme.AppShapes
import com.dustvalve.next.android.ui.theme.segmentedItemShape

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)
@Composable
fun LocalScreen(
    playerViewModel: PlayerViewModel,
    navViewModel: com.dustvalve.next.android.ui.navigation.NavigationViewModel? = null,
    onExpandPlayer: () -> Unit = {},
    viewModel: LocalViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val playerState by playerViewModel.uiState.collectAsStateWithLifecycle()
    val allTracks by viewModel.allLocalTracks.collectAsStateWithLifecycle()
    val filteredTracks by viewModel.filteredTracks.collectAsStateWithLifecycle()
    val filterState by viewModel.filterState.collectAsStateWithLifecycle()
    val availableArtists by viewModel.availableArtists.collectAsStateWithLifecycle()
    val availableAlbums by viewModel.availableAlbums.collectAsStateWithLifecycle()
    val availableFolders by viewModel.availableFolders.collectAsStateWithLifecycle()
    val recentSearches by viewModel.recentSearches.collectAsStateWithLifecycle()
    val searchHistoryEnabled by viewModel.searchHistoryEnabled.collectAsStateWithLifecycle()
    val localMusicEnabled by viewModel.localMusicEnabled.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted: Boolean ->
        if (granted) {
            viewModel.onAudioPermissionGranted()
        }
    }

    var contextMenuTrack by remember { mutableStateOf<Track?>(null) }
    var showLocalPlaylistSheet by remember { mutableStateOf(false) }
    var showLocalCreatePlaylistSheet by remember { mutableStateOf(false) }
    var showDeleteTrackDialog by remember { mutableStateOf(false) }
    var showSortSheet by remember { mutableStateOf(false) }
    var showArtistSheet by remember { mutableStateOf(false) }
    var showAlbumSheet by remember { mutableStateOf(false) }
    var showFolderSheet by remember { mutableStateOf(false) }

    val searchBarState = rememberSearchBarState()
    val textFieldState = rememberTextFieldState()

    LaunchedEffect(textFieldState) {
        snapshotFlow { textFieldState.text.toString() }
            .collect { viewModel.onQueryChange(it) }
    }

    // Observe pending artist filter from navigation (e.g. clicking artist in full player)
    if (navViewModel != null) {
        val pendingArtist by navViewModel.pendingLocalArtistFilter.collectAsStateWithLifecycle()
        LaunchedEffect(pendingArtist) {
            val artist = pendingArtist ?: return@LaunchedEffect
            viewModel.setArtistFilter(artist)
            navViewModel.consumeLocalArtistFilter()
        }
    }

    val inputField = @Composable {
        SearchBarDefaults.InputField(
            searchBarState = searchBarState,
            textFieldState = textFieldState,
            onSearch = { viewModel.onSearch() },
            placeholder = { Text("Search local music...") },
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
        contentWindowInsets = WindowInsets(0),
        floatingActionButton = {
            if (filteredTracks.isNotEmpty()) {
                FloatingActionButton(
                    onClick = {
                        val shuffled = filteredTracks.shuffled()
                        playerViewModel.playTrackInList(shuffled, 0)
                    },
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_shuffle),
                        contentDescription = "Mix",
                    )
                }
            }
        },
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

                if (allTracks.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (!localMusicEnabled) {
                            // Not enabled — show CTA to enable
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(96.dp)
                                        .clip(AppShapes.EmptyStateIcon)
                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_phone_android),
                                        contentDescription = null,
                                        modifier = Modifier.size(48.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    )
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Scan your music",
                                    style = MaterialTheme.typography.titleMedium,
                                    textAlign = TextAlign.Center,
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Find and play music stored on your device",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                                Spacer(modifier = Modifier.height(24.dp))
                                Button(
                                    onClick = {
                                        viewModel.enableLocalMusic()
                                        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            Manifest.permission.READ_MEDIA_AUDIO
                                        } else {
                                            Manifest.permission.READ_EXTERNAL_STORAGE
                                        }
                                        audioPermissionLauncher.launch(permission)
                                    },
                                    shapes = ButtonDefaults.shapes(),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp),
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_phone_android),
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Enable local music")
                                }
                            }
                        } else if (isScanning) {
                            // Scanning in progress
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                ContainedLoadingIndicator()
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Scanning your music...",
                                    style = MaterialTheme.typography.titleMedium,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        } else {
                            // Enabled but no tracks found
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(96.dp)
                                        .clip(AppShapes.EmptyStateIcon)
                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_phone_android),
                                        contentDescription = null,
                                        modifier = Modifier.size(48.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    )
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "No local music",
                                    style = MaterialTheme.typography.titleMedium,
                                    textAlign = TextAlign.Center,
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Choose a folder in Settings to scan your music files",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                } else {
                    // Track count
                    Text(
                        text = if (filterState.hasActiveFilters) {
                            "${filteredTracks.size} of ${allTracks.size} songs"
                        } else {
                            "${allTracks.size} songs"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )

                    // Filter chips + Mix button
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Sort chip
                        FilterChip(
                            selected = filterState.sortOption != LocalSortOption.TITLE_AZ,
                            onClick = { showSortSheet = true },
                            label = { Text(filterState.sortOption.label) },
                            trailingIcon = {
                                Icon(
                                    painter = painterResource(R.drawable.ic_keyboard_arrow_down),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                        )

                        // Artist chip
                        FilterChip(
                            selected = filterState.selectedArtists.isNotEmpty(),
                            onClick = { showArtistSheet = true },
                            label = {
                                Text(
                                    when (filterState.selectedArtists.size) {
                                        0 -> "Artist"
                                        1 -> filterState.selectedArtists.first()
                                        else -> "Artist (${filterState.selectedArtists.size})"
                                    },
                                )
                            },
                            trailingIcon = {
                                Icon(
                                    painter = painterResource(R.drawable.ic_keyboard_arrow_down),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                        )

                        // Album chip
                        FilterChip(
                            selected = filterState.selectedAlbums.isNotEmpty(),
                            onClick = { showAlbumSheet = true },
                            label = {
                                Text(
                                    when (filterState.selectedAlbums.size) {
                                        0 -> "Album"
                                        1 -> filterState.selectedAlbums.first()
                                        else -> "Album (${filterState.selectedAlbums.size})"
                                    },
                                )
                            },
                            trailingIcon = {
                                Icon(
                                    painter = painterResource(R.drawable.ic_keyboard_arrow_down),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                        )

                        // Favorites chip
                        FilterChip(
                            selected = filterState.favoritesOnly,
                            onClick = { viewModel.toggleFavoritesFilter() },
                            label = { Text("Favorites") },
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(
                                        if (filterState.favoritesOnly) R.drawable.ic_check
                                        else R.drawable.ic_favorite_border
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                        )

                        // Folder chip
                        if (availableFolders.size > 1) {
                            FilterChip(
                                selected = filterState.selectedFolders.isNotEmpty(),
                                onClick = { showFolderSheet = true },
                                label = {
                                    Text(
                                        when (filterState.selectedFolders.size) {
                                            0 -> "Folder"
                                            1 -> filterState.selectedFolders.first()
                                                .toUri().lastPathSegment?.substringAfterLast(':') ?: "Folder"
                                            else -> "Folder (${filterState.selectedFolders.size})"
                                        },
                                    )
                                },
                                trailingIcon = {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_keyboard_arrow_down),
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                },
                            )
                        }

                    }

                    // Track list
                    val localListState = rememberLazyListState()
                    Box(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            state = localListState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 80.dp),
                        ) {
                            itemsIndexed(
                                items = filteredTracks,
                                key = { _, track -> track.id },
                            ) { index, track ->
                                LocalTrackItem(
                                    track = track,
                                    index = index,
                                    total = filteredTracks.size,
                                    onClick = {
                                        playerViewModel.playTrackInList(filteredTracks, index)
                                    },
                                    onLongClick = { contextMenuTrack = track },
                                    modifier = Modifier.animateItem(
                                        fadeInSpec = null,
                                        fadeOutSpec = null,
                                        placementSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                                    ),
                                )
                            }
                        }
                        if (filteredTracks.size > 15) {
                            FastScrollbar(
                                listState = localListState,
                                modifier = Modifier.align(Alignment.CenterEnd),
                            )
                        }
                    }
                }
            }

            // Expanded search overlay
            ExpandedFullScreenSearchBar(
                state = searchBarState,
                inputField = inputField,
            ) {
                // Type filter chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = state.searchFilter == null,
                        onClick = { viewModel.onSearchFilterSelected(null) },
                        label = { Text("All") },
                    )
                    FilterChip(
                        selected = state.searchFilter == LocalSearchFilter.ARTISTS,
                        onClick = { viewModel.onSearchFilterSelected(LocalSearchFilter.ARTISTS) },
                        label = { Text("Artists") },
                    )
                    FilterChip(
                        selected = state.searchFilter == LocalSearchFilter.ALBUMS,
                        onClick = { viewModel.onSearchFilterSelected(LocalSearchFilter.ALBUMS) },
                        label = { Text("Albums") },
                    )
                    FilterChip(
                        selected = state.searchFilter == LocalSearchFilter.TRACKS,
                        onClick = { viewModel.onSearchFilterSelected(LocalSearchFilter.TRACKS) },
                        label = { Text("Tracks") },
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
                        state.isSearching -> {
                            ContainedLoadingIndicator(
                                modifier = Modifier.align(Alignment.Center),
                            )
                        }
                        state.searchResults.isEmpty() && state.query.isNotBlank() && !state.isSearching -> {
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
                                contentPadding = PaddingValues(bottom = 80.dp),
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                itemsIndexed(
                                    items = state.searchResults,
                                    key = { _, track -> "search_${track.id}" },
                                ) { index, track ->
                                    LocalTrackItem(
                                        track = track,
                                        index = index,
                                        total = state.searchResults.size,
                                        onClick = {
                                            playerViewModel.playTrackInList(state.searchResults, index)
                                            onExpandPlayer()
                                        },
                                        onLongClick = { contextMenuTrack = track },
                                        modifier = Modifier.animateItem(
                                            fadeInSpec = null,
                                            fadeOutSpec = null,
                                            placementSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Local song context menu bottom sheet
    contextMenuTrack?.let { menuTrack ->
        ModalBottomSheet(
            onDismissRequest = { contextMenuTrack = null },
        ) {
            Text(
                text = menuTrack.title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            ListItem(
                headlineContent = { Text("Play next") },
                leadingContent = {
                    Icon(
                        painter = painterResource(R.drawable.ic_skip_next),
                        contentDescription = null,
                    )
                },
                modifier = Modifier.clickable {
                    playerViewModel.playNext(menuTrack)
                    contextMenuTrack = null
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )

            ListItem(
                headlineContent = {
                    Text(if (menuTrack.isFavorite) "Remove from favorites" else "Add to favorites")
                },
                leadingContent = {
                    Icon(
                        painter = painterResource(
                            if (menuTrack.isFavorite) R.drawable.ic_favorite
                            else R.drawable.ic_favorite_border,
                        ),
                        contentDescription = null,
                    )
                },
                modifier = Modifier.clickable {
                    playerViewModel.toggleFavoriteById(menuTrack.id)
                    contextMenuTrack = null
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )

            ListItem(
                headlineContent = { Text("Add to playlist") },
                leadingContent = {
                    Icon(
                        painter = painterResource(R.drawable.ic_playlist_add),
                        contentDescription = null,
                    )
                },
                modifier = Modifier.clickable {
                    showLocalPlaylistSheet = true
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )

            ListItem(
                headlineContent = {
                    Text(
                        text = "Delete",
                        color = MaterialTheme.colorScheme.error,
                    )
                },
                leadingContent = {
                    Icon(
                        painter = painterResource(R.drawable.ic_delete),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                },
                modifier = Modifier.clickable {
                    showDeleteTrackDialog = true
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )

            Spacer(modifier = Modifier.height(28.dp))
        }
    }

    // Delete confirmation dialog
    if (showDeleteTrackDialog) {
        contextMenuTrack?.let { menuTrack ->
            AlertDialog(
                onDismissRequest = { showDeleteTrackDialog = false },
                title = { Text("Delete song") },
                text = { Text("Delete '${menuTrack.title}'? This cannot be undone.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.deleteLocalTrack(menuTrack)
                            showDeleteTrackDialog = false
                            contextMenuTrack = null
                        },
                    ) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteTrackDialog = false }) {
                        Text("Cancel")
                    }
                },
            )
        }
    }

    // Local song — add to playlist sheet
    if (showLocalPlaylistSheet) {
        ModalBottomSheet(
            onDismissRequest = { showLocalPlaylistSheet = false },
        ) {
            val userPlaylists = playerState.playlists.filter { !it.isSystem }
            Box {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Add to playlist",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                    if (userPlaylists.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(horizontal = 32.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(96.dp)
                                        .clip(AppShapes.EmptyStateIcon)
                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_queue_music),
                                        contentDescription = null,
                                        modifier = Modifier.size(48.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    )
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "No playlists yet",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Create one to get started",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            userPlaylists.forEachIndexed { index, playlist ->
                                Surface(
                                    shape = segmentedItemShape(index, userPlaylists.size),
                                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                                ) {
                                    PlaylistListItem(
                                        playlist = playlist,
                                        onClick = {
                                            showLocalPlaylistSheet = false
                                            contextMenuTrack?.let { ctxTrack ->
                                                playerViewModel.addTrackToPlaylist(playlist.id, ctxTrack.id)
                                            }
                                            contextMenuTrack = null
                                        },
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(72.dp))
                }
                FloatingActionButton(
                    onClick = { showLocalCreatePlaylistSheet = true },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 16.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_add),
                        contentDescription = "Create playlist",
                    )
                }
            }
        }
    }

    // Local song — create playlist sheet
    if (showLocalCreatePlaylistSheet) {
        PlaylistEditSheet(
            onDismiss = { showLocalCreatePlaylistSheet = false },
            onConfirm = { name, shapeKey, iconUrl ->
                showLocalCreatePlaylistSheet = false
                showLocalPlaylistSheet = false
                contextMenuTrack?.let { ctxTrack ->
                    playerViewModel.createPlaylistAndAddArbitraryTrack(name, shapeKey, iconUrl, ctxTrack.id)
                }
                contextMenuTrack = null
            },
            isCreate = true,
        )
    }

    // Sort bottom sheet
    if (showSortSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSortSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Text(
                text = "Sort by",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(1.dp),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                LocalSortOption.entries.forEachIndexed { index, option ->
                    val isSelected = filterState.sortOption == option
                    val sortItemColor by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerLow,
                        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                        label = "sortItemColor",
                    )
                    Surface(
                        shape = segmentedItemShape(index, LocalSortOption.entries.size),
                        color = sortItemColor,
                        modifier = Modifier.selectable(
                            selected = isSelected,
                            onClick = {
                                viewModel.setSortOption(option)
                                showSortSheet = false
                            },
                            role = Role.RadioButton,
                        ),
                    ) {
                        ListItem(
                            headlineContent = { Text(option.label) },
                            leadingContent = { RadioButton(selected = isSelected, onClick = null) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                    }
                }
            }
            androidx.compose.material3.HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            ListItem(
                headlineContent = { Text("Reverse order") },
                trailingContent = {
                    androidx.compose.material3.Switch(
                        checked = filterState.reverseOrder,
                        onCheckedChange = { viewModel.toggleReverseOrder() },
                    )
                },
                modifier = Modifier
                    .clickable { viewModel.toggleReverseOrder() }
                    .padding(horizontal = 16.dp),
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
            Spacer(modifier = Modifier.height(28.dp))
        }
    }

    // Artist filter bottom sheet
    if (showArtistSheet) {
        ModalBottomSheet(
            onDismissRequest = { showArtistSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Text(
                text = "Filter by artist",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                itemsIndexed(availableArtists) { index, artist ->
                    val isChecked = artist in filterState.selectedArtists
                    val artistItemColor by animateColorAsState(
                        targetValue = if (isChecked) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerLow,
                        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                        label = "artistItemColor",
                    )
                    Surface(
                        shape = segmentedItemShape(index, availableArtists.size),
                        color = artistItemColor,
                    ) {
                        ListItem(
                            headlineContent = { Text(artist, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingContent = { Checkbox(checked = isChecked, onCheckedChange = null) },
                            modifier = Modifier.clickable { viewModel.toggleArtist(artist) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(28.dp))
        }
    }

    // Album filter bottom sheet
    if (showAlbumSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAlbumSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Text(
                text = "Filter by album",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                itemsIndexed(availableAlbums) { index, album ->
                    val isChecked = album in filterState.selectedAlbums
                    val albumItemColor by animateColorAsState(
                        targetValue = if (isChecked) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerLow,
                        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                        label = "albumItemColor",
                    )
                    Surface(
                        shape = segmentedItemShape(index, availableAlbums.size),
                        color = albumItemColor,
                    ) {
                        ListItem(
                            headlineContent = { Text(album, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingContent = { Checkbox(checked = isChecked, onCheckedChange = null) },
                            modifier = Modifier.clickable { viewModel.toggleAlbum(album) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(28.dp))
        }
    }

    // Folder filter bottom sheet
    if (showFolderSheet) {
        ModalBottomSheet(
            onDismissRequest = { showFolderSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Text(
                text = "Filter by folder",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                itemsIndexed(availableFolders) { index, folder ->
                    val isChecked = folder in filterState.selectedFolders
                    val displayName = try {
                        folder.toUri().lastPathSegment?.substringAfterLast(':') ?: folder
                    } catch (_: Exception) {
                        folder
                    }
                    val folderItemColor by animateColorAsState(
                        targetValue = if (isChecked) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerLow,
                        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                        label = "folderItemColor",
                    )
                    Surface(
                        shape = segmentedItemShape(index, availableFolders.size),
                        color = folderItemColor,
                    ) {
                        ListItem(
                            headlineContent = { Text(displayName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingContent = { Checkbox(checked = isChecked, onCheckedChange = null) },
                            modifier = Modifier.clickable { viewModel.toggleFolder(folder) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)
@Composable
private fun LocalTrackItem(
    track: Track,
    index: Int,
    total: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "pressScale",
    )
    val hapticFeedback = LocalHapticFeedback.current

    Surface(
        shape = segmentedItemShape(index, total),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier
            .padding(
                start = 16.dp,
                end = 16.dp,
                top = if (index == 0) 8.dp else 1.dp,
                bottom = if (index == total - 1) 0.dp else 1.dp,
            )
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick,
                onLongClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                },
            ),
    ) {
        ListItem(
            headlineContent = {
                Text(
                    text = track.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            supportingContent = {
                val supporting = buildString {
                    append(track.artist)
                    if (track.albumTitle.isNotBlank()) {
                        append(" \u00B7 ")
                        append(track.albumTitle)
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
                if (track.artUrl.isNotBlank()) {
                    AsyncImage(
                        model = track.artUrl,
                        contentDescription = track.title,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(AppShapes.SearchResultTrack),
                    )
                } else {
                    TrackArtPlaceholder(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(AppShapes.SearchResultTrack),
                    )
                }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
}
