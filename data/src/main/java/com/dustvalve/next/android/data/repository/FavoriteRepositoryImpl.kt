package com.dustvalve.next.android.data.repository

import com.dustvalve.next.android.data.local.DatabaseGateway
import com.dustvalve.next.android.data.local.db.dao.FavoriteDao
import com.dustvalve.next.android.data.local.db.entity.FavoriteEntity
import com.dustvalve.next.android.domain.model.FavoriteType
import com.dustvalve.next.android.domain.repository.FavoriteRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FavoriteRepositoryImpl(private val favoriteDao: FavoriteDao) : FavoriteRepository {

    @Inject constructor(gateway: DatabaseGateway) : this(gateway.favoriteDao)

    override suspend fun isFavorite(id: String, type: FavoriteType): Boolean =
        favoriteDao.isFavorite(id, type.key)

    // Deliberately no distinctUntilChanged: the emission cadence feeding the
    // VMs' stateIn/collect chains is load-bearing (see FavoriteRepository KDoc).
    override fun favoriteIds(type: FavoriteType): Flow<Set<String>> =
        favoriteDao.getAllByType(type.key).map { list -> list.mapTo(HashSet()) { it.id } }

    override suspend fun add(id: String, type: FavoriteType) {
        favoriteDao.insert(FavoriteEntity(id = id, type = type.key))
    }

    override suspend fun remove(id: String, type: FavoriteType) {
        favoriteDao.delete(id, type.key)
    }

    override suspend fun setPinned(id: String, type: FavoriteType, isPinned: Boolean) {
        favoriteDao.setPinned(id, type.key, isPinned)
    }

    override suspend fun setShapeKey(id: String, type: FavoriteType, shapeKey: String?) {
        favoriteDao.setShapeKey(id, type.key, shapeKey)
    }
}
