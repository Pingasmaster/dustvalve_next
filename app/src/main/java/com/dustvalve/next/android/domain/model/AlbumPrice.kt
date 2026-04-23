package com.dustvalve.next.android.domain.model

import androidx.compose.runtime.Immutable

/**
 * The "this album costs X" hint scraped from a Bandcamp album page's
 * JSON-LD `albumRelease[0].offers` block. Null when the page advertises no
 * price (free album, name-your-price with no minimum, missing JSON-LD).
 *
 * @param amount Major-unit price as a Double (e.g. `11.11`).
 * @param currency ISO 4217 code (e.g. `"CAD"`, `"USD"`, `"GBP"`).
 */
@Immutable
data class AlbumPrice(
    val amount: Double,
    val currency: String,
)
