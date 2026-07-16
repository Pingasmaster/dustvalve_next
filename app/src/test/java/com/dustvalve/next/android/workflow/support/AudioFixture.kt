package com.dustvalve.next.android.workflow.support

import java.io.File

/**
 * Materializes the checked-in audio fixtures (test resources) as real files
 * so ExoPlayer can play them through a file:// URI under Robolectric.
 */
object AudioFixture {

    /** 2 second 440 Hz mono PCM WAV - plays without any MediaCodec shadow. */
    fun toneWavFile(): File = materialize("fixtures/audio/tone_2s.wav", "tone_2s", ".wav")

    /** 2 second 440 Hz mono MP3 - needs a shadow decoder; used where realism matters. */
    fun toneMp3File(): File = materialize("fixtures/audio/tone_2s.mp3", "tone_2s", ".mp3")

    fun toneWavUri(): String = toneWavFile().toURI().toString()

    private fun materialize(resourcePath: String, prefix: String, suffix: String): File {
        val stream = checkNotNull(javaClass.classLoader?.getResourceAsStream(resourcePath)) {
            "Missing test resource: $resourcePath"
        }
        val file = File.createTempFile(prefix, suffix)
        file.deleteOnExit()
        stream.use { input -> file.outputStream().use { output -> input.copyTo(output) } }
        return file
    }
}
