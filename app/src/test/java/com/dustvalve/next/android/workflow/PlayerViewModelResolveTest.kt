package com.dustvalve.next.android.workflow

import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.test.utils.FakeClock
import androidx.media3.test.utils.TestExoPlayerBuilder
import androidx.media3.test.utils.robolectric.RobolectricUtil
import androidx.media3.test.utils.robolectric.TestPlayerRunHelper
import androidx.test.core.app.ApplicationProvider
import com.dustvalve.next.android.data.local.datastore.SettingsDataStore
import com.dustvalve.next.android.data.local.db.dao.FavoriteDao
import com.dustvalve.next.android.data.remote.DustvalveStreamResolver
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
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
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
    private val dustvalveStreamResolver = mockk<DustvalveStreamResolver>()
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
            dustvalveStreamResolver,
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

    // --- H3: never hand a watch-page URL to ExoPlayer, skip path ---

    @Test
    fun skipNext_toUnresolvedYouTubeEntry_resolvesBeforeHandingToExoPlayer() {
        val resolved = AudioFixture.toneWavUri()
        coEvery { youtubeRepository.getStreamUrl(any()) } returns resolved

        // Install the queue directly so the second entry still carries its
        // watch-page URL (exactly the background-resolution window state).
        playbackManager.playQueue(listOf(FixtureTracks.localTrack(), FixtureTracks.youtubeTrack()), 0)
        idle()
        TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_READY)

        playbackManager.skipNext()
        idle()

        TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_READY)
        val playingUri = player.currentMediaItem?.localConfiguration?.uri.toString()
        assertThat(playingUri).isEqualTo(resolved)
        assertThat(playingUri).doesNotContain("watch?v=")
        // The queue entry was patched in place with the resolved URL.
        assertThat(queueManager.queue.value[1].streamUrl).isEqualTo(resolved)
    }

    // --- H2: expired stream URLs are re-resolved once and playback retried ---

    @Test
    fun expiredStreamUrl_ioError_autoReResolvesOnce_andRetriesPlayback() {
        val goodUri = AudioFixture.toneWavUri()
        // First resolution hands out a URL that dies with an IO error (stands
        // in for an expired CDN link); the re-resolve returns a live one.
        coEvery { youtubeRepository.getStreamUrl(any()) } returnsMany listOf("file:///does/not/exist.mp3", goodUri)

        viewModel.playTrack(FixtureTracks.youtubeTrack())
        idle()

        // Pump until the one-shot recovery has re-resolved and reached READY
        // on the fresh URL (the intermediate error and the auto-retry can land
        // anywhere in between, so wait on the final outcome only).
        RobolectricUtil.runMainLooperUntil {
            player.playbackState == Player.STATE_READY &&
                player.currentMediaItem?.localConfiguration?.uri.toString() == goodUri
        }

        assertThat(queueManager.queue.value[0].streamUrl).isEqualTo(goodUri)
        assertThat(playbackManager.playbackError.value).isNull()
        coVerify(exactly = 2) { youtubeRepository.getStreamUrl(any()) }
    }

    @Test
    fun expiredStreamUrl_reResolveAlsoDead_surfacesErrorWithoutLooping() {
        // First call = initial resolution, second = the single auto-retry.
        val deadUrls = listOf("file:///does/not/exist.mp3", "file:///also/does/not/exist.mp3")
        coEvery { youtubeRepository.getStreamUrl(any()) } returnsMany deadUrls

        val collected = mutableListOf<com.dustvalve.next.android.ui.screens.player.PlayerUiState>()
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main.immediate)
        val job = scope.launch { viewModel.uiState.collect { collected.add(it) } }

        viewModel.playTrack(FixtureTracks.youtubeTrack())
        idle()

        // First failure triggers the single auto-retry; the retry's URL is
        // dead too, so the ordinary error UI must surface - and only then.
        RobolectricUtil.runMainLooperUntil {
            collected.lastOrNull()?.snackbarMessage != null
        }

        // Exactly one automatic re-resolve (initial + retry = 2 calls): no
        // retry loop on a permanently dead track.
        coVerify(exactly = 2) { youtubeRepository.getStreamUrl(any()) }
        job.cancel()
    }

    // --- H5: a failed direct tap must not destroy the previous queue ---

    @Test
    fun playTrack_resolutionFails_leavesPreviousQueueAndPlayerIntact() {
        viewModel.playTrackInList(listOf(FixtureTracks.localTrack()), 0)
        idle()
        TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_READY)
        val playingBefore = player.currentMediaItem?.localConfiguration?.uri.toString()

        coEvery { youtubeRepository.getStreamUrl(any()) } throws IllegalStateException("HTTP 403")
        viewModel.playTrack(FixtureTracks.youtubeTrack())
        idle()

        // Queue untouched, player still holds the previous track.
        assertThat(queueManager.queue.value.map { it.id }).containsExactly("local_ms_1")
        assertThat(queueManager.currentTrack.value?.id).isEqualTo("local_ms_1")
        assertThat(player.currentMediaItem?.localConfiguration?.uri.toString()).isEqualTo(playingBefore)
    }

    // --- H4: queue edits made during background resolution survive ---

    @Test
    fun queueEdit_duringBackgroundResolution_survivesAndPatchLandsById() {
        val gate = CompletableDeferred<String>()
        coEvery { youtubeRepository.getStreamUrl(any()) } coAnswers { gate.await() }

        viewModel.playTrackInList(listOf(FixtureTracks.localTrack(), FixtureTracks.youtubeTrack()), 0)
        idle()
        TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_READY)

        // Edit the queue while the YouTube entry is still resolving.
        viewModel.playNext(FixtureTracks.bandcampTrack(id = "bc_inserted"))
        idle()

        gate.complete(AudioFixture.toneWavUri())
        idle()

        // Old behavior: setQueue(originalListCopy) reverted the insert. Now the
        // edit survives and the resolved URL is patched onto the right entry.
        assertThat(queueManager.queue.value.map { it.id })
            .containsExactly("local_ms_1", "bc_inserted", "yt_dQw4w9WgXcQ").inOrder()
        assertThat(queueManager.queue.value.last().streamUrl).doesNotContain("watch?v=")
    }

    // --- L20: rapid double play must not lose the loading indicator ---

    @Test
    fun rapidDoublePlay_cancelledJobDoesNotClearNewJobsLoadingState() {
        fun ytTrack(videoId: String) = FixtureTracks.youtubeTrack(id = "yt_$videoId")
            .copy(streamUrl = "https://www.youtube.com/watch?v=$videoId")

        // The gates suspend inside non-immediate Dispatchers.Main so that
        // cancelling job A delivers its finally-block on the NEXT looper drain
        // (like the production IO-dispatcher hop in getStreamUrl) - i.e. AFTER
        // job B already set isLoadingTrack. That deferred finally is exactly
        // what used to wrongly clear B's loading indicator.
        val gateA = CompletableDeferred<String>()
        val gateB = CompletableDeferred<String>()
        coEvery { youtubeRepository.getStreamUrl("https://www.youtube.com/watch?v=aaaaaaaaaaa") } coAnswers {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { gateA.await() }
        }
        coEvery { youtubeRepository.getStreamUrl("https://www.youtube.com/watch?v=bbbbbbbbbbb") } coAnswers {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { gateB.await() }
        }

        val collected = mutableListOf<com.dustvalve.next.android.ui.screens.player.PlayerUiState>()
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main.immediate)
        val job = scope.launch { viewModel.uiState.collect { collected.add(it) } }

        viewModel.playTrack(ytTrack("aaaaaaaaaaa"))
        idle()
        viewModel.playTrack(ytTrack("bbbbbbbbbbb"))
        idle()

        // The cancelled first job's finally must not clear the loading flag
        // the second job set.
        assertThat(collected.last().isLoadingTrack).isTrue()

        gateB.complete(AudioFixture.toneWavUri())
        idle()

        assertThat(collected.last().isLoadingTrack).isFalse()
        assertThat(queueManager.currentTrack.value?.id).isEqualTo("yt_bbbbbbbbbbb")
        job.cancel()
    }
}
