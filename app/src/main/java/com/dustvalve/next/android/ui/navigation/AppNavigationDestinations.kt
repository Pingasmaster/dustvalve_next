package com.dustvalve.next.android.ui.navigation

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.dustvalve.next.android.ui.adaptive.AdaptiveLayoutInfo
import com.dustvalve.next.android.ui.screens.album.AlbumDetailScreen
import com.dustvalve.next.android.ui.screens.bandcamp.BandcampScreen
import com.dustvalve.next.android.ui.screens.bandcamp.BandcampScreenNav
import com.dustvalve.next.android.ui.screens.detail.ArtistDetailArgs
import com.dustvalve.next.android.ui.screens.detail.ArtistDetailScreen
import com.dustvalve.next.android.ui.screens.detail.CollectionDetailArgs
import com.dustvalve.next.android.ui.screens.detail.CollectionDetailScreen
import com.dustvalve.next.android.ui.screens.library.LibraryScreen
import com.dustvalve.next.android.ui.screens.local.LocalScreen
import com.dustvalve.next.android.ui.screens.playlist.PlaylistDetailScreen
import com.dustvalve.next.android.ui.screens.settings.SettingsScreen
import com.dustvalve.next.android.ui.screens.soundcloud.SoundCloudScreen
import com.dustvalve.next.android.ui.screens.youtube.YouTubeScreen

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun AppNavigationDestination(
    destination: NavDestination,
    adaptiveInfo: AdaptiveLayoutInfo,
    detailVmStores: DetailVmStoreRegistry,
    navViewModel: NavigationViewModel = hiltViewModel(),
) {
    when (destination) {
        is NavDestination.LocalHome,
        is NavDestination.BandcampHome,
        is NavDestination.YouTubeHome,
        is NavDestination.SoundCloudHome,
        is NavDestination.Library,
        is NavDestination.Settings,
        -> AppNavigationHomeDestination(destination, adaptiveInfo)
        else -> AppNavigationDetailDestination(destination, adaptiveInfo, detailVmStores)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AppNavigationHomeDestination(
    destination: NavDestination,
    adaptiveInfo: AdaptiveLayoutInfo,
    navViewModel: NavigationViewModel = hiltViewModel(),
) {
    when (destination) {
        is NavDestination.LocalHome -> LocalScreen(
            onExpandPlayer = { navViewModel.expandPlayer() },
        )

        is NavDestination.BandcampHome -> BandcampScreen(
            adaptiveInfo = adaptiveInfo,
            nav = BandcampScreenNav(
                onAlbumClick = { url -> navViewModel.navigateTo(NavDestination.AlbumDetail(url)) },
                onArtistClick = { url -> navViewModel.navigateTo(NavDestination.ArtistDetail(url)) },
                onOpenLink = { navViewModel.openLink(it) },
                onExpandPlayer = { navViewModel.expandPlayer() },
            ),
        )

        is NavDestination.YouTubeHome -> YouTubeScreen(
            adaptiveInfo = adaptiveInfo,
            onPlaylistClick = { url, name, coverUrl ->
                navViewModel.navigateTo(
                    NavDestination.CollectionDetail(
                        url = url,
                        sourceId = "youtube",
                        name = name,
                        coverUrl = coverUrl,
                    ),
                )
            },
            onArtistClick = { url, name, imageUrl ->
                navViewModel.navigateTo(
                    NavDestination.ArtistDetail(
                        url = url,
                        sourceId = "youtube",
                        name = name,
                        imageUrl = imageUrl,
                    ),
                )
            },
            onOpenLink = { navViewModel.openLink(it) },
            onExpandPlayer = { navViewModel.expandPlayer() },
        )

        is NavDestination.SoundCloudHome -> SoundCloudScreen(
            adaptiveInfo = adaptiveInfo,
            onCollectionClick = { url, name, coverUrl ->
                navViewModel.navigateTo(
                    NavDestination.CollectionDetail(
                        url = url,
                        sourceId = "soundcloud",
                        name = name,
                        coverUrl = coverUrl,
                    ),
                )
            },
            onArtistClick = { url, name, imageUrl ->
                navViewModel.navigateTo(
                    NavDestination.ArtistDetail(
                        url = url,
                        sourceId = "soundcloud",
                        name = name,
                        imageUrl = imageUrl,
                    ),
                )
            },
            onOpenLink = { navViewModel.openLink(it) },
            onExpandPlayer = { navViewModel.expandPlayer() },
        )

        is NavDestination.Library -> LibraryScreen(
            adaptiveInfo = adaptiveInfo,
            onAlbumClick = { url -> navViewModel.navigateTo(NavDestination.AlbumDetail(url)) },
            onArtistClick = { url ->
                val sourceId = when {
                    url.contains("soundcloud.com") -> "soundcloud"
                    url.contains("youtube.com") || url.contains("youtu.be") -> "youtube"
                    else -> "bandcamp"
                }
                navViewModel.navigateTo(NavDestination.ArtistDetail(url = url, sourceId = sourceId))
            },
            onPlaylistClick = { playlistId -> navViewModel.navigateTo(NavDestination.PlaylistDetail(playlistId)) },
        )

        is NavDestination.Settings -> SettingsScreen(adaptiveInfo = adaptiveInfo)

        else -> error("expected home destination, got $destination")
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AppNavigationDetailDestination(
    destination: NavDestination,
    adaptiveInfo: AdaptiveLayoutInfo,
    detailVmStores: DetailVmStoreRegistry,
    navViewModel: NavigationViewModel = hiltViewModel(),
) {
    when (destination) {
        // Detail destinations: the detail ViewModel is resolved against a
        // per-destination store owner (cleared once the destination leaves
        // every back stack). Only the detail VM is scoped this way - the
        // screens' PlayerViewModel/NavigationViewModel defaults still
        // resolve to the activity-scoped shared instances.
        is NavDestination.AlbumDetail -> {
            val storeKey = checkNotNull(detailStoreKey(destination)) {
                "detailStoreKey must be non-null for detail destination $destination"
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
                "detailStoreKey must be non-null for detail destination $destination"
            }
            ArtistDetailScreen(
                adaptiveInfo = adaptiveInfo,
                args = ArtistDetailArgs(
                    sourceId = destination.sourceId,
                    artistUrl = destination.url,
                    artistNameHint = destination.name,
                    artistImageHint = destination.imageUrl,
                ),
                onAlbumClick = { url ->
                    when (destination.sourceId) {
                        "youtube", "soundcloud" -> navViewModel.navigateTo(
                            NavDestination.CollectionDetail(
                                url = url,
                                sourceId = destination.sourceId,
                            ),
                        )

                        else -> navViewModel.navigateTo(NavDestination.AlbumDetail(url))
                    }
                },
                onBack = { navViewModel.navigateBack() },
                viewModel = hiltViewModel(viewModelStoreOwner = detailVmStores.owner(storeKey), key = storeKey),
            )
        }

        is NavDestination.PlaylistDetail -> {
            val storeKey = checkNotNull(detailStoreKey(destination)) {
                "detailStoreKey must be non-null for detail destination $destination"
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
                "detailStoreKey must be non-null for detail destination $destination"
            }
            CollectionDetailScreen(
                adaptiveInfo = adaptiveInfo,
                args = CollectionDetailArgs(
                    sourceId = destination.sourceId,
                    collectionUrl = destination.url,
                    collectionName = destination.name,
                    collectionCoverHint = destination.coverUrl,
                ),
                onBack = { navViewModel.navigateBack() },
                viewModel = hiltViewModel(viewModelStoreOwner = detailVmStores.owner(storeKey), key = storeKey),
            )
        }

        else -> error("expected detail destination, got $destination")
    }
}
