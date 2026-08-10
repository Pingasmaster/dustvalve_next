package com.dustvalve.next.android.data.remote.soundcloud

import com.dustvalve.next.android.domain.model.Album
import com.dustvalve.next.android.domain.model.Artist
import com.dustvalve.next.android.domain.model.MusicCollection
import com.dustvalve.next.android.domain.model.SearchResult
import com.dustvalve.next.android.domain.model.SearchResultType
import com.dustvalve.next.android.domain.model.SoundCloudHomeFeed
import com.dustvalve.next.android.domain.model.SoundCloudShelf
import com.dustvalve.next.android.domain.model.SoundCloudShelfItem
import com.dustvalve.next.android.domain.model.SoundCloudShelfKind
import com.dustvalve.next.android.domain.model.StreamPolicy
import com.dustvalve.next.android.domain.model.Track
import com.dustvalve.next.android.domain.model.TrackSource
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal fun JsonElement.path(key: String): JsonElement? = (this as? JsonObject)?.get(key)

internal fun JsonElement.arr(): JsonArray? = this as? JsonArray

internal fun JsonElement.obj(): JsonObject? = this as? JsonObject

internal fun JsonElement.str(): String? {
    val primitive = this as? JsonPrimitive ?: return null
    // JsonNull is a JsonPrimitive with content "null" and isString=false.
    if (this is JsonNull) return null
    return primitive.content
}

internal fun JsonElement.str(key: String): String? = path(key)?.str()

internal fun JsonElement.long(key: String): Long? = path(key)?.str()?.toLongOrNull()

internal fun JsonElement.int(key: String): Int? = path(key)?.str()?.toIntOrNull()

internal fun JsonElement.bool(key: String): Boolean? = (path(key) as? JsonPrimitive)?.content?.toBooleanStrictOrNull()

/** Upgrade SoundCloud `-large` artwork to `-t500x500`. */
internal fun upgradeArtworkUrl(url: String?): String? {
    if (url.isNullOrBlank()) return null
    return url
        .replace("-large.", "-t500x500.")
        .replace("-large?", "-t500x500?")
}

internal fun trackIdFromNumeric(numericId: String): String = "sc_$numericId"

internal fun numericIdFromTrackId(idOrUrl: String): String? {
    val trimmed = idOrUrl.trim()
    if (trimmed.startsWith("sc_")) {
        return trimmed.removePrefix("sc_").takeIf { it.all(Char::isDigit) }
    }
    if (trimmed.all(Char::isDigit)) return trimmed
    Regex("""(?:tracks/|soundcloud:tracks:)(\d+)""").find(trimmed)?.groupValues?.getOrNull(1)
        ?.let { return it }
    return null
}

object SoundCloudMappers {

    fun parseChartsTracks(root: JsonElement): List<Track> {
        val collection = root.path("collection")?.arr() ?: return emptyList()
        return collection.mapNotNull { entry ->
            val trackObj = entry.path("track") ?: entry
            parseTrack(trackObj)
        }
    }

    fun parseMixedSelections(root: JsonElement): List<SoundCloudShelf> {
        val shelves = root.path("collection")?.arr() ?: return emptyList()
        return shelves.mapNotNull { shelf ->
            val title = shelf.str("title")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val items = shelf.path("items")?.path("collection")?.arr()
                ?: shelf.path("items")?.arr()
                ?: JsonArray(emptyList())
            val mapped = items.mapNotNull { parseShelfItem(it) }
            if (mapped.isEmpty()) return@mapNotNull null
            SoundCloudShelf(title = title, items = mapped)
        }
    }

    fun parseHome(genre: String, chartsRoot: JsonElement, mixedRoot: JsonElement): SoundCloudHomeFeed = SoundCloudHomeFeed(
        genre = genre,
        trending = parseChartsTracks(chartsRoot),
        shelves = parseMixedSelections(mixedRoot),
    )

    fun parseSearchResults(root: JsonElement): List<SearchResult> {
        val collection = root.path("collection")?.arr() ?: return emptyList()
        return collection.mapNotNull { parseSearchItem(it) }
    }

