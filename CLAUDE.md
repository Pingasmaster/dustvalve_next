# Project: Dustvalve Next

## Build Instructions

After making any code fixes or changes, always verify compilation by running `./build.sh` **as a background process**:

```bash
./build.sh
```

This script builds debug + release, copies the release APK to the project root, and bumps the version automatically.

Always use `run_in_background: true` when launching builds so you can continue working while they compile. Once the build finishes and you've completed your task, stop and wait — you will be notified via a hook when the build completes.

## Gradle perf flags to AVOID

Do NOT enable these in `gradle.properties`; they cause problems on this stack:

- `org.gradle.configuration-cache=true`
- `org.gradle.caching=true`

Even though Gradle's build log keeps suggesting configuration cache, leave it off. The combination of AGP alpha + KSP + Hilt at the bleeding-edge versions this project tracks is not reliably configuration-cache-safe.

## Git Policy

Auto-commit and push is acceptable when the user has explicitly requested automated workflows (e.g., cron jobs). Otherwise, follow the user's explicit instructions.

The README download badge uses `releases/latest/download/app-release.apk` which always resolves to the latest GitHub release automatically. No manual badge update is needed on commit and push.

## Legacy Android 8-12L branch (`legacy-android8`)

A long-lived, **unsupported** backport branch named `legacy-android8` exists for users on Android 8.0 (API 26) through Android 12L (API 32). Master targets Android 13+ (`minSdk=33`) and uses bleeding-edge alpha deps; the legacy branch carries a minimal patch set on top of whatever master HEAD it was last cherry-picked from:

- `minSdk=26`, `versionNameSuffix="-legacy"`, `coreLibraryDesugaring` enabled.
- Manifest gates: `FOREGROUND_SERVICE_MEDIA_PLAYBACK` (`minSdkVersion="34"`), `READ_MEDIA_AUDIO` (`minSdkVersion="33"`), `READ_EXTERNAL_STORAGE` (`maxSdkVersion="32"`); no `enableOnBackInvokedCallback`.
- Runtime guards: `Build.VERSION.SDK_INT` checks for POST_NOTIFICATIONS prompt and the audio permission picker (`util/LegacyPermissions.kt::legacyAudioPermission()`).

### Goals
- Give Android 8-12L users a working APK on every release without compromising master's modern stack.
- Ship as `dustvalve-old.apk` on the **same** GitHub Release as the modern `app-release.apk` (release workflow has a `build-legacy` job that checks out `legacy-android8` and uploads it).
- Zero dependency forks: legacy stays on the same dep versions as master so cherry-picks are mostly clean.

### Maintenance rules — important
- **Only update this branch when the user explicitly asks.** Do not proactively cherry-pick from master, do not propose syncing, do not surface the branch's drift unless asked.
- **Do not build the legacy branch to verify changes before pushing.** It is unsupported; if its CI release job fails, that is the signal to fix it — locally rebuilding `legacy-android8` is not part of the normal flow.
- When the user does ask to update legacy: enter the `legacy-android8` worktree (or check it out), cherry-pick the relevant master commits, resolve conflicts with the patch policy above (keep minSdk=26, the manifest gates, the desugaring, the `legacyAudioPermission()` indirection), commit, and push. No local build verification required.
- **Always keep `versionCode` and `versionName` on `legacy-android8` identical to the latest master.** The `versionNameSuffix = "-legacy"` is what differentiates the two builds at install time; the underlying numbers must match so each GitHub Release ships an `app-release.apk` and a `dustvalve-old.apk` from the same version line. When resolving cherry-pick conflicts on `app/build.gradle.kts`, take master's version values verbatim.

## Pre-alpha update policy

This project is **pre-alpha**. Until beta:

