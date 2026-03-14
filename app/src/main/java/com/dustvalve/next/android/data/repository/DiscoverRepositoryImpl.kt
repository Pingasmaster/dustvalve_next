package com.dustvalve.next.android.data.repository

import com.dustvalve.next.android.data.remote.DustvalveDiscoverScraper
import com.dustvalve.next.android.domain.model.DiscoverResult
import com.dustvalve.next.android.domain.repository.DiscoverRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DiscoverRepositoryImpl @Inject constructor(
    private val discoverScraper: DustvalveDiscoverScraper,
) : DiscoverRepository {

    override suspend fun discover(genre: String?, cursor: String?): DiscoverResult {
        return discoverScraper.discover(genre = genre, cursor = cursor)
    }
}
