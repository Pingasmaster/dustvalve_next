package com.dustvalve.next.android.data.remote.youtube.innertube

import com.dustvalve.next.android.domain.model.AudioFormat
import com.dustvalve.next.android.domain.model.TrackSource
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test

class YouTubePlayerParserTest {

    private val parser = YouTubePlayerParser()
    private val json = Json { isLenient = true; ignoreUnknownKeys = true }

    @Test fun `parsePlayerStreamInfo picks highest-bitrate audio from real ANDROID_VR fixture`() {
        val info = parser.parsePlayerStreamInfo(
            Fixtures.load("player_android_vr_jNQXAC9IVRw.json")
        )
        assertThat(info.streamUrl).startsWith("http")
        assertThat(info.bitrate).isGreaterThan(50_000)
        assertThat(info.mimeType).startsWith("audio/")
        // The fixture's best audio is 130171 bps AAC. Spec says: highest bitrate
        // wins, opus only on tie - so the AAC must be picked.
        assertThat(info.format).isEqualTo(AudioFormat.AAC)
    }

    @Test fun `parsePlayerStreamInfo IOS fixture also yields direct audio URL`() {
        val info = parser.parsePlayerStreamInfo(
            Fixtures.load("player_ios_jNQXAC9IVRw.json")
        )
        assertThat(info.streamUrl).startsWith("http")
        assertThat(info.bitrate).isGreaterThan(50_000)
    }

    @Test fun `parseTrack pulls title author duration and largest thumb`() {
        val track = parser.parseTrack(
            Fixtures.load("player_android_vr_jNQXAC9IVRw.json"),
            videoId = "jNQXAC9IVRw",
        )
        assertThat(track.id).isEqualTo("yt_jNQXAC9IVRw")
        assertThat(track.title).isEqualTo("Me at the zoo")
        assertThat(track.artist).isNotEmpty()
        assertThat(track.duration).isGreaterThan(0f)
        assertThat(track.streamUrl).isEqualTo("https://www.youtube.com/watch?v=jNQXAC9IVRw")
        assertThat(track.artUrl).startsWith("http")
        assertThat(track.source).isEqualTo(TrackSource.YOUTUBE)
    }

    @Test fun `parsePlayerStreamInfo prefers opus on bitrate tie`() {
        val tied = json.parseToJsonElement(
            """
            {"streamingData":{"adaptiveFormats":[
              {"mimeType":"audio/mp4; codecs=\"mp4a.40.2\"","bitrate":128000,"url":"https://aac.example/x"},
              {"mimeType":"audio/webm; codecs=\"opus\"","bitrate":128000,"url":"https://opus.example/x"}
            ]}}
            """.trimIndent()
        )
        val info = parser.parsePlayerStreamInfo(tied)
        assertThat(info.format).isEqualTo(AudioFormat.OPUS)
        assertThat(info.streamUrl).contains("opus.example")
    }

    @Test fun `parsePlayerStreamInfo highest bitrate beats codec preference`() {
        val mixed = json.parseToJsonElement(
            """
            {"streamingData":{"adaptiveFormats":[
              {"mimeType":"audio/webm; codecs=\"opus\"","bitrate":48000,"url":"https://opus.example/lo"},
              {"mimeType":"audio/mp4; codecs=\"mp4a.40.2\"","bitrate":256000,"url":"https://aac.example/hi"}
            ]}}
            """.trimIndent()
        )
        val info = parser.parsePlayerStreamInfo(mixed)
        assertThat(info.format).isEqualTo(AudioFormat.AAC)
        assertThat(info.streamUrl).contains("aac.example")
        assertThat(info.bitrate).isEqualTo(256_000)
    }

    @Test fun `parsePlayerStreamInfo throws when no streamingData`() {
        val empty = json.parseToJsonElement(
            """{"playabilityStatus":{"status":"LOGIN_REQUIRED"}}"""
        )
        val ex = runCatching { parser.parsePlayerStreamInfo(empty) }.exceptionOrNull()
        assertThat(ex).isInstanceOf(IllegalStateException::class.java)
        assertThat(ex!!.message).contains("no streamingData")
        assertThat(ex.message).contains("LOGIN_REQUIRED")
    }

    @Test fun `parsePlayerStreamInfo throws when no audio formats`() {
        val videoOnly = json.parseToJsonElement(
            """
            {"streamingData":{"adaptiveFormats":[
              {"mimeType":"video/mp4","bitrate":500000,"url":"https://x"}
            ]}, "playabilityStatus":{"status":"OK"}}
            """.trimIndent()
        )
        val ex = runCatching { parser.parsePlayerStreamInfo(videoOnly) }.exceptionOrNull()
        assertThat(ex).isInstanceOf(IllegalStateException::class.java)
        assertThat(ex!!.message).contains("no audio formats")
    }

    @Test fun `parsePlayerStreamInfo throws when best audio missing url`() {
        val noUrl = json.parseToJsonElement(
            """
            {"streamingData":{"adaptiveFormats":[
              {"mimeType":"audio/webm; codecs=\"opus\"","bitrate":128000,"signatureCipher":"x"}
            ]}}
            """.trimIndent()
        )
        val ex = runCatching { parser.parsePlayerStreamInfo(noUrl) }.exceptionOrNull()
        assertThat(ex).isInstanceOf(IllegalStateException::class.java)
        assertThat(ex!!.message).contains("missing url")
    }

    @Test fun `parseTrack throws when videoDetails absent`() {
        val empty = json.parseToJsonElement("""{}""")
        val ex = runCatching { parser.parseTrack(empty, "vid") }.exceptionOrNull()
        assertThat(ex).isInstanceOf(IllegalStateException::class.java)
        assertThat(ex!!.message).contains("videoDetails")
    }
}
