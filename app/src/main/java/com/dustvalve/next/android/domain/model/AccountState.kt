package com.dustvalve.next.android.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class AccountState(
    val isLoggedIn: Boolean = false,
    val username: String? = null,
    val avatarUrl: String? = null,
    val fanId: Long? = null,
)
