package com.dustvalve.next.android.ui.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dustvalve.next.android.BuildConfig
import com.dustvalve.next.android.domain.repository.AppUpdate
import com.dustvalve.next.android.domain.repository.UpdateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

data class UpdateUiState(
    val availableUpdate: AppUpdate? = null,
    val showDialog: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadProgress: Float = 0f,
    val downloadError: String? = null,
    val readyToInstall: Boolean = false,
)

@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val updateRepository: UpdateRepository,
    @param:ApplicationContext private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(UpdateUiState())
    val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()

    fun checkForUpdate() {
        viewModelScope.launch {
            try {
                val update = updateRepository.checkForUpdate(BuildConfig.VERSION_NAME)
                if (update != null) {
                    _uiState.value = UpdateUiState(
                        availableUpdate = update,
                        showDialog = true,
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
    }

    fun dismissDialog() {
        _uiState.value = UpdateUiState()
    }

    fun startDownload() {
        val update = _uiState.value.availableUpdate ?: return
        _uiState.value = _uiState.value.copy(
            isDownloading = true,
            downloadProgress = 0f,
            downloadError = null,
        )

        viewModelScope.launch {
            updateRepository.downloadApk(update.apkDownloadUrl)
                .onCompletion { cause ->
                    if (cause == null) {
                        _uiState.value = _uiState.value.copy(
                            isDownloading = false,
                            downloadProgress = 1f,
                            readyToInstall = true,
                        )
                    }
                }
                .catch { e ->
                    _uiState.value = _uiState.value.copy(
                        isDownloading = false,
                        downloadError = e.message ?: "Download failed",
                    )
                }
                .collect { progress ->
                    _uiState.value = _uiState.value.copy(
                        downloadProgress = progress.fraction,
                    )
                }
        }
    }

    fun installApk() {
        val path = updateRepository.getDownloadedApkPath() ?: return
        val file = File(path)
        if (!file.exists()) return

        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(installIntent)
    }
}
