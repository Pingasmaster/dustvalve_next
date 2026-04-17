package com.dustvalve.next.android.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class SpotifyAccountState(
    val isConnected: Boolean = false,
)
