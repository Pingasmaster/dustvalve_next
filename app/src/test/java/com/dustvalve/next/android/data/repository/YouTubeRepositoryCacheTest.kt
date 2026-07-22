@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.dustvalve.next.android.data.repository

import com.dustvalve.next.android.data.local.db.dao.YouTubePlaylistCacheDao
import com.dustvalve.next.android.data.local.db.dao.YouTubeVideoCacheDao
import com.dustvalve.next.android.data.local.db.entity.YouTubePlaylistCacheEntity
import com.dustvalve.next.android.data.local.db.entity.YouTubeVideoCacheEntity
import com.dustvalve.next.android.data.remote.youtube.innertube.YouTubeChannelParser
import com.dustvalve.next.android.data.remote.youtube.innertube.YouTubeInnertubeClient
import com.dustvalve.next.android.data.remote.youtube.innertube.YouTubeNextParser
import com.dustvalve.next.android.data.remote.youtube.innertube.YouTubePlayerParser
import com.dustvalve.next.android.data.remote.youtube.innertube.YouTubePlaylistParser
import com.dustvalve.next.android.data.remote.youtube.innertube.YouTubeSearchParser
import com.dustvalve.next.android.domain.model.Track
import com.dustvalve.next.android.domain.model.TrackSource
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import org.junit.Before
import org.junit.Test

/**
 * Verifies the unified-cache behaviour added to [YouTubeRepositoryImpl]:
 *
 * - getTrackInfo is **cache-first**: a cached row returns instantly with no
 *   network access.
 * - getTrackInfo persists fresh fetches into the video cache.
 * - getPlaylistTracks rebuilds from the cached snapshot when present and
 *   doesn't re-issue the browse request.
 * - Cache write failures never break the user-facing call (errors are
 *   silently swallowed).
 *
 * These guarantee the "we never get a resource a second time" + "background
 * caching is best-effort and never propagates errors" invariants for YT.
 */
class YouTubeRepositoryCacheTest {

    private lateinit var client: YouTubeInnertubeClient
    private lateinit var playerParser: YouTubePlayerParser
    private lateinit var searchParser: YouTubeSearchParser
    private lateinit var playlistParser: YouTubePlaylistParser
    private lateinit var channelParser: YouTubeChannelParser
    private lateinit var nextParser: YouTubeNextParser
    private lateinit var videoCache: YouTubeVideoCacheDao
    private lateinit var playlistCache: YouTubePlaylistCacheDao
    private lateinit var ytmRepo: com.dustvalve.next.android.domain.repository.YouTubeMusicRepository
    private lateinit var albumResolver: com.dustvalve.next.android.data.remote.youtubemusic.YouTubeMusicAlbumResolver
    private lateinit var repo: YouTubeRepositoryImpl

    @Before fun setUp() {
        client = mockk(relaxed = true)
        playerParser = mockk(relaxed = true)
        searchParser = mockk(relaxed = true)
        playlistParser = mockk(relaxed = true)
        channelParser = mockk(relaxed = true)
        nextParser = mockk(relaxed = true)
        videoCache = mockk(relaxed = true)
        playlistCache = mockk(relaxed = true)
        ytmRepo = mockk(relaxed = true)
        albumResolver = mockk(relaxed = true)
        coEvery { ytmRepo.lookupAlbumPlaylistForVideo(any()) } returns null
        repo = YouTubeRepositoryImpl(
            client, playerParser, searchParser, playlistParser, channelParser, nextParser,
            videoCache, playlistCache, ytmRepo, albumResolver,
            ioDispatcher = UnconfinedTestDispatcher(),
        )
    }

    @Test fun `getTrackInfo cache hit returns cached track without hitting network`() = runTest {
        coEvery { videoCache.getById("abc12345678") } returns YouTubeVideoCacheEntity(
            videoId = "abc12345678",
            title = "Cached Title",
            artist = "Cached Artist",
            artistUrl = "https://www.youtube.com/channel/UCx",
            durationSec = 233f,
            artUrl = "https://i.ytimg.com/vi/abc12345678/hqdefault.jpg",
            albumLookupDone = true, // Already-resolved row - fully complete cache hit.
        )

        val track = repo.getTrackInfo("https://www.youtube.com/watch?v=abc12345678")

        assertThat(track.title).isEqualTo("Cached Title")
        assertThat(track.artist).isEqualTo("Cached Artist")
        assertThat(track.duration).isEqualTo(233f)
        assertThat(track.source).isEqualTo(TrackSource.YOUTUBE)
        // Cache hits carry the canonical watch URL (never null, never a raw
        // googlevideo stream): the player re-resolves the real stream from it.
        // null here made every REPLAY of a cached video silently unplayable.
        assertThat(track.streamUrl).isEqualTo("https://www.youtube.com/watch?v=abc12345678")
        coVerify(exactly = 0) { client.player(any()) }
    }

