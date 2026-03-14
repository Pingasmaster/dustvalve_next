package com.dustvalve.next.android.domain.model

data class Artist(
    val id: String,
    val name: String,
    val url: String,
    val imageUrl: String?,
    val bio: String?,
    val location: String?,
    val albums: List<Album>,
    val isFavorite: Boolean = false,
    val autoDownload: Boolean = false,
)
