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
    /** Bandcamp "buy full discography" bundle offer, when present in the album's JSON-LD. */
    val discographyOffer: DiscographyOffer? = null,
    /**
     * Bandcamp's per-track default price (`tralbumData.defaultPrice`) — null
     * unless it differs from the whole-album [price]. Drives the "Buy a
     * single track" split-button option and the per-row price suffix.
     */
    val singleTrackPrice: AlbumPrice? = null,
)
