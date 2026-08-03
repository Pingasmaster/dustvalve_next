package com.dustvalve.next.android.domain.usecase

import com.dustvalve.next.android.domain.model.MusicCollection
import com.dustvalve.next.android.domain.model.Track
import com.dustvalve.next.android.domain.model.TrackSource
import com.dustvalve.next.android.domain.repository.MusicSource
import com.dustvalve.next.android.domain.repository.SourceConcept
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ExpandSourceTracksUseCaseTest {

    private val useCase = ExpandSourceTracksUseCase()

    @Test
    fun `expandCollection drains pages up to maxTracks`() = runTest {
        val source = mockk<MusicSource>()
        every { source.capabilities } returns setOf(SourceConcept.COLLECTION)
        coEvery { source.getCollection("u", "c1") } returns page(
            tracks = (21..40).map { track("t$it") },
            continuation = "c2",
            hasMore = true,
        )
        coEvery { source.getCollection("u", "c2") } returns page(
            tracks = (41..60).map { track("t$it") },
            continuation = null,
            hasMore = false,
        )

        val result = useCase.expandCollection(
            source = source,
            url = "u",
            seedTracks = (1..20).map { track("t$it") },
            seedContinuation = "c1",
            seedHasMore = true,
            maxTracks = 55,
        )

        assertThat(result).hasSize(55)
        assertThat(result.first().id).isEqualTo("t1")
        assertThat(result.last().id).isEqualTo("t55")
    }

    @Test
    fun `expandArtistTracks dedupes and respects cap`() = runTest {
        val source = mockk<MusicSource>()
        every { source.capabilities } returns setOf(SourceConcept.ARTIST_TRACKS)
        coEvery { source.getArtistTracks("a", "n1") } returns page(
            tracks = listOf(track("t2"), track("t3"), track("t4")),
            continuation = null,
            hasMore = false,
        )

        val result = useCase.expandArtistTracks(
            source = source,
            url = "a",
            seedTracks = listOf(track("t1"), track("t2")),
            seedContinuation = "n1",
            seedHasMore = true,
            maxTracks = 3,
        )

        assertThat(result.map { it.id }).containsExactly("t1", "t2", "t3").inOrder()
    }

    @Test
    fun `expandCollection without capability returns seed only`() = runTest {
        val source = mockk<MusicSource>()
        every { source.capabilities } returns emptySet()
        val seed = listOf(track("a"), track("b"))
        val result = useCase.expandCollection(
            source = source,
            url = "u",
            seedTracks = seed,
            seedHasMore = true,
        )
        assertThat(result).isEqualTo(seed)
    }

    private fun page(
        tracks: List<Track>,
        continuation: Any?,
        hasMore: Boolean,
    ) = MusicCollection(
        id = "id",
        url = "u",
        name = "n",
        owner = "",
        coverUrl = null,
        tracks = tracks,
        continuation = continuation,
        hasMore = hasMore,
    )

    private fun track(id: String) = Track(
        id = id,
        albumId = "",
        title = id,
        artist = "",
        trackNumber = 0,
        duration = 0f,
        streamUrl = null,
        artUrl = "",
        albumTitle = "",
        source = TrackSource.YOUTUBE,
    )
}
