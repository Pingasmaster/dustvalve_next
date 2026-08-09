package com.dustvalve.next.android.ui.screens.detail

import com.dustvalve.next.android.R
import com.dustvalve.next.android.domain.model.FavoriteType
import com.dustvalve.next.android.domain.model.MusicCollection
import com.dustvalve.next.android.domain.model.Playlist
import com.dustvalve.next.android.domain.model.Track
import com.dustvalve.next.android.domain.model.TrackSource
import com.dustvalve.next.android.domain.repository.DownloadRepository
import com.dustvalve.next.android.domain.repository.FavoriteRepository
import com.dustvalve.next.android.domain.repository.MusicSource
import com.dustvalve.next.android.domain.repository.MusicSourceRegistry
import com.dustvalve.next.android.domain.repository.PlaylistRepository
import com.dustvalve.next.android.domain.repository.SourceConcept
import com.dustvalve.next.android.domain.repository.UnsupportedSourceOperation
import com.dustvalve.next.android.domain.usecase.DownloadAlbumUseCase
import com.dustvalve.next.android.domain.usecase.ExpandSourceTracksUseCase
import com.dustvalve.next.android.download.DownloadController
import com.dustvalve.next.android.util.UiText
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
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
    private val favoriteRepository = mockk<FavoriteRepository>(relaxed = true)
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
        coEvery { favoriteRepository.isFavorite(url, FavoriteType.YOUTUBE_PLAYLIST) } returns false
        coEvery { playlistRepository.getPlaylistIdForSourceUrl(url) } returns null

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

    @Test fun `load keeps coverHint when source returns no coverUrl`() = runTest(dispatcher) {
        val url = "https://youtube.com/playlist?list=PL1"
        val source = sourceWith("youtube", setOf(SourceConcept.COLLECTION))
        coEvery { source.getCollection(url, null) } returns MusicCollection(
            id = url,
            url = url,
            name = "Chill Mix",
            owner = "",
            coverUrl = null,
            tracks = listOf(track("yt_1").copy(artUrl = "")),
            continuation = null,
            hasMore = false,
        )
        every { sources["youtube"] } returns source
        coEvery { favoriteRepository.isFavorite(url, FavoriteType.YOUTUBE_PLAYLIST) } returns false
        coEvery { playlistRepository.getPlaylistIdForSourceUrl(url) } returns null

        val vm = newVm()
        vm.load(
            sourceId = "youtube",
            url = url,
            nameHint = "Chill Mix",
            coverHint = "https://hint/cover.jpg",
        )
        advanceUntilIdle()

        assertThat(vm.uiState.value.coverUrl).isEqualTo("https://hint/cover.jpg")
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

    @Test fun `load marks isImported from durable sourceUrl mapping not by name`() = runTest(dispatcher) {
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
        coEvery { favoriteRepository.isFavorite(url, FavoriteType.YOUTUBE_PLAYLIST) } returns true
        // Same-named playlist may exist, but import state comes only from mapping.
        coEvery { playlistRepository.playlistExistsByName("My Mix") } returns true
        coEvery { playlistRepository.getPlaylistIdForSourceUrl(url) } returns "mapped_pl"

        val vm = newVm()
        vm.load(sourceId = "youtube", url = url, nameHint = "My Mix")
        advanceUntilIdle()

        val state = vm.uiState.value
        assertThat(state.isImported).isTrue()
        assertThat(state.importedPlaylistId).isEqualTo("mapped_pl")
        assertThat(state.isFavorite).isTrue()
        coVerify(exactly = 0) { playlistRepository.playlistExistsByName(any()) }
    }

    @Test fun `name collision alone does not mark isImported`() = runTest(dispatcher) {
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
        coEvery { favoriteRepository.isFavorite(url, FavoriteType.YOUTUBE_PLAYLIST) } returns true
        coEvery { playlistRepository.playlistExistsByName("My Mix") } returns true
        coEvery { playlistRepository.getPlaylistIdForSourceUrl(url) } returns null

        val vm = newVm()
        vm.load(sourceId = "youtube", url = url, nameHint = "My Mix")
        advanceUntilIdle()

        assertThat(vm.uiState.value.isImported).isFalse()
        assertThat(vm.uiState.value.importedPlaylistId).isNull()
    }

    @Test fun `unfavoriting never deletes a same-named user playlist without mapping`() = runTest(dispatcher) {
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
        coEvery { favoriteRepository.isFavorite(url, FavoriteType.YOUTUBE_PLAYLIST) } returns true
        coEvery { playlistRepository.getPlaylistIdForSourceUrl(url) } returns null

        val vm = newVm()
        vm.load(sourceId = "youtube", url = url, nameHint = "My Mix")
        advanceUntilIdle()

        vm.toggleFavorite()
        advanceUntilIdle()

        coVerify(exactly = 0) { playlistRepository.deletePlaylist(any()) }
        coVerify { favoriteRepository.remove(url, FavoriteType.YOUTUBE_PLAYLIST) }
        assertThat(vm.uiState.value.isFavorite).isFalse()
    }

    @Test fun `playExpanded drains remaining pages before playing`() = runTest(dispatcher) {
        val url = "https://youtube.com/playlist?list=MIX"
        val source = sourceWith("youtube", setOf(SourceConcept.COLLECTION))
        coEvery { source.getCollection(url, null) } returns MusicCollection(
            id = url,
            url = url,
            name = "Infinite Mix",
            owner = "",
            coverUrl = null,
            tracks = listOf(track("yt_1"), track("yt_2")),
            continuation = "page2",
            hasMore = true,
        )
        coEvery { source.getCollection(url, "page2") } returns MusicCollection(
            id = url,
            url = url,
            name = "Infinite Mix",
            owner = "",
            coverUrl = null,
            tracks = listOf(track("yt_3"), track("yt_4"), track("yt_5")),
            continuation = null,
            hasMore = false,
        )
        every { sources["youtube"] } returns source
        coEvery { favoriteRepository.isFavorite(url, FavoriteType.YOUTUBE_PLAYLIST) } returns false
        coEvery { playlistRepository.getPlaylistIdForSourceUrl(url) } returns null

        val vm = newVm()
        vm.load(sourceId = "youtube", url = url, nameHint = "Infinite Mix")
        advanceUntilIdle()
        assertThat(vm.uiState.value.tracks).hasSize(2)
        assertThat(vm.uiState.value.hasMore).isTrue()

        var played: List<Track>? = null
        var start = -1
        vm.playExpanded(1) { tracks, index ->
            played = tracks
            start = index
        }
        advanceUntilIdle()

        assertThat(played!!.map { it.id }).containsExactly(
            "yt_1",
            "yt_2",
            "yt_3",
            "yt_4",
            "yt_5",
        ).inOrder()
        assertThat(start).isEqualTo(1)
        assertThat(vm.uiState.value.tracks).hasSize(5)
        assertThat(vm.uiState.value.hasMore).isFalse()
    }

    @Test fun `unfavoriting deletes the durably mapped imported playlist`() = runTest(dispatcher) {
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
        coEvery { favoriteRepository.isFavorite(url, FavoriteType.YOUTUBE_PLAYLIST) } returns false
        coEvery { playlistRepository.getPlaylistIdForSourceUrl(url) } returns null
        coEvery {
            playlistRepository.importTracksAsPlaylist(
                name = "My Mix",
                tracks = any(),
                favoriteId = url,
                favoriteType = FavoriteType.YOUTUBE_PLAYLIST,
                sourceUrl = url,
            )
        } returns Playlist(id = "imported_1", name = "My Mix", sourceUrl = url)

        val vm = newVm()
        vm.load(sourceId = "youtube", url = url, nameHint = "My Mix")
        advanceUntilIdle()

        // Favoriting imports + favorites in one transaction (no separate add).
        vm.toggleFavorite()
        advanceUntilIdle()
        assertThat(vm.uiState.value.importedPlaylistId).isEqualTo("imported_1")
        assertThat(vm.uiState.value.isImported).isTrue()

        vm.toggleFavorite()
        advanceUntilIdle()

        coVerify(exactly = 0) { favoriteRepository.add(any(), any()) }
        coVerify(exactly = 1) {
            playlistRepository.importTracksAsPlaylist(
                name = "My Mix",
                tracks = any(),
                favoriteId = url,
                favoriteType = FavoriteType.YOUTUBE_PLAYLIST,
                sourceUrl = url,
            )
        }
        coVerify(exactly = 1) { playlistRepository.deletePlaylist("imported_1") }
        coVerify { favoriteRepository.remove(url, FavoriteType.YOUTUBE_PLAYLIST) }
        assertThat(vm.uiState.value.importedPlaylistId).isNull()
        assertThat(vm.uiState.value.isImported).isFalse()
    }

    // --- helpers ------------------------------------------------------------

    private fun newVm() = CollectionDetailViewModel(
        sources = sources,
        playlistRepository = playlistRepository,
        favoriteRepository = favoriteRepository,
        downloadRepository = downloadRepository,
        downloadAlbumUseCase = downloadAlbumUseCase,
        downloadController = downloadController,
        expandSourceTracks = ExpandSourceTracksUseCase(),
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
