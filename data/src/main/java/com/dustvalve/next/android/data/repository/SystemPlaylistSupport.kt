package com.dustvalve.next.android.data.repository

import com.dustvalve.next.android.data.local.db.dao.FavoriteDao
import com.dustvalve.next.android.data.local.db.entity.TrackEntity
import com.dustvalve.next.android.domain.model.Playlist
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

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
