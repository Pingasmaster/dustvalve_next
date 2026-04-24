package com.dustvalve.next.android.domain.model

import androidx.compose.runtime.Immutable

/**
 * A "buy the artist's full discography as a bundle" purchase option, scraped
 * from Bandcamp's per-tralbum JSON-LD. The fragment URL (e.g.
 * `.../track/foo#b104210103-buy`) opens Bandcamp's bundle buy modal in the
 * browser.
 */
@Immutable
data class DiscographyOffer(
    val price: AlbumPrice,
    val url: String,
    /** Bandcamp's user-facing label, e.g. "full digital discography (19 releases)". */
    val name: String,
)
