@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.dustvalve.next.android.data.remote

import com.dustvalve.next.android.data.remote.youtube.innertube.YouTubeChannelParser
import com.dustvalve.next.android.data.remote.youtube.innertube.YouTubeInnertubeClient
import com.dustvalve.next.android.data.remote.youtube.innertube.YouTubePlaylistParser
import com.dustvalve.next.android.data.remote.youtube.innertube.YouTubeVisitorDataFetcher
import com.dustvalve.next.android.data.remote.youtubemusic.YouTubeMusicAlbumResolver
import com.dustvalve.next.android.data.remote.youtubemusic.YouTubeMusicInnertubeClient
import com.dustvalve.next.android.data.remote.youtubemusic.YouTubeMusicVisitorDataFetcher
import com.dustvalve.next.android.data.repository.YouTubeRepositoryImpl
import com.dustvalve.next.android.domain.repository.YouTubePlaylistResult
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Live end-to-end integrity checks against real YouTube / YouTube Music /
 * Bandcamp responses. Skipped unless `DUSTVALVE_LIVE_NET=1`.
 *
 * Catalog coverage (docs/testing/catalog-youtube.md + bandcamp):
 * - yt-playlist-lockup-rows / yt-playlist-title-cover
 * - yt-album-olak-cover-tracks (via MPREb resolve + song->album)
 * - yt-channel-lockup-rows / yt-channel-avatar
 * - yt-topic-channel-avatar (Topic channels often have no Videos tab)
 * - yt-play-album-lookup-crossover (song -> OLAK album playlist)
 * - bc-artist-grid-art / bc-artist-relative-client-items / bc-album-cover
 *
 * Run:
 *   DUSTVALVE_LIVE_NET=1 ./build.sh --live-net
 *   DUSTVALVE_LIVE_NET=1 ./gradlew :app:testFutureDebugUnitTest --tests \
 *     '*LiveProviderMetadataIntegrityTest'
 */
class LiveProviderMetadataIntegrityTest {

    private lateinit var okHttp: OkHttpClient
    private lateinit var io: CoroutineDispatcher
    private lateinit var ytClient: YouTubeInnertubeClient
    private lateinit var ytmClient: YouTubeMusicInnertubeClient
    private lateinit var playlistParser: YouTubePlaylistParser
    private lateinit var channelParser: YouTubeChannelParser
    private lateinit var albumResolver: YouTubeMusicAlbumResolver
    private lateinit var artistScraper: DustvalveArtistScraper
    private lateinit var albumScraper: DustvalveAlbumScraper

    @Before fun setUp() {
        assumeTrue(
            "Set DUSTVALVE_LIVE_NET=1 to run live-network metadata integrity tests",
            System.getenv("DUSTVALVE_LIVE_NET") == "1",
        )
        io = UnconfinedTestDispatcher()
        okHttp = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .addInterceptor(
                Interceptor { chain ->
                    chain.proceed(
                        chain.request().newBuilder()
                            .header(
                                "User-Agent",
                                "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
                                    "(KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36",
                            )
                            .build(),
                    )
                },
            )
            .build()
        val ytVisitor = YouTubeVisitorDataFetcher(okHttp, io)
        val ytmVisitor = YouTubeMusicVisitorDataFetcher(okHttp, io)
        ytClient = YouTubeInnertubeClient(okHttp, ytVisitor, io)
        ytmClient = YouTubeMusicInnertubeClient(okHttp, ytmVisitor, io)
        playlistParser = YouTubePlaylistParser()
        channelParser = YouTubeChannelParser()
        albumResolver = YouTubeMusicAlbumResolver(ytmClient)
        artistScraper = DustvalveArtistScraper(okHttp, io)
        albumScraper = DustvalveAlbumScraper(okHttp, io)
    }

    // --- YouTube / YTM playlists -------------------------------------------

