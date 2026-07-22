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
 * Regression net for queue-lifecycle black holes around service teardown and
 * queue mutation:
 *
 * - H1: PlaybackService's 5-minute idle-stop used to release() managers that
 *   cleared the queue - a long pause or phone call silently erased the whole
 *   session and hid the mini player. Teardown must be non-destructive and
 *   play() must revive the same track at the saved position.
 * - L18: repeat-all wraparound used to restart via setQueue, nulling the
 *   pre-shuffle snapshot so shuffle-off silently no-oped after one pass.
 * - L19: the no-playable-successor give-up branch used to force IDLE flows
 *   over still-audible playback and leave currentIndex walked to queue end.
 * - L17: removing the currently playing queue entry used to repoint the index
 *   while the player kept playing the removed track.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37])
class PlaybackQueueRecoveryTest {

    private lateinit var player: ExoPlayer
    private lateinit var queueManager: QueueManager
    private lateinit var manager: PlaybackManager

    // NOTE: no Dispatchers.setMain here - PlaybackManager's scope must run on
    // the real Robolectric main-looper dispatcher (see PlaybackPositionAdvancesTest).
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

    private fun idle() = shadowOf(android.os.Looper.getMainLooper()).idle()

    @Test
    fun idleStop_preservesQueue_andPlayResumesSameTrackAtSavedPosition() {
        val t1 = FixtureTracks.localTrack(id = "t1")
        val t2 = FixtureTracks.localTrack(id = "t2")
        manager.playQueue(listOf(t1, t2), 0)
        TestPlayerRunHelper.playUntilPosition(player, 0, 400L)

        manager.pause()
        idle()
        val pausedAt = player.currentPosition
        assertThat(pausedAt).isAtLeast(350L)

        // Exactly what PlaybackService does on the 5-minute idle-stop:
        // player.stop() + stopSelf() -> onDestroy -> release both managers.
        player.stop()
        idle()
        manager.release()
        queueManager.release()
        idle()

        // Queue and current track survive, so the mini player stays visible.
        assertThat(queueManager.queue.value.map { it.id }).containsExactly("t1", "t2").inOrder()
        assertThat(queueManager.currentTrack.value?.id).isEqualTo("t1")
        assertThat(manager.isPlaying.value).isFalse()
        // Displayed position is not wiped back to 0:00.
        assertThat(manager.currentPosition.value).isAtLeast(350L)

        // Pressing play revives the session: same track, saved position.
        manager.togglePlayPause()
        idle()
        TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_READY)
        assertThat(player.currentMediaItem?.mediaId).isEqualTo("t1")
        assertThat(player.playWhenReady).isTrue()
        assertThat(player.currentPosition).isAtLeast(pausedAt)
    }

    @Test
    fun idleStop_skipNext_alsoRevivesPlayback() {
        val t1 = FixtureTracks.localTrack(id = "t1")
        val t2 = FixtureTracks.localTrack(id = "t2")
        manager.playQueue(listOf(t1, t2), 0)
        TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_READY)

        player.stop()
        idle()
        manager.release()
        queueManager.release()
        idle()

        manager.skipNext()
        idle()
        TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_READY)
        assertThat(player.currentMediaItem?.mediaId).isEqualTo("t2")
        assertThat(player.playWhenReady).isTrue()
    }

    @Test
    fun repeatAllWraparound_preservesShuffleSnapshot() {
        val tracks = listOf(
            FixtureTracks.localTrack(id = "t1"),
            FixtureTracks.localTrack(id = "t2"),
            FixtureTracks.localTrack(id = "t3"),
            FixtureTracks.localTrack(id = "t4"),
        )
        val originalIds = tracks.map { it.id }
        manager.playQueue(tracks, 0)
        TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_READY)
        manager.setRepeatMode(RepeatMode.ALL)
        manager.setShuffleEnabled(true)
        // Make sure the shuffled order actually differs from the original so
        // the restore assertion below is meaningful.
        var guard = 0
        while (queueManager.queue.value.map { it.id } == originalIds && guard++ < 100) {
            queueManager.shuffle()
        }

        // Jump to the last slot, then skip past the end: repeat-all wraps.
        manager.skipToQueueIndex(queueManager.queue.value.lastIndex)
        idle()
        manager.skipNext()
        idle()
        assertThat(queueManager.currentIndex.value).isEqualTo(0)

        // L18 regression: before the fix, the wraparound went through setQueue,
        // nulling the pre-shuffle snapshot - this restore silently no-oped.
        manager.setShuffleEnabled(false)
        idle()
        assertThat(queueManager.queue.value.map { it.id }).isEqualTo(originalIds)
    }

    @Test
    fun giveUpBranch_keepsAudibleStateTruthful_andRestoresIndex() {
        val playable = FixtureTracks.localTrack(id = "t1")
        val dead1 = FixtureTracks.localTrack(id = "t2", streamUrl = null)
        val dead2 = FixtureTracks.localTrack(id = "t3", streamUrl = null)
        manager.playQueue(listOf(playable, dead1, dead2), 0)
        TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_READY)
        idle()
        assertThat(manager.isPlaying.value).isTrue()

        // Skip lands on an all-unplayable tail: no successor can play.
        manager.skipNext()
        idle()

        // L19 regression: the error is surfaced, but t1 keeps playing and the
        // flows reflect the audible truth instead of forcing IDLE/stopped.
        assertThat(manager.playbackError.value).isNotNull()
        assertThat(player.currentMediaItem?.mediaId).isEqualTo("t1")
        assertThat(player.isPlaying).isTrue()
        assertThat(manager.isPlaying.value).isTrue()
        assertThat(manager.playbackState.value).isEqualTo(player.playbackState)
        // currentIndex restored to where the attempt started (the skip target),
        // not walked to the queue end.
        assertThat(queueManager.currentIndex.value).isEqualTo(1)
    }

    @Test
    fun removeCurrentEntry_advancesPlayerToSuccessor() {
        val t1 = FixtureTracks.localTrack(id = "t1")
        val t2 = FixtureTracks.localTrack(id = "t2")
        manager.playQueue(listOf(t1, t2), 0)
        TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_READY)

        // L17 regression: removing the playing entry used to repoint the index
        // at t2 while the player kept audibly playing the removed t1.
        queueManager.removeFromQueue(0)
        idle()

        assertThat(queueManager.currentTrack.value?.id).isEqualTo("t2")
        assertThat(player.currentMediaItem?.mediaId).isEqualTo("t2")
        assertThat(player.playWhenReady).isTrue()
    }

    @Test
    fun removeLastRemainingCurrentEntry_stopsPlayback() {
        val t1 = FixtureTracks.localTrack(id = "t1")
        manager.playQueue(listOf(t1), 0)
        TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_READY)

        queueManager.removeFromQueue(0)
        idle()

        assertThat(queueManager.currentTrack.value).isNull()
        assertThat(player.mediaItemCount).isEqualTo(0)
        assertThat(manager.isPlaying.value).isFalse()
    }
}
