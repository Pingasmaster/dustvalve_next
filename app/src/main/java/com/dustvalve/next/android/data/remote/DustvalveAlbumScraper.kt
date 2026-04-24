package com.dustvalve.next.android.data.remote

import com.dustvalve.next.android.domain.model.Album
import com.dustvalve.next.android.domain.model.AlbumPrice
import com.dustvalve.next.android.domain.model.Track
import com.dustvalve.next.android.util.HtmlUtils
import com.dustvalve.next.android.util.NetworkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URL
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DustvalveAlbumScraper @Inject constructor(
    private val client: OkHttpClient
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
    )

    @Serializable
    data class TrackFile(
        @SerialName("mp3-128") val mp3128: String? = null,
    )

    suspend fun scrapeAlbum(albumUrl: String, maxRedirects: Int = 3): Album = withContext(Dispatchers.IO) {
        require(NetworkUtils.isValidHttpsUrl(albumUrl)) { "Invalid Dustvalve URL: $albumUrl" }

        val request = Request.Builder()
            .url(albumUrl)
            .build()

        val call = client.newCall(request)
        coroutineContext[Job]?.invokeOnCompletion { cause -> if (cause != null) call.cancel() }
        val html = call.execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            response.body.string()
        }
        ensureActive()

        val tralbumJson = HtmlUtils.extractJsonFromScript(html, "TralbumData")
            ?: HtmlUtils.extractDataAttribute(html, "data-tralbum")
            ?: throw IllegalStateException("Could not find TralbumData in page: $albumUrl")

        val tralbumData = json.decodeFromString<TralbumData>(tralbumJson)

        // If this is a track page with an album URL, redirect to the album page
        if (tralbumData.itemType == "track" && !tralbumData.albumUrl.isNullOrBlank()) {
            if (maxRedirects <= 0) throw IOException("Too many redirects for $albumUrl")
            val parsedBase = URL(albumUrl)
            val resolvedAlbumUrl = URL(parsedBase, tralbumData.albumUrl).toString()
            if (NetworkUtils.isValidHttpsUrl(resolvedAlbumUrl)) {
                ensureActive()
                return@withContext scrapeAlbum(resolvedAlbumUrl, maxRedirects - 1)
            }
        }

        val parsedUrl = URL(albumUrl)
        val baseUrl = "${parsedUrl.protocol}://${parsedUrl.host}"
        val rawArtistUrl = tralbumData.current.bandUrl ?: baseUrl
        val artistUrl = if (NetworkUtils.isValidHttpsUrl(rawArtistUrl)) rawArtistUrl else baseUrl
        val artUrl = "https://f4.bcbits.com/img/a${tralbumData.artId}_10.jpg"

        val albumId = stableId(tralbumData.url.ifEmpty { albumUrl })
        val artistName = tralbumData.current.artist ?: extractArtistFromHtml(html) ?: "Unknown Artist"

        val tags = extractTags(html)

        val resolvedAlbumUrl = tralbumData.url.ifEmpty { albumUrl }
        val tracks = tralbumData.trackinfo.mapIndexed { index, trackInfo ->
            // Prefer Bandcamp's stable track id when present, else fall back
            // to the 1-based positional index so sibling tracks with a null
            // track id on the same album don't collide on the key.
            val trackKey = trackInfo.id?.toString() ?: "idx${index + 1}"
            Track(
                id = "${albumId}_$trackKey",
                albumId = albumId,
                title = trackInfo.title,
                artist = artistName,
                artistUrl = artistUrl,
                trackNumber = trackInfo.trackNum?.takeIf { it > 0 } ?: (index + 1),
                duration = trackInfo.duration,
                streamUrl = trackInfo.file?.mp3128,
                artUrl = artUrl,
                albumTitle = tralbumData.current.title,
                albumUrl = resolvedAlbumUrl,
            )
        }

        Album(
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
            price = extractAlbumPrice(html),
            discographyOffer = extractDiscographyOffer(html),
            singleTrackPrice = run {
                val albumPrice = extractAlbumPrice(html)
                val def = tralbumData.defaultPrice
                // Only surface a per-track price when bandcamp gives us one
                // AND it differs from the album price; otherwise the "Buy a
                // single track" option would be redundant noise.
                if (def != null && def > 0.0 && albumPrice != null && def != albumPrice.amount) {
                    AlbumPrice(amount = def, currency = albumPrice.currency)
                } else null
            },
        )
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
     *   - parse failures (defensive — bad HTML never crashes the scraper)
     *
     * Public + open so unit tests can drive it from the captured fixtures
     * under `app/src/test/resources/fixtures/bandcamp/` without spinning
     * up a MockWebServer.
     */
    fun extractAlbumPrice(html: String): AlbumPrice? {
        for (releases in iterAlbumReleases(html)) {
            for (release in releases) {
                val obj = release as? kotlinx.serialization.json.JsonObject ?: continue
                // Skip the discography bundle (item_type == "b"); we want the
                // album/track itself (item_type == "a" or "t"), which on
                // bandcamp is always the first non-bundle entry.
                val itemType = additionalProperty(obj, "item_type")
                if (itemType == "b") continue
                val offer = obj["offers"] as? kotlinx.serialization.json.JsonObject ?: continue
                val price = parseOffer(offer) ?: continue
                return price
            }
            return null  // Found a release block but no usable non-bundle offer.
        }
        return null
    }

    /**
     * Extracts the artist's "buy full discography" bundle offer that bandcamp
     * embeds in every tralbum page's JSON-LD as the entry whose
     * `additionalProperty[item_type] == "b"`. Caching this on the album row
     * means the album viewer can show a "Buy full discography (N)" menu
     * option without re-scraping.
     */
    fun extractDiscographyOffer(html: String): com.dustvalve.next.android.domain.model.DiscographyOffer? {
        for (releases in iterAlbumReleases(html)) {
            for (release in releases) {
                val obj = release as? kotlinx.serialization.json.JsonObject ?: continue
                val itemType = additionalProperty(obj, "item_type")
                if (itemType != "b") continue
                val offer = obj["offers"] as? kotlinx.serialization.json.JsonObject ?: continue
                val price = parseOffer(offer) ?: continue
                val url = offer["url"]?.let { it as? kotlinx.serialization.json.JsonPrimitive }
                    ?.contentOrNull
                    ?: (obj["@id"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
                    ?: continue
                val name = (obj["name"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull.orEmpty()
                return com.dustvalve.next.android.domain.model.DiscographyOffer(
                    price = price,
                    url = url,
                    name = name,
                )
            }
        }
        return null
    }

    /**
     * Yields the `albumRelease` arrays from each JSON-LD block on the page.
     * Bandcamp ships either a top-level `MusicAlbum` (regular album page) or
     * a `MusicRecording` whose `inAlbum.albumRelease[]` carries the same
     * shape (track-only releases like moe shop's HARDCODED). We unify both
     * here so price + discography extraction works in either case.
     */
    private fun iterAlbumReleases(html: String): Sequence<kotlinx.serialization.json.JsonArray> = sequence {
        val scriptRegex = Regex(
            """<script type="application/ld\+json"[^>]*>(.+?)</script>""",
            RegexOption.DOT_MATCHES_ALL,
        )
        for (m in scriptRegex.findAll(html)) {
            val body = m.groupValues[1].trim()
            val root = try { json.parseToJsonElement(body) } catch (_: Throwable) { continue }
            val obj = root as? kotlinx.serialization.json.JsonObject ?: continue
            val type = obj["@type"]?.let { it as? kotlinx.serialization.json.JsonPrimitive }?.contentOrNull
            val releases: kotlinx.serialization.json.JsonArray? = when (type) {
                "MusicAlbum" -> obj["albumRelease"] as? kotlinx.serialization.json.JsonArray
                "MusicRecording" -> ((obj["inAlbum"] as? kotlinx.serialization.json.JsonObject)
                    ?.get("albumRelease") as? kotlinx.serialization.json.JsonArray)
                else -> null
            }
            if (releases != null) yield(releases)
        }
    }

    private fun parseOffer(offer: kotlinx.serialization.json.JsonObject): AlbumPrice? {
        val priceNum = offer["price"]?.let { it as? kotlinx.serialization.json.JsonPrimitive }
            ?.contentOrNull?.toDoubleOrNull()
        val currency = offer["priceCurrency"]?.let { it as? kotlinx.serialization.json.JsonPrimitive }
            ?.contentOrNull
        return if (priceNum != null && priceNum > 0.0 && !currency.isNullOrBlank()) {
            AlbumPrice(amount = priceNum, currency = currency)
        } else null
    }

    private fun additionalProperty(obj: kotlinx.serialization.json.JsonObject, name: String): String? {
        val arr = obj["additionalProperty"] as? kotlinx.serialization.json.JsonArray ?: return null
        for (e in arr) {
            val o = e as? kotlinx.serialization.json.JsonObject ?: continue
            val n = (o["name"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
            if (n == name) {
                return (o["value"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
            }
        }
        return null
    }

    private fun extractArtistFromHtml(html: String): String? {
        // 1. Schema.org itemprop="byArtist" — most structured
        val byArtist = Regex("""<span[^>]*\bitemprop="byArtist"[^>]*>[^<]*<a[^>]*>([^<]+)</a>""")
            .find(html)?.groupValues?.get(1)?.trim()
        if (!byArtist.isNullOrBlank()) return HtmlUtils.decodeHtmlEntities(byArtist)

        // 2. Band name from #band-name-location (present on all Dustvalve pages)
        val bandName = Regex("""<(?:span|p)[^>]*id="?band-name-location"?[^>]*>[\s\S]*?class="?title"?[^>]*>([^<]+)<""")
            .find(html)?.groupValues?.get(1)?.trim()
        if (!bandName.isNullOrBlank()) return HtmlUtils.decodeHtmlEntities(bandName)

        // 3. og:site_name meta tag — Dustvalve sets this to the band name
        val ogSiteName = HtmlUtils.extractMetaContent(html, "og:site_name")?.trim()
        if (!ogSiteName.isNullOrBlank()) return ogSiteName

        // 4. Artist name from the #name-section .subheadline a
        val subheadline = Regex("""class="?subheadline"?[^>]*>[\s\S]*?<a[^>]*>([^<]+)</a>""")
            .find(html)?.groupValues?.get(1)?.trim()
        if (!subheadline.isNullOrBlank()) return HtmlUtils.decodeHtmlEntities(subheadline)

        return null
    }

    private fun extractTags(html: String): List<String> {
        val regex = Regex("""<a[^>]*\bclass="[^"]*\btag\b[^"]*"[^>]*>([^<]+)</a>""")
        return regex.findAll(html).map { HtmlUtils.decodeHtmlEntities(it.groupValues[1].trim()) }.toList()
    }

    private fun normalizeUrl(url: String): String {
        return url.trimEnd('/').substringBefore('?').substringBefore('#')
    }

    private fun stableId(input: String): String {
        val normalized = normalizeUrl(input)
        val bytes = MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray())
        return bytes.take(16).joinToString("") { "%02x".format(it) }
    }
}
