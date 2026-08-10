package com.dustvalve.next.android.ui.screens.player

import com.dustvalve.next.android.data.remote.DustvalveStreamResolver
import com.dustvalve.next.android.domain.model.Track
import com.dustvalve.next.android.domain.repository.BandcampStreamUrlResolver
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-layer [BandcampStreamUrlResolver] backed by [DustvalveStreamResolver].
 * Kept separate from [PlaybackStreamResolver] to avoid a Hilt cycle with
 * [com.dustvalve.next.android.domain.usecase.ResolveTrackForPlaybackUseCase].
 */
@Singleton
class DustvalveBandcampStreamUrlResolver @Inject constructor(private val dustvalveStreamResolver: DustvalveStreamResolver) :
    BandcampStreamUrlResolver {
    override suspend fun resolveStreamUrl(track: Track): String? {
        val pageUrl = track.albumUrl.takeIf { it.isNotBlank() } ?: track.bandcampTrackUrl
        if (pageUrl.isNullOrBlank()) return null
        return dustvalveStreamResolver.resolveStreamUrl(track.copy(streamUrl = null), pageUrl)
    }
}
