package com.dustvalve.next.android.workflow

import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.test.utils.FakeClock
import androidx.media3.test.utils.TestExoPlayerBuilder
import androidx.media3.test.utils.robolectric.TestPlayerRunHelper
import androidx.test.core.app.ApplicationProvider
import com.dustvalve.next.android.domain.model.RepeatMode
import com.dustvalve.next.android.player.PlaybackManager
import com.dustvalve.next.android.player.QueueManager
import com.dustvalve.next.android.workflow.support.AudioFixture
import com.dustvalve.next.android.workflow.support.FixtureTracks
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

/**
 * Regression net for the v0.5.0 "local playback stays at 0:00 and play does
 * nothing" bug: drives the REAL PlaybackManager with a REAL ExoPlayer that
 * decodes a checked-in PCM fixture, and asserts that pressing play makes the
 * playback position actually advance.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37])
class PlaybackPositionAdvancesTest {

    private lateinit var player: ExoPlayer
    private lateinit var queueManager: QueueManager
    private lateinit var manager: PlaybackManager

    // NOTE: no Dispatchers.setMain here. PlaybackManager's scope must run on
    // the real Robolectric main-looper dispatcher; substituting Unconfined
    // makes its position poller resume on a background thread and touch
    // ExoPlayer off the main looper.
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
    fun playTrack_reachesReady_andIsPlaying() {
        manager.playTrack(FixtureTracks.localTrack())

        TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_READY)

        assertThat(player.playWhenReady).isTrue()
        assertThat(manager.playbackState.value).isEqualTo(Player.STATE_READY)
        assertThat(manager.duration.value).isGreaterThan(0L)
    }

    @Test
    fun playTrack_positionAdvancesPastZero() {
        manager.playTrack(FixtureTracks.localTrack())

        TestPlayerRunHelper.playUntilPosition(player, 0, 500L)

        // playUntilPosition pauses on a 10ms render boundary just before the
        // target, so allow one boundary of slack.
        assertThat(player.currentPosition).isAtLeast(450L)
        // Let the 200ms position poller propagate into the manager's flow.
        shadowOf(android.os.Looper.getMainLooper()).idleFor(400L, TimeUnit.MILLISECONDS)
        assertThat(manager.currentPosition.value).isGreaterThan(0L)
    }

    @Test
    fun playQueue_autoAdvancesToNextTrack_atTrackEnd() {
        val first = FixtureTracks.localTrack(id = "t1")
        val second = FixtureTracks.localTrack(id = "t2")
        manager.playQueue(listOf(first, second), 0)

        TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_READY)
        // Run the first track to completion; handlePlaybackEnded must start t2.
        TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_ENDED)
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertThat(queueManager.currentTrack.value?.id).isEqualTo("t2")
        TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_READY)
        assertThat(player.playWhenReady).isTrue()
    }

    @Test
    fun repeatOne_restartsSameTrack_atTrackEnd() {
        manager.setRepeatMode(RepeatMode.ONE)
        manager.playTrack(FixtureTracks.localTrack(id = "solo"))

        TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_READY)
        TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_ENDED)
        shadowOf(android.os.Looper.getMainLooper()).idle()

        // Player restarted from 0 and is playing again.
        assertThat(player.playWhenReady).isTrue()
        assertThat(player.playbackState).isAnyOf(Player.STATE_READY, Player.STATE_BUFFERING)
    }

    @Test
    fun playTrack_nullStreamUrl_noopsWithoutCrash() {
        manager.playTrack(FixtureTracks.localTrack(streamUrl = null))

        assertThat(manager.isPlaying.value).isFalse()
        assertThat(manager.playbackState.value).isEqualTo(Player.STATE_IDLE)
        assertThat(player.mediaItemCount).isEqualTo(0)
    }

    @Test
    fun playTrack_barePath_isConvertedToFileUri() {
        val path = AudioFixture.toneWavFile().absolutePath
        manager.playTrack(FixtureTracks.localTrack(streamUrl = path))

        val uri = player.currentMediaItem?.localConfiguration?.uri
        assertThat(uri?.scheme).isEqualTo("file")
        TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_READY)
        assertThat(manager.duration.value).isGreaterThan(0L)
    }

    @Test
    fun pause_thenPlay_resumesFromSamePosition() {
        manager.playTrack(FixtureTracks.localTrack())
        TestPlayerRunHelper.playUntilPosition(player, 0, 400L)

        manager.pause()
        shadowOf(android.os.Looper.getMainLooper()).idle()
        val pausedAt = player.currentPosition
        assertThat(pausedAt).isAtLeast(350L)

        manager.play()
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertThat(player.playWhenReady).isTrue()
        assertThat(player.currentPosition).isAtLeast(pausedAt)
    }
}
