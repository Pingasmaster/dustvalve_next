package com.dustvalve.next.android.domain.repository

import com.dustvalve.next.android.domain.model.Track
import kotlinx.coroutines.flow.Flow

interface LibraryRepository {
    fun getFavoriteTracks(): Flow<List<Track>>
    /** Toggles the favorite status and returns the new isFavorite value. */
    suspend fun toggleTrackFavorite(trackId: String): Boolean
    fun getRecentTracks(): Flow<List<Track>>
    suspend fun addToRecent(track: Track)
}
