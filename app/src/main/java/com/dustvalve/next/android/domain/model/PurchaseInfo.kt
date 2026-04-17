package com.dustvalve.next.android.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class PurchaseInfo(
    val saleItemId: Long,
    val saleItemType: String,
)
