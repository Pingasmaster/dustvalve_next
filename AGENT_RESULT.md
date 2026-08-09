# AGENT_RESULT - hard lint item 6/7 ComposeUnstableCollections

## Verdict

**Ignore removed.** Global `ComposeUnstableCollections` `severity="ignore"` deleted from `config/lint/lint.xml`. Detekt `Compose.UnstableCollections` remains `active: false` (with rationale comment). No ImmutableList migration.

## Branch / path / SHA

- Branch: `wip/lint-hard-unstable-collections`
- Worktree: `/home/user/dustvalve_next_lint_unstable_collections`
- Base: `f2c6019` (`wip/lint-hard-base`)
- Ignore-removal SHA: `38dad4ae69329b31f4ae125f3cdc78c9609a3675`
- Branch tip: see `git rev-parse HEAD` on this branch (AGENT_RESULT follow-up commits)


## Path taken

### 1) Tooling: honor `compose_stability_config.conf` - impossible

`slack.lint.compose.UnstableCollectionsDetector` (compose-lints 1.5.4):

- `enabledByDefault = false`
- No lint.xml option / no file loader for Compose Compiler stability configuration
- Hardcodes unstable collection detection via `isTypeUnstableCollection` (List/Set/Map)

Upstream docs (compose-lints rules.md) mark this stability check as largely obsolete under Strong Skipping and leave it off by default. Forking or wrapping the detector to parse `compose_stability_config.conf` is out of scope.

Project already wires the stability file for the real runtime concern:

- `compose_stability_config.conf` marks `kotlin.collections.{List,Set,Map,Collection}`
- `app/build.gradle.kts` `composeCompiler.stabilityConfigurationFiles`
- Strong Skipping default on this Compose Compiler line

### 2) Remove ignore (without enabling the check)

Removed the vestigial global ignore. Replaced with an explicit comment: do **not** `enable += "ComposeUnstableCollections"`. Leaving the check at library default (disabled) is the correct alignment with the stability config + Strong Skipping approach.

### 3) Migration spike (not pursued)

Heuristic scan of `src/main` composable params: ~23 sites / ~14 files with `List`/`Set`/`Map`/`Collection` params (under the 50-file stop line). A call-site `toImmutableList()` boundary pass is feasible in isolation, but it would fight the project's intentional stability-config design and churn UI APIs for a check upstream keeps off. Stopped after spike; full Immutable* migration is a follow-up only if product chooses to enforce the check.

## Verification

- `./scripts/check_ascii.sh` on edited configs: pass
- `./gradlew :app:lintCompatDebug`: **no** `ComposeUnstableCollections` findings (HTML lists issue as "Disabled By: Default")
- Same lint run fails on **pre-existing** base `UnusedResources` (orphaned sign-in / load-page strings from account-login removal on `f2c6019`) - out of scope for this item

## Ignore removed?

**Yes** - `ComposeUnstableCollections` global ignore eliminated. Check not enabled. Detekt rule stays inactive.
