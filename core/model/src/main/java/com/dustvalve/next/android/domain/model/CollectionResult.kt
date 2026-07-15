package com.dustvalve.next.android.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class CollectionResult(
    val albums: List<Album>,
    val hasMore: Boolean,
    val lastToken: String?,
    val purchaseInfo: Map<String, PurchaseInfo> = emptyMap(),
)
