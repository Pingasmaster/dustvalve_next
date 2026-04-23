package com.dustvalve.next.android.ui.screens.settings

import app.cash.turbine.test
import com.dustvalve.next.android.cache.StorageTracker
import com.dustvalve.next.android.data.asset.AssetEvictionPolicy
import com.dustvalve.next.android.data.local.datastore.SettingsDataStore
import com.dustvalve.next.android.data.local.db.dao.RecentSearchDao
import com.dustvalve.next.android.update.AppUpdateService
import com.dustvalve.next.android.domain.model.AccountState
import com.dustvalve.next.android.domain.model.AudioFormat
import com.dustvalve.next.android.domain.model.CacheInfo
import com.dustvalve.next.android.domain.model.ExportableTrack
import com.dustvalve.next.android.domain.model.SpotifyAccountState
import com.dustvalve.next.android.domain.model.Track
import com.dustvalve.next.android.domain.model.TrackSource
import com.dustvalve.next.android.domain.model.YouTubeMusicAccountState
import com.dustvalve.next.android.domain.repository.AccountRepository
import com.dustvalve.next.android.domain.repository.DownloadRepository
import com.dustvalve.next.android.domain.repository.LocalMusicRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelExportTest {

    private val dispatcher = StandardTestDispatcher()

    private lateinit var accountRepository: AccountRepository
    private lateinit var storageTracker: StorageTracker
    private lateinit var assetEvictionPolicy: AssetEvictionPolicy
    private lateinit var settingsDataStore: SettingsDataStore
    private lateinit var localMusicRepository: LocalMusicRepository
    private lateinit var downloadRepository: DownloadRepository
    private lateinit var recentSearchDao: RecentSearchDao
    private lateinit var appUpdateService: AppUpdateService
    private lateinit var exportableFlow: MutableStateFlow<List<ExportableTrack>>

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)

        accountRepository = mockk(relaxed = true)
        every { accountRepository.getAccountState() } returns flowOf(AccountState())
        every { accountRepository.getYouTubeMusicAccountState() } returns flowOf(YouTubeMusicAccountState())
        every { accountRepository.getSpotifyAccountState() } returns flowOf(SpotifyAccountState())

        storageTracker = mockk(relaxed = true)
        every { storageTracker.getCacheInfo() } returns flowOf(CacheInfo(0, 0, 0, 0, 0, 0f))

        // Stub every settings flow getter the SettingsViewModel collects on init.
        settingsDataStore = mockk(relaxed = true) {
            every { themeMode } returns flowOf("system")
            every { dynamicColor } returns flowOf(true)
            every { storageLimit } returns flowOf(2L * 1024 * 1024 * 1024)
            every { autoDownloadCollection } returns flowOf(true)
            every { autoDownloadFutureContent } returns flowOf(false)
            every { downloadFormat } returns flowOf("flac")
            every { saveDataOnMetered } returns flowOf(true)
            every { progressiveDownload } returns flowOf(true)
            every { seamlessQualityUpgrade } returns flowOf(true)
            every { oledBlack } returns flowOf(false)
            every { albumArtTheme } returns flowOf(false)
            every { wavyProgressBar } returns flowOf(true)
            every { localMusicEnabled } returns flowOf(false)
            every { localMusicFolderUris } returns flowOf(emptyList())
            every { localMusicUseMediaStore } returns flowOf(true)
            every { bandcampEnabled } returns flowOf(true)
            every { youtubeEnabled } returns flowOf(true)
            every { spotifyEnabled } returns flowOf(false)
            every { showInlineVolumeSlider } returns flowOf(false)
            every { showVolumeButton } returns flowOf(false)
            every { searchHistoryEnabled } returns flowOf(true)
            every { youtubeDefaultSource } returns flowOf("youtube")
            every { albumCoverLongPressCarousel } returns flowOf(true)
            every { keepScreenOnInApp } returns flowOf(false)
            every { keepScreenOnWhilePlaying } returns flowOf(false)
        }

        assetEvictionPolicy = mockk(relaxed = true)
        localMusicRepository = mockk(relaxed = true)

        downloadRepository = mockk(relaxed = true)
        exportableFlow = MutableStateFlow(emptyList())
        every { downloadRepository.getExportableTracks() } returns exportableFlow

        recentSearchDao = mockk(relaxed = true)
        appUpdateService = mockk(relaxed = true)
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newViewModel(): SettingsViewModel = SettingsViewModel(
        accountRepository = accountRepository,
        storageTracker = storageTracker,
        assetEvictionPolicy = assetEvictionPolicy,
        settingsDataStore = settingsDataStore,
        localMusicRepository = localMusicRepository,
        downloadRepository = downloadRepository,
        recentSearchDao = recentSearchDao,
        appUpdateService = appUpdateService,
    )

    private fun makeTrack(
        id: String,
        source: TrackSource = TrackSource.BANDCAMP,
    ): Track = Track(
        id = id,
        albumId = "album_$id",
        title = "Title $id",
        artist = "Artist $id",
        trackNumber = 1,
        duration = 200f,
        streamUrl = "file:///tmp/$id.mp3",
        artUrl = "https://example.com/$id.jpg",
        albumTitle = "Album $id",
        source = source,
    )

    private fun makeExportable(
        id: String,
        format: AudioFormat = AudioFormat.FLAC,
        qualityLabel: String = "FLAC",
    ): ExportableTrack = ExportableTrack(
        track = makeTrack(id),
        format = format,
        sizeBytes = 4_000_000L,
        qualityLabel = qualityLabel,
    )

    @Test fun `exportSelectedDownloads forwards uri and ids to repository`() = runTest {
        coEvery {
            downloadRepository.exportDownloads(
                destinationUri = any(),
                trackIds = any(),
                onProgress = any(),
            )
        } returns 2

        val vm = newViewModel()
        advanceUntilIdle()

        val ids = setOf("a", "b")
        vm.exportSelectedDownloads("content://export", ids)
        advanceUntilIdle()

        val capturedUri = slot<String>()
        val capturedIds = slot<Set<String>>()
        coVerify {
            downloadRepository.exportDownloads(
                destinationUri = capture(capturedUri),
                trackIds = capture(capturedIds),
                onProgress = any(),
            )
        }
        assertThat(capturedUri.captured).isEqualTo("content://export")
        assertThat(capturedIds.captured).isEqualTo(ids)
    }

    @Test fun `exportSelectedDownloads with empty ids is a no-op`() = runTest {
        val vm = newViewModel()
        advanceUntilIdle()

        vm.exportSelectedDownloads("content://export", emptySet())
        advanceUntilIdle()

        coVerify(exactly = 0) {
            downloadRepository.exportDownloads(any(), any(), any())
        }
    }

    @Test fun `exportableTracks emits the repository flow`() = runTest {
        val vm = newViewModel()

        // Push values *before* subscribing — turbine will see the latest.
        val tracks = listOf(
            makeExportable("a", AudioFormat.FLAC, "FLAC"),
            makeExportable("b", AudioFormat.MP3_320, "320 kbps"),
        )
        exportableFlow.value = tracks
        advanceUntilIdle()

        vm.exportableTracks.test {
            // First emission is whatever value the StateFlow holds when we attach.
            var current = awaitItem()
            // Loop until the upstream value propagates (StateFlow conflates).
            while (current.size != 2) {
                current = awaitItem()
            }
            assertThat(current.map { it.track.id }).containsExactly("a", "b").inOrder()
            assertThat(current[0].format).isEqualTo(AudioFormat.FLAC)
            assertThat(current[1].qualityLabel).isEqualTo("320 kbps")
            cancelAndIgnoreRemainingEvents()
        }
    }
}
