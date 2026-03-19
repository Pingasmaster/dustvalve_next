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
    private val client: OkHttpClient
) {

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
        val html = call.execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            response.body.string()
        }
        ensureActive()

        val document = Jsoup.parse(html, artistUrl)

        val bandName = document.selectFirst("#band-name-location .title")?.text()?.trim()?.takeIf { it.isNotEmpty() }
            ?: document.selectFirst("p#band-name-location span.title")?.text()?.trim()?.takeIf { it.isNotEmpty() }
            ?: "Unknown Artist"

        val bio = document.selectFirst(".signed-out-artists-bio-text")?.text()?.trim()

        val imageUrl = document.selectFirst(".band-photo img")?.attr("abs:src")
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
}
