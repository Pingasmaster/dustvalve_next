package com.dustvalve.next.android

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
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
import androidx.compose.runtime.DisposableEffect
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
import com.dustvalve.next.android.data.storage.folder.FolderHealthChecker
import com.dustvalve.next.android.data.storage.folder.FolderMirror
import com.dustvalve.next.android.data.storage.folder.FolderRehydrator
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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

    @Inject
    lateinit var folderHealthChecker: FolderHealthChecker

    @Inject
    lateinit var folderRehydrator: FolderRehydrator

    @Inject
    lateinit var folderMirror: FolderMirror

    @Inject
    lateinit var appUpdateController: com.dustvalve.next.android.update.AppUpdateController

    sealed interface BootState {
        object Loading : BootState
        object Ready : BootState
        object DedicatedFolderUnreachable : BootState
    }

    private val _bootState = MutableStateFlow<BootState>(BootState.Loading)
    val bootState: StateFlow<BootState> = _bootState.asStateFlow()

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
        bootstrapDedicatedFolderIfNeeded()
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
            // Host the self-update dialog at the theme scope so it surfaces
            // from any screen when the cold-start silent check (or an in-flight
            // download kicked off elsewhere) transitions state to Available /
            // Downloading. Idle / Checking render nothing.
            val updateState by appUpdateController.state.collectAsStateWithLifecycle()
            DustvalveNextTheme(
                darkTheme = darkTheme,
                dynamicColor = config.dynamicColor,
                oledBlack = config.oledBlack,
                albumSeedColor = config.albumSeedColor,
            ) {
                val boot by bootState.collectAsStateWithLifecycle()
                when (boot) {
                    BootState.Loading -> com.dustvalve.next.android.ui.components.DedicatedFolderBootLoading()
                    BootState.DedicatedFolderUnreachable -> com.dustvalve.next.android.ui.screens.folder.DedicatedFolderErrorScreen(
                        onLocateFolder = { uri ->
                            lifecycleScope.launch(Dispatchers.IO) {
                                try {
                                    val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                                    try { contentResolver.takePersistableUriPermission(uri, flags) } catch (_: Exception) {}
                                    settingsDataStore.setDedicatedFolder(enabled = true, treeUri = uri.toString())
                                    folderMirror.suspendFor(5_000L)
                                    folderRehydrator.rehydrateAll()
                                    clearDedicatedFolderError()
                                } catch (_: Exception) {
                                    // Stay on error screen if re-pick fails.
                                }
                            }
                        },
                        onTurnOff = {
                            lifecycleScope.launch(Dispatchers.IO) {
                                try {
                                    settingsDataStore.setDedicatedFolder(enabled = false, treeUri = null)
                                    settingsDataStore.setDedicatedFolderIncludeImageCache(false)
                                    settingsDataStore.setDedicatedFolderIncludeMetadataCache(false)
                                } catch (_: Exception) {}
                                clearDedicatedFolderError()
                            }
                        },
                    )
                    BootState.Ready -> MainContent(accountRepository = accountRepository, activity = this@MainActivity)
                }

                // Pre-alpha nag: the cold-start silent check (fired from
                // Application.onCreate) may have populated this while the
                // user is on any screen. The dialog is a no-op on Idle /
                // Checking so it's safe to host unconditionally.
                com.dustvalve.next.android.ui.components.update.AppUpdateDialog(
                    state = updateState,
                    onConfirmDownload = { appUpdateController.confirmDownload() },
                    onDismiss = { appUpdateController.dismiss() },
                )
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

    private fun bootstrapDedicatedFolderIfNeeded() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val enabled = settingsDataStore.getDedicatedFolderEnabledSync()
                if (!enabled) {
                    _bootState.value = BootState.Ready
                    return@launch
                }
                if (!folderHealthChecker.check()) {
                    _bootState.value = BootState.DedicatedFolderUnreachable
                    return@launch
                }
                // Suspend the mirror while we overwrite Room + DataStore so its
                // Flow collectors don't kick in and re-flush stale data.
                folderMirror.suspendFor(5_000L)
                folderRehydrator.rehydrateAll()
                _bootState.value = BootState.Ready
            } catch (_: Exception) {
                _bootState.value = BootState.DedicatedFolderUnreachable
            }
        }
    }

    fun clearDedicatedFolderError() {
        _bootState.value = BootState.Ready
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
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
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
    val backStack by navViewModel.backStack.collectAsStateWithLifecycle()
    val showFullPlayer by navViewModel.showFullPlayer.collectAsStateWithLifecycle()
    val dragProgress by navViewModel.playerDragProgress.collectAsStateWithLifecycle()
    val currentTab by navViewModel.currentTab.collectAsStateWithLifecycle()
    val visibleTabs by navViewModel.visibleTabs.collectAsStateWithLifecycle()

    // Screen wake lock
    val isPlaying by remember {
        playerViewModel.uiState.map { it.isPlaying }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = false)
    val keepScreenOnInApp by remember {
        activity.settingsDataStore.keepScreenOnInApp
    }.collectAsStateWithLifecycle(initialValue = false)
    val keepScreenOnWhilePlaying by remember {
        activity.settingsDataStore.keepScreenOnWhilePlaying
    }.collectAsStateWithLifecycle(initialValue = false)

    // keepScreenOnWhilePlaying is a sub-toggle of keepScreenOnInApp:
    // - parent off            → never keep screen on
    // - parent on, sub off    → screen on whenever the app is open
    // - parent on, sub on     → screen on only while the app is open AND playing
    val shouldKeepScreenOn = keepScreenOnInApp && (!keepScreenOnWhilePlaying || isPlaying)

    DisposableEffect(shouldKeepScreenOn) {
        if (shouldKeepScreenOn) {
            activity.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            activity.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Update checks are now opt-in from Settings → About → "Search for updates"
    // (Pre-alpha: no auto-update at startup.)

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
                                NavDestination.ArtistDetail(
                                    url = track.artistUrl,
                                    sourceId = "youtube",
                                    name = track.artist,
                                    imageUrl = null,
                                )
                            )
                            else -> navViewModel.navigateTo(NavDestination.ArtistDetail(track.artistUrl))
                        }
                    },
                    onAlbumClick = { track ->
                        if (track.albumUrl.isNotBlank()) {
                            navViewModel.collapsePlayer()
                            navViewModel.navigateTo(NavDestination.AlbumDetail(track.albumUrl))
                        }
                    },
                )
            }
        }
    }
}
