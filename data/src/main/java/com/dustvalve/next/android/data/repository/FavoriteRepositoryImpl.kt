package com.dustvalve.next.android.data.repository

import com.dustvalve.next.android.data.local.db.dao.FavoriteDao
import com.dustvalve.next.android.data.local.db.entity.FavoriteEntity
import com.dustvalve.next.android.domain.model.FavoriteType
import com.dustvalve.next.android.domain.repository.FavoriteRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FavoriteRepositoryImpl @Inject constructor(private val favoriteDao: FavoriteDao) : FavoriteRepository {

    override suspend fun isFavorite(id: String): Boolean = favoriteDao.isFavorite(id)

    // Deliberately no distinctUntilChanged: the emission cadence feeding the
    // VMs' stateIn/collect chains is load-bearing (see FavoriteRepository KDoc).
    override fun favoriteIds(type: FavoriteType): Flow<Set<String>> =
        favoriteDao.getAllByType(type.key).map { list -> list.mapTo(HashSet()) { it.id } }

    override suspend fun add(id: String, type: FavoriteType) {
        favoriteDao.insert(FavoriteEntity(id = id, type = type.key))
    }

    override suspend fun remove(id: String) {
        favoriteDao.delete(id)
    }

    override suspend fun setPinned(id: String, isPinned: Boolean) {
        favoriteDao.setPinned(id, isPinned)
    }

    override suspend fun setShapeKey(id: String, shapeKey: String?) {
        favoriteDao.setShapeKey(id, shapeKey)
    }
}
