package com.dustvalve.next.android.data.repository

import com.dustvalve.next.android.domain.model.Album
import com.dustvalve.next.android.domain.model.Artist
import com.dustvalve.next.android.domain.model.MusicCollection
import com.dustvalve.next.android.domain.model.SearchResult
import com.dustvalve.next.android.domain.repository.MusicSource
import com.dustvalve.next.android.domain.repository.SoundCloudRepository
import com.dustvalve.next.android.domain.repository.SourceConcept
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [MusicSource] adapter for SoundCloud (anonymous api-v2).
 */
@Singleton
class SoundCloudMusicSource @Inject constructor(private val soundCloudRepository: SoundCloudRepository) : MusicSource {

    override val id: String = "soundcloud"

    override val capabilities: Set<SourceConcept> = setOf(
        SourceConcept.SEARCH,
        SourceConcept.ARTIST,
        SourceConcept.ARTIST_TRACKS,
        SourceConcept.ALBUM,
        SourceConcept.COLLECTION,
    )

    override suspend fun search(query: String, filter: String?): List<SearchResult> =
        soundCloudRepository.search(query = query, filter = filter)

    override suspend fun getArtist(url: String): Artist = soundCloudRepository.getArtist(url)

    override suspend fun getArtistTracks(url: String, continuation: Any?): MusicCollection =
        soundCloudRepository.getArtistTracks(url = url, continuation = continuation)

    override suspend fun getAlbum(url: String): Album = soundCloudRepository.getAlbum(url)

    override suspend fun getCollection(url: String, continuation: Any?): MusicCollection =
        soundCloudRepository.getCollection(url = url, continuation = continuation)
}
