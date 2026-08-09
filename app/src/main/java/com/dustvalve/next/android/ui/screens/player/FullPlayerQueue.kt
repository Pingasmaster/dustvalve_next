package com.dustvalve.next.android.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dustvalve.next.android.R
import com.dustvalve.next.android.player.QueueEntry
import com.dustvalve.next.android.ui.components.FastScrollbar
import com.dustvalve.next.android.ui.components.lists.MusicRow
import com.dustvalve.next.android.ui.components.lists.MusicRowActions
import com.dustvalve.next.android.ui.components.lists.MusicRowFlags
import com.dustvalve.next.android.ui.components.lists.ReorderableListSlots
import com.dustvalve.next.android.ui.components.lists.ReorderableMusicList
import com.dustvalve.next.android.ui.components.lists.SegmentedListItem
import com.dustvalve.next.android.ui.theme.segmentedItemShape

/**
 * Shared "Up Next" queue list used by the ModalBottomSheet (Compact/Medium)
 * and the Expanded dual-pane supporting side.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun UpNextQueuePane(
    currentQueueIndex: Int,
    currentTrackId: String?,
    downloadedTrackIds: Set<String>,
    onEntryLongClick: (QueueEntry) -> Unit,
    modifier: Modifier = Modifier,
    // Activity-scoped: same PlayerViewModel FullPlayer uses.
    playerViewModel: PlayerViewModel = hiltViewModel(),
) {
    val queueEntries by playerViewModel.queueEntries.collectAsStateWithLifecycle()
    val allUpNextEntries = if (currentQueueIndex >= 0 && currentQueueIndex < queueEntries.lastIndex) {
        queueEntries.subList(currentQueueIndex + 1, queueEntries.size)
    } else {
        emptyList()
    }

    var displayCount by remember(currentQueueIndex) { mutableIntStateOf(25) }
    val displayedEntries = allUpNextEntries.take(displayCount)
    val hasMore = displayCount < allUpNextEntries.size
    val queueListState = rememberLazyListState()

    val currentHasMore by rememberUpdatedState(hasMore)
    val currentDisplayedCount by rememberUpdatedState(displayedEntries.size)
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

    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = stringResource(R.string.player_up_next_count, allUpNextEntries.size),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )

        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            ReorderableMusicList(
                items = displayedEntries,
                keyFn = { it.uid },
                onMove = { from, to ->
                    val fromUid = displayedEntries.getOrNull(from)?.uid
                    val toUid = displayedEntries.getOrNull(to)?.uid
                    if (fromUid != null && toUid != null) {
                        playerViewModel.moveQueueEntry(fromUid, toUid)
                    }
                },
                lazyListState = queueListState,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                slots = ReorderableListSlots(
                    footer = {
                    if (hasMore) {
                        item(key = "queue_loading_more") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                                    .animateItem(),
                                contentAlignment = Alignment.Center,
                            ) {
                                ContainedLoadingIndicator()
                            }
                        }
                    }
                    item(key = "queue_bottom_spacer") {
                        Spacer(modifier = Modifier.height(28.dp))
                    }
                },
                ),
            ) { upNextIndex, queueEntry, isDragging, dragHandleModifier ->
                UpNextQueueRow(
                    model = UpNextQueueRowModel(
                        upNextIndex = upNextIndex,
                        queueEntry = queueEntry,
                        isDragging = isDragging,
                        displayedCount = displayedEntries.size,
                        currentTrackId = currentTrackId,
                        downloadedTrackIds = downloadedTrackIds,
                    ),
                    onPlay = { playerViewModel.playQueueEntry(queueEntry.uid) },
                    onRemove = { playerViewModel.removeQueueEntry(queueEntry.uid) },
                    onLongClick = { onEntryLongClick(queueEntry) },
                    modifier = dragHandleModifier,
                )
            }
            if (displayedEntries.size > 15) {
                FastScrollbar(
                    listState = queueListState,
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun UpNextQueueRow(
    model: UpNextQueueRowModel,
    onPlay: () -> Unit,
    onRemove: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hapticFeedback = LocalHapticFeedback.current
    val queueTrack = model.queueEntry.track
    val isDownloaded = queueTrack.id in model.downloadedTrackIds || queueTrack.isLocal
    val isCurrentTrack = model.currentTrackId == queueTrack.id
    val isFirst = model.upNextIndex == 0
    val isLast = model.upNextIndex == model.displayedCount - 1
    val currentOnRemove by rememberUpdatedState(onRemove)

    val dismissState = rememberSwipeToDismissBoxState()
    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
            currentOnRemove()
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        gesturesEnabled = !model.isDragging,
        backgroundContent = {
            if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            top = if (isFirst) 0.dp else 1.dp,
                            bottom = if (isLast) 0.dp else 1.dp,
                        )
                        .clip(segmentedItemShape(model.upNextIndex, model.displayedCount))
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(horizontal = 20.dp),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_delete),
                        contentDescription = stringResource(R.string.common_cd_delete),
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        },
    ) {
        SegmentedListItem(
            index = model.upNextIndex,
            count = model.displayedCount,
            isDragging = model.isDragging,
            contentPadding = PaddingValues(
                top = if (isFirst) 0.dp else 1.dp,
                bottom = if (isLast) 0.dp else 1.dp,
            ),
        ) {
            MusicRow(
                track = queueTrack,
                onClick = onPlay,
                flags = MusicRowFlags(
                    isCurrentTrack = isCurrentTrack,
                    showDownload = false,
                    isDownloaded = isDownloaded,
                ),
                actions = MusicRowActions(
                    onLongClick = onLongClick,
                    dragHandle = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isDownloaded) {
                            Icon(
                                painter = painterResource(R.drawable.ic_download_done),
                                contentDescription = stringResource(R.string.common_cd_downloaded),
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .then(modifier),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_drag_handle),
                                contentDescription = stringResource(R.string.common_cd_reorder),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                },
                ),
            )
        }
    }
}

@Stable
private class UpNextQueueRowModel(
    val upNextIndex: Int,
    val queueEntry: QueueEntry,
    val isDragging: Boolean,
    val displayedCount: Int,
    val currentTrackId: String?,
    val downloadedTrackIds: Set<String>,
)
