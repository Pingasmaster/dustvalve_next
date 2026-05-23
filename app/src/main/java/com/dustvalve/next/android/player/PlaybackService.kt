package com.dustvalve.next.android.player

import android.content.Intent
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
     * After 5 minutes of pause the FGS still pins the app's standby bucket and
     * blocks Doze entry; stopping the player + service releases both.
     */
    private val pauseIdleListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            idleStopJob?.cancel()
            idleStopJob = if (isPlaying) {
                null
            } else {
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
        addSession(mediaSession)
        queueManager.reinitialize()
        playbackManager.reinitialize()
        mediaSession.player.addListener(pauseIdleListener)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession.player
        if (!player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        idleStopJob?.cancel()
        mediaSession.player.removeListener(pauseIdleListener)
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
    }
}
