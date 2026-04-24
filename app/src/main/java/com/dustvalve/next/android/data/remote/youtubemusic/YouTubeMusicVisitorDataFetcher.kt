package com.dustvalve.next.android.data.remote.youtubemusic

import com.dustvalve.next.android.data.remote.youtube.innertube.YouTubeYtcfgExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fetches and caches the YouTube Music `VISITOR_DATA` token plus the live
 * `INNERTUBE_CLIENT_VERSION` by scraping the landing page's `ytcfg.set({...})`
 * block. Without this token YT Music answers unauthenticated /browse
 * FEmusic_home requests with a placeholder messageRenderer instead of real
 * shelves (verified across ytmusicapi, Metrolist, InnerTune, yt-dlp). The
 * fetcher is in-memory only: a fresh token is cheap (one HTML GET) and
 * rotating it across app launches is fine.
 *
 * ## Hardening
 *
 * Historically this fetcher failed with "missing ytcfg.set block" whenever
 * Google returned a degraded page (no ytcfg) under hostile bot-detection
 * conditions. Three layers of defense now apply:
 *
 *  1. **Navigation-style headers** on the landing GET: `Sec-Fetch-Mode:
 *     navigate`, `Upgrade-Insecure-Requests: 1`, etc. Google's bot heuristic
 *     then serves the real page instead of the "Your browser is deprecated"
 *     stub.
 *  2. **Dual consent cookies** (`SOCS=CAI` and `CONSENT=YES+1`). The EU
 *     consent gate accepts either form; sending both covers every locale
 *     variant we've seen.
 *  3. **Fallback URL** — if music.youtube.com returns no usable ytcfg, we
 *     fetch https://www.youtube.com/ and take its visitorData. The token is
 *     shared across the `.youtube.com` realm, so YT Music Innertube calls
 *     accept it. We pair that visitorData with our hardcoded
 *     [DEFAULT_CLIENT_VERSION] for WEB_REMIX since www.youtube.com's ytcfg
 *     reports the WEB (non-Music) clientVersion.
 */
@Singleton
open class YouTubeMusicVisitorDataFetcher @Inject constructor(
    sharedOkHttpClient: OkHttpClient,
) {

    /**
     * Landing fetches MUST NOT carry the shared CookieJar's cookies. A stale
     * login cookie from a prior session (or any SID / HSID / SAPISID) rotates
     * the landing page onto a signed-in variant whose `ytcfg.set(...)` block
     * is served behind additional setup scripts that vary by account, and in
     * some EU-locale combinations the block is omitted entirely — parsing
     * then fails with "missing ytcfg.set block". Reuse the shared connection
     * pool / dispatcher / interceptors via newBuilder() but drop the cookie
     * jar so only the manual consent cookies below are sent.
     */
    private val okHttpClient: OkHttpClient = sharedOkHttpClient.newBuilder()
        .cookieJar(okhttp3.CookieJar.NO_COOKIES)
        .build()

    /** Overridable in tests so MockWebServer can answer the primary landing GET. */
    protected open val landingUrl: String = "https://music.youtube.com/"

    /** Overridable in tests. When the primary URL has no ytcfg, we fetch this. */
    protected open val fallbackLandingUrl: String = "https://www.youtube.com/"

    @Volatile
    private var cached: VisitorConfig? = null
    private val mutex = Mutex()

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
        val primary = fetchLanding(landingUrl)
        primary.extract()?.let {
            // Use the scraped clientVersion only if it is plausibly a
            // WEB_REMIX version (`1.x`). Fallback to our hardcoded default
            // otherwise (e.g. if the primary URL degraded and we somehow
            // got the WEB `2.x` variant).
            val version = it.clientVersion?.takeIf { v -> v.startsWith("1.") }
                ?: DEFAULT_CLIENT_VERSION
            return@withContext VisitorConfig(it.visitorData, version)
        }

        // Primary yielded no ytcfg (browser-deprecated stub, consent redirect,
        // or locale-specific variant). Fall back to www.youtube.com whose
        // ytcfg is more reliable, and pair its visitorData with the WEB_REMIX
        // default clientVersion.
        val fallback = fetchLanding(fallbackLandingUrl)
        fallback.extract()?.let {
            return@withContext VisitorConfig(
                visitorData = it.visitorData,
                clientVersion = DEFAULT_CLIENT_VERSION,
            )
        }

        throw IllegalStateException(
            "YT Music landing missing ytcfg.set block " +
                "(primary=HTTP ${primary.status}, ${primary.body.length} B; " +
                "fallback=HTTP ${fallback.status}, ${fallback.body.length} B; " +
                "head='${primary.body.take(120).replace('\n', ' ')}')",
        )
    }

    private data class LandingResponse(val status: Int, val body: String) {
        fun extract(): YouTubeYtcfgExtractor.YtcfgData? =
            if (status in 200..299) YouTubeYtcfgExtractor.extract(body) else null
    }

    private fun fetchLanding(url: String): LandingResponse {
        val request = Request.Builder()
            .url(url)
            // Explicit UA in addition to the shared interceptor — the
            // interceptor IS preserved by newBuilder(), but setting UA here
            // too eliminates any edge case where a custom sharedOkHttpClient
            // (e.g. in tests) is missing the interceptor.
            .header("User-Agent", DESKTOP_CHROME_UA)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Upgrade-Insecure-Requests", "1")
            .header("Sec-Fetch-Mode", "navigate")
            .header("Sec-Fetch-Site", "none")
            .header("Sec-Fetch-Dest", "document")
            .header("Sec-Fetch-User", "?1")
            // Dual consent cookies: SOCS=CAI is the historical YT ack;
            // CONSENT=YES+1 covers the newer ytmusicapi/NewPipe form.
            // Sending both concurrently is accepted by the server (verified
            // against the live URL).
            .header("Cookie", "SOCS=CAI; CONSENT=YES+1")
            .build()

        return okHttpClient.newCall(request).execute().use { response ->
            val body = try {
                response.body.string()
            } catch (_: Throwable) {
                ""
            }
            LandingResponse(status = response.code, body = body)
        }
    }

    data class VisitorConfig(val visitorData: String, val clientVersion: String)

    companion object {
        const val DEFAULT_CLIENT_VERSION = "1.20260417.03.00"

        // Desktop Chrome UA. YT Music's bot heuristic is friendlier to
        // desktop signatures than to mobile ones.
        private const val DESKTOP_CHROME_UA =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }
}
