package com.dustvalve.next.android.ui.navigation

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dustvalve.next.android.data.remote.BandcampDomainSniffer
import com.dustvalve.next.android.domain.model.MusicProvider
import com.dustvalve.next.android.domain.model.Track
import com.dustvalve.next.android.domain.repository.YouTubeRepository
import com.dustvalve.next.android.domain.usecase.ProviderStateUseCase
import com.dustvalve.next.android.util.DeepLinkAction
import com.dustvalve.next.android.util.DeepLinkRouter
import com.dustvalve.next.android.util.LinkResourceType
import com.dustvalve.next.android.util.NetworkUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.net.URLDecoder
import java.net.URLEncoder
import javax.inject.Inject

/** A compatible link whose provider is disabled - drives the "enable provider?" dialog. */
data class PendingLink(val provider: MusicProvider, val type: LinkResourceType, val action: DeepLinkAction)

@HiltViewModel
class NavigationViewModel @Inject constructor(
    private val providerStateUseCase: ProviderStateUseCase,
    private val youtubeRepository: YouTubeRepository,
    private val bandcampDomainSniffer: BandcampDomainSniffer,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _activeProviders = MutableStateFlow(setOf(MusicProvider.LOCAL))

    val visibleTabs: StateFlow<List<BottomNavItem>> = _activeProviders
        .map { providers ->
            BottomNavItem.entries.filter { item ->
                item.provider == null || item.provider in providers
            }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            listOf(
                BottomNavItem.LOCAL,
                BottomNavItem.LIBRARY,
                BottomNavItem.SETTINGS,
            ),
        )

    private val _currentTab = MutableStateFlow(BottomNavItem.LOCAL)
    val currentTab: StateFlow<BottomNavItem> = _currentTab.asStateFlow()

    private val tabStacks = java.util.concurrent.ConcurrentHashMap<BottomNavItem, List<NavDestination>>(
        mapOf(
            BottomNavItem.LOCAL to listOf(NavDestination.LocalHome),
            BottomNavItem.BANDCAMP to listOf(NavDestination.BandcampHome),
            BottomNavItem.YOUTUBE to listOf(NavDestination.YouTubeHome),
            BottomNavItem.LIBRARY to listOf(NavDestination.Library),
            BottomNavItem.SETTINGS to listOf(NavDestination.Settings),
        ),
    )

    private val _backStack = MutableStateFlow<List<NavDestination>>(listOf(NavDestination.LocalHome))
    val backStack: StateFlow<List<NavDestination>> = _backStack.asStateFlow()

    private val _lastNavigationForward = MutableStateFlow(true)
    val lastNavigationForward: StateFlow<Boolean> = _lastNavigationForward.asStateFlow()

    private val _showFullPlayer = MutableStateFlow(false)
    val showFullPlayer: StateFlow<Boolean> = _showFullPlayer.asStateFlow()

    /**
     * Every destination currently alive in ANY tab's stack. AppNavigation uses
     * this to retire per-destination ViewModelStores once a detail page can no
     * longer be navigated back to.
     */
    private val _allDestinations = MutableStateFlow<Set<NavDestination>>(tabStacks.values.flatten().toSet())
    val allDestinations: StateFlow<Set<NavDestination>> = _allDestinations.asStateFlow()

    init {
        restoreSavedState()
        viewModelScope.launch {
            providerStateUseCase.activeProviders.collect { providers ->
                _activeProviders.value = providers
                // Reset tab stacks for disabled providers so re-enabling starts fresh
                for (item in BottomNavItem.entries) {
                    if (item.provider != null && item.provider !in providers) {
                        tabStacks[item] = listOf(item.destination)
                    }
                }
                // If current tab's provider got disabled, redirect to LOCAL
                val currentProvider = _currentTab.value.provider
                if (currentProvider != null && currentProvider !in providers) {
                    navigateTo(NavDestination.LocalHome)
                } else {
                    refreshAllDestinations()
                    persistState()
                }
            }
        }
    }

    private val _deepLinkTrack = MutableStateFlow<Track?>(null)
    val deepLinkTrack: StateFlow<Track?> = _deepLinkTrack.asStateFlow()

    fun consumeDeepLinkTrack() {
        _deepLinkTrack.value = null
    }

    /** A compatible link pointing at a disabled provider; non-null shows the enable dialog. */
    private val _pendingLinkConfirmation = MutableStateFlow<PendingLink?>(null)
    val pendingLinkConfirmation: StateFlow<PendingLink?> = _pendingLinkConfirmation.asStateFlow()

    /** Fired when an opened/pasted link isn't from a supported source. */
    private val _unsupportedLinkEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val unsupportedLinkEvents: SharedFlow<Unit> = _unsupportedLinkEvents.asSharedFlow()

    /**
     * Open a pasted/external link. Resolves it offline first, then (only for input with an
     * explicit http/https scheme) via a single network sniff for custom-domain Bandcamp
     * pages - scheme-less dotted text like "will.i.am" is a search query, not a URL, and
     * must never hit the network. If it targets a disabled provider, surfaces the enable
     * dialog; otherwise executes immediately. Unsupported input raises an
     * [unsupportedLinkEvents] notice.
     */
    fun openLink(raw: String) {
        viewModelScope.launch {
            try {
                val detected = DeepLinkRouter.detect(raw)
                    ?: if (hasExplicitWebScheme(raw) && DeepLinkRouter.looksLikeUrl(raw)) {
                        bandcampDomainSniffer.sniff(raw)
                    } else {
                        null
                    }
                if (detected == null) {
                    _unsupportedLinkEvents.tryEmit(Unit)
                    return@launch
                }
                // Read the persisted provider set directly: on a cold-start deep link
                // _activeProviders may still hold its {LOCAL} default because the
                // DataStore emission hasn't landed yet, which would raise a spurious
                // enable-provider dialog.
                val activeProviders = try {
                    providerStateUseCase.activeProviders.first()
                } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                    throw e
                } catch (_: Exception) {
                    _activeProviders.value
                }
                if (detected.provider !in activeProviders) {
                    _pendingLinkConfirmation.value =
                        PendingLink(detected.provider, detected.type, detected.action)
                } else {
                    execute(detected.action)
                }
            } catch (e: Exception) {
                if (e is kotlin.coroutines.cancellation.CancellationException) throw e
                Log.e(TAG, "openLink failed for input", e)
                _unsupportedLinkEvents.tryEmit(Unit)
            }
        }
    }

    /** OS deep links / shares funnel through the same path so gating + routing are shared. */
    fun handleDeepLink(url: String) = openLink(url)

    /** User accepted enabling the provider for the pending link: enable it, then open. */
    fun confirmPendingLink() {
        val pending = _pendingLinkConfirmation.value ?: return
        _pendingLinkConfirmation.value = null
        viewModelScope.launch {
            providerStateUseCase.setEnabled(pending.provider, true)
            execute(pending.action)
        }
    }

    fun dismissPendingLink() {
        _pendingLinkConfirmation.value = null
    }

    private fun execute(action: DeepLinkAction) {
        when (action) {
            is DeepLinkAction.Navigate -> navigateTo(action.destination)

            is DeepLinkAction.PlayYouTubeVideo -> {
                navigateTo(NavDestination.YouTubeHome)
                viewModelScope.launch {
                    try {
                        val track = youtubeRepository.getTrackInfo(action.videoUrl)
                        _deepLinkTrack.value = track
                    } catch (e: Exception) {
                        if (e is kotlin.coroutines.cancellation.CancellationException) throw e
                        // Track resolution failed - silently ignore
                    }
                }
            }
        }
    }

    fun navigateTo(dest: NavDestination) {
        val tab = tabForDestination(dest)
        if (tab != null) {
            val currentOrdinal = _currentTab.value.ordinal
            val targetOrdinal = tab.ordinal
            if (tab == _currentTab.value) {
                _lastNavigationForward.value = false
                val rootStack = listOf(dest)
                tabStacks[tab] = rootStack
                _backStack.value = rootStack
            } else {
                _lastNavigationForward.value = targetOrdinal >= currentOrdinal
                tabStacks[_currentTab.value] = _backStack.value
                _currentTab.value = tab
                _backStack.value = tabStacks[tab] ?: listOf(dest)
            }
        } else {
            when (dest) {
                is NavDestination.AlbumDetail ->
                    if (!NetworkUtils.isValidHttpsUrl(dest.url)) return

                is NavDestination.ArtistDetail ->
                    if (!NetworkUtils.isValidHttpsUrl(dest.url)) return

                else -> {}
            }
            val currentStack = _backStack.value
            if (currentStack.lastOrNull() == dest) return
            val newStack = if (currentStack.size >= MAX_STACK_DEPTH) {
                // Keep index 0 (the tab's home destination); drop the oldest
                // detail entry instead so back always lands on the tab root.
                listOf(currentStack.first()) + currentStack.drop(2) + dest
            } else {
                currentStack + dest
            }
            _lastNavigationForward.value = true
            _backStack.value = newStack
            tabStacks[_currentTab.value] = newStack
        }
        refreshAllDestinations()
        persistState()
    }

    fun navigateBack(): Boolean {
        val current = _backStack.value
        return if (current.size > 1) {
            _lastNavigationForward.value = false
            val newStack = current.dropLast(1)
            _backStack.value = newStack
            tabStacks[_currentTab.value] = newStack
            refreshAllDestinations()
            persistState()
            true
        } else {
            false
        }
    }

    private val _pendingLocalArtistFilter = MutableStateFlow<String?>(null)
    val pendingLocalArtistFilter: StateFlow<String?> = _pendingLocalArtistFilter.asStateFlow()

    fun requestLocalArtistFilter(artist: String) {
        navigateTo(NavDestination.LocalHome)
        _pendingLocalArtistFilter.value = artist
    }

    fun consumeLocalArtistFilter() {
        _pendingLocalArtistFilter.value = null
    }

    fun expandPlayer() {
        _showFullPlayer.value = true
        persistState()
    }

    fun collapsePlayer() {
        _showFullPlayer.value = false
        persistState()
    }

    private fun tabForDestination(dest: NavDestination): BottomNavItem? = when (dest) {
        is NavDestination.LocalHome -> BottomNavItem.LOCAL
        is NavDestination.BandcampHome -> BottomNavItem.BANDCAMP
        is NavDestination.YouTubeHome -> BottomNavItem.YOUTUBE
        is NavDestination.Library -> BottomNavItem.LIBRARY
        is NavDestination.Settings -> BottomNavItem.SETTINGS
        else -> null
    }

    private fun refreshAllDestinations() {
        _allDestinations.value = buildSet {
            tabStacks.values.forEach { addAll(it) }
            addAll(_backStack.value)
        }
    }

    // --- process-death persistence ---------------------------------------

    /** Persist current tab, per-tab back stacks, and player expansion. */
    private fun persistState() {
        try {
            savedStateHandle[KEY_TAB] = _currentTab.value.name
            savedStateHandle[KEY_SHOW_FULL_PLAYER] = _showFullPlayer.value
            for ((tab, stack) in tabStacks) {
                savedStateHandle[stackKey(tab)] = ArrayList(stack.map { encodeDestination(it) })
            }
        } catch (_: Exception) {
            // Best-effort: never let persistence break navigation.
        }
    }

    private fun restoreSavedState() {
        val savedTab = savedStateHandle.get<String>(KEY_TAB) ?: return
        try {
            for (tab in BottomNavItem.entries) {
                val saved = savedStateHandle.get<ArrayList<String>>(stackKey(tab)) ?: continue
                val decoded = saved.mapNotNull { decodeDestination(it) }
                if (decoded.isNotEmpty()) {
                    // Keep MAX_STACK_DEPTH semantics on restore: preserve the
                    // home root, trim the oldest detail entries.
                    tabStacks[tab] = if (decoded.size > MAX_STACK_DEPTH) {
                        listOf(decoded.first()) + decoded.takeLast(MAX_STACK_DEPTH - 1)
                    } else {
                        decoded
                    }
                }
            }
            val tab = BottomNavItem.entries.firstOrNull { it.name == savedTab }
            if (tab != null) {
                _currentTab.value = tab
                _backStack.value = tabStacks[tab] ?: listOf(tab.destination)
            }
            _showFullPlayer.value = savedStateHandle.get<Boolean>(KEY_SHOW_FULL_PLAYER) ?: false
            refreshAllDestinations()
        } catch (_: Exception) {
            // Corrupt saved state: fall back to the default cold-start stacks.
        }
    }

    companion object {
        private const val TAG = "NavigationViewModel"
        private const val MAX_STACK_DEPTH = 20

        private const val KEY_TAB = "nav.tab"
        private const val KEY_SHOW_FULL_PLAYER = "nav.showFullPlayer"
        private fun stackKey(tab: BottomNavItem) = "nav.stack.${tab.name}"

        /** True only for input the user explicitly typed/pasted as a web URL. */
        private fun hasExplicitWebScheme(raw: String): Boolean {
            val trimmed = raw.trim()
            return trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true)
        }

        // NavDestination lives in :core:model, which does not apply the
        // kotlinx-serialization plugin; a hand-rolled compact string encoding
        // keeps that module untouched. Fields are URL-encoded so '|' is safe.
        internal fun encodeDestination(dest: NavDestination): String = when (dest) {
            NavDestination.LocalHome -> "local"
            NavDestination.BandcampHome -> "bandcampHome"
            NavDestination.YouTubeHome -> "youtubeHome"
            NavDestination.Library -> "library"
            NavDestination.Settings -> "settings"
            NavDestination.AccountLogin -> "accountLogin"
            NavDestination.YouTubeMusicLogin -> "ytmLogin"
            is NavDestination.AlbumDetail -> "album|" + enc(dest.url)
            is NavDestination.ArtistDetail ->
                "artist|" + enc(dest.url) + "|" + enc(dest.sourceId) + "|" + enc(dest.name.orEmpty()) + "|" + enc(dest.imageUrl.orEmpty())
            is NavDestination.PlaylistDetail -> "playlist|" + enc(dest.playlistId)
            is NavDestination.CollectionDetail -> "collection|" + enc(dest.url) + "|" + enc(dest.sourceId) + "|" + enc(dest.name)
        }

        internal fun decodeDestination(raw: String): NavDestination? = try {
            val parts = raw.split('|')
            when (parts[0]) {
                "local" -> NavDestination.LocalHome
                "bandcampHome" -> NavDestination.BandcampHome
                "youtubeHome" -> NavDestination.YouTubeHome
                "library" -> NavDestination.Library
                "settings" -> NavDestination.Settings
                "accountLogin" -> NavDestination.AccountLogin
                "ytmLogin" -> NavDestination.YouTubeMusicLogin
                "album" -> NavDestination.AlbumDetail(url = dec(parts[1]))
                "artist" -> NavDestination.ArtistDetail(
                    url = dec(parts[1]),
                    sourceId = dec(parts[2]),
                    name = dec(parts[3]).takeIf { it.isNotEmpty() },
                    imageUrl = dec(parts[4]).takeIf { it.isNotEmpty() },
                )
                "playlist" -> NavDestination.PlaylistDetail(playlistId = dec(parts[1]))
                "collection" -> NavDestination.CollectionDetail(
                    url = dec(parts[1]),
                    sourceId = dec(parts[2]),
                    name = dec(parts[3]),
                )
                else -> null
            }
        } catch (_: Exception) {
            null
        }

        private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
        private fun dec(s: String): String = URLDecoder.decode(s, "UTF-8")
    }
}