    fun parseTrack(element: JsonElement): Track? {
        val id = element.long("id")?.toString() ?: element.str("id") ?: return null
        val title = element.str("title")?.takeIf { it.isNotBlank() } ?: return null
        val user = element.path("user")
        val artist = user?.str("username")
            ?: user?.str("full_name")
            ?: element.str("user")
            ?: ""
        val artistUrl = user?.str("permalink_url").orEmpty()
        val art = upgradeArtworkUrl(
            element.str("artwork_url")
                ?: user?.str("avatar_url"),
        ).orEmpty()
        val durationMs = element.long("full_duration")
            ?: element.long("duration")
            ?: 0L
        val permalink = element.str("permalink_url").orEmpty()
        return Track(
            id = trackIdFromNumeric(id),
            albumId = "",
            title = title,
            artist = artist,
            artistUrl = artistUrl,
            trackNumber = 0,
            duration = durationMs / 1000f,
            streamUrl = null,
            artUrl = art,
            albumTitle = "",
            source = TrackSource.SOUNDCLOUD,
            albumUrl = permalink,
            streamPolicy = inferStreamPolicy(element),
        )
    }

    fun parseArtist(element: JsonElement): Artist? {
        val id = element.long("id")?.toString() ?: element.str("id") ?: return null
        val name = element.str("username")
            ?: element.str("full_name")
            ?: return null
        val url = element.str("permalink_url") ?: return null
        return Artist(
            id = "sc_user_$id",
            name = name,
            url = url,
            imageUrl = upgradeArtworkUrl(element.str("avatar_url")),
            bio = element.str("description"),
            location = listOfNotNull(
                element.str("city")?.takeIf { it.isNotBlank() },
                element.str("country_code")?.takeIf { it.isNotBlank() },
            ).joinToString(", ").takeIf { it.isNotBlank() },
            albums = emptyList(),
        )
    }

    fun parseAlbum(element: JsonElement, tracks: List<Track>): Album? {
        val id = element.long("id")?.toString() ?: element.str("id") ?: return null
        val title = element.str("title")?.takeIf { it.isNotBlank() } ?: return null
        val url = element.str("permalink_url") ?: return null
        val user = element.path("user")
        val artist = user?.str("username").orEmpty()
        val artistUrl = user?.str("permalink_url").orEmpty()
        val art = upgradeArtworkUrl(
            element.str("artwork_url")
                ?: tracks.firstOrNull()?.artUrl?.takeIf { it.isNotBlank() },
        ).orEmpty()
        val numbered = tracks.mapIndexed { index, track ->
            track.copy(
                albumId = "sc_pl_$id",
                albumTitle = title,
                albumUrl = url,
                trackNumber = index + 1,
                artist = track.artist.ifBlank { artist },
                artistUrl = track.artistUrl.ifBlank { artistUrl },
            )
        }
        return Album(
            id = "sc_pl_$id",
            url = url,
            title = title,
            artist = artist,
            artistUrl = artistUrl,
            artUrl = art,
            releaseDate = element.str("release_date") ?: element.str("published_at")
                ?: element.str("created_at"),
            about = element.str("description"),
            tracks = numbered,
            tags = element.str("tag_list")
                ?.split(Regex("""\s+"""))
                ?.map { it.trim('"') }
                ?.filter { it.isNotBlank() }
                .orEmpty(),
        )
    }

    fun parseCollection(element: JsonElement, tracks: List<Track>, nextHref: String?): MusicCollection? {
        val id = element.long("id")?.toString() ?: element.str("id") ?: return null
        val title = element.str("title")?.takeIf { it.isNotBlank() } ?: return null
        val url = element.str("permalink_url") ?: return null
        val user = element.path("user")
        val owner = user?.str("username").orEmpty()
        val art = upgradeArtworkUrl(
            element.str("artwork_url")
                ?: tracks.firstOrNull()?.artUrl?.takeIf { it.isNotBlank() },
        )
        return MusicCollection(
            id = "sc_pl_$id",
            url = url,
            name = title,
            owner = owner,
            coverUrl = art,
            tracks = tracks.mapIndexed { index, track ->
                track.copy(
                    albumId = "sc_pl_$id",
                    albumTitle = title,
                    albumUrl = url,
                    trackNumber = index + 1,
                )
            },
            continuation = nextHref,
            hasMore = !nextHref.isNullOrBlank(),
        )
    }

