package com.dustvalve.next.android.player

import androidx.annotation.OptIn
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi

/**
 * A [ForwardingPlayer] that exposes skip-next/previous commands and routes them
 * through [PlaybackManager]'s custom queue logic, so the media notification
 * shows functional skip buttons even though ExoPlayer only has a single MediaItem.
 */
@OptIn(UnstableApi::class)
class QueueForwardingPlayer(
    player: Player,
    private val playbackManager: PlaybackManager,
    private val queueManager: QueueManager,
) : ForwardingPlayer(player) {

    override fun getAvailableCommands(): Player.Commands {
        return super.getAvailableCommands().buildUpon()
            .add(Player.COMMAND_SEEK_TO_NEXT)
            .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            .build()
    }

    override fun isCommandAvailable(command: Int): Boolean {
        return when (command) {
            Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> true
            else -> super.isCommandAvailable(command)
        }
    }

    override fun hasNextMediaItem(): Boolean = queueManager.hasNext()

    override fun hasPreviousMediaItem(): Boolean = queueManager.hasPrevious()

    override fun seekToNext() {
        playbackManager.skipNext()
    }

    override fun seekToNextMediaItem() {
        playbackManager.skipNext()
    }

    override fun seekToPrevious() {
        playbackManager.skipPrevious()
    }

    override fun seekToPreviousMediaItem() {
        playbackManager.skipPrevious()
    }
}
