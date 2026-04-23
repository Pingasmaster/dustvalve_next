package com.dustvalve.next.android.data.remote

import com.dustvalve.next.android.domain.model.Album
import com.dustvalve.next.android.domain.model.Artist
import com.dustvalve.next.android.util.HtmlUtils
import com.dustvalve.next.android.util.NetworkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.io.IOException
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DustvalveArtistScraper @Inject constructor(
    sharedClient: OkHttpClient,
) {

    /**
     * Bandcamp serves a heavily-stripped HTML when it sees a mobile User-Agent
     * (no `band-name-location`, no `band-photo`, no `signed-out-artists-bio-text`,
     * no `music-grid` — the artist hero is reduced to a JSON-LD `byArtist.name`
     * with no photo, no location, no bio). The shared `OkHttpClient` ships a
     * mobile UA via `userAgentInterceptor` (set in NetworkModule), so every
     * scrapeArtist call would produce an "Unknown Artist" with no metadata for
     * single-album-artists whose root URL redirects to the album page.
     *
     * Override the UA on a derived client that we own. The interceptor we add
     * runs after NetworkModule's mobile-UA interceptor in the chain, so its
     * `.header("User-Agent", DESKTOP_UA)` wins.
     */
    private val client: OkHttpClient = sharedClient.newBuilder()
        .addInterceptor(::desktopUserAgentInterceptor)
        .build()

    private fun desktopUserAgentInterceptor(chain: okhttp3.Interceptor.Chain): okhttp3.Response {
        val req = chain.request().newBuilder()
            .header("User-Agent", DESKTOP_UA)
            .build()
        return chain.proceed(req)
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Serializable
    private data class ClientItem(
        val type: String = "",
        val title: String = "",
        val artist: String = "",
        @SerialName("art_id") val artId: Long = 0,
        @SerialName("page_url") val pageUrl: String = "",
        val id: Long = 0,
    )

    suspend fun scrapeArtist(artistUrl: String): Artist = withContext(Dispatchers.IO) {
        require(NetworkUtils.isValidHttpsUrl(artistUrl)) { "Invalid URL: $artistUrl" }

        val request = Request.Builder()
            .url(artistUrl)
            .build()

        val call = client.newCall(request)
        coroutineContext[Job]?.invokeOnCompletion { cause -> if (cause != null) call.cancel() }
        var html = call.execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val finalPath = response.request.url.encodedPath
            if (finalPath.contains("/album/") || finalPath.contains("/track/")) {
                // Main page redirected to an album/track page — mobile layout lacks
                // artist metadata, so re-fetch the /music page instead.
                response.body.string() // consume body to release connection
                val baseUrl = artistUrl.trimEnd('/')
                val musicRequest = Request.Builder().url("$baseUrl/music").build()
                val musicCall = client.newCall(musicRequest)
                coroutineContext[Job]?.invokeOnCompletion { cause -> if (cause != null) musicCall.cancel() }
                musicCall.execute().use { musicResponse ->
                    if (!musicResponse.isSuccessful) throw IOException("HTTP ${musicResponse.code}")
                    musicResponse.body.string()
                }
            } else {
                response.body.string()
            }
        }
        ensureActive()

        val document = Jsoup.parse(html, artistUrl)

        val bandName = document.selectFirst("#band-name-location .title")?.text()?.trim()?.takeIf { it.isNotEmpty() }
            ?: document.selectFirst("p#band-name-location span.title")?.text()?.trim()?.takeIf { it.isNotEmpty() }
            ?: "Unknown Artist"

        val bio = document.selectFirst(".signed-out-artists-bio-text")?.let { bioEl ->
            // Bandcamp wraps long bios in a `bcTruncate` JS plugin: it hides the
            // overflow inside `<span class="peekaboo-text">` and renders a
            // `<span class="peekaboo-link"><span class="peekaboo-ellipsis">...</span>
            // <a>more</a></span>` overlay that the user clicks to expand. We don't
            // run the JS, so Jsoup's `.text()` would otherwise concatenate the
            // hidden long form AND the literal "...more" link tail. Drop the link
            // overlay (we keep `.peekaboo-text` so the FULL bio is returned).
            val cleaned = bioEl.clone()
            cleaned.select(".peekaboo-link, .peekaboo-ellipsis").remove()
            cleaned.text().trim().takeIf { it.isNotEmpty() }
        }

        // Band photos are wrapped in an <a class="popupImage" href="..._10.jpg">
        // (~480x480) around the small `<img class="band-photo" src="..._21.jpg">`
        // (100-ish px). Prefer the popupImage href so we don't show a blurry
        // thumbnail. Fall back to the small img src if the wrapper is missing.
        val imageUrl = document.selectFirst("a.popupImage:has(img.band-photo)")?.attr("abs:href")?.takeIf { it.isNotBlank() }
            ?: document.selectFirst(".band-photo img")?.attr("abs:src")
            ?: document.selectFirst("img.band-photo")?.attr("abs:src")

        val location = document.selectFirst("#band-name-location .location")?.text()?.trim()

        val artistId = stableId(artistUrl)

        val albums = mutableListOf<Album>()
        document.select("#music-grid .music-grid-item a, .music-grid-item a").forEach { element ->
            val albumHref = element.attr("abs:href").takeIf { it.isNotBlank() } ?: return@forEach
            val albumTitle = element.selectFirst(".title")?.text()?.trim()
                ?: element.selectFirst("p.title")?.text()?.trim()
                ?: return@forEach

            // Dustvalve uses data-original for lazy-loaded images (beyond ~8 albums)
            val artImg = element.selectFirst("img")?.let { img ->
                img.attr("abs:data-original").takeIf { it.isNotBlank() }
                    ?: img.attr("abs:src").takeIf { it.isNotBlank() && !it.endsWith("/img/0.gif") }
            }

            albums.add(
                Album(
                    id = stableId(albumHref),
                    url = albumHref,
                    title = albumTitle,
                    artist = bandName,
                    artistUrl = artistUrl,
                    artUrl = artImg ?: "",
                    releaseDate = null,
                    about = null,
                    tracks = emptyList(),
                    tags = emptyList(),
                )
            )
        }

        // Fallback: parse data-client-items JSON for albums missing art URLs
        val albumsMissingArt = albums.any { it.artUrl.isBlank() }
        if (albumsMissingArt) {
            val clientItemsRaw = document.selectFirst("ol[data-client-items]")
                ?.attr("data-client-items")
                ?.takeIf { it.isNotBlank() }
                ?.let { HtmlUtils.decodeHtmlEntities(it) }

            if (clientItemsRaw != null) {
                try {
                    val clientItems = json.decodeFromString<List<ClientItem>>(clientItemsRaw)
                    val artIdByUrl = clientItems
                        .filter { it.artId > 0 && it.pageUrl.isNotBlank() }
                        .associateBy(
                            { normalizeUrl(it.pageUrl) },
                            { NetworkUtils.buildArtUrl(it.artId) },
                        )

                    for (i in albums.indices) {
                        if (albums[i].artUrl.isBlank()) {
                            val normalizedUrl = normalizeUrl(albums[i].url)
                            val matchedArt = artIdByUrl[normalizedUrl]
                            if (matchedArt != null) {
                                albums[i] = albums[i].copy(artUrl = matchedArt)
                            }
                        }
                    }
                } catch (_: Exception) {
                    // JSON parsing failed — continue with whatever art URLs we have
                }
            }
        }

        // Single-album / single-track artists: the artist root URL redirects
        // to the album/track page and Bandcamp doesn't render a music-grid for
        // them on /music either. Fall back to the OpenGraph metadata so the
        // artist screen still surfaces the one item instead of looking empty.
        if (albums.isEmpty()) {
            val ogUrl = document.selectFirst("meta[property=og:url]")?.attr("content")
            if (!ogUrl.isNullOrBlank() && (ogUrl.contains("/album/") || ogUrl.contains("/track/"))) {
                val ogTitle = document.selectFirst("meta[property=og:title]")?.attr("content").orEmpty()
                // Bandcamp formats og:title as "Album Title, by Artist Name".
                val albumTitle = ogTitle.substringBefore(", by ").trim().takeIf { it.isNotEmpty() } ?: "Untitled"
                val albumArt = document.selectFirst("meta[property=og:image]")?.attr("content").orEmpty()
                albums.add(
                    Album(
                        id = stableId(ogUrl),
                        url = ogUrl,
                        title = albumTitle,
                        artist = bandName,
                        artistUrl = artistUrl,
                        artUrl = albumArt,
                        releaseDate = null,
                        about = null,
                        tracks = emptyList(),
                        tags = emptyList(),
                    )
                )
            }
        }

        Artist(
            id = artistId,
            name = bandName,
            url = artistUrl,
            imageUrl = imageUrl,
            bio = bio,
            location = location,
            albums = albums,
        )
    }

    private fun normalizeUrl(url: String): String {
        return url.trimEnd('/').substringBefore('?').substringBefore('#')
    }

    private fun stableId(input: String): String {
        val normalized = normalizeUrl(input)
        val bytes = MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray())
        return bytes.take(16).joinToString("") { "%02x".format(it) }
    }

    companion object {
        // A current desktop Chrome UA — Bandcamp serves the desktop layout
        // (with band-name-location / band-photo / signed-out-artists-bio-text
        // selectors we depend on) for any non-mobile-flagged UA.
        internal const val DESKTOP_UA =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }
}
