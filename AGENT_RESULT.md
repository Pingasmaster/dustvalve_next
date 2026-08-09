# AGENT_RESULT

## Branch / path / SHA

- Branch: `wip/lint-hard-compose-screens`
- Worktree: `/home/user/dustvalve_next_lint_compose_screens`
- Base: `f2c6019` (`wip/lint-hard-base`)
- Tip SHA: (filled after commit)

## Goal

Hard lint item 3/7: decompose mega Compose screens + nav orchestration to clear
LongMethod / CyclomaticComplexMethod baseline entries and related @Suppress.

## Screens / files refactored

### FullPlayer (primary)

Decomposed into companion files without product behavior changes:

- `FullPlayer.kt` - thin orchestrator (state, shared elements, sheet + scaffold)
- `FullPlayerModels.kt` - motion/chrome/sheet/layout holders
- `FullPlayerSheets.kt` - volume / debug / delete / context / queue sheets
- `FullPlayerSheetHost.kt` - sheet visibility host
- `FullPlayerContent.kt` - scaffold, transport, collapse bar
- `FullPlayerAlbumArt.kt` - carousel / stack / inline volume
- `FullPlayerQueue.kt` - Up Next pane + row
- `FullPlayerAudioOutput.kt` - device name/icon helpers
- `FullPlayerControls.kt` - SeekBar split (indicator / time labels / gestures)

Kept `LocalAdaptiveLayoutInfo.current` (did not migrate to AdaptiveLayoutInfo params).

### AppNavigation

- Extracted destination `when` into `AppNavigationDestinations.kt`
  (home vs detail helpers)
- Extracted provider-enable dialog
- Removed `@Suppress("LongMethod", "CyclomaticComplexMethod")`

### MainActivity MainContent / MainContentBody

- Removed `@Suppress("LongMethod", "CyclomaticComplexMethod")`
- Split into KeepScreenOn, DeepLinks, PlayerChrome, SharedHost, NavShell,
  PlayerOverlay + `MainPlayerSession` holder
- Kept `LocalAdaptiveLayoutInfo.current`

## Baseline IDs removed

From `config/detekt/detekt-baseline.xml`:

1. `CyclomaticComplexMethod:FullPlayer.kt:... fun FullPlayer`
2. `LongMethod:FullPlayer.kt:... fun FullPlayer`
3. `LongMethod:FullPlayerControls.kt:... fun FullPlayerSeekBar`

## @Suppress removed

- `AppNavigation` LongMethod / CyclomaticComplexMethod
- `MainContent` / `MainContentBody` LongMethod / CyclomaticComplexMethod
- `UpNextQueuePane` LongMethod (moved/split into `FullPlayerQueue.kt`)

## Not cleared in this pass (still baselined)

Provider screens and detail screens were not fully decomposed yet; baseline
entries remain for:

- BandcampScreen (LongMethod + CCM + SearchResultItem CCM)
- YouTubeScreen (LongMethod + CCM)
- LocalScreen (LongMethod + CCM)
- LibraryScreen (LongMethod + CCM + LibraryList LongMethod)
- AlbumDetailScreen / ArtistDetailScreen / CollectionDetailScreen
- PlaylistDetailScreen / PlaylistContent
- MiniPlayer
- PlaylistEditSheet / PlaylistListItem (related, not primary)

## Verification

- `./gradlew :app:compileCompatDebugKotlin` - SUCCESS
- `scripts/check_ascii.sh` - passed
- Detekt: FullPlayer LongMethod/CCM no longer reported; remaining repo-wide
  detekt debt includes pre-existing LongParameterList (also on base FullPlayer)
  owned across other lint-hard agents
