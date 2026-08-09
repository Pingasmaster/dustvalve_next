package com.dustvalve.next.android.ui.screens.local

import android.content.Context
import android.content.IntentSender
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dustvalve.next.android.R
import com.dustvalve.next.android.data.local.datastore.SettingsDataStore
import com.dustvalve.next.android.di.qualifiers.AppDispatchers
import com.dustvalve.next.android.di.qualifiers.Dispatcher
import com.dustvalve.next.android.domain.model.Track
import com.dustvalve.next.android.domain.repository.LocalMusicRepository
import com.dustvalve.next.android.domain.repository.RecentSearchRepository
import com.dustvalve.next.android.util.LocaleCollation
import com.dustvalve.next.android.util.isAtLeastR
import com.dustvalve.next.android.util.legacyAudioPermission
import com.dustvalve.next.android.util.runCatchingUiIgnore
import com.dustvalve.next.android.util.runCatchingUiOrNull
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import javax.inject.Inject

data class LocalUiState(
    val query: String = "",
    val searchResults: List<Track> = emptyList(),
    val isSearching: Boolean = false,
    val searchFilter: LocalSearchFilter? = null,
    /** Non-null while a MediaStore delete needs user consent via IntentSender. */
    val pendingDeleteRequest: PendingDeleteRequest? = null,
    /** One-shot flag: a delete attempt failed; the screen shows a snackbar. */
    val deleteFailed: Boolean = false,
)

/** A MediaStore consent prompt for deleting [trackId]'s backing file. */
data class PendingDeleteRequest(val trackId: String, val intentSender: IntentSender)

enum class LocalSearchFilter { TRACKS, ARTISTS, ALBUMS }

enum class LocalSortOption(@param:StringRes val labelRes: Int) {
    TITLE_AZ(R.string.sort_title_az),
    ARTIST_AZ(R.string.sort_artist_az),
    ALBUM_AZ(R.string.sort_album_az),
    SHORTEST(R.string.sort_shortest),
    LONGEST(R.string.sort_longest),
    DATE_ADDED(R.string.sort_date_added),
    RELEASE_YEAR(R.string.sort_release_year),
}

enum class DurationRange(@param:StringRes val labelRes: Int) {
    UNDER_3(R.string.duration_under_3),
    THREE_TO_FIVE(R.string.duration_3_to_5),
    OVER_5(R.string.duration_over_5),
}

data class LocalFilterState(
    val sortOption: LocalSortOption = LocalSortOption.TITLE_AZ,
    val selectedArtists: Set<String> = emptySet(),
    val selectedAlbums: Set<String> = emptySet(),
    val selectedDurations: Set<DurationRange> = emptySet(),
    val favoritesOnly: Boolean = false,
    val selectedFolders: Set<String> = emptySet(),
    val reverseOrder: Boolean = false,
) {
    val hasActiveFilters: Boolean get() =
        selectedArtists.isNotEmpty() ||
            selectedAlbums.isNotEmpty() ||
            selectedDurations.isNotEmpty() ||
            favoritesOnly ||
            selectedFolders.isNotEmpty()
}

