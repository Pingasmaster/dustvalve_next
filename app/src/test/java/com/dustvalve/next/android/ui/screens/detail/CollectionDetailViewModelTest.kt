package com.dustvalve.next.android.ui.screens.detail

import androidx.room.withTransaction
import com.dustvalve.next.android.R
import com.dustvalve.next.android.data.local.db.DustvalveNextDatabase
import com.dustvalve.next.android.data.local.db.dao.FavoriteDao
import com.dustvalve.next.android.data.local.db.dao.PlaylistDao
import com.dustvalve.next.android.data.local.db.dao.TrackDao
import com.dustvalve.next.android.domain.model.MusicCollection
import com.dustvalve.next.android.domain.model.MusicProvider
import com.dustvalve.next.android.domain.model.Playlist
import com.dustvalve.next.android.domain.model.Track
import com.dustvalve.next.android.domain.model.TrackSource
import com.dustvalve.next.android.domain.repository.DownloadRepository
import com.dustvalve.next.android.domain.repository.MusicSource
import com.dustvalve.next.android.domain.repository.MusicSourceRegistry
import com.dustvalve.next.android.domain.repository.PlaylistRepository
import com.dustvalve.next.android.domain.repository.SourceConcept
import com.dustvalve.next.android.domain.repository.UnsupportedSourceOperation
import com.dustvalve.next.android.domain.usecase.DownloadAlbumUseCase
import com.dustvalve.next.android.download.DownloadController
import com.dustvalve.next.android.util.UiText
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
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
    private val downloadController = mockk<DownloadController>(relaxed = true)

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
            id = url,
            url = url,
            name = "Chill Mix",
            owner = "",
            coverUrl = "cover.jpg",
            tracks = listOf(track("yt_1"), track("yt_2")),
            continuation = null,
            hasMore = false,
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
        val error = state.error as UiText.StringResource
        assertThat(error.resId).isEqualTo(R.string.error_unknown_source)
        assertThat(error.args).containsExactly("nope")
    }

    @Test fun `load surfaces error when source lacks COLLECTION capability`() = runTest(dispatcher) {
        val source = sourceWith("bandcamp", setOf(SourceConcept.SEARCH, SourceConcept.ARTIST))
        every { sources["bandcamp"] } returns source

        val vm = newVm()
        vm.load(sourceId = "bandcamp", url = "https://x", nameHint = "N")
        advanceUntilIdle()

        val state = vm.uiState.value
        assertThat(state.isLoading).isFalse()
        assertThat((state.error as UiText.StringResource).resId)
            .isEqualTo(R.string.error_source_no_collections)
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

    @Test fun `load marks isImported by name for display but never captures the foreign playlist id`() = runTest(dispatcher) {
        val url = "https://youtube.com/playlist?list=PL1"
        val source = sourceWith("youtube", setOf(SourceConcept.COLLECTION))
        coEvery { source.getCollection(url, null) } returns MusicCollection(
            id = url,
            url = url,
            name = "My Mix",
            owner = "",
            coverUrl = null,
            tracks = listOf(track("a")),
            continuation = null,
            hasMore = false,
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
        // Name matching is display-only: the id must NOT be captured, because
        // importedPlaylistId authorizes deletion in toggleFavorite.
        assertThat(state.importedPlaylistId).isNull()
        assertThat(state.isFavorite).isTrue()
    }

    @Test fun `unfavoriting never deletes a same-named user playlist`() = runTest(dispatcher) {
        val url = "https://youtube.com/playlist?list=PL1"
        val source = sourceWith("youtube", setOf(SourceConcept.COLLECTION))
        coEvery { source.getCollection(url, null) } returns MusicCollection(
            id = url,
            url = url,
            name = "My Mix",
            owner = "",
            coverUrl = null,
            tracks = listOf(track("a")),
            continuation = null,
            hasMore = false,
        )
        every { sources["youtube"] } returns source
        coEvery { favoriteDao.isFavorite(url) } returns true
        val userPlaylist = mockk<com.dustvalve.next.android.data.local.db.entity.PlaylistEntity>()
        every { userPlaylist.id } returns "users_own_playlist"
        coEvery { playlistDao.getPlaylistByName("My Mix") } returns userPlaylist

        val vm = newVm()
        vm.load(sourceId = "youtube", url = url, nameHint = "My Mix")
        advanceUntilIdle()

        // Not imported this session (importedPlaylistId == null): unfavoriting
        // must not resolve a deletion target by name.
        vm.toggleFavorite()
        advanceUntilIdle()

        coVerify(exactly = 0) { playlistRepository.deletePlaylist(any()) }
        assertThat(vm.uiState.value.isFavorite).isFalse()
    }

    @Test fun `unfavoriting deletes only the playlist imported this session`() = runTest(dispatcher) {
        mockkStatic("androidx.room.RoomDatabaseKt")
        try {
            coEvery { database.withTransaction(any<suspend () -> Any?>()) } coAnswers {
                firstArg<suspend () -> Any?>().invoke()
            }
            val url = "https://youtube.com/playlist?list=PL1"
            val source = sourceWith("youtube", setOf(SourceConcept.COLLECTION))
            coEvery { source.getCollection(url, null) } returns MusicCollection(
                id = url,
                url = url,
                name = "My Mix",
                owner = "",
                coverUrl = null,
                tracks = listOf(track("a")),
                continuation = null,
                hasMore = false,
            )
            every { sources["youtube"] } returns source
            coEvery { favoriteDao.isFavorite(url) } returns false
            coEvery { playlistDao.getPlaylistByName("My Mix") } returns null
            coEvery { playlistRepository.createPlaylist("My Mix") } returns Playlist(id = "imported_1", name = "My Mix")

            val vm = newVm()
            vm.load(sourceId = "youtube", url = url, nameHint = "My Mix")
            advanceUntilIdle()

            vm.importToLibrary()
            advanceUntilIdle()
            assertThat(vm.uiState.value.importedPlaylistId).isEqualTo("imported_1")

            // Favorite, then unfavorite: exactly the imported id is deleted.
            vm.toggleFavorite()
            advanceUntilIdle()
            vm.toggleFavorite()
            advanceUntilIdle()

            coVerify(exactly = 1) { playlistRepository.deletePlaylist("imported_1") }
            assertThat(vm.uiState.value.importedPlaylistId).isNull()
            assertThat(vm.uiState.value.isImported).isFalse()
        } finally {
            unmockkStatic("androidx.room.RoomDatabaseKt")
        }
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
        downloadController = downloadController,
    )

    private fun sourceWith(id: String, capabilities: Set<SourceConcept>): MusicSource {
        val s = mockk<MusicSource>(relaxed = true)
        every { s.id } returns id
        every { s.capabilities } returns capabilities
        return s
    }

    private fun track(id: String) = Track(
        id = id, albumId = "", title = id, artist = "", trackNumber = 0,
        duration = 0f, streamUrl = null, artUrl = "", albumTitle = "",
        source = TrackSource.YOUTUBE,
    )
}
