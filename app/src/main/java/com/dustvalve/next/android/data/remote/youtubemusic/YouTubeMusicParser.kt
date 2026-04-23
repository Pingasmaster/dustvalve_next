package com.dustvalve.next.android.data.remote.youtubemusic

import com.dustvalve.next.android.domain.model.ArtistItem
import com.dustvalve.next.android.domain.model.HeroItem
import com.dustvalve.next.android.domain.model.MoodChip
import com.dustvalve.next.android.domain.model.Shelf
import com.dustvalve.next.android.domain.model.SongItem
import com.dustvalve.next.android.domain.model.TileItem
import com.dustvalve.next.android.domain.model.TileKind
import com.dustvalve.next.android.domain.model.YouTubeMusicHomeFeed
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YouTubeMusicParser @Inject constructor() {

    fun parseHome(root: JsonElement): YouTubeMusicHomeFeed {
        val sectionList = root.path("contents")
            ?.path("singleColumnBrowseResultsRenderer")
            ?.path("tabs")?.arr()?.firstOrNull()
            ?.path("tabRenderer")?.path("content")?.path("sectionListRenderer")
            ?: return YouTubeMusicHomeFeed(emptyList(), emptyList())

        val chips = parseChips(sectionList.path("header")?.path("chipCloudRenderer"))
        val shelves = sectionList.path("contents")?.arr()
            ?.mapNotNull { parseShelf(it) }
            ?: emptyList()

        return YouTubeMusicHomeFeed(chips = chips, shelves = shelves)
    }

    private fun parseChips(chipCloud: JsonElement?): List<MoodChip> {
        chipCloud ?: return emptyList()
        val chips = chipCloud.path("chips")?.arr() ?: return emptyList()
        return chips.mapNotNull { chip ->
            val renderer = chip.path("chipCloudChipRenderer") ?: return@mapNotNull null
            val title = renderer.runsText("text") ?: return@mapNotNull null
            val params = renderer.path("navigationEndpoint")
                ?.path("browseEndpoint")?.path("params")?.str()
                ?: return@mapNotNull null
            MoodChip(title = title, params = params)
        }
    }

    private fun parseShelf(section: JsonElement): Shelf? {
        section.path("musicImmersiveCarouselShelfRenderer")?.let { return parseImmersive(it) }
        section.path("musicCarouselShelfRenderer")?.let { return parseCarousel(it) }
        section.path("musicShelfRenderer")?.let { return parseListShelf(it) }
        return null
    }

    private fun parseImmersive(renderer: JsonElement): Shelf? {
        val title = renderer.carouselTitle() ?: "Featured"
        val items = renderer.path("contents")?.arr()?.mapNotNull { parseTwoRowItemAsHero(it) } ?: emptyList()
        if (items.isEmpty()) return null
        return Shelf.Hero(title = title, items = items)
    }

    private fun parseCarousel(renderer: JsonElement): Shelf? {
        val title = renderer.carouselTitle() ?: return null
        val contents = renderer.path("contents")?.arr() ?: return null
        if (contents.isEmpty()) return null

        val firstChild = contents.first().jsonObject
        return when {
            firstChild.containsKey("musicResponsiveListItemRenderer") -> {
                val items = contents.mapNotNull { parseResponsiveListItemAsSong(it) }
                if (items.isEmpty()) null else Shelf.QuickPicks(title, items)
            }
            firstChild.containsKey("musicTwoRowItemRenderer") -> {
                val tiles = contents.mapNotNull { parseTwoRowItem(it) }
                if (tiles.isEmpty()) return null
                val allArtists = tiles.all { it.kind == RawKind.ARTIST_PLACEHOLDER }
                if (allArtists) {
                    Shelf.Artists(title, tiles.map { ArtistItem(it.id, it.title, it.thumbnailUrl) })
                } else {
                    val filtered = tiles.filter { it.kind != RawKind.ARTIST_PLACEHOLDER }
                        .map { TileItem(it.kind.toDomain(), it.id, it.title, it.subtitle, it.thumbnailUrl) }
                    if (filtered.isEmpty()) null else Shelf.Tiles(title, filtered)
                }
            }
            else -> null
        }
    }

    private fun parseListShelf(renderer: JsonElement): Shelf? {
        val title = renderer.runsText("title") ?: "Quick picks"
        val items = renderer.path("contents")?.arr()
            ?.mapNotNull { parseResponsiveListItemAsSong(it) }
            ?: emptyList()
        if (items.isEmpty()) return null
        return Shelf.QuickPicks(title, items)
    }

    private fun parseResponsiveListItemAsSong(wrapper: JsonElement): SongItem? {
        val item = wrapper.path("musicResponsiveListItemRenderer") ?: return null
        val columns = item.path("flexColumns")?.arr() ?: return null
        if (columns.isEmpty()) return null

        val titleRuns = columns.getOrNull(0)
            ?.path("musicResponsiveListItemFlexColumnRenderer")
            ?.path("text")?.path("runs")?.arr()
            ?: return null
        val title = titleRuns.firstOrNull()?.str("text") ?: return null
        val videoId = titleRuns.firstOrNull()
            ?.path("navigationEndpoint")?.path("watchEndpoint")?.path("videoId")?.str()
            ?: return null

        val subRuns = columns.getOrNull(1)
            ?.path("musicResponsiveListItemFlexColumnRenderer")
            ?.path("text")?.path("runs")?.arr()
            ?: JsonArray(emptyList())

        val artist = subRuns.firstOrNull { run ->
            run.path("navigationEndpoint")?.path("browseEndpoint")
                ?.path("browseEndpointContextSupportedConfigs")
                ?.path("browseEndpointContextMusicConfig")?.str("pageType")
                ?.contains("ARTIST") == true
        }?.str("text")
            ?: subRuns.firstOrNull()?.str("text")
            ?: ""

        val album = subRuns.firstOrNull { run ->
            run.path("navigationEndpoint")?.path("browseEndpoint")
                ?.path("browseEndpointContextSupportedConfigs")
                ?.path("browseEndpointContextMusicConfig")?.str("pageType")
                ?.contains("ALBUM") == true
        }?.str("text")

        val thumbnail = item.extractThumbnail()

        return SongItem(
            videoId = videoId,
            title = title,
            artist = artist,
            album = album,
            thumbnailUrl = thumbnail,
        )
    }

    private fun parseTwoRowItem(wrapper: JsonElement): RawTile? {
        val item = wrapper.path("musicTwoRowItemRenderer") ?: return null
        val title = item.runsText("title") ?: return null
        val subtitle = item.runsText("subtitle") ?: ""
        val thumbnail = item.extractThumbnail()

        val nav = item.path("navigationEndpoint") ?: return null
        val watchVideo = nav.path("watchEndpoint")?.path("videoId")?.str()
        val watchPlaylist = nav.path("watchPlaylistEndpoint")?.path("playlistId")?.str()
        val browse = nav.path("browseEndpoint")
        val browseId = browse?.path("browseId")?.str()
        val pageType = browse?.path("browseEndpointContextSupportedConfigs")
            ?.path("browseEndpointContextMusicConfig")?.str("pageType")

        return when {
            watchVideo != null -> RawTile(RawKind.SONG, watchVideo, title, subtitle, thumbnail)
            watchPlaylist != null -> RawTile(RawKind.PLAYLIST, watchPlaylist, title, subtitle, thumbnail)
            browseId != null && pageType?.contains("ALBUM") == true ->
                RawTile(RawKind.ALBUM, browseId, title, subtitle, thumbnail)
            browseId != null && pageType?.contains("PLAYLIST") == true ->
                RawTile(RawKind.PLAYLIST, browseId, title, subtitle, thumbnail)
            browseId != null && pageType?.contains("ARTIST") == true ->
                RawTile(RawKind.ARTIST_PLACEHOLDER, browseId, title, subtitle, thumbnail)
            browseId != null -> RawTile(RawKind.PLAYLIST, browseId, title, subtitle, thumbnail)
            else -> null
        }
    }

    private fun parseTwoRowItemAsHero(wrapper: JsonElement): HeroItem? {
        val raw = parseTwoRowItem(wrapper) ?: return null
        val videoId = if (raw.kind == RawKind.SONG) raw.id else null
        val playlistId = if (raw.kind == RawKind.PLAYLIST || raw.kind == RawKind.ALBUM) raw.id else null
        return HeroItem(
            videoId = videoId,
            playlistId = playlistId,
            title = raw.title,
            subtitle = raw.subtitle,
            thumbnailUrl = raw.thumbnailUrl,
        )
    }

    // Shape helpers operating on JsonElement in a null-safe way.

    private fun JsonElement.path(key: String): JsonElement? =
        (this as? JsonObject)?.get(key)

    private fun JsonElement.arr(): JsonArray? = this as? JsonArray

    private fun JsonElement.str(): String? =
        (this as? JsonPrimitive)?.let { if (it.isString) it.content else null }

    private fun JsonElement.str(key: String): String? = path(key)?.str()

    private fun JsonElement.runsText(key: String): String? {
        val runs = path(key)?.path("runs")?.arr()
        val fromRuns = runs?.firstOrNull()?.str("text")
        if (fromRuns != null) return fromRuns
        return path(key)?.path("simpleText")?.str()
    }

    private fun JsonElement.carouselTitle(): String? {
        return path("header")?.path("musicCarouselShelfBasicHeaderRenderer")
            ?.runsText("title")
    }

    private fun JsonElement.extractThumbnail(): String? {
        val thumbnails = path("thumbnail")?.path("musicThumbnailRenderer")
            ?.path("thumbnail")?.path("thumbnails")?.arr()
            ?: path("thumbnailRenderer")?.path("musicThumbnailRenderer")
                ?.path("thumbnail")?.path("thumbnails")?.arr()
            ?: return null

        return thumbnails.maxByOrNull { it.path("width")?.jsonPrimitive?.content?.toIntOrNull() ?: 0 }
            ?.str("url")
            ?.replace(Regex("=w\\d+-h\\d+.*$"), "=w544-h544-l90-rj")
    }

    private data class RawTile(
        val kind: RawKind,
        val id: String,
        val title: String,
        val subtitle: String,
        val thumbnailUrl: String?,
    )

    private enum class RawKind {
        SONG, ALBUM, PLAYLIST, VIDEO, ARTIST_PLACEHOLDER;

        fun toDomain(): TileKind = when (this) {
            SONG -> TileKind.SONG
            ALBUM -> TileKind.ALBUM
            PLAYLIST -> TileKind.PLAYLIST
            VIDEO -> TileKind.VIDEO
            ARTIST_PLACEHOLDER -> TileKind.PLAYLIST
        }
    }
}
