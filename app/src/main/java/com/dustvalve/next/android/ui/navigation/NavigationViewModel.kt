package com.dustvalve.next.android.ui.navigation

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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A compatible link whose provider is disabled — drives the "enable provider?" dialog. */
data class PendingLink(val provider: MusicProvider, val type: LinkResourceType, val action: DeepLinkAction)

@HiltViewModel
class NavigationViewModel @Inject constructor(
    private val providerStateUseCase: ProviderStateUseCase,
    private val youtubeRepository: YouTubeRepository,
    private val bandcampDomainSniffer: BandcampDomainSniffer,
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

    init {
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
     * Open a pasted/external link. Resolves it offline first, then (only if it looks like a URL)
     * via a single network sniff for custom-domain Bandcamp pages. If it targets a disabled
     * provider, surfaces the enable dialog; otherwise executes immediately. Unsupported input
     * raises an [unsupportedLinkEvents] notice.
     */
    fun openLink(raw: String) {
        viewModelScope.launch {
            val detected = DeepLinkRouter.detect(raw)
                ?: if (DeepLinkRouter.looksLikeUrl(raw)) bandcampDomainSniffer.sniff(raw) else null
            if (detected == null) {
                _unsupportedLinkEvents.tryEmit(Unit)
                return@launch
            }
            if (detected.provider !in _activeProviders.value) {
                _pendingLinkConfirmation.value =
                    PendingLink(detected.provider, detected.type, detected.action)
            } else {
                execute(detected.action)
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
                        // Track resolution failed — silently ignore
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
                currentStack.drop(1) + dest
            } else {
                currentStack + dest
            }
            _lastNavigationForward.value = true
            _backStack.value = newStack
            tabStacks[_currentTab.value] = newStack
        }
    }

    fun navigateBack(): Boolean {
        val current = _backStack.value
        return if (current.size > 1) {
            _lastNavigationForward.value = false
            val newStack = current.dropLast(1)
            _backStack.value = newStack
            tabStacks[_currentTab.value] = newStack
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
    }

    fun collapsePlayer() {
        _showFullPlayer.value = false
    }

    private fun tabForDestination(dest: NavDestination): BottomNavItem? = when (dest) {
        is NavDestination.LocalHome -> BottomNavItem.LOCAL
        is NavDestination.BandcampHome -> BottomNavItem.BANDCAMP
        is NavDestination.YouTubeHome -> BottomNavItem.YOUTUBE
        is NavDestination.Library -> BottomNavItem.LIBRARY
        is NavDestination.Settings -> BottomNavItem.SETTINGS
        else -> null
    }

    companion object {
        private const val MAX_STACK_DEPTH = 20
    }
}
