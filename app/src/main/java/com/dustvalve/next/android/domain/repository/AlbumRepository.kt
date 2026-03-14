package com.dustvalve.next.android.domain.repository

import com.dustvalve.next.android.domain.model.Album
import com.dustvalve.next.android.domain.model.PurchaseInfo
import kotlinx.coroutines.flow.Flow

interface AlbumRepository {
    suspend fun getAlbumDetail(url: String): Album
    fun getAlbumDetailFlow(url: String): Flow<Album>
    suspend fun getAlbumById(id: String): Album?
    fun getFavoriteAlbums(): Flow<List<Album>>
    suspend fun toggleFavorite(albumId: String)
    suspend fun setAutoDownload(albumId: String, autoDownload: Boolean)
    suspend fun updatePurchaseInfo(albumId: String, purchaseInfo: PurchaseInfo)
}
