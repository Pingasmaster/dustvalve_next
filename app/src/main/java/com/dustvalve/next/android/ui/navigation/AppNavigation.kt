package com.dustvalve.next.android.ui.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dustvalve.next.android.R
import com.dustvalve.next.android.domain.repository.AccountRepository
import com.dustvalve.next.android.ui.screens.album.AlbumDetailScreen
import com.dustvalve.next.android.ui.screens.bandcamp.BandcampScreen
import com.dustvalve.next.android.ui.screens.detail.ArtistDetailScreen
import com.dustvalve.next.android.ui.screens.detail.CollectionDetailScreen
import com.dustvalve.next.android.ui.screens.library.LibraryScreen
import com.dustvalve.next.android.ui.screens.local.LocalScreen
import com.dustvalve.next.android.ui.screens.playlist.PlaylistDetailScreen
import com.dustvalve.next.android.ui.screens.settings.AccountLoginScreen
import com.dustvalve.next.android.ui.screens.settings.SettingsScreen
import com.dustvalve.next.android.ui.screens.settings.YouTubeMusicLoginScreen
import com.dustvalve.next.android.ui.screens.youtube.YouTubeScreen
import com.dustvalve.next.android.ui.util.iconRes
import com.dustvalve.next.android.util.LinkResourceType
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
// LongMethod / CyclomaticComplexMethod: AppNavigation is the single NavDestination
// when-router; its size and branching are intrinsic to that role (one arm per
// destination), so they are suppressed rather than split into artificial helpers.
@Suppress("LongMethod", "CyclomaticComplexMethod")
fun AppNavigation(
    accountRepository: AccountRepository,
    modifier: Modifier = Modifier,
    // Activity-scoped: hiltViewModel() resolves to MainActivity's
    // ViewModelStoreOwner, so this is the same instance MainContent owns.
    // Each child screen self-injects PlayerViewModel via its own
    // hiltViewModel() default, which resolves to this same shared instance,
    // so we hoist navigation state here and never forward a ViewModel down.
    navViewModel: NavigationViewModel = hiltViewModel(),
) {
    val backStack by navViewModel.backStack.collectAsStateWithLifecycle()
    val isForward by navViewModel.lastNavigationForward.collectAsStateWithLifecycle()
    val currentDestination = backStack.lastOrNull() ?: NavDestination.LocalHome
    val coroutineScope = rememberCoroutineScope()
    val pendingLink by navViewModel.pendingLinkConfirmation.collectAsStateWithLifecycle()
    val linkSnackbarHostState = remember { SnackbarHostState() }
    val unsupportedMsg = stringResource(R.string.snackbar_unsupported_source)

    // Surface "this link isn't from a supported source" regardless of which tab triggered it.
    androidx.compose.runtime.LaunchedEffect(Unit) {
        navViewModel.unsupportedLinkEvents.collect {
            linkSnackbarHostState.showSnackbar(unsupportedMsg)
        }
    }

    // Full-screen transitions use slow specs for a grander, more cinematic feel
    val slideSpec = MaterialTheme.motionScheme.slowSpatialSpec<IntOffset>()
    val fadeSpec = MaterialTheme.motionScheme.slowEffectsSpec<Float>()

    Box(modifier = modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = currentDestination,
            modifier = Modifier,
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
                is NavDestination.LocalHome -> LocalScreen(
                    onExpandPlayer = { navViewModel.expandPlayer() },
                )

                is NavDestination.BandcampHome -> BandcampScreen(
                    onAlbumClick = { url -> navViewModel.navigateTo(NavDestination.AlbumDetail(url)) },
                    onArtistClick = { url -> navViewModel.navigateTo(NavDestination.ArtistDetail(url)) },
                    onOpenLink = { navViewModel.openLink(it) },
                    onExpandPlayer = { navViewModel.expandPlayer() },
                )

                is NavDestination.YouTubeHome -> YouTubeScreen(
                    onPlaylistClick = { url, name ->
                        navViewModel.navigateTo(
                            NavDestination.CollectionDetail(url = url, sourceId = "youtube", name = name),
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

                is NavDestination.Library -> LibraryScreen(
                    onAlbumClick = { url -> navViewModel.navigateTo(NavDestination.AlbumDetail(url)) },
                    onArtistClick = { url ->
                        val sourceId = if (url.contains("youtube.com") || url.contains("youtu.be")) {
                            "youtube"
                        } else {
                            "bandcamp"
                        }
                        navViewModel.navigateTo(NavDestination.ArtistDetail(url = url, sourceId = sourceId))
                    },
                    onPlaylistClick = { playlistId -> navViewModel.navigateTo(NavDestination.PlaylistDetail(playlistId)) },
                )

                is NavDestination.Settings -> SettingsScreen(
                    onBandcampLoginClick = { navViewModel.navigateTo(NavDestination.AccountLogin) },
                    onYouTubeMusicLoginClick = { navViewModel.navigateTo(NavDestination.YouTubeMusicLogin) },
                )

                is NavDestination.AlbumDetail -> AlbumDetailScreen(
                    albumUrl = destination.url,
                    onArtistClick = { url -> navViewModel.navigateTo(NavDestination.ArtistDetail(url)) },
                    onBack = { navViewModel.navigateBack() },
                    viewModel = hiltViewModel(key = destination.url),
                )

                is NavDestination.ArtistDetail -> ArtistDetailScreen(
                    sourceId = destination.sourceId,
                    artistUrl = destination.url,
                    artistNameHint = destination.name,
                    artistImageHint = destination.imageUrl,
                    onAlbumClick = { url -> navViewModel.navigateTo(NavDestination.AlbumDetail(url)) },
                    onBack = { navViewModel.navigateBack() },
                    viewModel = hiltViewModel(key = "${destination.sourceId}|${destination.url}"),
                )

                is NavDestination.PlaylistDetail -> PlaylistDetailScreen(
                    playlistId = destination.playlistId,
                    onBack = { navViewModel.navigateBack() },
                    viewModel = hiltViewModel(key = destination.playlistId),
                )

                is NavDestination.CollectionDetail -> CollectionDetailScreen(
                    sourceId = destination.sourceId,
                    collectionUrl = destination.url,
                    collectionName = destination.name,
                    onBack = { navViewModel.navigateBack() },
                    viewModel = hiltViewModel(key = "${destination.sourceId}|${destination.url}"),
                )

                is NavDestination.YouTubeMusicLogin -> YouTubeMusicLoginScreen(
                    onLoginSuccess = { cookies ->
                        coroutineScope.launch {
                            try {
                                accountRepository.saveYouTubeMusicCookies(cookies)
                            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                                throw e
                            } catch (_: Exception) {
                                // Cookie save failed - still navigate back
                            }
                            navViewModel.navigateBack()
                        }
                    },
                    onBack = { navViewModel.navigateBack() },
                )

                is NavDestination.AccountLogin -> AccountLoginScreen(
                    onLoginSuccess = { cookies ->
                        coroutineScope.launch {
                            try {
                                accountRepository.saveCookies(cookies)
                            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                                throw e
                            } catch (_: Exception) {
                                // Cookie save failed - still navigate back
                            }
                            navViewModel.navigateBack()
                        }
                    },
                    onBack = { navViewModel.navigateBack() },
                )
            }
        }

        // "This link isn't from a supported source" feedback
        SnackbarHost(
            hostState = linkSnackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
        )

        // Enable-provider confirmation for a link pointing at a disabled source
        pendingLink?.let { pending ->
            val typeNoun = stringResource(linkKindRes(pending.type))
            AlertDialog(
                onDismissRequest = { navViewModel.dismissPendingLink() },
                icon = {
                    Icon(
                        painter = painterResource(pending.provider.iconRes),
                        contentDescription = null,
                    )
                },
                title = { Text(stringResource(R.string.provider_enable_title, pending.provider.label)) },
                text = {
                    Text(stringResource(R.string.provider_enable_text, pending.provider.label, typeNoun))
                },
                confirmButton = {
                    TextButton(
                        onClick = { navViewModel.confirmPendingLink() },
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Text(stringResource(R.string.common_action_enable))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { navViewModel.dismissPendingLink() },
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Text(stringResource(R.string.common_action_cancel))
                    }
                },
            )
        }
    }
}

private fun linkKindRes(type: LinkResourceType): Int = when (type) {
    LinkResourceType.VIDEO -> R.string.link_kind_video
    LinkResourceType.SONG -> R.string.link_kind_song
    LinkResourceType.PLAYLIST -> R.string.link_kind_playlist
    LinkResourceType.ALBUM -> R.string.link_kind_album
    LinkResourceType.ARTIST -> R.string.link_kind_artist
    LinkResourceType.TRACK -> R.string.link_kind_track
}