    @Test fun `live YT playlists expose non-blank title cover and tracks`() = runTest {
        // One long-lived official playlist plus a few discovered via search so
        // we cover multiple uploaders / sizes without relying on a brittle
        // hardcoded list of third-party IDs.
        val playlistIds = linkedSetOf("PLFgquLnL59alCl_2TQvOiD5Vgm1hCaGSI")
        val search = ytClient.search(query = "lofi hip hop playlist", params = YT_PLAYLIST_FILTER)
        playlistIds += collectYtPlaylistIds(search).take(4)
        val failures = mutableListOf<String>()
        for (id in playlistIds) {
            val page = playlistParser.parse(ytClient.browse("VL$id"), id)
            val problems = mutableListOf<String>()
            if (page.title.isNullOrBlank()) problems += "blank title"
            if (page.coverUrl.isNullOrBlank()) problems += "blank cover"
            if (page.tracks.isEmpty()) problems += "zero tracks"
            page.tracks.take(3).forEachIndexed { i, t ->
                if (t.title.isBlank()) problems += "track[$i] blank title"
                if (t.id.isBlank()) problems += "track[$i] blank id"
                if (t.artUrl.isBlank()) problems += "track[$i] blank art"
            }
            if (problems.isNotEmpty()) {
                failures += "$id -> ${problems.joinToString()}"
            }
            println(
                "[LIVE] playlist $id title='${page.title}' cover=${page.coverUrl != null} " +
                    "tracks=${page.tracks.size} cont=${page.continuation != null}",
            )
        }
        assertThat(failures).isEmpty()
    }

    @Test fun `live YTM search playlists resolve with title cover and tracks`() = runTest {
        // YTM playlist browseIds are VLPL...; strip VL then browse as playlist.
        val search = ytmClient.search(query = "chill beats", params = PLAYLISTS_PARAMS)
        val browseIds = collectBrowseIds(search, pageTypeContains = "PLAYLIST").take(4)
        assumeTrue("YTM playlist search returned no results", browseIds.isNotEmpty())
        val failures = mutableListOf<String>()
        for (browseId in browseIds) {
            val playlistId = browseId.removePrefix("VL")
            val page = playlistParser.parse(ytClient.browse("VL$playlistId"), playlistId)
            val problems = mutableListOf<String>()
            if (page.title.isNullOrBlank()) problems += "blank title"
            if (page.coverUrl.isNullOrBlank()) problems += "blank cover"
            if (page.tracks.isEmpty()) problems += "zero tracks"
            if (problems.isNotEmpty()) failures += "$playlistId -> ${problems.joinToString()}"
            println(
                "[LIVE] ytm-playlist $playlistId title='${page.title}' " +
                    "cover=${page.coverUrl != null} tracks=${page.tracks.size}",
            )
        }
        assertThat(failures).isEmpty()
    }

    @Test fun `live YTM albums resolve MPREb to OLAK with title cover and tracks`() = runTest {
        val search = ytmClient.search(query = "Daft Punk Random Access Memories", params = ALBUMS_PARAMS)
        val albumIds = collectBrowseIds(search, pageTypeContains = "ALBUM")
            .filter { it.startsWith("MPREb") }
            .take(3)
        assumeTrue("YTM album search returned no MPREb ids", albumIds.isNotEmpty())
        val failures = mutableListOf<String>()
        for (mpreb in albumIds) {
            val olak = albumResolver.resolveAudioPlaylistId(mpreb)
            if (olak.isNullOrBlank()) {
                failures += "$mpreb -> no OLAK"
                continue
            }
            val page = playlistParser.parse(ytClient.browse("VL$olak"), olak)
            val problems = mutableListOf<String>()
            if (page.title.isNullOrBlank()) problems += "blank title"
            if (page.coverUrl.isNullOrBlank()) problems += "blank cover"
            if (page.tracks.isEmpty()) problems += "zero tracks"
            if (problems.isNotEmpty()) {
                failures += "$mpreb/$olak -> ${problems.joinToString()}"
            }
            println(
                "[LIVE] ytm-album $mpreb -> $olak title='${page.title}' " +
                    "cover=${page.coverUrl != null} tracks=${page.tracks.size}",
            )
        }
        assertThat(failures).isEmpty()
    }

