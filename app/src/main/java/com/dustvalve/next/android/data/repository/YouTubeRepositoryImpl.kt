package com.dustvalve.next.android.data.repository

import com.dustvalve.next.android.data.remote.youtube.innertube.YouTubeChannelParser
import com.dustvalve.next.android.data.remote.youtube.innertube.YouTubeInnertubeClient
import com.dustvalve.next.android.data.remote.youtube.innertube.YouTubeNextParser
import com.dustvalve.next.android.data.remote.youtube.innertube.YouTubePlayerParser
import com.dustvalve.next.android.data.remote.youtube.innertube.YouTubePlaylistParser
import com.dustvalve.next.android.data.remote.youtube.innertube.YouTubeSearchParser
import com.dustvalve.next.android.domain.model.AudioFormat
import com.dustvalve.next.android.domain.model.SearchResult
import com.dustvalve.next.android.domain.model.SearchResultType
import com.dustvalve.next.android.domain.model.Track
import com.dustvalve.next.android.domain.repository.YouTubeRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * First-party YouTube repository backed by the no-auth Innertube layer
 * (see com.dustvalve.next.android.data.remote.youtube.innertube). Public
 * signatures match the YouTubeRepository interface so callers (player VM,
 * search VM, etc.) keep working unchanged.
 */
