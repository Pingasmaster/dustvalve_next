package com.dustvalve.next.android.workflow

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.test.utils.FakeClock
import androidx.media3.test.utils.TestExoPlayerBuilder
import androidx.media3.test.utils.robolectric.TestPlayerRunHelper
import androidx.test.core.app.ApplicationProvider
import com.dustvalve.next.android.player.PlaybackManager
import com.dustvalve.next.android.player.QueueManager
import com.dustvalve.next.android.workflow.support.FixtureTracks
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Regression net for the "play button does nothing after a failure" black
 * hole: after a PlaybackException ExoPlayer sits in STATE_IDLE and silently
 * ignores play() until prepare() is called again. v0.5.x never re-prepared
 * and never surfaced the error, so the UI showed the track at 0:00 with a
 * dead play button and zero feedback.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37])
class PlaybackErrorRecoveryTest {

    private lateinit var player: ExoPlayer
    private lateinit var queueManager: QueueManager
    private lateinit var manager: PlaybackManager

    @Before fun setUp() {
        player = TestExoPlayerBuilder(ApplicationProvider.getApplicationContext())
            .setClock(FakeClock(true))
            .build()
        queueManager = QueueManager()
        manager = PlaybackManager(player, queueManager, ApplicationProvider.getApplicationContext())
    }

    @After fun tearDown() {
        player.release()
    }

    @Test
    fun play_afterIdleWithMediaItems_preparesInsteadOfNoop() {
        // Simulate the post-error state: a media item is set but the player
        // is idle (this is exactly where onPlayerError leaves ExoPlayer).
        player.setMediaItem(MediaItem.fromUri(FixtureTracks.localTrack().streamUrl!!))
        assertThat(player.playbackState).isEqualTo(Player.STATE_IDLE)

        manager.play()
        shadowOf(android.os.Looper.getMainLooper()).idle()

        // Before the fix play() was a silent no-op and the player stayed IDLE
        // forever. With the fix it re-prepares and starts.
        assertThat(player.playbackState).isNotEqualTo(Player.STATE_IDLE)
        TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_READY)
        assertThat(player.playWhenReady).isTrue()
    }

    @Test
    fun play_withNoMediaItems_staysIdleWithoutCrashing() {
        manager.play()
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertThat(player.playbackState).isEqualTo(Player.STATE_IDLE)
    }

    @Test
    fun playerError_isExposed_andClearable() {
        // Feed the player a URI that cannot resolve to trigger a real error.
        player.setMediaItem(MediaItem.fromUri("file:///does/not/exist.mp3"))
        player.prepare()
        player.play()
        TestPlayerRunHelper.runUntilError(player)
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertThat(manager.playbackError.value).isNotNull()

        manager.clearPlaybackError()
        assertThat(manager.playbackError.value).isNull()
    }

    @Test
    fun playTrack_afterError_clearsErrorAndPlays() {
        player.setMediaItem(MediaItem.fromUri("file:///does/not/exist.mp3"))
        player.prepare()
        player.play()
        TestPlayerRunHelper.runUntilError(player)
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertThat(manager.playbackError.value).isNotNull()

        manager.playTrack(FixtureTracks.localTrack())
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertThat(manager.playbackError.value).isNull()
        TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_READY)
        assertThat(player.playWhenReady).isTrue()
    }
}