    @Test fun `live repository getPlaylistTracks returns metadata for a public playlist`() = runTest {
        // End-to-end through YouTubeRepositoryImpl (cache mocks stay empty so
        // the live browse path always runs).
        val videoCache = mockk<com.dustvalve.next.android.data.local.db.dao.YouTubeVideoCacheDao>(relaxed = true)
        val playlistCache = mockk<com.dustvalve.next.android.data.local.db.dao.YouTubePlaylistCacheDao>(relaxed = true)
        coEvery { videoCache.getById(any()) } returns null
        coEvery { videoCache.getByIds(any()) } returns emptyList()
        coEvery { playlistCache.getById(any()) } returns null
        val ytmRepo = mockk<com.dustvalve.next.android.domain.repository.YouTubeMusicRepository>(relaxed = true)
        coEvery { ytmRepo.lookupAlbumPlaylistForVideo(any()) } returns null
        val repo = YouTubeRepositoryImpl(
            client = ytClient,
            playerParser = com.dustvalve.next.android.data.remote.youtube.innertube.YouTubePlayerParser(),
            searchParser = com.dustvalve.next.android.data.remote.youtube.innertube.YouTubeSearchParser(),
            playlistParser = playlistParser,
            channelParser = channelParser,
            nextParser = com.dustvalve.next.android.data.remote.youtube.innertube.YouTubeNextParser(),
            videoCache = videoCache,
            playlistCache = playlistCache,
            youTubeMusicRepository = ytmRepo,
            albumResolver = albumResolver,
            ioDispatcher = io,
        )
        val result: YouTubePlaylistResult = repo.getPlaylistTracks(
            "https://www.youtube.com/playlist?list=PLFgquLnL59alCl_2TQvOiD5Vgm1hCaGSI",
        )
        assertThat(result.title).isNotEmpty()
        assertThat(result.coverUrl).isNotNull()
        assertThat(result.tracks.size).isAtLeast(5)
        println(
            "[LIVE] repo playlist title='${result.title}' cover=${result.coverUrl} " +
                "tracks=${result.tracks.size}",
        )
    }

    @Test fun `live song to album lookup yields OLAK playlist with title cover and tracks`() = runTest {
        // Catalog: yt-play-album-lookup-crossover. Discover real YTM catalog
        // song ids (plain YouTube watch ids often lack MUSIC_PAGE_TYPE_ALBUM),
        // then song -> /next MPREb -> OLAK browse must expose full metadata.
        val queries = listOf(
            "Daft Punk Get Lucky",
            "Radiohead Karma Police",
            "Billie Eilish bad guy",
        )
        val videoIds = linkedSetOf<String>()
        for (q in queries) {
            val search = ytmClient.search(query = q, params = SONGS_PARAMS)
            videoIds += collectVideoIds(search).take(2)
        }
        assumeTrue("YTM song search returned no video ids", videoIds.isNotEmpty())
        val outcomes = videoIds.take(5).map { videoId -> evaluateSongToAlbum(videoId) }
        outcomes.filterIsInstance<SongAlbumOutcome.Skipped>().forEach {
            println("[LIVE] song->album ${it.videoId} -> no MPREb (skip)")
        }
        val failures = outcomes.filterIsInstance<SongAlbumOutcome.Failed>().map { it.detail }
        val resolved = outcomes.filterIsInstance<SongAlbumOutcome.Ok>()
        resolved.forEach {
            println(
                "[LIVE] song->album ${it.videoId} -> ${it.mpreb}/${it.olak} title='${it.title}' " +
                    "cover=${it.hasCover} tracks=${it.trackCount}",
            )
        }
        assumeTrue("No song->album resolutions succeeded", resolved.isNotEmpty())
        assertThat(failures).isEmpty()
        // At least one resolved album must be a multi-track LP/EP so we are
        // not only exercising single-track OLAK edges.
        assertThat(resolved.count { it.trackCount >= 3 }).isAtLeast(1)
    }

