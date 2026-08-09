package com.dustvalve.next.android.ui.screens.player

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dustvalve.next.android.domain.model.Track
import com.dustvalve.next.android.ui.adaptive.AdaptiveLayoutInfo

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)
@Composable
fun FullPlayer(
    adaptiveInfo: AdaptiveLayoutInfo,
    sharedScope: SharedTransitionScope,
    visScope: AnimatedVisibilityScope,
    expandDistancePx: Float,
    onCollapse: () -> Unit,
    onCollapseSeek: (Float) -> Unit,
    onCollapseSettle: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onArtistClick: (Track) -> Unit = {},
    onAlbumClick: (Track) -> Unit = {},
    playerViewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by playerViewModel.uiState.collectAsStateWithLifecycle()
    val positionState by playerViewModel.positionState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val sheets = remember { FullPlayerSheetState() }

    val snackbarText = state.snackbarMessage?.asString()
    LaunchedEffect(snackbarText) {
        snackbarText?.let { message ->
            try {
                snackbarHostState.showSnackbar(message)
            } finally {
                playerViewModel.clearSnackbar()
            }
        }
    }

    val heartProgress = remember { Animatable(0f) }
    val albumSwipeOffsetX = remember { Animatable(0f) }
    val feedbackScale = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val squarePolygon = remember { MaterialShapes.Square }
    val heartPolygon = remember { MaterialShapes.Heart }
    val heartMorph = remember(squarePolygon, heartPolygon) { Morph(squarePolygon, heartPolygon) }
    val heartInSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val heartOutSpec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    val feedbackSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val motion = FullPlayerMotion(
        heartMorph = heartMorph,
        heartProgress = heartProgress,
        heartInSpec = heartInSpec,
        heartOutSpec = heartOutSpec,
        albumSwipeOffsetX = albumSwipeOffsetX,
        feedbackScale = feedbackScale,
        feedbackSpec = feedbackSpec,
        scope = scope,
    )
    val chrome = FullPlayerChrome(
        onCollapse = onCollapse,
        onCollapseSeek = onCollapseSeek,
        onCollapseSettle = onCollapseSettle,
        onArtistClick = onArtistClick,
        onAlbumClick = onAlbumClick,
        onShowVolumeSheet = { sheets.showVolumeSheet = true },
        onShowQueueSheet = { sheets.showQueueSheet = true },
        onShowDebugSheet = { sheets.showDebugSheet = true },
        onShowDeleteDownloadDialog = { sheets.showDeleteDownloadDialog = true },
        onShowPlaylistSheet = { sheets.showPlaylistSheet = true },
        onEntryLongClick = { sheets.upNextContextEntry = it },
        onCarouselModeChange = { sheets.isCarouselMode = it },
    )

    val boundsSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Rect>()
    val shared = with(sharedScope) {
        FullPlayerSharedModifiers(
            surfaceShared = Modifier.sharedBounds(
                sharedContentState = rememberSharedContentState(PLAYER_SURFACE_KEY),
                animatedVisibilityScope = visScope,
                boundsTransform = BoundsTransform { _, _ -> boundsSpec },
                resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
                clipInOverlayDuringTransition = OverlayClip(RoundedCornerShape(0.dp)),
            ),
            artShared = Modifier.sharedElement(
                sharedContentState = rememberSharedContentState(PLAYER_ART_KEY),
                animatedVisibilityScope = visScope,
                boundsTransform = BoundsTransform { _, _ -> boundsSpec },
            ),
        )
    }

    FullPlayerSheetHost(adaptiveInfo = adaptiveInfo, state = state, sheets = sheets)
    FullPlayerScaffold(
        adaptiveInfo = adaptiveInfo,
        state = state,
        positionState = positionState,
        snackbarHostState = snackbarHostState,
        shared = shared,
        motion = motion,
        chrome = chrome,
        layout = FullPlayerLayout(
            expandDistancePx = expandDistancePx,
            isCarouselMode = sheets.isCarouselMode,
        ),
        modifier = modifier,
    )
}