    fun parsePagedTracks(root: JsonElement): Pair<List<Track>, String?> {
        val collection = root.path("collection")?.arr() ?: return emptyList<Track>() to null
        val tracks = collection.mapNotNull { entry ->
            // Likes / stream wrappers may nest the track.
            parseTrack(entry.path("track") ?: entry)
        }
        val next = root.str("next_href")?.takeIf { it.isNotBlank() }
        return tracks to next
    }

    fun playlistTrackStubs(element: JsonElement): List<String> {
        val tracks = element.path("tracks")?.arr() ?: return emptyList()
        return tracks.mapNotNull { t ->
            t.long("id")?.toString() ?: t.str("id")
        }
    }

    /** True when [element] looks like a SoundCloud album (set_type album/ep/single). */
    fun isAlbumSet(element: JsonElement): Boolean {
        val setType = element.str("set_type")?.lowercase().orEmpty()
        return setType in setOf("album", "ep", "single")
    }

    fun parseTracksArray(element: JsonElement): List<Track> = when (element) {
        is JsonArray -> element.mapNotNull { parseTrack(it) }

        is JsonObject -> {
            element.path("collection")?.arr()?.mapNotNull { parseTrack(it) }
                ?: listOfNotNull(parseTrack(element))
        }

        else -> emptyList()
    }

    fun pickBestTranscodingUrls(trackElement: JsonElement, progressiveOnly: Boolean = false): List<String> {
        val transcodings = trackElement.path("media")?.path("transcodings")?.arr()
            ?: return emptyList()
        data class Candidate(val url: String, val progressive: Boolean, val quality: Int)

        return transcodings.mapNotNull { t ->
            if (t.bool("snipped") == true) return@mapNotNull null
            val url = t.str("url") ?: return@mapNotNull null
            val protocol = t.path("format")?.str("protocol")?.lowercase().orEmpty()
            if (isEncryptedProtocol(protocol)) return@mapNotNull null
            if (protocol != "progressive" && protocol != "hls") return@mapNotNull null
            if (progressiveOnly && protocol != "progressive") return@mapNotNull null
            val quality = when (t.str("quality")?.lowercase()) {
                "hq" -> 3
                "sq" -> 2
                "lq" -> 1
                else -> 0
            }
            Candidate(url = url, progressive = protocol == "progressive", quality = quality)
        }
            .sortedWith(
                compareByDescending<Candidate> { it.progressive }
                    .thenByDescending { it.quality },
            )
            .map { it.url }
    }

    /** Best single URL for callers that only need one candidate. */
    fun pickBestTranscodingUrl(trackElement: JsonElement): String? = pickBestTranscodingUrls(trackElement).firstOrNull()

    /**
     * True when the track lists media but every non-snipped transcoding is
     * encrypted/Go+ DRM (no plain progressive or HLS left to try).
     */
    fun hasOnlyEncryptedTranscodings(trackElement: JsonElement): Boolean {
        val summary = summarizeTranscodings(trackElement) ?: return false
        return summary.hasEncrypted && !summary.hasProgressive && !summary.hasHls
    }

    /** True when any non-snipped encrypted/Go+ transcoding is present. */
    fun hasEncryptedTranscodings(trackElement: JsonElement): Boolean = summarizeTranscodings(trackElement)?.hasEncrypted == true

    /**
     * Infer [StreamPolicy] from media.transcodings without resolving URLs.
     *
     * Live charts often list plain progressive/HLS next to encrypted Go+
     * entries; those plain URLs 404 for anonymous clients. Any encrypted
     * listing therefore maps to [StreamPolicy.BLOCKED].
     */
    fun inferStreamPolicy(trackElement: JsonElement): StreamPolicy {
        val summary = summarizeTranscodings(trackElement) ?: return StreamPolicy.UNKNOWN
        if (summary.hasEncrypted) return StreamPolicy.BLOCKED
        if (summary.hasProgressive) return StreamPolicy.DOWNLOADABLE
        if (summary.hasHls) return StreamPolicy.STREAM_ONLY
        return StreamPolicy.UNKNOWN
    }

