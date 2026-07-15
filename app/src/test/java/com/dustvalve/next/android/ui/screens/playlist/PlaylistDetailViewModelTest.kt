package com.dustvalve.next.android.ui.screens.playlist

import com.dustvalve.next.android.data.local.datastore.SettingsDataStore
import com.dustvalve.next.android.domain.model.Playlist
import com.dustvalve.next.android.domain.model.Track
import com.dustvalve.next.android.domain.model.TrackSource
import com.dustvalve.next.android.domain.repository.DownloadRepository
import com.dustvalve.next.android.domain.repository.PlaylistRepository
import com.dustvalve.next.android.download.DownloadController
import com.dustvalve.next.android.util.UiText
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistDetailViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var playlistRepo: PlaylistRepository
    private lateinit var downloadController: DownloadController
    private lateinit var downloadRepo: DownloadRepository
    private lateinit var settings: SettingsDataStore

    private val playlist = Playlist(
        id = "p1",
        name = "Road Trip",
        isSystem = false,
        systemType = null,
        trackCount = 2,
    )

    private fun track(id: String, source: TrackSource = TrackSource.BANDCAMP) = Track(
        id = id, albumId = "al", title = id, artist = "A", trackNumber = 1,
        duration = 60f, streamUrl = "https://s/$id", artUrl = "", albumTitle = "Alb",
        source = source,
    )

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        playlistRepo = mockk(relaxed = true)
        downloadController = mockk(relaxed = true)
        downloadRepo = mockk(relaxed = true)
        settings = mockk(relaxed = true)
        every { settings.autoDownloadFavorites } returns flowOf(false)
        every { downloadRepo.getDownloadedTrackIds() } returns flowOf(emptyList())
        every { playlistRepo.getPlaylistById("p1") } returns flowOf(playlist)
        every { playlistRepo.getTracksInPlaylist("p1") } returns flowOf(listOf(track("t1"), track("t2")))
    }

    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm() = PlaylistDetailViewModel(playlistRepo, downloadController, downloadRepo, settings)

    @Test fun `loadPlaylist publishes playlist and tracks`() = runTest(dispatcher) {
        val vm = vm()
        vm.loadPlaylist("p1")
        advanceUntilIdle()

        val s = vm.uiState.value
        assertThat(s.playlist).isEqualTo(playlist)
        assertThat(s.tracks.map { it.id }).containsExactly("t1", "t2").inOrder()
        assertThat(s.isLoading).isFalse()
        assertThat(s.error).isNull()
    }

    @Test fun `loadPlaylist with the same id is idempotent`() = runTest(dispatcher) {
        val vm = vm()
        vm.loadPlaylist("p1")
        advanceUntilIdle()
        vm.loadPlaylist("p1")
        advanceUntilIdle()
        coVerify(exactly = 1) { playlistRepo.getPlaylistById("p1") }
    }

    @Test fun `load failure surfaces the error`() = runTest(dispatcher) {
        every { playlistRepo.getPlaylistById("p1") } returns flow { throw IOException("db broken") }
        val vm = vm()
        vm.loadPlaylist("p1")
        advanceUntilIdle()

        assertThat(vm.uiState.value.error).isEqualTo(UiText.DynamicString("db broken"))
        assertThat(vm.uiState.value.isLoading).isFalse()
    }

    @Test fun `downloaded track ids are reflected in state`() = runTest(dispatcher) {
        every { downloadRepo.getDownloadedTrackIds() } returns flowOf(listOf("t1", "t9"))
        val vm = vm()
        advanceUntilIdle()
        assertThat(vm.uiState.value.downloadedTrackIds).containsExactly("t1", "t9")
    }

    @Test fun `downloadAll downloads only non-local tracks and shows success snackbar`() = runTest(dispatcher) {
        every { playlistRepo.getTracksInPlaylist("p1") } returns
            flowOf(listOf(track("t1"), track("local1", source = TrackSource.LOCAL)))
        coEvery { settings.getAutoDownloadFutureContentSync() } returns false
        val vm = vm()
        vm.loadPlaylist("p1")
        advanceUntilIdle()

        vm.downloadAll()
        advanceUntilIdle()

        coVerify(exactly = 1) {
            downloadController.downloadPlaylistBlocking(
                label = "Road Trip",
                tracks = match { it.size == 1 && it.single().id == "t1" },
            )
        }
        val s = vm.uiState.value
        assertThat(s.isDownloading).isFalse()
        assertThat(s.snackbarMessage).isInstanceOf(UiText.StringResource::class.java)
        assertThat(s.isSnackbarError).isFalse()
    }

    @Test fun `downloadAll enables auto-download when the global setting is on`() = runTest(dispatcher) {
        coEvery { settings.getAutoDownloadFutureContentSync() } returns true
        val vm = vm()
        vm.loadPlaylist("p1")
        advanceUntilIdle()

        vm.downloadAll()
        advanceUntilIdle()

        coVerify(exactly = 1) { playlistRepo.setAutoDownload("p1", true) }
    }

    @Test fun `downloadAll failure shows error snackbar and records retry action`() = runTest(dispatcher) {
        coEvery { downloadController.downloadPlaylistBlocking(any(), any()) } throws IOException("offline")
        val vm = vm()
        vm.loadPlaylist("p1")
        advanceUntilIdle()

        vm.downloadAll()
        advanceUntilIdle()

        val s = vm.uiState.value
        assertThat(s.isDownloading).isFalse()
        assertThat(s.isSnackbarError).isTrue()
        assertThat((s.snackbarMessage as UiText.DynamicString).value).isEqualTo("offline")
        assertThat(vm.retryAction).isNotNull()

        vm.clearSnackbar()
        assertThat(vm.uiState.value.snackbarMessage).isNull()
    }

    @Test fun `downloadAll with only local tracks is a no-op`() = runTest(dispatcher) {
        every { playlistRepo.getTracksInPlaylist("p1") } returns
            flowOf(listOf(track("local1", source = TrackSource.LOCAL)))
        val vm = vm()
        vm.loadPlaylist("p1")
        advanceUntilIdle()

        vm.downloadAll()
        advanceUntilIdle()

        coVerify(exactly = 0) { downloadController.downloadPlaylistBlocking(any(), any()) }
        assertThat(vm.uiState.value.isDownloading).isFalse()
    }

    @Test fun `removeTrack and moveTrack delegate with the loaded playlist id`() = runTest(dispatcher) {
        val vm = vm()
        vm.loadPlaylist("p1")
        advanceUntilIdle()

        vm.removeTrack("t1")
        vm.moveTrack(0, 1)
        advanceUntilIdle()

        coVerify { playlistRepo.removeTrackFromPlaylist("p1", "t1") }
        coVerify { playlistRepo.moveTrackInPlaylist("p1", 0, 1) }
    }

    @Test fun `removeTrack failure surfaces the error and clearError resets it`() = runTest(dispatcher) {
        coEvery { playlistRepo.removeTrackFromPlaylist(any(), any()) } throws IOException("nope")
        val vm = vm()
        vm.loadPlaylist("p1")
        advanceUntilIdle()

        vm.removeTrack("t1")
        advanceUntilIdle()
        assertThat(vm.uiState.value.error).isEqualTo(UiText.DynamicString("nope"))

        vm.clearError()
        assertThat(vm.uiState.value.error).isNull()
    }
}
