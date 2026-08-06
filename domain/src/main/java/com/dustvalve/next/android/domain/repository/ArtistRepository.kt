package com.dustvalve.next.android.domain.repository

import com.dustvalve.next.android.domain.model.Artist
import com.dustvalve.next.android.domain.model.Track
import kotlinx.coroutines.flow.Flow

interface ArtistRepository {
    suspend fun getArtistDetail(url: String): Artist
    fun getArtistDetailFlow(url: String): Flow<Artist>
    suspend fun toggleFavorite(artistId: String)
    suspend fun isFavorite(artistId: String): Boolean

    /**
     * The artist's cached tracks across [albumIds], in random order. Shuffled
     * by the implementation because a mix that returns a stable order is just
     * "play the first album".
     */
    suspend fun getArtistMixTracks(albumIds: List<String>): List<Track>

    /**
     * Of [albumIds], those with no cached tracks yet. Drives the mix preload:
     * an artist's albums are listed without their track lists, so the mix pool
     * only covers albums that happen to have been opened or downloaded until
     * the missing ones are fetched.
     */
    suspend fun albumIdsMissingTracks(albumIds: List<String>): List<String>
    suspend fun setAutoDownload(artistId: String, autoDownload: Boolean)

    /**
     * Best-effort cache of a remote-source artist row WITHOUT touching
     * favorites (any failure is swallowed). The insert is a REPLACE, so the
     * artist-detail load path calls this on every visit to refresh cached
     * name/image/bio - library INNER JOINs on the artist id then resolve with
     * fresh metadata.
     */
    suspend fun cacheRemoteArtist(artist: Artist, source: String)

    /**
     * Remote-source (non-Bandcamp) favorite path. Best-effort persist of the
     * artist row (so library INNER JOINs on the artist id resolve) THEN the
     * favorites insert of type ARTIST. Deliberately NOT transactional - a
     * failed artist-row insert must still favorite, matching the historical
     * two-step sequence. Bandcamp keeps using [toggleFavorite].
     */
    suspend fun favoriteRemoteArtist(artist: Artist, source: String)

    /** Favorites-row delete only (the remote-source unfavorite path); the artist row stays cached. */
    suspend fun unfavoriteArtist(artistId: String)
}
