package com.dustvalve.next.android.data.remote.youtubemusic

import com.dustvalve.next.android.domain.model.SearchResult
import com.dustvalve.next.android.domain.model.SearchResultType
import kotlinx.serialization.json.JsonElement
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YouTubeMusicSearchParser @Inject constructor() {

    fun parse(root: JsonElement): List<SearchResult> {
        val sectionList = root.path("contents")
            ?.path("tabbedSearchResultsRenderer")
            ?.path("tabs")?.arr()?.firstOrNull()
            ?.path("tabRenderer")?.path("content")?.path("sectionListRenderer")
            ?: return emptyList()

        val sections = sectionList.path("contents")?.arr() ?: return emptyList()

        val out = mutableListOf<SearchResult>()
        // Flatten itemSectionRenderer wrappers (YT sometimes nests shelves one
        // level deeper). Top-result musicCardShelfRenderer is rendered as a
        // single row before the long musicShelfRenderer list.
        for (section in sections.flatMap { flattenSection(it) }) {
            section.path("musicCardShelfRenderer")?.let { card ->
                parseTopResult(card)?.let { out += it }
            }
            section.path("musicShelfRenderer")?.let { shelf ->
                val items = shelf.path("contents")?.arr() ?: return@let
                for (item in items) {
                    parseRow(item)?.let { out += it }
                }
            }
        }
        return out
    }

    private fun flattenSection(section: JsonElement): List<JsonElement> {
        val obj = section as? kotlinx.serialization.json.JsonObject ?: return listOf(section)
        if (obj.keys.firstOrNull() != "itemSectionRenderer") return listOf(section)
        val inner = section.path("itemSectionRenderer")?.path("contents")?.arr() ?: return emptyList()
        return inner.flatMap { flattenSection(it) }
    }

    private fun parseTopResult(card: JsonElement): SearchResult? {
        val title = card.runsText("title") ?: return null
        val subtitle = card.runsText("subtitle")
        val thumbnail = card.extractMusicThumbnail()
        val nav = card.path("onTap") ?: card.path("buttons")
            ?.arr()?.firstOrNull()?.path("buttonRenderer")?.path("command")
        val watch = nav?.path("watchEndpoint")?.path("videoId")?.str()
        val browse = nav?.path("browseEndpoint")
        val browseId = browse?.path("browseId")?.str()
        val pageType = browse?.path("browseEndpointContextSupportedConfigs")
            ?.path("browseEndpointContextMusicConfig")?.str("pageType")

        return when {
            watch != null -> SearchResult(
                type = SearchResultType.YOUTUBE_TRACK,
                name = title,
                url = "https://www.youtube.com/watch?v=$watch",
                imageUrl = thumbnail,
                artist = subtitle,
                album = null, genre = null, releaseDate = null,
            )
            browseId != null && pageType?.contains("ARTIST") == true -> SearchResult(
                type = SearchResultType.YOUTUBE_ARTIST,
                name = title,
                url = "https://www.youtube.com/channel/$browseId",
                imageUrl = thumbnail,
                artist = null, album = null, genre = null, releaseDate = null,
            )
            browseId != null && pageType?.contains("ALBUM") == true -> SearchResult(
                type = SearchResultType.YOUTUBE_ALBUM,
                name = title,
                url = "https://www.youtube.com/playlist?list=${browseId.removePrefix("VL")}",
                imageUrl = thumbnail,
                artist = subtitle, album = null, genre = null, releaseDate = null,
            )
            browseId != null -> SearchResult(
                type = SearchResultType.YOUTUBE_PLAYLIST,
                name = title,
                url = "https://www.youtube.com/playlist?list=${browseId.removePrefix("VL")}",
                imageUrl = thumbnail,
                artist = subtitle, album = null, genre = null, releaseDate = null,
            )
            else -> null
        }
    }

    private fun parseRow(wrapper: JsonElement): SearchResult? {
        val item = wrapper.path("musicResponsiveListItemRenderer") ?: return null
        val columns = item.path("flexColumns")?.arr() ?: return null
        if (columns.isEmpty()) return null

        val titleRuns = columns[0].path("musicResponsiveListItemFlexColumnRenderer")
            ?.path("text")?.path("runs")?.arr()
            ?: return null
        val title = titleRuns.firstOrNull()?.str("text") ?: return null

        val subtitleRuns = columns.getOrNull(1)
            ?.path("musicResponsiveListItemFlexColumnRenderer")
            ?.path("text")?.path("runs")?.arr()

        // Subtitle text starts with the row "kind" label ("Song", "Artist",
        // "Album", "Playlist") then a separator and the artist / count. Drop
        // the leading kind label when assembling the SearchResult.artist field.
        val artist = subtitleRuns
            ?.drop(1)
            ?.mapNotNull { it.str("text") }
            ?.filter { it.isNotBlank() && it != " • " && it != " & " }
            ?.joinToString(", ")
            ?.takeIf { it.isNotBlank() }

        val thumbnail = item.extractMusicThumbnail()

        // Try the item-level navigationEndpoint first (artists/albums/playlists,
        // and many songs). Fall back to playlistItemData (videos shelf) and
        // finally to the first title run's navigationEndpoint.
        val itemNav = item.path("navigationEndpoint")
        val itemWatch = itemNav?.path("watchEndpoint")?.path("videoId")?.str()
        val playlistItemVideo = item.path("playlistItemData")?.path("videoId")?.str()
        val titleRunWatch = titleRuns.firstOrNull()
            ?.path("navigationEndpoint")?.path("watchEndpoint")?.path("videoId")?.str()
        val videoId = itemWatch ?: playlistItemVideo ?: titleRunWatch

        val browse = itemNav?.path("browseEndpoint")
        val browseId = browse?.path("browseId")?.str()
        val pageType = browse?.path("browseEndpointContextSupportedConfigs")
            ?.path("browseEndpointContextMusicConfig")?.str("pageType")

        return when {
            videoId != null -> SearchResult(
                type = SearchResultType.YOUTUBE_TRACK,
                name = title,
                url = "https://www.youtube.com/watch?v=$videoId",
                imageUrl = thumbnail,
                artist = artist,
                album = null,
                genre = null,
                releaseDate = null,
            )
            browseId != null && pageType?.contains("ARTIST") == true -> SearchResult(
                type = SearchResultType.YOUTUBE_ARTIST,
                name = title,
                url = "https://www.youtube.com/channel/$browseId",
                imageUrl = thumbnail,
                artist = null,
                album = null,
                genre = null,
                releaseDate = null,
            )
            browseId != null && pageType?.contains("ALBUM") == true -> SearchResult(
                type = SearchResultType.YOUTUBE_ALBUM,
                name = title,
                url = "https://www.youtube.com/playlist?list=${browseId.removePrefix("VL")}",
                imageUrl = thumbnail,
                artist = artist,
                album = null,
                genre = null,
                releaseDate = null,
            )
            browseId != null && pageType?.contains("PLAYLIST") == true -> SearchResult(
                type = SearchResultType.YOUTUBE_PLAYLIST,
                name = title,
                url = "https://www.youtube.com/playlist?list=${browseId.removePrefix("VL")}",
                imageUrl = thumbnail,
                artist = artist,
                album = null,
                genre = null,
                releaseDate = null,
            )
            else -> null
        }
    }
}
