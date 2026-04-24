package com.dustvalve.next.android.data.remote.youtube.innertube

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fetches and caches YouTube's `VISITOR_DATA` token plus the live
 * `INNERTUBE_CLIENT_VERSION` by scraping the landing page's `ytcfg.set({...})`
 * block. Anonymous /search and /browse calls degrade (returning placeholder
 * shelves or 400s) without a real visitor cookie, so we always inject one.
 * Cached in-memory only; rotating across app launches is fine.
 *
 * Twin of [com.dustvalve.next.android.data.remote.youtubemusic.YouTubeMusicVisitorDataFetcher]
 * — same hardening (navigation headers, dual consent cookies, fallback URL,
 * shared brace-balanced ytcfg extractor). The fallback URL is the same as
 * the primary because plain youtube.com is the most reliable source already;
 * having the second attempt covers transient failures.
 */
@Singleton
open class YouTubeVisitorDataFetcher @Inject constructor(
    sharedOkHttpClient: OkHttpClient,
) {

    private val okHttpClient: OkHttpClient = sharedOkHttpClient.newBuilder()
        .cookieJar(okhttp3.CookieJar.NO_COOKIES)
        .build()

    /** Overridable in tests so MockWebServer can answer the landing GET. */
    protected open val landingUrl: String = "https://www.youtube.com/"

    /** Overridable in tests. Hit this if the primary returns no ytcfg. */
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
            return@withContext VisitorConfig(
                visitorData = it.visitorData,
                clientVersion = it.clientVersion ?: DEFAULT_CLIENT_VERSION,
            )
        }

        val fallback = if (fallbackLandingUrl != landingUrl) {
            fetchLanding(fallbackLandingUrl).also { fb ->
                fb.extract()?.let {
                    return@withContext VisitorConfig(
                        visitorData = it.visitorData,
                        clientVersion = it.clientVersion ?: DEFAULT_CLIENT_VERSION,
                    )
                }
            }
        } else null

        throw IllegalStateException(
            "YouTube landing missing ytcfg.set block " +
                "(primary=HTTP ${primary.status}, ${primary.body.length} B; " +
                (if (fallback != null)
                    "fallback=HTTP ${fallback.status}, ${fallback.body.length} B; "
                else "") +
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
            .header("User-Agent", DESKTOP_CHROME_UA)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Upgrade-Insecure-Requests", "1")
            .header("Sec-Fetch-Mode", "navigate")
            .header("Sec-Fetch-Site", "none")
            .header("Sec-Fetch-Dest", "document")
            .header("Sec-Fetch-User", "?1")
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
        const val DEFAULT_CLIENT_VERSION = "2.20260421.00.00"

        private const val DESKTOP_CHROME_UA =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }
}
