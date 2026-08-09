# AGENT_RESULT - hard lint item 5/7 (ViewModel error handling)

## Branch / path / SHA

- Branch: `wip/lint-hard-vm-errors`
- Worktree path: `/home/user/dustvalve_next_lint_vm_errors`
- Commit: `aa38d240a5eacc7eaec638db93598ebaff197d77`
- Base: `f2c6019` (`wip/lint-hard-base`)

## What landed

Shared helpers in `core/common/.../util/UiResult.kt`:

- `UiResult` / `Throwable.toUiText`
- `runCatchingUi` / `runCatchingUiSync` (map failures to `UiText`)
- `runCatchingUiIgnore` / `runCatchingUiIgnoreSync` (best-effort side effects)
- `runCatchingUiOrNull` / `runCatchingUiOrNullSync`
- Always rethrows `CancellationException`

Applied across feature ViewModels (catch sites only; no class splits).

## VMs updated

- `AlbumDetailViewModel`
- `ArtistDetailViewModel`
- `BandcampViewModel`
- `CollectionDetailViewModel`
- `LibraryViewModel`
- `LocalViewModel`
- `NavigationViewModel`
- `PlayerViewModel` (catch replacements only)
- `PlaylistDetailViewModel`
- `SearchViewModel`
- `SettingsViewModel` (catch replacements only)
- `YouTubeViewModel`

Unit tests: `UiFailuresTest` plus existing VM tests listed below.

## Baseline IDs removed

From `config/detekt/detekt-baseline.xml` (`TooGenericExceptionCaught`):

- `AlbumDetailViewModel.kt:AlbumDetailViewModel$e: Exception`
- `ArtistDetailViewModel.kt:ArtistDetailViewModel$e: Exception`
- `BandcampViewModel.kt:BandcampViewModel$e: Exception`
- `CollectionDetailViewModel.kt:CollectionDetailViewModel$e: Exception`
- `LibraryViewModel.kt:LibraryViewModel$e: Exception`
- `LocalViewModel.kt:LocalViewModel$e: Exception`
- `NavigationViewModel.kt:NavigationViewModel$e: Exception`
- `PlayerViewModel.kt:PlayerViewModel$e: Exception`
- `PlaylistDetailViewModel.kt:PlaylistDetailViewModel$e: Exception`
- `SearchViewModel.kt:SearchViewModel$e: Exception`
- `SettingsViewModel.kt:SettingsViewModel$e: Exception`
- `YouTubeViewModel.kt:YouTubeViewModel$e: Exception`

Kept:

- `AppUpdateController.kt:AppUpdateController$e: Exception` (out of scope)

Added (intentional choke point):

- `UiResult.kt:e: Exception`

## Tests run

```text
./gradlew :app:testCompatDebugUnitTest \
  --tests com.dustvalve.next.android.util.UiFailuresTest \
  --tests com.dustvalve.next.android.ui.screens.detail.ArtistDetailViewModelTest \
  --tests com.dustvalve.next.android.ui.screens.detail.CollectionDetailViewModelTest \
  --tests com.dustvalve.next.android.ui.screens.search.SearchViewModelTest \
  --tests com.dustvalve.next.android.ui.screens.playlist.PlaylistDetailViewModelTest \
  --tests com.dustvalve.next.android.ui.screens.bandcamp.BandcampViewModelTest \
  --tests com.dustvalve.next.android.ui.screens.youtube.YouTubeViewModelSearchDispatchTest \
  --tests com.dustvalve.next.android.ui.navigation.NavigationViewModelTest \
  --tests com.dustvalve.next.android.workflow.PlayerViewModelResolveTest
```

Result: BUILD SUCCESSFUL.

Also: `:core:common:detekt` green; `:app:compileCompatDebugKotlin` green.
(`:app:detektCompatDebug` still reports pre-existing non-TooGeneric debt on base as well.)
