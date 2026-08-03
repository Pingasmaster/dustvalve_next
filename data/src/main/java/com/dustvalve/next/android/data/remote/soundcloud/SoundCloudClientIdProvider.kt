package com.dustvalve.next.android.data.remote.soundcloud

import com.dustvalve.next.android.data.local.datastore.SettingsDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    private fun scrapeClientId(): String {
        val homepage = httpGet(HOMEPAGE_URL)
        val scriptUrls = SCRIPT_SRC_REGEX.findAll(homepage)
            .map { it.groupValues[1] }
            .filter { it.contains("sndcdn.com", ignoreCase = true) && it.endsWith(".js") }
            .toList()
            .asReversed()

        return scriptUrls.firstNotNullOfOrNull { src ->
            val body = try {
                httpGet(src, rangeBytes = SCRIPT_RANGE_BYTES)
            } catch (_: IOException) {
                return@firstNotNullOfOrNull null
            }
            val match = CLIENT_ID_REGEX.find(body) ?: return@firstNotNullOfOrNull null
            match.groupValues[1].takeIf { it.length == CLIENT_ID_LENGTH }
        } ?: throw IOException("Could not extract SoundCloud client_id from homepage scripts")
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

        val SCRIPT_SRC_REGEX = Regex("""src=["'](https?://[^"']+\.js[^"']*)["']""")
        val CLIENT_ID_REGEX = Regex("""client_id\s*:\s*"([0-9a-zA-Z]{32})""")
    }
}
