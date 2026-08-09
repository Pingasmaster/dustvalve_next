package com.dustvalve.next.android.domain.repository

import com.dustvalve.next.android.data.local.scanner.ScanResult
import com.dustvalve.next.android.domain.model.Track
import kotlinx.coroutines.flow.Flow

interface LocalMusicRepository {
    suspend fun scan(): ScanResult
    suspend fun addFolder(uri: String)
    suspend fun removeFolder(uri: String)
    suspend fun clearAll()
    suspend fun scheduleSyncWork()
    suspend fun cancelSyncWork()

    /**
     * Re-takes READ|WRITE persistable grants for every saved SAF tree when the
     * platform still allows it. Returns URIs that lack a durable write grant
     * (pre-fix READ-only trees, or revoked access) so the UI can prompt a
     * re-pick.
     */
    suspend fun ensurePersistableWriteGrants(): List<String>

    /**
     * All local tracks decorated with live favorite flags: re-emits when a
     * favorite toggles, not only when the track list changes.
     */
    fun getLocalTracks(): Flow<List<Track>>

    /** One-shot cached-track lookup decorated with the current favorite flag; null when not cached. */
    suspend fun getLocalTrack(trackId: String): Track?

    /** Local-library title/artist/album substring search (SQL LIMIT 50) with favorite decoration. */
    suspend fun searchLocalTracks(query: String): List<Track>

    /** Deletes DB rows only; callers own the surrounding file/SAF deletion (incl. local_art covers). */
    suspend fun deleteTrackRows(trackIds: List<String>)
}
