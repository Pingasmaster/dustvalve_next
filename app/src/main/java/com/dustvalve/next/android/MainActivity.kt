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
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.rememberTransition
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.dustvalve.next.android.data.local.datastore.SettingsDataStore
import com.dustvalve.next.android.di.qualifiers.AppDispatchers
import com.dustvalve.next.android.di.qualifiers.Dispatcher
import com.dustvalve.next.android.domain.model.TrackSource
import com.dustvalve.next.android.domain.repository.LocalMusicRepository
import com.dustvalve.next.android.ui.adaptive.AdaptiveLayoutInfo
import com.dustvalve.next.android.ui.adaptive.rememberAdaptiveLayoutInfo
import com.dustvalve.next.android.ui.navigation.AppNavigation
import com.dustvalve.next.android.ui.navigation.BottomNavBar
import com.dustvalve.next.android.ui.navigation.BottomNavItem
import com.dustvalve.next.android.ui.navigation.NavDestination
import com.dustvalve.next.android.ui.navigation.NavigationViewModel
import com.dustvalve.next.android.ui.navigation.SideNavRail
import com.dustvalve.next.android.ui.navigation.isSoundCloudCollectionUrl
import com.dustvalve.next.android.ui.screens.player.FullPlayer
import com.dustvalve.next.android.ui.screens.player.MiniPlayer
import com.dustvalve.next.android.ui.screens.player.PlayerViewModel
import com.dustvalve.next.android.ui.screens.player.clearSnackbar
import com.dustvalve.next.android.ui.screens.player.playTrack
import com.dustvalve.next.android.ui.screens.player.showNoAlbumSnackbar
import com.dustvalve.next.android.ui.theme.AlbumThemeManager
import com.dustvalve.next.android.ui.theme.DustvalveNextTheme
import com.dustvalve.next.android.util.isAtLeastTiramisu
import com.dustvalve.next.android.util.legacyAudioPermission
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
    lateinit var settingsDataStore: SettingsDataStore

    @Inject
    lateinit var localMusicRepository: LocalMusicRepository

    @Inject
    lateinit var albumThemeManager: AlbumThemeManager

    @Inject
    lateinit var appUpdateController: com.dustvalve.next.android.update.AppUpdateController

    @Inject
    lateinit var crashReportManager: com.dustvalve.next.android.crash.CrashReportManager

    @Inject
    @Dispatcher(AppDispatchers.IO)
    lateinit var ioDispatcher: CoroutineDispatcher

    private val _deepLinkUrl = MutableStateFlow<String?>(null)
    val deepLinkUrl: StateFlow<String?> = _deepLinkUrl.asStateFlow()

    fun consumeDeepLink() {
        _deepLinkUrl.value = null
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* Granted: media session notification works. Denied: Settings offers a deep link when permanently blocked. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Only on a genuinely fresh launch: on recreation (rotation, theme
        // change) the OS redelivers the ORIGINAL VIEW/SEND intent, but the
        // previous instance already consumed it - re-handling would renavigate
        // and restart playback of the linked track. New links while alive
        // arrive via onNewIntent.
        if (savedInstanceState == null) {
            handleIncomingIntent(intent)
        }
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
                MainContent(activity = this@MainActivity)

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
        // Once per process: a rotation must not re-walk MediaStore/SAF.
        if (!localRescanTriggered.compareAndSet(false, true)) return
        lifecycleScope.launch(ioDispatcher) {
            try {
                if (settingsDataStore.getLocalMusicEnabledSync() &&
                    (
                        settingsDataStore.getLocalMusicUseMediaStoreSync() ||
                            settingsDataStore.getLocalMusicFolderUrisSync().isNotEmpty()
                        )
                ) {
                    if (settingsDataStore.getLocalMusicUseMediaStoreSync() &&
                        ContextCompat.checkSelfPermission(this@MainActivity, legacyAudioPermission())
                        != PackageManager.PERMISSION_GRANTED
                    ) {
                        return@launch
                    }
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

    override fun onResume() {
        super.onResume()
        // After unknown-sources grant, retry install of the already-downloaded APK.
        appUpdateController.retryPendingInstallIfReady()
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
        // POST_NOTIFICATIONS only exists on API 33+; below that notifications
        // are granted at install time and the runtime prompt is a no-op.
        // Flavor-safe gate: required for compat (minSdk 26), always true on future.
        if (!isAtLeastTiramisu()) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        // Remember we asked so a permanent denial (rationale == false after a
        // prior prompt) does not re-spam the system dialog on every cold start.
        // Settings -> Storage still deep-links into app notification settings.
        val prefs = getPreferences(MODE_PRIVATE)
        val asked = prefs.getBoolean(PREF_NOTIFICATION_PERMISSION_ASKED, false)
        if (!asked) {
            prefs.edit().putBoolean(PREF_NOTIFICATION_PERMISSION_ASKED, true).apply()
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private companion object {
        const val PREF_NOTIFICATION_PERMISSION_ASKED = "notification_permission_asked"

        /**
         * Process-wide guard so Activity recreation (rotation, theme change)
         * never re-runs the local-music rescan. A ViewModel would only survive
         * configuration changes for one Activity; this flag also covers a
         * finished Activity being relaunched in a warm process.
         */
        val localRescanTriggered = java.util.concurrent.atomic.AtomicBoolean(false)
    }
}

private data class ThemeConfig(val themeMode: String, val dynamicColor: Boolean, val oledBlack: Boolean, val albumSeedColor: Color?)

/**
 * Collapsed mini-bar height (64.dp row + 2.dp progress strip). Used to reserve
 * the docked slot above the bottom nav and to position the mini state of the
 * mini <-> full container transform.
 */
private val MINI_BAR_HEIGHT = 66.dp

@Composable
private fun MainContent(activity: MainActivity) {
    val adaptiveInfo = rememberAdaptiveLayoutInfo()
    MainContentBody(
        activity = activity,
        adaptiveInfo = adaptiveInfo,
    )
}

@Composable
private fun MainContentBody(activity: MainActivity, adaptiveInfo: AdaptiveLayoutInfo) {
    MainContentKeepScreenOn(activity = activity)
    MainContentDeepLinks(activity = activity)
    MainContentPlayerChrome(adaptiveInfo = adaptiveInfo)
}

@Composable
private fun MainContentPlayerChrome(
    adaptiveInfo: AdaptiveLayoutInfo,
    playerViewModel: PlayerViewModel = hiltViewModel(),
    navViewModel: NavigationViewModel = hiltViewModel(),
) {
    val backStack by navViewModel.backStack.collectAsStateWithLifecycle()
    val showFullPlayer by navViewModel.showFullPlayer.collectAsStateWithLifecycle()
    val currentTab by navViewModel.currentTab.collectAsStateWithLifecycle()
    val visibleTabs by navViewModel.visibleTabs.collectAsStateWithLifecycle()
    val miniVisible by remember {
        playerViewModel.uiState.map { it.isMiniPlayerVisible }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = false)

    // Player snackbars (play-next / added-to-playlist confirmations, playback
    // errors) were historically rendered ONLY inside FullPlayer, so a message
    // raised from a browse screen while the full player was closed showed
    // nothing - and then popped up stale the next time the player opened.
    // This host renders them globally whenever the full player is NOT on
    // screen; FullPlayer keeps its own host for anchoring above its controls.
    val globalSnackbarHostState = remember { SnackbarHostState() }
    val playerSnackbarMessage by remember {
        playerViewModel.uiState.map { it.snackbarMessage }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = null)
    val globalSnackbarText = if (!showFullPlayer) playerSnackbarMessage?.asString() else null
    LaunchedEffect(globalSnackbarText) {
        globalSnackbarText?.let { message ->
            try {
                globalSnackbarHostState.showSnackbar(message)
            } finally {
                playerViewModel.clearSnackbar()
            }
        }
    }

    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    // Single source of truth for the mini <-> full container transform: a
    // seekable transition whose fraction (0 = mini, 1 = full) is driven
    // continuously by the drag gestures and the predictive-back gesture, and
    // settled with velocity on release. No more maxOf() of two systems.
    val seekState = remember { SeekableTransitionState(false) }
    val playerTransition = rememberTransition(seekState, label = "player")

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

    // Do not key remember() on ViewModels: compose-lints DataFlowAnalyzer would
    // treat the remember result (and anything built from it) as a forwarded
    // ViewModel and flag MainContentSharedHost(session). Activity-scoped VMs
    // are stable for this composition, so plain lambdas are enough.
    val navActions = remember {
        MainNavActions(
            navigateTo = { dest -> navViewModel.navigateTo(dest) },
            navigateBack = { navViewModel.navigateBack() },
            expandPlayer = { navViewModel.expandPlayer() },
            collapsePlayer = { navViewModel.collapsePlayer() },
            requestLocalArtistFilter = { artist -> navViewModel.requestLocalArtistFilter(artist) },
        )
    }
    val playerActions = remember {
        MainPlayerActions(showNoAlbumSnackbar = { playerViewModel.showNoAlbumSnackbar() })
    }

    val session = MainPlayerSession(
        adaptiveInfo = adaptiveInfo,
        chrome = MainPlayerChromeState(
            backStackSize = backStack.size,
            showFullPlayer = showFullPlayer,
            currentTab = currentTab,
            visibleTabs = visibleTabs,
            miniVisible = miniVisible,
        ),
        gesture = MainPlayerGestureState(
            seekState = seekState,
            playerTransition = playerTransition,
            onExpandSeek = onExpandSeek,
            onExpandSettle = onExpandSettle,
            onCollapseSeek = onCollapseSeek,
            onCollapseSettle = onCollapseSettle,
        ),
        nav = navActions,
        player = playerActions,
        globalSnackbarHostState = globalSnackbarHostState,
        density = density,
    )
    // Keep mutable height fields in sync: SharedHost writes session.containerHeightPx
    MainContentSharedHost(session = session)
}

@Stable
private class MainNavActions(
    val navigateTo: (NavDestination) -> Unit,
    val navigateBack: () -> Unit,
    val expandPlayer: () -> Unit,
    val collapsePlayer: () -> Unit,
    val requestLocalArtistFilter: (String) -> Unit,
)

@Stable
private class MainPlayerActions(val showNoAlbumSnackbar: () -> Unit)

@Stable
private class MainPlayerChromeState(
    val backStackSize: Int,
    val showFullPlayer: Boolean,
    val currentTab: BottomNavItem,
    val visibleTabs: List<BottomNavItem>,
    val miniVisible: Boolean,
)

@Stable
private class MainPlayerGestureState(
    val seekState: SeekableTransitionState<Boolean>,
    val playerTransition: Transition<Boolean>,
    val onExpandSeek: (Float) -> Unit,
    val onExpandSettle: (Float) -> Unit,
    val onCollapseSeek: (Float) -> Unit,
    val onCollapseSettle: (Float) -> Unit,
)

@Stable
private class MainPlayerSession(
    val adaptiveInfo: AdaptiveLayoutInfo,
    val chrome: MainPlayerChromeState,
    val gesture: MainPlayerGestureState,
    val nav: MainNavActions,
    val player: MainPlayerActions,
    val globalSnackbarHostState: SnackbarHostState,
    val density: Density,
) {
    var containerHeightPx by mutableFloatStateOf(1f)
    var bottomBarHeightPx by mutableIntStateOf(0)
    val expandDistancePx: Float get() = containerHeightPx
    val miniBarHeightPx: Float get() = with(density) { MINI_BAR_HEIGHT.toPx() }
}

@Composable
private fun MainContentSharedHost(session: MainPlayerSession) {
    SharedTransitionLayout(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { session.containerHeightPx = it.height.toFloat() },
    ) {
        val sts = this
        Box(
            modifier = Modifier
                .fillMaxSize()
                // Expose compose testTags as resource-ids so UiAutomator
                // (macrobenchmark, E2E helpers) can address them via By.res().
                .semantics { testTagsAsResourceId = true },
        ) {
            MainContentNavShell(session = session)
            MainContentPlayerOverlay(session = session, sharedScope = sts)
            SnackbarHost(
                hostState = session.globalSnackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = with(session.density) { session.bottomBarHeightPx.toDp() } + 8.dp),
            )
        }
    }
}

@Composable
private fun MainContentNavShell(session: MainPlayerSession) {
    if (session.adaptiveInfo.useNavRail) {
        // Tablet / large screen layout: NavigationRail on the left
        Row(modifier = Modifier.fillMaxSize()) {
            SideNavRail(
                currentTab = session.chrome.currentTab,
                visibleTabs = session.chrome.visibleTabs,
                onItemSelected = { dest -> session.nav.navigateTo(dest) },
            )
            Scaffold(
                bottomBar = {
                    // Reserve the mini-bar slot; the bar itself renders in
                    // the shared-transition overlay so it can morph.
                    if (session.chrome.miniVisible) {
                        Spacer(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(MINI_BAR_HEIGHT)
                                .onSizeChanged { session.bottomBarHeightPx = it.height },
                        )
                    }
                },
            ) { innerPadding ->
                AppNavigation(
                    adaptiveInfo = session.adaptiveInfo,
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
                Column(modifier = Modifier.onSizeChanged { session.bottomBarHeightPx = it.height }) {
                    if (session.chrome.miniVisible) {
                        Spacer(modifier = Modifier.fillMaxWidth().height(MINI_BAR_HEIGHT))
                    }
                    BottomNavBar(
                        currentTab = session.chrome.currentTab,
                        visibleTabs = session.chrome.visibleTabs,
                        onItemSelected = { dest -> session.nav.navigateTo(dest) },
                    )
                }
            },
        ) { innerPadding ->
            AppNavigation(
                adaptiveInfo = session.adaptiveInfo,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
        }
    }
}

@Composable
private fun BoxScope.MainContentPlayerOverlay(session: MainPlayerSession, sharedScope: SharedTransitionScope) {
    // Player surface: the mini bar and full player are the two states of
    // one container transform. The transparent host Box does not consume
    // touches, so taps outside the docked mini bar reach the app behind it.
    if (session.chrome.miniVisible) {
        val dockDp = with(session.density) {
            (session.bottomBarHeightPx - session.miniBarHeightPx).coerceAtLeast(0f).toDp()
        }
        session.gesture.playerTransition.AnimatedContent(
            modifier = Modifier.align(Alignment.BottomCenter),
            contentAlignment = Alignment.BottomCenter,
        ) { isFull ->
            val avScope = this
            if (isFull) {
                FullPlayer(
                    adaptiveInfo = session.adaptiveInfo,
                    sharedScope = sharedScope,
                    visScope = avScope,
                    expandDistancePx = session.expandDistancePx,
                    collapse = com.dustvalve.next.android.ui.screens.player.FullPlayerCollapseActions(
                        onCollapse = { session.nav.collapsePlayer() },
                        onCollapseSeek = session.gesture.onCollapseSeek,
                        onCollapseSettle = session.gesture.onCollapseSettle,
                    ),
                    nav = com.dustvalve.next.android.ui.screens.player.FullPlayerNavActions(
                        onArtistClick = { track ->
                            session.nav.collapsePlayer()
                            when {
                                track.isLocal -> session.nav.requestLocalArtistFilter(track.artist)

                                track.source == TrackSource.YOUTUBE -> session.nav.navigateTo(
                                    NavDestination.ArtistDetail(
                                        url = track.artistUrl,
                                        sourceId = "youtube",
                                        name = track.artist,
                                        imageUrl = track.artUrl.takeIf { it.isNotBlank() },
                                    ),
                                )

                                track.source == TrackSource.SOUNDCLOUD -> session.nav.navigateTo(
                                    NavDestination.ArtistDetail(
                                        url = track.artistUrl,
                                        sourceId = "soundcloud",
                                        name = track.artist,
                                        imageUrl = track.artUrl.takeIf { it.isNotBlank() },
                                    ),
                                )

                                else -> session.nav.navigateTo(NavDestination.ArtistDetail(track.artistUrl))
                            }
                        },
                        onAlbumClick = { track ->
                            when {
                                track.source == TrackSource.YOUTUBE -> {
                                    if (track.albumUrl.isNotBlank()) {
                                        session.nav.collapsePlayer()
                                        session.nav.navigateTo(
                                            NavDestination.CollectionDetail(
                                                url = track.albumUrl,
                                                sourceId = "youtube",
                                                name = track.albumTitle,
                                            ),
                                        )
                                    } else {
                                        // Pre-fetch already ran (albumLookupDone=true);
                                        // empty means the video has no YTM album.
                                        session.player.showNoAlbumSnackbar()
                                    }
                                }

                                track.source == TrackSource.SOUNDCLOUD -> {
                                    // Standalone SC tracks stash the track permalink in
                                    // albumUrl; only /sets/ URLs are openable collections.
                                    if (isSoundCloudCollectionUrl(track.albumUrl)) {
                                        session.nav.collapsePlayer()
                                        session.nav.navigateTo(
                                            NavDestination.CollectionDetail(
                                                url = track.albumUrl,
                                                sourceId = "soundcloud",
                                                name = track.albumTitle,
                                            ),
                                        )
                                    } else {
                                        session.player.showNoAlbumSnackbar()
                                    }
                                }

                                track.albumUrl.isNotBlank() -> {
                                    session.nav.collapsePlayer()
                                    session.nav.navigateTo(NavDestination.AlbumDetail(track.albumUrl))
                                }
                            }
                        },
                    ),
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = dockDp),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    val miniMax = session.adaptiveInfo.miniPlayerMaxWidth
                    MiniPlayer(
                        sharedScope = sharedScope,
                        visScope = avScope,
                        expandDistancePx = session.expandDistancePx,
                        onExpandClick = { session.nav.expandPlayer() },
                        onExpandSeek = session.gesture.onExpandSeek,
                        onExpandSettle = session.gesture.onExpandSettle,
                        modifier = if (miniMax == Dp.Unspecified) {
                            Modifier.fillMaxWidth()
                        } else {
                            Modifier
                                .fillMaxWidth()
                                .widthIn(max = miniMax)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun MainContentKeepScreenOn(activity: MainActivity, playerViewModel: PlayerViewModel = hiltViewModel()) {
    val isPlaying by remember {
        playerViewModel.uiState.map { it.isPlaying }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = false)
    val keepScreenOnInApp by remember {
        activity.settingsDataStore.keepScreenOnInApp
    }.collectAsStateWithLifecycle(initialValue = false)
    val keepScreenOnWhilePlaying by remember {
        activity.settingsDataStore.keepScreenOnWhilePlaying
    }.collectAsStateWithLifecycle(initialValue = true)
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
}

@Composable
private fun MainContentDeepLinks(
    activity: MainActivity,
    navViewModel: NavigationViewModel = hiltViewModel(),
    playerViewModel: PlayerViewModel = hiltViewModel(),
) {
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
}
