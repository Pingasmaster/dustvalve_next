package com.dustvalve.next.android.domain.repository

import com.dustvalve.next.android.domain.model.SearchResult
import com.dustvalve.next.android.domain.model.SearchResultType

interface SearchRepository {
    suspend fun search(
        query: String,
        page: Int = 1,
        type: SearchResultType? = null,
    ): List<SearchResult>
}
