package com.dustvalve.next.android.ui.screens.settings

import com.dustvalve.next.android.R
import com.dustvalve.next.android.data.storage.folder.FolderMirror
import com.dustvalve.next.android.data.storage.folder.StorageMigrator
import com.dustvalve.next.android.util.UiText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/**
 * Owns dedicated-folder enable/disable/include-cache migrations so
 * [SettingsViewModel] does not carry the long try/finally blocks inline.
 */
internal class DedicatedFolderSettingsCoordinator(
    private val scope: CoroutineScope,
    private val uiState: MutableStateFlow<SettingsUiState>,
    private val storageMigrator: StorageMigrator,
    private val folderMirror: FolderMirror,
) {
    fun enable(treeUri: String) {
        scope.launch {
            try {
                beginMigration()
                val includeImages = uiState.value.dedicatedFolderIncludeImageCache
                val includeMetadata = uiState.value.dedicatedFolderIncludeMetadataCache
                // Suppression is held for the migration's actual duration
                // (try/finally inside suppressed) - a slow SAF provider used
                // to outrun the old fixed 60s window and let the mirror
                // overwrite good snapshots mid-copy.
                folderMirror.suppressed {
                    storageMigrator.migrateToFolder(
                        treeUriStr = treeUri,
                        includeImages = includeImages,
                        includeMetadata = includeMetadata,
                    ) { p ->
                        uiState.update {
                            it.copy(
                                folderMigrationProgress = p.fraction,
                                folderMigrationMessage = UiText.DynamicString(p.label),
                            )
                        }
                    }
                }
                finishMigrationSuccess(R.string.settings_dedicated_folder_migration_success)
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                finishMigrationFailure(e)
            } catch (e: SecurityException) {
                finishMigrationFailure(e)
            } catch (e: IllegalStateException) {
                finishMigrationFailure(e)
            } catch (e: IllegalArgumentException) {
                finishMigrationFailure(e)
            }
        }
    }

    fun disable() {
        scope.launch {
            try {
                beginMigration()
                // Hold suppression for the migration's actual duration, not
                // a fixed 60s window (see enable).
                folderMirror.suppressed {
                    storageMigrator.migrateFromFolder { p ->
                        uiState.update {
                            it.copy(
                                folderMigrationProgress = p.fraction,
                                folderMigrationMessage = UiText.DynamicString(p.label),
                            )
                        }
                    }
                }
                finishMigrationSuccess(R.string.settings_dedicated_folder_migration_reverted)
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                finishMigrationFailure(e)
            } catch (e: SecurityException) {
                finishMigrationFailure(e)
            } catch (e: IllegalStateException) {
                finishMigrationFailure(e)
            } catch (e: IllegalArgumentException) {
                finishMigrationFailure(e)
            }
        }
    }

    fun setIncludeImageCache(enabled: Boolean) {
        scope.launch {
            try {
                uiState.update { it.copy(folderMigrationInProgress = true, folderMigrationProgress = 0.1f) }
                storageMigrator.setIncludeImageCache(enabled)
                uiState.update { it.copy(folderMigrationInProgress = false, folderMigrationProgress = 1f) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: IOException) {
                uiState.update { it.copy(folderMigrationInProgress = false) }
            } catch (_: SecurityException) {
                uiState.update { it.copy(folderMigrationInProgress = false) }
            } catch (_: IllegalStateException) {
                uiState.update { it.copy(folderMigrationInProgress = false) }
            }
        }
    }

    fun setIncludeMetadataCache(enabled: Boolean) {
        scope.launch {
            try {
                uiState.update { it.copy(folderMigrationInProgress = true, folderMigrationProgress = 0.1f) }
                storageMigrator.setIncludeMetadataCache(enabled)
                uiState.update { it.copy(folderMigrationInProgress = false, folderMigrationProgress = 1f) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: IOException) {
                uiState.update { it.copy(folderMigrationInProgress = false) }
            } catch (_: SecurityException) {
                uiState.update { it.copy(folderMigrationInProgress = false) }
            } catch (_: IllegalStateException) {
                uiState.update { it.copy(folderMigrationInProgress = false) }
            }
        }
    }

    private fun beginMigration() {
        uiState.update {
            it.copy(
                folderMigrationInProgress = true,
                folderMigrationProgress = 0f,
                folderMigrationMessage = null,
                folderMigrationError = null,
            )
        }
    }

    private fun finishMigrationSuccess(messageRes: Int) {
        uiState.update {
            it.copy(
                folderMigrationInProgress = false,
                folderMigrationProgress = 1f,
                folderMigrationMessage = UiText.StringResource(messageRes),
            )
        }
    }

    private fun finishMigrationFailure(e: Exception) {
        uiState.update {
            it.copy(
                folderMigrationInProgress = false,
                folderMigrationError = UiText.StringResource(
                    R.string.settings_dedicated_folder_migration_failed,
                    listOf(e.message ?: UiText.StringResource(R.string.error_unknown)),
                ),
            )
        }
    }
}
