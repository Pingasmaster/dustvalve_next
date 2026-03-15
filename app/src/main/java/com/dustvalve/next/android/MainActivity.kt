package com.dustvalve.next.android

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dustvalve.next.android.data.local.datastore.SettingsDataStore
import com.dustvalve.next.android.domain.repository.AccountRepository
import com.dustvalve.next.android.domain.repository.LocalMusicRepository
import com.dustvalve.next.android.ui.navigation.AppNavigation
import com.dustvalve.next.android.ui.navigation.BottomNavBar
import com.dustvalve.next.android.ui.navigation.BottomNavItem
import com.dustvalve.next.android.ui.navigation.NavDestination
import com.dustvalve.next.android.ui.navigation.NavigationViewModel
import com.dustvalve.next.android.ui.navigation.SideNavRail
import com.dustvalve.next.android.ui.screens.player.FullPlayer
import com.dustvalve.next.android.ui.screens.player.MiniPlayer
import com.dustvalve.next.android.ui.screens.player.PlayerViewModel
import com.dustvalve.next.android.ui.theme.DustvalveNextTheme
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleStartEffect
import dagger.hilt.android.AndroidEntryPoint
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var accountRepository: AccountRepository

    @Inject
    lateinit var settingsDataStore: SettingsDataStore

    @Inject
    lateinit var localMusicRepository: LocalMusicRepository

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Result not needed — media session works without it, just no notification */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()
        triggerLocalMusicRescanIfNeeded()
        setContent {
            // Combine theme flows into a single emission to avoid theme flash on cold start
            val themeConfig by remember {
                combine(
                    settingsDataStore.themeMode,
                    settingsDataStore.dynamicColor,
                    settingsDataStore.oledBlack,
                ) { mode, dynamic, oled ->
                    Triple(mode, dynamic, oled)
                }
            }.collectAsStateWithLifecycle(initialValue = null)

            val config = themeConfig
            if (config == null) return@setContent // Brief blank while DataStore loads (avoids theme flash)

            val darkTheme = when (config.first) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }
            DustvalveNextTheme(
                darkTheme = darkTheme,
                dynamicColor = config.second,
                oledBlack = config.third,
            ) {
                MainContent(accountRepository = accountRepository)
            }
        }
    }

    private fun triggerLocalMusicRescanIfNeeded() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                if (settingsDataStore.getLocalMusicEnabledSync() &&
                    settingsDataStore.getLocalMusicFolderUriSync() != null
                ) {
                    localMusicRepository.scan()
                }
            } catch (_: Exception) {
                // Best-effort foreground rescan
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MainContent(accountRepository: AccountRepository) {
    val playerViewModel: PlayerViewModel = hiltViewModel()
    val navViewModel: NavigationViewModel = hiltViewModel()
    val backStack by navViewModel.backStack.collectAsStateWithLifecycle()
    val showFullPlayer by navViewModel.showFullPlayer.collectAsStateWithLifecycle()
    val currentTab by navViewModel.currentTab.collectAsStateWithLifecycle()

    // Adaptive layout: use NavigationRail on screens >= 600dp wide
    val windowInfo = LocalWindowInfo.current
    val useNavRail = windowInfo.containerDpSize.width >= 600.dp

    // BackHandlers: later in composition = higher priority
    // Lowest priority: at root of non-HOME tab, switch to HOME instead of exiting
    BackHandler(enabled = !showFullPlayer && backStack.size <= 1 && currentTab != BottomNavItem.HOME) {
        navViewModel.navigateTo(NavDestination.Home)
    }
    BackHandler(enabled = !showFullPlayer && backStack.size > 1) {
        navViewModel.navigateBack()
    }
    // Highest priority: collapse full player
    BackHandler(enabled = showFullPlayer) {
        navViewModel.collapsePlayer()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (useNavRail) {
            // Tablet / large screen layout: NavigationRail on the left
            Row(modifier = Modifier.fillMaxSize()) {
                SideNavRail(
                    currentTab = currentTab,
                    onItemSelected = { dest -> navViewModel.navigateTo(dest) },
                )
                Scaffold(
                    bottomBar = {
                        MiniPlayer(
                            playerViewModel = playerViewModel,
                            onExpandClick = { navViewModel.expandPlayer() },
                        )
                    },
                ) { innerPadding ->
                    AppNavigation(
                        navViewModel = navViewModel,
                        playerViewModel = playerViewModel,
                        accountRepository = accountRepository,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                    )
                }
            }
        } else {
            // Compact layout: NavigationBar at the bottom
            Scaffold(
                bottomBar = {
                    Column {
                        MiniPlayer(
                            playerViewModel = playerViewModel,
                            onExpandClick = { navViewModel.expandPlayer() },
                        )
                        BottomNavBar(
                            currentTab = currentTab,
                            onItemSelected = { dest -> navViewModel.navigateTo(dest) },
                        )
                    }
                },
            ) { innerPadding ->
                AppNavigation(
                    navViewModel = navViewModel,
                    playerViewModel = playerViewModel,
                    accountRepository = accountRepository,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                )
            }
        }

        // Full-screen player expand/collapse uses slow spec
        val slideSpec = MaterialTheme.motionScheme.slowSpatialSpec<IntOffset>()
        AnimatedVisibility(
            visible = showFullPlayer,
            enter = slideInVertically(
                animationSpec = slideSpec,
                initialOffsetY = { it },
            ),
            exit = slideOutVertically(
                animationSpec = slideSpec,
                targetOffsetY = { it },
            ),
        ) {
            FullPlayer(
                playerViewModel = playerViewModel,
                onCollapse = { navViewModel.collapsePlayer() },
                onArtistClick = { url ->
                    navViewModel.collapsePlayer()
                    navViewModel.navigateTo(NavDestination.ArtistDetail(url))
                },
            )
        }
    }
}
