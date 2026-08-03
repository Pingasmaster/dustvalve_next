package com.dustvalve.next.android.domain.repository

import com.dustvalve.next.android.domain.model.Album
import com.dustvalve.next.android.domain.model.Artist
import com.dustvalve.next.android.domain.model.MusicCollection
import com.dustvalve.next.android.domain.model.SearchResult
import com.dustvalve.next.android.domain.model.SoundCloudHomeFeed
import com.dustvalve.next.android.domain.model.Track

/**
 * Anonymous SoundCloud api-v2 access (NewPipe / yt-dlp style client_id).
 *
 * Resolve is folded into [getTrack] / [getArtist] / [getAlbum] /
 * [getCollection]: pass a permalink URL or a numeric / `sc_` id where
 * applicable. Stream URLs are resolved on demand via [getStreamUrl].
 */
interface SoundCloudRepository {
    suspend fun getHome(genre: String = "all-music"): SoundCloudHomeFeed
    suspend fun search(query: String, filter: String? = null): List<SearchResult>
    suspend fun getTrack(urlOrId: String): Track
    suspend fun getStreamUrl(track: Track): String
    suspend fun getArtist(url: String): Artist
    suspend fun getArtistTracks(url: String, continuation: Any? = null): MusicCollection
    suspend fun getAlbum(url: String): Album
    suspend fun getCollection(url: String, continuation: Any? = null): MusicCollection
}
