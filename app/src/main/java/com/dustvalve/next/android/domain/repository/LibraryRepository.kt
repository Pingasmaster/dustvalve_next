package com.dustvalve.next.android.domain.repository

import com.dustvalve.next.android.domain.model.Track

interface LibraryRepository {
    /** Toggles the favorite status and returns the new isFavorite value. */
    suspend fun toggleTrackFavorite(trackId: String): Boolean
    suspend fun addToRecent(track: Track)
}
