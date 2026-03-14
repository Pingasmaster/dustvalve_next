package com.dustvalve.next.android.domain.repository

import com.dustvalve.next.android.domain.model.AccountState
import kotlinx.coroutines.flow.Flow

interface AccountRepository {
    fun getAccountState(): Flow<AccountState>
    suspend fun saveCookies(cookies: Map<String, String>)
    suspend fun clearAccount()
    suspend fun getCookies(): Map<String, String>
}
