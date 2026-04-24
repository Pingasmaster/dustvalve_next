package com.dustvalve.next.android.data.remote.youtubemusic

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
 * Fetches and caches the YouTube Music `VISITOR_DATA` token plus the live
 * `INNERTUBE_CLIENT_VERSION` by GET-ing https://music.youtube.com/ and
 * scraping its `ytcfg.set({...})` block. Without this token YT Music answers
 * unauthenticated /browse FEmusic_home requests with a placeholder
 * messageRenderer instead of real shelves (verified across ytmusicapi,
 * Metrolist, InnerTune, yt-dlp). The fetcher is in-memory only — a fresh
 * token is cheap (one HTML GET) and rotating it across app launches is fine.
 */
@Singleton
open class YouTubeMusicVisitorDataFetcher @Inject constructor(
    sharedOkHttpClient: OkHttpClient,
) {

    /**
     * The landing fetch MUST NOT carry the shared CookieJar's cookies. A
     * stale login cookie from a prior session (or any SID / HSID / SAPISID)
     * rotates the landing page onto a signed-in variant whose `ytcfg.set(...)`
     * block is served behind additional setup scripts that vary by account,
     * and in some EU-locale combinations the block is omitted entirely —
     * parsing then fails with "missing ytcfg.set block". Reuse the shared
     * connection pool / dispatcher / interceptors via newBuilder() but drop
     * the cookie jar so only the manual `SOCS=CAI` header below is sent.
     * (Same pattern as YouTubeMusicInnertubeClient.)
     */
    private val okHttpClient: OkHttpClient = sharedOkHttpClient.newBuilder()
        .cookieJar(okhttp3.CookieJar.NO_COOKIES)
        .build()

    /** Overridable in tests so MockWebServer can answer the landing GET. */
    protected open val landingUrl: String = "https://music.youtube.com/"

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
                throw IllegalStateException("YT Music landing fetch failed: HTTP ${response.code}")
            }
            response.body.string()
        }

        val configMatch = YTCFG_REGEX.find(html)
            ?: throw IllegalStateException("YT Music landing missing ytcfg.set block")
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
        const val DEFAULT_CLIENT_VERSION = "1.20260417.03.00"

        // Matches `ytcfg.set({...});` and captures the JSON object body. Lazy
        // (.+?) and DOT_MATCHES_ALL handle multi-line / large objects without
        // catastrophic backtracking. Both braces are escaped: Android's
        // ICU-backed regex engine rejects a literal `}` outside `{n,m}` —
        // the JVM-tolerated unescaped form crashes with
        // PatternSyntaxException at class init on-device. (Visible to tests.)
        internal const val YTCFG_PATTERN = """ytcfg\.set\s*\(\s*(\{.+?\})\s*\)\s*;"""

        private val YTCFG_REGEX = Regex(
            YTCFG_PATTERN,
            RegexOption.DOT_MATCHES_ALL,
        )
    }
}
