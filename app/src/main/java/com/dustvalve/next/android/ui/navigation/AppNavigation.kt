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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.dustvalve.next.android.R
import com.dustvalve.next.android.ui.TestTags
import com.dustvalve.next.android.ui.adaptive.AdaptiveLayoutInfo
import com.dustvalve.next.android.ui.util.iconRes
import com.dustvalve.next.android.util.LinkResourceType

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppNavigation(
    adaptiveInfo: AdaptiveLayoutInfo,
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
    val currentTab by navViewModel.currentTab.collectAsStateWithLifecycle()
    val currentDestination = backStack.lastOrNull() ?: NavDestination.Library
    val pendingLink by navViewModel.pendingLinkConfirmation.collectAsStateWithLifecycle()
    val linkSnackbarHostState = remember { SnackbarHostState() }
    val unsupportedMsg = stringResource(R.string.snackbar_unsupported_source)
    val deepLinkPlayFailedMsg = stringResource(R.string.common_failed_to_play)
    val useLibraryDualPane =
        adaptiveInfo.useDualPane && currentTab == BottomNavItem.LIBRARY

    // Surface "this link isn't from a supported source" regardless of which tab triggered it.
    androidx.compose.runtime.LaunchedEffect(Unit) {
        navViewModel.unsupportedLinkEvents.collect {
            linkSnackbarHostState.showSnackbar(unsupportedMsg)
        }
    }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        navViewModel.deepLinkPlayFailedEvents.collect {
            linkSnackbarHostState.showSnackbar(deepLinkPlayFailedMsg)
        }
    }

    // Per-destination ViewModelStore scoping for detail pages. Each detail
    // destination gets its own store (created lazily below) so its ViewModel
    // is cleared - collectors cancelled, memory released - once the
    // destination is no longer reachable from ANY tab's back stack, instead
    // of leaking into MainActivity's store for the Activity's lifetime.
    val hostVmStoreOwner = checkNotNull(LocalViewModelStoreOwner.current) {
        "AppNavigation requires a ViewModelStoreOwner"
    }
    val detailVmStores = remember { DetailVmStoreRegistry(hostVmStoreOwner) }
    val allDestinations by navViewModel.allDestinations.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(allDestinations) {
        detailVmStores.retainOnly(allDestinations.mapNotNull { detailStoreKey(it) }.toSet())
    }
    DisposableEffect(Unit) {
        onDispose { detailVmStores.clearAll() }
    }

    // Full-screen transitions use slow specs for a grander, more cinematic feel
    val slideSpec = MaterialTheme.motionScheme.slowSpatialSpec<IntOffset>()
    val fadeSpec = MaterialTheme.motionScheme.slowEffectsSpec<Float>()

    Box(modifier = modifier.fillMaxSize()) {
        if (useLibraryDualPane) {
            LibraryListDetailHost(
                adaptiveInfo = adaptiveInfo,
                backStack = backStack,
                detailVmStores = detailVmStores,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
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
                AppNavigationDestination(
                    destination = destination,
                    adaptiveInfo = adaptiveInfo,
                    detailVmStores = detailVmStores,
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
            ProviderEnableDialog(
                pending = pending,
                onConfirm = { navViewModel.confirmPendingLink() },
                onDismiss = { navViewModel.dismissPendingLink() },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ProviderEnableDialog(pending: PendingLink, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val typeNoun = stringResource(linkKindRes(pending.type))
    AlertDialog(
        modifier = Modifier.testTag(TestTags.PROVIDER_ENABLE_DIALOG),
        onDismissRequest = onDismiss,
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
                onClick = onConfirm,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(R.string.common_action_enable))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(R.string.common_action_cancel))
            }
        },
    )
}

private fun linkKindRes(type: LinkResourceType): Int = when (type) {
    LinkResourceType.VIDEO -> R.string.link_kind_video
    LinkResourceType.SONG -> R.string.link_kind_song
    LinkResourceType.PLAYLIST -> R.string.link_kind_playlist
    LinkResourceType.ALBUM -> R.string.link_kind_album
    LinkResourceType.ARTIST -> R.string.link_kind_artist
    LinkResourceType.TRACK -> R.string.link_kind_track
}
