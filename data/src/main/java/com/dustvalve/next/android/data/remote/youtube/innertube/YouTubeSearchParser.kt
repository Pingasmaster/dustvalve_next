package com.dustvalve.next.android.data.remote.youtube.innertube

import com.dustvalve.next.android.domain.model.SearchResult
import com.dustvalve.next.android.domain.model.SearchResultType
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parses standard YouTube /search responses (WEB client). Walks the
 * primary section list and emits domain SearchResults for the renderer
 * shapes we recognize: videoRenderer, channelRenderer, playlistRenderer,
 * and the newer lockupViewModel (used for some playlist results).
 *
 * Anything else (shelfRenderer, gridShelfViewModel, ads, etc.) is skipped
 * silently so the result list stays clean.
 *
 * Returns the response's continuation token alongside results so callers
 * can paginate via /search?continuation=<token>.
 */
@Singleton
class YouTubeSearchParser @Inject constructor() {

    data class Page(val items: List<SearchResult>, val continuation: String?)

    fun parse(root: JsonElement): Page {
        val sectionContents = resolvePrimarySectionList(root) ?: return Page(emptyList(), null)
        val items = mutableListOf<SearchResult>()
        var continuation: String? = null

        for (section in sectionContents) {
            // Continuation lives at the section list level, not inside
            // itemSectionRenderer. Look for it on every iteration.
            section.path("continuationItemRenderer")
                ?.path("continuationEndpoint")?.path("continuationCommand")
                ?.str("token")
                ?.let { continuation = it }

            val isr = section.path("itemSectionRenderer") ?: continue
            val rows = isr.path("contents")?.arr() ?: continue
            for (row in rows) {
                parseRow(row)?.let { items += it }
            }
        }
        return Page(items, continuation)
    }

    private fun resolvePrimarySectionList(root: JsonElement): List<JsonElement>? {
        val contents = root.path("contents") ?: return null
        // WEB (twoColumnSearchResultsRenderer) is the canonical shape for
        // our search calls. We also accept the legacy single-column shape
        // in case Innertube ever switches answer modes.
        val sl = contents
            .path("twoColumnSearchResultsRenderer")
            ?.path("primaryContents")?.path("sectionListRenderer")
            ?: contents.path("sectionListRenderer")
            ?: return null
        return sl.path("contents")?.arr()
    }

    private fun parseRow(row: JsonElement): SearchResult? {
        row.path("videoRenderer")?.let { return parseVideo(it) }
        row.path("playlistRenderer")?.let { return parsePlaylist(it) }
        row.path("channelRenderer")?.let { return parseChannel(it) }
        row.path("lockupViewModel")?.let { return parseLockup(it) }
        return null
    }

    private fun parseVideo(vr: JsonElement): SearchResult? {
        val videoId = vr.str("videoId") ?: return null
        val title = vr.runsText("title") ?: return null
        val artist = vr.runsText("ownerText") ?: vr.runsText("longBylineText")
        val thumbnail = vr.extractThumbnail()
        return SearchResult(
            type = SearchResultType.YOUTUBE_TRACK,
            name = title,
            url = "https://www.youtube.com/watch?v=$videoId",
            imageUrl = thumbnail,
            artist = artist,
            album = null,
            genre = null,
            releaseDate = null,
        )
    }

    private fun parsePlaylist(pr: JsonElement): SearchResult? {
        val playlistId = pr.str("playlistId") ?: return null
        val title = pr.runsText("title") ?: return null
        val owner = pr.runsText("shortBylineText") ?: pr.runsText("longBylineText")
        // playlistRenderer thumbnails live under thumbnailRenderer
        val thumbnail = pr.path("thumbnailRenderer")
            ?.path("playlistVideoThumbnailRenderer")
            ?.path("thumbnail")?.extractThumbnail()
            ?: pr.extractThumbnail()
        return SearchResult(
            type = SearchResultType.YOUTUBE_PLAYLIST,
            name = title,
            url = "https://www.youtube.com/playlist?list=$playlistId",
            imageUrl = thumbnail,
            artist = owner,
            album = null,
            genre = null,
            releaseDate = null,
        )
    }

    private fun parseChannel(cr: JsonElement): SearchResult? {
        val channelId = cr.str("channelId") ?: return null
        val title = cr.runsText("title") ?: return null
        val thumbnail = cr.path("thumbnail")?.extractThumbnail()
        return SearchResult(
            type = SearchResultType.YOUTUBE_ARTIST,
            name = title,
            url = "https://www.youtube.com/channel/$channelId",
            imageUrl = thumbnail,
            artist = null,
            album = null,
            genre = null,
            releaseDate = null,
        )
    }

    /**
     * Newer "view model" rendering used for playlist search results.
     * contentType="LOCKUP_CONTENT_TYPE_PLAYLIST" -> playlist; we ignore
     * other content types (videos still come back as videoRenderer for now).
     */
    private fun parseLockup(lvm: JsonElement): SearchResult? {
        val type = lvm.str("contentType") ?: return null
        if (type != "LOCKUP_CONTENT_TYPE_PLAYLIST") return null
        val playlistId = lvm.str("contentId") ?: return null
        val meta = lvm.path("metadata")?.path("lockupMetadataViewModel") ?: return null
        val title = meta.path("title")?.str("content") ?: return null
        val owner = meta.path("metadata")?.path("contentMetadataViewModel")
            ?.path("metadataRows")?.arr()?.firstOrNull()
            ?.path("metadataParts")?.arr()?.firstOrNull()
            ?.path("text")?.str("content")
        val thumbnail = lvm.path("contentImage")
            ?.path("collectionThumbnailViewModel")
            ?.path("primaryThumbnail")?.path("thumbnailViewModel")
            ?.path("image")?.path("sources")?.arr()
            ?.maxByOrNull { (it as? JsonObject)?.get("width")?.toString()?.toIntOrNull() ?: 0 }
            ?.str("url")
        return SearchResult(
            type = SearchResultType.YOUTUBE_PLAYLIST,
            name = title,
            url = "https://www.youtube.com/playlist?list=$playlistId",
            imageUrl = thumbnail,
            artist = owner,
            album = null,
            genre = null,
            releaseDate = null,
        )
    }
}
