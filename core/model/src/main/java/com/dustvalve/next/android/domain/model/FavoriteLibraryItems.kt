package com.dustvalve.next.android.domain.model

/**
 * Domain twins of the :core:database favorites-join projections
 * (FavoriteAlbumInfo / FavoriteArtistInfo in FavoriteJoinResults.kt).
 * Fields and nullability must stay 1:1 with those projections:
 * [FavoriteAlbumItem.albumArtUrl] is non-null String,
 * [FavoriteArtistItem.artistImageUrl] is String?.
 */
data class FavoriteAlbumItem(
    val id: String,
    val addedAt: Long,
    val isPinned: Boolean,
    val shapeKey: String?,
    val albumTitle: String,
    val albumArtist: String,
    val albumArtUrl: String,
    val albumUrl: String,
)

data class FavoriteArtistItem(
    val id: String,
    val addedAt: Long,
    val isPinned: Boolean,
    val shapeKey: String?,
    val artistName: String,
    val artistImageUrl: String?,
    val artistUrl: String,
)