    private suspend fun evaluateSongToAlbum(videoId: String): SongAlbumOutcome {
        val mpreb = findAlbumBrowseId(ytmClient.next(videoId))
            ?: return SongAlbumOutcome.Skipped(videoId)
        val olak = albumResolver.resolveAudioPlaylistId(mpreb)
            ?: return SongAlbumOutcome.Failed("$videoId/$mpreb -> no OLAK")
        val page = playlistParser.parse(ytClient.browse("VL$olak"), olak)
        val problems = mutableListOf<String>()
        if (page.title.isNullOrBlank()) problems += "blank title"
        if (page.coverUrl.isNullOrBlank()) problems += "blank cover"
        // Singles legitimately have 1 track; still require a non-empty list.
        if (page.tracks.isEmpty()) problems += "zero tracks"
        page.tracks.take(3).forEachIndexed { i, t ->
            if (t.title.isBlank()) problems += "track[$i] blank title"
            if (t.artUrl.isBlank()) problems += "track[$i] blank art"
        }
        if (problems.isNotEmpty()) {
            return SongAlbumOutcome.Failed("$videoId/$olak -> ${problems.joinToString()}")
        }
        return SongAlbumOutcome.Ok(
            videoId = videoId,
            mpreb = mpreb,
            olak = olak,
            title = page.title,
            hasCover = page.coverUrl != null,
            trackCount = page.tracks.size,
        )
    }

    private sealed class SongAlbumOutcome {
        data class Skipped(val videoId: String) : SongAlbumOutcome()
        data class Failed(val detail: String) : SongAlbumOutcome()
        data class Ok(
            val videoId: String,
            val mpreb: String,
            val olak: String,
            val title: String?,
            val hasCover: Boolean,
            val trackCount: Int,
        ) : SongAlbumOutcome()
    }

    @Test fun `live getTrackInfo exposes title artist channel art and duration`() = runTest {
        val videoCache = mockk<com.dustvalve.next.android.data.local.db.dao.YouTubeVideoCacheDao>(relaxed = true)
        val playlistCache = mockk<com.dustvalve.next.android.data.local.db.dao.YouTubePlaylistCacheDao>(relaxed = true)
        coEvery { videoCache.getById(any()) } returns null
        coEvery { videoCache.getByIds(any()) } returns emptyList()
        coEvery { playlistCache.getById(any()) } returns null
        val ytmRepo = mockk<com.dustvalve.next.android.domain.repository.YouTubeMusicRepository>(relaxed = true)
        coEvery { ytmRepo.lookupAlbumPlaylistForVideo(any()) } returns null
        val repo = YouTubeRepositoryImpl(
            client = ytClient,
            playerParser = com.dustvalve.next.android.data.remote.youtube.innertube.YouTubePlayerParser(),
            searchParser = com.dustvalve.next.android.data.remote.youtube.innertube.YouTubeSearchParser(),
            playlistParser = playlistParser,
            channelParser = channelParser,
            nextParser = com.dustvalve.next.android.data.remote.youtube.innertube.YouTubeNextParser(),
            videoCache = videoCache,
            playlistCache = playlistCache,
            youTubeMusicRepository = ytmRepo,
            albumResolver = albumResolver,
            ioDispatcher = io,
        )
        // Stable public videos across age / channel types. Avoid region-locked
        // music-video ids that /player rejects anonymously.
        val urls = listOf(
            "https://www.youtube.com/watch?v=jNQXAC9IVRw", // Me at the zoo (oldest)
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ", // Never Gonna Give You Up
            "https://www.youtube.com/watch?v=aqz-KE-bpKQ", // Big Buck Bunny
        )
        for (url in urls) {
            val track = repo.getTrackInfo(url)
            assertThat(track.title).isNotEmpty()
            assertThat(track.artist).isNotEmpty()
            assertThat(track.artistUrl).contains("youtube.com/")
            assertThat(track.artUrl).isNotEmpty()
            assertThat(track.duration).isGreaterThan(0)
            assertThat(track.id).startsWith("yt_")
            println(
                "[LIVE] trackInfo ${track.id} title='${track.title}' artist='${track.artist}' " +
                    "art=${track.artUrl.isNotBlank()} dur=${track.duration}",
            )
        }
    }

