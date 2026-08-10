package com.dustvalve.next.android.data.local.datastore

import com.dustvalve.next.android.util.CookieEncryption
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Provider toggles, search-history, player chrome, auth cookies, and misc
 * prefs that are not appearance / download / local-music.
 */
interface SourcesSearchPlayerPrefsStore {
    val authCookies: Flow<String?>
    val bandcampEnabled: Flow<Boolean>
    val youtubeEnabled: Flow<Boolean>
    val soundcloudEnabled: Flow<Boolean>
    val soundcloudClientId: Flow<String?>
    val showInlineVolumeSlider: Flow<Boolean>
    val showVolumeButton: Flow<Boolean>
    val lastYoutubeVideoId: Flow<String?>
    val keepScreenOnInApp: Flow<Boolean>

    /**
     * Sub-mode of [keepScreenOnInApp]: when the parent is on AND this is on,
     * the screen only stays awake while music is actively playing. Defaults
     * to true so that flipping the parent on yields the lower-impact
     * "only-while-playing" behaviour out of the box.
     */
    val keepScreenOnWhilePlaying: Flow<Boolean>
    val searchHistoryEnabled: Flow<Boolean>
    val searchHistoryBandcamp: Flow<Boolean>
    val searchHistoryYoutube: Flow<Boolean>
    val searchHistorySoundcloud: Flow<Boolean>
    val searchHistoryLocal: Flow<Boolean>

    /**
     * Repurposed from "long-press cover for carousel": when on, the player
     * shows a debug overlay instead of the cover carousel. Off by default.
     * Surfaced as the "Show debug info" toggle in Settings -> Debug.
     *
     * The persisted key is still `album_cover_long_press_carousel`, which now
     * reads as the opposite of what the flag means. That mismatch is
     * deliberate: renaming the key would silently reset the toggle for every
     * existing install, and the Kotlin name is the one people read. Rename the
     * key only alongside a migration that carries the old value across.
     */
    val playerDebugOverlay: Flow<Boolean>
    val youtubeDefaultSource: Flow<String>

    // Gates the silent cold-start update check fired from
    // DustvalveNextApplication.onCreate. Defaults on: pre-alpha ships several
    // builds a day and we want users on the latest by default. The manual
    // "Search for updates" button in Settings -> About is never gated by this.
    val autoUpdateCheckEnabled: Flow<Boolean>

    /** Custom Bandcamp genres added by the user, stored as JSON. */
    val bandcampCustomGenres: Flow<List<String>>

    /**
     * Bluetooth stability profile storage: "off", "normal", or "extreme".
     * See app BluetoothStabilityMode for semantics.
     */
    val bluetoothStabilityMode: Flow<String>
    val bluetoothPcmBufferMs: Flow<Int>
    val bluetoothExoBufferBoost: Flow<Boolean>
    val bluetoothPauseDownloadsWhilePlaying: Flow<Boolean>
    val bluetoothDisableFloatOutput: Flow<Boolean>

    suspend fun setAuthCookies(cookiesJson: String?)
    suspend fun setBandcampEnabled(enabled: Boolean)
    suspend fun setYoutubeEnabled(enabled: Boolean)
    suspend fun setSoundcloudEnabled(enabled: Boolean)
    suspend fun setSoundcloudClientId(clientId: String)
    suspend fun clearSoundcloudClientId()
    suspend fun setShowInlineVolumeSlider(enabled: Boolean)
    suspend fun setShowVolumeButton(enabled: Boolean)
    suspend fun setKeepScreenOnInApp(enabled: Boolean)
    suspend fun setKeepScreenOnWhilePlaying(enabled: Boolean)
    suspend fun setSearchHistoryEnabled(enabled: Boolean)
    suspend fun setSearchHistoryBandcamp(enabled: Boolean)
    suspend fun setSearchHistoryYoutube(enabled: Boolean)
    suspend fun setSearchHistorySoundcloud(enabled: Boolean)
    suspend fun setSearchHistoryLocal(enabled: Boolean)
    suspend fun setPlayerDebugOverlay(enabled: Boolean)
    suspend fun setYoutubeDefaultSource(source: String)
    suspend fun setAutoUpdateCheckEnabled(enabled: Boolean)
    suspend fun setLastYoutubeVideoId(videoId: String?)
    suspend fun setBandcampCustomGenres(genres: List<String>)
    suspend fun setBluetoothStabilityMode(mode: String)
    suspend fun setBluetoothPcmBufferMs(ms: Int)
    suspend fun setBluetoothExoBufferBoost(enabled: Boolean)
    suspend fun setBluetoothPauseDownloadsWhilePlaying(enabled: Boolean)
    suspend fun setBluetoothDisableFloatOutput(enabled: Boolean)
}
