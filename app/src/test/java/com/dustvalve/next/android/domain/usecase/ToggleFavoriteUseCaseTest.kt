package com.dustvalve.next.android.domain.usecase

import com.dustvalve.next.android.domain.repository.AlbumRepository
import com.dustvalve.next.android.domain.repository.ArtistRepository
import com.dustvalve.next.android.domain.repository.LibraryRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class ToggleFavoriteUseCaseTest {

    private lateinit var albumRepo: AlbumRepository
    private lateinit var artistRepo: ArtistRepository
    private lateinit var libRepo: LibraryRepository
    private lateinit var useCase: ToggleFavoriteUseCase

    @Before fun setUp() {
        albumRepo = mockk(relaxed = true)
        artistRepo = mockk(relaxed = true)
        libRepo = mockk(relaxed = true)
        useCase = ToggleFavoriteUseCase(albumRepo, artistRepo, libRepo)
    }

    @Test fun `toggleAlbumFavorite delegates to album repository`() = runTest {
        useCase.toggleAlbumFavorite("album-1")
        coVerify { albumRepo.toggleFavorite("album-1") }
    }

    @Test fun `toggleArtistFavorite delegates to artist repository`() = runTest {
        useCase.toggleArtistFavorite("artist-1")
        coVerify { artistRepo.toggleFavorite("artist-1") }
    }

    @Test fun `toggleTrackFavorite returns repository result`() = runTest {
        coEvery { libRepo.toggleTrackFavorite("track-1") } returns true
        assertThat(useCase.toggleTrackFavorite("track-1")).isTrue()
        coEvery { libRepo.toggleTrackFavorite("track-2") } returns false
        assertThat(useCase.toggleTrackFavorite("track-2")).isFalse()
    }
}
