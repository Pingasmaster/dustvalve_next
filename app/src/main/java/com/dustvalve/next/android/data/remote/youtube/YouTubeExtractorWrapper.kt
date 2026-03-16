package com.dustvalve.next.android.data.remote.youtube

import com.dustvalve.next.android.domain.model.SearchResult
import com.dustvalve.next.android.domain.model.SearchResultType
import com.dustvalve.next.android.domain.model.Track
import com.dustvalve.next.android.domain.model.TrackSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.ListExtractor
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YouTubeExtractorWrapper @Inject constructor() {

    @Volatile
    private var initialized = false

    private fun ensureInitialized() {
        if (!initialized) {
            synchronized(this) {
                if (!initialized) {
                    NewPipe.init(NewPipeDownloader())
                    initialized = true
                }
            }
        }
    }

    suspend fun search(
        query: String,
        contentFilter: List<String>?,
        page: Page? = null,
    ): Pair<List<SearchResult>, Page?> = withContext(Dispatchers.IO) {
        ensureInitialized()
        val service = ServiceList.YouTube
        val extractor = service.getSearchExtractor(query, contentFilter ?: emptyList(), "")

        val infoPage: ListExtractor.InfoItemsPage<InfoItem> = if (page == null) {
            extractor.fetchPage()
            extractor.initialPage
        } else {
            extractor.getPage(page)
        }

        val results = infoPage.items.mapNotNull { item ->
            mapInfoItemToSearchResult(item)
        }

        results to infoPage.nextPage
    }

    suspend fun getStreamUrl(videoUrl: String): String = withContext(Dispatchers.IO) {
        ensureInitialized()
        val extractor = ServiceList.YouTube.getStreamExtractor(videoUrl)
        extractor.fetchPage()
        val audioStreams = extractor.audioStreams ?: emptyList()
        pickBestAudioStream(audioStreams)?.content
            ?: throw IllegalStateException("No audio streams available for $videoUrl")
    }

    suspend fun getStreamInfo(videoUrl: String): Track = withContext(Dispatchers.IO) {
        ensureInitialized()
        val extractor = ServiceList.YouTube.getStreamExtractor(videoUrl)
        extractor.fetchPage()

        val videoId = extractVideoId(videoUrl) ?: extractor.id
        val audioStreams = extractor.audioStreams ?: emptyList()
        val bestStream = pickBestAudioStream(audioStreams)

        Track(
            id = "yt_$videoId",
            albumId = "yt_album_$videoId",
            title = extractor.name,
            artist = extractor.uploaderName,
            artistUrl = extractor.uploaderUrl,
            trackNumber = 0,
            duration = extractor.length.toFloat(),
            streamUrl = bestStream?.content ?: videoUrl,
            artUrl = extractor.thumbnails.firstOrNull()?.url ?: "",
            albumTitle = "",
            source = TrackSource.YOUTUBE,
        )
    }

    suspend fun getRelatedItems(videoUrl: String): List<SearchResult> = withContext(Dispatchers.IO) {
        ensureInitialized()
        val extractor = ServiceList.YouTube.getStreamExtractor(videoUrl)
        extractor.fetchPage()

        try {
            val relatedPage = extractor.relatedItems
            relatedPage?.items?.mapNotNull { item ->
                mapInfoItemToSearchResult(item)
            } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun getPlaylistItems(playlistUrl: String): List<Track> = withContext(Dispatchers.IO) {
        ensureInitialized()
        val extractor = ServiceList.YouTube.getPlaylistExtractor(playlistUrl)
        extractor.fetchPage()

        val tracks = mutableListOf<Track>()
        var page: ListExtractor.InfoItemsPage<StreamInfoItem> = extractor.initialPage

        while (true) {
            for (item in page.items) {
                val videoId = extractVideoId(item.url) ?: md5Hash(item.url).take(12)
                tracks.add(
                    Track(
                        id = "yt_$videoId",
                        albumId = "yt_playlist_${md5Hash(playlistUrl).take(12)}",
                        title = item.name ?: "Unknown",
                        artist = item.uploaderName ?: "Unknown",
                        artistUrl = item.uploaderUrl ?: "",
                        trackNumber = tracks.size + 1,
                        duration = item.duration.toFloat(),
                        streamUrl = item.url,
                        artUrl = item.thumbnails.firstOrNull()?.url ?: "",
                        albumTitle = "",
                        source = TrackSource.YOUTUBE,
                    )
                )
            }

            if (!page.hasNextPage()) break
            page = extractor.getPage(page.nextPage)
        }

        tracks
    }

    private fun mapInfoItemToSearchResult(item: InfoItem): SearchResult? {
        return when (item) {
            is StreamInfoItem -> SearchResult(
                type = SearchResultType.YOUTUBE_TRACK,
                name = item.name ?: return null,
                url = item.url ?: return null,
                imageUrl = item.thumbnails.firstOrNull()?.url,
                artist = item.uploaderName,
                album = null,
                genre = null,
                releaseDate = null,
            )
            is PlaylistInfoItem -> SearchResult(
                type = SearchResultType.YOUTUBE_PLAYLIST,
                name = item.name ?: return null,
                url = item.url ?: return null,
                imageUrl = item.thumbnails.firstOrNull()?.url,
                artist = item.uploaderName,
                album = null,
                genre = null,
                releaseDate = null,
            )
            is ChannelInfoItem -> SearchResult(
                type = SearchResultType.YOUTUBE_ARTIST,
                name = item.name ?: return null,
                url = item.url ?: return null,
                imageUrl = item.thumbnails.firstOrNull()?.url,
                artist = null,
                album = null,
                genre = null,
                releaseDate = null,
            )
            else -> null
        }
    }

    private fun pickBestAudioStream(streams: List<AudioStream>): AudioStream? {
        if (streams.isEmpty()) return null
        // Prefer OPUS/M4A, highest bitrate
        return streams
            .sortedWith(
                compareByDescending<AudioStream> { it.averageBitrate }
            )
            .firstOrNull()
    }

    private fun extractVideoId(url: String): String? {
        val patterns = listOf(
            Regex("[?&]v=([a-zA-Z0-9_-]{11})"),
            Regex("/shorts/([a-zA-Z0-9_-]{11})"),
            Regex("youtu\\.be/([a-zA-Z0-9_-]{11})"),
        )
        for (pattern in patterns) {
            pattern.find(url)?.groupValues?.getOrNull(1)?.let { return it }
        }
        return null
    }

    private fun md5Hash(input: String): String {
        val digest = MessageDigest.getInstance("MD5")
        return digest.digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}
