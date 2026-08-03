package com.dustvalve.next.android.domain.repository

import com.dustvalve.next.android.domain.model.AudioFormat
import com.dustvalve.next.android.domain.model.SearchResult
import com.dustvalve.next.android.domain.model.Track

/** Result of [YouTubeRepository.getPlaylistTracks]. */
data class YouTubePlaylistResult(val tracks: List<Track>, val title: String, val coverUrl: String? = null)

/** One page of [YouTubeRepository.getChannelVideos]. */
data class YouTubeChannelResult(val tracks: List<Track>, val channelName: String?, val nextPage: Any?, val avatarUrl: String? = null)

interface YouTubeRepository {
    suspend fun search(query: String, filter: String? = null, page: Any? = null): Pair<List<SearchResult>, Any?>
    suspend fun getStreamUrl(videoUrl: String): String
    suspend fun getDownloadableStream(videoUrl: String): Pair<String, AudioFormat>
    suspend fun getTrackInfo(videoUrl: String): Track
    suspend fun getRecommendations(videoUrl: String): List<SearchResult>
    suspend fun getPlaylistTracks(playlistUrl: String): YouTubePlaylistResult

    /**
     * Fetches one page of a YouTube Mix (auto-generated radio playlist, IDs
     * starting with `RD`). Pass `cursor = null` for the first page; pass the
     * returned cursor for subsequent pages. `seenVideoIds` is used to dedupe
     * against already-loaded tracks (YT re-returns the last ~24 items on each
     * paginated call). Returns (newTracks, mixTitle, nextCursorOrNullIfEnd).
     */
    suspend fun getMixPage(mixUrl: String, cursor: Any? = null, seenVideoIds: Set<String> = emptySet()): Triple<List<Track>, String, Any?>
    suspend fun getChannelVideos(channelUrl: String, page: Any? = null): YouTubeChannelResult
}
