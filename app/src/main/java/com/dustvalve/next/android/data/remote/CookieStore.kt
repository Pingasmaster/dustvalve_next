package com.dustvalve.next.android.data.remote

import com.dustvalve.next.android.data.local.datastore.SettingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CookieStore @Inject constructor(
    private val settingsDataStore: SettingsDataStore
) : CookieJar {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    // Scope intentionally lives for the entire app lifetime (@Singleton); no cancellation needed
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO +
            kotlinx.coroutines.CoroutineExceptionHandler { _, throwable ->
                android.util.Log.e("CookieStore", "Cookie persistence error", throwable)
            }
    )

    private val lock = Any()
    private var cachedCookies: List<SerializableCookie> = emptyList()
    private val initLatch = CountDownLatch(1)
    private val persistMutex = Mutex()

    init {
        // Load cookies asynchronously instead of blocking the main thread
        scope.launch {
            val cookiesJson = settingsDataStore.authCookies.firstOrNull()
            val loaded = if (cookiesJson != null) {
                try {
                    json.decodeFromString<List<SerializableCookie>>(cookiesJson)
                } catch (_: Exception) {
                    emptyList()
                }
            } else {
                emptyList()
            }
            synchronized(lock) {
                cachedCookies = loaded
            }
            initLatch.countDown()
        }
    }

    @Serializable
    data class SerializableCookie(
        val name: String,
        val value: String,
        val domain: String,
        val path: String = "/",
        val expiresAt: Long = 0L,
        val secure: Boolean = false,
        val httpOnly: Boolean = false,
    )

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        if (!initLatch.await(500, TimeUnit.MILLISECONDS)) {
            android.util.Log.w("CookieStore", "Cookie initialization timed out, proceeding without cookies")
            return emptyList()
        }
        val cookies = synchronized(lock) { cachedCookies }
        val urlPath = url.encodedPath
        return cookies
            .filter { cookie -> matchesDomain(url.host, cookie.domain) }
            .filter { cookie -> urlPath.startsWith(cookie.path) }
            .filter { cookie -> cookie.expiresAt == 0L || cookie.expiresAt > System.currentTimeMillis() }
            .mapNotNull { cookie ->
                try {
                    Cookie.Builder()
                        .name(cookie.name)
                        .value(cookie.value)
                        .domain(cookie.domain)
                        .path(cookie.path)
                        .apply {
                            if (cookie.expiresAt > 0) expiresAt(cookie.expiresAt)
                            if (cookie.secure) secure()
                            if (cookie.httpOnly) httpOnly()
                        }
                        .build()
                } catch (_: Exception) {
                    null // Skip invalid cookies
                }
            }
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (!isDustvalveHost(url.host)) return
        if (!initLatch.await(3, TimeUnit.SECONDS)) {
            android.util.Log.w("CookieStore", "Cookie initialization timed out in saveFromResponse, skipping save")
            return
        }

        val newCookies = cookies.map { cookie ->
            SerializableCookie(
                name = cookie.name,
                value = cookie.value,
                domain = cookie.domain,
                path = cookie.path,
                expiresAt = cookie.expiresAt,
                secure = cookie.secure,
                httpOnly = cookie.httpOnly,
            )
        }

        // Update in-memory cache atomically under lock
        synchronized(lock) {
            val existingCookies = cachedCookies.toMutableList()
            for (newCookie in newCookies) {
                existingCookies.removeAll { it.name == newCookie.name && it.domain == newCookie.domain && it.path == newCookie.path }
                existingCookies.add(newCookie)
            }
            cachedCookies = existingCookies.toList()
        }

        // Persist async with mutex to prevent out-of-order writes
        scope.launch {
            persistMutex.withLock {
                val currentSnapshot = synchronized(lock) { cachedCookies }
                val updatedJson = json.encodeToString(currentSnapshot)
                settingsDataStore.setAuthCookies(updatedJson)
            }
        }
    }

    fun loadCookiesForDomain(domain: String, path: String = "/"): List<Cookie> {
        if (!initLatch.await(3, TimeUnit.SECONDS)) {
            android.util.Log.w("CookieStore", "Cookie initialization timed out in loadCookiesForDomain")
            return emptyList()
        }
        val cookies = synchronized(lock) { cachedCookies }
        return cookies
            .filter { cookie -> matchesDomain(domain, cookie.domain) }
            .filter { cookie -> path.startsWith(cookie.path) }
            .filter { cookie -> cookie.expiresAt == 0L || cookie.expiresAt > System.currentTimeMillis() }
            .mapNotNull { cookie ->
                try {
                    Cookie.Builder()
                        .name(cookie.name)
                        .value(cookie.value)
                        .domain(cookie.domain)
                        .path(cookie.path)
                        .apply {
                            if (cookie.expiresAt > 0) expiresAt(cookie.expiresAt)
                            if (cookie.secure) secure()
                            if (cookie.httpOnly) httpOnly()
                        }
                        .build()
                } catch (_: Exception) {
                    null // Skip invalid cookies
                }
            }
    }

    /**
     * Imports cookies from a name/value map (e.g. from WebView login).
     * Converts to SerializableCookie format and persists through the existing mechanism.
     */
    suspend fun importCookies(cookies: Map<String, String>, domain: String = "bandcamp.com") {
        val newCookies = cookies.map { (name, value) ->
            SerializableCookie(
                name = name,
                value = value,
                domain = domain,
                path = "/",
                secure = true,
            )
        }

        synchronized(lock) {
            val existing = cachedCookies.toMutableList()
            for (newCookie in newCookies) {
                existing.removeAll { it.name == newCookie.name && it.domain == newCookie.domain && it.path == newCookie.path }
                existing.add(newCookie)
            }
            cachedCookies = existing.toList()
        }

        // Persist synchronously so the caller can rely on cookies being saved
        persistMutex.withLock {
            val currentSnapshot = synchronized(lock) { cachedCookies }
            val updatedJson = json.encodeToString(currentSnapshot)
            settingsDataStore.setAuthCookies(updatedJson)
        }
    }

    suspend fun clearCookies() {
        persistMutex.withLock {
            synchronized(lock) { cachedCookies = emptyList() }
            settingsDataStore.setAuthCookies(null)
        }
    }

    suspend fun clearCookiesForDomain(domain: String) {
        persistMutex.withLock {
            synchronized(lock) {
                cachedCookies = cachedCookies.filter { cookie ->
                    !matchesDomain(domain, cookie.domain)
                }
            }
            val currentSnapshot = synchronized(lock) { cachedCookies }
            if (currentSnapshot.isEmpty()) {
                settingsDataStore.setAuthCookies(null)
            } else {
                val updatedJson = json.encodeToString(currentSnapshot)
                settingsDataStore.setAuthCookies(updatedJson)
            }
        }
    }

    /**
     * Returns true only for bandcamp.com or *.bandcamp.com hosts.
     * Prevents lookalike domains like "evilbandcamp.com" from matching.
     */
    private fun isDustvalveHost(host: String): Boolean {
        return host == "bandcamp.com" || host.endsWith(".bandcamp.com") ||
            host == "youtube.com" || host.endsWith(".youtube.com") ||
            host == "google.com" || host.endsWith(".google.com")
    }

    /**
     * Safe domain matching that prevents "evilbandcamp.com" from matching "bandcamp.com".
     */
    private fun matchesDomain(host: String, cookieDomain: String): Boolean {
        val normalizedDomain = cookieDomain.removePrefix(".")
        return host == normalizedDomain || host.endsWith(".$normalizedDomain")
    }
}