- DB schema is wiped on every install (Room `fallbackToDestructiveMigration(dropAllTables = true)`); schema version stays at 1; no migration code, ever.
- No "in-place upgrade" support of any kind: no SharedPreferences migrations, no DataStore version-bump handlers, no on-disk file-format upgraders.
- If you find any such code, **delete it** without asking — it is debt that the user has no plans to keep.
- Self-update has exactly two entry points, both driven by `AppUpdateService` via the process-wide `AppUpdateController` (no Play Store, no Firebase): (1) a **silent cold-start check** fired once per process from `DustvalveNextApplication.onCreate()` → `AppUpdateController.checkSilently()`, and (2) the user-triggered **"Search for updates"** button in Settings → About → `checkManually()`. The cold-start check is gated by the **"Automatic update checks"** toggle (Settings → About, DataStore key `auto_update_check_enabled`, default **on**); the manual button is never gated. Do **not** add background `WorkManager` update jobs, and do not add any *other* startup auto-check beyond the single `checkSilently()` call.

## Known Non-Bugs (Do NOT Fix)

- **Bug #1 (Missing `org.jetbrains.kotlin.android` plugin)**: This is NOT a real bug. The `org.jetbrains.kotlin.plugin.compose` plugin already applies the Kotlin Android plugin internally. Adding `id("org.jetbrains.kotlin.android")` to `app/build.gradle.kts` causes a build failure: "Cannot add extension with name 'kotlin', as there is an extension already registered with that name." Do NOT add this plugin.

- **Bug #2 (WorkManagerInitializer manifest-merge warning)**: This is NOT a real bug and will **NEVER be fixed** — always ignore it. Every build prints, in the unit-test variant manifest merge (`mergeDebugUnitTestManifest`) only:

  ```
  AndroidManifest.xml:28 Warning: meta-data#androidx.work.WorkManagerInitializer was tagged at AndroidManifest.xml:28 to remove other declarations but no other declaration present
  ```

  The `<provider tools:node="merge">` block in `app/src/main/AndroidManifest.xml` with `tools:node="remove"` on the `androidx.work.WorkManagerInitializer` meta-data is the **canonical, required** Hilt + WorkManager pattern (lets Hilt provide `HiltWorkerFactory`). The warning fires only in the unit-test variant because AGP deliberately does not merge dependency/AAR manifests into the test sourceset (Google Issue Tracker 127986458), so the `remove` directive finds nothing to act on. In the real debug/release APK merge the library contributes the node, the removal works, and there is NO warning.

  There is no safe targeted suppression: `tools:ignore` only affects **lint** issue IDs (this is a manifest-*merger* warning, not lint), and `tools:node="removeAll"` would also strip the `ProfileInstallerInitializer` from the same provider and break baseline-profile installation. Do NOT touch the manifest block, do NOT add a suppression, do NOT "fix" this warning. It has zero runtime impact. Android Lint itself reports "No issues found"; this is purely a cosmetic build-log line.

## Pre-Alpha Status

This project is in **pre-alpha**. Do NOT worry about database migrations or retrocompatibility. Always use `fallbackToDestructiveMigration()` and keep DB version at 1. Schema changes are free — just modify entities directly.

## Material You 3 Expressive — ALWAYS Prefer

Always search for and use **Material 3 Expressive** APIs over outdated or generic alternatives:

- **Animations**: Use `MaterialTheme.motionScheme` spring specs (`defaultSpatialSpec`, `fastSpatialSpec`, `fastEffectsSpec`, `slowSpatialSpec`) instead of duration-based `tween`/`keyframes`. Spatial specs for movement, effects specs for color/opacity.
- **Lists**: Use M3 `ListItem` composable for all list rows. Use `Modifier.animateItem()` for lazy list item animations.
- **Menus**: Use `ModalBottomSheet` for contextual menus on mobile list items (not `DropdownMenu`).
- **Drag-and-drop**: Use native `detectDragGesturesAfterLongPress` with M3E visual feedback. Use `surfaceContainerHigh` for dragged item backgrounds. Use `animateColorAsState`/`animateDpAsState` with MotionScheme spring specs for elevation and color transitions.
- **Icons**: Use `Icons.Rounded` style. Use `DragHandle` (not `DragIndicator`) for reorder handles.
- **Interactions**: Use `combinedClickable` for long-press + click. Add haptic feedback on drag interactions.
- **Shapes**: Use M3 Expressive shapes from `MaterialShapes` / `graphics-shapes` library.

