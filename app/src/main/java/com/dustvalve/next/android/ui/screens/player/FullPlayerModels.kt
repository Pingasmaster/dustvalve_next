package com.dustvalve.next.android.ui.screens.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
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

/** Chrome callbacks for sheets / navigation owned by [FullPlayer]. */
@Stable
internal class FullPlayerChrome(
    val onCollapse: () -> Unit,
    val onCollapseSeek: (Float) -> Unit,
    val onCollapseSettle: (Float) -> Unit,
    val onArtistClick: (Track) -> Unit,
    val onAlbumClick: (Track) -> Unit,
    val onShowVolumeSheet: () -> Unit,
    val onShowQueueSheet: () -> Unit,
    val onShowDebugSheet: () -> Unit,
    val onShowDeleteDownloadDialog: () -> Unit,
    val onShowPlaylistSheet: () -> Unit,
    val onEntryLongClick: (QueueEntry) -> Unit,
    val onCarouselModeChange: (Boolean) -> Unit,
)

@Stable
internal class FullPlayerSharedModifiers(
    val surfaceShared: Modifier,
    val artShared: Modifier,
)

@Stable
internal class FullPlayerLayout(
    val expandDistancePx: Float,
    val isCarouselMode: Boolean,
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
