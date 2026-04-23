package com.dustvalve.next.android.data.repository

import com.dustvalve.next.android.data.remote.youtubemusic.YouTubeMusicInnertubeClient
import com.dustvalve.next.android.data.remote.youtubemusic.YouTubeMusicParser
import com.dustvalve.next.android.domain.model.YouTubeMusicHomeFeed
import com.dustvalve.next.android.domain.repository.YouTubeMusicRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YouTubeMusicRepositoryImpl @Inject constructor(
    private val client: YouTubeMusicInnertubeClient,
    private val parser: YouTubeMusicParser,
) : YouTubeMusicRepository {

    override suspend fun getHome(): YouTubeMusicHomeFeed {
        val response = client.browse(browseId = HOME_BROWSE_ID)
        return parser.parseHome(response)
    }

    override suspend fun getMoodHome(params: String): YouTubeMusicHomeFeed {
        val response = client.browse(browseId = HOME_BROWSE_ID, params = params)
        return parser.parseHome(response)
    }

    private companion object {
        const val HOME_BROWSE_ID = "FEmusic_home"
    }
}
