package com.dustvalve.next.android.data.repository

import com.dustvalve.next.android.domain.model.Album
import com.dustvalve.next.android.domain.model.Artist
import com.dustvalve.next.android.domain.model.MusicCollection
import com.dustvalve.next.android.domain.model.MusicProvider
import com.dustvalve.next.android.domain.model.SearchResult
import com.dustvalve.next.android.domain.model.SearchResultType
import com.dustvalve.next.android.domain.repository.AlbumRepository
import com.dustvalve.next.android.domain.repository.ArtistRepository
import com.dustvalve.next.android.domain.repository.MusicSource
import com.dustvalve.next.android.domain.repository.SearchRepository
import com.dustvalve.next.android.domain.repository.SourceConcept
import com.dustvalve.next.android.domain.repository.UnsupportedSourceOperation
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [MusicSource] adapter for Bandcamp. Bandcamp supports search / artist /
 * album but has no public playlist concept.
 */
@Singleton
class BandcampMusicSource @Inject constructor(
    private val searchRepository: SearchRepository,
    private val artistRepository: ArtistRepository,
    private val albumRepository: AlbumRepository,
) : MusicSource {

    override val provider: MusicProvider = MusicProvider.BANDCAMP
    override val id: String = "bandcamp"
    override val capabilities: Set<SourceConcept> = setOf(
        SourceConcept.SEARCH,
        SourceConcept.ARTIST,
        SourceConcept.ALBUM,
    )

    override suspend fun search(query: String, filter: String?): List<SearchResult> {
        val type = when (filter) {
            null -> null
            "artists" -> SearchResultType.ARTIST
            "albums" -> SearchResultType.ALBUM
            "tracks" -> SearchResultType.TRACK
            else -> null
        }
        return searchRepository.search(query = query, type = type)
    }

    override suspend fun getArtist(url: String): Artist =
        artistRepository.getArtistDetail(url)

    override suspend fun getArtistTracks(
        url: String,
        continuation: Any?,
    ): MusicCollection =
        throw UnsupportedSourceOperation(id, SourceConcept.ARTIST_TRACKS)

    override suspend fun getAlbum(url: String): Album =
        albumRepository.getAlbumDetail(url)

    override suspend fun getCollection(
        url: String,
        continuation: Any?,
    ): MusicCollection =
        throw UnsupportedSourceOperation(id, SourceConcept.COLLECTION)
}