@HiltViewModel
class LocalViewModel @Inject constructor(
    private val recentSearchRepository: RecentSearchRepository,
    private val settingsDataStore: SettingsDataStore,
    private val localMusicRepository: LocalMusicRepository,
    @param:ApplicationContext private val appContext: Context,
    @param:Dispatcher(AppDispatchers.IO) private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LocalUiState())
    val uiState: StateFlow<LocalUiState> = _uiState.asStateFlow()

    val recentSearches: StateFlow<List<String>> = recentSearchRepository.getRecent("local")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val searchHistoryEnabled: StateFlow<Boolean> = kotlinx.coroutines.flow.combine(
        settingsDataStore.searchHistoryEnabled,
        settingsDataStore.searchHistoryLocal,
    ) { global, perSource -> global && perSource }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val localMusicEnabled: StateFlow<Boolean> = settingsDataStore.localMusicEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val localMusicUseMediaStore: StateFlow<Boolean> = settingsDataStore.localMusicUseMediaStore
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    /** Bumped on resume so the screen re-reads [hasAudioPermission]. */
    private val _permissionCheckEpoch = MutableStateFlow(0)
    val permissionCheckEpoch: StateFlow<Int> = _permissionCheckEpoch.asStateFlow()

    fun refreshPermissionCheck() {
        _permissionCheckEpoch.update { it + 1 }
    }

    fun hasAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, legacyAudioPermission()) ==
            PackageManager.PERMISSION_GRANTED

    val allLocalTracks: StateFlow<List<Track>> = localMusicRepository.getLocalTracks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Filter state

    private val _filterState = MutableStateFlow(LocalFilterState())
    val filterState: StateFlow<LocalFilterState> = _filterState.asStateFlow()

    val filteredTracks: StateFlow<List<Track>> = combine(
        allLocalTracks,
        _filterState,
    ) { tracks, filters ->
        val comparator = getSortComparator(filters.sortOption).let {
            if (filters.reverseOrder) it.reversed() else it
        }
        tracks
            .filter { applyFilters(it, filters) }
            .sortedWith(comparator)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // We keep blank values in the available lists so the UI can render
    // "Unknown artist" / "Unknown album" filter chips. Sorting puts blank
    // (unknown) at the bottom by treating it as the highest string.
    val availableArtists: StateFlow<List<String>> = allLocalTracks
        .map { tracks ->
            tracks.map { it.artist }.distinct().sortedWith(blankLastComparator())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val availableAlbums: StateFlow<List<String>> = allLocalTracks
        .map { tracks ->
            tracks.map { it.albumTitle }.distinct().sortedWith(blankLastComparator())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun blankLastComparator(): Comparator<String> {
        val byName = LocaleCollation.comparator()
        return Comparator { a, b ->
            when {
                a.isBlank() && b.isBlank() -> 0
                a.isBlank() -> 1
                b.isBlank() -> -1
                else -> byName.compare(a, b)
            }
        }
    }

    val availableFolders: StateFlow<List<String>> = allLocalTracks
        .map { tracks -> tracks.map { it.folderUri }.filter { it.isNotBlank() }.distinct().sorted() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // One-shot restore at construction. Sort + filters are independently
        // gated by their own toggles in Settings.
        viewModelScope.launch {
            runCatchingUiIgnore {

                if (settingsDataStore.keepLocalSort.first()) {
                    val sortName = settingsDataStore.localSortOption.first()
                    val reverse = settingsDataStore.localReverseOrder.first()
                    val parsed = sortName?.let { name ->
                        LocalSortOption.entries.firstOrNull { it.name == name }
                    }
                    if (parsed != null) {
                        _filterState.update { it.copy(sortOption = parsed, reverseOrder = reverse) }
                    }
                }
                if (settingsDataStore.keepLocalFilters.first()) {
                    val artists = settingsDataStore.localSelectedArtists.first()
                    val albums = settingsDataStore.localSelectedAlbums.first()
                    val durations = settingsDataStore.localSelectedDurations.first()
                        .mapNotNull { name -> DurationRange.entries.firstOrNull { it.name == name } }
                        .toSet()
                    val favoritesOnly = settingsDataStore.localFavoritesOnly.first()
                    val folders = settingsDataStore.localSelectedFolders.first()
                    _filterState.update {
                        it.copy(
                            selectedArtists = artists,
                            selectedAlbums = albums,
                            selectedDurations = durations,
                            favoritesOnly = favoritesOnly,
                            selectedFolders = folders,
                        )
                    }
                }
            }
        }
        // Persist sort changes when the toggle is on. drop(1) skips the
        // initial state (we don't want to overwrite restored values with the
        // pre-restore default).
        viewModelScope.launch {
            _filterState
                .map { it.sortOption.name to it.reverseOrder }
                .distinctUntilChanged()
                .drop(1)
                .collect { (name, reverse) ->
                    if (settingsDataStore.keepLocalSort.first()) {
                        runCatchingUiIgnore {

                            settingsDataStore.setLocalSort(name, reverse)
                        }
                    }
                }
        }
        viewModelScope.launch {
            _filterState
                .map {
                    FilterSnapshot(
                        artists = it.selectedArtists,
                        albums = it.selectedAlbums,
                        durations = it.selectedDurations.map { d -> d.name }.toSet(),
                        favoritesOnly = it.favoritesOnly,
                        folders = it.selectedFolders,
                    )
                }
                .distinctUntilChanged()
                .drop(1)
                .collect { snap ->
                    if (settingsDataStore.keepLocalFilters.first()) {
                        runCatchingUiIgnore {

                            settingsDataStore.setLocalFilters(
                                artists = snap.artists,
                                albums = snap.albums,
                                durations = snap.durations,
                                favoritesOnly = snap.favoritesOnly,
                                folders = snap.folders,
                            )
                        }
                    }
                }
        }
    }

    private data class FilterSnapshot(
        val artists: Set<String>,
        val albums: Set<String>,
        val durations: Set<String>,
        val favoritesOnly: Boolean,
        val folders: Set<String>,
    )

    // Filter mutation functions

    fun setSortOption(option: LocalSortOption) {
        _filterState.update { it.copy(sortOption = option) }
    }

    fun toggleArtist(artist: String) {
        _filterState.update { state ->
            val newSet = state.selectedArtists.toMutableSet()
            if (artist in newSet) newSet.remove(artist) else newSet.add(artist)
            state.copy(selectedArtists = newSet)
        }
    }

    fun toggleAlbum(album: String) {
        _filterState.update { state ->
            val newSet = state.selectedAlbums.toMutableSet()
            if (album in newSet) newSet.remove(album) else newSet.add(album)
            state.copy(selectedAlbums = newSet)
        }
    }

    fun toggleDuration(range: DurationRange) {
        _filterState.update { state ->
            val newSet = state.selectedDurations.toMutableSet()
            if (range in newSet) newSet.remove(range) else newSet.add(range)
            state.copy(selectedDurations = newSet)
        }
    }

    fun toggleFavoritesFilter() {
        _filterState.update { it.copy(favoritesOnly = !it.favoritesOnly) }
    }

    fun toggleFolder(folder: String) {
        _filterState.update { state ->
            val newSet = state.selectedFolders.toMutableSet()
            if (folder in newSet) newSet.remove(folder) else newSet.add(folder)
            state.copy(selectedFolders = newSet)
        }
    }

    fun toggleReverseOrder() {
        _filterState.update { it.copy(reverseOrder = !it.reverseOrder) }
    }

    fun setArtistFilter(artist: String) {
        _filterState.update { LocalFilterState(selectedArtists = setOf(artist)) }
    }

    fun clearFilters() {
        _filterState.update { LocalFilterState() }
    }

    // Local music enable + scan

    fun enableLocalMusic() {
        viewModelScope.launch {
            runCatchingUiIgnore {
                settingsDataStore.setLocalMusicEnabled(true)
            }
        }
    }

    /**
     * Permission denied during a first-enable attempt: roll the enable flag
     * back so the "Enable local music" button reappears. A re-grant denial
     * (already enabled, MediaStore mode, permission still missing) must leave
     * the flag on so the Local tab keeps showing the grant CTA.
     */
    fun onAudioPermissionDenied(wasRegrant: Boolean = false) {
        if (wasRegrant) return
        viewModelScope.launch {
            runCatchingUiIgnore {
                settingsDataStore.setLocalMusicEnabled(false)
            }
        }
    }

    /**
     * Audio permission granted. Respects the current local mode: never wipe
     * SAF folders via [LocalMusicRepository.clearAll] just because the Local
     * CTA ran. MediaStore scans upsert/diff on their own sentinel; SAF mode
     * with folders configured stays on SAF and rescans those trees.
     */
    fun onAudioPermissionGranted() {
        viewModelScope.launch {
            runCatchingUiIgnore(
                onFailure = { cause ->
                    _isScanning.value = false
                },
            ) {
                val useMediaStore = settingsDataStore.getLocalMusicUseMediaStoreSync()
                val folders = settingsDataStore.getLocalMusicFolderUrisSync()
                if (!useMediaStore && folders.isNotEmpty()) {
                    // Existing SAF setup: scan folders, do not force MediaStore.
                    _isScanning.value = true
                    localMusicRepository.scan()
                    _isScanning.value = false
                    localMusicRepository.scheduleSyncWork()
                    return@runCatchingUiIgnore
                }
                // MediaStore path (default or empty SAF): enable MediaStore
                // without clearAll so folder URI prefs survive accidental CTA.
                if (!useMediaStore) {
                    settingsDataStore.setLocalMusicUseMediaStore(true)
                }
                _isScanning.value = true
                localMusicRepository.scan()
                _isScanning.value = false
                localMusicRepository.scheduleSyncWork()
            }
        }
    }

    /** Enable + scan when Local CTA does not need MediaStore audio permission. */
    fun enableAndScanSaf() {
        viewModelScope.launch {
            runCatchingUiIgnore(
                onFailure = { _isScanning.value = false },
            ) {
                settingsDataStore.setLocalMusicEnabled(true)
                _isScanning.value = true
                localMusicRepository.scan()
                _isScanning.value = false
                localMusicRepository.scheduleSyncWork()
            }
        }
    }

    private fun applyFilters(track: Track, filters: LocalFilterState): Boolean {
        if (filters.selectedArtists.isNotEmpty() && track.artist !in filters.selectedArtists) return false
        if (filters.selectedAlbums.isNotEmpty() && track.albumTitle !in filters.selectedAlbums) return false
        if (filters.selectedDurations.isNotEmpty()) {
            val minutes = track.duration / 60f
            val matches = filters.selectedDurations.any { range ->
                when (range) {
                    DurationRange.UNDER_3 -> minutes < 3f
                    DurationRange.THREE_TO_FIVE -> minutes in 3f..5f
                    DurationRange.OVER_5 -> minutes > 5f
                }
            }
            if (!matches) return false
        }
        if (filters.favoritesOnly && !track.isFavorite) return false
        if (filters.selectedFolders.isNotEmpty() && track.folderUri !in filters.selectedFolders) return false
        return true
    }

    private fun getSortComparator(option: LocalSortOption): Comparator<Track> {
        val byName = LocaleCollation.comparator()
        return when (option) {
            LocalSortOption.TITLE_AZ -> compareBy(byName) { it.title }

            LocalSortOption.ARTIST_AZ -> compareBy<Track, String>(byName) { it.artist }
                .thenBy(byName) { it.title }

            LocalSortOption.ALBUM_AZ -> compareBy<Track, String>(byName) { it.albumTitle }
                .thenBy { it.trackNumber }

            LocalSortOption.SHORTEST -> compareBy { it.duration }

            LocalSortOption.LONGEST -> compareByDescending { it.duration }

            LocalSortOption.DATE_ADDED -> compareByDescending { it.dateAdded }

            LocalSortOption.RELEASE_YEAR -> compareByDescending<Track> { it.year }
                .thenBy(byName) { it.title }
        }
    }

    // Search

    private var searchJob: Job? = null

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query) }
        searchJob?.cancel()
        if (query.isNotBlank()) {
            searchJob = viewModelScope.launch {
                delay(SEARCH_DEBOUNCE_MS)
                performSearch(query)
            }
        } else {
            _uiState.update { it.copy(searchResults = emptyList(), isSearching = false) }
        }
    }

    fun onSearch() {
        searchJob?.cancel()
        val query = _uiState.value.query
        if (query.isNotBlank()) {
            saveRecentSearch(query)
            searchJob = viewModelScope.launch { performSearch(query) }
        }
    }

    fun onSearchFilterSelected(filter: LocalSearchFilter?) {
        _uiState.update { it.copy(searchFilter = filter, searchResults = emptyList()) }
        val query = _uiState.value.query
        if (query.isNotBlank()) {
            searchJob?.cancel()
            searchJob = viewModelScope.launch { performSearch(query) }
        }
    }

    fun removeRecentSearch(query: String) {
        viewModelScope.launch { recentSearchRepository.remove(query, "local") }
    }

    fun clearRecentSearches() {
        viewModelScope.launch { recentSearchRepository.clear("local") }
    }

    private fun saveRecentSearch(query: String) {
        if (!searchHistoryEnabled.value) return
        viewModelScope.launch {
            // The repository stores the query verbatim, so keep trimming here
            // (see RecentSearchRepository.add's kdoc); trim = true caps the
            // local history at 20 entries, as before.
            recentSearchRepository.add(query.trim(), "local")
        }
    }

    private suspend fun performSearch(query: String) {
        _uiState.update { it.copy(isSearching = true) }
        runCatchingUiIgnore(
            onFailure = { cause ->
                _uiState.update { it.copy(isSearching = false) }
            },
        ) {
            val filter = _uiState.value.searchFilter
            val results = withContext(ioDispatcher) {
                // The IO wrap stays here (not in the repository) so the
                // in-memory locale-aware filtering below keeps running on IO.
                val all = localMusicRepository.searchLocalTracks(query)
                // Locale-aware case folding (e.g. Turkish dotted/dotless i)
                val locale = Locale.getDefault()
                val lowerQuery = query.lowercase(locale)
                when (filter) {
                    null -> all
                    LocalSearchFilter.TRACKS -> all.filter { it.title.lowercase(locale).contains(lowerQuery) }
                    LocalSearchFilter.ARTISTS -> all.filter { it.artist.lowercase(locale).contains(lowerQuery) }
                    LocalSearchFilter.ALBUMS -> all.filter { it.albumTitle.lowercase(locale).contains(lowerQuery) }
                }
            }
            _uiState.update { it.copy(searchResults = results, isSearching = false) }
        }
    }

    /**
     * Delete a local track's backing file, then its DB row - and ONLY then.
     * Deleting the row while the file survives just resurrects the track on
     * the next scan.
     *
     * - SAF documents (folder mode) need [DocumentsContract.deleteDocument];
     *   a plain ContentResolver.delete() is a no-op/failure for them.
     * - MediaStore items the app doesn't own throw RecoverableSecurityException;
     *   [MediaStore.createDeleteRequest] raises a system consent prompt whose
     *   IntentSender the screen launches; the row is removed on RESULT_OK.
     */
    fun deleteLocalTrack(track: Track) {
        viewModelScope.launch {
            runCatchingUiIgnore(
                onFailure = { cause ->
                    reportDeleteFailure()
                },
            ) {
                val rawUrl = track.streamUrl
                if (rawUrl.isNullOrBlank()) {
                    deleteDbRow(track.id)
                    return@launch
                }
                val uri = rawUrl.toUri()
                when {
                    uri.scheme != "content" -> {
                        // Not a content URI (e.g. plain file path): best-effort direct
                        // delete; the DB row goes regardless since there is no
                        // consent flow to fall back to.
                        runCatchingUiIgnore {
                            appContext.contentResolver.delete(uri, null, null)
                        }
                        deleteDbRow(track.id)
                    }

                    DocumentsContract.isDocumentUri(appContext, uri) -> deleteSafDocument(track.id, uri)

                    else -> deleteMediaStoreItem(track.id, uri)
                }
            }
        }
    }

    private suspend fun deleteSafDocument(trackId: String, uri: Uri) {
        val deleted = runCatchingUiOrNull {
            DocumentsContract.deleteDocument(appContext.contentResolver, uri)
        } ?: false
        if (deleted) deleteDbRow(trackId) else reportDeleteFailure()
    }

    /**
     * MediaStore content URI. Direct delete works for items this app owns;
     * anything else throws (Recoverable)SecurityException and needs the
     * user-consent delete request whose IntentSender the screen launches.
     */
    private suspend fun deleteMediaStoreItem(trackId: String, uri: Uri) {
        val deleted = runCatchingUiOrNull {
            appContext.contentResolver.delete(uri, null, null) > 0
        } ?: false
        if (deleted) {
            deleteDbRow(trackId)
            return
        }
        // createDeleteRequest is API 30+; below that there is no consent
        // IntentSender path and the delete simply fails.
        val sender = if (!isAtLeastR()) {
            null
        } else {
            runCatchingUiOrNull {
                MediaStore.createDeleteRequest(appContext.contentResolver, listOf(uri)).intentSender
            }
        }
        if (sender != null) {
            _uiState.update {
                it.copy(pendingDeleteRequest = PendingDeleteRequest(trackId, sender))
            }
        } else {
            reportDeleteFailure()
        }
    }

    /** Result of the MediaStore consent prompt launched by the screen. */
    fun onDeleteRequestResult(granted: Boolean) {
        val pending = _uiState.value.pendingDeleteRequest ?: return
        _uiState.update { it.copy(pendingDeleteRequest = null) }
        if (granted) {
            // The system already deleted the media item on consent; drop the row.
            viewModelScope.launch { deleteDbRow(pending.trackId) }
        }
    }

    fun clearDeleteFailed() {
        _uiState.update { it.copy(deleteFailed = false) }
    }

    private fun reportDeleteFailure() {
        _uiState.update { it.copy(deleteFailed = true) }
    }

    private suspend fun deleteDbRow(trackId: String) {
        runCatchingUiIgnore(
            onFailure = { cause ->
                reportDeleteFailure()
            },
        ) {
            localMusicRepository.deleteTrackRows(listOf(trackId))
            // SAF-mode covers live at local_art/<trackId>.jpg; drop the orphan.
            // The cover file deletion deliberately stays in the ViewModel: the
            // repository deletes DB rows only.
            File(appContext.filesDir, "local_art/$trackId.jpg").delete()
        }
    }

    private companion object {
        private const val SEARCH_DEBOUNCE_MS = 300L
    }
}
