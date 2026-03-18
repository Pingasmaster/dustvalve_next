package com.dustvalve.next.android.domain.repository

import com.dustvalve.next.android.data.local.scanner.ScanResult
import kotlinx.coroutines.flow.Flow

interface LocalMusicRepository {
    suspend fun scan(): ScanResult
    suspend fun addFolder(uri: String)
    suspend fun removeFolder(uri: String)
    suspend fun clearAll()
    suspend fun scheduleSyncWork()
    suspend fun cancelSyncWork()
    fun getLocalTrackCount(): Flow<Int>
}
