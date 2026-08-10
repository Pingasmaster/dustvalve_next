package com.dustvalve.next.android.ui.screens.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.graphics.shapes.Morph
import com.dustvalve.next.android.domain.model.Track
import com.dustvalve.next.android.player.QueueEntry
import kotlinx.coroutines.CoroutineScope

/** Animation handles owned by [FullPlayer] and passed as one parameter. */
@Stable
internal class FullPlayerMotion(
    val heartMorph: Morph,
    val heartProgress: Animatable<Float, AnimationVector1D>,
    val heartInSpec: AnimationSpec<Float>,
    val heartOutSpec: AnimationSpec<Float>,
    val albumSwipeOffsetX: Animatable<Float, AnimationVector1D>,
    val feedbackScale: Animatable<Float, AnimationVector1D>,
    val feedbackSpec: AnimationSpec<Float>,
    val scope: CoroutineScope,
)

@Stable
class FullPlayerCollapseActions(
    val onCollapse: () -> Unit,
    val onCollapseSeek: (Float) -> Unit,
    val onCollapseSettle: (Float) -> Unit,
    val onCollapseCancel: () -> Unit,
)

@Stable
class FullPlayerNavActions(val onArtistClick: (Track) -> Unit, val onAlbumClick: (Track) -> Unit)

@Stable
class FullPlayerSheetActions(
    val onShowVolumeSheet: () -> Unit,
    val onShowQueueSheet: () -> Unit,
    val onShowDebugSheet: () -> Unit,
    val onShowDeleteDownloadDialog: () -> Unit,
    val onShowPlaylistSheet: () -> Unit,
    val onEntryLongClick: (QueueEntry) -> Unit,
    val onCarouselModeChange: (Boolean) -> Unit,
)

/** Chrome callbacks for sheets / navigation owned by [FullPlayer]. */
@Stable
class FullPlayerChrome(
    private val collapse: FullPlayerCollapseActions,
    private val nav: FullPlayerNavActions,
    private val sheets: FullPlayerSheetActions,
) {
    val onCollapse: () -> Unit get() = collapse.onCollapse
    val onCollapseSeek: (Float) -> Unit get() = collapse.onCollapseSeek
    val onCollapseSettle: (Float) -> Unit get() = collapse.onCollapseSettle
    val onCollapseCancel: () -> Unit get() = collapse.onCollapseCancel
    val onArtistClick: (Track) -> Unit get() = nav.onArtistClick
    val onAlbumClick: (Track) -> Unit get() = nav.onAlbumClick
    val onShowVolumeSheet: () -> Unit get() = sheets.onShowVolumeSheet
    val onShowQueueSheet: () -> Unit get() = sheets.onShowQueueSheet
    val onShowDebugSheet: () -> Unit get() = sheets.onShowDebugSheet
    val onShowDeleteDownloadDialog: () -> Unit get() = sheets.onShowDeleteDownloadDialog
    val onShowPlaylistSheet: () -> Unit get() = sheets.onShowPlaylistSheet
    val onEntryLongClick: (QueueEntry) -> Unit get() = sheets.onEntryLongClick
    val onCarouselModeChange: (Boolean) -> Unit get() = sheets.onCarouselModeChange
}

@Stable
internal class FullPlayerSharedModifiers(val surfaceShared: Modifier, val artShared: Modifier)

@Stable
internal class FullPlayerLayout(val expandDistancePx: Float, val isCarouselMode: Boolean)

@Stable
internal class FullPlayerPlaybackSnapshot(val state: PlayerUiState, val positionState: PlaybackPositionState)

@Stable
internal class FullPlayerTrackActions(
    val onArtistClick: (Track) -> Unit,
    val onAlbumClick: (Track) -> Unit,
    val onToggleFavorite: () -> Unit,
    val onRequestDeleteDownload: () -> Unit,
    val onDownloadTrack: () -> Unit,
    val onAddToPlaylist: () -> Unit,
)

@Stable
internal class FullPlayerTransportActions(
    val onPrevious: () -> Unit,
    val onPlayPause: () -> Unit,
    val onNext: () -> Unit,
    val onSeek: (Long) -> Unit,
    val onToggleShuffle: () -> Unit,
    val onToggleRepeat: () -> Unit,
)

@Stable
internal class FullPlayerArtActions(
    val onPrevious: () -> Unit,
    val onPlayPause: () -> Unit,
    val onNext: () -> Unit,
    val onSkipToQueueIndex: (Int) -> Unit,
    val onSetVolume: (Float) -> Unit,
    val onToggleFavorite: () -> Unit,
)

@Stable
internal class FullPlayerActions(
    val transport: FullPlayerTransportActions,
    val track: FullPlayerTrackActions,
    val art: FullPlayerArtActions,
)

@Stable
internal class FullPlayerScaffoldModel(
    val playback: FullPlayerPlaybackSnapshot,
    val snackbarHostState: SnackbarHostState,
    val shared: FullPlayerSharedModifiers,
    val motion: FullPlayerMotion,
    val chrome: FullPlayerChrome,
    val layout: FullPlayerLayout,
    val actions: FullPlayerActions,
)

/** Mutable sheet/dialog visibility owned by [FullPlayer]. */
@Stable
internal class FullPlayerSheetState {
    var showDeleteDownloadDialog by mutableStateOf(false)
    var showPlaylistSheet by mutableStateOf(false)
    var showDebugSheet by mutableStateOf(false)
    var showVolumeSheet by mutableStateOf(false)
    var showQueueSheet by mutableStateOf(false)
    var isCarouselMode by mutableStateOf(false)
    var upNextContextEntry by mutableStateOf<QueueEntry?>(null)
    var showUpNextPlaylistSheet by mutableStateOf(false)
}
