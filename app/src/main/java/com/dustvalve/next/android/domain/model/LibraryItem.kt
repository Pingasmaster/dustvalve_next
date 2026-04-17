package com.dustvalve.next.android.domain.model

import androidx.compose.runtime.Immutable

@Immutable
sealed interface LibraryItem {
    val id: String
    val name: String
    val isPinned: Boolean
    val addedAt: Long
    val shapeKey: String?

    data class PlaylistItem(
        val playlist: Playlist,
    ) : LibraryItem {
        override val id get() = "playlist_${playlist.id}"
        override val name get() = playlist.name
        override val isPinned get() = playlist.isPinned
        override val addedAt get() = playlist.createdAt
        override val shapeKey get() = playlist.shapeKey
    }

    data class AlbumItem(
        val favoriteId: String,
        override val name: String,
        val artist: String,
        val artUrl: String,
        val albumUrl: String,
        override val isPinned: Boolean,
        override val addedAt: Long,
        override val shapeKey: String?,
    ) : LibraryItem {
        override val id get() = "album_$favoriteId"
    }

    data class ArtistItem(
        val favoriteId: String,
        override val name: String,
        val imageUrl: String?,
        val artistUrl: String,
        override val isPinned: Boolean,
        override val addedAt: Long,
        override val shapeKey: String?,
    ) : LibraryItem {
        override val id get() = "artist_$favoriteId"
    }
}
