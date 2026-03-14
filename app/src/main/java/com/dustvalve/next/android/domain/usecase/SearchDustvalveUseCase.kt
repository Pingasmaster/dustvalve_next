package com.dustvalve.next.android.domain.usecase

import com.dustvalve.next.android.domain.model.SearchResult
import com.dustvalve.next.android.domain.model.SearchResultType
import com.dustvalve.next.android.domain.repository.SearchRepository
import javax.inject.Inject

class SearchDustvalveUseCase @Inject constructor(
    private val searchRepository: SearchRepository,
) {
    suspend operator fun invoke(
        query: String,
        page: Int = 1,
        type: SearchResultType? = null,
    ): List<SearchResult> {
        return searchRepository.search(query = query, page = page, type = type)
    }
}
