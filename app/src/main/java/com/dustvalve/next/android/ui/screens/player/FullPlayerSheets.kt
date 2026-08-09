package com.dustvalve.next.android.ui.screens.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dustvalve.next.android.R
import com.dustvalve.next.android.domain.model.Track
import com.dustvalve.next.android.player.QueueEntry
import com.dustvalve.next.android.ui.adaptive.AdaptiveLayoutInfo
import com.dustvalve.next.android.ui.components.lists.SegmentedListItem
import com.dustvalve.next.android.ui.components.sheet.AddToPlaylistSheet
import com.dustvalve.next.android.ui.theme.segmentedItemShape
import com.dustvalve.next.android.ui.util.displayNameRes
import com.dustvalve.next.android.ui.util.tick
import kotlinx.coroutines.flow.distinctUntilChanged
import android.media.AudioDeviceInfo

/** Number of haptic segments across a volume slider. */
internal const val VOLUME_TICK_SEGMENTS = 15


@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun FullPlayerVolumeSheet(
    adaptiveInfo: AdaptiveLayoutInfo,
    visible: Boolean,
    volumeLevel: Float,
    audioOutputDevices: List<AudioDeviceInfo>,
    activeAudioDevice: AudioDeviceInfo?,
    onDismiss: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onSelectDevice: (AudioDeviceInfo?) -> Unit,
) {
    if (!visible) return
    val adaptive = adaptiveInfo
    val hapticFeedback = LocalHapticFeedback.current
    val onVolumeChangeState = rememberUpdatedState(onVolumeChange)
    val sheetVolumeState = androidx.compose.material3.rememberSliderState(
        value = volumeLevel,
    )
    LaunchedEffect(volumeLevel) {
        if (!sheetVolumeState.isDragging) sheetVolumeState.value = volumeLevel
    }
    LaunchedEffect(Unit) {
        snapshotFlow { sheetVolumeState.value }
            .collect { onVolumeChangeState.value(it) }
    }
    LaunchedEffect(sheetVolumeState) {
        snapshotFlow { (sheetVolumeState.value * VOLUME_TICK_SEGMENTS).toInt() }
            .distinctUntilChanged()
            .collect { if (sheetVolumeState.isDragging) hapticFeedback.tick() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = androidx.compose.material3.rememberBottomSheetState(
            initialValue = androidx.compose.material3.SheetValue.Hidden,
            enabledValues = setOf(
                androidx.compose.material3.SheetValue.Hidden,
                androidx.compose.material3.SheetValue.Expanded,
            ),
        ),
        sheetMaxWidth = adaptive.sheetMaxWidth,
    ) {
        FullPlayerVolumeSheetBody(
            sheetVolumeState = sheetVolumeState,
            audioOutputDevices = audioOutputDevices,
            activeAudioDevice = activeAudioDevice,
            onSelectDevice = onSelectDevice,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FullPlayerVolumeSheetBody(
    sheetVolumeState: androidx.compose.material3.SliderState,
    audioOutputDevices: List<AudioDeviceInfo>,
    activeAudioDevice: AudioDeviceInfo?,
    onSelectDevice: (AudioDeviceInfo?) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.player_volume),
            style = MaterialTheme.typography.titleMedium,
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
            topToBottom = false,
            thumb = { _ ->
                androidx.compose.material3.SliderDefaults.Thumb(
                    interactionSource = remember { MutableInteractionSource() },
                    isVertical = true,
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
            text = stringResource(R.string.player_output),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))
        FullPlayerAudioOutputList(
            audioOutputDevices = audioOutputDevices,
            activeAudioDevice = activeAudioDevice,
            onSelectDevice = onSelectDevice,
        )
        Spacer(modifier = Modifier.height(28.dp))
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FullPlayerAudioOutputList(
    audioOutputDevices: List<AudioDeviceInfo>,
    activeAudioDevice: AudioDeviceInfo?,
    onSelectDevice: (AudioDeviceInfo?) -> Unit,
) {
    Column(modifier = Modifier.selectableGroup()) {
        val totalDeviceCount = 1 + audioOutputDevices.size
        val autoSelected = activeAudioDevice == null
        val autoColor by animateColorAsState(
            targetValue = if (autoSelected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                Color.Transparent
            },
            animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
            label = "autoDeviceColor",
        )
        Surface(
            shape = segmentedItemShape(0, totalDeviceCount),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier
                .selectable(
                    selected = autoSelected,
                    onClick = { onSelectDevice(null) },
                    role = Role.RadioButton,
                ),
        ) {
            ListItem(
                leadingContent = { RadioButton(selected = autoSelected, onClick = null) },
                trailingContent = {
                    Icon(
                        painter = painterResource(R.drawable.ic_speaker),
                        contentDescription = null,
                    )
                },
                colors = ListItemDefaults.colors(containerColor = autoColor),
            ) {
                Text(stringResource(R.string.player_automatic))
            }
        }
        audioOutputDevices.forEachIndexed { index, device ->
            val deviceIndex = index + 1
            val isActive = activeAudioDevice?.id == device.id
            val bgColor by animateColorAsState(
                targetValue = if (isActive) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    Color.Transparent
                },
                animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
                label = "deviceColor",
            )
            Surface(
                shape = segmentedItemShape(deviceIndex, totalDeviceCount),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .selectable(
                        selected = isActive,
                        onClick = { onSelectDevice(device) },
                        role = Role.RadioButton,
                    ),
            ) {
                ListItem(
                    leadingContent = { RadioButton(selected = isActive, onClick = null) },
                    trailingContent = {
                        Icon(
                            painter = painterResource(audioDeviceIcon(device)),
                            contentDescription = null,
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = bgColor),
                ) {
                    Text(audioDeviceDisplayName(device))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun FullPlayerDeleteDownloadDialog(
    trackTitle: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (trackTitle == null) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.player_delete_download_title)) },
        text = { Text(stringResource(R.string.player_delete_download_text, trackTitle)) },
        confirmButton = {
            TextButton(onClick = onConfirm, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.common_action_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.common_action_cancel))
            }
        },
    )
}


@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun FullPlayerDebugSheet(
    adaptiveInfo: AdaptiveLayoutInfo,
    track: Track?,
    currentSourcePath: String?,
    currentPlaybackFormat: com.dustvalve.next.android.domain.model.AudioFormat?,
    downloadedTrackIds: Set<String>,
    downloadingTrackId: String?,
    onDismiss: () -> Unit,
) {
    if (track == null) return
    val adaptive = adaptiveInfo
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetMaxWidth = adaptive.sheetMaxWidth,
    ) {
        Text(
            text = stringResource(R.string.player_playback_info),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        FullPlayerDebugRows(
            track = track,
            currentSourcePath = currentSourcePath,
            currentPlaybackFormat = currentPlaybackFormat,
            downloadedTrackIds = downloadedTrackIds,
            downloadingTrackId = downloadingTrackId,
        )
        Spacer(modifier = Modifier.height(28.dp))
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FullPlayerDebugRows(
    track: Track,
    currentSourcePath: String?,
    currentPlaybackFormat: com.dustvalve.next.android.domain.model.AudioFormat?,
    downloadedTrackIds: Set<String>,
    downloadingTrackId: String?,
) {
    val isLocalTrackDebug = track.isLocal
    val isLocal = currentSourcePath != null
    val isTrackDownloaded = track.id in downloadedTrackIds
    val isDownloading = downloadingTrackId == track.id

    val downloadStatus = stringResource(
        when {
            isLocalTrackDebug -> R.string.player_debug_local_file
            isDownloading -> R.string.player_debug_downloading
            isTrackDownloaded -> R.string.player_debug_downloaded
            isLocal -> R.string.player_debug_cached
            else -> R.string.player_debug_not_downloaded
        },
    )
    val formatDisplay = if (isLocalTrackDebug) {
        stringResource(R.string.player_debug_local)
    } else {
        currentPlaybackFormat?.let { stringResource(it.displayNameRes) }
            ?: stringResource(R.string.player_debug_unknown)
    }
    val sourceDisplay = stringResource(
        if (isLocalTrackDebug || isLocal) {
            R.string.player_debug_local_file
        } else {
            R.string.player_debug_streaming
        },
    )
    val pathDisplay = currentSourcePath?.let {
        it.substringAfterLast("/downloads/")
    } ?: track.streamUrl?.take(60) ?: stringResource(R.string.player_debug_none)

    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        FullPlayerDebugRow(0, R.string.player_debug_audio_format, R.drawable.ic_audio_file, formatDisplay)
        FullPlayerDebugRow(1, R.string.player_debug_source, R.drawable.ic_cloud, sourceDisplay)
        FullPlayerDebugRow(2, R.string.player_debug_download_status, R.drawable.ic_download, downloadStatus)
        FullPlayerDebugRow(3, R.string.player_debug_file_path, R.drawable.ic_storage, pathDisplay)
        FullPlayerDebugRow(4, R.string.player_debug_track_id, R.drawable.ic_info, track.id)
        FullPlayerDebugRow(5, R.string.player_debug_album_id, R.drawable.ic_info, track.albumId)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FullPlayerDebugRow(
    index: Int,
    labelRes: Int,
    iconRes: Int,
    value: String,
) {
    SegmentedListItem(
        index = index,
        count = 6,
        contentPadding = PaddingValues(0.dp),
    ) {
        ListItem(
            supportingContent = { Text(stringResource(labelRes)) },
            leadingContent = {
                Icon(painterResource(iconRes), contentDescription = null)
            },
        ) {
            Text(
                text = value,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun FullPlayerAddToPlaylistSheet(
    visible: Boolean,
    playlists: List<com.dustvalve.next.android.domain.model.Playlist>,
    onDismiss: () -> Unit,
    onSelectPlaylist: (String) -> Unit,
    onCreatePlaylist: (String, String?, String?) -> Unit,
) {
    if (!visible) return
    AddToPlaylistSheet(
        playlists = playlists,
        onDismiss = onDismiss,
        onPlaylistSelected = onSelectPlaylist,
        onCreatePlaylist = onCreatePlaylist,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun FullPlayerUpNextContextSheet(
    adaptiveInfo: AdaptiveLayoutInfo,
    contextEntry: QueueEntry?,
    onDismiss: () -> Unit,
    onToggleFavorite: (String) -> Unit,
    onAddToPlaylist: () -> Unit,
    onRemoveFromQueue: (Long) -> Unit,
) {
    if (contextEntry == null) return
    val adaptive = adaptiveInfo
    val contextTrack = contextEntry.track
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetMaxWidth = adaptive.sheetMaxWidth,
    ) {
        Text(
            text = contextTrack.title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        ListItem(
            leadingContent = {
                Icon(
                    painter = painterResource(
                        if (contextTrack.isFavorite) R.drawable.ic_favorite else R.drawable.ic_favorite_border,
                    ),
                    contentDescription = null,
                )
            },
            modifier = Modifier.clickable {
                onToggleFavorite(contextTrack.id)
                onDismiss()
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        ) {
            Text(
                stringResource(
                    if (contextTrack.isFavorite) {
                        R.string.player_remove_from_favorites
                    } else {
                        R.string.player_add_to_favorites
                    },
                ),
            )
        }
        ListItem(
            leadingContent = {
                Icon(
                    painter = painterResource(R.drawable.ic_playlist_add),
                    contentDescription = null,
                )
            },
            modifier = Modifier.clickable(onClick = onAddToPlaylist),
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        ) {
            Text(stringResource(R.string.common_add_to_playlist))
        }
        ListItem(
            leadingContent = {
                Icon(
                    painter = painterResource(R.drawable.ic_close),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
            },
            modifier = Modifier.clickable {
                onRemoveFromQueue(contextEntry.uid)
                onDismiss()
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        ) {
            Text(
                text = stringResource(R.string.player_remove_from_queue),
                color = MaterialTheme.colorScheme.error,
            )
        }
        Spacer(modifier = Modifier.height(28.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun FullPlayerQueueSheet(
    adaptiveInfo: AdaptiveLayoutInfo,
    visible: Boolean,
    currentQueueIndex: Int,
    currentTrackId: String?,
    downloadedTrackIds: Set<String>,
    onDismiss: () -> Unit,
    onEntryLongClick: (QueueEntry) -> Unit,
) {
    if (!visible) return
    val adaptive = adaptiveInfo
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = androidx.compose.material3.rememberBottomSheetState(
            initialValue = androidx.compose.material3.SheetValue.Hidden,
            enabledValues = setOf(
                androidx.compose.material3.SheetValue.Hidden,
                androidx.compose.material3.SheetValue.Expanded,
            ),
        ),
        containerColor = MaterialTheme.colorScheme.surface,
        sheetMaxWidth = adaptive.sheetMaxWidth,
    ) {
        UpNextQueuePane(
            currentQueueIndex = currentQueueIndex,
            currentTrackId = currentTrackId,
            downloadedTrackIds = downloadedTrackIds,
            onEntryLongClick = onEntryLongClick,
        )
    }
}
