# Settings and app chrome - E2E test scenario catalog (~110 scenarios)

Backlog + traceability for the workflow test suites. Each implemented
test references its id here; mark ids [x] with the test class as waves
land. Tiers: JVM (Robolectric/Compose), emulator-smoke, emulator-E2E.


Negative findings (assert as contracts): NO in-app language picker (per-app
language via generateLocaleConfig; localeFilters = en, de, es, fr, it,
pt-rBR, ja, zh-rCN, ru) and NO crash-reporting settings toggle (opt-in is
the post-crash sheet only). SettingsDataStore.kt holds all keys + defaults.

Group 1: Appearance (14) [all JVM, none]
- set-theme-light / -dark / -system / -default-value: theme_mode writes;
  dark shows OLED sub-toggle (isDarkEffective); system follows
  isSystemInDarkTheme() without restart; default "system".
- set-dynamic-color-off/-on: Material You vs app palette (API 31+).
- [ ] set-album-art-theme-on [smoke]: AlbumThemeManager.albumSeedColor recolors
  theme from cover.
- set-oled-black-visibility / -on (#000000 surfaces) / -ignored-in-light
  (value retained, not consumed).
- set-progress-bar-style-linear / -wavy-default (default wavy).
- set-progress-bar-size-slider (8 steps 4..32dp, write on
  onValueChangeFinished) / -default (24, index 5).

Group 2: Locale (5) [JVM except noted]
- set-locale-per-app-each: 9 locales x Settings screenshot baseline
  (LocaleScreenshotTest pattern).
- set-locale-pseudolocale-rtl (ar-XB mirrors) / -long (en-XA wraps, no
  overlap with Switch).
- [ ] set-locale-runtime-switch [E2E]: system per-app language change recreates
  activity, DataStore intact.
- set-locale-plurals-scan: scan snackbar plurals correct in de/ru.

Group 3: Sources (17) [JVM unless noted]
- [ ] set-source-local-enable-mediastore [smoke]: permission dialog ->
  setLocalMusicUseMediaStore(true) rescan; Local tab in nav.
- [ ] set-source-local-enable-folders-empty [smoke]: folder mode + zero folders
  -> OpenDocumentTree launches.
- set-source-local-disable: sync cancelled, clearAll, use_mediastore reset,
  sub-rows animate out.
- set-source-local-use-individual-folders: clear + SAF list + Add folder.
- [ ] set-source-local-back-to-mediastore [smoke]: permission -> clearAll + full
  scan + spinner + plural snackbar.
- [ ] set-source-local-add-folder [E2E]: persistable permission, JSON list, scan,
  sync scheduled.
- set-source-local-add-folder-dedupe: duplicate URI no-op.
- set-source-local-remove-folder: URI removed (key deleted when empty), last
  removal cancels sync.
- set-source-local-rescan: disabled button + wavy spinner + scan_found_
  detailed snackbar.
- set-source-local-rescan-failure: isScanning false + snackbar_scan_failed.
- set-source-local-keep-sort / -keep-filters: 5 filter keys persisted and
  restored only when toggle on.
- set-source-bandcamp-enable / -disable: tab appears/disappears in
  visibleTabs; search-history sub-row; nav fallback when current tab
  removed.
- set-source-youtube-enable: tab + default-source ButtonGroup animates in.
- set-source-youtube-default-source-ytm / -yt: youtube_default_source;
  unknown value coerces to index 0.

Group 4: Connections (6)
- [ ] set-conn-bandcamp-login-nav [smoke, live]: Connect -> AccountLogin WebView;
  back returns.
- [ ] set-conn-bandcamp-login-success [E2E, live]: cookies saved encrypted
  (CookieEncryption), username + avatar shown, auto-download-purchases
  toggle appears.
- [ ] set-conn-bandcamp-signout [JVM]: clears USERNAME/AVATAR/AUTH_COOKIES/
  FAN_ID, expires WebView cookies, snackbar, UI reverts.
- [ ] set-conn-cookie-corrupt [JVM]: undecryptable bytes -> null (logged out),
  no crash.
- [ ] set-conn-ytm-login [E2E, live]: ytm_connected=true, "Connected" shown.
- [ ] set-conn-ytm-signout [JVM]: keys cleared, cookies expired, snackbar.

Group 5: Storage / dedicated folder (17) [JVM unless noted]
- set-storage-limit-each-step: 7 steps 100MB..Unlimited (Long.MAX_VALUE).
- set-storage-limit-eviction: lowering evicts auto-cache overage; pinned
  user downloads never evicted.
- set-storage-limit-restore-index: arbitrary bytes snap to closest step.
- set-storage-remove-all-downloads: confirm dialog -> clearAll; cancel
  intact.
- [ ] set-storage-dedicated-enable [E2E]: SAF tree, folderMirror.suspendFor +
  migrateToFolder with progress overlay, keys + snackbar.
- set-storage-dedicated-enable-failure: error snackbar, toggle stays off.
- set-storage-dedicated-disable: confirm -> migrateFromFolder + reverted
  snackbar; cancel keeps on.
- [ ] set-storage-dedicated-change-folder [E2E]: re-pick + migrate + label
  updates.
- set-storage-dedicated-image-cache / -metadata-cache: sub-toggles move
  caches in/out of tree.
- set-storage-dedicated-controls-disabled-during-migration: all 4 controls
  dimmed (alpha 0.38).
- [ ] set-storage-dedicated-boot-unreachable [E2E]: revoked tree -> BootState.
  DedicatedFolderUnreachable -> error screen; Locate re-picks; Turn off
  clears 4 keys, boots Ready.
- set-storage-dedicated-mirror-excludes-own-keys: settings.json mirror
  excludes the 4 dedicated_folder_* keys both directions.
- set-storage-auto-download-purchases-visibility (login-gated) /
  -purchases (default on) / -future (reveals favorites sub-toggle) /
  -favorites (queues download on favorite; hides manual button).

Group 6: Downloads / audio quality / notifications (13) [JVM unless noted]
- set-audio-format-sheet-open: DOWNLOADABLE formats, check on current.
- set-audio-format-select-each: haptic, key write, label update, pipeline
  reads getDownloadFormatSync.
- set-audio-format-unknown-key-fallback: bogus key -> FLAC label, no crash.
- set-audio-metered-mp3: metered -> mp3 variant, unmetered -> chosen format.
- set-audio-progressive-download: off hides seamless sub-toggle; playback
  waits for full file.
- [ ] set-audio-seamless-upgrade [E2E]: mid-playback swap preserves position.
- set-dl-notifications-off: combine() cancels/never posts (unless
  foreground-owned); shadow NotificationManager assert.
- set-dl-notifications-on-progress: channel "downloads", progress +
  pause/resume/cancel actions, cleared at batch end.
- set-dl-notifications-no-permission: no notify(), no crash.
- [ ] set-dl-live-updates-prompt-visible [smoke] / -deep-link (ACTION_APP_
  NOTIFICATION_SETTINGS w/ fallback) / -resume-recheck [E2E] / -api-absent
  (catch -> true, row hidden).

Group 7: Player (7) [all JVM]
- set-player-inline-volume / -volume-button: FullPlayer conditional UI.
- set-player-keep-screen-on-parent: reveals "only while playing" sub
  (default true).
- set-player-wakelock-parent-only: FLAG_KEEP_SCREEN_ON whenever app open.
- set-player-wakelock-while-playing: flag tracks isPlaying transitions.
- set-player-wakelock-off: never set, sub value retained.
- set-player-wakelock-cleared-on-dispose: onDispose clears flag.

Group 8: Search history (4) [all JVM]
- set-search-history-master-off: rows animate out, nothing recorded, no
  recents shown.
- set-search-history-source-rows-gated: rows only for enabled sources.
- set-search-history-per-source-each: 3 independent keys; disabled source
  records nothing while others record.
- set-search-history-clear-all: clearAll x3 sources + snackbar.

Group 9: Updates (13) [JVM, hermetic - AppUpdateService mockable]
- set-update-manual-check-uptodate: Checking (disabled) -> Idle + no-update
  snackbar.
- set-update-manual-check-available: Available(version, apkUrl, notes) ->
  AppUpdateDialog with Markdown notes.
- set-update-manual-check-failed: Idle + check-failed snackbar.
- [ ] set-update-confirm-download [E2E]: progress 0..1 (indeterminate when no
  Content-Length) -> launchInstaller (REQUEST_INSTALL_PACKAGES).
- set-update-download-failed / set-update-install-failed: Idle + respective
  snackbar.
- set-update-dismiss: Idle; dismiss during Downloading is no-op.
- set-update-check-disabled-while-downloading: manual check no-op.
- set-update-silent-cold-start: checkSilently once per process; Available
  dialog from ANY screen; silent on no-update/error.
- set-update-auto-toggle-off: zero requests on restart; manual still works.
- set-update-silent-respects-inflight: existing Available/Downloading
  preserved.
- set-update-release-on-trim: onTrimMemory resets Available->Idle,
  Downloading preserved.
- set-update-settings-shares-state: single process-wide controller flow.

Group 10: Crash reporting (9) [all JVM, CrashReportManagerTest pattern]
- set-crash-jvm-crash-prompts: pending_crash.txt (header + stack) ->
  Pending -> CrashReportSheet with 3 actions.
- set-crash-share-log: ACTION_SEND chooser, subject "Dustvalve Next crash
  log", EXTRA_TEXT = exact logText.
- set-crash-open-github-issue: issues/new URL, code-fenced body, 3000-char
  truncation marker (buildIssueUrl).
- set-crash-dismiss-deletes: Hidden, file deleted, no re-prompt.
- set-crash-exitinfo-anr: REASON_ANR/CRASH_NATIVE newer than watermark ->
  OS-reported prompt; watermark advanced.
- set-crash-user-force-close-never-prompts: USER_REQUESTED/LMK/EXIT_SELF ->
  no prompt.
- set-crash-watermark-dedupe: same record never prompts twice.
- set-crash-no-crash-no-prompt: clean launch stays Hidden.
- set-crash-write-failure-safe: unwritable dir swallowed, crash still
  propagates to platform handler.

Group 11: Debug (1)
- [ ] set-debug-show-info [JVM]: album_cover_long_press_carousel repurposed key;
  debug overlay replaces cover carousel.

Group 12: Navigation / app chrome (20) [JVM unless noted]
- set-nav-default-tabs: fresh install -> Local, Library, Settings only.
- set-nav-all-tabs: +Bandcamp +YouTube in BottomNavItem enum order.
- set-nav-visit-each-destination: AnimatedContent swaps 5 screens, per-tab
  back stacks (tabStacks) preserved.
- set-nav-detail-destinations: AlbumDetail/ArtistDetail/PlaylistDetail/
  CollectionDetail/AccountLogin/YouTubeMusicLogin render + back pops; slide
  direction = lastNavigationForward.
- set-nav-back-to-local-root: back on non-Local root -> LocalHome, not exit.
- set-nav-back-pops-stack.
- [ ] set-nav-predictive-back-player [E2E]: gesture scrubs collapse; cancel
  springs back.
- set-nav-rail-adaptive: >=600dp -> SideNavRail replaces bottom bar.
- [ ] set-nav-mini-full-player-morph [E2E]: SharedTransition morph + velocity
  settle.
- set-nav-deeplink-view / -send (first https URL extracted) / -unsupported
  (snackbar_unsupported_source) / -disabled-provider (enable? dialog with
  provider icon + kind noun; Enable proceeds, Cancel dismisses).
- [ ] set-chrome-notification-permission-first-run [smoke]: POST_NOTIFICATIONS
  asked at first launch; denial tolerated.
- set-chrome-about-version (BuildConfig.VERSION_NAME) / -about-license (GPL
  line) / -open-repository (github URL) / -report-issue (/issues).
- [ ] set-chrome-theme-no-flash [smoke]: nothing rendered until ThemeConfig
  emits (no light->dark flash).
- set-chrome-boot-loading: DedicatedFolderBootLoading until rehydrateAll.

Group 13: Persistence across restart (5) [all JVM]
- set-persist-all-toggles-survive-restart: set every non-default value, kill
  + relaunch, every flow re-emits (golden screenshot or per-key asserts).
- set-persist-defaults-fresh-install: full default matrix (see
  SettingsDataStore.kt / SettingsDataStoreTest.kt).
- set-persist-account-survives: login state restored (decrypted cookies).
- set-persist-folder-mirror-roundtrip: wipe data keeping tree, re-enable ->
  restorePreferences repopulates all types, dedicated keys excluded.
- set-persist-viewmodel-vs-store-divergence: SettingsUiState defaults
  seamlessQualityUpgrade=true and albumCoverLongPressCarousel=true while
  DataStore defaults are false - assert UI never renders pre-collection
  defaults (bug risk).

Key files: SettingsScreen.kt, SettingsViewModel.kt, SettingsDataStore.kt
(core/datastore), CrashReportManager.kt, CrashReportSheet.kt,
AppUpdateController.kt, AppUpdateService.kt, AppUpdateDialog.kt,
MainActivity.kt, ui/navigation/ (AppNavigation, BottomNavItem,
NavigationViewModel), DownloadNotificationCenter.kt, AccountLoginScreen.kt,
YouTubeMusicLoginScreen.kt.
