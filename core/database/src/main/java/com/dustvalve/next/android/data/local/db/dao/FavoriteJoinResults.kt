package com.dustvalve.next.android.data.local.db.dao

data class FavoriteAlbumInfo(
    val id: String,
    val addedAt: Long,
    val isPinned: Boolean,
    val shapeKey: String?,
    val albumTitle: String,
    val albumArtist: String,
    val albumArtUrl: String,
    val albumUrl: String,
)

data class FavoriteArtistInfo(
    val id: String,
    val addedAt: Long,
    val isPinned: Boolean,
    val shapeKey: String?,
    val artistName: String,
    val artistImageUrl: String?,
    val artistUrl: String,
)
