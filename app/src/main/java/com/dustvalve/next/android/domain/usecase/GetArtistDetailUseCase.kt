package com.dustvalve.next.android.domain.usecase

import com.dustvalve.next.android.domain.model.Artist
import com.dustvalve.next.android.domain.repository.ArtistRepository
import javax.inject.Inject

class GetArtistDetailUseCase @Inject constructor(
    private val artistRepository: ArtistRepository,
) {
    suspend operator fun invoke(url: String): Artist {
        return artistRepository.getArtistDetail(url)
    }
}
