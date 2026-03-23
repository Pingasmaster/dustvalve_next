package com.dustvalve.next.android

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.dustvalve.next.android.domain.model.TrackSource
import com.dustvalve.next.android.ui.navigation.NavDestination
import com.dustvalve.next.android.ui.navigation.NavigationViewModel
import com.dustvalve.next.android.ui.navigation.SideNavRail
import com.dustvalve.next.android.ui.screens.player.FullPlayer
import com.dustvalve.next.android.ui.screens.player.MiniPlayer
import com.dustvalve.next.android.ui.screens.player.PlayerViewModel
import com.dustvalve.next.android.ui.update.UpdateDialog
import com.dustvalve.next.android.ui.update.UpdateViewModel
import com.dustvalve.next.android.ui.theme.AlbumThemeManager
import com.dustvalve.next.android.ui.theme.DustvalveNextTheme
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleStartEffect
import dagger.hilt.android.AndroidEntryPoint
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    @Inject
    lateinit var albumThemeManager: AlbumThemeManager

    private val _deepLinkUrl = MutableStateFlow<String?>(null)
    val deepLinkUrl: StateFlow<String?> = _deepLinkUrl.asStateFlow()

    fun consumeDeepLink() {
        _deepLinkUrl.value = null
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Result not needed — media session works without it, just no notification */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIncomingIntent(intent)
        requestNotificationPermissionIfNeeded()
        triggerLocalMusicRescanIfNeeded()
        setContent {
            // Combine theme flows into a single emission to avoid theme flash on cold start
            val themeConfig by remember {
                combine(
                    settingsDataStore.themeMode,
                    settingsDataStore.dynamicColor,
                    settingsDataStore.oledBlack,
                    albumThemeManager.albumSeedColor,
                ) { mode, dynamic, oled, seedColor ->
                    ThemeConfig(mode, dynamic, oled, seedColor)
                }
            }.collectAsStateWithLifecycle(initialValue = null)

            val config = themeConfig
            if (config == null) return@setContent // Brief blank while DataStore loads (avoids theme flash)

            val darkTheme = when (config.themeMode) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }
            DustvalveNextTheme(
                darkTheme = darkTheme,
                dynamicColor = config.dynamicColor,
                oledBlack = config.oledBlack,
                albumSeedColor = config.albumSeedColor,
            ) {
                MainContent(accountRepository = accountRepository, activity = this@MainActivity)
            }
        }
    }

    private fun triggerLocalMusicRescanIfNeeded() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                if (settingsDataStore.getLocalMusicEnabledSync() &&
                    (settingsDataStore.getLocalMusicUseMediaStoreSync() ||
                        settingsDataStore.getLocalMusicFolderUrisSync().isNotEmpty())
                ) {
                    localMusicRepository.scan()
                }
            } catch (_: Exception) {
                // Best-effort foreground rescan
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        val url = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data?.toString()
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()?.let { text ->
                Regex("https?://\\S+").find(text)?.value
            }
            else -> null
        }
        if (url != null) {
            _deepLinkUrl.value = url
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

private data class ThemeConfig(
    val themeMode: String,
    val dynamicColor: Boolean,
    val oledBlack: Boolean,
    val albumSeedColor: Color?,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MainContent(accountRepository: AccountRepository, activity: MainActivity) {
    val playerViewModel: PlayerViewModel = hiltViewModel()
    val navViewModel: NavigationViewModel = hiltViewModel()
    val updateViewModel: UpdateViewModel = hiltViewModel()
    val backStack by navViewModel.backStack.collectAsStateWithLifecycle()
    val showFullPlayer by navViewModel.showFullPlayer.collectAsStateWithLifecycle()
    val dragProgress by navViewModel.playerDragProgress.collectAsStateWithLifecycle()
    val currentTab by navViewModel.currentTab.collectAsStateWithLifecycle()
    val visibleTabs by navViewModel.visibleTabs.collectAsStateWithLifecycle()
    val updateState by updateViewModel.uiState.collectAsStateWithLifecycle()

    // Auto-update check
    LaunchedEffect(Unit) {
        updateViewModel.checkForUpdate()
    }

    if (updateState.showDialog) {
        UpdateDialog(
            state = updateState,
            onDismiss = { updateViewModel.dismissDialog() },
            onDownload = { updateViewModel.startDownload() },
            onInstall = { updateViewModel.installApk() },
        )
    }

    // Deep link handling
    val deepLinkUrl by activity.deepLinkUrl.collectAsStateWithLifecycle()
    val deepLinkTrack by navViewModel.deepLinkTrack.collectAsStateWithLifecycle()

    LaunchedEffect(deepLinkUrl) {
        val url = deepLinkUrl ?: return@LaunchedEffect
        activity.consumeDeepLink()
        navViewModel.handleDeepLink(url)
    }

    LaunchedEffect(deepLinkTrack) {
        val track = deepLinkTrack ?: return@LaunchedEffect
        navViewModel.consumeDeepLinkTrack()
        playerViewModel.playTrack(track)
    }

    // Adaptive layout: use NavigationRail on screens >= 600dp wide
    val windowInfo = LocalWindowInfo.current
    val useNavRail = windowInfo.containerDpSize.width >= 600.dp

    // BackHandlers: later in composition = higher priority
    // Lowest priority: at root of non-LOCAL tab, switch to LOCAL instead of exiting
    BackHandler(enabled = !showFullPlayer && backStack.size <= 1 && currentTab != BottomNavItem.LOCAL) {
        navViewModel.navigateTo(NavDestination.LocalHome)
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
                    visibleTabs = visibleTabs,
                    onItemSelected = { dest -> navViewModel.navigateTo(dest) },
                )
                Scaffold(
                    bottomBar = {
                        MiniPlayer(
                            playerViewModel = playerViewModel,
                            onExpandClick = { navViewModel.expandPlayer() },
                            onDragUpProgress = { navViewModel.setPlayerDragProgress(it) },
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
                            onDragUpProgress = { navViewModel.setPlayerDragProgress(it) },
                        )
                        BottomNavBar(
                            currentTab = currentTab,
                            visibleTabs = visibleTabs,
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

        // Full-screen player: offset-driven for swipe preview
        val expandAnimatable = remember { Animatable(0f) }
        val expandSpec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
        val collapseSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()

        LaunchedEffect(showFullPlayer) {
            if (showFullPlayer) {
                expandAnimatable.animateTo(1f, expandSpec)
            } else {
                expandAnimatable.animateTo(0f, collapseSpec)
            }
        }

        val effectiveProgress = maxOf(expandAnimatable.value, dragProgress)

        if (effectiveProgress > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationY = size.height * (1f - effectiveProgress)
                    },
            ) {
                FullPlayer(
                    playerViewModel = playerViewModel,
                    onCollapse = { navViewModel.collapsePlayer() },
                    onArtistClick = { track ->
                        navViewModel.collapsePlayer()
                        when {
                            track.isLocal -> navViewModel.requestLocalArtistFilter(track.artist)
                            track.source == TrackSource.YOUTUBE -> navViewModel.navigateTo(
                                NavDestination.YouTubeArtistDetail(
                                    url = track.artistUrl,
                                    name = track.artist,
                                    imageUrl = null,
                                )
                            )
                            else -> navViewModel.navigateTo(NavDestination.ArtistDetail(track.artistUrl))
                        }
                    },
                )
            }
        }
    }
}
