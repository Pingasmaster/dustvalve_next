package com.dustvalve.next.android.domain.repository

import com.dustvalve.next.android.domain.model.Track

/**
 * Fetches a live Bandcamp mp3-128 URL for a track whose scrape-time
 * [Track.streamUrl] is blank or known-stale. Implemented in the app layer via
 * [com.dustvalve.next.android.data.remote.DustvalveStreamResolver].
 */
fun interface BandcampStreamUrlResolver {
    suspend fun resolveStreamUrl(track: Track): String?
}
