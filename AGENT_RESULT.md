# AGENT_RESULT - lint hard item 1/7 (LocalAdaptiveLayoutInfo)

## Goal
Eliminate the `LocalAdaptiveLayoutInfo` CompositionLocal allowlist by threading
`AdaptiveLayoutInfo` (or needed slices) as explicit parameters from the activity
root.

## Branch / worktree
- Branch: `wip/lint-hard-adaptive-layout`
- Worktree: `/home/user/dustvalve_next`
- Base: `f2c6019` (`wip/lint-hard-base`)

## What changed
- Removed `LocalAdaptiveLayoutInfo` and `ProvideAdaptiveLayout` from
  `AdaptiveLayout.kt`. Kept `rememberAdaptiveLayoutInfo()`,
  `AdaptiveLayoutInfo`, `AdaptiveTokens`, and explicit-arg helpers
  (`adaptiveContentWidth(maxWidth)`, `adaptiveHeroSize`, `AdaptiveContentColumn`).
- `MainContent` now calls `rememberAdaptiveLayoutInfo()` and passes
  `adaptiveInfo` into `MainContentBody` -> `AppNavigation` / `FullPlayer`.
- `AppNavigation` / `LibraryListDetailHost` forward `adaptiveInfo` to screens
  that need width-class metrics (content cap, hero, grid, carousel, dual-pane,
  player layout).
- Screens that only needed sheet width use `AdaptiveTokens.SheetMaxWidth`
  directly (constant; no CompositionLocal required).
- Removed lint allowlist entry
  (`config/lint/lint.xml` ComposeCompositionLocalUsage option).
- Cleared detekt allowlist
  (`config/detekt/detekt.yml` CompositionLocalAllowlist -> empty list).

## Allowlists removed?
- Lint `LocalAdaptiveLayoutInfo` allowlist: **yes (removed)**
- Detekt `LocalAdaptiveLayoutInfo` allowlist: **yes (cleared to empty)**

## Verification
- `./gradlew :app:compileCompatDebugKotlin` - pass
- `./gradlew :app:testCompatDebugUnitTest --tests 'com.dustvalve.next.android.ui.adaptive.*'` - pass
- `scripts/check_ascii.sh` - pass

## Remaining risks
- Preview / isolated screen hosts that previously relied on the CompositionLocal
  default must now supply `AdaptiveLayoutInfo` (or a compact default) when
  invoked outside `MainActivity`.
- Baseline/startup profiles still reference the old CompositionLocal symbols;
  they regenerate on the default `./build.sh` release path (not updated here).
- Parameter plumbing adds args through several navigation/screen layers; future
  screens that need adaptive metrics must take an explicit param rather than
  reading a local.
