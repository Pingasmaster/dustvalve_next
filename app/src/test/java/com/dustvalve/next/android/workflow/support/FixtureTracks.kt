package com.dustvalve.next.android.workflow.support

import com.dustvalve.next.android.domain.model.Track
import com.dustvalve.next.android.domain.model.TrackSource

object FixtureTracks {

    fun localTrack(id: String = "local_ms_1", streamUrl: String? = AudioFixture.toneWavUri()) = Track(
        id = id,
        albumId = "local_album",
        title = "Local Tone $id",
        artist = "Test Artist",
        trackNumber = 1,
        duration = 2f,
        streamUrl = streamUrl,
        artUrl = "",
        albumTitle = "Test Album",
        source = TrackSource.LOCAL,
    )

    fun bandcampTrack(id: String = "12345_1", streamUrl: String? = AudioFixture.toneWavUri()) = Track(
        id = id,
        albumId = "12345",
        title = "Bandcamp Tone",
        artist = "BC Artist",
        trackNumber = 1,
        duration = 2f,
        streamUrl = streamUrl,
        artUrl = "",
        albumTitle = "BC Album",
        source = TrackSource.BANDCAMP,
    )

    fun youtubeTrack(id: String = "yt_dQw4w9WgXcQ") = Track(
        id = id,
        albumId = "",
        title = "YT Tone",
        artist = "YT Artist",
        trackNumber = 1,
        duration = 2f,
        streamUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
        artUrl = "",
        albumTitle = "",
        source = TrackSource.YOUTUBE,
    )
}
