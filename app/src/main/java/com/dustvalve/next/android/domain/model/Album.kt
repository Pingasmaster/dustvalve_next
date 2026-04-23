package com.dustvalve.next.android.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class Album(
    val id: String,
    val url: String,
    val title: String,
    val artist: String,
    val artistUrl: String,
    val artUrl: String,
    val releaseDate: String?,
    val about: String?,
    val tracks: List<Track>,
    val tags: List<String>,
    val isFavorite: Boolean = false,
    val autoDownload: Boolean = false,
    val purchaseInfo: PurchaseInfo? = null,
    /** Bandcamp album price scraped from JSON-LD; null on free / unknown / non-Bandcamp. */
    val price: AlbumPrice? = null,
)
