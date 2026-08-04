package com.dustvalve.next.android.domain.repository

import com.dustvalve.next.android.domain.model.FavoriteType
import kotlinx.coroutines.flow.Flow

/**
 * Generic favorites rows (any [FavoriteType]). Track-favorite TOGGLING stays
 * on [LibraryRepository.toggleTrackFavorite] (transactional read-then-write);
 * this repository covers direct reads/writes of favorites rows.
 */
interface FavoriteRepository {

    suspend fun isFavorite(id: String): Boolean

    /**
     * Reactive id set of favorites of one [type]. Do NOT add
     * distinctUntilChanged - the emission cadence feeding the ViewModels'
     * stateIn/collect chains is load-bearing.
     */
    fun favoriteIds(type: FavoriteType): Flow<Set<String>>

    suspend fun add(id: String, type: FavoriteType)

    suspend fun remove(id: String)

    suspend fun setPinned(id: String, isPinned: Boolean)

    suspend fun setShapeKey(id: String, shapeKey: String?)
}
