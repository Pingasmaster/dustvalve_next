package com.dustvalve.next.android.data.repository

import com.dustvalve.next.android.domain.model.Album
import com.dustvalve.next.android.domain.model.Artist
import com.dustvalve.next.android.domain.model.MusicCollection
import com.dustvalve.next.android.domain.model.MusicProvider
import com.dustvalve.next.android.domain.model.SearchResult
import com.dustvalve.next.android.domain.repository.MusicSource
import com.dustvalve.next.android.domain.repository.SourceConcept
import com.dustvalve.next.android.domain.repository.UnsupportedSourceOperation
import com.dustvalve.next.android.domain.repository.YouTubeMusicRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [MusicSource] adapter for YouTube Music. Shares [MusicProvider.YOUTUBE]
 * with plain YouTube — the two are distinguished by [id] ("youtube_music"
 * vs "youtube") and by routing in the UI layer.
 *
 * Currently only SEARCH is wired up. The underlying
 * [YouTubeMusicRepository] does not yet expose dedicated browse endpoints
 * for artist / album / playlist detail; those calls throw
 * [UnsupportedSourceOperation] and the unified-UI phases will add the
 * missing repository surface before routing YT Music detail screens
 * through this source.
 */
@Singleton
class YouTubeMusicSource @Inject constructor(
    private val youTubeMusicRepository: YouTubeMusicRepository,
) : MusicSource {

    override val provider: MusicProvider = MusicProvider.YOUTUBE
    override val id: String = "youtube_music"
    override val capabilities: Set<SourceConcept> = setOf(
        SourceConcept.SEARCH,
    )

    override suspend fun search(query: String, filter: String?): List<SearchResult> =
        youTubeMusicRepository.search(query = query, filter = filter)

    override suspend fun getArtist(url: String): Artist =
        throw UnsupportedSourceOperation(id, SourceConcept.ARTIST)

    override suspend fun getArtistTracks(
        url: String,
        continuation: Any?,
    ): MusicCollection =
        throw UnsupportedSourceOperation(id, SourceConcept.ARTIST_TRACKS)

    override suspend fun getAlbum(url: String): Album =
        throw UnsupportedSourceOperation(id, SourceConcept.ALBUM)

    override suspend fun getCollection(
        url: String,
        continuation: Any?,
    ): MusicCollection =
        throw UnsupportedSourceOperation(id, SourceConcept.COLLECTION)
}
