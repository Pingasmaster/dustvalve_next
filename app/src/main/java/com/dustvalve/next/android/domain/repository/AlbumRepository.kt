package com.dustvalve.next.android.domain.repository

import com.dustvalve.next.android.domain.model.Album
import com.dustvalve.next.android.domain.model.AlbumPrice
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

    /**
     * Fetches the per-track price for a single Bandcamp track URL. Returns
     * null when the track isn't priced individually or any fetch/parse error
     * occurs. See [DustvalveAlbumScraper.fetchTrackPrice].
     */
    suspend fun fetchBandcampTrackPrice(trackUrl: String, fallbackCurrency: String): AlbumPrice?
}
