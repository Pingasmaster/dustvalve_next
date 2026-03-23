package com.dustvalve.next.android.data.repository

import com.dustvalve.next.android.data.local.datastore.SettingsDataStore
import com.dustvalve.next.android.data.remote.CookieStore
import com.dustvalve.next.android.domain.model.AccountState
import com.dustvalve.next.android.domain.model.SpotifyAccountState
import com.dustvalve.next.android.domain.model.YouTubeMusicAccountState
import com.dustvalve.next.android.domain.repository.AccountRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccountRepositoryImpl @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val cookieStore: CookieStore,
) : AccountRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    override fun getAccountState(): Flow<AccountState> {
        return combine(
            settingsDataStore.accountUsername,
            settingsDataStore.accountAvatar,
            settingsDataStore.accountFanId,
        ) { username, avatar, fanId ->
            AccountState(
                isLoggedIn = username != null,
                username = username,
                avatarUrl = avatar,
                fanId = fanId,
            )
        }
    }

    override suspend fun saveCookies(cookies: Map<String, String>) {
        // Delegate to CookieStore so cookies are persisted in the same
        // List<SerializableCookie> format that CookieStore reads back
        cookieStore.importCookies(cookies)

        // Parse identity cookie once and extract all fields
        val identityObj = cookies["identity"]?.let { identity ->
            try {
                val decoded = java.net.URLDecoder.decode(identity, "UTF-8")
                json.parseToJsonElement(decoded).jsonObject
            } catch (_: Exception) {
                null
            }
        }

        // Extract all fields, defaulting to null if absent so stale data is cleared
        val username = identityObj?.get("username")?.jsonPrimitive?.content
        val avatarUrl = identityObj?.get("photo")?.jsonPrimitive?.content
        val fanId = try {
            identityObj?.get("fan_id")?.jsonPrimitive?.long
        } catch (_: Exception) {
            null // fan_id may not be a long in all identity formats
        }

        // Save all fields atomically in a single DataStore edit
        settingsDataStore.setAccountInfo(username, avatarUrl, fanId)
    }

    override suspend fun clearAccount() {
        cookieStore.clearCookiesForDomain("bandcamp.com")
        settingsDataStore.clearAccount()
    }

    override suspend fun getCookies(): Map<String, String> {
        // Read from CookieStore's in-memory cache to avoid format mismatch
        return cookieStore.loadCookiesForDomain("bandcamp.com")
            .associate { it.name to it.value }
    }

    // YouTube Music

    override fun getYouTubeMusicAccountState(): Flow<YouTubeMusicAccountState> {
        return settingsDataStore.ytmConnected.map { connected ->
            YouTubeMusicAccountState(isLoggedIn = connected)
        }
    }

    override suspend fun saveYouTubeMusicCookies(cookies: Map<String, String>) {
        cookieStore.importCookies(cookies, domain = "youtube.com")
        settingsDataStore.setYtmConnected(true)
    }

    override suspend fun clearYouTubeMusicAccount() {
        cookieStore.clearCookiesForDomain("youtube.com")
        cookieStore.clearCookiesForDomain("google.com")
        settingsDataStore.clearYtmAccount()
    }

    // Spotify
    override fun getSpotifyAccountState(): Flow<SpotifyAccountState> {
        return settingsDataStore.spotifyConnected.map { connected ->
            SpotifyAccountState(isConnected = connected)
        }
    }

    override suspend fun setSpotifyConnected(connected: Boolean) {
        settingsDataStore.setSpotifyConnected(connected)
    }

    override suspend fun clearSpotifyAccount() {
        settingsDataStore.clearSpotifyAccount()
    }
}
