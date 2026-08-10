package com.dustvalve.next.android.ui.screens.player

import com.dustvalve.next.android.domain.model.RepeatMode
import com.dustvalve.next.android.player.PlaybackManager
import com.dustvalve.next.android.player.QueueManager

/**
 * Play/pause, skip, seek, stop, shuffle, and repeat.
 * Extracted so [PlayerLibraryCoordinator] stays under detekt TooManyFunctions.
 */
internal class PlayerTransportCoordinator(private val playbackManager: PlaybackManager, private val queueManager: QueueManager) {
    fun onPlayPause() = playbackManager.togglePlayPause()
    fun onNext() = playbackManager.skipNext()
    fun onPrevious() = playbackManager.skipPrevious()
    fun onSeek(ms: Long) = playbackManager.seekTo(ms)

    fun onStop() {
        playbackManager.stop()
        queueManager.clear()
    }

    fun onToggleShuffle() {
        playbackManager.setShuffleEnabled(!playbackManager.shuffleEnabled.value)
    }

    fun onToggleRepeat() {
        val nextMode = when (playbackManager.repeatMode.value) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        playbackManager.setRepeatMode(nextMode)
    }
}
