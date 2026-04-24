package com.dustvalve.next.android.update

import com.dustvalve.next.android.R
import com.dustvalve.next.android.util.UiText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/** UI-facing shape of the update flow. Shared between the Settings row and the startup dialog. */
sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    /** Server said a newer build exists. Awaiting user confirmation. */
    data class Available(val versionName: String, val apkUrl: String) : UpdateUiState
    /** APK is streaming. [progress] is 0f..1f, or `null` when no Content-Length was sent. */
    data class Downloading(val versionName: String, val progress: Float?) : UpdateUiState
}

/**
 * Process-wide owner of the self-update state so the Settings screen and the
 * cold-start dialog share a single [UpdateUiState] flow. Without this the two
 * surfaces would each run their own check, have divergent progress bars, and
 * fight over the download coroutine.
 *
 * Cold start: [DustvalveNextApplication.onCreate] calls [checkSilently] once.
 * If a newer APK exists, state moves to [UpdateUiState.Available] and the
 * MainActivity dialog host surfaces a prompt. Manual re-checks from
 * Settings → About go through [checkManually] (emits an "up to date" / "check
 * failed" message on the [messages] flow that only the Settings screen
 * listens to — we don't want startup toasts).
 */
@Singleton
class AppUpdateController @Inject constructor(
    private val service: AppUpdateService,
) {
    /** Overridable in tests so a TestDispatcher can drive the internal scope. Set once, before first call. */
    internal var scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    /**
     * One-shot messages for the Settings row only (e.g. "no update" after a
     * manual check). Silent startup checks NEVER emit here; a "no update"
     * result from a silent check is just silent.
     */
    private val _messages = MutableSharedFlow<UiText>(replay = 0, extraBufferCapacity = 4)
    val messages: SharedFlow<UiText> = _messages.asSharedFlow()

    @Volatile
    private var silentCheckStarted = false
    private var downloadJob: Job? = null

    /**
     * Idempotent per process. Fires once from [com.dustvalve.next.android.DustvalveNextApplication.onCreate].
     * All errors are swallowed — a startup update check must never surface
     * a toast or block the UI.
     */
    fun checkSilently() {
        if (silentCheckStarted) return
        silentCheckStarted = true
        scope.launch {
            try {
                val available = service.checkForUpdate() ?: return@launch
                _state.update { current ->
                    // Respect an in-flight manual flow — the user is already
                    // looking at a dialog, we don't want to reset their state.
                    when (current) {
                        is UpdateUiState.Downloading, is UpdateUiState.Available -> current
                        else -> UpdateUiState.Available(
                            versionName = available.versionName,
                            apkUrl = available.apkDownloadUrl,
                        )
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                // Swallow — silent check.
            }
        }
    }

    /**
     * User-triggered re-check from Settings → About. Emits an [UiText] on
     * [messages] for the "no update" / "check failed" cases so Settings can
     * snackbar it.
     */
    fun checkManually() {
        if (_state.value is UpdateUiState.Downloading) return
        scope.launch {
            _state.value = UpdateUiState.Checking
            try {
                val available = service.checkForUpdate()
                if (available == null) {
                    _state.value = UpdateUiState.Idle
                    _messages.tryEmit(UiText.StringResource(R.string.settings_update_no_update))
                } else {
                    _state.value = UpdateUiState.Available(
                        versionName = available.versionName,
                        apkUrl = available.apkDownloadUrl,
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _state.value = UpdateUiState.Idle
                _messages.tryEmit(UiText.StringResource(R.string.settings_update_check_failed))
            }
        }
    }

    /** User confirmed the download; streams the APK + hands off to the installer. */
    fun confirmDownload() {
        val current = _state.value
        if (current !is UpdateUiState.Available) return
        downloadJob?.cancel()
        downloadJob = scope.launch {
            _state.value = UpdateUiState.Downloading(current.versionName, 0f)
            try {
                service.downloadApk(current.apkUrl).collect { p ->
                    val frac = if (p.totalBytes > 0L) p.fraction else null
                    _state.value = UpdateUiState.Downloading(current.versionName, frac)
                }
                try {
                    service.launchInstaller()
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    _messages.tryEmit(UiText.StringResource(R.string.settings_update_install_failed))
                }
                _state.value = UpdateUiState.Idle
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _state.value = UpdateUiState.Idle
                _messages.tryEmit(UiText.StringResource(R.string.settings_update_download_failed))
            }
        }
    }

    /** Moves state back to Idle. Does not cancel an in-flight download. */
    fun dismiss() {
        if (_state.value is UpdateUiState.Downloading) return
        _state.value = UpdateUiState.Idle
    }
}
