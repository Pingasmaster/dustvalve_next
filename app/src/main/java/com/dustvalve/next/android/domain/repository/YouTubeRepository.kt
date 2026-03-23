package com.dustvalve.next.android.domain.repository

import com.dustvalve.next.android.domain.model.AudioFormat
import com.dustvalve.next.android.domain.model.SearchResult
import com.dustvalve.next.android.domain.model.Track

interface YouTubeRepository {
    suspend fun search(query: String, filter: String? = null, page: Any? = null): Pair<List<SearchResult>, Any?>
    suspend fun getStreamUrl(videoUrl: String): String
    suspend fun getDownloadableStream(videoUrl: String): Pair<String, AudioFormat>
    suspend fun getTrackInfo(videoUrl: String): Track
    suspend fun getRecommendations(videoUrl: String): List<SearchResult>
    suspend fun getPlaylistTracks(playlistUrl: String): Pair<List<Track>, String>
    suspend fun getChannelVideos(channelUrl: String, page: Any? = null): Triple<List<Track>, String?, Any?>
}
