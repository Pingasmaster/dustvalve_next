package com.dustvalve.next.android.domain.repository

import com.dustvalve.next.android.domain.model.AccountState
import com.dustvalve.next.android.domain.model.SpotifyAccountState
import com.dustvalve.next.android.domain.model.YouTubeMusicAccountState
import kotlinx.coroutines.flow.Flow

interface AccountRepository {
    fun getAccountState(): Flow<AccountState>
    suspend fun saveCookies(cookies: Map<String, String>)
    suspend fun clearAccount()
    suspend fun getCookies(): Map<String, String>

    // YouTube Music
    fun getYouTubeMusicAccountState(): Flow<YouTubeMusicAccountState>
    suspend fun saveYouTubeMusicCookies(cookies: Map<String, String>)
    suspend fun clearYouTubeMusicAccount()

    // Spotify
    fun getSpotifyAccountState(): Flow<SpotifyAccountState>
    suspend fun setSpotifyConnected(connected: Boolean)
    suspend fun clearSpotifyAccount()
}
