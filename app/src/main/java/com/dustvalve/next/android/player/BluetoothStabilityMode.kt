package com.dustvalve.next.android.player

/**
 * User-facing Bluetooth playback stability profile.
 *
 * [NORMAL] only tunes buffers / download contention (no quality loss).
 * [EXTREME] unlocks optional quality tradeoffs (float off, etc.), which stay
 * off until the user enables them with an explicit warning.
 */
enum class BluetoothStabilityMode {
    OFF,
    NORMAL,
    EXTREME,
    ;

    val isEnabled: Boolean get() = this != OFF

    val isExtreme: Boolean get() = this == EXTREME

    fun toStorage(): String = when (this) {
        OFF -> STORAGE_OFF
        NORMAL -> STORAGE_NORMAL
        EXTREME -> STORAGE_EXTREME
    }

    companion object {
        const val STORAGE_OFF = "off"
        const val STORAGE_NORMAL = "normal"
        const val STORAGE_EXTREME = "extreme"

        /** Default PCM cushion while a stability mode is on (ms). */
        const val DEFAULT_ACTIVE_PCM_BUFFER_MS = 3_000

        /** App default when mode is off (matches historical HiFi factory). */
        const val DEFAULT_PCM_BUFFER_MS = 2_000

        val PCM_BUFFER_STEPS_MS = listOf(1_000, 2_000, 3_000, 4_000)

        fun fromStorage(raw: String?): BluetoothStabilityMode = when (raw) {
            STORAGE_NORMAL -> NORMAL
            STORAGE_EXTREME -> EXTREME
            else -> OFF
        }
    }
}
