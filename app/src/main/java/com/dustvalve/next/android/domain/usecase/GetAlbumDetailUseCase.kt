package com.dustvalve.next.android.domain.usecase

import com.dustvalve.next.android.domain.model.Album
import com.dustvalve.next.android.domain.repository.AlbumRepository
import javax.inject.Inject

class GetAlbumDetailUseCase @Inject constructor(
    private val albumRepository: AlbumRepository,
) {
    suspend operator fun invoke(url: String): Album {
        return albumRepository.getAlbumDetail(url)
    }
}
