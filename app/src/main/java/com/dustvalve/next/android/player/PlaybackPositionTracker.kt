package com.dustvalve.next.android.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import androidx.media3.exoplayer.ExoPlayer

/**
 * Demand-gated 5 Hz position poll for [PlaybackManager].
 * Extracted so the manager stays under detekt TooManyFunctions.
 */
internal class PlaybackPositionTracker(
    private val player: ExoPlayer,
    private val scopeProvider: () -> CoroutineScope,
    private val currentPosition: MutableStateFlow<Long>,
) {
    private var positionUpdateJob: Job? = null

    /** Tracks whether a seek is in progress to avoid position update overwrite */
    @Volatile
    var seekInProgress = false

    /** True while at least one collector is subscribed to [currentPosition]. */
    @Volatile
    private var hasPositionSubscribers = false

    fun startDemandGate() {
        scopeProvider().launch {
            currentPosition.subscriptionCount
                .map { it > 0 }
                .distinctUntilChanged()
                .collect { active ->
                    hasPositionSubscribers = active
                    if (active) {
                        if (player.isPlaying) startUpdates()
                    } else {
                        stopUpdates()
                    }
                }
        }
    }

    fun startUpdates() {
        stopUpdates()
        if (!hasPositionSubscribers) return
        positionUpdateJob = scopeProvider().launch {
            while (isActive) {
                if (!seekInProgress) {
                    currentPosition.value = player.currentPosition.coerceAtLeast(0L)
                }
                delay(POSITION_POLL_INTERVAL_MS)
            }
        }
    }

    fun stopUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = null
    }

    private companion object {
        private const val POSITION_POLL_INTERVAL_MS = 200L
    }
}
