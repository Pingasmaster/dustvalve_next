package com.dustvalve.next.android.domain.repository

import com.dustvalve.next.android.data.remote.spotify.AlbumInfo
import com.dustvalve.next.android.data.remote.spotify.ArtistInfo
import com.dustvalve.next.android.domain.model.AudioFormat
import com.dustvalve.next.android.domain.model.SearchResult
import com.dustvalve.next.android.domain.model.Track

interface SpotifyRepository {
    suspend fun search(query: String, filter: String? = null): List<SearchResult>
    suspend fun getTrackInfo(uri: String): Track
    suspend fun getAlbumTracks(uri: String): Pair<List<Track>, AlbumInfo>
    suspend fun getArtistInfo(uri: String): ArtistInfo
    suspend fun getPlaylistTracks(uri: String): Pair<List<Track>, String>
    suspend fun downloadTrack(uri: String, outputPath: String): AudioFormat
    suspend fun initialize(cacheDir: String, credentialsPath: String)
    fun shutdown()
    fun hasCachedCredentials(credentialsPath: String): Boolean
    fun getAuthorizationURL(): String
    suspend fun handleOAuthCode(code: String, state: String)
    fun isAvailable(): Boolean
}
