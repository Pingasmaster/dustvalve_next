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

# Important

If any agents launched report inconclusive results, research more and  if youre unsure how to do something, confirm by launhcing an agent to deep dive the web.

Make sure you understand thoroughly the problem and the solution before writing ANY plan. If you're unsure, launch more research agents on the web.
Also make sure to confirm manually ALL bugs if your plan includes bug fixes to make sure ALL bugs found are real.
