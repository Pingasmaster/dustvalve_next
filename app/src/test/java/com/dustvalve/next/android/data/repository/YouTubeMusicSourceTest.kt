package com.dustvalve.next.android.data.repository

import com.dustvalve.next.android.domain.model.MusicProvider
import com.dustvalve.next.android.domain.model.SearchResult
import com.dustvalve.next.android.domain.model.SearchResultType
import com.dustvalve.next.android.domain.repository.SourceConcept
import com.dustvalve.next.android.domain.repository.UnsupportedSourceOperation
import com.dustvalve.next.android.domain.repository.YouTubeMusicRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class YouTubeMusicSourceTest {

    private val ytMusicRepo = mockk<YouTubeMusicRepository>()
    private val source = YouTubeMusicSource(ytMusicRepo)

    @Test fun `provider YOUTUBE id 'youtube_music' — distinct from plain youtube`() {
        assertThat(source.provider).isEqualTo(MusicProvider.YOUTUBE)
        assertThat(source.id).isEqualTo("youtube_music")
    }

    @Test fun `capabilities list only SEARCH (browse endpoints not yet wired)`() {
        assertThat(source.capabilities).containsExactly(SourceConcept.SEARCH)
    }

    @Test fun `search delegates verbatim to YouTubeMusicRepository`() = runTest {
        val expected = listOf(
            SearchResult(
                type = SearchResultType.YOUTUBE_TRACK, name = "Song",
                url = "https://music.youtube.com/watch?v=x",
                imageUrl = null, artist = "A", album = null,
                genre = null, releaseDate = null,
            ),
        )
        coEvery { ytMusicRepo.search(query = "q", filter = null) } returns expected

        assertThat(source.search("q")).isEqualTo(expected)
    }

    @Test fun `search passes filter through`() = runTest {
        coEvery { ytMusicRepo.search(query = any(), filter = any()) } returns emptyList()

        source.search("q", filter = "songs")
        source.search("q", filter = "albums")

        coVerify { ytMusicRepo.search(query = "q", filter = "songs") }
        coVerify { ytMusicRepo.search(query = "q", filter = "albums") }
    }

    @Test fun `getArtist throws UnsupportedSourceOperation with id marker`() = runTest {
        val ex = runCatching { source.getArtist("https://x") }.exceptionOrNull()
        assertThat(ex).isInstanceOf(UnsupportedSourceOperation::class.java)
        assertThat(ex!!.message).contains("youtube_music")
    }

    @Test fun `getAlbum throws`() = runTest {
        val ex = runCatching { source.getAlbum("https://x") }.exceptionOrNull()
        assertThat(ex).isInstanceOf(UnsupportedSourceOperation::class.java)
    }

    @Test fun `getCollection throws`() = runTest {
        val ex = runCatching { source.getCollection("https://x") }.exceptionOrNull()
        assertThat(ex).isInstanceOf(UnsupportedSourceOperation::class.java)
    }

    @Test fun `getArtistTracks throws`() = runTest {
        val ex = runCatching { source.getArtistTracks("https://x") }.exceptionOrNull()
        assertThat(ex).isInstanceOf(UnsupportedSourceOperation::class.java)
    }
}
