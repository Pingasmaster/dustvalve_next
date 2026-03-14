package com.dustvalve.next.android.domain.repository

import com.dustvalve.next.android.domain.model.Artist
import com.dustvalve.next.android.domain.model.Track
import kotlinx.coroutines.flow.Flow

interface ArtistRepository {
    suspend fun getArtistDetail(url: String): Artist
    fun getArtistDetailFlow(url: String): Flow<Artist>
    suspend fun toggleFavorite(artistId: String)
    suspend fun isFavorite(artistId: String): Boolean
    suspend fun getArtistMixTracks(albumIds: List<String>): List<Track>
    fun getFavoriteArtists(): Flow<List<Artist>>
    suspend fun setAutoDownload(artistId: String, autoDownload: Boolean)
}
