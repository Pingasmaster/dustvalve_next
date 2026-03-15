package com.dustvalve.next.android.ui.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.IntOffset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dustvalve.next.android.domain.repository.AccountRepository
import com.dustvalve.next.android.ui.screens.album.AlbumDetailScreen
import com.dustvalve.next.android.ui.screens.artist.ArtistDetailScreen
import com.dustvalve.next.android.ui.screens.home.HomeScreen
import com.dustvalve.next.android.ui.screens.library.LibraryScreen
import com.dustvalve.next.android.ui.screens.playlist.PlaylistDetailScreen
import com.dustvalve.next.android.ui.screens.settings.AccountLoginScreen
import com.dustvalve.next.android.ui.screens.settings.SettingsScreen
import com.dustvalve.next.android.ui.screens.player.PlayerViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppNavigation(
    navViewModel: NavigationViewModel,
    playerViewModel: PlayerViewModel,
    accountRepository: AccountRepository,
    modifier: Modifier = Modifier,
) {
    val backStack by navViewModel.backStack.collectAsStateWithLifecycle()
    val isForward by navViewModel.lastNavigationForward.collectAsStateWithLifecycle()
    val currentDestination = backStack.lastOrNull() ?: NavDestination.Home
    val coroutineScope = rememberCoroutineScope()

    // Full-screen transitions use slow specs for a grander, more cinematic feel
    val slideSpec = MaterialTheme.motionScheme.slowSpatialSpec<IntOffset>()
    val fadeSpec = MaterialTheme.motionScheme.slowEffectsSpec<Float>()

    AnimatedContent(
        targetState = currentDestination,
        modifier = modifier,
        transitionSpec = {
            if (isForward) {
                (fadeIn(animationSpec = fadeSpec) + slideInHorizontally(animationSpec = slideSpec) { it / 4 })
                    .togetherWith(fadeOut(animationSpec = fadeSpec) + slideOutHorizontally(animationSpec = slideSpec) { -it / 4 })
            } else {
                (fadeIn(animationSpec = fadeSpec) + slideInHorizontally(animationSpec = slideSpec) { -it / 4 })
                    .togetherWith(fadeOut(animationSpec = fadeSpec) + slideOutHorizontally(animationSpec = slideSpec) { it / 4 })
            }
        },
        label = "NavContent",
    ) { destination ->
        when (destination) {
            is NavDestination.Home -> HomeScreen(
                onAlbumClick = { url -> navViewModel.navigateTo(NavDestination.AlbumDetail(url)) },
                onArtistClick = { url -> navViewModel.navigateTo(NavDestination.ArtistDetail(url)) },
                playerViewModel = playerViewModel,
            )
            is NavDestination.Library -> LibraryScreen(
                onAlbumClick = { url -> navViewModel.navigateTo(NavDestination.AlbumDetail(url)) },
                onArtistClick = { url -> navViewModel.navigateTo(NavDestination.ArtistDetail(url)) },
                onPlaylistClick = { playlistId -> navViewModel.navigateTo(NavDestination.PlaylistDetail(playlistId)) },
                playerViewModel = playerViewModel,
            )
            is NavDestination.Settings -> SettingsScreen(
                onLoginClick = { navViewModel.navigateTo(NavDestination.AccountLogin) },
                onDownloadsClick = {
                    navViewModel.navigateTo(
                        NavDestination.PlaylistDetail(com.dustvalve.next.android.domain.model.Playlist.ID_DOWNLOADS)
                    )
                },
            )
            is NavDestination.AlbumDetail -> AlbumDetailScreen(
                albumUrl = destination.url,
                onArtistClick = { url -> navViewModel.navigateTo(NavDestination.ArtistDetail(url)) },
                onBack = { navViewModel.navigateBack() },
                playerViewModel = playerViewModel,
                viewModel = hiltViewModel(key = destination.url),
            )
            is NavDestination.ArtistDetail -> ArtistDetailScreen(
                artistUrl = destination.url,
                onAlbumClick = { url -> navViewModel.navigateTo(NavDestination.AlbumDetail(url)) },
                onBack = { navViewModel.navigateBack() },
                playerViewModel = playerViewModel,
                viewModel = hiltViewModel(key = destination.url),
            )
            is NavDestination.PlaylistDetail -> PlaylistDetailScreen(
                playlistId = destination.playlistId,
                onBack = { navViewModel.navigateBack() },
                onAlbumClick = { url -> navViewModel.navigateTo(NavDestination.AlbumDetail(url)) },
                onArtistClick = { url -> navViewModel.navigateTo(NavDestination.ArtistDetail(url)) },
                playerViewModel = playerViewModel,
                viewModel = hiltViewModel(key = destination.playlistId),
            )
            is NavDestination.AccountLogin -> AccountLoginScreen(
                onLoginSuccess = { cookies ->
                    coroutineScope.launch {
                        accountRepository.saveCookies(cookies)
                        navViewModel.navigateBack()
                    }
                },
                onBack = { navViewModel.navigateBack() },
            )
        }
    }
}
