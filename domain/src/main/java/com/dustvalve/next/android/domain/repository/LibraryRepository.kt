package com.dustvalve.next.android.domain.repository

import com.dustvalve.next.android.domain.model.FavoriteAlbumItem
import com.dustvalve.next.android.domain.model.FavoriteArtistItem
import com.dustvalve.next.android.domain.model.Track
import kotlinx.coroutines.flow.Flow

interface LibraryRepository {
    /**
     * Toggles favorite for an already-cached track id. When favoriting, the
     * tracks row must already exist (Favorites is an INNER JOIN); returns
     * false without writing an orphan favorite when it does not.
     */
    suspend fun toggleTrackFavorite(trackId: String): Boolean

    /**
     * Ensures [track] is cached in Room, then toggles favorite. Prefer this
     * when the caller has a Track (player, queue, media session).
     */
    suspend fun toggleTrackFavorite(track: Track): Boolean
    suspend fun addToRecent(track: Track)

    /** Favorited albums joined with their album rows; SQL ordering (pinned DESC, addedAt DESC) preserved. */
    fun getFavoriteAlbums(): Flow<List<FavoriteAlbumItem>>

    /** Favorited artists joined with their artist rows; SQL ordering (pinned DESC, addedAt DESC) preserved. */
    fun getFavoriteArtists(): Flow<List<FavoriteArtistItem>>
}
