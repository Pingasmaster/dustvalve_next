package com.dustvalve.next.android.ui.navigation

import androidx.lifecycle.ViewModel
import com.dustvalve.next.android.util.NetworkUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class NavigationViewModel @Inject constructor() : ViewModel() {

    private val _currentTab = MutableStateFlow(BottomNavItem.HOME)
    val currentTab: StateFlow<BottomNavItem> = _currentTab.asStateFlow()

    private val tabStacks = java.util.concurrent.ConcurrentHashMap<BottomNavItem, List<NavDestination>>(
        mapOf(
            BottomNavItem.HOME to listOf(NavDestination.Home),
            BottomNavItem.LIBRARY to listOf(NavDestination.Library),
            BottomNavItem.SETTINGS to listOf(NavDestination.Settings),
        )
    )

    private val _backStack = MutableStateFlow<List<NavDestination>>(listOf(NavDestination.Home))
    val backStack: StateFlow<List<NavDestination>> = _backStack.asStateFlow()

    private val _lastNavigationForward = MutableStateFlow(true)
    val lastNavigationForward: StateFlow<Boolean> = _lastNavigationForward.asStateFlow()

    private val _showFullPlayer = MutableStateFlow(false)
    val showFullPlayer: StateFlow<Boolean> = _showFullPlayer.asStateFlow()

    fun navigateTo(dest: NavDestination) {
        val tab = tabForDestination(dest)
        if (tab != null) {
            // Tab navigation — determine animation direction from tab ordinal
            val currentOrdinal = _currentTab.value.ordinal
            val targetOrdinal = tab.ordinal
            if (tab == _currentTab.value) {
                // Popping to root — animate backward
                _lastNavigationForward.value = false
                // Tapping current tab: pop to root
                val rootStack = listOf(dest)
                tabStacks[tab] = rootStack
                _backStack.value = rootStack
            } else {
                _lastNavigationForward.value = targetOrdinal >= currentOrdinal
                // Save current tab's stack, switch to target tab
                tabStacks[_currentTab.value] = _backStack.value
                _currentTab.value = tab
                _backStack.value = tabStacks[tab] ?: listOf(dest)
            }
        } else {
            // Detail navigation — validate URLs
            when (dest) {
                is NavDestination.AlbumDetail ->
                    if (!NetworkUtils.isValidHttpsUrl(dest.url)) return
                is NavDestination.ArtistDetail ->
                    if (!NetworkUtils.isValidHttpsUrl(dest.url)) return
                else -> {}
            }
            val currentStack = _backStack.value
            // Dedup: don't push the same destination twice
            if (currentStack.lastOrNull() == dest) return
            // Cap stack depth
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

    fun expandPlayer() {
        _showFullPlayer.value = true
    }

    fun collapsePlayer() {
        _showFullPlayer.value = false
    }

    private fun tabForDestination(dest: NavDestination): BottomNavItem? = when (dest) {
        is NavDestination.Home -> BottomNavItem.HOME
        is NavDestination.Library -> BottomNavItem.LIBRARY
        is NavDestination.Settings -> BottomNavItem.SETTINGS
        else -> null
    }

    companion object {
        private const val MAX_STACK_DEPTH = 20
    }
}