@Singleton
class YouTubeRepositoryImpl @Inject constructor(
    private val client: YouTubeInnertubeClient,
    private val playerParser: YouTubePlayerParser,
    private val searchParser: YouTubeSearchParser,
    private val playlistParser: YouTubePlaylistParser,
    private val channelParser: YouTubeChannelParser,
    private val nextParser: YouTubeNextParser,
) : YouTubeRepository {

    /**
     * filter: "songs" -> videos only, "playlists" -> playlists only,
     * "artists" -> channels only. Pagination uses the search response's
     * continuation token; the [page] parameter is the opaque token from
     * the previous call (or null on the first page).
     *
     * YT Innertube has dedicated `params` filter tokens (sp= URL params on
     * the website). We don't currently send those; instead we filter the
     * mixed results client-side. That matches what the legacy NewPipe
     * wrapper did and avoids rotating filter tokens we'd have to update.
     */
    override suspend fun search(
        query: String,
        filter: String?,
        page: Any?,
    ): Pair<List<SearchResult>, Any?> {
        // page is the continuation token from the previous call. We always
        // re-issue the same /search query; the search continuation API
        // requires a dedicated route we have not implemented yet. The
        // previous NewPipe-backed implementation exposed a Page object the
        // caller passed back and forth; callers (search VM, etc.) tolerate
        // a null next-page sentinel meaning "no more pages".
        val response = client.search(query = query)
        val parsed = searchParser.parse(response)
        val filtered = when (filter) {
            "songs", "videos" -> parsed.items.filter { it.type == SearchResultType.YOUTUBE_TRACK }
            "playlists" -> parsed.items.filter { it.type == SearchResultType.YOUTUBE_PLAYLIST }
            "artists" -> parsed.items.filter { it.type == SearchResultType.YOUTUBE_ARTIST }
            else -> parsed.items
        }
        // Surface no continuation: callers will treat this as a single-page
        // response, matching the legacy behaviour for filtered searches.
        // page param accepted for ABI parity but unused.
        @Suppress("UNUSED_VARIABLE") val unused = page
        return filtered to null
    }

    override suspend fun getStreamUrl(videoUrl: String): String {
        val videoId = extractVideoId(videoUrl)
            ?: throw IllegalArgumentException("Cannot extract videoId from $videoUrl")
        val response = client.player(videoId)
        return playerParser.parsePlayerStreamInfo(response).streamUrl
    }

    override suspend fun getDownloadableStream(videoUrl: String): Pair<String, AudioFormat> {
        val videoId = extractVideoId(videoUrl)
            ?: throw IllegalArgumentException("Cannot extract videoId from $videoUrl")
        val response = client.player(videoId)
        val info = playerParser.parsePlayerStreamInfo(response)
        return info.streamUrl to info.format
    }

    override suspend fun getTrackInfo(videoUrl: String): Track {
        val videoId = extractVideoId(videoUrl)
            ?: throw IllegalArgumentException("Cannot extract videoId from $videoUrl")
        val response = client.player(videoId)
        return playerParser.parseTrack(response, videoId)
    }

    override suspend fun getRecommendations(videoUrl: String): List<SearchResult> {
        val videoId = extractVideoId(videoUrl)
            ?: throw IllegalArgumentException("Cannot extract videoId from $videoUrl")
        val response = client.next(videoId)
        return nextParser.parse(response)
    }

    override suspend fun getPlaylistTracks(playlistUrl: String): Pair<List<Track>, String> {
        val playlistId = extractPlaylistId(playlistUrl)
            ?: throw IllegalArgumentException("Cannot extract playlistId from $playlistUrl")
        val response = client.browse("VL$playlistId")
        val first = playlistParser.parse(response, playlistId)
        val all = first.tracks.toMutableList()

        var cont = first.continuation
        var safety = 0
        // Paginate through continuations until exhausted, capped at 20
        // pages (~2k tracks) to avoid runaway loops on huge playlists.
        while (cont != null && safety < 20) {
            val contResp = client.browseContinuation(cont)
            val nextPage = playlistParser.parseContinuation(contResp, playlistId, all.size + 1)
            all += nextPage.tracks
            cont = nextPage.continuation
            safety += 1
        }
        return all to (first.title ?: "")
    }

    override suspend fun getChannelVideos(
        channelUrl: String,
        page: Any?,
    ): Triple<List<Track>, String?, Any?> {
        val channelId = extractChannelId(channelUrl)
            ?: throw IllegalArgumentException("Cannot extract channelId from $channelUrl")
        val token = page as? ChannelPageToken
        return if (token == null) {
            val response = client.browse(channelId, params = VIDEOS_TAB_PARAMS)
            val parsed = channelParser.parse(response, channelId)
            Triple(
                parsed.tracks,
                parsed.channelName,
                parsed.continuation?.let { ChannelPageToken(channelId, parsed.channelName, parsed.tracks.size, it) },
            )
        } else {
            val response = client.browseContinuation(token.continuation)
            val parsed = channelParser.parseContinuation(response, channelId, token.channelName, token.totalSoFar + 1)
            val newTotal = token.totalSoFar + parsed.tracks.size
            Triple(
                parsed.tracks,
                token.channelName,
                parsed.continuation?.let { ChannelPageToken(channelId, token.channelName, newTotal, it) },
            )
        }
    }

    /** Opaque page token for getChannelVideos pagination. */
    private data class ChannelPageToken(
        val channelId: String,
        val channelName: String?,
        val totalSoFar: Int,
        val continuation: String,
    )

    private fun extractVideoId(url: String): String? {
        val patterns = listOf(
            Regex("[?&]v=([a-zA-Z0-9_-]{11})"),
            Regex("/shorts/([a-zA-Z0-9_-]{11})"),
            Regex("youtu\\.be/([a-zA-Z0-9_-]{11})"),
        )
        for (p in patterns) p.find(url)?.groupValues?.getOrNull(1)?.let { return it }
        // Bare 11-char videoId
        if (url.matches(Regex("[a-zA-Z0-9_-]{11}"))) return url
        return null
    }

    private fun extractPlaylistId(url: String): String? {
        Regex("[?&]list=([A-Za-z0-9_-]+)").find(url)?.groupValues?.getOrNull(1)?.let { return it }
        // Allow callers to pass a bare playlistId
        if (url.matches(Regex("[A-Za-z0-9_-]+")) && url.length in 8..64) return url
        return null
    }

    private fun extractChannelId(url: String): String? {
        Regex("/channel/(UC[A-Za-z0-9_-]{22})").find(url)?.groupValues?.getOrNull(1)?.let { return it }
        if (url.matches(Regex("UC[A-Za-z0-9_-]{22}"))) return url
        return null
    }

    private companion object {
        // Opaque YouTube `params` token selecting the channel "Videos" tab.
        // This is the same value Metrolist / NewPipe / yt-dlp use; YT has
        // not rotated it in years.
        const val VIDEOS_TAB_PARAMS = "EgZ2aWRlb3PyBgQKAjoA"
    }
}
