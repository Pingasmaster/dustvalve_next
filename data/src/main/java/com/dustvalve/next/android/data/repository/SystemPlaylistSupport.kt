package com.dustvalve.next.android.data.repository

import androidx.room.withTransaction
import com.dustvalve.next.android.data.local.db.DustvalveNextDatabase
import com.dustvalve.next.android.data.local.db.dao.FavoriteDao
import com.dustvalve.next.android.data.local.db.dao.PlaylistDao
import com.dustvalve.next.android.data.local.db.dao.TrackDao
import com.dustvalve.next.android.data.local.db.entity.PlaylistEntity
import com.dustvalve.next.android.data.local.db.entity.PlaylistTrackEntity
import com.dustvalve.next.android.data.local.db.entity.TrackEntity
import com.dustvalve.next.android.data.mapper.toDomain
import com.dustvalve.next.android.domain.model.Playlist
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

// System-playlist (Favorites / Downloads / Recent) helpers for
// PlaylistRepositoryImpl, extracted 1:1 for cohesion. Behavior must stay
// byte-identical to the former private members.

/**
 * Reactive id set of favorited tracks. Combine this into any Flow that
 * decorates tracks with isFavorite so heart toggles re-emit.
 */
internal fun FavoriteDao.trackFavoriteIdsFlow(): Flow<Set<String>> = getAllTrackFavoriteIdsFlow().map { it.toSet() }

internal fun isSystemPlaylistId(playlistId: String): Boolean = when (playlistId) {
    Playlist.ID_FAVORITES, Playlist.ID_DOWNLOADS, Playlist.ID_RECENT -> true
    else -> false
}

/**
 * Merges a source-table list (favorites / downloaded) with an optional
 * manual-order override from `playlist_tracks`:
 *
 *  - Tracks present in both source AND override: take the override order.
 *  - Tracks in source but not in override: append in their source order
 *    (so newly-favorited tracks land at the end of the custom list).
 *  - Tracks in override but not in source: drop (unfavorited / undownloaded).
 *  - Override empty -> return source verbatim.
 */
internal fun mergeSystemPlaylist(source: List<TrackEntity>, ordered: List<TrackEntity>): List<TrackEntity> {
    if (ordered.isEmpty()) return source
    val sourceById = source.associateBy { it.id }
    val byOrder = ordered.mapNotNull { sourceById[it.id] }
    val orderedIds = byOrder.mapTo(HashSet()) { it.id }
    val tail = source.filter { it.id !in orderedIds }
    return byOrder + tail
}

/**
 * Creates a user (non-system) playlist row and returns the domain model.
 * Kept outside [PlaylistRepositoryImpl] so that class stays under the
 * TooManyFunctions ceiling.
 */
internal suspend fun insertUserPlaylist(
    playlistDao: PlaylistDao,
    name: String,
    shapeKey: String? = null,
    iconUrl: String? = null,
    sourceUrl: String? = null,
): Playlist {
    val playlist = PlaylistEntity(
        id = UUID.randomUUID().toString(),
        name = name,
        shapeKey = shapeKey,
        iconUrl = iconUrl,
        isSystem = false,
        isPinned = false,
        sortOrder = 0,
        trackCount = 0,
        sourceUrl = sourceUrl,
    )
    playlistDao.insertPlaylist(playlist)
    return playlist.toDomain()
}

/**
 * Rewrites the playlist_tracks override for a system playlist so its
 * rows exactly match the merged view the UI is displaying right now
 * (override order, minus tracks that left the source, plus new source
 * tracks appended). Guarantees contiguous 0..n-1 positions, which
 * reorderTrack's range-shift arithmetic depends on.
 */
internal suspend fun reseedSystemPlaylistFromMergedView(
    database: DustvalveNextDatabase,
    playlistDao: PlaylistDao,
    trackDao: TrackDao,
    playlistId: String,
) {
    val source: List<TrackEntity> = when (playlistId) {
        Playlist.ID_FAVORITES -> trackDao.getFavorites().first()
        Playlist.ID_DOWNLOADS -> trackDao.getDownloaded().first()
        Playlist.ID_RECENT -> trackDao.getRecent().first()
        else -> return
    }
    if (source.isEmpty()) return
    val ordered = playlistDao.getTracksInPlaylistSync(playlistId)
    val merged = mergeSystemPlaylist(source, ordered)
    database.withTransaction {
        playlistDao.clearPlaylistTracks(playlistId)
        playlistDao.insertPlaylistTracks(
            merged.mapIndexed { index, t ->
                PlaylistTrackEntity(playlistId = playlistId, trackId = t.id, position = index)
            },
        )
    }
}
