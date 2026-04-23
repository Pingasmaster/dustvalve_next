package com.dustvalve.next.android.data.repository

import com.dustvalve.next.android.data.remote.youtube.YouTubeExtractorWrapper
import com.dustvalve.next.android.domain.model.AudioFormat
import com.dustvalve.next.android.domain.model.SearchResult
import com.dustvalve.next.android.domain.model.Track
import com.dustvalve.next.android.domain.repository.YouTubeRepository
import org.schabi.newpipe.extractor.Page
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YouTubeRepositoryImpl @Inject constructor(
    private val extractor: YouTubeExtractorWrapper,
) : YouTubeRepository {

    override suspend fun search(
        query: String,
        filter: String?,
        page: Any?,
    ): Pair<List<SearchResult>, Any?> {
        val contentFilter = when (filter) {
            "songs" -> listOf("videos")
            "playlists" -> listOf("playlists")
            "artists" -> listOf("channels")
            "music_songs", "music_videos", "music_albums",
            "music_playlists", "music_artists" -> listOf(filter)
            else -> emptyList()
        }
        val npPage = page as? Page
        return extractor.search(query, contentFilter, npPage)
    }

    override suspend fun getStreamUrl(videoUrl: String): String {
        return extractor.getStreamUrl(videoUrl)
    }

    override suspend fun getDownloadableStream(videoUrl: String): Pair<String, AudioFormat> {
        return extractor.getDownloadableStream(videoUrl)
    }

    override suspend fun getTrackInfo(videoUrl: String): Track {
        return extractor.getStreamInfo(videoUrl)
    }

    override suspend fun getRecommendations(videoUrl: String): List<SearchResult> {
        return extractor.getRelatedItems(videoUrl)
    }

    override suspend fun getPlaylistTracks(playlistUrl: String): Pair<List<Track>, String> {
        return extractor.getPlaylistItems(playlistUrl)
    }

    override suspend fun getChannelVideos(channelUrl: String, page: Any?): Triple<List<Track>, String?, Any?> {
        val npPage = page as? Page
        return extractor.getChannelVideos(channelUrl, npPage)
    }
}
