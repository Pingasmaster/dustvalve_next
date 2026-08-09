# AGENT_RESULT - lint hard item 2/7 (TooManyFunctions / LargeClass splits)

## Branch / worktree / SHA

- Branch: `wip/lint-hard-split-playback`
- Worktree: `/home/user/dustvalve_next_lint_split_playback`
- Base: `f2c6019` (`wip/lint-hard-base`)
- Tip SHA: `dade40f6b78c70ba0cda9dbc72fddef405638ae2`

## Baseline IDs removed

Cleared from `config/detekt/detekt-baseline.xml`:

1. `LargeClass:PlayerViewModel.kt:PlayerViewModel : ViewModel`
2. `TooManyFunctions:PlaybackManager.kt:PlaybackManager`
3. `TooManyFunctions:PlayerViewModel.kt:PlayerViewModel : ViewModel`
4. `TooManyFunctions:SettingsDataStore.kt:SettingsDataStore`
5. `TooManyFunctions:SettingsViewModel.kt:SettingsViewModel : ViewModel`

## Relocated (not expanded) TooGenericExceptionCaught debt

Existing ViewModel catch-all style was preserved via shared helpers; baseline IDs moved with the code (agent 5 owns the rewrite):

- Removed: `TooGenericExceptionCaught:PlayerViewModel.kt:PlayerViewModel$e: Exception`
- Removed: `TooGenericExceptionCaught:SettingsViewModel.kt:SettingsViewModel$e: Exception`
- Added: `TooGenericExceptionCaught:PlayerUiActionCatch.kt:PlayerUiActionCatch$e: Exception`
- Added: `TooGenericExceptionCaught:SettingsPrefWrite.kt:SettingsPrefWrite$e: Exception`

## What was split

### SettingsDataStore
Facade over domain stores sharing one preferences DataStore:
- `SettingsPreferences` (keys + guarded flow)
- `AppearanceSettingsStore` (+ Impl)
- `DownloadStorageSettingsStore` (+ Impl)
- `LocalMusicPrefsStore` (+ Impl)
- `SourcesSearchPlayerPrefsStore` (+ Impl)
- `SettingsDataStore` delegates via Kotlin `by`

### SettingsViewModel
- `SettingsAppearancePrefsCoordinator`
- `SettingsStorageSourcesPrefsCoordinator`
- Existing `LocalMusicSettingsCoordinator` kept
- Action handlers / SettingsScreen wired to coordinators

### PlaybackManager
- `PlaybackPositionTracker`
- `PlaybackMediaPreparer`

### PlayerViewModel
- `PlayerExtraState`, `PlayerAudioController`, `PlayerStateCollectors`
- `PlayerPlayCoordinator`, `PlayerLibraryCoordinator`
- Public API kept as same-package extensions (`PlayerViewModelTransportActions` / `PlayerViewModelLibraryActions`) with call-site imports outside the player package

## Verification

- `:core:datastore:detekt` + `:app:detekt` pass
- `:app:compileCompatDebugKotlin` + unit-test compile pass
- SettingsDataStore + PlayerViewModelResolveTest unit tests pass
- ASCII check pass
