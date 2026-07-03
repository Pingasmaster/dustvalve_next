package com.dustvalve.next.android.player

import android.content.Intent
import android.os.PerformanceHintManager
import android.os.Process
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.dustvalve.next.android.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    @Inject
    lateinit var playbackManager: PlaybackManager

    @Inject
    lateinit var queueManager: QueueManager

    @Inject
    lateinit var mediaSession: MediaSession

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var idleStopJob: Job? = null

    /**
     * ADPF hint session: tells the OS that the main thread has known periodic
     * audio-callback work. Without this the kernel may opportunistically boost
     * CPU clocks during quiet stretches between Media3 callbacks. We open the
     * session lazily on first playback and report a conservative actual
     * duration each time isPlaying flips so the OS sees consistent headroom.
     *
     * Audio offload (PlayerModule) carries the heavy lifting on supporting
     * SoCs; this complements the non-offload path.
     */
    private var perfHintSession: PerformanceHintManager.Session? = null

    private fun ensurePerfHintSession(): PerformanceHintManager.Session? {
        if (perfHintSession == null) {
            val mgr = getSystemService(PerformanceHintManager::class.java) ?: return null
            perfHintSession = mgr.createHintSession(
                intArrayOf(Process.myTid()),
                TimeUnit.MILLISECONDS.toNanos(PERF_HINT_TARGET_MS),
            )
        }
        return perfHintSession
    }

    /**
     * After 5 minutes of pause the FGS still pins the app's standby bucket and
     * blocks Doze entry; stopping the player + service releases both.
     */
    private val pauseIdleListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            idleStopJob?.cancel()
            idleStopJob = if (isPlaying) {
                ensurePerfHintSession()?.reportActualWorkDuration(
                    TimeUnit.MILLISECONDS.toNanos(PERF_HINT_ACTUAL_MS),
                )
                null
            } else {
                // Paused → lots of CPU headroom; nudge the OS toward lower clocks.
                perfHintSession?.reportActualWorkDuration(
                    TimeUnit.MICROSECONDS.toNanos(PERF_HINT_IDLE_US),
                )
                serviceScope.launch {
                    delay(TimeUnit.MINUTES.toMillis(IDLE_STOP_MINUTES))
                    mediaSession.player.stop()
                    stopSelf()
                }
            }
        }
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        val notificationProvider = DefaultMediaNotificationProvider.Builder(this)
            .setChannelName(R.string.playback_notification_channel)
            .build()
        notificationProvider.setSmallIcon(R.drawable.ic_notification)
        setMediaNotificationProvider(notificationProvider)
        // An idle/un-prepared player otherwise keeps the FGS notification alive
        // longer than needed.
        setShowNotificationForIdlePlayer(SHOW_NOTIFICATION_FOR_IDLE_PLAYER_NEVER)
        addSession(mediaSession)
        queueManager.reinitialize()
        playbackManager.reinitialize()
        mediaSession.player.addListener(pauseIdleListener)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession.player
        if (!player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        idleStopJob?.cancel()
        mediaSession.player.removeListener(pauseIdleListener)
        perfHintSession?.close()
        perfHintSession = null
        serviceScope.cancel()
        playbackManager.release()
        queueManager.release()
        // Remove the session before super.onDestroy() to prevent MediaSessionService's
        // base implementation from releasing our singleton MediaSession.
        removeSession(mediaSession)
        super.onDestroy()
    }

    companion object {
        private const val IDLE_STOP_MINUTES = 5L

        // Target work-duration the OS uses to size CPU clocks.
        private const val PERF_HINT_TARGET_MS = 10L
        private const val PERF_HINT_ACTUAL_MS = 2L
        private const val PERF_HINT_IDLE_US = 100L
    }
}
