package com.dustvalve.next.android.data.remote

import com.dustvalve.next.android.di.qualifiers.AppDispatchers
import com.dustvalve.next.android.di.qualifiers.Dispatcher
import com.dustvalve.next.android.domain.model.MusicProvider
import com.dustvalve.next.android.ui.navigation.NavDestination
import com.dustvalve.next.android.util.DeepLinkAction
import com.dustvalve.next.android.util.DetectedLink
import com.dustvalve.next.android.util.LinkResourceType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Detects whether an arbitrary (custom-domain) URL is actually a Bandcamp page and, if so,
 * classifies it. Bandcamp Pro lets artists serve `/album/<slug>` and `/track/<slug>` on their
 * own domain (e.g. music.artistname.com), so [DeepLinkRouter] can't recognise them by host.
 * This runs only on Enter (one network GET), never while typing.
 */
@Singleton
class BandcampDomainSniffer @Inject constructor(
    private val client: OkHttpClient,
    @param:Dispatcher(AppDispatchers.IO) private val ioDispatcher: CoroutineDispatcher,
) {
    /**
     * Fetch [url] and, if it's a Bandcamp page, return the matching [DetectedLink].
     *
     * Total on bad input: callers pass raw user text that merely *looks like* a URL
     * (e.g. "will.i.am"), so malformed/scheme-less strings and every network or parse
     * failure return null instead of throwing.
     */
    suspend fun sniff(url: String): DetectedLink? = withContext(ioDispatcher) {
        // The looks-like-URL gate upstream accepts scheme-less input; normalize the
        // same way it does before OkHttp sees the string.
        val trimmed = url.trim()
        val normalized = if (
            trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            trimmed
        } else {
            "https://$trimmed"
        }
        val httpUrl = normalized.toHttpUrlOrNull() ?: return@withContext null
        val request = Request.Builder().url(httpUrl).header("User-Agent", USER_AGENT).build()
        val call = client.newCall(request)
        coroutineContext[Job]?.invokeOnCompletion { cause -> if (cause != null) call.cancel() }
        val html = try {
            call.execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                // The markers we need all live in <head>; only read a bounded prefix so a
                // hostile/huge page can't blow up memory, and skip obvious non-pages.
                val contentType = response.header("Content-Type")
                if (contentType != null &&
                    !contentType.contains("html", ignoreCase = true) &&
                    !contentType.contains("text", ignoreCase = true)
                ) {
                    return@withContext null
                }
                if (response.body.contentLength() > MAX_BYTES) return@withContext null
                val source = response.body.source()
                source.request(MAX_BYTES)
                source.buffer.readString(minOf(source.buffer.size, MAX_BYTES), Charsets.UTF_8)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return@withContext null
        }
        parse(html, normalized)
    }

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Mobile"
        private const val MAX_BYTES = 256L * 1024

        private val META_TAG_RE = Regex("""<meta\b[^>]*>""", RegexOption.IGNORE_CASE)
        private val ATTR_RE = Regex(
            """([a-zA-Z:_-]+)\s*=\s*"([^"]*)"|([a-zA-Z:_-]+)\s*=\s*'([^']*)'""",
        )

        /**
         * Pure marker-parsing: given page [html] and the [requestUrl] it came from, decide if this
         * is a Bandcamp page and what it points at. Detection order (most->least reliable):
         *   1. <meta name="generator" content="Bandcamp">
         *   2. a `data-tralbum` / `data-band` attribute (Bandcamp's embedded album/band payload)
         *   3. an og:url whose host is *.bandcamp.com
         * The canonical og:url (always the *.bandcamp.com form, even on custom domains) is preferred
         * for the action URL so the existing Bandcamp scrapers resolve it.
         */
        fun parse(html: String, requestUrl: String): DetectedLink? {
            val metas = META_TAG_RE.findAll(html).map { attrs(it.value) }.toList()
            val ogUrl = metas.firstOrNull { it.metaKey() == "og:url" }?.get("content")

            val isBandcamp =
                metas.any { it.metaKey() == "generator" && it["content"]?.contains("bandcamp", true) == true } ||
                    html.contains("data-tralbum", ignoreCase = true) ||
                    html.contains("data-band", ignoreCase = true) ||
                    (ogUrl != null && hostOf(ogUrl)?.let(::isBandcampHost) == true)
            if (!isBandcamp) return null

            // Prefer the canonical bandcamp.com URL; fall back to the page we fetched.
            val canonical = ogUrl?.takeIf { hostOf(it)?.let(::isBandcampHost) == true } ?: requestUrl
            val classifyPath = (pathOf(ogUrl) ?: "").ifEmpty { pathOf(requestUrl) ?: "" }
            val segments = classifyPath.trimEnd('/').split('/').filter { it.isNotEmpty() }

            return when {
                segments.size >= 2 && segments[0] == "album" ->
                    DetectedLink(MusicProvider.BANDCAMP, LinkResourceType.ALBUM, navAlbum(canonical))

                segments.size >= 2 && segments[0] == "track" ->
                    DetectedLink(MusicProvider.BANDCAMP, LinkResourceType.TRACK, navAlbum(canonical))

                else -> {
                    val artistUrl = hostOf(canonical)?.let { "https://$it" } ?: canonical
                    DetectedLink(
                        MusicProvider.BANDCAMP,
                        LinkResourceType.ARTIST,
                        DeepLinkAction.Navigate(NavDestination.ArtistDetail(artistUrl, sourceId = "bandcamp")),
                    )
                }
            }
        }

        private fun navAlbum(url: String): DeepLinkAction = DeepLinkAction.Navigate(NavDestination.AlbumDetail(url))

        private fun attrs(tag: String): Map<String, String> = ATTR_RE.findAll(tag).associate { m ->
            val key = (m.groupValues[1].ifEmpty { m.groupValues[3] }).lowercase()
            val value = m.groupValues[2].ifEmpty { m.groupValues[4] }
            key to value
        }

        /** A meta tag identifies itself by either `name=` or `property=`. */
        private fun Map<String, String>.metaKey(): String? = this["property"] ?: this["name"]

        /**
         * True only for bandcamp.com or *.bandcamp.com hosts; a plain endsWith
         * check would also accept lookalikes such as "evilbandcamp.com".
         */
        private fun isBandcampHost(host: String): Boolean =
            host == "bandcamp.com" || host.endsWith(".bandcamp.com")

        private fun hostOf(url: String?): String? = url?.let {
            try {
                URI(it).host?.lowercase()
            } catch (_: Exception) {
                null
            }
        }

        private fun pathOf(url: String?): String? = url?.let {
            try {
                URI(it).path
            } catch (_: Exception) {
                null
            }
        }
    }
}
