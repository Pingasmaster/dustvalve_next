package com.dustvalve.next.android.player

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.LoadControl
import com.dustvalve.next.android.data.local.datastore.SettingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Live effective audio-path knobs for Bluetooth stability mode.
 *
 * Pause-downloads is honored immediately by the player coordinators. PCM
 * cushion, float output, and ExoPlayer LoadControl sizes are sampled when
 * ExoPlayer is built (process start); changing them mid-session takes effect
 * after the app is relaunched.
 *
 * Defaults match "mode off" so ExoPlayer construction during Hilt startup does
 * not block on DataStore. Prefs arrive on the collect below.
 */
@OptIn(UnstableApi::class)
@Suppress("RawDispatchersUse")
@Singleton
class PlaybackAudioTuning @Inject constructor(settingsDataStore: SettingsDataStore) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val pcmBufferUs = AtomicLong(HiFiAudioRenderersFactory.TARGET_PCM_BUFFER_DURATION_US.toLong())
    private val floatOutputEnabled = AtomicBoolean(true)
    private val pauseDownloadsWhilePlaying = AtomicBoolean(false)
    private val exoBufferBoost = AtomicBoolean(false)

    init {
        scope.launch {
            combine(
                settingsDataStore.bluetoothStabilityMode,
                settingsDataStore.bluetoothPcmBufferMs,
                settingsDataStore.bluetoothExoBufferBoost,
                settingsDataStore.bluetoothPauseDownloadsWhilePlaying,
                settingsDataStore.bluetoothDisableFloatOutput,
            ) { modeRaw, pcmMs, boost, pauseDownloads, disableFloat ->
                EffectiveTuning(
                    mode = BluetoothStabilityMode.fromStorage(modeRaw),
                    pcmBufferMs = pcmMs,
                    exoBufferBoost = boost,
                    pauseDownloadsWhilePlaying = pauseDownloads,
                    disableFloatOutput = disableFloat,
                )
            }
                .distinctUntilChanged()
                .collect { apply(it) }
        }
    }

    fun pcmBufferDurationUs(): Int = pcmBufferUs.get().toInt()

    fun isFloatOutputEnabled(): Boolean = floatOutputEnabled.get()

    fun shouldPauseDownloadsWhilePlaying(): Boolean = pauseDownloadsWhilePlaying.get()

    fun buildLoadControl(): LoadControl {
        val boost = exoBufferBoost.get()
        val min = if (boost) BOOST_MIN_BUFFER_MS else MIN_BUFFER_MS
        val max = if (boost) BOOST_MAX_BUFFER_MS else MAX_BUFFER_MS
        val playback = if (boost) BOOST_PLAYBACK_BUFFER_MS else PLAYBACK_BUFFER_MS
        val rebuffer = if (boost) BOOST_REBUFFER_MS else REBUFFER_MS
        return DefaultLoadControl.Builder()
            .setBufferDurationsMs(min, max, playback, rebuffer)
            .setPrioritizeTimeOverSizeThresholds(true)
            .setBackBuffer(0, false)
            .build()
    }

    private fun apply(tuning: EffectiveTuning) {
        if (!tuning.mode.isEnabled) {
            pcmBufferUs.set(HiFiAudioRenderersFactory.TARGET_PCM_BUFFER_DURATION_US.toLong())
            floatOutputEnabled.set(true)
            pauseDownloadsWhilePlaying.set(false)
            exoBufferBoost.set(false)
            return
        }
        val pcmMs = tuning.pcmBufferMs.coerceIn(
            BluetoothStabilityMode.PCM_BUFFER_STEPS_MS.first(),
            BluetoothStabilityMode.PCM_BUFFER_STEPS_MS.last(),
        )
        pcmBufferUs.set(pcmMs * 1_000L)
        floatOutputEnabled.set(!(tuning.mode.isExtreme && tuning.disableFloatOutput))
        pauseDownloadsWhilePlaying.set(tuning.pauseDownloadsWhilePlaying)
        exoBufferBoost.set(tuning.exoBufferBoost)
    }

    private data class EffectiveTuning(
        val mode: BluetoothStabilityMode,
        val pcmBufferMs: Int,
        val exoBufferBoost: Boolean,
        val pauseDownloadsWhilePlaying: Boolean,
        val disableFloatOutput: Boolean,
    )

    companion object {
        private const val MIN_BUFFER_MS = 60_000
        private const val MAX_BUFFER_MS = 120_000
        private const val PLAYBACK_BUFFER_MS = 2_500
        private const val REBUFFER_MS = 5_000

        private const val BOOST_MIN_BUFFER_MS = 90_000
        private const val BOOST_MAX_BUFFER_MS = 180_000
        private const val BOOST_PLAYBACK_BUFFER_MS = 3_000
        private const val BOOST_REBUFFER_MS = 8_000
    }
}
