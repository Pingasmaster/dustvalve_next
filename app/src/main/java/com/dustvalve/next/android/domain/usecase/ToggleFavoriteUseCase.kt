package com.dustvalve.next.android.domain.usecase

import com.dustvalve.next.android.domain.repository.AlbumRepository
import com.dustvalve.next.android.domain.repository.ArtistRepository
import com.dustvalve.next.android.domain.repository.LibraryRepository
import javax.inject.Inject

class ToggleFavoriteUseCase @Inject constructor(
    private val albumRepository: AlbumRepository,
    private val artistRepository: ArtistRepository,
    private val libraryRepository: LibraryRepository,
) {
    suspend fun toggleAlbumFavorite(albumId: String) {
        albumRepository.toggleFavorite(albumId)
    }

    suspend fun toggleTrackFavorite(trackId: String): Boolean {
        return libraryRepository.toggleTrackFavorite(trackId)
    }

    suspend fun toggleArtistFavorite(artistId: String) {
        artistRepository.toggleFavorite(artistId)
    }
}
