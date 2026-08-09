package com.dustvalve.next.android.ui.components.sheet

import androidx.compose.runtime.Immutable

@Immutable
data class RemoteResultActions(
    val onDismiss: () -> Unit,
    val onPlayNext: () -> Unit,
    val onAddToQueue: () -> Unit,
    val onAddToPlaylist: () -> Unit,
    val onPlayAll: () -> Unit,
    val onEnqueueAll: () -> Unit,
    val onShare: () -> Unit,
    val onOpenInBrowser: () -> Unit,
)
