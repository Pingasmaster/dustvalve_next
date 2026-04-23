package com.dustvalve.next.android.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class YouTubeMusicHomeFeed(
    val chips: List<MoodChip>,
    val shelves: List<Shelf>,
)

@Immutable
data class MoodChip(
    val title: String,
    val params: String,
)

sealed interface Shelf {
    val title: String

    @Immutable
    data class QuickPicks(
        override val title: String,
        val items: List<SongItem>,
    ) : Shelf

    @Immutable
    data class Hero(
        override val title: String,
        val items: List<HeroItem>,
    ) : Shelf

    @Immutable
    data class Tiles(
        override val title: String,
        val items: List<TileItem>,
    ) : Shelf

    @Immutable
    data class Artists(
        override val title: String,
        val items: List<ArtistItem>,
    ) : Shelf
}

@Immutable
data class SongItem(
    val videoId: String,
    val title: String,
    val artist: String,
    val album: String?,
    val thumbnailUrl: String?,
)

@Immutable
data class HeroItem(
    val videoId: String?,
    val playlistId: String?,
    val title: String,
    val subtitle: String,
    val thumbnailUrl: String?,
)

@Immutable
data class TileItem(
    val kind: TileKind,
    val id: String,
    val title: String,
    val subtitle: String,
    val thumbnailUrl: String?,
)

enum class TileKind { SONG, ALBUM, PLAYLIST, VIDEO }

@Immutable
data class ArtistItem(
    val browseId: String,
    val name: String,
    val thumbnailUrl: String?,
)
