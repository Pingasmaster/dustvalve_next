package com.dustvalve.next.android.domain.model

data class DiscoverResult(
    val albums: List<Album>,
    val cursor: String? = null,
)
