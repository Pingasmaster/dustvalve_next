package com.dustvalve.next.android.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class YouTubeMusicAccountState(
    val isLoggedIn: Boolean = false,
)
