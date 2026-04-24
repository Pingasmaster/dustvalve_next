package com.dustvalve.next.android.ui.screens.detail

import com.dustvalve.next.android.data.local.db.DustvalveNextDatabase
import com.dustvalve.next.android.data.local.db.dao.FavoriteDao
import com.dustvalve.next.android.data.local.db.dao.PlaylistDao
import com.dustvalve.next.android.data.local.db.dao.TrackDao
import com.dustvalve.next.android.domain.model.MusicCollection
import com.dustvalve.next.android.domain.model.MusicProvider
import com.dustvalve.next.android.domain.model.Track
import com.dustvalve.next.android.domain.model.TrackSource
import com.dustvalve.next.android.domain.repository.DownloadRepository
import com.dustvalve.next.android.domain.repository.MusicSource
import com.dustvalve.next.android.domain.repository.MusicSourceRegistry
import com.dustvalve.next.android.domain.repository.PlaylistRepository
import com.dustvalve.next.android.domain.repository.SourceConcept
import com.dustvalve.next.android.domain.repository.UnsupportedSourceOperation
import com.dustvalve.next.android.domain.usecase.DownloadAlbumUseCase
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Regression coverage for the unified [CollectionDetailViewModel]. Replaces
 * the deleted `YouTubePlaylistDetailViewModelTest`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CollectionDetailViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val sources = mockk<MusicSourceRegistry>()
    private val playlistRepository = mockk<PlaylistRepository>(relaxed = true)
    private val trackDao = mockk<TrackDao>(relaxed = true)
    private val playlistDao = mockk<PlaylistDao>(relaxed = true)
    private val favoriteDao = mockk<FavoriteDao>(relaxed = true)
    private val database = mockk<DustvalveNextDatabase>(relaxed = true)
    private val downloadRepository = mockk<DownloadRepository>()
    private val downloadAlbumUseCase = mockk<DownloadAlbumUseCase>(relaxed = true)

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { downloadRepository.getDownloadedTrackIds() } returns flowOf(emptyList())
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test fun `load fetches collection from source and exposes tracks`() = runTest(dispatcher) {
        val url = "https://youtube.com/playlist?list=PL1"
        val source = sourceWith("youtube", setOf(SourceConcept.COLLECTION))
        coEvery { source.getCollection(url, null) } returns MusicCollection(
            id = url, url = url, name = "Chill Mix", owner = "",
            coverUrl = "cover.jpg",
            tracks = listOf(track("yt_1"), track("yt_2")),
            continuation = null, hasMore = false,
        )
        every { sources["youtube"] } returns source
        coEvery { favoriteDao.isFavorite(url) } returns false
        coEvery { playlistDao.getPlaylistByName("Chill Mix") } returns null

        val vm = newVm()
        vm.load(sourceId = "youtube", url = url, nameHint = "Chill Mix")
        advanceUntilIdle()

        val state = vm.uiState.value
        assertThat(state.isLoading).isFalse()
        assertThat(state.name).isEqualTo("Chill Mix")
        assertThat(state.coverUrl).isEqualTo("cover.jpg")
        assertThat(state.tracks).hasSize(2)
        assertThat(state.isImported).isFalse()
        assertThat(state.isFavorite).isFalse()
    }

    @Test fun `load surfaces error for unknown sourceId`() = runTest(dispatcher) {
        every { sources["nope"] } returns null
        val vm = newVm()
        vm.load(sourceId = "nope", url = "https://x", nameHint = "N")
        advanceUntilIdle()

        val state = vm.uiState.value
        assertThat(state.isLoading).isFalse()
        assertThat(state.error).contains("Unknown source: nope")
    }

    @Test fun `load surfaces error when source lacks COLLECTION capability`() = runTest(dispatcher) {
        val source = sourceWith("bandcamp", setOf(SourceConcept.SEARCH, SourceConcept.ARTIST))
        every { sources["bandcamp"] } returns source

        val vm = newVm()
        vm.load(sourceId = "bandcamp", url = "https://x", nameHint = "N")
        advanceUntilIdle()

        val state = vm.uiState.value
        assertThat(state.isLoading).isFalse()
        assertThat(state.error).contains("does not expose collections")
    }

    @Test fun `load surfaces message from UnsupportedSourceOperation thrown by getCollection`() = runTest(dispatcher) {
        val source = sourceWith("youtube", setOf(SourceConcept.COLLECTION))
        coEvery { source.getCollection(any(), any()) } throws
            UnsupportedSourceOperation("youtube", SourceConcept.COLLECTION)
        every { sources["youtube"] } returns source

        val vm = newVm()
        vm.load(sourceId = "youtube", url = "https://x", nameHint = "N")
        advanceUntilIdle()

        val state = vm.uiState.value
        assertThat(state.isLoading).isFalse()
        assertThat(state.error).isNotNull()
    }

    @Test fun `load marks isImported when a playlist with the same name exists`() = runTest(dispatcher) {
        val url = "https://youtube.com/playlist?list=PL1"
        val source = sourceWith("youtube", setOf(SourceConcept.COLLECTION))
        coEvery { source.getCollection(url, null) } returns MusicCollection(
            id = url, url = url, name = "My Mix", owner = "",
            coverUrl = null, tracks = listOf(track("a")), continuation = null, hasMore = false,
        )
        every { sources["youtube"] } returns source
        coEvery { favoriteDao.isFavorite(url) } returns true
        val existing = mockk<com.dustvalve.next.android.data.local.db.entity.PlaylistEntity>()
        every { existing.id } returns "local_playlist_42"
        coEvery { playlistDao.getPlaylistByName("My Mix") } returns existing

        val vm = newVm()
        vm.load(sourceId = "youtube", url = url, nameHint = "My Mix")
        advanceUntilIdle()

        val state = vm.uiState.value
        assertThat(state.isImported).isTrue()
        assertThat(state.importedPlaylistId).isEqualTo("local_playlist_42")
        assertThat(state.isFavorite).isTrue()
    }

    // --- helpers ------------------------------------------------------------

    private fun newVm() = CollectionDetailViewModel(
        sources = sources,
        playlistRepository = playlistRepository,
        trackDao = trackDao,
        playlistDao = playlistDao,
        favoriteDao = favoriteDao,
        database = database,
        downloadRepository = downloadRepository,
        downloadAlbumUseCase = downloadAlbumUseCase,
    )

    private fun sourceWith(id: String, capabilities: Set<SourceConcept>): MusicSource {
        val s = mockk<MusicSource>(relaxed = true)
        every { s.id } returns id
        every { s.provider } returns MusicProvider.YOUTUBE
        every { s.capabilities } returns capabilities
        return s
    }

    private fun track(id: String) = Track(
        id = id, albumId = "", title = id, artist = "", trackNumber = 0,
        duration = 0f, streamUrl = null, artUrl = "", albumTitle = "",
        source = TrackSource.YOUTUBE,
    )
}
