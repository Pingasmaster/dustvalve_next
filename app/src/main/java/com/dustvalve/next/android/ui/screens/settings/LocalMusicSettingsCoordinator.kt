package com.dustvalve.next.android.ui.screens.settings

import com.dustvalve.next.android.R
import com.dustvalve.next.android.data.local.datastore.SettingsDataStore
import com.dustvalve.next.android.domain.repository.LocalMusicRepository
import com.dustvalve.next.android.util.UiText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/**
 * Local-source enable / MediaStore-vs-SAF / folder add-remove / rescan.
 * Extracted from [SettingsViewModel] to keep scan-job bookkeeping out of the
 * preference-setter surface.
 */
internal class LocalMusicSettingsCoordinator(
    private val scope: CoroutineScope,
    private val uiState: MutableStateFlow<SettingsUiState>,
    private val settingsDataStore: SettingsDataStore,
    private val localMusicRepository: LocalMusicRepository,
    private val hasAudioPermission: () -> Boolean,
) {
    private var scanJob: Job? = null

    fun setEnabled(enabled: Boolean) {
        if (!enabled) {
            // Cancel any in-flight scan before wiping the local library so a
            // late scan() completion cannot re-insert tracks / schedule sync.
            scanJob?.cancel()
            scanJob = null
        }
        scope.launch {
            try {
                settingsDataStore.setLocalMusicEnabled(enabled)
                if (!enabled) {
                    localMusicRepository.cancelSyncWork()
                    localMusicRepository.clearAll()
                    settingsDataStore.setLocalMusicUseMediaStore(true)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: IOException) {
                // Preference / clear failure is silent
            } catch (_: SecurityException) {
                // Preference / clear failure is silent
            } catch (_: IllegalStateException) {
                // Preference / clear failure is silent
            }
        }
    }

    fun setUseMediaStore(enabled: Boolean) {
        scanJob?.cancel()
        scanJob = scope.launch {
            try {
                // Clear all existing local tracks before switching modes
                localMusicRepository.clearAll()
                settingsDataStore.setLocalMusicUseMediaStore(enabled)
                if (enabled) {
                    if (!hasAudioPermission()) {
                        uiState.update {
                            it.copy(
                                isScanning = false,
                                scanMessage = UiText.StringResource(R.string.snackbar_scan_needs_permission),
                            )
                        }
                        return@launch
                    }
                    uiState.update { it.copy(isScanning = true) }
                    val result = localMusicRepository.scan()
                    uiState.update {
                        it.copy(
                            isScanning = false,
                            scanMessage = UiText.PluralsResource(R.plurals.scan_found, result.total),
                        )
                    }
                    if (settingsDataStore.getLocalMusicEnabledSync()) {
                        localMusicRepository.scheduleSyncWork()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                reportScanFailure(e)
            } catch (e: SecurityException) {
                reportScanFailure(e)
            } catch (e: IllegalStateException) {
                reportScanFailure(e)
            }
        }
    }

    fun addFolder(uri: String) {
        scanJob?.cancel()
        scanJob = scope.launch {
            try {
                localMusicRepository.addFolder(uri)
                uiState.update { it.copy(isScanning = true) }
                val result = localMusicRepository.scan()
                uiState.update {
                    it.copy(
                        isScanning = false,
                        scanMessage = UiText.PluralsResource(R.plurals.scan_found, result.total),
                    )
                }
                if (settingsDataStore.getLocalMusicEnabledSync()) {
                    localMusicRepository.scheduleSyncWork()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                reportScanFailure(e)
            } catch (e: SecurityException) {
                reportScanFailure(e)
            } catch (e: IllegalStateException) {
                reportScanFailure(e)
            }
        }
    }

    fun removeFolder(uri: String) {
        scope.launch {
            try {
                localMusicRepository.removeFolder(uri)
                val uris = uiState.value.localMusicFolderUris - uri
                if (uris.isEmpty()) {
                    localMusicRepository.cancelSyncWork()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: IOException) {
                // Folder remove failure is silent
            } catch (_: SecurityException) {
                // Folder remove failure is silent
            } catch (_: IllegalStateException) {
                // Folder remove failure is silent
            }
        }
    }

    fun rescan() {
        scanJob?.cancel()
        scanJob = scope.launch {
            try {
                if (settingsDataStore.getLocalMusicUseMediaStoreSync() && !hasAudioPermission()) {
                    uiState.update {
                        it.copy(
                            isScanning = false,
                            scanMessage = UiText.StringResource(R.string.snackbar_scan_needs_permission),
                        )
                    }
                    return@launch
                }
                uiState.update { it.copy(isScanning = true) }
                val result = localMusicRepository.scan()
                uiState.update {
                    it.copy(
                        isScanning = false,
                        scanMessage = UiText.PluralsResource(
                            R.plurals.scan_found_detailed,
                            result.total,
                            listOf(result.total, result.added, result.removed),
                        ),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                reportScanFailure(e)
            } catch (e: SecurityException) {
                reportScanFailure(e)
            } catch (e: IllegalStateException) {
                reportScanFailure(e)
            }
        }
    }

    /**
     * Cold-start / folder-list: upgrade saved trees to READ|WRITE when possible.
     * Surfaces a snackbar when any folder still lacks write (user must re-pick).
     */
    fun ensurePersistableWriteGrants() {
        scope.launch {
            try {
                if (settingsDataStore.getLocalMusicUseMediaStoreSync()) return@launch
                if (settingsDataStore.getLocalMusicFolderUrisSync().isEmpty()) return@launch
                val missing = localMusicRepository.ensurePersistableWriteGrants()
                if (missing.isNotEmpty()) {
                    uiState.update {
                        it.copy(
                            scanMessage = UiText.StringResource(R.string.snackbar_folder_needs_repick),
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: IOException) {
                // Silent: next delete/scan will surface permission issues.
            } catch (_: SecurityException) {
                // Silent
            } catch (_: IllegalStateException) {
                // Silent
            }
        }
    }

    fun clearScanMessage() {
        uiState.update { it.copy(scanMessage = null) }
    }

    private fun reportScanFailure(e: Exception) {
        uiState.update {
            it.copy(
                isScanning = false,
                scanMessage =
                e.message?.let { msg -> UiText.StringResource(R.string.snackbar_scan_failed, listOf(msg)) }
                    ?: UiText.StringResource(
                        R.string.snackbar_scan_failed,
                        listOf(UiText.StringResource(R.string.error_unknown)),
                    ),
            )
        }
    }
}
