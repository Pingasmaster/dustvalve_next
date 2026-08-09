# Dustvalve Next - agent guidelines

## Suppressions and allowlists

Prefer fixing the root cause over `@Suppress`, lint `ignore`/`disable`,
detekt baselines, ktlint disables, or allowlists. If suppression is the
real choice: stop, explain why a fix is not viable, and ask the user
before adding or expanding it (unless they already authorized that path).
See also Cursor rule `android-ask-before-suppress`.

## Workflow tests - run before shipping behavior changes

Four automated tiers (see docs/testing/README.md):
- `./build.sh` (default) - RELEASE path: regenerates baseline+startup profiles
  (KVM), bumps version, full lint/test/assemble, then GMD shippedsmoke +
  smoke + hermetic e2e. Use `./build.sh --debug` for day-to-day builds
  (skips baselines, version bump, and device gates).
- `./build.sh --workflow-tests` - fast JVM regression net (real ExoPlayer +
  real MainActivity under Robolectric). Run this after ANY change touching
  playback, navigation, or the provider screens.
- `./build.sh --smoke` / `--e2e` / `--e2e-live` - Gradle Managed Device
  suites (`pixel7aApi37`). Requires KVM; run locally only (also gated on
  the default release path except `--e2e-live`).
- `:shippedsmoke` - drives the APK as SHIPPED (release + proguard-rules.pro
  alone) through UiAutomator, covering the library minification the
  `-PtestReleaseBuild` lane cannot. NEVER pass `-PtestReleaseBuild` to it:
  that applies proguard-test-support.pro and defeats the whole point.
  Local: `./build.sh --smoke-shipped` (also gated on the default release
  path).
- Baseline profiles regenerate on every default `./build.sh` release path
  (skipped by `--debug`). Macrobenchmark stays opt-in / advisory:
  `./build.sh --macrobenchmark`.
- Scenario backlog lives in docs/testing/catalog-*.md; new E2E tests must
  reference their catalog id.

E2E tests must not inherit provider state. The release lane runs the suite
UNFILTERED in one pass, so DataStore flags leak between classes; declare what
a class needs with `ProviderStateRule` rather than assuming a starting state.

## Protected branches - DO NOT DELETE OR FORCE-PUSH

When asked to clean up "dangling" branches, worktrees, or any other git
state, the following branch is **legitimate and must never be erased**:

- `master` - the canonical default branch. Ships both APKs from one
  tree via the `api` product-flavor dimension (`compat` = Android 8-16,
  `future` = Android 17).

The old `legacy-android8` backport branch has been retired; do not
recreate it. API-specific seams live in `app/src/compat` and
`app/src/future`.

Before deleting any branch whose name you are not 100% sure about,
stop and ask the user.

This rule applies regardless of how the cleanup was framed
("delete dangling branches", "prune stale refs", "wipe worktrees",
`git push origin --delete ...`, `git branch -D`, `git worktree remove`,
etc.).

## ASCII-only sources

Everything committed to this repo must be plain ASCII: no em/en dashes,
arrows, ellipses, bullets, box-drawing characters, typographic quotes,
or any other non-ASCII character. Use ASCII equivalents instead:
`-`, `->`, `...`, `"`, `x`, `>=`, `~`.

Enforced by `scripts/check_ascii.sh`: `./build.sh` hard-fails on violations.
No GitHub Actions workflows - all gates run locally via `./build.sh`.

Documented exceptions (allowlisted in `scripts/check_ascii.sh`; keep the
two lists in sync):

- `*/src/main/res/values*/` - localization resources, every locale
  including the default `values/` (user-facing typography is correct there).
- `*/src/test/resources/fixtures/` - captured real server responses;
  bytes must stay byte-faithful for parser tests.
- `*/src/release/baseline-prof.txt` and `startup-prof.txt` - generated AOT
  profiles (may contain non-ASCII from dex descriptors).
- `TRANSLATIONS.md` - documents typographic punctuation for translators.
- `gradlew` - Gradle-generated, never hand-edited.
- Unicode-behavior code and tests, where the non-ASCII IS the tested
  behavior: `LocaleCollation.kt`, `LocaleCollationTest.kt`,
  `NetworkUtilsTest.kt` (sanitizes accented filenames), `TracksHeaderLabelTest.kt`
  (asserts the localized middle-dot separator),
  `YouTubeMusicSearchParser.kt` + `YouTubeMusicSearchParserTest.kt`
  (YouTube Music sends a literal bullet separator),
  `SubTag.kt` (real Bandcamp tag slugs with accents).
- Binary assets (png/webp/jar/jks/...).
