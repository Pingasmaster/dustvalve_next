package com.dustvalve.next.android.domain.model

import androidx.compose.runtime.Immutable

@Immutable
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
    /**
     * True for Bandcamp artists whose data-band JSON has
     * `meets_buy_full_discography_criteria: true`. Cached on the entity so
     * subsequent loads don't refetch the artist page just to check.
     */
    val hasDiscographyOffer: Boolean = false,
)
