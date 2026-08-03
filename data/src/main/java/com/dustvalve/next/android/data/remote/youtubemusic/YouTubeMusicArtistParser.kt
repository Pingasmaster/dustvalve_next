package com.dustvalve.next.android.data.remote.youtubemusic

import com.dustvalve.next.android.domain.model.Track
import com.dustvalve.next.android.domain.model.TrackSource
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parses a YouTube Music WEB_REMIX `/browse` response for an artist channel
 * (`UC...`, including auto-generated "Topic" channels).
 *
 * Topic channels often have no YouTube Videos tab; the YTM artist page still
 * exposes Top songs, Albums / Singles shelves, and a link to the official
 * `MUSIC_PAGE_TYPE_USER_CHANNEL` when one exists.
 */
@Singleton
class YouTubeMusicArtistParser @Inject constructor() {

    data class ArtistAlbum(val browseId: String, val title: String, val artUrl: String, val year: String? = null)

    data class ArtistPage(
        val name: String?,
        val avatarUrl: String?,
        val songs: List<Track>,
        val albums: List<ArtistAlbum>,
        val linkedChannelId: String?,
    )

    fun parse(root: JsonElement, channelId: String): ArtistPage {
        val name = extractName(root)
        val avatarUrl = extractAvatarUrl(root)
        val songs = mutableListOf<Track>()
        val albums = mutableListOf<ArtistAlbum>()
        for (section in resolveSections(root)) {
            section.path("musicShelfRenderer")?.let { shelf ->
                songs += parseSongShelf(shelf, channelId, name)
            }
            section.path("musicCarouselShelfRenderer")?.let { carousel ->
                albums += parseAlbumCarousel(carousel)
            }
        }
        return ArtistPage(
            name = name,
            avatarUrl = avatarUrl,
            songs = songs.distinctBy { it.id },
            albums = albums.distinctBy { it.browseId },
            linkedChannelId = findLinkedChannelId(root, channelId),
        )
    }

    private fun extractName(root: JsonElement): String? = root.path("header")
        ?.path("musicImmersiveHeaderRenderer")
        ?.runsText("title")
        ?: root.path("header")
            ?.path("musicVisualHeaderRenderer")
            ?.runsText("title")

    private fun extractAvatarUrl(root: JsonElement): String? {
        val header = root.path("header")
            ?.path("musicImmersiveHeaderRenderer")
            ?: root.path("header")?.path("musicVisualHeaderRenderer")
            ?: return null
        // extractMusicThumbnail expects the parent of musicThumbnailRenderer
        // (header.thumbnail.musicThumbnailRenderer...), not the thumbnail node.
        return header.extractMusicThumbnail()
            ?: header.path("foregroundThumbnail")?.extractMusicThumbnail()
    }

    private fun resolveSections(root: JsonElement): List<JsonElement> {
        val contents = root.path("contents") ?: return emptyList()
        val tabs = contents.path("singleColumnBrowseResultsRenderer")?.path("tabs")?.arr()
            ?: contents.path("twoColumnBrowseResultsRenderer")?.path("tabs")?.arr()
            ?: return emptyList()
        for (tab in tabs) {
            val sections = tab.path("tabRenderer")
                ?.path("content")
                ?.path("sectionListRenderer")
                ?.path("contents")
                ?.arr()
            if (sections != null) {
                return sections.flatMap { flattenSection(it) }
            }
        }
        return emptyList()
    }

    private fun flattenSection(section: JsonElement): List<JsonElement> {
        val obj = section as? JsonObject ?: return listOf(section)
        return when (obj.keys.firstOrNull()) {
            "itemSectionRenderer" -> {
                section.path("itemSectionRenderer")?.path("contents")?.arr()
                    ?.flatMap { flattenSection(it) }
                    .orEmpty()
            }

            else -> listOf(section)
        }
    }

    private fun parseSongShelf(shelf: JsonElement, channelId: String, artistName: String?): List<Track> {
        val rows = shelf.path("contents")?.arr().orEmpty()
        return rows.mapIndexedNotNull { index, row ->
            parseSongRow(row, channelId, artistName, index + 1)
        }
    }