    @Test fun `getTrackInfo cache miss fetches from network and persists`() = runTest {
        coEvery { videoCache.getById("missingvid1") } returns null
        coEvery { client.player("missingvid1") } returns JsonObject(emptyMap())
        every { playerParser.parseTrack(any(), "missingvid1") } returns Track(
            id = "yt_missingvid1",
            albumId = "",
            title = "Fresh",
            artist = "Fresh Artist",
            artistUrl = "https://www.youtube.com/channel/UCy",
            trackNumber = 0,
            duration = 100f,
            streamUrl = null,
            artUrl = "https://i.ytimg.com/vi/missingvid1/hq.jpg",
            albumTitle = "",
            source = TrackSource.YOUTUBE,
        )

        val track = repo.getTrackInfo("https://www.youtube.com/watch?v=missingvid1")

        assertThat(track.title).isEqualTo("Fresh")
        // The cache write is best-effort but should be attempted.
        val captured = slot<YouTubeVideoCacheEntity>()
        coVerify { videoCache.insert(capture(captured)) }
        assertThat(captured.captured.videoId).isEqualTo("missingvid1")
        assertThat(captured.captured.title).isEqualTo("Fresh")
    }

    @Test fun `getTrackInfo swallows cache write failures so user call still succeeds`() = runTest {
        coEvery { videoCache.getById(any()) } returns null
        coEvery { client.player(any()) } returns JsonObject(emptyMap())
        every { playerParser.parseTrack(any(), any()) } returns sampleTrack("yt_okvideo123")
        // Persist throws - the user-facing getTrackInfo must not propagate.
        coEvery { videoCache.insert(any()) } throws RuntimeException("disk full")

        val track = repo.getTrackInfo("https://www.youtube.com/watch?v=okvideo1234")
        assertThat(track.title).isEqualTo("Sample Title")
    }

    @Test fun `getPlaylistTracks cache hit returns cached snapshot without re-browsing`() = runTest {
        val playlistId = "PLcacheTest"
        coEvery { playlistCache.getById(playlistId) } returns YouTubePlaylistCacheEntity(
            playlistId = playlistId,
            title = "My Playlist",
            videoIdsJson = "[\"vid00000001\",\"vid00000002\"]",
            cachedAt = System.currentTimeMillis(), // Fresh - no background revalidate.
        )
        coEvery { videoCache.getByIds(listOf("vid00000001", "vid00000002")) } returns listOf(
            YouTubeVideoCacheEntity(
                videoId = "vid00000001",
                title = "First",
                artist = "A",
                artistUrl = "",
                durationSec = 60f,
                artUrl = "",
            ),
            YouTubeVideoCacheEntity(
                videoId = "vid00000002",
                title = "Second",
                artist = "B",
                artistUrl = "",
                durationSec = 90f,
                artUrl = "",
            ),
        )

        val (tracks, title) = repo.getPlaylistTracks("https://www.youtube.com/playlist?list=$playlistId")

        assertThat(title).isEqualTo("My Playlist")
        assertThat(tracks.map { it.title }).containsExactly("First", "Second").inOrder()
        coVerify(exactly = 0) { client.browse(any(), any()) }
    }

    @Test fun `getPlaylistTracks falls through to network when cache is incomplete`() = runTest {
        val playlistId = "PLincomplete"
        // Snapshot says 2 videos, but only 1 is in the video cache -> fall through.
        coEvery { playlistCache.getById(playlistId) } returns YouTubePlaylistCacheEntity(
            playlistId = playlistId,
            title = "Stale",
            videoIdsJson = "[\"vid00000001\",\"vid00000002\"]",
        )
        coEvery { videoCache.getByIds(any()) } returns listOf(
            YouTubeVideoCacheEntity(
                videoId = "vid00000001",
                title = "Just one",
                artist = "X",
                artistUrl = "",
                durationSec = 30f,
                artUrl = "",
            ),
        )
        // Network response for the fall-through.
        coEvery { client.browse("VL$playlistId") } returns JsonObject(emptyMap())
        every { playlistParser.parse(any(), playlistId) } returns
            com.dustvalve.next.android.data.remote.youtube.innertube.YouTubePlaylistParser.PlaylistPage(
                tracks = listOf(sampleTrack("yt_freshvid001"), sampleTrack("yt_freshvid002")),
                continuation = null,
                title = "Refreshed",
            )

        val (tracks, title) = repo.getPlaylistTracks("https://www.youtube.com/playlist?list=$playlistId")

        assertThat(title).isEqualTo("Refreshed")
        assertThat(tracks).hasSize(2)
        coVerify { client.browse("VL$playlistId") }
    }

    @Test fun `playlist snapshot uses non-destructive bulk insert so upgraded rows survive`() = runTest {
        val playlistId = "PLbulkIgnore"
        coEvery { playlistCache.getById(playlistId) } returns null
        coEvery { client.browse("VL$playlistId") } returns JsonObject(emptyMap())
        every { playlistParser.parse(any(), playlistId) } returns
            YouTubePlaylistParser.PlaylistPage(
                tracks = listOf(sampleTrack("yt_bulkvid0001")),
                title = "Bulk",
                continuation = null,
            )

        repo.getPlaylistTracks("https://www.youtube.com/playlist?list=$playlistId")

        // REPLACE here would clobber rows already upgraded with a resolved
        // albumUrl / albumLookupDone=true by the single-video path,
        // breaking the "once attempted, never re-lookup" invariant.
        coVerify(exactly = 1) { videoCache.insertAllIgnore(any()) }
        coVerify(exactly = 0) { videoCache.insertAll(any()) }
    }

