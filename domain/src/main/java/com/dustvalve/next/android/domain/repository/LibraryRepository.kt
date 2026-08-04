package com.dustvalve.next.android.domain.repository

import com.dustvalve.next.android.domain.model.FavoriteAlbumItem
import com.dustvalve.next.android.domain.model.FavoriteArtistItem
import com.dustvalve.next.android.domain.model.Track
import kotlinx.coroutines.flow.Flow

interface LibraryRepository {
    /** Toggles the favorite status and returns the new isFavorite value. */
    suspend fun toggleTrackFavorite(trackId: String): Boolean
    suspend fun addToRecent(track: Track)

    /** Favorited albums joined with their album rows; SQL ordering (pinned DESC, addedAt DESC) preserved. */
    fun getFavoriteAlbums(): Flow<List<FavoriteAlbumItem>>

    /** Favorited artists joined with their artist rows; SQL ordering (pinned DESC, addedAt DESC) preserved. */
    fun getFavoriteArtists(): Flow<List<FavoriteArtistItem>>
}
