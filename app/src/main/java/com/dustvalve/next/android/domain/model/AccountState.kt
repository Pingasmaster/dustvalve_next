package com.dustvalve.next.android.domain.model

data class AccountState(
    val isLoggedIn: Boolean = false,
    val username: String? = null,
    val avatarUrl: String? = null,
    val fanId: Long? = null,
)
