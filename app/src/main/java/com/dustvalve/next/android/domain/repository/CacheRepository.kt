package com.dustvalve.next.android.domain.repository

import com.dustvalve.next.android.domain.model.CacheInfo
import kotlinx.coroutines.flow.Flow

interface CacheRepository {
    fun getCacheInfo(): Flow<CacheInfo>
    suspend fun clearCache()
    suspend fun setStorageLimit(bytes: Long)
    suspend fun getStorageLimit(): Long
    suspend fun evictIfNeeded()
}
