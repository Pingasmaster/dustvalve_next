package com.dustvalve.next.android.ui.screens.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import com.dustvalve.next.android.R
import com.dustvalve.next.android.domain.model.Track
import com.dustvalve.next.android.ui.adaptive.AdaptiveLayoutInfo
import com.dustvalve.next.android.ui.components.MorphShape
import com.dustvalve.next.android.ui.components.TrackArtPlaceholder
import com.dustvalve.next.android.ui.theme.AppShapes
import com.dustvalve.next.android.ui.util.tick
import com.dustvalve.next.android.ui.util.toggle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun FullPlayerAlbumArtRow(
    adaptiveInfo: AdaptiveLayoutInfo,
    track: Track,
    state: PlayerUiState,
    isCarouselMode: Boolean,
    motion: FullPlayerMotion,
    chrome: FullPlayerChrome,
    artActions: FullPlayerArtActions,
    modifier: Modifier = Modifier,
) {
    val adaptive = adaptiveInfo
    val artMax = adaptive.heroMaxSize
    val artCapped = artMax != Dp.Unspecified
    BackHandler(enabled = isCarouselMode) {
        chrome.onCarouselModeChange(false)
    }
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (artCapped) Arrangement.Center else Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.then(
                when {
                    isCarouselMode && artCapped ->
                        Modifier.fillMaxWidth().height(artMax)
                    isCarouselMode ->
                        Modifier.weight(1f).aspectRatio(1f)
                    artCapped ->
                        Modifier.widthIn(max = artMax).fillMaxWidth().aspectRatio(1f)
                    else ->
                        Modifier.weight(1f).aspectRatio(1f)
                },
            ),
            contentAlignment = Alignment.Center,
        ) {
            val carouselTransitionSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
            AnimatedContent(
                targetState = isCarouselMode,
                transitionSpec = {
                    fadeIn(animationSpec = carouselTransitionSpec) togetherWith
                        fadeOut(animationSpec = carouselTransitionSpec)
                },
                label = "albumArtCarousel",
            ) { carousel ->
                if (carousel) {
                    FullPlayerAlbumCarousel(
                        queue = state.queue,
                        currentQueueIndex = state.currentQueueIndex,
                        preferredItemWidth = adaptive.carouselItemWidth,
                        onSelectIndex = { index ->
                            artActions.onSkipToQueueIndex(index)
                            chrome.onCarouselModeChange(false)
                        },
                        onEmpty = { chrome.onCarouselModeChange(false) },
                    )
                } else {
                    FullPlayerAlbumArtStack(
                        track = track,
                        state = state,
                        motion = motion,
                        chrome = chrome,
                        artActions = artActions,
                    )
                }
            }
        }
        if (state.showInlineVolumeSlider) {
            FullPlayerInlineVolumeSlider(
                volumeLevel = state.volumeLevel,
                onVolumeChange = artActions.onSetVolume,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FullPlayerAlbumCarousel(
    queue: List<Track>,
    currentQueueIndex: Int,
    preferredItemWidth: Dp,
    onSelectIndex: (Int) -> Unit,
    onEmpty: () -> Unit,
) {
    val onEmptyState = rememberUpdatedState(onEmpty)
    val carouselTracks = if (currentQueueIndex >= 0) {
        queue.subList(currentQueueIndex, minOf(currentQueueIndex + 26, queue.size))
    } else {
        emptyList()
    }
    if (carouselTracks.isEmpty()) {
        LaunchedEffect(Unit) { onEmptyState.value() }
        return
    }
    val carouselState = rememberCarouselState { carouselTracks.size }
    HorizontalMultiBrowseCarousel(
        state = carouselState,
        preferredItemWidth = preferredItemWidth,
        modifier = Modifier.fillMaxSize(),
        itemSpacing = 8.dp,
    ) { page ->
        val carouselTrack = carouselTracks[page]
        val carouselQueueIndex = currentQueueIndex + page
        Box(
            modifier = Modifier
                .maskClip(AppShapes.SearchResultTrack)
                .aspectRatio(1f)
                .clickable { onSelectIndex(carouselQueueIndex) },
        ) {
            if (carouselTrack.artUrl.isNotBlank()) {
                AsyncImage(
                    model = carouselTrack.artUrl,
                    contentDescription = carouselTrack.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                TrackArtPlaceholder(modifier = Modifier.fillMaxSize(), iconSize = 48.dp)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FullPlayerAlbumArtStack(
    track: Track,
    state: PlayerUiState,
    motion: FullPlayerMotion,
    chrome: FullPlayerChrome,
    artActions: FullPlayerArtActions,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        FullPlayerStackedCovers(
            queue = state.queue,
            currentQueueIndex = state.currentQueueIndex,
            heartMorph = motion.heartMorph,
            onSkipToQueueIndex = artActions.onSkipToQueueIndex,
        )
        FullPlayerMainCover(
            track = track,
            state = state,
            motion = motion,
            chrome = chrome,
            artActions = artActions,
        )
    }
}

@Composable
private fun FullPlayerStackedCovers(
    queue: List<Track>,
    currentQueueIndex: Int,
    heartMorph: androidx.graphics.shapes.Morph,
    onSkipToQueueIndex: (Int) -> Unit,
) {
    val stackedTracks = if (currentQueueIndex >= 0 && currentQueueIndex < queue.lastIndex) {
        queue.subList(currentQueueIndex + 1, minOf(currentQueueIndex + 4, queue.size))
    } else {
        emptyList()
    }
    stackedTracks.reversed().forEachIndexed { reverseIndex, stackTrack ->
        val stackIndex = stackedTracks.size - 1 - reverseIndex
        val offsetX = ((stackIndex + 1) * 20).dp
        val offsetY = (-(stackIndex + 1) * 24).dp
        val stackScale = 1f - ((stackIndex + 1) * 0.06f)
        val actualQueueIndex = currentQueueIndex + 1 + stackIndex
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(-((stackIndex + 1).toFloat()))
                .graphicsLayer {
                    translationX = offsetX.toPx()
                    translationY = offsetY.toPx()
                    scaleX = stackScale
                    scaleY = stackScale
                    rotationZ = (stackIndex + 1) * 5f
                    transformOrigin = TransformOrigin(1f, 0f)
                }
                .clip(MorphShape(heartMorph, 0f))
                .clickable { onSkipToQueueIndex(actualQueueIndex) },
        ) {
            if (stackTrack.artUrl.isNotBlank()) {
                AsyncImage(
                    model = stackTrack.artUrl,
                    contentDescription = stackTrack.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                TrackArtPlaceholder(modifier = Modifier.fillMaxSize(), iconSize = 48.dp)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FullPlayerMainCover(
    track: Track,
    state: PlayerUiState,
    motion: FullPlayerMotion,
    chrome: FullPlayerChrome,
    artActions: FullPlayerArtActions,
    modifier: Modifier = Modifier,
) {
    val hapticFeedback = LocalHapticFeedback.current
    var showPlayPauseFeedback by remember { mutableStateOf(false) }
    var feedbackIsPlaying by remember { mutableStateOf(false) }
    val swipeSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val albumArtGestureModifier = Modifier
        .fillMaxSize()
        .zIndex(1f)
        .graphicsLayer {
            translationX = motion.albumSwipeOffsetX.value
            shape = MorphShape(motion.heartMorph, motion.heartProgress.value)
            clip = true
        }
        .pointerInput(Unit) {
            detectHorizontalDragGestures(
                onDragEnd = {
                    val threshold = size.width * 0.3f
                    if (motion.albumSwipeOffsetX.value < -threshold) {
                        motion.scope.launch {
                            motion.albumSwipeOffsetX.animateTo(-size.width.toFloat(), swipeSpec)
                            artActions.onNext()
                            motion.albumSwipeOffsetX.snapTo(0f)
                        }
                    } else if (motion.albumSwipeOffsetX.value > threshold) {
                        motion.scope.launch {
                            motion.albumSwipeOffsetX.animateTo(size.width.toFloat(), swipeSpec)
                            artActions.onPrevious()
                            motion.albumSwipeOffsetX.snapTo(0f)
                        }
                    } else {
                        motion.scope.launch { motion.albumSwipeOffsetX.animateTo(0f, swipeSpec) }
                    }
                },
                onDragCancel = {
                    motion.scope.launch { motion.albumSwipeOffsetX.animateTo(0f, swipeSpec) }
                },
                onHorizontalDrag = { change, dragAmount ->
                    change.consume()
                    motion.scope.launch {
                        motion.albumSwipeOffsetX.snapTo(motion.albumSwipeOffsetX.value + dragAmount)
                    }
                },
            )
        }
        .pointerInput(Unit) {
            detectTapGestures(
                onTap = {
                    feedbackIsPlaying = state.isPlaying
                    artActions.onPlayPause()
                    motion.scope.launch {
                        motion.feedbackScale.snapTo(0f)
                        showPlayPauseFeedback = true
                        motion.feedbackScale.animateTo(1f, motion.feedbackSpec)
                        delay(400L)
                        motion.feedbackScale.animateTo(0f, motion.feedbackSpec)
                        showPlayPauseFeedback = false
                    }
                },
                onDoubleTap = {
                    hapticFeedback.toggle(!track.isFavorite)
                    artActions.onToggleFavorite()
                    motion.scope.launch {
                        motion.heartProgress.animateTo(1f, motion.heartInSpec)
                        delay(1000L)
                        motion.heartProgress.animateTo(0f, motion.heartOutSpec)
                    }
                },
                onLongPress = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (state.playerDebugOverlay) {
                        chrome.onShowDebugSheet()
                    } else {
                        chrome.onCarouselModeChange(true)
                    }
                },
            )
        }
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (track.artUrl.isNotBlank()) {
            AsyncImage(
                model = track.artUrl,
                contentDescription = track.albumTitle.ifEmpty {
                    stringResource(R.string.player_cd_album_art)
                },
                contentScale = ContentScale.Crop,
                modifier = albumArtGestureModifier,
            )
        } else {
            TrackArtPlaceholder(modifier = albumArtGestureModifier, iconSize = 64.dp)
        }
        if (showPlayPauseFeedback) {
            Icon(
                painter = painterResource(
                    if (!feedbackIsPlaying) R.drawable.ic_play_arrow else R.drawable.ic_pause,
                ),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                modifier = Modifier
                    .size(72.dp)
                    .zIndex(2f)
                    .graphicsLayer {
                        scaleX = motion.feedbackScale.value
                        scaleY = motion.feedbackScale.value
                        alpha = motion.feedbackScale.value
                    },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FullPlayerInlineVolumeSlider(
    volumeLevel: Float,
    onVolumeChange: (Float) -> Unit,
) {
    val hapticFeedback = LocalHapticFeedback.current
    val onVolumeChangeState = rememberUpdatedState(onVolumeChange)
    val inlineVolumeState = androidx.compose.material3.rememberSliderState(value = volumeLevel)
    LaunchedEffect(volumeLevel) {
        if (!inlineVolumeState.isDragging) inlineVolumeState.value = volumeLevel
    }
    LaunchedEffect(Unit) {
        snapshotFlow { inlineVolumeState.value }.collect { onVolumeChangeState.value(it) }
    }
    LaunchedEffect(inlineVolumeState) {
        snapshotFlow { (inlineVolumeState.value * VOLUME_TICK_SEGMENTS).toInt() }
            .distinctUntilChanged()
            .collect { if (inlineVolumeState.isDragging) hapticFeedback.tick() }
    }
    Row {
        Spacer(modifier = Modifier.width(12.dp))
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.height(240.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_volume_up),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            androidx.compose.material3.VerticalSlider(
                state = inlineVolumeState,
                modifier = Modifier.weight(1f),
                topToBottom = false,
                thumb = { _ ->
                    androidx.compose.material3.SliderDefaults.Thumb(
                        interactionSource = remember { MutableInteractionSource() },
                        isVertical = true,
                        thumbSize = androidx.compose.ui.unit.DpSize(44.dp, 4.dp),
                    )
                },
                track = { sliderState ->
                    androidx.compose.material3.SliderDefaults.Track(
                        sliderState = sliderState,
                        modifier = Modifier.width(40.dp),
                        trackCornerSize = 12.dp,
                    )
                },
            )
            Spacer(modifier = Modifier.height(4.dp))
            Icon(
                painter = painterResource(R.drawable.ic_volume_down),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
