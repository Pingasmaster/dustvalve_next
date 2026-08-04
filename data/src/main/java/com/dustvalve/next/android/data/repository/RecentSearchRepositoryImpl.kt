package com.dustvalve.next.android.data.repository

import com.dustvalve.next.android.data.local.db.dao.RecentSearchDao
import com.dustvalve.next.android.data.local.db.entity.RecentSearchEntity
import com.dustvalve.next.android.domain.repository.RecentSearchRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecentSearchRepositoryImpl @Inject constructor(private val recentSearchDao: RecentSearchDao) : RecentSearchRepository {

    companion object {
        /** Per-source history cap, relocated verbatim from the VMs' deleteOld(source, keepCount = 20) calls. */
        private const val KEEP_COUNT = 20

        /** Canonical list of sources that write recent-search rows (SettingsViewModel's clear-all loop, relocated). */
        private val ALL_SOURCES = listOf("bandcamp", "youtube", "soundcloud", "local")
    }

    override fun getRecent(source: String, limit: Int): Flow<List<String>> =
        recentSearchDao.getRecent(source, limit).map { entities -> entities.map { it.query } }

    override suspend fun add(query: String, source: String, trim: Boolean) {
        recentSearchDao.insert(RecentSearchEntity(query = query, source = source))
        // trim = false preserves SoundCloudViewModel's historical uncapped
        // history (that screen never called deleteOld).
        if (trim) {
            recentSearchDao.deleteOld(source = source, keepCount = KEEP_COUNT)
        }
    }

    override suspend fun remove(query: String, source: String) {
        recentSearchDao.delete(query, source)
    }

    override suspend fun clear(source: String) {
        recentSearchDao.clearAll(source)
    }

    override suspend fun clearAllSources() {
        for (source in ALL_SOURCES) {
            recentSearchDao.clearAll(source)
        }
    }
}