    private fun parseSongRow(wrapper: JsonElement, channelId: String, artistName: String?, trackNumber: Int): Track? {
        val item = wrapper.path("musicResponsiveListItemRenderer") ?: return null
        val videoId = extractVideoId(item) ?: return null
        val columns = item.path("flexColumns")?.arr().orEmpty()
        val title = columns.getOrNull(0)
            ?.path("musicResponsiveListItemFlexColumnRenderer")
            ?.path("text")
            ?.path("runs")
            ?.arr()
            ?.firstOrNull()
            ?.str("text")
            ?: return null
        val artist = columns.getOrNull(1)
            ?.path("musicResponsiveListItemFlexColumnRenderer")
            ?.runsText("text")
            ?: artistName
            ?: ""
        val art = item.extractMusicThumbnail().orEmpty()
        return Track(
            id = "yt_$videoId",
            albumId = "yt_channel_$channelId",
            title = title,
            artist = artist,
            artistUrl = "https://www.youtube.com/channel/$channelId",
            trackNumber = trackNumber,
            duration = 0f,
            streamUrl = "https://www.youtube.com/watch?v=$videoId",
            artUrl = art,
            albumTitle = "",
            source = TrackSource.YOUTUBE,
        )
    }

    private fun extractVideoId(item: JsonElement): String? {
        item.path("playlistItemData")?.str("videoId")?.let { return it }
        item.path("overlay")
            ?.path("musicItemThumbnailOverlayRenderer")
            ?.path("content")
            ?.path("musicPlayButtonRenderer")
            ?.path("playNavigationEndpoint")
            ?.path("watchEndpoint")
            ?.str("videoId")
            ?.let { return it }
        val titleRuns = item.path("flexColumns")?.arr()?.getOrNull(0)
            ?.path("musicResponsiveListItemFlexColumnRenderer")
            ?.path("text")
            ?.path("runs")
            ?.arr()
            .orEmpty()
        for (run in titleRuns) {
            run.path("navigationEndpoint")?.path("watchEndpoint")?.str("videoId")?.let { return it }
        }
        return null
    }

    private fun parseAlbumCarousel(carousel: JsonElement): List<ArtistAlbum> {
        val contents = carousel.path("contents")?.arr().orEmpty()
        if (contents.isEmpty()) return emptyList()
        val albums = contents.mapNotNull { parseAlbumTile(it) }
        // Only keep carousels that are actually album tiles (skip Videos /
        // Featured / Fans-also-like / artist chips).
        return if (albums.isNotEmpty()) albums else emptyList()
    }

    private fun parseAlbumTile(wrapper: JsonElement): ArtistAlbum? {
        val item = wrapper.path("musicTwoRowItemRenderer") ?: return null
        val browse = item.path("navigationEndpoint")?.path("browseEndpoint")
            ?: item.path("title")?.path("runs")?.arr()?.firstOrNull()
                ?.path("navigationEndpoint")?.path("browseEndpoint")
            ?: return null
        val pageType = browse.path("browseEndpointContextSupportedConfigs")
            ?.path("browseEndpointContextMusicConfig")
            ?.str("pageType")
            .orEmpty()
        if (!pageType.contains("ALBUM")) return null
        val browseId = browse.str("browseId")?.takeIf { it.startsWith("MPREb") } ?: return null
        val title = item.runsText("title") ?: return null
        val year = item.runsText("subtitle")
        return ArtistAlbum(
            browseId = browseId,
            title = title,
            artUrl = item.extractMusicThumbnail().orEmpty(),
            year = year,
        )
    }

    /**
     * Official artist channel linked from a Topic page, when present. Prefer
     * an explicit `MUSIC_PAGE_TYPE_USER_CHANNEL` browseId over a bare
     * `channelId` field so we do not confuse related-artist chips.
     */
    private fun findLinkedChannelId(root: JsonElement, topicChannelId: String): String? {
        var linked: String? = null
        fun walk(el: JsonElement?) {
            if (linked != null || el == null) return
            when (el) {
                is JsonObject -> {
                    val browse = el["browseEndpoint"] as? JsonObject
                    if (browse != null) {
                        val pageType = browse["browseEndpointContextSupportedConfigs"]
                            .let { it as? JsonObject }
                            ?.get("browseEndpointContextMusicConfig")
                            .let { it as? JsonObject }
                            ?.get("pageType")
                            .let { it as? JsonPrimitive }
                            ?.content
                            .orEmpty()
                        val browseId = (browse["browseId"] as? JsonPrimitive)?.content
                        if (isLinkedOfficialChannel(pageType, browseId, topicChannelId)) {
                            linked = browseId
                            return
                        }
                    }
                    el.values.forEach { walk(it) }
                }

                is JsonArray -> el.forEach { walk(it) }

                else -> Unit
            }
        }
        walk(root)
        return linked
    }

    private fun isLinkedOfficialChannel(pageType: String, browseId: String?, topicChannelId: String): Boolean =
        pageType == "MUSIC_PAGE_TYPE_USER_CHANNEL" &&
            browseId != null &&
            browseId.startsWith("UC") &&
            browseId != topicChannelId
}