    @Test fun `live channel uploads UU playlist has title cover and tracks`() = runTest {
        // UU<channelWithoutUC> is the auto "Uploads" playlist - a different
        // playlist id family than PL*, still served via lockupViewModel today.
        val channelId = "UCHnyfMqiRRG1u-2MsSQLbXA" // Veritasium
        val uploadsId = "UU" + channelId.removePrefix("UC")
        val page = playlistParser.parse(ytClient.browse("VL$uploadsId"), uploadsId)
        assertThat(page.title).isNotNull()
        assertThat(page.title!!).isNotEmpty()
        assertThat(page.coverUrl).isNotNull()
        assertThat(page.tracks.size).isAtLeast(5)
        println(
            "[LIVE] UU playlist $uploadsId title='${page.title}' " +
                "cover=${page.coverUrl != null} tracks=${page.tracks.size}",
        )
    }

    // --- YouTube channels / YTM artists ------------------------------------

    @Test fun `live standard channel Videos tab has avatar and video tracks`() = runTest {
        // Veritasium - classic channel with a Videos tab (richGrid + lockups).
        val channelId = "UCHnyfMqiRRG1u-2MsSQLbXA"
        val page = channelParser.parse(
            ytClient.browse(channelId, params = VIDEOS_TAB_PARAMS),
            channelId,
        )
        assertThat(page.channelName).isNotNull()
        assertThat(page.avatarUrl).isNotNull()
        assertThat(page.avatarUrl!!).contains("googleusercontent.com")
        assertThat(page.tracks.size).isAtLeast(5)
        page.tracks.take(5).forEach { t ->
            assertThat(t.title).isNotEmpty()
            assertThat(t.id).startsWith("yt_")
            assertThat(t.artUrl).isNotEmpty()
        }
        println(
            "[LIVE] channel $channelId name='${page.channelName}' " +
                "avatar=${page.avatarUrl != null} tracks=${page.tracks.size}",
        )
    }

    @Test fun `live YTM artist Topic channel still yields avatar metadata`() = runTest {
        // Topic channels (common for YTM artists) often expose only a Home
        // tab of album lockups - no Videos richGrid. Avatar + name must still
        // populate ArtistDetail even when the track feed is empty.
        val search = ytmClient.search(query = "Radiohead", params = ARTISTS_PARAMS)
        val artistIds = collectBrowseIds(search, pageTypeContains = "ARTIST").take(3)
        assumeTrue("YTM artist search returned no results", artistIds.isNotEmpty())
        val failures = mutableListOf<String>()
        for (channelId in artistIds) {
            val page = channelParser.parse(
                ytClient.browse(channelId, params = VIDEOS_TAB_PARAMS),
                channelId,
            )
            val problems = mutableListOf<String>()
            if (page.channelName.isNullOrBlank()) problems += "blank name"
            if (page.avatarUrl.isNullOrBlank()) problems += "blank avatar"
            // Tracks may legitimately be empty on Topic-only channels.
            if (problems.isNotEmpty()) failures += "$channelId -> ${problems.joinToString()}"
            println(
                "[LIVE] ytm-artist $channelId name='${page.channelName}' " +
                    "avatar=${page.avatarUrl != null} tracks=${page.tracks.size}",
            )
        }
        assertThat(failures).isEmpty()
    }

