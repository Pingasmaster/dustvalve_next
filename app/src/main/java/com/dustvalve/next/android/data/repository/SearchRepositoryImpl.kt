package com.dustvalve.next.android.data.repository

import com.dustvalve.next.android.data.remote.DustvalveSearchScraper
import com.dustvalve.next.android.domain.model.SearchResult
import com.dustvalve.next.android.domain.model.SearchResultType
import com.dustvalve.next.android.domain.repository.SearchRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchRepositoryImpl @Inject constructor(
    private val searchScraper: DustvalveSearchScraper,
) : SearchRepository {

    override suspend fun search(
        query: String,
        page: Int,
        type: SearchResultType?
    ): List<SearchResult> {
        return searchScraper.search(query = query, page = page, type = type)
    }
}
