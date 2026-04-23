# Project: Dustvalve Next

## Build Instructions

After making any code fixes or changes, always verify compilation by running `./build.sh` **as a background process**:

```bash
./build.sh
```

This script builds debug + release, copies the release APK to the project root, and bumps the version automatically.

Always use `run_in_background: true` when launching builds so you can continue working while they compile. Once the build finishes and you've completed your task, stop and wait — you will be notified via a hook when the build completes.

## Git Policy

Do NOT commit or push unless the user explicitly asks you to. Never commit/push proactively after completing changes.

**When the user says "commit and push", always update the README download badge first.** Before staging:

1. Read the current `versionName` from `app/build.gradle.kts` (e.g. `0.3.20`).
2. Edit `README.md`: replace the `releases/download/v<old>/app-release.apk` URL fragment AND the `alt="Download APK v<old>"` text with `v<new>`.
3. Edit `download.svg`: replace BOTH `aria-label="Download APK v<old>"` and the `Download APK v<old>` text node body with `v<new>`.
4. Stage README.md + download.svg in the same commit.

Skip this step ONLY if the user explicitly says not to bump the badge.

## Pre-alpha update policy

This project is **pre-alpha**. Until beta:

- DB schema is wiped on every install (Room `fallbackToDestructiveMigration(dropAllTables = true)`); schema version stays at 1; no migration code, ever.
- No "in-place upgrade" support of any kind: no SharedPreferences migrations, no DataStore version-bump handlers, no on-disk file-format upgraders.
- If you find any such code, **delete it** without asking — it is debt that the user has no plans to keep.
- The only self-update path is the user-triggered "Search for updates" button in Settings → About (driven by `AppUpdateService`). Do not add startup auto-checks; do not add background WorkManager update jobs.

## Known Non-Bugs (Do NOT Fix)

- **Bug #1 (Missing `org.jetbrains.kotlin.android` plugin)**: This is NOT a real bug. The `org.jetbrains.kotlin.plugin.compose` plugin already applies the Kotlin Android plugin internally. Adding `id("org.jetbrains.kotlin.android")` to `app/build.gradle.kts` causes a build failure: "Cannot add extension with name 'kotlin', as there is an extension already registered with that name." Do NOT add this plugin.

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

## Future improvements

- **YT player JS / nsig support (deferred)**: We deliberately ship without a JS engine. The `ANDROID_VR_NO_AUTH` + `IOS` cascade in `data/remote/youtube/innertube` returns playback URLs that need no sig/nsig deciphering, but loses Premium 256 kbps Opus and age-gated playback. Adding `com.github.gedoor:rhino-android` (~1 MB) for nsig only (sig can be hand-translated) would unlock both. Not urgent today: track the yt-dlp `web_music` config and the yt-dlp PoToken Guide; revisit when ANDROID_VR / IOS clients start failing or when Premium quality becomes a goal.

# Important

If any agents launched report inconclusive results, research more and  if youre unsure how to do something, confirm by launhcing an agent to deep dive the web.

Make sure you understand thoroughly the problem and the solution before writing ANY plan. If you're unsure, launch more research agents on the web.
Also make sure to confirm manually ALL bugs if your plan includes bug fixes to make sure ALL bugs found are real.
