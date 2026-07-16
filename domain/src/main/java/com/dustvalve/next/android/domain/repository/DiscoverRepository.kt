package com.dustvalve.next.android.domain.repository

import com.dustvalve.next.android.domain.model.DiscoverResult

interface DiscoverRepository {
    suspend fun discover(genre: String? = null, cursor: String? = null): DiscoverResult
}
