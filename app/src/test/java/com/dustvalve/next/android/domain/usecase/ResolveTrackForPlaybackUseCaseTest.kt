package com.dustvalve.next.android.domain.usecase

import com.dustvalve.next.android.domain.model.AudioFormat
import com.dustvalve.next.android.domain.model.StreamPolicy
import com.dustvalve.next.android.domain.model.Track
import com.dustvalve.next.android.domain.model.TrackSource
import com.dustvalve.next.android.domain.repository.BandcampStreamUrlResolver
import com.dustvalve.next.android.domain.repository.DownloadInfo
import com.dustvalve.next.android.domain.repository.DownloadRepository
import com.dustvalve.next.android.domain.repository.SoundCloudRepository
import com.dustvalve.next.android.domain.repository.SoundCloudResolvedStream
import com.dustvalve.next.android.domain.repository.YouTubeRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.io.IOException

class ResolveTrackForPlaybackUseCaseTest {

    private lateinit var downloadRepository: DownloadRepository
    private lateinit var youtubeRepository: YouTubeRepository
    private lateinit var soundCloudRepository: SoundCloudRepository
    private lateinit var bandcampStreamUrlResolver: BandcampStreamUrlResolver
    private lateinit var useCase: ResolveTrackForPlaybackUseCase

    @Before fun setUp() {
        downloadRepository = mockk()
        youtubeRepository = mockk()
        soundCloudRepository = mockk()
        bandcampStreamUrlResolver = mockk()
        useCase = ResolveTrackForPlaybackUseCase(
            downloadRepository,
            youtubeRepository,
            soundCloudRepository,
            bandcampStreamUrlResolver,
        )
    }

    @Test fun `local track is returned as-is with stream path`() = runTest {
        val track = sampleTrack(source = TrackSource.LOCAL, streamUrl = "content://local/1")
        coEvery { downloadRepository.getDownloadInfo(any()) } returns null

        val result = useCase(track)

        assertThat(result.track).isEqualTo(track)
        assertThat(result.sourcePath).isEqualTo("content://local/1")
        assertThat(result.playbackFormat).isNull()
        coVerify(exactly = 0) { youtubeRepository.getStreamUrl(any()) }
    }

    @Test fun `bandcamp prefers local download when quality is high enough`() = runTest {
        val track = sampleTrack(source = TrackSource.BANDCAMP, streamUrl = "https://cdn.example/stream.mp3")
        coEvery { downloadRepository.getDownloadInfo(track.id) } returns DownloadInfo(
            filePath = "/data/data/com.dustvalve.next.android/files/downloads/a1/t1.flac",
            format = AudioFormat.FLAC,
        )

        val result = useCase(track)

        assertThat(result.track.streamUrl).isEqualTo("file:///data/data/com.dustvalve.next.android/files/downloads/a1/t1.flac")
        assertThat(result.playbackFormat).isEqualTo(AudioFormat.FLAC)
        assertThat(result.sourcePath).isEqualTo("/data/data/com.dustvalve.next.android/files/downloads/a1/t1.flac")
        coVerify(exactly = 0) { bandcampStreamUrlResolver.resolveStreamUrl(any()) }
    }

    @Test fun `bandcamp with blank streamUrl re-resolves via Dustvalve`() = runTest {
        val track = sampleTrack(
            source = TrackSource.BANDCAMP,
            streamUrl = null,
            albumUrl = "https://artist.bandcamp.com/album/x",
        )
        coEvery { downloadRepository.getDownloadInfo(track.id) } returns null
        coEvery { bandcampStreamUrlResolver.resolveStreamUrl(track) } returns "https://cdn.example/fresh.mp3"

        val result = useCase(track)

        assertThat(result.track.streamUrl).isEqualTo("https://cdn.example/fresh.mp3")
        assertThat(result.recordedRemoteResolution).isTrue()
        assertThat(result.streamFailed).isFalse()
    }

    @Test fun `bandcamp blank stream that still fails reports streamFailed`() = runTest {
        val track = sampleTrack(source = TrackSource.BANDCAMP, streamUrl = "")
        coEvery { downloadRepository.getDownloadInfo(track.id) } returns null
        coEvery { bandcampStreamUrlResolver.resolveStreamUrl(track) } returns null

        val reported = useCase(track, reportFailure = true)
        assertThat(reported.streamFailed).isTrue()
        assertThat(reported.track.streamUrl).isNull()

        val silent = useCase(track, reportFailure = false)
        assertThat(silent.streamFailed).isFalse()
        assertThat(silent.track.streamUrl).isNull()
    }