    // --- Bandcamp ----------------------------------------------------------

    @Test fun `live Bandcamp artists expose photo and non-blank album art`() = runTest {
        val urls = listOf(
            "https://c418.bandcamp.com",
            "https://nofx.bandcamp.com",
            "https://burial.bandcamp.com",
            "https://aphextwin.bandcamp.com",
            "https://anginedepoitrine.bandcamp.com",
        )
        val failures = mutableListOf<String>()
        for (url in urls) {
            val artist = artistScraper.scrapeArtist(url)
            val problems = mutableListOf<String>()
            if (artist.name.isBlank() || artist.name == "Unknown Artist") {
                problems += "bad name='${artist.name}'"
            }
            if (artist.imageUrl.isNullOrBlank()) problems += "blank artist image"
            if (artist.albums.isEmpty()) problems += "zero albums"
            val blankArt = artist.albums.count { it.artUrl.isBlank() }
            // Allow a small minority of blank thumbs (hidden/private items),
            // but the majority of the visible discography must carry art.
            if (artist.albums.isNotEmpty() && blankArt * 2 >= artist.albums.size) {
                problems += "too many blank album arts ($blankArt/${artist.albums.size})"
            }
            artist.albums.take(8).forEach { alb ->
                if (alb.artUrl.isNotBlank() && !alb.artUrl.contains("_0.")) {
                    problems += "album '${alb.title}' art not upgraded to _0: ${alb.artUrl}"
                }
            }
            if (problems.isNotEmpty()) failures += "$url -> ${problems.joinToString()}"
            println(
                "[LIVE] bandcamp $url name='${artist.name}' image=${artist.imageUrl != null} " +
                    "albums=${artist.albums.size} blankArt=$blankArt",
            )
        }
        assertThat(failures).isEmpty()
    }

    @Test fun `live Bandcamp albums expose cover tracks and artist link`() = runTest {
        // Discover album URLs from live artist pages so renamed/moved albums
        // do not hard-fail the suite on a stale slug.
        val artistUrls = listOf(
            "https://c418.bandcamp.com",
            "https://burial.bandcamp.com",
            "https://aphextwin.bandcamp.com",
        )
        val albumUrls = mutableListOf<String>()
        for (artistUrl in artistUrls) {
            val artist = artistScraper.scrapeArtist(artistUrl)
            albumUrls += artist.albums.map { it.url }.filter { it.contains("/album/") }.take(2)
        }
        assumeTrue("No Bandcamp album URLs discovered", albumUrls.isNotEmpty())
        val failures = mutableListOf<String>()
        for (url in albumUrls) {
            val album = albumScraper.scrapeAlbum(url)
            val problems = mutableListOf<String>()
            if (album.title.isBlank()) problems += "blank title"
            if (album.artist.isBlank()) problems += "blank artist"
            if (album.artUrl.isBlank()) problems += "blank art"
            if (album.artUrl.isNotBlank() && !album.artUrl.contains("_0.")) {
                problems += "art not upgraded to _0: ${album.artUrl}"
            }
            if (album.artistUrl.isBlank()) problems += "blank artistUrl"
            if (album.tracks.isEmpty()) problems += "zero tracks"
            album.tracks.take(3).forEachIndexed { i, t ->
                if (t.title.isBlank()) problems += "track[$i] blank title"
            }
            if (problems.isNotEmpty()) failures += "$url -> ${problems.joinToString()}"
            println(
                "[LIVE] bandcamp-album $url title='${album.title}' " +
                    "tracks=${album.tracks.size} art=${album.artUrl.isNotBlank()}",
            )
        }
        assertThat(failures).isEmpty()
    }

    // --- helpers -----------------------------------------------------------

