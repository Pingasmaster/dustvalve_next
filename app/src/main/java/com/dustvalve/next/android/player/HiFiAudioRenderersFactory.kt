package com.dustvalve.next.android.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.AudioTrackAudioOutputProvider
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.DefaultAudioTrackBufferSizeProvider

/**
 * Music-tuned renderers factory for high-fidelity / Bluetooth (LDAC) playback.
 *
 * Media3's default PCM [AudioTrack] cushion is ~500 ms. That is enough for
 * wired/speaker paths, but high-rate Bluetooth codecs (LDAC 96 kHz / 990 kbps)
 * spend more CPU in the HAL encode + possible resampling path; a half-second
 * cushion underruns as short periodic hiccups even when ExoPlayer's compressed
 * LoadControl buffer is full for minutes.
 *
 * Float output defaults on so 24-bit / high-res decoded PCM is not truncated to
 * 16-bit before it reaches AudioFlinger. [PlaybackAudioTuning] may turn float
 * off under Extreme Bluetooth stability when the user opts in.
 */
@OptIn(UnstableApi::class)
class HiFiAudioRenderersFactory(context: Context, private val audioTuning: PlaybackAudioTuning) : DefaultRenderersFactory(context) {

    init {
        setEnableAudioFloatOutput(audioTuning.isFloatOutputEnabled())
    }

    override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink {
        val bufferSizeProvider = DefaultAudioTrackBufferSizeProvider.Builder()
            .setTargetPcmBufferDurationUs(audioTuning.pcmBufferDurationUs())
            .build()
        val audioOutputProvider = AudioTrackAudioOutputProvider.Builder(context)
            .setAudioTrackBufferSizeProvider(bufferSizeProvider)
            .build()
        return DefaultAudioSink.Builder(context)
            .setEnableFloatOutput(enableFloatOutput && audioTuning.isFloatOutputEnabled())
            .setEnableAudioOutputPlaybackParameters(enableAudioTrackPlaybackParams)
            .setAudioOutputProvider(audioOutputProvider)
            .build()
    }

    companion object {
        /** 2 s of PCM at the output sample rate (Media3 default is 500 ms). */
        const val TARGET_PCM_BUFFER_DURATION_US = 2_000_000
    }
}
