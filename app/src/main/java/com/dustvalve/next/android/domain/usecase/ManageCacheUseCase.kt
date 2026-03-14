package com.dustvalve.next.android.domain.usecase

import com.dustvalve.next.android.domain.model.CacheInfo
import com.dustvalve.next.android.domain.repository.CacheRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ManageCacheUseCase @Inject constructor(
    private val cacheRepository: CacheRepository,
) {
    fun getCacheInfo(): Flow<CacheInfo> {
        return cacheRepository.getCacheInfo()
    }

    suspend fun clearCache() {
        cacheRepository.clearCache()
    }

    suspend fun setStorageLimit(bytes: Long) {
        cacheRepository.setStorageLimit(bytes)
        cacheRepository.evictIfNeeded()
    }
}
