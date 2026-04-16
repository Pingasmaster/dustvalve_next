package com.dustvalve.next.android.ui.screens.youtube

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dustvalve.next.android.data.local.db.dao.ArtistDao
import com.dustvalve.next.android.data.local.db.dao.FavoriteDao
import com.dustvalve.next.android.data.local.db.dao.TrackDao
import com.dustvalve.next.android.data.local.db.dao.getFavoriteIds
import com.dustvalve.next.android.data.local.db.entity.ArtistEntity
import com.dustvalve.next.android.data.local.db.entity.FavoriteEntity
import com.dustvalve.next.android.data.mapper.toDomain
import com.dustvalve.next.android.data.mapper.toEntity
import com.dustvalve.next.android.domain.model.Track
import com.dustvalve.next.android.domain.repository.DownloadRepository
import com.dustvalve.next.android.domain.repository.YouTubeRepository
import com.dustvalve.next.android.domain.usecase.DownloadAlbumUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.security.MessageDigest
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

data class YouTubeArtistDetailUiState(
    val artistName: String = "",
    val artistUrl: String = "",
    val imageUrl: String? = null,
    val tracks: List<Track> = emptyList(),
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val error: String? = null,
    val isFavorite: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadedTrackIds: Set<String> = emptySet(),
)

@HiltViewModel
class YouTubeArtistDetailViewModel @Inject constructor(
    private val youtubeRepository: YouTubeRepository,
    private val favoriteDao: FavoriteDao,
    private val downloadRepository: DownloadRepository,
    private val downloadAlbumUseCase: DownloadAlbumUseCase,
    private val artistDao: ArtistDao,
    private val trackDao: TrackDao,
) : ViewModel() {

    private val _uiState = MutableStateFlow(YouTubeArtistDetailUiState())
    val uiState: StateFlow<YouTubeArtistDetailUiState> = _uiState.asStateFlow()

    private var loadedUrl: String? = null
    private var nextPage: Any? = null

    init {
        collectDownloadedTrackIds()
    }

    private fun collectDownloadedTrackIds() {
        viewModelScope.launch {
            downloadRepository.getDownloadedTrackIds()
                .catch { /* ignore */ }
                .collect { ids ->
                    _uiState.update { it.copy(downloadedTrackIds = ids.toSet()) }
                }
        }
    }

    fun loadArtist(url: String, name: String, imageUrl: String?) {
        if (loadedUrl == url && _uiState.value.tracks.isNotEmpty()) return
        loadedUrl = url
        nextPage = null
        _uiState.update {
            it.copy(
                artistName = name, artistUrl = url, imageUrl = imageUrl,
                isLoading = true, error = null, tracks = emptyList(), hasMore = true,
            )
        }
        viewModelScope.launch {
            try {
                // Check cache first
                val cachedArtist = artistDao.getByUrl(url)
                if (cachedArtist != null &&
                    (System.currentTimeMillis() - cachedArtist.cachedAt) < CACHE_TTL_MS
                ) {
                    val channelAlbumId = "yt_channel_${md5Hash(url).take(12)}"
                    val cachedTracks = trackDao.getByAlbumId(channelAlbumId)
                    if (cachedTracks.isNotEmpty()) {
                        val isFav = favoriteDao.isFavorite(url)
                        val allTrackIds = cachedTracks.map { it.id }
                        val favoriteIds = favoriteDao.getFavoriteIds(allTrackIds).toSet()
                        _uiState.update {
                            it.copy(
                                tracks = cachedTracks.map { t -> t.toDomain(t.id in favoriteIds) },
                                artistName = cachedArtist.name,
                                imageUrl = cachedArtist.imageUrl ?: imageUrl,
                                isLoading = false,
                                hasMore = false,
                                isFavorite = isFav,
                            )
                        }
                        // Refresh in background for next visit
                        refreshInBackground(url, name, imageUrl)
                        return@launch
                    }
                }

                // Cache miss or stale: fetch from network
                fetchAndPersist(url, name, imageUrl)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: "Failed to load artist")
                }
            }
        }
    }

    private suspend fun fetchAndPersist(url: String, name: String, imageUrl: String?) {
        val (tracks, channelName, newNextPage) = youtubeRepository.getChannelVideos(url)
        nextPage = newNextPage
        val isFav = favoriteDao.isFavorite(url)

        // Persist artist
        artistDao.insert(
            ArtistEntity(
                id = url,
                name = channelName ?: name,
                url = url,
                imageUrl = imageUrl,
                bio = null,
                location = null,
                source = "youtube",
            )
        )

        // Persist tracks
        if (tracks.isNotEmpty()) {
            trackDao.insertAll(tracks.map { it.toEntity() })
        }

        _uiState.update {
            it.copy(
                tracks = tracks,
                artistName = channelName ?: name,
                isLoading = false,
                hasMore = newNextPage != null && tracks.isNotEmpty(),
                isFavorite = isFav,
            )
        }
    }

    private fun refreshInBackground(url: String, name: String, imageUrl: String?) {
        viewModelScope.launch {
            try {
                val (tracks, channelName, newNextPage) = youtubeRepository.getChannelVideos(url)
                nextPage = newNextPage

                // Update cache
                artistDao.updateCachedAt(url)
                if (tracks.isNotEmpty()) {
                    trackDao.insertAll(tracks.map { it.toEntity() })
                }

                // Update UI if user is still on this artist
                if (loadedUrl == url) {
                    val isFav = favoriteDao.isFavorite(url)
                    _uiState.update {
                        it.copy(
                            tracks = tracks,
                            artistName = channelName ?: name,
                            imageUrl = imageUrl ?: it.imageUrl,
                            hasMore = newNextPage != null && tracks.isNotEmpty(),
                            isFavorite = isFav,
                        )
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                // Background refresh failure is silent — cache is still valid
            }
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoadingMore || !state.hasMore || state.artistUrl.isBlank()) return
        _uiState.update { it.copy(isLoadingMore = true) }
        viewModelScope.launch {
            try {
                val (tracks, _, newNextPage) = youtubeRepository.getChannelVideos(
                    state.artistUrl, nextPage,
                )
                nextPage = newNextPage

                // Persist new tracks
                if (tracks.isNotEmpty()) {
                    trackDao.insertAll(tracks.map { it.toEntity() })
                }

                _uiState.update {
                    val existingIds = it.tracks.mapTo(HashSet()) { t -> t.id }
                    val newTracks = tracks.filter { t -> t.id !in existingIds }
                    it.copy(
                        tracks = it.tracks + newTracks,
                        isLoadingMore = false,
                        hasMore = newNextPage != null && tracks.isNotEmpty(),
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update { it.copy(isLoadingMore = false) }
            }
        }
    }

    fun toggleFavorite() {
        val state = _uiState.value
        val url = state.artistUrl
        if (url.isBlank()) return
        val prev = state.isFavorite
        _uiState.update { it.copy(isFavorite = !prev) }
        viewModelScope.launch {
            try {
                if (prev) {
                    favoriteDao.delete(url)
                } else {
                    // Persist the artist entity so library INNER JOIN works
                    artistDao.insert(
                        ArtistEntity(
                            id = url,
                            name = state.artistName,
                            url = url,
                            imageUrl = state.imageUrl,
                            bio = null,
                            location = null,
                            source = "youtube",
                        )
                    )
                    favoriteDao.insert(FavoriteEntity(id = url, type = "artist"))
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update { it.copy(isFavorite = prev) }
            }
        }
    }

    fun downloadAll() {
        val tracks = _uiState.value.tracks
        if (tracks.isEmpty() || _uiState.value.isDownloading) return
        _uiState.update { it.copy(isDownloading = true) }
        viewModelScope.launch {
            try {
                for (track in tracks) {
                    if (track.id !in _uiState.value.downloadedTrackIds) {
                        downloadAlbumUseCase.downloadTrack(track)
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
            _uiState.update { it.copy(isDownloading = false) }
        }
    }

    fun deleteAllDownloads() {
        viewModelScope.launch {
            for (track in _uiState.value.tracks) {
                try {
                    downloadAlbumUseCase.deleteTrackDownload(track.id)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                }
            }
        }
    }

    private fun md5Hash(input: String): String {
        val digest = MessageDigest.getInstance("MD5")
        return digest.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val CACHE_TTL_MS = 30 * 60 * 1000L // 30 minutes, matches Bandcamp
    }
}
