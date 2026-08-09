package com.dustvalve.next.android.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.dustvalve.next.android.R
import com.dustvalve.next.android.ui.adaptive.AdaptiveLayoutInfo
import com.dustvalve.next.android.ui.components.EmptyState
import com.dustvalve.next.android.ui.screens.album.AlbumDetailScreen
import com.dustvalve.next.android.ui.screens.detail.ArtistDetailScreen
import com.dustvalve.next.android.ui.screens.detail.CollectionDetailScreen
import com.dustvalve.next.android.ui.screens.library.LibraryScreen
import com.dustvalve.next.android.ui.screens.playlist.PlaylistDetailScreen

/**
 * Expanded-width Library host: list pane stays visible while playlist/album/
 * artist/collection detail opens beside it. Compact/Medium keep the
 * single-pane [AppNavigation] push path instead.
 *
 * Selection state stays in [NavigationViewModel]'s Library tab stack so back
 * handling and ViewModel store retention stay unchanged.
 */
@Composable
internal fun LibraryListDetailHost(
    adaptiveInfo: AdaptiveLayoutInfo,
    backStack: List<NavDestination>,
    detailVmStores: DetailVmStoreRegistry,
    modifier: Modifier = Modifier,
    // Activity-scoped: same NavigationViewModel AppNavigation uses.
    navViewModel: NavigationViewModel = hiltViewModel(),
) {
    val detailDestination = backStack.lastOrNull()?.takeUnless { it is NavDestination.Library }

    Row(modifier = modifier.fillMaxSize()) {
        Surface(
            tonalElevation = 1.dp,
            modifier = Modifier
                .weight(0.4f)
                .fillMaxHeight(),
        ) {
            LibraryScreen(
                adaptiveInfo = adaptiveInfo,
                onAlbumClick = { url -> navViewModel.navigateTo(NavDestination.AlbumDetail(url)) },
                onArtistClick = { url ->
                    val sourceId = if (url.contains("youtube.com") || url.contains("youtu.be")) {
                        "youtube"
                    } else {
                        "bandcamp"
                    }
                    navViewModel.navigateTo(NavDestination.ArtistDetail(url = url, sourceId = sourceId))
                },
                onPlaylistClick = { playlistId ->
                    navViewModel.navigateTo(NavDestination.PlaylistDetail(playlistId))
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        VerticalDivider()

        Surface(
            modifier = Modifier
                .weight(0.6f)
                .fillMaxHeight(),
        ) {
            LibraryDetailPane(
                adaptiveInfo = adaptiveInfo,
                destination = detailDestination,
                detailVmStores = detailVmStores,
            )
        }
    }
}

@Composable
private fun LibraryDetailPane(
    adaptiveInfo: AdaptiveLayoutInfo,
    destination: NavDestination?,
    detailVmStores: DetailVmStoreRegistry,
    navViewModel: NavigationViewModel = hiltViewModel(),
) {
    when (destination) {
        null -> {
            EmptyState(
                icon = R.drawable.ic_library_music,
                title = stringResource(R.string.library_select_item_title),
                subtitle = stringResource(R.string.library_select_item_subtitle),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp, vertical = 48.dp),
            )
        }

        is NavDestination.AlbumDetail -> {
            val storeKey = checkNotNull(detailStoreKey(destination)) {
                "AlbumDetail missing detail store key"
            }
            AlbumDetailScreen(
                adaptiveInfo = adaptiveInfo,
                albumUrl = destination.url,
                onArtistClick = { url -> navViewModel.navigateTo(NavDestination.ArtistDetail(url)) },
                onBack = { navViewModel.navigateBack() },
                viewModel = hiltViewModel(viewModelStoreOwner = detailVmStores.owner(storeKey), key = storeKey),
            )
        }

        is NavDestination.ArtistDetail -> {
            val storeKey = checkNotNull(detailStoreKey(destination)) {
                "ArtistDetail missing detail store key"
            }
            ArtistDetailScreen(
                adaptiveInfo = adaptiveInfo,
                sourceId = destination.sourceId,
                artistUrl = destination.url,
                artistNameHint = destination.name,
                artistImageHint = destination.imageUrl,
                onAlbumClick = { url -> navViewModel.navigateTo(NavDestination.AlbumDetail(url)) },
                onBack = { navViewModel.navigateBack() },
                viewModel = hiltViewModel(viewModelStoreOwner = detailVmStores.owner(storeKey), key = storeKey),
            )
        }

        is NavDestination.PlaylistDetail -> {
            val storeKey = checkNotNull(detailStoreKey(destination)) {
                "PlaylistDetail missing detail store key"
            }
            PlaylistDetailScreen(
                adaptiveInfo = adaptiveInfo,
                playlistId = destination.playlistId,
                onBack = { navViewModel.navigateBack() },
                viewModel = hiltViewModel(viewModelStoreOwner = detailVmStores.owner(storeKey), key = storeKey),
            )
        }

        is NavDestination.CollectionDetail -> {
            val storeKey = checkNotNull(detailStoreKey(destination)) {
                "CollectionDetail missing detail store key"
            }
            CollectionDetailScreen(
                adaptiveInfo = adaptiveInfo,
                sourceId = destination.sourceId,
                collectionUrl = destination.url,
                collectionName = destination.name,
                collectionCoverHint = destination.coverUrl,
                onBack = { navViewModel.navigateBack() },
                viewModel = hiltViewModel(viewModelStoreOwner = detailVmStores.owner(storeKey), key = storeKey),
            )
        }

        else -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.library_select_item_title),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(32.dp),
                )
            }
        }
    }
}
