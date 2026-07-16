package com.dustvalve.next.android.workflow

import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.test.utils.FakeClock
import androidx.media3.test.utils.TestExoPlayerBuilder
import androidx.media3.test.utils.robolectric.TestPlayerRunHelper
import androidx.test.core.app.ApplicationProvider
import com.dustvalve.next.android.data.local.datastore.SettingsDataStore
import com.dustvalve.next.android.data.local.db.dao.FavoriteDao
import com.dustvalve.next.android.domain.model.AudioFormat
import com.dustvalve.next.android.domain.repository.DownloadInfo
import com.dustvalve.next.android.domain.repository.DownloadRepository
import com.dustvalve.next.android.domain.repository.LibraryRepository
import com.dustvalve.next.android.domain.repository.PlaylistRepository
import com.dustvalve.next.android.domain.repository.YouTubeRepository
import com.dustvalve.next.android.domain.usecase.DownloadAlbumUseCase
import com.dustvalve.next.android.download.DownloadController
import com.dustvalve.next.android.player.PlaybackManager
import com.dustvalve.next.android.player.QueueManager
import com.dustvalve.next.android.ui.screens.player.PlayerViewModel
import com.dustvalve.next.android.workflow.support.AudioFixture
import com.dustvalve.next.android.workflow.support.FixtureTracks
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Workflow net over PlayerViewModel + REAL PlaybackManager/QueueManager/
 * ExoPlayer: exercises resolveTrackForPlayback per provider and asserts the
 * exact v0.5.0 contract - a failed YouTube stream resolution must surface a
 * snackbar and SKIP playback (never hand a watch-page URL to ExoPlayer), and
 * a local track tap must actually start playback.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37])
class PlayerViewModelResolveTest {

    private lateinit var player: ExoPlayer
    private lateinit var queueManager: QueueManager
    private lateinit var playbackManager: PlaybackManager

    private val downloadRepository = mockk<DownloadRepository> {
        every { getDownloadedTrackIds() } returns flowOf(emptyList())
        coEvery { getDownloadInfo(any()) } returns null
    }
    private val playlistRepository = mockk<PlaylistRepository>(relaxed = true) {
        every { getAllPlaylists() } returns flowOf(emptyList())
        every { getTrackIdsInUserPlaylists() } returns flowOf(emptySet())
    }
    private val favoriteDao = mockk<FavoriteDao> {
        every { getAllByType(any()) } returns flowOf(emptyList())
    }
    private val libraryRepository = mockk<LibraryRepository>(relaxed = true)
    private val youtubeRepository = mockk<YouTubeRepository>()
    private val settingsDataStore = mockk<SettingsDataStore>(relaxed = true) {
        coEvery { getProgressiveDownloadSync() } returns false
        // uiState combines these flows; they must emit or uiState stays at
        // its initial value forever.
        every { progressBarStyle } returns flowOf("wavy")
        every { progressBarSizeDp } returns flowOf(24)
        every { showInlineVolumeSlider } returns flowOf(false)
        every { showVolumeButton } returns flowOf(false)
        every { albumCoverLongPressCarousel } returns flowOf(false)
    }

    private lateinit var viewModel: PlayerViewModel

    @Before fun setUp() {
        player = TestExoPlayerBuilder(ApplicationProvider.getApplicationContext())
            .setClock(FakeClock(true))
            .build()
        queueManager = QueueManager()
        playbackManager = PlaybackManager(player, queueManager, ApplicationProvider.getApplicationContext())
        viewModel = PlayerViewModel(
            playbackManager,
            queueManager,
            libraryRepository,
            mockk<DownloadAlbumUseCase>(relaxed = true),
            mockk<DownloadController>(relaxed = true),
            downloadRepository,
            playlistRepository,
            favoriteDao,
            settingsDataStore,
            youtubeRepository,
            ApplicationProvider.getApplicationContext(),
        )
    }

    @After fun tearDown() {
        player.release()
    }

    private fun idle() = shadowOf(android.os.Looper.getMainLooper()).idle()

    @Test
    fun localTrack_click_startsPlayback_positionAdvances() {
        viewModel.playTrackInList(listOf(FixtureTracks.localTrack()), 0)
        idle()

        TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_READY)
        TestPlayerRunHelper.playUntilPosition(player, 0, 400L)

        assertThat(player.currentPosition).isGreaterThan(0L)
        assertThat(queueManager.currentTrack.value?.isLocal).isTrue()
    }

    @Test
    fun youtubeTrack_streamResolutionFails_skipsPlayback_showsSnackbar() {
        coEvery { youtubeRepository.getStreamUrl(any()) } throws IllegalStateException("HTTP 403")

        // uiState is WhileSubscribed - collect it so the snackbar propagates.
        val collected = mutableListOf<com.dustvalve.next.android.ui.screens.player.PlayerUiState>()
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main.immediate)
        val job = scope.launch { viewModel.uiState.collect { collected.add(it) } }

        viewModel.playTrack(FixtureTracks.youtubeTrack())
        idle()

        // Playback skipped: nothing handed to ExoPlayer.
        assertThat(player.mediaItemCount).isEqualTo(0)
        assertThat(playbackManager.isPlaying.value).isFalse()
        // Error surfaced to the user.
        assertThat(collected.last().snackbarMessage).isNotNull()
        job.cancel()
    }

    @Test
    fun youtubeTrack_streamResolves_playsGoogleVideoUrl_notWatchPage() {
        val resolved = AudioFixture.toneWavUri()
        coEvery { youtubeRepository.getStreamUrl(any()) } returns resolved

        viewModel.playTrack(FixtureTracks.youtubeTrack())
        idle()

        TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_READY)
        val playingUri = player.currentMediaItem?.localConfiguration?.uri.toString()
        assertThat(playingUri).isEqualTo(resolved)
        assertThat(playingUri).doesNotContain("watch?v=")
    }

    @Test
    fun youtubeTrack_downloadedFile_isPreferredOverStreamResolution() {
        val wav = AudioFixture.toneWavFile()
        coEvery { downloadRepository.getDownloadInfo("yt_dQw4w9WgXcQ") } returns
            DownloadInfo(filePath = wav.absolutePath, format = AudioFormat.MP3_320)
        coEvery { youtubeRepository.getStreamUrl(any()) } throws AssertionError("must not resolve when downloaded")

        viewModel.playTrack(FixtureTracks.youtubeTrack())
        idle()

        TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_READY)
        assertThat(player.currentMediaItem?.localConfiguration?.uri?.scheme).isEqualTo("file")
    }

    @Test
    fun bandcampTrack_playsItsMp3StreamUrl() {
        viewModel.playTrack(FixtureTracks.bandcampTrack())
        idle()

        TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_READY)
        assertThat(playbackManager.playbackState.value).isEqualTo(Player.STATE_READY)
        assertThat(player.playWhenReady).isTrue()
    }

    @Test
    fun playAlbum_startsAtRequestedIndex() {
        val tracks = listOf(
            FixtureTracks.localTrack(id = "a1"),
            FixtureTracks.localTrack(id = "a2"),
            FixtureTracks.localTrack(id = "a3"),
        )
        viewModel.playAlbum(tracks, 1)
        idle()

        TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_READY)
        assertThat(queueManager.currentTrack.value?.id).isEqualTo("a2")
        assertThat(queueManager.queue.value).hasSize(3)
    }
}
