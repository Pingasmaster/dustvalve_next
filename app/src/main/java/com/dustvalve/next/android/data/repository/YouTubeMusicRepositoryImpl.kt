package com.dustvalve.next.android.data.repository

import com.dustvalve.next.android.data.remote.youtubemusic.YouTubeMusicInnertubeClient
import com.dustvalve.next.android.data.remote.youtubemusic.YouTubeMusicParser
import com.dustvalve.next.android.data.remote.youtubemusic.YouTubeMusicSearchParser
import com.dustvalve.next.android.domain.model.SearchResult
import com.dustvalve.next.android.domain.model.YouTubeMusicHomeFeed
import com.dustvalve.next.android.domain.repository.YouTubeMusicRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YouTubeMusicRepositoryImpl @Inject constructor(
    private val client: YouTubeMusicInnertubeClient,
    private val parser: YouTubeMusicParser,
    private val searchParser: YouTubeMusicSearchParser,
) : YouTubeMusicRepository {

    override suspend fun getHome(): YouTubeMusicHomeFeed {
        val response = client.browse(browseId = HOME_BROWSE_ID)
        return parser.parseHome(response)
    }

    override suspend fun getMoodHome(params: String): YouTubeMusicHomeFeed {
        val response = client.browse(browseId = HOME_BROWSE_ID, params = params)
        return parser.parseHome(response)
    }

    override suspend fun search(query: String, filter: String?): List<SearchResult> {
        val params = filterParams(filter)
        val response = client.search(query = query, params = params)
        return searchParser.parse(response)
    }

    /** YT Music search filter params (opaque tokens YT publishes for each filter chip). */
    private fun filterParams(filter: String?): String = when (filter) {
        null, "songs" -> SONGS_PARAMS
        "videos" -> VIDEOS_PARAMS
        "albums" -> ALBUMS_PARAMS
        "playlists" -> PLAYLISTS_PARAMS
        "artists" -> ARTISTS_PARAMS
        else -> SONGS_PARAMS
    }

    private companion object {
        const val HOME_BROWSE_ID = "FEmusic_home"
        const val SONGS_PARAMS = "EgWKAQIIAWoMEA4QChADEAQQCRAF"
        const val VIDEOS_PARAMS = "EgWKAQIQAWoMEA4QChADEAQQCRAF"
        const val ALBUMS_PARAMS = "EgWKAQIYAWoMEA4QChADEAQQCRAF"
        const val PLAYLISTS_PARAMS = "EgWKAQIoAWoMEA4QChADEAQQCRAF"
        const val ARTISTS_PARAMS = "EgWKAQIgAWoMEA4QChADEAQQCRAF"
    }
}
