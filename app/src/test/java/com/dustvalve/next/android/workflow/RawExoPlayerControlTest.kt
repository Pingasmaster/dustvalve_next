package com.dustvalve.next.android.workflow

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.test.utils.FakeClock
import androidx.media3.test.utils.TestExoPlayerBuilder
import androidx.media3.test.utils.robolectric.TestPlayerRunHelper
import androidx.test.core.app.ApplicationProvider
import com.dustvalve.next.android.workflow.support.AudioFixture
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Control experiment: a bare TestExoPlayerBuilder player with no app code in
 * the loop. If position does not advance here either, the stall seen in
 * PlaybackPositionAdvancesTest is a Robolectric pipeline artifact, not an
 * app regression.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37])
class RawExoPlayerControlTest {

    private lateinit var player: ExoPlayer

    @Before fun setUp() {
        player = TestExoPlayerBuilder(ApplicationProvider.getApplicationContext())
            .setClock(FakeClock(true))
            .build()
    }

    @After fun tearDown() {
        player.release()
    }

    @Test
    fun rawPlayer_wavPositionAdvances() {
        player.setMediaItem(MediaItem.fromUri(AudioFixture.toneWavUri()))
        player.prepare()
        player.play()

        TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_READY)
        TestPlayerRunHelper.playUntilPosition(player, 0, 500L)

        assertThat(player.currentPosition).isAtLeast(500L)
    }

    @Test
    fun rawPlayer_wavPlaysToEnd() {
        player.setMediaItem(MediaItem.fromUri(AudioFixture.toneWavUri()))
        player.prepare()
        player.play()

        TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_ENDED)
        assertThat(player.playbackState).isEqualTo(Player.STATE_ENDED)
    }
}