## Web Fetching

When `WebFetch` fails to retrieve a resource (e.g., raw GitHub files returning 404), use `wget -qO-` via Bash as a fallback.

IMPORTANT: Before any design actions, make sure to fully understand material you 3 expressive deisng around it, best guidelines and implementations by doing at least two agents that search material you 3 expressive guidelines and elements related to what you want to achieve in EACH CONVERSATION. THIS IS MANDATORY if any complex design work is done.

## Dependency Notes

- **hiltViewModel**: Use `androidx.hilt:hilt-lifecycle-viewmodel-compose` (not `hilt-navigation-compose`). Import from `androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel`. The navigation-compose variant is deprecated as of 1.3.0.

## Android-runtime gotchas (host JVM != device)

- **Regex strictness**: Android's `java.util.regex.Pattern` is backed by ICU and is stricter than OpenJDK's. A literal `}` outside a `{n,m}` quantifier throws `PatternSyntaxException` on-device while compiling fine on the host JVM (so JVM unit tests do NOT catch it). Always escape both braces in regex literals: write `\{...\}`, never `\{...}`. Same caution for `]`, `(`, `)` when used literally.

## Future improvements

- **YT player JS / nsig support (deferred)**: We deliberately ship without a JS engine. The `ANDROID_VR_NO_AUTH` + `IOS` cascade in `data/remote/youtube/innertube` returns playback URLs that need no sig/nsig deciphering, but loses Premium 256 kbps Opus and age-gated playback. Adding `com.github.gedoor:rhino-android` (~1 MB) for nsig only (sig can be hand-translated) would unlock both. Not urgent today: track the yt-dlp `web_music` config and the yt-dlp PoToken Guide; revisit when ANDROID_VR / IOS clients start failing or when Premium quality becomes a goal.

- **Spotify via librespot (scaffold only, currently dead code)**: The `rust/` directory (`librespot-ffi`, `buildLibrespot.sh`, `prepare.sh`) is a JNI-bridge scaffold added in commit `d17c361` for a future Spotify provider. It is **not wired into the build**: nothing in `settings.gradle.kts` / `app/build.gradle.kts` compiles it, no `.so` is bundled, and the Kotlin counterpart it references (`SpotifyBridge.kt`) does **not** exist in `app/src` (`lib.rs` is all `TODO`/placeholder). There is currently **zero Spotify code in the app** — the README's "Spotify is currently broken" really means "not implemented." Treat `rust/` as inert scaffolding; do not assume a working Spotify path exists, and do not let it confuse provider audits.

- **Android 17 "Min Mode" AOD mini-player (deferred — re-check Aug 2026 or later)**: Rendering the now-playing mini-player on the Always-On Display via Min Mode is **not implementable yet** — as of mid-2026 it is not a public API (absent from API 37 `android.jar` and Android 17 Beta; disabled in Canary; no documented `MinModeActivity`/`MinModeProvider` contract, permission, sample, or emulator support). Google says it lands as a developer API after the June 2026 stable release. **Action: from August 2026 onward, check the Android Developers blog / API 37+ release notes for a documented Min Mode API, then implement the AOD mini-player.**

# Important

If any agents launched report inconclusive results, research more and  if youre unsure how to do something, confirm by launhcing an agent to deep dive the web.

Make sure you understand thoroughly the problem and the solution before writing ANY plan. If you're unsure, launch more research agents on the web.
Also make sure to confirm manually ALL bugs if your plan includes bug fixes to make sure ALL bugs found are real.
