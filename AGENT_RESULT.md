# AGENT_RESULT - hard lint item 7/7 (upstream / product-blocked allows)

Branch: `wip/lint-hard-allows`
Worktree: `/home/user/dustvalve_next_lint_allows`
Base: `f2c6019` (`wip/lint-hard-base`)

## Verdict

Only one suppress was removable without fighting product/Compose conventions:
`@SuppressLint("QueryPermissionsNeeded")` on `AppUpdateService.resolveSystemInstallerPackage`.
Manifest already declares the matching package-archive VIEW `<queries>` carve-out;
lintCompatRelease stays clean without that annotation. `DEPRECATION` stays.

Everything else evaluated as KEEP (comments clarified where helpful).

## Keep vs fixed

| Candidate | Decision | Reason |
| --- | --- | --- |
| `lint.xml` GoogleAppIndexingWarning | KEEP | Deep-link VIEW/BROWSABLE filters (autoVerify=false) are intentional in-app open handlers for YouTube/Bandcamp/SoundCloud. Removing them breaks deep links; App Indexing is not a product goal. Comment clarified. |
| NewerVersionAvailable / AndroidGradlePluginVersion / GradleDependency | KEEP (informational) | `./build.sh` already auto-bumps via `scripts/check_latest_deps.py --apply`. Demotion avoids duplicate failures under warningsAsErrors; editor markers remain. Comment clarified. |
| `.editorconfig` ktlint naming | KEEP | `function-naming` already uses `ignore_when_annotated_with = Composable` (not fully disabled). `property-naming` has no LocalFoo/PascalCase allowlist in ktlint 1.8 (only `constant_naming`). Re-enabling `backing-property-naming` fails on `_state` without a matching public `state` (QueueManager, PlayerViewModel, NavigationViewModel, StorageTracker). |
| `scripts/check_ascii.sh` allowlists | KEEP | i18n resources, fixtures, Unicode-is-the-test sources, binaries - policy exceptions documented in CLAUDE.md. |
| `DeprecationShims.kt` DeprecatedCall | KEEP | slack-lint-checks **0.11.1** (Maven latest) still false-positives non-deprecated FlowRow/ButtonGroup overloads. Empirically: removing `@file:Suppress` fails lintCompatRelease. Changelog has no overload-resolution fix. |
| `PlayerModule` DefaultMediaSourceFactory DeprecatedCall | KEEP | Same slack-lints false positive on Media3 ctor when class has other deprecated members. Empirically fails lint without suppress; kotlinc clean. |
| `AppUpdateService` QueryPermissionsNeeded | FIXED | Removed. Existing AndroidManifest `<queries>` for VIEW + `application/vnd.android.package-archive` already satisfies the check (lint verified). Security posture unchanged (narrow mime-type carve-out kept). |
| `AppUpdateService` DEPRECATION | KEEP | Int-flags `queryIntentActivities` is the only overload on compat minSdk 26. API 33+ `ResolveInfoFlags` dual path would still need DEPRECATION on the pre-33 branch; not a full remove. |
| Login WebView SetJavaScriptEnabled | N/A | Already gone with account login on this base; nothing to do. |

## Evidence notes

- Empirically removed DeprecatedCall suppressions -> lint errors on DeprecationShims FlowRow and PlayerModule DefaultMediaSourceFactory.
- Empirically re-enabled `ktlint_standard_backing-property-naming` -> 6 backing-property-naming failures.
- Empirically removed only QueryPermissionsNeeded -> no QueryPermissionsNeeded finding in lintCompatRelease.
- Note: base `f2c6019` still has leftover unused login strings (`settings_sign_in_*`, `error_load_page`) that Lint UnusedResources flags; cleanup belongs with the account-login removal track, not this item. Not included in this commit.