    @Test fun `mix page cache write is non-destructive too`() = runTest {
        every { playlistParser.extractMixSeedVideoId("RDdQw4w9WgXcQ") } returns "dQw4w9WgXcQ"
        coEvery { client.next(videoId = "dQw4w9WgXcQ", playlistId = "RDdQw4w9WgXcQ") } returns JsonObject(emptyMap())
        every { playlistParser.parseMix(any(), any(), any(), any()) } returns
            YouTubePlaylistParser.MixPage(
                tracks = listOf(sampleTrack("yt_mixvid00001")),
                title = "Mix",
                continuation = null,
            )

        repo.getMixPage("https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=RDdQw4w9WgXcQ")

        coVerify(exactly = 1) { videoCache.insertAllIgnore(any()) }
        coVerify(exactly = 0) { videoCache.insertAll(any()) }
    }

    @Test fun `failed album lookup leaves the cached row untouched for a later retry`() = runTest {
        coEvery { videoCache.getById("retryvid001") } returns YouTubeVideoCacheEntity(
            videoId = "retryvid001",
            title = "Seeded",
            artist = "A",
            artistUrl = "",
            durationSec = 10f,
            artUrl = "",
            albumLookupDone = false,
        )
        coEvery { ytmRepo.lookupAlbumPlaylistForVideo("retryvid001") } throws RuntimeException("network down")

        val track = repo.getTrackInfo("https://www.youtube.com/watch?v=retryvid001")

        assertThat(track.title).isEqualTo("Seeded")
        // No write: persisting albumLookupDone=true after a transient
        // failure would poison the negative cache forever.
        coVerify(exactly = 0) { videoCache.insert(any()) }
    }

    @Test fun `completed negative lookup upgrades the row with albumLookupDone=true`() = runTest {
        coEvery { videoCache.getById("negvid00001") } returns YouTubeVideoCacheEntity(
            videoId = "negvid00001",
            title = "Seeded",
            artist = "A",
            artistUrl = "",
            durationSec = 10f,
            artUrl = "",
            albumLookupDone = false,
        )
        // Definitive "no album" from a successful lookup.
        coEvery { ytmRepo.lookupAlbumPlaylistForVideo("negvid00001") } returns null

        repo.getTrackInfo("https://www.youtube.com/watch?v=negvid00001")

        val captured = slot<YouTubeVideoCacheEntity>()
        coVerify { videoCache.insert(capture(captured)) }
        assertThat(captured.captured.albumLookupDone).isTrue()
        assertThat(captured.captured.albumUrl).isEmpty()
    }

    @Test fun `failed album lookup on a fresh fetch persists albumLookupDone=false`() = runTest {
        coEvery { videoCache.getById("freshfail01") } returns null
        coEvery { client.player("freshfail01") } returns JsonObject(emptyMap())
        every { playerParser.parseTrack(any(), "freshfail01") } returns sampleTrack("yt_freshfail01")
        coEvery { ytmRepo.lookupAlbumPlaylistForVideo("freshfail01") } throws RuntimeException("boom")

        val track = repo.getTrackInfo("https://www.youtube.com/watch?v=freshfail01")

        assertThat(track.albumUrl).isEmpty()
        val captured = slot<YouTubeVideoCacheEntity>()
        coVerify { videoCache.insert(capture(captured)) }
        assertThat(captured.captured.albumLookupDone).isFalse()
    }

    @Test fun `cancellation during album lookup propagates instead of being swallowed`() = runTest {
        coEvery { videoCache.getById("cancelvid01") } returns YouTubeVideoCacheEntity(
            videoId = "cancelvid01",
            title = "T",
            artist = "A",
            artistUrl = "",
            durationSec = 1f,
            artUrl = "",
            albumLookupDone = false,
        )
        coEvery { ytmRepo.lookupAlbumPlaylistForVideo("cancelvid01") } throws
            kotlinx.coroutines.CancellationException("cancelled")

        val ex = runCatching {
            repo.getTrackInfo("https://www.youtube.com/watch?v=cancelvid01")
        }.exceptionOrNull()

        assertThat(ex).isInstanceOf(kotlinx.coroutines.CancellationException::class.java)
        coVerify(exactly = 0) { videoCache.insert(any()) }
    }

    private fun sampleTrack(id: String): Track = Track(
        id = id,
        albumId = "",
        title = "Sample Title",
        artist = "Sample Artist",
        artistUrl = "",
        trackNumber = 0,
        duration = 120f,
        streamUrl = null,
        artUrl = "",
        albumTitle = "",
        source = TrackSource.YOUTUBE,
    )
}
