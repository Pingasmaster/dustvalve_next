package com.dustvalve.next.android.domain.usecase

import com.dustvalve.next.android.domain.model.DiscoverResult
import com.dustvalve.next.android.domain.repository.DiscoverRepository
import javax.inject.Inject

class DiscoverDustvalveUseCase @Inject constructor(
    private val discoverRepository: DiscoverRepository,
) {
    suspend operator fun invoke(
        genre: String? = null,
        cursor: String? = null,
    ): DiscoverResult {
        return discoverRepository.discover(genre = genre, cursor = cursor)
    }
}
