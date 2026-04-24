package com.dustvalve.next.android.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "artists", indices = [Index("url")])
data class ArtistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val url: String,
    val imageUrl: String?,
    val bio: String?,
    val location: String?,
    val cachedAt: Long = System.currentTimeMillis(),
    val autoDownload: Boolean = false,
    /** JSON-encoded list of album IDs in display order (from scraper) */
    val albumIdOrder: String? = null,
    val source: String = "bandcamp",
    /**
     * Bandcamp "meets_buy_full_discography_criteria" — true for artists who
     * surface a "buy full discography" offer. Cached on the entity so the
     * artist viewer can render the button without re-scraping.
     */
    val hasDiscographyOffer: Boolean = false,
)
