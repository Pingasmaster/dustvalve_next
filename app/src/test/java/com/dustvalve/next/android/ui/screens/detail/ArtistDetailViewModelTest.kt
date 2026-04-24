package com.dustvalve.next.android.ui.screens.detail

import com.dustvalve.next.android.data.local.db.DustvalveNextDatabase
import com.dustvalve.next.android.data.local.db.dao.ArtistDao
import com.dustvalve.next.android.data.local.db.dao.FavoriteDao
import com.dustvalve.next.android.data.local.db.dao.TrackDao
import com.dustvalve.next.android.domain.model.Album
import com.dustvalve.next.android.domain.model.Artist
import com.dustvalve.next.android.domain.model.MusicCollection
import com.dustvalve.next.android.domain.model.MusicProvider
import com.dustvalve.next.android.domain.model.Track
import com.dustvalve.next.android.domain.model.TrackSource
import com.dustvalve.next.android.domain.repository.ArtistRepository
import com.dustvalve.next.android.domain.repository.DownloadRepository
import com.dustvalve.next.android.domain.repository.MusicSource
import com.dustvalve.next.android.domain.repository.MusicSourceRegistry
import com.dustvalve.next.android.domain.repository.SourceConcept
import com.dustvalve.next.android.domain.usecase.DownloadAlbumUseCase
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Regression coverage for the unified [ArtistDetailViewModel]. We want to lock
 * in the two branches the VM dispatches on: Bandcamp (album grid, no paginated
 * track feed) and YouTube (flat paginated tracks, no albums). These tests
 * replaced the deleted Bandcamp + YouTube artist detail VM tests.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ArtistDetailViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val sources = mockk<MusicSourceRegistry>()
    private val artistRepository = mockk<ArtistRepository>(relaxed = true)
    private val favoriteDao = mockk<FavoriteDao>(relaxed = true)
    private val artistDao = mockk<ArtistDao>(relaxed = true)
    private val trackDao = mockk<TrackDao>(relaxed = true)
    private val downloadRepository = mockk<DownloadRepository>()
    private val downloadAlbumUseCase = mockk<DownloadAlbumUseCase>(relaxed = true)
    private val database = mockk<DustvalveNextDatabase>(relaxed = true)

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { downloadRepository.getDownloadedTrackIds() } returns flowOf(emptyList())
        every { downloadRepository.getDownloadedAlbumIds() } returns flowOf(emptyList())
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test fun `bandcamp artist load exposes Artist with albums and does not call getArtistTracks`() = runTest(dispatcher) {
        val bandcampSource = sourceWith(
            id = "bandcamp",
            capabilities = setOf(SourceConcept.SEARCH, SourceConcept.ARTIST, SourceConcept.ALBUM),
        )
        val artist = Artist(
            id = "bc/artist",
            name = "Bandcamp Artist",
            url = "https://foo.bandcamp.com",
            imageUrl = "https://img",
            bio = "bio",
            location = "Paris",
            albums = listOf(sampleAlbum("a1"), sampleAlbum("a2")),
        )
        coEvery { bandcampSource.getArtist(artist.url) } returns artist
        every { sources["bandcamp"] } returns bandcampSource
        coEvery { favoriteDao.isFavorite(artist.url) } returns false

        val vm = newVm()
        vm.load(sourceId = "bandcamp", url = artist.url)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertThat(state.isLoading).isFalse()
        assertThat(state.artist?.name).isEqualTo("Bandcamp Artist")
        assertThat(state.artist?.albums).hasSize(2)
        assertThat(state.tracks).isEmpty()
        assertThat(state.hasMore).isFalse()
        coVerify(exactly = 0) { bandcampSource.getArtistTracks(any(), any()) }
    }

    @Test fun `youtube artist load fetches first page of tracks and propagates hasMore`() = runTest(dispatcher) {
        val ytSource = sourceWith(
            id = "youtube",
            capabilities = setOf(
                SourceConcept.SEARCH, SourceConcept.ARTIST,
                SourceConcept.ARTIST_TRACKS, SourceConcept.COLLECTION,
            ),
        )
        val url = "https://youtube.com/channel/UC1"
        val artist = Artist(
            id = url, name = "YT Channel", url = url,
            imageUrl = null, bio = null, location = null, albums = emptyList(),
        )
        val firstPage = MusicCollection(
            id = url, url = url, name = "YT Channel", owner = "YT Channel",
            coverUrl = null,
            tracks = listOf(track("yt_1"), track("yt_2"), track("yt_3")),
            continuation = "cont_token",
            hasMore = true,
        )
        coEvery { ytSource.getArtist(url) } returns artist
        coEvery { ytSource.getArtistTracks(url, continuation = null) } returns firstPage
        every { sources["youtube"] } returns ytSource
        coEvery { favoriteDao.isFavorite(url) } returns false

        val vm = newVm()
        vm.load(sourceId = "youtube", url = url, name = "YT Channel", imageUrl = "hint.jpg")
        advanceUntilIdle()

        val state = vm.uiState.value
        assertThat(state.isLoading).isFalse()
        assertThat(state.artist?.name).isEqualTo("YT Channel")
        // Caller-provided imageUrl is kept as a fallback when the source returns none.
        assertThat(state.artist?.imageUrl).isEqualTo("hint.jpg")
        assertThat(state.tracks).hasSize(3)
        assertThat(state.hasMore).isTrue()
    }

    @Test fun `youtube loadMore appends next page with continuation`() = runTest(dispatcher) {
        val ytSource = sourceWith(
            id = "youtube",
            capabilities = setOf(
                SourceConcept.ARTIST, SourceConcept.ARTIST_TRACKS,
            ),
        )
        val url = "https://youtube.com/channel/UC1"
        val page1 = MusicCollection(
            id = url, url = url, name = "Ch", owner = "Ch", coverUrl = null,
            tracks = listOf(track("yt_1")),
            continuation = "T1", hasMore = true,
        )
        val page2 = MusicCollection(
            id = url, url = url, name = "Ch", owner = "Ch", coverUrl = null,
            tracks = listOf(track("yt_2"), track("yt_3")),
            continuation = null, hasMore = false,
        )
        coEvery { ytSource.getArtist(url) } returns Artist(
            id = url, name = "Ch", url = url, imageUrl = null, bio = null,
            location = null, albums = emptyList(),
        )
        coEvery { ytSource.getArtistTracks(url, continuation = null) } returns page1
        coEvery { ytSource.getArtistTracks(url, continuation = "T1") } returns page2
        every { sources["youtube"] } returns ytSource
        coEvery { favoriteDao.isFavorite(url) } returns false

        val vm = newVm()
        vm.load(sourceId = "youtube", url = url)
        advanceUntilIdle()
        assertThat(vm.uiState.value.tracks).hasSize(1)

        vm.loadMore()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertThat(state.tracks.map { it.id }).containsExactly("yt_1", "yt_2", "yt_3").inOrder()
        assertThat(state.hasMore).isFalse()
    }

    @Test fun `unknown sourceId surfaces a clear error`() = runTest(dispatcher) {
        every { sources["nope"] } returns null

        val vm = newVm()
        vm.load(sourceId = "nope", url = "https://x")
        advanceUntilIdle()

        val state = vm.uiState.value
        assertThat(state.isLoading).isFalse()
        assertThat(state.error).contains("Unknown source: nope")
    }

    @Test fun `bandcamp toggleFavorite delegates to ArtistRepository, YT hits FavoriteDao directly`() = runTest(dispatcher) {
        val bc = sourceWith("bandcamp", setOf(SourceConcept.ARTIST, SourceConcept.ALBUM))
        val artist = Artist(
            id = "bc_id", name = "A", url = "https://foo.bandcamp.com",
            imageUrl = null, bio = null, location = null, albums = emptyList(),
        )
        coEvery { bc.getArtist(artist.url) } returns artist
        every { sources["bandcamp"] } returns bc
        coEvery { favoriteDao.isFavorite(artist.url) } returns false

        val vm = newVm()
        vm.load(sourceId = "bandcamp", url = artist.url)
        advanceUntilIdle()
        vm.toggleFavorite()
        advanceUntilIdle()

        coVerify { artistRepository.toggleFavorite("bc_id") }
    }

    // --- helpers ------------------------------------------------------------

    private fun newVm() = ArtistDetailViewModel(
        sources = sources,
        artistRepository = artistRepository,
        favoriteDao = favoriteDao,
        artistDao = artistDao,
        trackDao = trackDao,
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

    private fun sampleAlbum(id: String) = Album(
        id = id, url = "https://x/$id", title = id, artist = "A", artistUrl = "",
        artUrl = "", releaseDate = null, about = null, tracks = emptyList(), tags = emptyList(),
    )

    private fun track(id: String) = Track(
        id = id, albumId = "", title = id, artist = "A",
        trackNumber = 0, duration = 0f, streamUrl = null, artUrl = "",
        albumTitle = "", source = TrackSource.YOUTUBE,
    )

}
