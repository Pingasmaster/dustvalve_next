package com.dustvalve.next.android.domain.repository

import com.dustvalve.next.android.domain.model.Track

/**
 * Cache-side access to the tracks table for remote pages that must resolve
 * in library/playlist joins later (YouTube artist feeds, title lookups).
 */
interface TrackCacheRepository {

    /**
     * Upsert remote track pages so library/playlist joins resolve. The
     * underlying insert is a true upsert (deliberately NOT REPLACE - REPLACE
     * would cascade-delete playlist_tracks memberships). No transaction -
     * matches today's page-caching call sites.
     */
    suspend fun cacheTracks(tracks: List<Track>)

    /** One-shot lookup decorated with the current favorite flag; null when the id is not cached. */
    suspend fun getTrack(trackId: String): Track?

    /** Chunk-safe bulk lookup (safe above SQLite's 900-bind-parameter limit) decorated with favorite flags. */
    suspend fun getTracks(trackIds: List<String>): List<Track>
}
