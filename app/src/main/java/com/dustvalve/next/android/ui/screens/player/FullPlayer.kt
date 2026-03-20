package com.dustvalve.next.android.ui.screens.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TwoRowsTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.TonalToggleButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.toMutableStateList
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.drawscope.Stroke

import androidx.compose.ui.Alignment
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.Modifier
import android.graphics.Matrix
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.toPath
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.dustvalve.next.android.domain.model.Playlist
import com.dustvalve.next.android.domain.model.Track
import com.dustvalve.next.android.R
import com.dustvalve.next.android.domain.model.RepeatMode
import androidx.compose.foundation.background
import com.dustvalve.next.android.ui.components.FastScrollbar
import com.dustvalve.next.android.ui.components.TrackArtPlaceholder
import com.dustvalve.next.android.ui.components.PlaylistEditSheet
import com.dustvalve.next.android.ui.components.PlaylistListItem
import com.dustvalve.next.android.ui.theme.AppShapes
import com.dustvalve.next.android.ui.theme.segmentedItemShape
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)
@Composable
fun FullPlayer(
    playerViewModel: PlayerViewModel,
    onCollapse: () -> Unit,
    onArtistClick: (String) -> Unit = {},
) {
    val state by playerViewModel.uiState.collectAsStateWithLifecycle()
    val track = state.currentTrack
    val snackbarHostState = remember { SnackbarHostState() }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    // Snackbar handling
    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let { message ->
            try {
                snackbarHostState.showSnackbar(message)
            } finally {
                playerViewModel.clearSnackbar()
            }
        }
    }

    // Double-tap heart animation: morph from Square to Heart
    val heartProgress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val squarePolygon = remember { MaterialShapes.Square }
    val heartPolygon = remember { MaterialShapes.Heart }
    val heartMorph = remember(squarePolygon, heartPolygon) { Morph(squarePolygon, heartPolygon) }
    val heartInSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val heartOutSpec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()

    // Play/pause tap feedback on album art
    var showPlayPauseFeedback by remember { mutableStateOf(false) }
    var feedbackIsPlaying by remember { mutableStateOf(false) }
    val feedbackScale = remember { Animatable(0f) }
    val feedbackSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()

    // Dialog and sheet state
    var showDeleteDownloadDialog by remember { mutableStateOf(false) }
    var showPlaylistSheet by remember { mutableStateOf(false) }
    var showCreatePlaylistSheet by remember { mutableStateOf(false) }
    var showDebugSheet by remember { mutableStateOf(false) }
    var showVolumeSheet by remember { mutableStateOf(false) }
    var showQueueSheet by remember { mutableStateOf(false) }
    var isCarouselMode by remember { mutableStateOf(false) }
    val albumSwipeOffsetX = remember { Animatable(0f) }
    var upNextContextTrack by remember { mutableStateOf<Pair<Track, Int>?>(null) }
    var showUpNextPlaylistSheet by remember { mutableStateOf(false) }
    var showUpNextCreatePlaylistSheet by remember { mutableStateOf(false) }
    val hapticFeedback = LocalHapticFeedback.current

    // Full-screen volume control sheet
    if (showVolumeSheet) {
        val sheetVolumeState = androidx.compose.material3.rememberSliderState(
            value = state.volumeLevel,
        )
        LaunchedEffect(state.volumeLevel) { sheetVolumeState.value = state.volumeLevel }
        LaunchedEffect(Unit) {
            snapshotFlow { sheetVolumeState.value }
                .collect { playerViewModel.setVolume(it) }
        }

        ModalBottomSheet(
            onDismissRequest = { showVolumeSheet = false },
            sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 32.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Volume",
                    style = MaterialTheme.typography.headlineMedium,
                )
                Spacer(modifier = Modifier.height(32.dp))
                Icon(
                    painter = painterResource(R.drawable.ic_volume_up),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(16.dp))
                androidx.compose.material3.VerticalSlider(
                    state = sheetVolumeState,
                    modifier = Modifier.height(360.dp),
                    reverseDirection = true,
                    thumb = { sliderState ->
                        androidx.compose.material3.SliderDefaults.Thumb(
                            interactionSource = remember { MutableInteractionSource() },
                            sliderState = sliderState,
                            thumbSize = androidx.compose.ui.unit.DpSize(108.dp, 4.dp),
                        )
                    },
                    track = { sliderState ->
                        androidx.compose.material3.SliderDefaults.Track(
                            sliderState = sliderState,
                            modifier = Modifier.width(96.dp),
                            trackCornerSize = 28.dp,
                        )
                    },
                )
                Spacer(modifier = Modifier.height(16.dp))
                Icon(
                    painter = painterResource(R.drawable.ic_volume_off),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(24.dp))
                androidx.compose.material3.HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Output",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Column(modifier = Modifier.selectableGroup()) {
                    val totalDeviceCount = 1 + state.audioOutputDevices.size
                    // System default option
                    val autoSelected = state.activeAudioDevice == null
                    val autoColor by animateColorAsState(
                        targetValue = if (autoSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                      else Color.Transparent,
                        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
                        label = "autoDeviceColor",
                    )
                    Surface(
                        shape = segmentedItemShape(0, totalDeviceCount),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier
                            .selectable(
                                selected = autoSelected,
                                onClick = { playerViewModel.setAudioOutputDevice(null) },
                                role = Role.RadioButton,
                            ),
                    ) {
                        ListItem(
                            headlineContent = { Text("Automatic") },
                            leadingContent = { RadioButton(selected = autoSelected, onClick = null) },
                            trailingContent = {
                                Icon(
                                    painter = painterResource(R.drawable.ic_speaker),
                                    contentDescription = null,
                                )
                            },
                            colors = ListItemDefaults.colors(containerColor = autoColor),
                        )
                    }
                    state.audioOutputDevices.forEachIndexed { index, device ->
                        val deviceIndex = index + 1
                        val isActive = state.activeAudioDevice?.id == device.id
                        val bgColor by animateColorAsState(
                            targetValue = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                          else Color.Transparent,
                            animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
                            label = "deviceColor",
                        )
                        Surface(
                            shape = segmentedItemShape(deviceIndex, totalDeviceCount),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier
                                .padding(top = 1.dp)
                                .selectable(
                                    selected = isActive,
                                    onClick = { playerViewModel.setAudioOutputDevice(device) },
                                    role = Role.RadioButton,
                                ),
                        ) {
                            ListItem(
                                headlineContent = { Text(audioDeviceDisplayName(device)) },
                                leadingContent = { RadioButton(selected = isActive, onClick = null) },
                                trailingContent = {
                                    Icon(
                                        painter = painterResource(audioDeviceIcon(device)),
                                        contentDescription = null,
                                    )
                                },
                                colors = ListItemDefaults.colors(containerColor = bgColor),
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxSize(),
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            floatingActionButton = {
                val upNextCount = if (state.currentQueueIndex >= 0)
                    (state.queue.size - state.currentQueueIndex - 1).coerceAtLeast(0)
                else 0
                if (state.currentTrack != null && upNextCount > 0) {
                    ExtendedFloatingActionButton(
                        onClick = { showQueueSheet = true },
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.ic_queue_music),
                                contentDescription = null,
                            )
                        },
                        text = { Text("Queue ($upNextCount)") },
                    )
                }
            },
            topBar = {
                TwoRowsTopAppBar(
                    title = { expanded ->
                        Text(
                            text = if (expanded) "Now Playing" else (track?.title ?: "Now Playing"),
                            style = MaterialTheme.typography.titleMediumEmphasized,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    subtitle = { expanded ->
                        if (expanded && track != null) {
                            Text(
                                text = track.title,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onCollapse) {
                            Icon(
                                painter = painterResource(R.drawable.ic_keyboard_arrow_down),
                                contentDescription = "Collapse",
                            )
                        }
                    },
                    actions = {
                        if (state.showVolumeButton) {
                            IconButton(onClick = { showVolumeSheet = true }) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_volume_up),
                                    contentDescription = "Volume",
                                )
                            }
                        }
                    },
                    titleHorizontalAlignment = Alignment.CenterHorizontally,
                    scrollBehavior = scrollBehavior,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                )
            },
        ) { paddingValues ->
            if (track == null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No track playing",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                return@Scaffold
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {

                Spacer(modifier = Modifier.height(48.dp))

                // Album art with single-tap play/pause and double-tap heart
                val albumArtShape = if (heartProgress.value > 0f) {
                    PlaybackMorphShape(heartMorph, heartProgress.value)
                } else {
                    PlaybackMorphShape(heartMorph, 0f)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                // BackHandler to close carousel on back press
                BackHandler(enabled = isCarouselMode) {
                    isCarouselMode = false
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    val currentIndex = state.currentQueueIndex

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
                            // Carousel mode: M3E HorizontalMultiBrowseCarousel (starts with current track)
                            val carouselTracks = if (currentIndex >= 0) {
                                state.queue.subList(currentIndex, minOf(currentIndex + 26, state.queue.size))
                            } else {
                                emptyList()
                            }

                            if (carouselTracks.isNotEmpty()) {
                                val carouselState = rememberCarouselState { carouselTracks.size }
                                HorizontalMultiBrowseCarousel(
                                    state = carouselState,
                                    preferredItemWidth = 200.dp,
                                    modifier = Modifier.fillMaxSize(),
                                    itemSpacing = 8.dp,
                                ) { page ->
                                    val carouselTrack = carouselTracks[page]
                                    val carouselQueueIndex = currentIndex + page

                                    Box(
                                        modifier = Modifier
                                            .maskClip(AppShapes.SearchResultTrack)
                                            .aspectRatio(1f)
                                            .clickable {
                                                playerViewModel.skipToQueueIndex(carouselQueueIndex)
                                                isCarouselMode = false
                                            },
                                    ) {
                                        if (carouselTrack.artUrl.isNotBlank()) {
                                            AsyncImage(
                                                model = carouselTrack.artUrl,
                                                contentDescription = carouselTrack.title,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize(),
                                            )
                                        } else {
                                            TrackArtPlaceholder(
                                                modifier = Modifier.fillMaxSize(),
                                                iconSize = 48.dp,
                                            )
                                        }
                                    }
                                }
                            } else {
                                // No upcoming tracks — close carousel
                                LaunchedEffect(Unit) { isCarouselMode = false }
                            }
                        } else {
                            // Normal mode: stacked covers + main album art
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                // Stacked background covers (up to 3 behind current)
                                val stackedTracks = if (currentIndex >= 0 && currentIndex < state.queue.lastIndex) {
                                    state.queue.subList(
                                        currentIndex + 1,
                                        minOf(currentIndex + 4, state.queue.size),
                                    )
                                } else {
                                    emptyList()
                                }

                                // Render stacked covers back-to-front
                                stackedTracks.reversed().forEachIndexed { reverseIndex, stackTrack ->
                                    val stackIndex = stackedTracks.size - 1 - reverseIndex
                                    val offsetX = ((stackIndex + 1) * 20).dp
                                    val offsetY = (-(stackIndex + 1) * 24).dp
                                    val stackScale = 1f - ((stackIndex + 1) * 0.06f)
                                    val actualQueueIndex = currentIndex + 1 + stackIndex

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
                                            .clip(PlaybackMorphShape(heartMorph, 0f))
                                            .clickable {
                                                playerViewModel.skipToQueueIndex(actualQueueIndex)
                                            },
                                    ) {
                                        if (stackTrack.artUrl.isNotBlank()) {
                                            AsyncImage(
                                                model = stackTrack.artUrl,
                                                contentDescription = stackTrack.title,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize(),
                                            )
                                        } else {
                                            TrackArtPlaceholder(
                                                modifier = Modifier.fillMaxSize(),
                                                iconSize = 48.dp,
                                            )
                                        }
                                    }
                                }

                                // Main album art (on top)
                                val swipeSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
                                val albumArtGestureModifier = Modifier
                                    .fillMaxSize()
                                    .zIndex(1f)
                                    .graphicsLayer {
                                        translationX = albumSwipeOffsetX.value
                                    }
                                    .clip(albumArtShape)
                                    .pointerInput(Unit) {
                                        detectHorizontalDragGestures(
                                            onDragEnd = {
                                                val threshold = size.width * 0.3f
                                                if (albumSwipeOffsetX.value < -threshold) {
                                                    scope.launch {
                                                        albumSwipeOffsetX.animateTo(-size.width.toFloat(), swipeSpec)
                                                        playerViewModel.onNext()
                                                        albumSwipeOffsetX.snapTo(0f)
                                                    }
                                                } else if (albumSwipeOffsetX.value > threshold) {
                                                    scope.launch {
                                                        albumSwipeOffsetX.animateTo(size.width.toFloat(), swipeSpec)
                                                        playerViewModel.onPrevious()
                                                        albumSwipeOffsetX.snapTo(0f)
                                                    }
                                                } else {
                                                    scope.launch { albumSwipeOffsetX.animateTo(0f, swipeSpec) }
                                                }
                                            },
                                            onDragCancel = {
                                                scope.launch { albumSwipeOffsetX.animateTo(0f, swipeSpec) }
                                            },
                                            onHorizontalDrag = { change, dragAmount ->
                                                change.consume()
                                                scope.launch { albumSwipeOffsetX.snapTo(albumSwipeOffsetX.value + dragAmount) }
                                            },
                                        )
                                    }
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onTap = {
                                                feedbackIsPlaying = state.isPlaying
                                                playerViewModel.onPlayPause()
                                                scope.launch {
                                                    feedbackScale.snapTo(0f)
                                                    showPlayPauseFeedback = true
                                                    feedbackScale.animateTo(1f, feedbackSpec)
                                                    delay(400L)
                                                    feedbackScale.animateTo(0f, feedbackSpec)
                                                    showPlayPauseFeedback = false
                                                }
                                            },
                                            onDoubleTap = {
                                                playerViewModel.onToggleFavorite()
                                                scope.launch {
                                                    heartProgress.animateTo(
                                                        targetValue = 1f,
                                                        animationSpec = heartInSpec,
                                                    )
                                                    delay(1000L)
                                                    heartProgress.animateTo(
                                                        targetValue = 0f,
                                                        animationSpec = heartOutSpec,
                                                    )
                                                }
                                            },
                                            onLongPress = {
                                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                                if (state.albumCoverLongPressCarousel) {
                                                    isCarouselMode = true
                                                } else {
                                                    showDebugSheet = true
                                                }
                                            },
                                        )
                                    }
                                if (track.artUrl.isNotBlank()) {
                                    AsyncImage(
                                        model = track.artUrl,
                                        contentDescription = track.albumTitle.ifEmpty { "Album art" },
                                        contentScale = ContentScale.Crop,
                                        modifier = albumArtGestureModifier,
                                    )
                                } else {
                                    TrackArtPlaceholder(
                                        modifier = albumArtGestureModifier,
                                        iconSize = 64.dp,
                                    )
                                }

                                // Play/pause tap feedback overlay
                                if (showPlayPauseFeedback) {
                                    Icon(
                                        painter = painterResource(if (!feedbackIsPlaying) R.drawable.ic_play_arrow else R.drawable.ic_pause),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                                        modifier = Modifier
                                            .size(72.dp)
                                            .zIndex(2f)
                                            .graphicsLayer {
                                                scaleX = feedbackScale.value
                                                scaleY = feedbackScale.value
                                                alpha = feedbackScale.value
                                            },
                                    )
                                }
                            }
                        }
                    }
                } // end album art Box

                // Inline volume slider (to the right of album art)
                if (state.showInlineVolumeSlider) {
                    val inlineVolumeState = androidx.compose.material3.rememberSliderState(
                        value = state.volumeLevel,
                    )
                    LaunchedEffect(state.volumeLevel) { inlineVolumeState.value = state.volumeLevel }
                    LaunchedEffect(Unit) {
                        snapshotFlow { inlineVolumeState.value }
                            .collect { playerViewModel.setVolume(it) }
                    }

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
                            reverseDirection = true,
                            thumb = { sliderState ->
                                androidx.compose.material3.SliderDefaults.Thumb(
                                    interactionSource = remember { MutableInteractionSource() },
                                    sliderState = sliderState,
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
                } // end Row

                // Track title, artist, favorite, and download
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.headlineSmallEmphasized,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        // Artist name as TextButton
                        TextButton(
                            onClick = { onArtistClick(track.artistUrl) },
                            enabled = track.artistUrl.isNotEmpty(),
                            modifier = Modifier.weight(1f, fill = false),
                        ) {
                            Text(
                                text = track.artist,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        // Favorite toggle button
                        TonalToggleButton(
                            checked = track.isFavorite,
                            onCheckedChange = { playerViewModel.onToggleFavorite() },
                            colors = ToggleButtonDefaults.tonalToggleButtonColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                checkedContainerColor = MaterialTheme.colorScheme.errorContainer,
                                checkedContentColor = MaterialTheme.colorScheme.error,
                            ),
                        ) {
                            Icon(
                                painter = painterResource(if (track.isFavorite) R.drawable.ic_favorite else R.drawable.ic_favorite_border),
                                contentDescription = if (track.isFavorite) "Remove from favorites" else "Add to favorites",
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        // Download toggle button
                        val isTrackDownloaded = track.id in state.downloadedTrackIds
                        val isDownloading = state.downloadingTrackId == track.id
                        val isLocalTrack = track.isLocal
                        TonalToggleButton(
                            checked = isTrackDownloaded || isLocalTrack,
                            onCheckedChange = {
                                if (isLocalTrack) return@TonalToggleButton
                                if (isTrackDownloaded) {
                                    showDeleteDownloadDialog = true
                                } else if (!isDownloading) {
                                    playerViewModel.onDownloadTrack()
                                }
                            },
                            enabled = !isDownloading && !isLocalTrack,
                            colors = ToggleButtonDefaults.tonalToggleButtonColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                checkedContentColor = MaterialTheme.colorScheme.primary,
                            ),
                        ) {
                            if (isDownloading) {
                                CircularWavyProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                )
                            } else {
                                Icon(
                                    painter = painterResource(if (isTrackDownloaded || isLocalTrack) R.drawable.ic_download_done
                                        else R.drawable.ic_download),
                                    contentDescription = when {
                                        isLocalTrack -> "Local file"
                                        isTrackDownloaded -> "Delete download"
                                        else -> "Download track"
                                    },
                                )
                            }
                        }
                    }
                }

                // Wavy seek bar — keyed to track so state resets on track change
                val trackId = track.id
                var isSeeking by remember(trackId) { mutableStateOf(false) }
                var seekPosition by remember(trackId) { mutableFloatStateOf(0f) }

                // Clear isSeeking once the player position catches up to the seek target
                SideEffect {
                    if (isSeeking && state.duration > 0L) {
                        val playerFraction = state.currentPosition.toFloat() / state.duration.toFloat()
                        if ((playerFraction - seekPosition).let { it * it } < 0.001f) {
                            isSeeking = false
                        }
                    }
                }

                val sliderPosition = if (isSeeking) {
                    seekPosition
                } else {
                    if (state.duration > 0L) {
                        state.currentPosition.toFloat() / state.duration.toFloat()
                    } else {
                        0f
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .pointerInput(trackId) {
                            detectTapGestures { offset ->
                                val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                                seekPosition = fraction
                                val targetMs = (fraction * state.duration).toLong()
                                playerViewModel.onSeek(targetMs)
                                // Keep showing seekPosition until player catches up
                                isSeeking = true
                            }
                        }
                        .pointerInput(trackId) {
                            detectDragGestures(
                                onDragEnd = {
                                    val targetMs = (seekPosition * state.duration).toLong()
                                    playerViewModel.onSeek(targetMs)
                                    // Keep showing seekPosition until player catches up
                                },
                                onDragCancel = {
                                    isSeeking = false
                                },
                            ) { change, _ ->
                                change.consume()
                                val fraction = (change.position.x / size.width).coerceIn(0f, 1f)
                                isSeeking = true
                                seekPosition = fraction
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (state.wavyProgressBar) {
                        LinearWavyProgressIndicator(
                            progress = { sliderPosition.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(24.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            stroke = Stroke(width = 24.dp.value),
                            trackStroke = Stroke(width = 24.dp.value),
                        )
                    } else {
                        LinearProgressIndicator(
                            progress = { sliderPosition.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(24.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                    }
                }

                // Time labels — reflect seek position during drag
                val displayPosition = if (isSeeking) {
                    (seekPosition * state.duration).toLong()
                } else {
                    state.currentPosition
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = formatTime(displayPosition),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val remaining = (state.duration - displayPosition).coerceAtLeast(0L)
                    Text(
                        text = "-${formatTime(remaining)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Top control row: Previous / Play / Next
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    // Previous button
                    OutlinedButton(
                        onClick = { playerViewModel.onPrevious() },
                        modifier = Modifier.size(56.dp),
                        shapes = ButtonDefaults.shapes(),
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_skip_previous),
                            contentDescription = "Previous",
                            modifier = Modifier.size(28.dp),
                        )
                    }

                    Spacer(modifier = Modifier.width(24.dp))

                    // Play/Pause toggle button
                    ToggleButton(
                        checked = state.isPlaying,
                        onCheckedChange = { playerViewModel.onPlayPause() },
                        modifier = Modifier.size(80.dp),
                        colors = ToggleButtonDefaults.toggleButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            checkedContainerColor = MaterialTheme.colorScheme.primary,
                            checkedContentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        Icon(
                            painter = painterResource(if (state.isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow),
                            contentDescription = if (state.isPlaying) "Pause" else "Play",
                            modifier = Modifier.size(36.dp),
                        )
                    }

                    Spacer(modifier = Modifier.width(24.dp))

                    // Next button
                    OutlinedButton(
                        onClick = { playerViewModel.onNext() },
                        modifier = Modifier.size(56.dp),
                        shapes = ButtonDefaults.shapes(),
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_skip_next),
                            contentDescription = "Next",
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }

                // Bottom control row: Shuffle / Add to Playlist / Repeat
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    TonalToggleButton(
                        checked = state.shuffleEnabled,
                        onCheckedChange = { playerViewModel.onToggleShuffle() },
                        colors = ToggleButtonDefaults.tonalToggleButtonColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            checkedContainerColor = MaterialTheme.colorScheme.tertiary,
                            checkedContentColor = MaterialTheme.colorScheme.onTertiary,
                        ),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_shuffle),
                            contentDescription = "Shuffle",
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    TonalToggleButton(
                        checked = track.id in state.userPlaylistTrackIds,
                        onCheckedChange = { showPlaylistSheet = true },
                        colors = ToggleButtonDefaults.tonalToggleButtonColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            checkedContainerColor = MaterialTheme.colorScheme.tertiary,
                            checkedContentColor = MaterialTheme.colorScheme.onTertiary,
                        ),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_playlist_add),
                            contentDescription = "Add to playlist",
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    TonalToggleButton(
                        checked = state.repeatMode != RepeatMode.OFF,
                        onCheckedChange = { playerViewModel.onToggleRepeat() },
                        colors = ToggleButtonDefaults.tonalToggleButtonColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            checkedContainerColor = MaterialTheme.colorScheme.tertiary,
                            checkedContentColor = MaterialTheme.colorScheme.onTertiary,
                        ),
                    ) {
                        Icon(
                            painter = painterResource(when (state.repeatMode) {
                                RepeatMode.ONE -> R.drawable.ic_repeat_one
                                else -> R.drawable.ic_repeat
                            }),
                            contentDescription = "Repeat",
                        )
                    }
                }

                // Bottom spacer for FAB clearance
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }

    // Delete download confirmation dialog
    if (showDeleteDownloadDialog && track != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDownloadDialog = false },
            title = { Text("Delete download") },
            text = { Text("Remove downloaded file for '${track.title}'?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        playerViewModel.onDeleteTrackDownload()
                        showDeleteDownloadDialog = false
                    },
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDownloadDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    // Playback debug info sheet
    if (showDebugSheet && track != null) {
        ModalBottomSheet(
            onDismissRequest = { showDebugSheet = false },
        ) {
            Text(
                text = "Playback Info",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                val isLocalTrackDebug = track.isLocal
                val isLocal = state.currentSourcePath != null
                val isTrackDownloaded = track.id in state.downloadedTrackIds
                val isDownloading = state.downloadingTrackId == track.id

                val downloadStatus = when {
                    isLocalTrackDebug -> "Local file"
                    isDownloading -> "Downloading..."
                    isTrackDownloaded -> "Downloaded"
                    isLocal -> "Cached"
                    else -> "Not downloaded"
                }

                val formatDisplay = if (isLocalTrackDebug) "Local" else (state.currentPlaybackFormat?.displayName ?: "Unknown")
                val sourceDisplay = if (isLocalTrackDebug || isLocal) "Local file" else "Streaming"

                Surface(
                    shape = segmentedItemShape(0, 6),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    ListItem(
                        headlineContent = { Text(formatDisplay) },
                        supportingContent = { Text("Audio format") },
                        leadingContent = {
                            Icon(painterResource(R.drawable.ic_audio_file), contentDescription = null)
                        },
                    )
                }
                Surface(
                    shape = segmentedItemShape(1, 6),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    ListItem(
                        headlineContent = { Text(sourceDisplay) },
                        supportingContent = { Text("Source") },
                        leadingContent = {
                            Icon(painterResource(R.drawable.ic_cloud), contentDescription = null)
                        },
                    )
                }
                Surface(
                    shape = segmentedItemShape(2, 6),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    ListItem(
                        headlineContent = { Text(downloadStatus) },
                        supportingContent = { Text("Download status") },
                        leadingContent = {
                            Icon(painterResource(R.drawable.ic_download), contentDescription = null)
                        },
                    )
                }
                Surface(
                    shape = segmentedItemShape(3, 6),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    ListItem(
                        headlineContent = {
                            Text(
                                text = state.currentSourcePath?.let {
                                    it.substringAfterLast("/downloads/")
                                } ?: track.streamUrl?.take(60) ?: "None",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        supportingContent = { Text("File path") },
                        leadingContent = {
                            Icon(painterResource(R.drawable.ic_storage), contentDescription = null)
                        },
                    )
                }
                Surface(
                    shape = segmentedItemShape(4, 6),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    ListItem(
                        headlineContent = {
                            Text(
                                text = track.id,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        supportingContent = { Text("Track ID") },
                        leadingContent = {
                            Icon(painterResource(R.drawable.ic_info), contentDescription = null)
                        },
                    )
                }
                Surface(
                    shape = segmentedItemShape(5, 6),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    ListItem(
                        headlineContent = {
                            Text(
                                text = track.albumId,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        supportingContent = { Text("Album ID") },
                        leadingContent = {
                            Icon(painterResource(R.drawable.ic_info), contentDescription = null)
                        },
                    )
                }
            }
            Spacer(modifier = Modifier.height(28.dp))
        }
    }

    // Add to playlist bottom sheet
    if (showPlaylistSheet) {
        ModalBottomSheet(
            onDismissRequest = { showPlaylistSheet = false },
        ) {
            val userPlaylists = state.playlists.filter { !it.isSystem }
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
                                            showPlaylistSheet = false
                                            playerViewModel.addToPlaylist(playlist.id)
                                        },
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(72.dp))
                }
                FloatingActionButton(
                    onClick = { showCreatePlaylistSheet = true },
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

    // Create playlist sheet (from add-to-playlist context)
    if (showCreatePlaylistSheet) {
        PlaylistEditSheet(
            onDismiss = { showCreatePlaylistSheet = false },
            onConfirm = { name, shapeKey, iconUrl ->
                showCreatePlaylistSheet = false
                showPlaylistSheet = false
                playerViewModel.createPlaylistAndAddTrack(name, shapeKey, iconUrl)
            },
            isCreate = true,
        )
    }

    // Up Next context menu bottom sheet
    upNextContextTrack?.let { (contextTrack, contextQueueIndex) ->
        ModalBottomSheet(
            onDismissRequest = { upNextContextTrack = null },
        ) {
            Text(
                text = contextTrack.title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            ListItem(
                headlineContent = {
                    Text(if (contextTrack.isFavorite) "Remove from favorites" else "Add to favorites")
                },
                leadingContent = {
                    Icon(
                        painter = painterResource(
                            if (contextTrack.isFavorite) R.drawable.ic_favorite
                            else R.drawable.ic_favorite_border,
                        ),
                        contentDescription = null,
                    )
                },
                modifier = Modifier.clickable {
                    playerViewModel.toggleFavoriteById(contextTrack.id)
                    upNextContextTrack = null
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
                    showUpNextPlaylistSheet = true
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )

            ListItem(
                headlineContent = {
                    Text(
                        text = "Remove from queue",
                        color = MaterialTheme.colorScheme.error,
                    )
                },
                leadingContent = {
                    Icon(
                        painter = painterResource(R.drawable.ic_close),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                },
                modifier = Modifier.clickable {
                    playerViewModel.removeFromQueue(contextQueueIndex)
                    upNextContextTrack = null
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )

            Spacer(modifier = Modifier.height(28.dp))
        }
    }

    // Up Next — add to playlist sheet
    if (showUpNextPlaylistSheet) {
        ModalBottomSheet(
            onDismissRequest = { showUpNextPlaylistSheet = false },
        ) {
            val userPlaylists = state.playlists.filter { !it.isSystem }
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
                                            showUpNextPlaylistSheet = false
                                            upNextContextTrack?.let { (ctxTrack, _) ->
                                                playerViewModel.addTrackToPlaylist(playlist.id, ctxTrack.id)
                                            }
                                            upNextContextTrack = null
                                        },
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(72.dp))
                }
                FloatingActionButton(
                    onClick = { showUpNextCreatePlaylistSheet = true },
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

    // Up Next — create playlist sheet
    if (showUpNextCreatePlaylistSheet) {
        PlaylistEditSheet(
            onDismiss = { showUpNextCreatePlaylistSheet = false },
            onConfirm = { name, shapeKey, iconUrl ->
                showUpNextCreatePlaylistSheet = false
                showUpNextPlaylistSheet = false
                upNextContextTrack?.let { (ctxTrack, _) ->
                    playerViewModel.createPlaylistAndAddArbitraryTrack(name, shapeKey, iconUrl, ctxTrack.id)
                }
                upNextContextTrack = null
            },
            isCreate = true,
        )
    }

    // Queue bottom sheet with pagination, swipe-to-delete, and drag reorder
    if (showQueueSheet) {
        val currentIndex = state.currentQueueIndex
        val allUpNextTracks = if (currentIndex >= 0 && currentIndex < state.queue.lastIndex) {
            state.queue.subList(currentIndex + 1, state.queue.size)
        } else {
            emptyList()
        }

        var displayCount by remember(currentIndex) { mutableIntStateOf(25) }
        val displayedTracks = allUpNextTracks.take(displayCount)
        val hasMore = displayCount < allUpNextTracks.size
        val queueListState = rememberLazyListState()

        // Drag-and-drop state
        var queueDraggedIndex by remember { mutableIntStateOf(-1) }
        var queueDragStartIndex by remember { mutableIntStateOf(-1) }
        var queueDragOffset by remember { mutableFloatStateOf(0f) }
        val queueItemHeights = remember { mutableMapOf<Int, Float>() }
        var queueDroppingItemKey by remember { mutableStateOf<String?>(null) }
        val queueDropAnimOffset = remember { Animatable(0f) }
        val queueDropSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
        val queueReorderableTracks = remember { displayedTracks.toMutableStateList() }
        LaunchedEffect(displayedTracks) {
            if (queueDraggedIndex == -1) {
                queueReorderableTracks.clear()
                queueReorderableTracks.addAll(displayedTracks)
            }
        }

        // Pagination: load more when near bottom
        val currentHasMore by rememberUpdatedState(hasMore)
        val currentDisplayedCount by rememberUpdatedState(displayedTracks.size)
        LaunchedEffect(queueListState) {
            snapshotFlow {
                val last = queueListState.layoutInfo.visibleItemsInfo.lastOrNull()
                val totalCount = queueListState.layoutInfo.totalItemsCount
                last != null && totalCount > 0 && last.index >= totalCount - 3
            }.collect { nearEnd ->
                if (nearEnd && currentHasMore && currentDisplayedCount > 0) {
                    displayCount += 25
                }
            }
        }

        ModalBottomSheet(
            onDismissRequest = { showQueueSheet = false },
            sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Text(
                text = "Up next (${allUpNextTracks.size})",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )

            Box(modifier = Modifier.fillMaxWidth()) {
            LazyColumn(
                state = queueListState,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            ) {
                items(
                    count = queueReorderableTracks.size,
                    key = { queueReorderableTracks[it].id },
                ) { upNextIndex ->
                    val queueTrack = queueReorderableTracks[upNextIndex]
                    val queueIndex = currentIndex + 1 + upNextIndex
                    val isDownloaded = queueTrack.id in state.downloadedTrackIds || queueTrack.isLocal
                    val isDragging = queueDraggedIndex == upNextIndex

                    val interactionSource = remember { MutableInteractionSource() }
                    val isPressed by interactionSource.collectIsPressedAsState()
                    val pressScale by animateFloatAsState(
                        targetValue = if (isPressed) 0.97f else 1f,
                        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                        label = "queuePressScale",
                    )
                    val elevation by animateDpAsState(
                        targetValue = if (isDragging) 2.dp else 0.dp,
                        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                        label = "queueDragElevation",
                    )
                    val containerColor by animateColorAsState(
                        targetValue = if (isDragging) {
                            MaterialTheme.colorScheme.surfaceContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        },
                        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                        label = "queueDragColor",
                    )
                    val itemShape = if (isDragging) {
                        MaterialTheme.shapes.large
                    } else {
                        segmentedItemShape(upNextIndex, queueReorderableTracks.size)
                    }

                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { value ->
                            if (value == SwipeToDismissBoxValue.EndToStart) {
                                playerViewModel.removeFromQueue(queueIndex)
                                true
                            } else false
                        },
                    )
                    SwipeToDismissBox(
                        state = dismissState,
                        gesturesEnabled = queueDraggedIndex == -1,
                        modifier = Modifier
                            .padding(
                                top = if (upNextIndex == 0) 0.dp else 1.dp,
                                bottom = if (upNextIndex == queueReorderableTracks.lastIndex) 0.dp else 1.dp,
                            )
                            .animateItem(
                                fadeInSpec = null,
                                fadeOutSpec = null,
                                placementSpec = if (isDragging || queueTrack.id == queueDroppingItemKey) null
                                    else MaterialTheme.motionScheme.defaultSpatialSpec(),
                            )
                            .zIndex(if (isDragging || queueTrack.id == queueDroppingItemKey) 1f else 0f),
                        backgroundContent = {
                            if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(itemShape)
                                        .background(MaterialTheme.colorScheme.errorContainer)
                                        .padding(horizontal = 20.dp),
                                    contentAlignment = Alignment.CenterEnd,
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_delete),
                                        contentDescription = "Delete",
                                        tint = MaterialTheme.colorScheme.onErrorContainer,
                                    )
                                }
                            }
                        },
                        enableDismissFromStartToEnd = false,
                    ) {
                        Surface(
                            shape = itemShape,
                            color = containerColor,
                            modifier = Modifier
                                .graphicsLayer {
                                    scaleX = pressScale
                                    scaleY = pressScale
                                    if (isDragging) {
                                        translationY = queueDragOffset
                                        shadowElevation = elevation.toPx()
                                    } else if (queueTrack.id == queueDroppingItemKey) {
                                        translationY = queueDropAnimOffset.value
                                    }
                                }
                                .onGloballyPositioned { coords ->
                                    queueItemHeights[upNextIndex] = coords.size.height.toFloat()
                                }
                                .clickable(
                                    interactionSource = interactionSource,
                                    indication = LocalIndication.current,
                                ) {
                                    playerViewModel.skipToQueueIndex(queueIndex)
                                },
                            shadowElevation = if (!isDragging) elevation else 0.dp,
                        ) {
                            ListItem(
                                headlineContent = {
                                    Text(
                                        text = queueTrack.title,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                supportingContent = {
                                    Text(
                                        text = queueTrack.artist,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                leadingContent = {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(AppShapes.SearchResultTrack),
                                    ) {
                                        if (queueTrack.artUrl.isNotBlank()) {
                                            AsyncImage(
                                                model = queueTrack.artUrl,
                                                contentDescription = queueTrack.albumTitle,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop,
                                            )
                                        } else {
                                            TrackArtPlaceholder(modifier = Modifier.fillMaxSize())
                                        }
                                    }
                                },
                                trailingContent = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (isDownloaded) {
                                            Icon(
                                                painter = painterResource(R.drawable.ic_download_done),
                                                contentDescription = "Downloaded",
                                                modifier = Modifier.size(18.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
                                                .pointerInput(Unit) {
                                                    detectDragGesturesAfterLongPress(
                                                        onDragStart = {
                                                            queueDroppingItemKey = null
                                                            scope.launch { queueDropAnimOffset.snapTo(0f) }
                                                            queueDraggedIndex = upNextIndex
                                                            queueDragStartIndex = upNextIndex
                                                            queueDragOffset = 0f
                                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        },
                                                        onDrag = { change, dragAmount ->
                                                            change.consume()
                                                            queueDragOffset += dragAmount.y
                                                            val ci = queueDraggedIndex
                                                            if (ci < 0) return@detectDragGesturesAfterLongPress
                                                            val h = queueItemHeights[ci] ?: return@detectDragGesturesAfterLongPress
                                                            if (queueDragOffset > h * 0.6f && ci < queueReorderableTracks.lastIndex) {
                                                                val item = queueReorderableTracks.removeAt(ci)
                                                                queueReorderableTracks.add(ci + 1, item)
                                                                queueDraggedIndex = ci + 1
                                                                queueDragOffset -= h
                                                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                                            } else if (queueDragOffset < -h * 0.6f && ci > 0) {
                                                                val item = queueReorderableTracks.removeAt(ci)
                                                                queueReorderableTracks.add(ci - 1, item)
                                                                queueDraggedIndex = ci - 1
                                                                queueDragOffset += h
                                                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                                            }
                                                        },
                                                        onDragEnd = {
                                                            val from = currentIndex + 1 + queueDragStartIndex
                                                            val to = currentIndex + 1 + queueDraggedIndex
                                                            val finalOffset = queueDragOffset
                                                            val droppedKey = if (queueDraggedIndex in queueReorderableTracks.indices)
                                                                queueReorderableTracks[queueDraggedIndex].id else null

                                                            queueDraggedIndex = -1
                                                            queueDragStartIndex = -1
                                                            queueDragOffset = 0f

                                                            if (from != to) {
                                                                playerViewModel.moveQueueItem(from, to)
                                                            }

                                                            if (droppedKey != null && kotlin.math.abs(finalOffset) > 1f) {
                                                                queueDroppingItemKey = droppedKey
                                                                scope.launch {
                                                                    queueDropAnimOffset.snapTo(finalOffset)
                                                                    queueDropAnimOffset.animateTo(0f, queueDropSpec)
                                                                    queueDroppingItemKey = null
                                                                }
                                                            }
                                                        },
                                                        onDragCancel = {
                                                            queueDraggedIndex = -1
                                                            queueDragStartIndex = -1
                                                            queueDragOffset = 0f
                                                        },
                                                    )
                                                },
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.ic_drag_handle),
                                                contentDescription = "Reorder",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(24.dp),
                                            )
                                        }
                                    }
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            )
                        }
                    }
                }

                // Loading indicator at bottom for pagination
                if (hasMore) {
                    item(key = "queue_loading_more") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularWavyProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    }
                }

                // Bottom spacer
                item(key = "queue_bottom_spacer") {
                    Spacer(modifier = Modifier.height(28.dp))
                }
            }
            if (queueReorderableTracks.size > 15) {
                FastScrollbar(
                    listState = queueListState,
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
            }
            } // end Box wrapping LazyColumn + scrollbar
        }
    }
}

/**
 * A shape that morphs between two RoundedPolygon states.
 */
private class PlaybackMorphShape(
    private val morph: Morph,
    private val progress: Float,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val path = morph.toPath(progress = progress)

        // Use graphics-shapes native bounds calculation
        val bounds = morph.calculateBounds()
        val pathWidth = bounds[2] - bounds[0]  // right - left
        val pathHeight = bounds[3] - bounds[1] // bottom - top
        val centerX = (bounds[0] + bounds[2]) / 2f
        val centerY = (bounds[1] + bounds[3]) / 2f

        val matrix = Matrix()
        if (pathWidth > 0f && pathHeight > 0f) {
            matrix.postTranslate(-centerX, -centerY)
            val scale = minOf(size.width / pathWidth, size.height / pathHeight)
            matrix.postScale(scale, scale)
            matrix.postTranslate(size.width / 2f, size.height / 2f)
        } else {
            matrix.postScale(size.width / 2f, size.height / 2f)
            matrix.postTranslate(size.width / 2f, size.height / 2f)
        }
        path.transform(matrix)

        return Outline.Generic(path.asComposePath())
    }
}

private fun formatTime(ms: Long): String {
    val safeMs = ms.coerceAtLeast(0L)
    val totalSeconds = safeMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%d:%02d", minutes, seconds)
}

private fun audioDeviceDisplayName(device: android.media.AudioDeviceInfo): String {
    val productName = device.productName?.toString()?.takeIf { it.isNotBlank() }
    return productName ?: when (device.type) {
        android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Speaker"
        android.media.AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "Earpiece"
        android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired headset"
        android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Wired headphones"
        android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth"
        android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth SCO"
        android.media.AudioDeviceInfo.TYPE_USB_DEVICE -> "USB audio"
        android.media.AudioDeviceInfo.TYPE_USB_ACCESSORY -> "USB accessory"
        android.media.AudioDeviceInfo.TYPE_USB_HEADSET -> "USB headset"
        android.media.AudioDeviceInfo.TYPE_HDMI -> "HDMI"
        android.media.AudioDeviceInfo.TYPE_DOCK -> "Dock"
        android.media.AudioDeviceInfo.TYPE_AUX_LINE -> "Aux"
        android.media.AudioDeviceInfo.TYPE_BLE_HEADSET -> "BLE headset"
        android.media.AudioDeviceInfo.TYPE_BLE_SPEAKER -> "BLE speaker"
        else -> "Audio device"
    }
}

@androidx.annotation.DrawableRes
private fun audioDeviceIcon(device: android.media.AudioDeviceInfo): Int = when (device.type) {
    android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
    android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
    android.media.AudioDeviceInfo.TYPE_BLE_HEADSET,
    android.media.AudioDeviceInfo.TYPE_BLE_SPEAKER -> R.drawable.ic_bluetooth
    android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET,
    android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
    android.media.AudioDeviceInfo.TYPE_USB_HEADSET -> R.drawable.ic_headphones
    else -> R.drawable.ic_speaker
}
