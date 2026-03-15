package com.dustvalve.next.android.domain.model

data class Track(
    val id: String,
    val albumId: String,
    val title: String,
    val artist: String,
    val artistUrl: String = "",
    val trackNumber: Int,
    val duration: Float,
    val streamUrl: String?,
    val artUrl: String,
    val albumTitle: String,
    val isFavorite: Boolean = false,
    val isLocal: Boolean = false,
)
