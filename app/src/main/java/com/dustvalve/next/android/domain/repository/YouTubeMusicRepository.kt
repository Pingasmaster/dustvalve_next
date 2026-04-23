package com.dustvalve.next.android.domain.repository

import com.dustvalve.next.android.domain.model.SearchResult
import com.dustvalve.next.android.domain.model.YouTubeMusicHomeFeed

interface YouTubeMusicRepository {
    suspend fun getHome(): YouTubeMusicHomeFeed
    suspend fun getMoodHome(params: String): YouTubeMusicHomeFeed
    suspend fun search(query: String, filter: String?): List<SearchResult>

    /**
     * Resolves a YT Music videoId to a direct streaming URL by hitting the
     * shared first-party Innertube /player layer (no NewPipe dependency).
     */
    suspend fun resolveStreamUrl(videoId: String): String
}