    private fun findAlbumBrowseId(root: JsonElement): String? {
        when (root) {
            is JsonObject -> {
                val browseEndpoint = root["browseEndpoint"] as? JsonObject
                if (browseEndpoint != null) {
                    val pageType = (
                        (
                            browseEndpoint["browseEndpointContextSupportedConfigs"]
                                as? JsonObject
                            )
                            ?.get("browseEndpointContextMusicConfig") as? JsonObject
                        )
                        ?.get("pageType")
                        ?.let { (it as? JsonPrimitive)?.content }
                    if (pageType == "MUSIC_PAGE_TYPE_ALBUM") {
                        val id = (browseEndpoint["browseId"] as? JsonPrimitive)?.content
                        if (!id.isNullOrBlank()) return id
                    }
                }
                for (v in root.values) findAlbumBrowseId(v)?.let { return it }
            }

            is JsonArray -> for (v in root) findAlbumBrowseId(v)?.let { return it }

            else -> Unit
        }
        return null
    }

    private fun collectBrowseIds(root: JsonElement, pageTypeContains: String): List<String> {
        val out = mutableListOf<String>()
        fun walk(el: JsonElement?) {
            when (el) {
                is JsonObject -> {
                    val browse = el["browseEndpoint"] as? JsonObject
                        ?: (el["navigationEndpoint"] as? JsonObject)
                            ?.get("browseEndpoint") as? JsonObject
                    if (browse != null) {
                        val pageType = browse["browseEndpointContextSupportedConfigs"]
                            .let { it as? JsonObject }
                            ?.get("browseEndpointContextMusicConfig")
                            .let { it as? JsonObject }
                            ?.get("pageType")
                            .let { it as? JsonPrimitive }
                            ?.content
                            .orEmpty()
                        val browseId = (browse["browseId"] as? JsonPrimitive)?.content
                        if (browseId != null && pageType.contains(pageTypeContains)) {
                            out += browseId
                        }
                    }
                    el.values.forEach { walk(it) }
                }

                is JsonArray -> el.forEach { walk(it) }

                else -> Unit
            }
        }
        walk(root)
        return out.distinct()
    }

    private fun collectYtPlaylistIds(root: JsonElement): List<String> {
        val out = mutableListOf<String>()
        fun walk(el: JsonElement?) {
            when (el) {
                is JsonObject -> {
                    val playlistId = (el["playlistId"] as? JsonPrimitive)?.content
                    if (playlistId != null && playlistId.startsWith("PL")) {
                        out += playlistId
                    }
                    el.values.forEach { walk(it) }
                }

                is JsonArray -> el.forEach { walk(it) }

                else -> Unit
            }
        }
        walk(root)
        return out.distinct()
    }

    private fun collectVideoIds(root: JsonElement): List<String> {
        val out = mutableListOf<String>()
        fun walk(el: JsonElement?) {
            when (el) {
                is JsonObject -> {
                    val videoId = (el["videoId"] as? JsonPrimitive)?.content
                    if (videoId != null && videoId.length == 11) {
                        out += videoId
                    }
                    el.values.forEach { walk(it) }
                }

                is JsonArray -> el.forEach { walk(it) }

                else -> Unit
            }
        }
        walk(root)
        return out.distinct()
    }

    private companion object {
        // Keep in sync with YouTubeRepositoryImpl / YouTubeMusicRepositoryImpl.
        const val VIDEOS_TAB_PARAMS = "EgZ2aWRlb3PyBgQKAjoA"
        const val YT_PLAYLIST_FILTER = "EgIQAw%3D%3D"
        const val PLAYLISTS_PARAMS = "EgWKAQIoAWoMEA4QChADEAQQCRAF"
        const val ALBUMS_PARAMS = "EgWKAQIYAWoMEA4QChADEAQQCRAF"
        const val ARTISTS_PARAMS = "EgWKAQIgAWoMEA4QChADEAQQCRAF"
        const val SONGS_PARAMS = "EgWKAQIIAWoMEA4QChADEAQQCRAF"
    }
}
