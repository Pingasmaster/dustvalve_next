package com.dustvalve.next.android.data.remote

import com.dustvalve.next.android.di.qualifiers.AppDispatchers
import com.dustvalve.next.android.di.qualifiers.Dispatcher
import com.dustvalve.next.android.domain.model.Album
import com.dustvalve.next.android.domain.model.AlbumPrice
import com.dustvalve.next.android.domain.model.DiscographyOffer
import com.dustvalve.next.android.domain.model.Track
import com.dustvalve.next.android.util.HtmlUtils
import com.dustvalve.next.android.util.NetworkUtils
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

@Singleton
class DustvalveAlbumScraper @Inject constructor(
    private val client: OkHttpClient,
    @param:Dispatcher(AppDispatchers.IO) private val ioDispatcher: CoroutineDispatcher,
) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Serializable
    data class TralbumData(
        val url: String = "",
        val current: CurrentData = CurrentData(),
        val trackinfo: List<TrackInfo> = emptyList(),
        @SerialName("art_id") val artId: Long = 0,
        @SerialName("item_type") val itemType: String = "",
        @SerialName("album_url") val albumUrl: String? = null,
        /** Bandcamp's per-item suggested price for the artist (null on free/no-pricing items). */
        @SerialName("defaultPrice") val defaultPrice: Double? = null,
    )

    @Serializable
    data class CurrentData(
        val title: String = "",
        val artist: String? = null,
        @SerialName("band_id") val bandId: Long = 0,
        @SerialName("release_date") val releaseDate: String? = null,
        val about: String? = null,
        @SerialName("band_url") val bandUrl: String? = null,
    )

    @Serializable
    data class TrackInfo(
        // Nullable: Bandcamp ships `"id": null` for tracks on some albums
        // (compilations, pay-what-you-want singles, a.a.williams' `solstice`).
        // Using a default of 0 would collapse every such track onto the same
        // Track.id and crash LazyColumn with "Key ... was already used".
        val id: Long? = null,
        val title: String = "",
        // Single-track releases (e.g. moeshop's HARDCODED) ship `track_num: null`,
        // so this has to be nullable; downstream code coerces null to the
        // 1-based positional index.
        @SerialName("track_num") val trackNum: Int? = null,
        val duration: Float = 0f,
        val file: TrackFile? = null,
        // Path to the track's own page (e.g. `/track/cerise`); null when the
        // track isn't sold separately on Bandcamp.
        @SerialName("title_link") val titleLink: String? = null,
    )

    @Serializable
    data class TrackFile(@SerialName("mp3-128") val mp3128: String? = null)

    /** Intermediate scrape payload after HTML fetch + TralbumData decode. */
    private data class AlbumPagePayload(
        val requestedUrl: String,
        val html: String,
        val tralbum: TralbumData,
    )

    suspend fun scrapeAlbum(albumUrl: String, maxRedirects: Int = 3): Album = withContext(ioDispatcher) {
        require(NetworkUtils.isValidHttpsUrl(albumUrl)) { "Invalid Dustvalve URL: $albumUrl" }
        val payload = fetchAlbumPage(albumUrl)
        ensureActive()
        redirectToAlbumIfNeeded(payload, maxRedirects)?.let { return@withContext it }
        buildAlbum(payload)
    }

    private suspend fun fetchAlbumPage(albumUrl: String): AlbumPagePayload {
        val request = Request.Builder().url(albumUrl).build()
        val call = client.newCall(request)
        coroutineContext[Job]?.invokeOnCompletion { cause -> if (cause != null) call.cancel() }
        val html = call.execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            response.body.string()
        }
        val tralbumJson = HtmlUtils.extractJsonFromScript(html, "TralbumData")
            ?: HtmlUtils.extractDataAttribute(html, "data-tralbum")
            ?: throw IllegalStateException("Could not find TralbumData in page: $albumUrl")
        return AlbumPagePayload(
            requestedUrl = albumUrl,
            html = html,
            tralbum = json.decodeFromString(tralbumJson),
        )
    }

    /**
     * Track pages that advertise an album_url redirect to that album once.
     * Returns the redirected Album, or null when this page should be kept.
     */
    private suspend fun redirectToAlbumIfNeeded(payload: AlbumPagePayload, maxRedirects: Int): Album? {
        val tralbum = payload.tralbum
        if (tralbum.itemType != "track" || tralbum.albumUrl.isNullOrBlank()) return null
        if (maxRedirects <= 0) throw IOException("Too many redirects for ${payload.requestedUrl}")
        val parsedBase = URL(payload.requestedUrl)
        val resolvedAlbumUrl = URL(parsedBase, tralbum.albumUrl).toString()
        if (!NetworkUtils.isValidHttpsUrl(resolvedAlbumUrl)) return null
        return scrapeAlbum(resolvedAlbumUrl, maxRedirects - 1)
    }

    private fun buildAlbum(payload: AlbumPagePayload): Album {
        val albumUrl = payload.requestedUrl
        val html = payload.html
        val tralbumData = payload.tralbum

        val parsedUrl = URL(albumUrl)
        val baseUrl = "${parsedUrl.protocol}://${parsedUrl.host}"
        val rawArtistUrl = tralbumData.current.bandUrl ?: baseUrl
        val artistUrl = if (NetworkUtils.isValidHttpsUrl(rawArtistUrl)) rawArtistUrl else baseUrl
        // No art_id means no artwork; an unguarded a0_0.jpg URL would persist a 404.
        val artUrl = if (tralbumData.artId > 0) NetworkUtils.buildArtUrl(tralbumData.artId) else ""

        val albumId = stableId(tralbumData.url.ifEmpty { albumUrl })
        // Wire-level fallback persisted with the record; not localized on purpose
        // (metadata, not UI copy - the UI has its own localized "unknown" labels).
        val artistName = tralbumData.current.artist ?: extractArtistFromHtml(html) ?: "Unknown Artist"

        val tags = extractTags(html)
        val resolvedAlbumUrl = tralbumData.url.ifEmpty { albumUrl }
        val albumPrice = extractAlbumPrice(html)
        val tracks = tralbumData.trackinfo.mapIndexed { index, trackInfo ->
            trackFromInfo(
                trackInfo = trackInfo,
                index = index,
                albumId = albumId,
                artistName = artistName,
                artistUrl = artistUrl,
                artUrl = artUrl,
                albumTitle = tralbumData.current.title,
                albumUrl = resolvedAlbumUrl,
                parsedUrl = parsedUrl,
            )
        }

        return Album(
            id = albumId,
            url = tralbumData.url.ifEmpty { albumUrl },
            title = tralbumData.current.title,
            artist = artistName,
            artistUrl = artistUrl,
            artUrl = artUrl,
            releaseDate = tralbumData.current.releaseDate,
            about = tralbumData.current.about,
            tracks = tracks,
            tags = tags,
            price = albumPrice,
            discographyOffer = extractDiscographyOffer(html),
            singleTrackPrice = singleTrackPrice(tralbumData.defaultPrice, albumPrice),
        )
    }

    private fun trackFromInfo(
        trackInfo: TrackInfo,
        index: Int,
        albumId: String,
        artistName: String,
        artistUrl: String,
        artUrl: String,
        albumTitle: String,
        albumUrl: String,
        parsedUrl: URL,
    ): Track {
        // Prefer Bandcamp's stable track id when present, else fall back
        // to the 1-based positional index so sibling tracks with a null
        // track id on the same album don't collide on the key.
        val trackKey = trackInfo.id?.toString() ?: "idx${index + 1}"
        val trackPageUrl = trackInfo.titleLink
            ?.takeIf { it.isNotBlank() }
            ?.let { resolveAgainst(parsedUrl, it) }
            ?.takeIf { NetworkUtils.isValidHttpsUrl(it) }
        return Track(
            id = "${albumId}_$trackKey",
            albumId = albumId,
            title = trackInfo.title,
            artist = artistName,
            artistUrl = artistUrl,
            trackNumber = trackInfo.trackNum?.takeIf { it > 0 } ?: (index + 1),
            duration = trackInfo.duration,
            streamUrl = trackInfo.file?.mp3128,
            artUrl = artUrl,
            albumTitle = albumTitle,
            albumUrl = albumUrl,
            bandcampTrackUrl = trackPageUrl,
        )
    }

    /**
     * Only surface a per-track price when bandcamp gives us one AND it differs
     * from the album price; otherwise the "Buy a single track" option would be
     * redundant noise.
     */
    private fun singleTrackPrice(defaultPrice: Double?, albumPrice: AlbumPrice?): AlbumPrice? {
        if (defaultPrice == null || defaultPrice <= 0.0 || albumPrice == null) return null
        if (defaultPrice == albumPrice.amount) return null
        return AlbumPrice(amount = defaultPrice, currency = albumPrice.currency)
    }

    /**
     * Fetches a single Bandcamp track page and returns the per-track
     * `defaultPrice` parsed from its `data-tralbum` JSON. Used by the album
     * detail viewmodel to fill the row subtitle with each track's individual
     * sale price (Bandcamp doesn't ship per-track prices on the album page).
     *
     * Currency isn't reliably present on the track page's TralbumData, so the
     * caller passes the album-level currency (Bandcamp uses one currency per
     * artist). Returns null on any failure (404, parse error, no defaultPrice,
     * non-positive price) so a single bad track never crashes the album view.
     */
    suspend fun fetchTrackPrice(trackUrl: String, fallbackCurrency: String): AlbumPrice? = withContext(ioDispatcher) {
        if (!NetworkUtils.isValidHttpsUrl(trackUrl)) return@withContext null
        val request = Request.Builder().url(trackUrl).build()
        val call = client.newCall(request)
        coroutineContext[Job]?.invokeOnCompletion { cause -> if (cause != null) call.cancel() }
        val html = try {
            call.execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                response.body.string()
            }
        } catch (e: IOException) {
            return@withContext null
        }
        ensureActive()
        val tralbumJson = HtmlUtils.extractJsonFromScript(html, "TralbumData")
            ?: HtmlUtils.extractDataAttribute(html, "data-tralbum")
            ?: return@withContext null
        val tralbumData = try {
            json.decodeFromString<TralbumData>(tralbumJson)
        } catch (_: Throwable) {
            return@withContext null
        }
        val price = tralbumData.defaultPrice ?: return@withContext null
        if (price <= 0.0) return@withContext null
        AlbumPrice(amount = price, currency = fallbackCurrency)
    }

    /**
     * Extracts the album's headline buy price from the page's
     * `<script type="application/ld+json">` MusicAlbum block.
     *
     * Bandcamp's JSON-LD ships a `MusicAlbum` object whose `albumRelease`
     * array enumerates each purchase option (the album itself plus any
     * bundles or merch). The first entry is the album proper; we return
     * its `offers.price` + `offers.priceCurrency`. Falls back to null on:
     *   - free albums (offer present but missing price)
     *   - "name your price" with no minimum (price = 0 OR missing)
     *   - non-Bandcamp / non-MusicAlbum pages
     *   - parse failures (defensive - bad HTML never crashes the scraper)
     *
     * Public + open so unit tests can drive it from the captured fixtures
     * under `app/src/test/resources/fixtures/bandcamp/` without spinning
     * up a MockWebServer.
     */
    fun extractAlbumPrice(html: String): AlbumPrice? {
        // Match prior behaviour: once a MusicAlbum/MusicRecording release block
        // is found, do not scan later JSON-LD scripts even if this block has
        // no usable non-bundle offer.
        val releases = iterAlbumReleases(html).firstOrNull() ?: return null
        return releases.firstNotNullOfOrNull { release -> albumPriceFromRelease(release) }
    }

    /**
     * Extracts the artist's "buy full discography" bundle offer that bandcamp
     * embeds in every tralbum page's JSON-LD as the entry whose
     * `additionalProperty[item_type] == "b"`. Caching this on the album row
     * means the album viewer can show a "Buy full discography (N)" menu
     * option without re-scraping.
     */
    fun extractDiscographyOffer(html: String): DiscographyOffer? =
        iterAlbumReleases(html).firstNotNullOfOrNull { releases ->
            releases.firstNotNullOfOrNull { release -> discographyOfferFromRelease(release) }
        }

    private fun albumPriceFromRelease(release: JsonElement): AlbumPrice? {
        val obj = release as? JsonObject ?: return null
        // Skip the discography bundle (item_type == "b"); we want the
        // album/track itself (item_type == "a" or "t"), which on
        // bandcamp is always the first non-bundle entry.
        if (additionalProperty(obj, "item_type") == "b") return null
        val offer = obj["offers"] as? JsonObject ?: return null
        return parseOffer(offer)
    }

    private fun discographyOfferFromRelease(release: JsonElement): DiscographyOffer? {
        val obj = release as? JsonObject ?: return null
        if (additionalProperty(obj, "item_type") != "b") return null
        val offer = obj["offers"] as? JsonObject ?: return null
        val price = parseOffer(offer) ?: return null
        val url = offer["url"]?.let { it as? JsonPrimitive }?.contentOrNull
            ?: (obj["@id"] as? JsonPrimitive)?.contentOrNull
            ?: return null
        val name = (obj["name"] as? JsonPrimitive)?.contentOrNull.orEmpty()
        return DiscographyOffer(price = price, url = url, name = name)
    }

    /**
     * Yields the `albumRelease` arrays from each JSON-LD block on the page.
     * Bandcamp ships either a top-level `MusicAlbum` (regular album page) or
     * a `MusicRecording` whose `inAlbum.albumRelease[]` carries the same
     * shape (track-only releases like moe shop's HARDCODED). We unify both
     * here so price + discography extraction works in either case.
     */
    private fun iterAlbumReleases(html: String): Sequence<JsonArray> = sequence {
        val scriptRegex = Regex(
            """<script type="application/ld\+json"[^>]*>(.+?)</script>""",
            RegexOption.DOT_MATCHES_ALL,
        )
        for (m in scriptRegex.findAll(html)) {
            albumReleasesFromScript(m.groupValues[1].trim())?.let { yield(it) }
        }
    }

    private fun albumReleasesFromScript(body: String): JsonArray? {
        val root = try {
            json.parseToJsonElement(body)
        } catch (_: Throwable) {
            return null
        }
        val obj = root as? JsonObject ?: return null
        val type = obj["@type"]?.let { it as? JsonPrimitive }?.contentOrNull
        return when (type) {
            "MusicAlbum" -> obj["albumRelease"] as? JsonArray

            "MusicRecording" -> (
                (obj["inAlbum"] as? JsonObject)
                    ?.get("albumRelease") as? JsonArray
                )

            else -> null
        }
    }

    private fun parseOffer(offer: JsonObject): AlbumPrice? {
        val priceNum = offer["price"]?.let { it as? JsonPrimitive }
            ?.contentOrNull?.toDoubleOrNull()
        val currency = offer["priceCurrency"]?.let { it as? JsonPrimitive }
            ?.contentOrNull
        return if (priceNum != null && priceNum > 0.0 && !currency.isNullOrBlank()) {
            AlbumPrice(amount = priceNum, currency = currency)
        } else {
            null
        }
    }

    private fun additionalProperty(obj: JsonObject, name: String): String? {
        val arr = obj["additionalProperty"] as? JsonArray ?: return null
        return arr.firstNotNullOfOrNull { e ->
            val o = e as? JsonObject ?: return@firstNotNullOfOrNull null
            val n = (o["name"] as? JsonPrimitive)?.contentOrNull
            if (n == name) {
                (o["value"] as? JsonPrimitive)?.contentOrNull
            } else {
                null
            }
        }
    }

    private fun extractArtistFromHtml(html: String): String? {
        val candidates = listOf(
            {
                Regex("""<span[^>]*\bitemprop="byArtist"[^>]*>[^<]*<a[^>]*>([^<]+)</a>""")
                    .find(html)?.groupValues?.get(1)?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?.let { HtmlUtils.decodeHtmlEntities(it) }
            },
            {
                Regex("""<(?:span|p)[^>]*id="?band-name-location"?[^>]*>[\s\S]*?class="?title"?[^>]*>([^<]+)<""")
                    .find(html)?.groupValues?.get(1)?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?.let { HtmlUtils.decodeHtmlEntities(it) }
            },
            {
                HtmlUtils.extractMetaContent(html, "og:site_name")?.trim()?.takeIf { it.isNotBlank() }
            },
            {
                Regex("""class="?subheadline"?[^>]*>[\s\S]*?<a[^>]*>([^<]+)</a>""")
                    .find(html)?.groupValues?.get(1)?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?.let { HtmlUtils.decodeHtmlEntities(it) }
            },
        )
        return candidates.firstNotNullOfOrNull { it() }
    }

    private fun extractTags(html: String): List<String> {
        val regex = Regex("""<a[^>]*\bclass="[^"]*\btag\b[^"]*"[^>]*>([^<]+)</a>""")
        return regex.findAll(html).map { HtmlUtils.decodeHtmlEntities(it.groupValues[1].trim()) }.toList()
    }

    // Resolve a possibly-relative href against `base`, returning null if the
    // URL is malformed. URL(URL, String) throws MalformedURLException for bad
    // input; this is a narrow non-suspend wrapper.
    private fun resolveAgainst(base: URL, href: String): String? = try {
        URL(base, href).toString()
    } catch (_: java.net.MalformedURLException) {
        null
    }

    private fun normalizeUrl(url: String): String = url.trimEnd('/').substringBefore('?').substringBefore('#')

    private fun stableId(input: String): String {
        val normalized = normalizeUrl(input)
        val bytes = MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray())
        return bytes.take(16).joinToString("") { "%02x".format(Locale.ROOT, it) }
    }
}
