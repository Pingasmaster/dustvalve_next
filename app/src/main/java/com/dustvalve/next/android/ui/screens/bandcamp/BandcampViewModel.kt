package com.dustvalve.next.android.ui.screens.bandcamp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dustvalve.next.android.data.remote.SubTag
import com.dustvalve.next.android.data.remote.genreSubTags
import com.dustvalve.next.android.domain.model.Album
import com.dustvalve.next.android.domain.usecase.DiscoverDustvalveUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import android.util.Log
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

data class BandcampUiState(
    /** Preview thumbnails per genre tag (loaded on init) */
    val categoryPreviews: Map<String, List<Album>> = emptyMap(),
    val previewsError: Boolean = false,

    /** Bottom sheet state */
    val showCategorySheet: Boolean = false,
    val selectedGenreName: String = "",
    val selectedGenreTag: String = "",
    val categoryAlbums: List<Album> = emptyList(),
    val isCategoryLoading: Boolean = false,
    val categoryError: String? = null,
    val selectedSubTag: String? = null,
    val availableSubTags: List<SubTag> = emptyList(),
)

@HiltViewModel
class BandcampViewModel @Inject constructor(
    private val discoverDustvalveUseCase: DiscoverDustvalveUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BandcampUiState())
    val uiState: StateFlow<BandcampUiState> = _uiState.asStateFlow()

    private var loadCategoryJob: Job? = null

    init {
        loadPreviews()
    }

    private fun loadPreviews() {
        viewModelScope.launch {
            try {
                val result = discoverDustvalveUseCase()
                Log.d(TAG, "loadPreviews: ${result.albums.size} albums returned")
                val albums = result.albums
                val chunks = albums.chunked(4)
                val previews = mutableMapOf<String, List<Album>>()
                GENRE_TAGS.forEachIndexed { index, tag ->
                    previews[tag] = chunks.getOrElse(index) { emptyList() }
                }
                _uiState.update { it.copy(categoryPreviews = previews) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "loadPreviews failed", e)
                _uiState.update { it.copy(previewsError = true) }
            }
        }
    }

    fun retryPreviews() {
        _uiState.update { it.copy(previewsError = false) }
        loadPreviews()
    }

    fun selectCategory(tag: String, name: String) {
        val subTags = genreSubTags[tag] ?: emptyList()
        _uiState.update {
            it.copy(
                showCategorySheet = true,
                selectedGenreName = name,
                selectedGenreTag = tag,
                selectedSubTag = null,
                availableSubTags = subTags,
                isCategoryLoading = true,
                categoryAlbums = emptyList(),
                categoryError = null,
            )
        }
        loadCategoryAlbums(tag)
    }

    fun selectSubTag(subTagSlug: String?) {
        _uiState.update {
            it.copy(
                selectedSubTag = subTagSlug,
                isCategoryLoading = true,
                categoryAlbums = emptyList(),
                categoryError = null,
            )
        }
        val tagToLoad = subTagSlug ?: _uiState.value.selectedGenreTag
        loadCategoryAlbums(tagToLoad)
    }

    private fun loadCategoryAlbums(tag: String) {
        loadCategoryJob?.cancel()
        loadCategoryJob = viewModelScope.launch {
            try {
                val genreParam = tag.takeIf { it.isNotEmpty() }
                val result = discoverDustvalveUseCase(genre = genreParam)
                Log.d(TAG, "loadCategoryAlbums($tag): ${result.albums.size} albums returned")
                _uiState.update {
                    it.copy(
                        categoryAlbums = result.albums,
                        isCategoryLoading = false,
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(TAG, "loadCategoryAlbums($tag) failed", e)
                _uiState.update {
                    it.copy(
                        isCategoryLoading = false,
                        categoryError = e.message ?: "Failed to load",
                    )
                }
            }
        }
    }

    fun dismissCategory() {
        loadCategoryJob?.cancel()
        _uiState.update {
            it.copy(
                showCategorySheet = false,
                selectedGenreName = "",
                selectedGenreTag = "",
                selectedSubTag = null,
                availableSubTags = emptyList(),
                categoryAlbums = emptyList(),
                isCategoryLoading = false,
                categoryError = null,
            )
        }
    }

    fun retryCategory() {
        val current = _uiState.value
        if (!current.showCategorySheet) return
        val tagToLoad = current.selectedSubTag ?: current.selectedGenreTag
        loadCategoryAlbums(tagToLoad)
    }

    companion object {
        private const val TAG = "BandcampViewModel"
        val GENRE_TAGS = listOf(
            "", "rock", "hip-hop-rap", "alternative", "electronic", "metal",
            "experimental", "pop", "jazz", "blues", "punk", "r-b-soul",
        )
    }
}