    private data class TranscodingSummary(val hasProgressive: Boolean, val hasHls: Boolean, val hasEncrypted: Boolean)

    private fun summarizeTranscodings(trackElement: JsonElement): TranscodingSummary? {
        val transcodings = trackElement.path("media")?.path("transcodings")?.arr()
            ?: return null
        var hasProgressive = false
        var hasHls = false
        var hasEncrypted = false
        for (t in transcodings) {
            if (t.bool("snipped") == true) continue
            val protocol = t.path("format")?.str("protocol")?.lowercase().orEmpty()
            when {
                isEncryptedProtocol(protocol) -> hasEncrypted = true
                protocol == "progressive" -> hasProgressive = true
                protocol == "hls" -> hasHls = true
            }
        }
        return TranscodingSummary(hasProgressive, hasHls, hasEncrypted)
    }

    private fun isEncryptedProtocol(protocol: String): Boolean = protocol.contains("encrypted") ||
        protocol.startsWith("ctr-") ||
        protocol.startsWith("cbc-")

    private fun parseShelfItem(element: JsonElement): SoundCloudShelfItem? {
        val kindRaw = element.str("kind")?.lowercase() ?: return null
        val id = element.long("id")?.toString() ?: element.str("id") ?: return null
        val title = element.str("title")
            ?: element.str("username")
            ?: return null
        val url = element.str("permalink_url") ?: return null
        val user = element.path("user")
        val subtitle = when (kindRaw) {
            "user" -> element.str("full_name").orEmpty()
            else -> user?.str("username").orEmpty()
        }
        val art = upgradeArtworkUrl(
            element.str("artwork_url")
                ?: element.str("avatar_url")
                ?: user?.str("avatar_url"),
        )
        val kind = when {
            kindRaw == "track" -> SoundCloudShelfKind.TRACK
            kindRaw == "user" -> SoundCloudShelfKind.USER
            kindRaw == "playlist" && isAlbumSet(element) -> SoundCloudShelfKind.ALBUM
            kindRaw == "playlist" -> SoundCloudShelfKind.PLAYLIST
            else -> return null
        }
        return SoundCloudShelfItem(
            kind = kind,
            id = id,
            title = title,
            subtitle = subtitle,
            url = url,
            artUrl = art,
        )
    }

    private fun parseSearchItem(element: JsonElement): SearchResult? {
        val kind = element.str("kind")?.lowercase() ?: return null
        return when (kind) {
            "track" -> {
                val track = parseTrack(element)
                val url = element.str("permalink_url")
                when {
                    track == null || url == null -> null

                    else -> SearchResult(
                        type = SearchResultType.SOUNDCLOUD_TRACK,
                        name = track.title,
                        url = url,
                        imageUrl = track.artUrl.takeIf { it.isNotBlank() },
                        artist = track.artist.takeIf { it.isNotBlank() },
                        album = null,
                        genre = element.str("genre"),
                        releaseDate = element.str("release_date") ?: element.str("created_at"),
                    )
                }
            }

            "user" -> {
                val artist = parseArtist(element)
                when (artist) {
                    null -> null

                    else -> SearchResult(
                        type = SearchResultType.SOUNDCLOUD_ARTIST,
                        name = artist.name,
                        url = artist.url,
                        imageUrl = artist.imageUrl,
                        artist = null,
                        album = null,
                        genre = null,
                        releaseDate = null,
                    )
                }
            }

            "playlist" -> {
                val title = element.str("title")
                val url = element.str("permalink_url")
                when {
                    title == null || url == null -> null

                    else -> SearchResult(
                        type = if (isAlbumSet(element)) {
                            SearchResultType.SOUNDCLOUD_ALBUM
                        } else {
                            SearchResultType.SOUNDCLOUD_PLAYLIST
                        },
                        name = title,
                        url = url,
                        imageUrl = upgradeArtworkUrl(element.str("artwork_url")),
                        artist = element.path("user")?.str("username"),
                        album = null,
                        genre = element.str("genre"),
                        releaseDate = element.str("release_date") ?: element.str("created_at"),
                    )
                }
            }

            else -> null
        }
    }
}
