package com.dustvalve.next.android.data.remote.soundcloud

import com.dustvalve.next.android.data.local.datastore.SettingsDataStore
import com.dustvalve.next.android.di.qualifiers.AppDispatchers
import com.dustvalve.next.android.di.qualifiers.Dispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Scrapes SoundCloud's anonymous api-v2 [client_id] from homepage JS assets
 * (same approach as NewPipe / yt-dlp). Caches the value in [SettingsDataStore]
 * and invalidates on 401/403 from the API.
 */
@Singleton
class SoundCloudClientIdProvider @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val settingsDataStore: SettingsDataStore,
    @param:Dispatcher(AppDispatchers.IO) private val ioDispatcher: CoroutineDispatcher,
) {
    private val mutex = Mutex()
    private val memoryCache = AtomicReference<String?>(null)

    suspend fun getClientId(): String = mutex.withLock {
        memoryCache.get()?.let { return it }
        val stored = settingsDataStore.soundcloudClientId.first()
        if (!stored.isNullOrBlank()) {
            memoryCache.set(stored)
            return stored
        }
        val scraped = scrapeClientId()
        settingsDataStore.setSoundcloudClientId(scraped)
        memoryCache.set(scraped)
        scraped
    }

    suspend fun invalidate() = mutex.withLock {
        memoryCache.set(null)
        settingsDataStore.clearSoundcloudClientId()
    }

    /** Blocking homepage + script scrapes run on [ioDispatcher]. */
    private suspend fun scrapeClientId(): String = withContext(ioDispatcher) {
        val homepage = httpGet(HOMEPAGE_URL)
        val scriptUrls = SCRIPT_SRC_REGEX.findAll(homepage)
            .map { it.groupValues[1] }
            .filter { src ->
                src.contains("sndcdn.com", ignoreCase = true) &&
                    SCRIPT_JS_PATH_REGEX.containsMatchIn(src)
            }
            .toList()
            .asReversed()

        scriptUrls.firstNotNullOfOrNull { src ->
            // Prefer a Range fetch for speed; fall back to the full body when
            // the CDN rejects Range or the id sits past the window.
            extractClientId(httpGetOrNull(src, rangeBytes = SCRIPT_RANGE_BYTES))
                ?: extractClientId(httpGetOrNull(src, rangeBytes = null))
        } ?: throw IOException("Could not extract SoundCloud client_id from homepage scripts")
    }

    private fun extractClientId(body: String?): String? {
        if (body.isNullOrBlank()) return null
        for (regex in CLIENT_ID_REGEXES) {
            val match = regex.find(body) ?: continue
            val id = match.groupValues[1]
            if (id.length == CLIENT_ID_LENGTH) return id
        }
        return null
    }

    private fun httpGetOrNull(url: String, rangeBytes: Int?): String? = try {
        httpGet(url, rangeBytes = rangeBytes)
    } catch (_: IOException) {
        null
    }

    private fun httpGet(url: String, rangeBytes: Int? = null): String {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "*/*")
        if (rangeBytes != null) {
            builder.header("Range", "bytes=0-$rangeBytes")
        }
        okHttpClient.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful && response.code != HTTP_PARTIAL) {
                throw IOException("HTTP ${response.code} fetching $url")
            }
            return response.body.string()
        }
    }

    private companion object {
        const val HOMEPAGE_URL = "https://soundcloud.com/"
        const val USER_AGENT =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/120.0.0.0 Safari/537.36"
        const val CLIENT_ID_LENGTH = 32
        const val SCRIPT_RANGE_BYTES = 200_000
        const val HTTP_PARTIAL = 206

        // Allow `.js`, `.js?v=...`, and `.js#...` (CDN cache-busters).
        val SCRIPT_SRC_REGEX = Regex("""src=["'](https?://[^"']+\.js[^"']*)["']""")
        val SCRIPT_JS_PATH_REGEX = Regex("""\.js(?:[?#].*)?$""", RegexOption.IGNORE_CASE)

        // Match the shapes live_soundcloud_smoke.py tolerates.
        val CLIENT_ID_REGEXES = listOf(
            Regex("""client_id\s*[:=]\s*"([0-9a-zA-Z]{32})""""),
            Regex(""""client_id"\s*:\s*"([0-9a-zA-Z]{32})""""),
            Regex("""client_id\s*[:=]\s*'([0-9a-zA-Z]{32})'"""),
        )
    }
}
