package com.dustvalve.next.android.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class DiscoverResult(
    val albums: List<Album>,
    val cursor: String? = null,
)
