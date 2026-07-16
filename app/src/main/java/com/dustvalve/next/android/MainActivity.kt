package com.dustvalve.next.android

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.dustvalve.next.android.data.local.datastore.SettingsDataStore
import com.dustvalve.next.android.data.storage.folder.FolderHealthChecker
import com.dustvalve.next.android.data.storage.folder.FolderMirror
import com.dustvalve.next.android.data.storage.folder.FolderRehydrator
import com.dustvalve.next.android.di.qualifiers.AppDispatchers
import com.dustvalve.next.android.di.qualifiers.Dispatcher
import com.dustvalve.next.android.domain.model.TrackSource
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
import com.dustvalve.next.android.ui.theme.AlbumThemeManager
import com.dustvalve.next.android.ui.theme.DustvalveNextTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

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

    @Inject
    lateinit var crashReportManager: com.dustvalve.next.android.crash.CrashReportManager

    @Inject
    @Dispatcher(AppDispatchers.IO)
    lateinit var ioDispatcher: CoroutineDispatcher

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
        ActivityResultContracts.RequestPermission(),
    ) { /* Result not needed - media session works without it, just no notification */ }

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
                            lifecycleScope.launch(ioDispatcher) {
                                try {
                                    val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                                    try {
                                        contentResolver.takePersistableUriPermission(uri, flags)
                                    } catch (_: Exception) {}
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
                            lifecycleScope.launch(ioDispatcher) {
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

                // Post-crash prompt: only appears when the previous process
                // died from a crash / ANR (never a user force-close). All
                // sharing is opt-in; dismissing deletes the stored log.
                val crashPromptState by crashReportManager.state.collectAsStateWithLifecycle()
                com.dustvalve.next.android.ui.components.crash.CrashReportSheet(
                    state = crashPromptState,
                    onShareLog = { crashReportManager.sharePendingLog(this@MainActivity) },
                    onOpenIssue = { crashReportManager.openGitHubIssue(this@MainActivity) },
                    onDismiss = { crashReportManager.dismiss() },
                )
            }
        }
    }

    private fun triggerLocalMusicRescanIfNeeded() {
        lifecycleScope.launch(ioDispatcher) {
            try {
                if (settingsDataStore.getLocalMusicEnabledSync() &&
                    (
                        settingsDataStore.getLocalMusicUseMediaStoreSync() ||
                            settingsDataStore.getLocalMusicFolderUrisSync().isNotEmpty()
                        )
                ) {
                    localMusicRepository.scan()
                }
            } catch (_: Exception) {
                // Best-effort foreground rescan
            }
        }
    }

    private fun bootstrapDedicatedFolderIfNeeded() {
        lifecycleScope.launch(ioDispatcher) {
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
        // Legacy branch: POST_NOTIFICATIONS only exists on API 33+; below that
        // notifications are granted at install time and the prompt is a no-op.
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

private data class ThemeConfig(val themeMode: String, val dynamicColor: Boolean, val oledBlack: Boolean, val albumSeedColor: Color?)

/**
 * Collapsed mini-bar height (64.dp row + 2.dp progress strip). Used to reserve
 * the docked slot above the bottom nav and to position the mini state of the
 * mini <-> full container transform.
 */
private val MINI_BAR_HEIGHT = 66.dp

// LongMethod: MainContent is the composition root branching on adaptive width
// + expanded/collapsed player + bottom-nav placement; extracting tiny helpers
// just to chase a line-count threshold would split related layout decisions.
//
// ViewModelForwarding is no longer suppressed: MiniPlayer, FullPlayer, and
// AppNavigation now obtain PlayerViewModel / NavigationViewModel via their
// own `hiltViewModel()` default param. Because MainActivity is the
// ViewModelStoreOwner, every hiltViewModel<PlayerViewModel>() /
// hiltViewModel<NavigationViewModel>() call from this composition returns
// the SAME activity-scoped instance - no VM is passed as a parameter and
// the lint rule no longer fires.
//
// CyclomaticComplexMethod: the complexity is intrinsic to this orchestration
// root (adaptive width branch + the mini<->full seekable container transform +
// the artist/album navigation when-branches); extracting it would only relocate
// the same branching, so it is suppressed alongside LongMethod.
@Composable
@Suppress("LongMethod", "CyclomaticComplexMethod")
private fun MainContent(
    accountRepository: AccountRepository,
    activity: MainActivity,
    playerViewModel: PlayerViewModel = hiltViewModel(),
    navViewModel: NavigationViewModel = hiltViewModel(),
) {
    val backStack by navViewModel.backStack.collectAsStateWithLifecycle()
    val showFullPlayer by navViewModel.showFullPlayer.collectAsStateWithLifecycle()
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
    // - parent off            -> never keep screen on
    // - parent on, sub off    -> screen on whenever the app is open
    // - parent on, sub on     -> screen on only while the app is open AND playing
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

    // The self-update flow has two entry points: a silent cold-start check
    // (DustvalveNextApplication.onCreate -> AppUpdateController.checkSilently,
    // gated by the "Automatic update checks" toggle in Settings -> About) and
    // the manual "Search for updates" button. Both feed the dialog hosted above.

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

    val miniVisible by remember {
        playerViewModel.uiState.map { it.isMiniPlayerVisible }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = false)

    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    // Single source of truth for the mini <-> full container transform: a
    // seekable transition whose fraction (0 = mini, 1 = full) is driven
    // continuously by the drag gestures and the predictive-back gesture, and
    // settled with velocity on release. No more maxOf() of two systems.
    val seekState = remember { SeekableTransitionState(false) }
    val playerTransition = rememberTransition(seekState, label = "player")

    var containerHeightPx by remember { mutableFloatStateOf(1f) }
    var bottomBarHeightPx by remember { mutableIntStateOf(0) }
    val miniBarHeightPx = with(density) { MINI_BAR_HEIGHT.toPx() }
    val expandDistancePx = containerHeightPx

    // Gesture seek state (serialized through a single collector to avoid
    // racing concurrent seekTo calls).
    var seekFraction by remember { mutableFloatStateOf(0f) }
    var seekTargetFull by remember { mutableStateOf(true) }
    var isSeekingDrag by remember { mutableStateOf(false) }

    // External / committed transitions (tap, chevron, programmatic collapse on
    // artist/album navigation, and the gesture commits below) animate the morph.
    LaunchedEffect(showFullPlayer) {
        seekState.animateTo(showFullPlayer)
    }
    LaunchedEffect(Unit) {
        snapshotFlow { if (isSeekingDrag) seekFraction else -1f }
            .collect { f -> if (f >= 0f) seekState.seekTo(f.coerceIn(0f, 1f), targetState = seekTargetFull) }
    }

    val onExpandSeek: (Float) -> Unit = { f ->
        seekTargetFull = true
        seekFraction = f
        isSeekingDrag = f > 0f
    }
    val onExpandSettle: (Float) -> Unit = { velFrac ->
        isSeekingDrag = false
        if (seekFraction > 0.5f || velFrac > 0.8f) {
            navViewModel.expandPlayer()
        } else {
            scope.launch { seekState.animateTo(false) }
        }
    }
    val onCollapseSeek: (Float) -> Unit = { g ->
        seekTargetFull = false
        seekFraction = g
        isSeekingDrag = g > 0f
    }
    val onCollapseSettle: (Float) -> Unit = { velocityY ->
        isSeekingDrag = false
        if (seekFraction > 0.5f || velocityY > 1200f) {
            navViewModel.collapsePlayer()
        } else {
            scope.launch { seekState.animateTo(true) }
        }
    }

    // BackHandlers: later in composition = higher priority
    // Lowest priority: at root of non-LOCAL tab, switch to LOCAL instead of exiting
    BackHandler(enabled = !showFullPlayer && backStack.size <= 1 && currentTab != BottomNavItem.LOCAL) {
        navViewModel.navigateTo(NavDestination.LocalHome)
    }
    BackHandler(enabled = !showFullPlayer && backStack.size > 1) {
        navViewModel.navigateBack()
    }
    // Highest priority: predictive-back scrubs the collapse with a live preview,
    // commits on release, and springs back to full if the gesture is cancelled.
    PredictiveBackHandler(enabled = showFullPlayer) { progress ->
        try {
            progress.collect { backEvent ->
                seekState.seekTo(backEvent.progress.coerceIn(0f, 1f), targetState = false)
            }
            navViewModel.collapsePlayer()
        } catch (_: CancellationException) {
            seekState.animateTo(true)
        }
    }

    SharedTransitionLayout(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { containerHeightPx = it.height.toFloat() },
    ) {
        val sts = this
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
                            // Reserve the mini-bar slot; the bar itself renders in
                            // the shared-transition overlay so it can morph.
                            if (miniVisible) {
                                Spacer(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(MINI_BAR_HEIGHT)
                                        .onSizeChanged { bottomBarHeightPx = it.height },
                                )
                            }
                        },
                    ) { innerPadding ->
                        AppNavigation(
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
                        Column(modifier = Modifier.onSizeChanged { bottomBarHeightPx = it.height }) {
                            if (miniVisible) {
                                Spacer(modifier = Modifier.fillMaxWidth().height(MINI_BAR_HEIGHT))
                            }
                            BottomNavBar(
                                currentTab = currentTab,
                                visibleTabs = visibleTabs,
                                onItemSelected = { dest -> navViewModel.navigateTo(dest) },
                            )
                        }
                    },
                ) { innerPadding ->
                    AppNavigation(
                        accountRepository = accountRepository,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                    )
                }
            }

            // Player surface: the mini bar and full player are the two states of
            // one container transform. The transparent host Box does not consume
            // touches, so taps outside the docked mini bar reach the app behind it.
            if (miniVisible) {
                val dockDp = with(density) {
                    (bottomBarHeightPx - miniBarHeightPx).coerceAtLeast(0f).toDp()
                }
                playerTransition.AnimatedContent(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    contentAlignment = Alignment.BottomCenter,
                ) { isFull ->
                    val avScope = this
                    if (isFull) {
                        FullPlayer(
                            sharedScope = sts,
                            visScope = avScope,
                            expandDistancePx = expandDistancePx,
                            onCollapse = { navViewModel.collapsePlayer() },
                            onCollapseSeek = onCollapseSeek,
                            onCollapseSettle = onCollapseSettle,
                            modifier = Modifier.fillMaxSize(),
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
                                        ),
                                    )

                                    else -> navViewModel.navigateTo(NavDestination.ArtistDetail(track.artistUrl))
                                }
                            },
                            onAlbumClick = { track ->
                                when {
                                    track.source == TrackSource.YOUTUBE -> {
                                        if (track.albumUrl.isNotBlank()) {
                                            navViewModel.collapsePlayer()
                                            navViewModel.navigateTo(
                                                NavDestination.CollectionDetail(
                                                    url = track.albumUrl,
                                                    sourceId = "youtube",
                                                    name = track.albumTitle,
                                                ),
                                            )
                                        } else {
                                            // Pre-fetch already ran (albumLookupDone=true);
                                            // empty means the video has no YTM album.
                                            playerViewModel.showNoAlbumSnackbar()
                                        }
                                    }

                                    track.albumUrl.isNotBlank() -> {
                                        navViewModel.collapsePlayer()
                                        navViewModel.navigateTo(NavDestination.AlbumDetail(track.albumUrl))
                                    }
                                }
                            },
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = dockDp),
                        ) {
                            MiniPlayer(
                                sharedScope = sts,
                                visScope = avScope,
                                expandDistancePx = expandDistancePx,
                                onExpandClick = { navViewModel.expandPlayer() },
                                onExpandSeek = onExpandSeek,
                                onExpandSettle = onExpandSettle,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }
}