    @Test fun `youtube resolves live stream and records remote resolution`() = runTest {
        val track = sampleTrack(
            id = "yt_abc123",
            source = TrackSource.YOUTUBE,
            streamUrl = "https://www.youtube.com/watch?v=abc123",
        )
        coEvery { downloadRepository.getDownloadInfo(track.id) } returns null
        coEvery { youtubeRepository.getStreamUrl(track.streamUrl!!) } returns "https://googlevideo/fresh"

        val result = useCase(track)

        assertThat(result.track.streamUrl).isEqualTo("https://googlevideo/fresh")
        assertThat(result.recordedRemoteResolution).isTrue()
        assertThat(result.streamFailed).isFalse()
    }

    @Test fun `youtube failure reports streamFailed only when reportFailure is true`() = runTest {
        val track = sampleTrack(id = "yt_abc123", source = TrackSource.YOUTUBE, streamUrl = null)
        coEvery { downloadRepository.getDownloadInfo(track.id) } returns null
        coEvery { youtubeRepository.getStreamUrl(any()) } throws IOException("boom")

        val reported = useCase(track, reportFailure = true)
        assertThat(reported.streamFailed).isTrue()
        assertThat(reported.track.streamUrl).isNull()

        val silent = useCase(track, reportFailure = false)
        assertThat(silent.streamFailed).isFalse()
        assertThat(silent.track.streamUrl).isNull()
    }

    @Test fun `youtube unusable id reports streamFailed when watch URL missing`() = runTest {
        val track = sampleTrack(id = "not_a_yt_id", source = TrackSource.YOUTUBE, streamUrl = null)
        coEvery { downloadRepository.getDownloadInfo(track.id) } returns null

        val reported = useCase(track, reportFailure = true)
        assertThat(reported.streamFailed).isTrue()
        assertThat(reported.track.streamUrl).isNull()
        coVerify(exactly = 0) { youtubeRepository.getStreamUrl(any()) }

        val silent = useCase(track, reportFailure = false)
        assertThat(silent.streamFailed).isFalse()
        assertThat(silent.track.streamUrl).isNull()
    }

    @Test fun `youtubeWatchUrl rebuilds from yt_ id when streamUrl is stale googlevideo`() {
        val track = sampleTrack(
            id = "yt_vid99",
            source = TrackSource.YOUTUBE,
            streamUrl = "https://googlevideo.com/expired",
        )
        assertThat(ResolveTrackForPlaybackUseCase.youtubeWatchUrl(track))
            .isEqualTo("https://www.youtube.com/watch?v=vid99")
    }

    @Test fun `soundcloud resolve stamps streamPolicy onto the queue track`() = runTest {
        val track = sampleTrack(id = "sc_1", source = TrackSource.SOUNDCLOUD, streamUrl = null)
        coEvery { downloadRepository.getDownloadInfo(track.id) } returns null
        coEvery { soundCloudRepository.resolvePlayableStream(track) } returns SoundCloudResolvedStream(
            url = "https://cf-media.sndcdn.com/x.mp3",
            streamPolicy = StreamPolicy.STREAM_ONLY,
        )

        val result = useCase(track)

        assertThat(result.track.streamUrl).isEqualTo("https://cf-media.sndcdn.com/x.mp3")
        assertThat(result.track.streamPolicy).isEqualTo(StreamPolicy.STREAM_ONLY)
        assertThat(result.recordedRemoteResolution).isTrue()
        coVerify(exactly = 0) { soundCloudRepository.getStreamUrl(any()) }
    }

    @Test fun `soundcloud Go-plus failure surfaces streamFailedMessage`() = runTest {
        val track = sampleTrack(id = "sc_2", source = TrackSource.SOUNDCLOUD, streamUrl = null)
        coEvery { downloadRepository.getDownloadInfo(track.id) } returns null
        coEvery { soundCloudRepository.resolvePlayableStream(track) } throws
            IOException("This SoundCloud track is DRM-protected or requires Go+ and cannot be played")

        val result = useCase(track, reportFailure = true)
        assertThat(result.streamFailed).isTrue()
        assertThat(result.streamFailedMessage).contains("Go+")
    }

    private fun sampleTrack(
        id: String = "t1",
        source: TrackSource = TrackSource.BANDCAMP,
        streamUrl: String? = null,
        albumUrl: String = "",
    ) = Track(
        id = id,
        albumId = "a1",
        title = "Title",
        artist = "Artist",
        trackNumber = 1,
        duration = 120f,
        streamUrl = streamUrl,
        artUrl = "",
        albumTitle = "Album",
        source = source,
        albumUrl = albumUrl,
    )
}
