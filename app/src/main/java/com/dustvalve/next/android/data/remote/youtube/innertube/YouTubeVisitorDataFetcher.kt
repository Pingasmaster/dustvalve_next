package com.dustvalve.next.android.data.remote.youtube.innertube

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fetches and caches YouTube's `VISITOR_DATA` token plus the live
 * `INNERTUBE_CLIENT_VERSION` by GET-ing https://www.youtube.com/ and
 * scraping its `ytcfg.set({...})` block. Anonymous /search and /browse
 * calls degrade (returning placeholder shelves or 400s) without a real
 * visitor cookie, so we always inject one. Cached in-memory only; rotating
 * across app launches is fine.
 *
 * This is the YT-flavoured twin of YouTubeMusicVisitorDataFetcher.
 */
@Singleton
open class YouTubeVisitorDataFetcher @Inject constructor(
    private val okHttpClient: OkHttpClient,
) {

    /** Overridable in tests so MockWebServer can answer the landing GET. */
    protected open val landingUrl: String = "https://www.youtube.com/"

    @Volatile
    private var cached: VisitorConfig? = null
    private val mutex = Mutex()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    open suspend fun get(): VisitorConfig {
        cached?.let { return it }
        return mutex.withLock {
            cached?.let { return@withLock it }
            val fresh = fetch()
            cached = fresh
            fresh
        }
    }

    /** Invalidates the cached token so the next get() will re-scrape. */
    fun invalidate() { cached = null }

    private suspend fun fetch(): VisitorConfig = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(landingUrl)
            .header("Accept-Language", "en-US,en;q=0.9")
            // SOCS=CAI dismisses the EU consent banner so the landing page
            // returns the real ytcfg.set block instead of redirecting to
            // consent.youtube.com.
            .header("Cookie", "SOCS=CAI")
            .build()

        val html = okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("YouTube landing fetch failed: HTTP ${response.code}")
            }
            response.body.string()
        }

        val configMatch = YTCFG_REGEX.find(html)
            ?: throw IllegalStateException("YouTube landing missing ytcfg.set block")
        val config = json.parseToJsonElement(configMatch.groupValues[1]).jsonObject

        val visitorData = (config["INNERTUBE_CONTEXT"]?.jsonObject
            ?.get("client")?.jsonObject
            ?.get("visitorData")
            ?: config["VISITOR_DATA"])
            ?.jsonPrimitive?.content
            ?: throw IllegalStateException("ytcfg missing VISITOR_DATA")

        val clientVersion = config["INNERTUBE_CLIENT_VERSION"]?.jsonPrimitive?.content
            ?: config["INNERTUBE_CONTEXT_CLIENT_VERSION"]?.jsonPrimitive?.content
            ?: DEFAULT_CLIENT_VERSION

        VisitorConfig(visitorData = visitorData, clientVersion = clientVersion)
    }

    data class VisitorConfig(val visitorData: String, val clientVersion: String)

    companion object {
        const val DEFAULT_CLIENT_VERSION = "2.20260421.00.00"

        // Matches `ytcfg.set({...});` and captures the JSON object body.
        private val YTCFG_REGEX = Regex(
            """ytcfg\.set\s*\(\s*(\{.+?})\s*\)\s*;""",
            RegexOption.DOT_MATCHES_ALL,
        )
    }
}
