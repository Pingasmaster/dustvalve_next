package com.dustvalve.next.android.ui.screens.detail

import com.dustvalve.next.android.R
import com.dustvalve.next.android.domain.model.Album
import com.dustvalve.next.android.domain.model.Artist
import com.dustvalve.next.android.domain.model.MusicCollection
import com.dustvalve.next.android.domain.model.MusicProvider
import com.dustvalve.next.android.domain.model.Track
import com.dustvalve.next.android.domain.model.TrackSource
import com.dustvalve.next.android.domain.repository.ArtistRepository
import com.dustvalve.next.android.domain.repository.DownloadRepository
import com.dustvalve.next.android.domain.repository.FavoriteRepository
import com.dustvalve.next.android.domain.repository.MusicSource
import com.dustvalve.next.android.domain.repository.MusicSourceRegistry
import com.dustvalve.next.android.domain.repository.SourceConcept
import com.dustvalve.next.android.domain.repository.TrackCacheRepository
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
    private val favoriteRepository = mockk<FavoriteRepository>(relaxed = true)
    private val trackCacheRepository = mockk<TrackCacheRepository>(relaxed = true)
    private val downloadRepository = mockk<DownloadRepository>()
    private val downloadAlbumUseCase = mockk<DownloadAlbumUseCase>(relaxed = true)
    private val downloadController = mockk<DownloadController>(relaxed = true)

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
            isFavorite = true,
        )
        every { sources["bandcamp"] } returns bandcampSource
        every { artistRepository.getArtistDetailFlow(artist.url) } returns flowOf(artist)

        val vm = newVm()
        vm.load(sourceId = "bandcamp", url = artist.url)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertThat(state.isLoading).isFalse()
        assertThat(state.artist?.name).isEqualTo("Bandcamp Artist")
        assertThat(state.artist?.albums).hasSize(2)
        assertThat(state.isFavorite).isTrue()
        assertThat(state.tracks).isEmpty()
        assertThat(state.hasMore).isFalse()
        coVerify(exactly = 0) { bandcampSource.getArtist(any()) }
        coVerify(exactly = 0) { bandcampSource.getArtistTracks(any(), any()) }
        // Favorite is the stable hash id from the Artist row, not the URL.
        coVerify(exactly = 0) { favoriteRepository.isFavorite(any()) }
    }

    @Test fun `youtube artist load fetches first page of tracks and propagates hasMore`() = runTest(dispatcher) {
        val ytSource = sourceWith(
            id = "youtube",
            capabilities = setOf(
                SourceConcept.SEARCH,
                SourceConcept.ARTIST,
                SourceConcept.ARTIST_TRACKS,
                SourceConcept.COLLECTION,
            ),
        )
        val url = "https://youtube.com/channel/UC1"
        val artist = Artist(
            id = url,
            name = "YT Channel",
            url = url,
            imageUrl = null,
            bio = null,
            location = null,
            albums = emptyList(),
        )
        val firstPage = MusicCollection(
            id = url,
            url = url,
            name = "YT Channel",
            owner = "YT Channel",
            coverUrl = null,
            tracks = listOf(track("yt_1"), track("yt_2"), track("yt_3")),
            continuation = "cont_token",
            hasMore = true,
        )
        coEvery { ytSource.getArtist(url) } returns artist
        coEvery { ytSource.getArtistTracks(url, continuation = null) } returns firstPage
        every { sources["youtube"] } returns ytSource
        coEvery { favoriteRepository.isFavorite(url) } returns false

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
                SourceConcept.ARTIST,
                SourceConcept.ARTIST_TRACKS,
            ),
        )
        val url = "https://youtube.com/channel/UC1"
        val page1 = MusicCollection(
            id = url,
            url = url,
            name = "Ch",
            owner = "Ch",
            coverUrl = null,
            tracks = listOf(track("yt_1")),
            continuation = "T1",
            hasMore = true,
        )
        val page2 = MusicCollection(
            id = url,
            url = url,
            name = "Ch",
            owner = "Ch",
            coverUrl = null,
            tracks = listOf(track("yt_2"), track("yt_3")),
            continuation = null,
            hasMore = false,
        )
        coEvery { ytSource.getArtist(url) } returns Artist(
            id = url,
            name = "Ch",
            url = url,
            imageUrl = null,
            bio = null,
            location = null,
            albums = emptyList(),
        )
        coEvery { ytSource.getArtistTracks(url, continuation = null) } returns page1
        coEvery { ytSource.getArtistTracks(url, continuation = "T1") } returns page2
        every { sources["youtube"] } returns ytSource
        coEvery { favoriteRepository.isFavorite(url) } returns false

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

    @Test fun `playExpanded drains artist track pages before playing`() = runTest(dispatcher) {
        val ytSource = sourceWith(
            id = "youtube",
            capabilities = setOf(SourceConcept.ARTIST, SourceConcept.ARTIST_TRACKS),
        )
        val url = "https://youtube.com/channel/UC1"
        coEvery { ytSource.getArtist(url) } returns Artist(
            id = url,
            name = "Ch",
            url = url,
            imageUrl = null,
            bio = null,
            location = null,
            albums = emptyList(),
        )
        coEvery { ytSource.getArtistTracks(url, continuation = null) } returns MusicCollection(
            id = url,
            url = url,
            name = "Ch",
            owner = "Ch",
            coverUrl = null,
            tracks = listOf(track("yt_1"), track("yt_2")),
            continuation = "T1",
            hasMore = true,
        )
        coEvery { ytSource.getArtistTracks(url, continuation = "T1") } returns MusicCollection(
            id = url,
            url = url,
            name = "Ch",
            owner = "Ch",
            coverUrl = null,
            tracks = listOf(track("yt_3"), track("yt_4")),
            continuation = null,
            hasMore = false,
        )
        every { sources["youtube"] } returns ytSource
        coEvery { favoriteRepository.isFavorite(url) } returns false

        val vm = newVm()
        vm.load(sourceId = "youtube", url = url)
        advanceUntilIdle()

        var played: List<Track>? = null
        vm.playExpanded(0) { tracks, _ -> played = tracks }
        advanceUntilIdle()

        assertThat(played!!.map { it.id }).containsExactly("yt_1", "yt_2", "yt_3", "yt_4").inOrder()
        assertThat(vm.uiState.value.hasMore).isFalse()
    }

    @Test fun `failed load with a seeded hint surfaces the error and keeps retry working`() = runTest(dispatcher) {
        val ytSource = sourceWith(
            id = "youtube",
            capabilities = setOf(SourceConcept.ARTIST, SourceConcept.ARTIST_TRACKS),
        )
        val url = "https://youtube.com/channel/UC1"
        var fail = true
        coEvery { ytSource.getArtist(url) } coAnswers {
            if (fail) throw IllegalStateException("offline")
            Artist(id = url, name = "Ch", url = url, imageUrl = null, bio = null, location = null, albums = emptyList())
        }
        coEvery { ytSource.getArtistTracks(url, continuation = null) } returns MusicCollection(
            id = url,
            url = url,
            name = "Ch",
            owner = "Ch",
            coverUrl = null,
            tracks = listOf(track("yt_1")),
            continuation = null,
            hasMore = false,
        )
        every { sources["youtube"] } returns ytSource
        coEvery { favoriteRepository.isFavorite(url) } returns false

        val vm = newVm()
        vm.load(sourceId = "youtube", url = url, name = "Hint", imageUrl = "hint.jpg")
        advanceUntilIdle()

        // Seed hint is present but the fetch failed: the error must be exposed
        // (the screen renders error+retry, not a bogus "No releases").
        assertThat(vm.uiState.value.error).isNotNull()
        assertThat(vm.uiState.value.artist?.name).isEqualTo("Hint")

        // A later load() with the same key must NOT short-circuit on a key that
        // never loaded successfully - retry has to refetch.
        fail = false
        vm.load(sourceId = "youtube", url = url, name = "Hint", imageUrl = "hint.jpg")
        advanceUntilIdle()

        val state = vm.uiState.value
        assertThat(state.error).isNull()
        assertThat(state.artist?.name).isEqualTo("Ch")
        assertThat(state.tracks.map { it.id }).containsExactly("yt_1")
    }

    @Test fun `unknown sourceId surfaces a clear error`() = runTest(dispatcher) {
        every { sources["nope"] } returns null

        val vm = newVm()
        vm.load(sourceId = "nope", url = "https://x")
        advanceUntilIdle()

        val state = vm.uiState.value
        assertThat(state.isLoading).isFalse()
        val error = state.error as UiText.StringResource
        assertThat(error.resId).isEqualTo(R.string.error_unknown_source)
        assertThat(error.args).containsExactly("nope")
    }

    @Test fun `bandcamp toggleFavorite delegates to ArtistRepository toggleFavorite`() = runTest(dispatcher) {
        val bc = sourceWith("bandcamp", setOf(SourceConcept.ARTIST, SourceConcept.ALBUM))
        val artist = Artist(
            id = "bc_id",
            name = "A",
            url = "https://foo.bandcamp.com",
            imageUrl = null,
            bio = null,
            location = null,
            albums = emptyList(),
        )
        every { sources["bandcamp"] } returns bc
        every { artistRepository.getArtistDetailFlow(artist.url) } returns flowOf(artist)

        val vm = newVm()
        vm.load(sourceId = "bandcamp", url = artist.url)
        advanceUntilIdle()
        vm.toggleFavorite()
        advanceUntilIdle()

        coVerify { artistRepository.toggleFavorite("bc_id") }
        coVerify(exactly = 0) { artistRepository.favoriteRemoteArtist(any(), any()) }
    }

    @Test fun `bandcamp downloadAll resolves albums via DownloadAlbumUseCase`() = runTest(dispatcher) {
        val bc = sourceWith("bandcamp", setOf(SourceConcept.ARTIST, SourceConcept.ALBUM))
        val artist = Artist(
            id = "bc_id",
            name = "moe shop",
            url = "https://moeshop.bandcamp.com",
            imageUrl = null,
            bio = null,
            location = null,
            albums = listOf(sampleAlbum("a1"), sampleAlbum("a2")),
        )
        every { sources["bandcamp"] } returns bc
        every { artistRepository.getArtistDetailFlow(artist.url) } returns flowOf(artist)
        coEvery { downloadAlbumUseCase.downloadArtist(artist) } returns Unit

        val vm = newVm()
        vm.load(sourceId = "bandcamp", url = artist.url)
        advanceUntilIdle()
        vm.downloadAll()
        advanceUntilIdle()

        coVerify(exactly = 1) { downloadAlbumUseCase.downloadArtist(artist) }
        coVerify(exactly = 0) { downloadController.downloadTrackBlocking(any(), any()) }
        val state = vm.uiState.value
        assertThat(state.isDownloading).isFalse()
        assertThat((state.snackbarMessage as UiText.StringResource).resId)
            .isEqualTo(R.string.snackbar_downloaded)
    }

    @Test fun `bandcamp deleteAllDownloads deletes by album id not stub tracks`() = runTest(dispatcher) {
        val bc = sourceWith("bandcamp", setOf(SourceConcept.ARTIST, SourceConcept.ALBUM))
        val artist = Artist(
            id = "bc_id",
            name = "moe shop",
            url = "https://moeshop.bandcamp.com",
            imageUrl = null,
            bio = null,
            location = null,
            albums = listOf(sampleAlbum("a1")),
        )
        every { sources["bandcamp"] } returns bc
        every { artistRepository.getArtistDetailFlow(artist.url) } returns flowOf(artist)

        val vm = newVm()
        vm.load(sourceId = "bandcamp", url = artist.url)
        advanceUntilIdle()
        vm.deleteAllDownloads()
        advanceUntilIdle()

        coVerify(exactly = 1) { downloadAlbumUseCase.deleteArtistDownloads(artist) }
        coVerify(exactly = 0) { downloadAlbumUseCase.deleteTrackDownload(any()) }
    }

    @Test fun `youtube toggleFavorite uses favoriteRemoteArtist and unfavoriteArtist`() = runTest(dispatcher) {
        val ytSource = sourceWith("youtube", setOf(SourceConcept.ARTIST, SourceConcept.ARTIST_TRACKS))
        val url = "https://youtube.com/channel/UC1"
        val artist = Artist(
            id = url,
            name = "Ch",
            url = url,
            imageUrl = null,
            bio = null,
            location = null,
            albums = emptyList(),
        )
        coEvery { ytSource.getArtist(url) } returns artist
        coEvery { ytSource.getArtistTracks(url, continuation = null) } returns MusicCollection(
            id = url,
            url = url,
            name = "Ch",
            owner = "Ch",
            coverUrl = null,
            tracks = listOf(track("yt_1")),
            continuation = null,
            hasMore = false,
        )
        every { sources["youtube"] } returns ytSource
        coEvery { favoriteRepository.isFavorite(url) } returns false

        val vm = newVm()
        vm.load(sourceId = "youtube", url = url)
        advanceUntilIdle()

        vm.toggleFavorite()
        advanceUntilIdle()
        assertThat(vm.uiState.value.isFavorite).isTrue()
        coVerify(exactly = 1) { artistRepository.favoriteRemoteArtist(any(), source = "youtube") }

        vm.toggleFavorite()
        advanceUntilIdle()
        assertThat(vm.uiState.value.isFavorite).isFalse()
        coVerify(exactly = 1) { artistRepository.unfavoriteArtist(url) }
        coVerify(exactly = 0) { artistRepository.toggleFavorite(any()) }
    }

    @Test fun `soundcloud toggleFavorite persists sourceId soundcloud not youtube`() = runTest(dispatcher) {
        val scSource = sourceWith("soundcloud", setOf(SourceConcept.ARTIST, SourceConcept.ARTIST_TRACKS))
        val url = "https://soundcloud.com/cool-artist"
        val artist = Artist(
            id = url,
            name = "Cool",
            url = url,
            imageUrl = null,
            bio = null,
            location = null,
            albums = emptyList(),
        )
        coEvery { scSource.getArtist(url) } returns artist
        coEvery { scSource.getArtistTracks(url, continuation = null) } returns MusicCollection(
            id = url,
            url = url,
            name = "Cool",
            owner = "Cool",
            coverUrl = null,
            tracks = listOf(track("sc_1")),
            continuation = null,
            hasMore = false,
        )
        every { sources["soundcloud"] } returns scSource
        coEvery { favoriteRepository.isFavorite(url) } returns false

        val vm = newVm()
        vm.load(sourceId = "soundcloud", url = url)
        advanceUntilIdle()

        vm.toggleFavorite()
        advanceUntilIdle()
        assertThat(vm.uiState.value.isFavorite).isTrue()
        coVerify(exactly = 1) { artistRepository.favoriteRemoteArtist(any(), source = "soundcloud") }
        coVerify(exactly = 0) { artistRepository.favoriteRemoteArtist(any(), source = "youtube") }
    }

    @Test fun `downloadAll skips an undownloadable track, retries it last, and reports it unavailable`() = runTest(dispatcher) {
        val vm = loadedYoutubeArtist(listOf("t1", "bad", "t3"))
        val attempts = mutableListOf<String>()
        coEvery { downloadController.downloadTrackBlocking(any(), any()) } answers {
            val t = firstArg<Track>()
            attempts += t.id
            if (t.id == "bad") throw IllegalStateException("no audio adaptiveFormats (playabilityStatus=\"LOGIN_REQUIRED\")")
        }

        vm.downloadAll()
        advanceUntilIdle()

        // "t3" runs BEFORE "bad" is retried: the bad track no longer ends the
        // artist download where it stands.
        assertThat(attempts).containsExactly("t1", "bad", "t3", "bad").inOrder()

        val state = vm.uiState.value
        assertThat(state.isDownloading).isFalse()
        val message = state.snackbarMessage as UiText.PluralsResource
        assertThat(message.resId).isEqualTo(R.plurals.snackbar_downloaded_partial)
        assertThat(message.count).isEqualTo(1)
        assertThat(message.args).containsExactly(2, 3, 1).inOrder()
        // Partial success is not an error banner, but Retry is still offered.
        assertThat(state.isSnackbarError).isFalse()
        assertThat(vm.retryAction).isNotNull()
    }

    @Test fun `downloadAll reports plain success when the deferred retry lands`() = runTest(dispatcher) {
        val vm = loadedYoutubeArtist(listOf("t1", "flaky", "t3"))
        var flakyAttempts = 0
        coEvery { downloadController.downloadTrackBlocking(any(), any()) } answers {
            val t = firstArg<Track>()
            if (t.id == "flaky" && ++flakyAttempts == 1) throw java.io.IOException("connection reset")
        }

        vm.downloadAll()
        advanceUntilIdle()

        assertThat(flakyAttempts).isEqualTo(2)
        val state = vm.uiState.value
        val message = state.snackbarMessage as UiText.StringResource
        assertThat(message.resId).isEqualTo(R.string.snackbar_downloaded)
        assertThat(message.args).containsExactly("YT Channel")
        assertThat(state.isSnackbarError).isFalse()
        assertThat(vm.retryAction).isNull()
    }

    @Test fun `downloadAll surfaces the failure when every track is unavailable`() = runTest(dispatcher) {
        val vm = loadedYoutubeArtist(listOf("t1", "t2"))
        coEvery { downloadController.downloadTrackBlocking(any(), any()) } throws java.io.IOException("offline")

        vm.downloadAll()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertThat(state.isDownloading).isFalse()
        assertThat(state.isSnackbarError).isTrue()
        assertThat((state.snackbarMessage as UiText.DynamicString).value).isEqualTo("offline")
        assertThat(vm.retryAction).isNotNull()
    }

    // --- helpers ------------------------------------------------------------

    /** A VM sitting on a fully loaded, single-page YouTube artist. */
    private fun TestScope.loadedYoutubeArtist(trackIds: List<String>): ArtistDetailViewModel {
        val ytSource = sourceWith("youtube", setOf(SourceConcept.ARTIST, SourceConcept.ARTIST_TRACKS))
        val url = "https://youtube.com/channel/UC1"
        val artist = Artist(
            id = url,
            name = "YT Channel",
            url = url,
            imageUrl = null,
            bio = null,
            location = null,
            albums = emptyList(),
        )
        coEvery { ytSource.getArtist(url) } returns artist
        coEvery { ytSource.getArtistTracks(url, continuation = null) } returns MusicCollection(
            id = url,
            url = url,
            name = "YT Channel",
            owner = "YT Channel",
            coverUrl = null,
            tracks = trackIds.map { track(it) },
            continuation = null,
            hasMore = false,
        )
        every { sources["youtube"] } returns ytSource
        coEvery { favoriteRepository.isFavorite(url) } returns false

        val vm = newVm()
        vm.load(sourceId = "youtube", url = url)
        advanceUntilIdle()
        return vm
    }

    private fun newVm() = ArtistDetailViewModel(
        sources = sources,
        artistRepository = artistRepository,
        favoriteRepository = favoriteRepository,
        trackCacheRepository = trackCacheRepository,
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
