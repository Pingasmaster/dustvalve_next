package com.dustvalve.next.android.ui.screens.artist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dustvalve.next.android.data.local.datastore.SettingsDataStore
import com.dustvalve.next.android.domain.model.Artist
import com.dustvalve.next.android.domain.model.Track
import com.dustvalve.next.android.domain.repository.ArtistRepository
import com.dustvalve.next.android.domain.repository.DownloadRepository
import com.dustvalve.next.android.domain.usecase.DownloadAlbumUseCase
import com.dustvalve.next.android.domain.usecase.GetAlbumDetailUseCase
import com.dustvalve.next.android.domain.usecase.GetArtistDetailUseCase
import com.dustvalve.next.android.domain.usecase.ToggleFavoriteUseCase
import com.dustvalve.next.android.util.UiText
import com.dustvalve.next.android.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

data class ArtistDetailUiState(
    val artist: Artist? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val mixTracks: List<Track>? = null,
    val isLoadingMix: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadedAlbumIds: Set<String> = emptySet(),
    val snackbarMessage: UiText? = null,
    val isSnackbarError: Boolean = false,
)

@HiltViewModel
class ArtistDetailViewModel @Inject constructor(
    private val getArtistDetailUseCase: GetArtistDetailUseCase,
    private val getAlbumDetailUseCase: GetAlbumDetailUseCase,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase,
    private val downloadAlbumUseCase: DownloadAlbumUseCase,
    private val artistRepository: ArtistRepository,
    private val downloadRepository: DownloadRepository,
    private val settingsDataStore: SettingsDataStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ArtistDetailUiState())
    val uiState: StateFlow<ArtistDetailUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var loadedUrl: String? = null
    private var favoriteJob: Job? = null
    var retryAction: (() -> Unit)? = null
        private set

    init {
        collectDownloadedAlbumIds()
    }

    private fun collectDownloadedAlbumIds() {
        viewModelScope.launch {
            downloadRepository.getDownloadedAlbumIds()
                .catch { /* ignore */ }
                .collect { ids ->
                    _uiState.update { it.copy(downloadedAlbumIds = ids.toSet()) }
                }
        }
    }

    fun loadArtist(url: String) {
        if (loadedUrl == url && _uiState.value.artist != null) return
        loadJob?.cancel()
        val isNewUrl = loadedUrl != null && loadedUrl != url
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, artist = if (isNewUrl) null else it.artist) }
            try {
                artistRepository.getArtistDetailFlow(url)
                    .collect { artist ->
                        loadedUrl = url
                        _uiState.update {
                            it.copy(
                                artist = artist,
                                isLoading = false,
                                error = null,
                            )
                        }
                    }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load artist",
                    )
                }
            }
        }
    }

    fun toggleFavorite() {
        if (favoriteJob?.isActive == true) return
        val artist = _uiState.value.artist ?: return
        val previousFavorite = artist.isFavorite
        _uiState.update {
            it.copy(artist = it.artist?.copy(isFavorite = !previousFavorite))
        }
        favoriteJob = viewModelScope.launch {
            try {
                toggleFavoriteUseCase.toggleArtistFavorite(artist.id)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update {
                    it.copy(artist = it.artist?.copy(isFavorite = previousFavorite))
                }
            }
        }
    }

    private var downloadJob: Job? = null

    fun downloadAll() {
        if (downloadJob?.isActive == true) return
        val artist = _uiState.value.artist ?: return
        _uiState.update { it.copy(isDownloading = true) }
        downloadJob = viewModelScope.launch {
            try {
                downloadAlbumUseCase.downloadArtist(artist)
                if (settingsDataStore.getAutoDownloadFutureContentSync()) {
                    artistRepository.setAutoDownload(artist.id, true)
                }
                _uiState.update {
                    it.copy(
                        isDownloading = false,
                        snackbarMessage = UiText.StringResource(R.string.snackbar_downloaded_all_by, listOf(artist.name)),
                        isSnackbarError = false,
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                retryAction = { downloadAll() }
                _uiState.update {
                    it.copy(
                        isDownloading = false,
                        snackbarMessage = e.message?.let { UiText.DynamicString(it) } ?: UiText.StringResource(R.string.snackbar_download_failed),
                        isSnackbarError = true,
                    )
                }
            }
        }
    }

    fun deleteAllDownloads() {
        val artist = _uiState.value.artist ?: return
        viewModelScope.launch {
            try {
                downloadAlbumUseCase.deleteArtistDownloads(artist)
                _uiState.update {
                    it.copy(
                        snackbarMessage = UiText.StringResource(R.string.snackbar_deleted_all_by, listOf(artist.name)),
                        isSnackbarError = false,
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update {
                    it.copy(
                        snackbarMessage = e.message?.let { UiText.DynamicString(it) } ?: UiText.StringResource(R.string.snackbar_delete_failed),
                        isSnackbarError = true,
                    )
                }
            }
        }
    }

    fun clearSnackbar() {
        retryAction = null
        _uiState.update { it.copy(snackbarMessage = null, isSnackbarError = false) }
    }

    fun loadMixTracks(onReady: (List<Track>) -> Unit) {
        val artist = _uiState.value.artist ?: return
        if (_uiState.value.isLoadingMix) return

        _uiState.update { it.copy(isLoadingMix = true) }
        viewModelScope.launch {
            try {
                // Fetch all albums concurrently to get tracks from every release
                val allTracks = artist.albums.map { albumStub ->
                    async {
                        try {
                            getAlbumDetailUseCase(albumStub.url).tracks
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            emptyList()
                        }
                    }
                }.awaitAll().flatten()

                if (allTracks.isNotEmpty()) {
                    _uiState.update { it.copy(isLoadingMix = false) }
                    onReady(allTracks.shuffled())
                } else {
                    _uiState.update { it.copy(isLoadingMix = false) }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update { it.copy(isLoadingMix = false) }
            }
        }
    }
}
