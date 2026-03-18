package com.dustvalve.next.android.data.remote

import com.dustvalve.next.android.domain.model.Album
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
        val id: Long = 0,
        val title: String = "",
        @SerialName("track_num") val trackNum: Int = 0,
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

        val tracks = tralbumData.trackinfo.mapIndexed { index, trackInfo ->
            Track(
                id = "${albumId}_${trackInfo.id}",
                albumId = albumId,
                title = trackInfo.title,
                artist = artistName,
                artistUrl = artistUrl,
                trackNumber = if (trackInfo.trackNum > 0) trackInfo.trackNum else index + 1,
                duration = trackInfo.duration,
                streamUrl = trackInfo.file?.mp3128,
                artUrl = artUrl,
                albumTitle = tralbumData.current.title,
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
        )
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
