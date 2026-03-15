package com.dustvalve.next.android.domain.repository

import com.dustvalve.next.android.data.local.scanner.ScanResult
import kotlinx.coroutines.flow.Flow

interface LocalMusicRepository {
    suspend fun scan(): ScanResult
    suspend fun clearAll()
    suspend fun scheduleSyncWork()
    suspend fun cancelSyncWork()
    fun getLocalTrackCount(): Flow<Int>
}
