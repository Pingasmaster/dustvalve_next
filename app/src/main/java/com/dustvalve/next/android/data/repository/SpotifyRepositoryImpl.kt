package com.dustvalve.next.android.data.repository

import com.dustvalve.next.android.data.remote.spotify.AlbumInfo
import com.dustvalve.next.android.data.remote.spotify.ArtistInfo
import com.dustvalve.next.android.data.remote.spotify.SpotifyBridge
import com.dustvalve.next.android.data.remote.spotify.SpotifyExtractorWrapper
import com.dustvalve.next.android.domain.model.AudioFormat
import com.dustvalve.next.android.domain.model.SearchResult
import com.dustvalve.next.android.domain.model.Track
import com.dustvalve.next.android.domain.repository.SpotifyRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpotifyRepositoryImpl @Inject constructor(
    private val extractor: SpotifyExtractorWrapper,
) : SpotifyRepository {

    override suspend fun search(query: String, filter: String?): List<SearchResult> {
        return extractor.search(query, filter)
    }

    override suspend fun getTrackInfo(uri: String): Track {
        return extractor.getTrackInfo(uri)
    }

    override suspend fun getAlbumTracks(uri: String): Pair<List<Track>, AlbumInfo> {
        return extractor.getAlbumTracks(uri)
    }

    override suspend fun getArtistInfo(uri: String): ArtistInfo {
        return extractor.getArtistInfo(uri)
    }

    override suspend fun getPlaylistTracks(uri: String): Pair<List<Track>, String> {
        return extractor.getPlaylistTracks(uri)
    }

    override suspend fun downloadTrack(uri: String, outputPath: String): AudioFormat {
        return extractor.downloadTrackToFile(uri, outputPath)
    }

    override suspend fun initialize(cacheDir: String, credentialsPath: String) {
        extractor.initialize(cacheDir, credentialsPath)
    }

    override fun shutdown() {
        extractor.shutdown()
    }

    override fun hasCachedCredentials(credentialsPath: String): Boolean {
        return extractor.hasCachedCredentials(credentialsPath)
    }

    override fun getAuthorizationURL(): String {
        return extractor.getAuthorizationURL()
    }

    override suspend fun handleOAuthCode(code: String, state: String) {
        extractor.handleOAuthCode(code, state)
    }

    override fun isAvailable(): Boolean {
        return SpotifyBridge.isAvailable()
    }
}
