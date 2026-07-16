# Dustvalve Next - agent guidelines

## Workflow tests - run before shipping behavior changes

Three automated tiers (see docs/testing/README.md):
- `./build.sh --workflow-tests` - fast JVM regression net (real ExoPlayer +
  real MainActivity under Robolectric). Run this after ANY change touching
  playback, navigation, or the provider screens.
- `./build.sh --smoke` / `--e2e` / `--e2e-live` - Gradle Managed Device
  suites (`pixel6Api33`). If the host QEMU cannot boot modern images, rely
  on the check.yml emulator-smoke / emulator-e2e CI jobs instead.
- Scenario backlog lives in docs/testing/catalog-*.md; new E2E tests must
  reference their catalog id.

## Protected branches - DO NOT DELETE OR FORCE-PUSH

When asked to clean up "dangling" branches, worktrees, or any other git
state, the following branches are **legitimate and must never be erased**:

- `master`     - the canonical default branch.
- `legacy-android8` - the long-lived Android 8-16 backport branch that
  ships as the **default APK** on every GitHub release.

They are NOT dangling, not orphaned, and not stale - even if a sweep
finds no recent commits on them, that does NOT make them safe to delete.
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

Enforced by `scripts/check_ascii.sh`: CI fails on violations (check.yml,
"ASCII-only source check" step) and `./build.sh` prints a warning.

Documented exceptions (allowlisted in `scripts/check_ascii.sh`; keep the
two lists in sync):

- `app/src/main/res/values*/` - localization resources, every locale
  including the default `values/` (user-facing typography is correct there).
- `*/src/test/resources/fixtures/` - captured real server responses;
  bytes must stay byte-faithful for parser tests.
- `TRANSLATIONS.md` - documents typographic punctuation for translators.
- `gradlew` - Gradle-generated, never hand-edited.
- Unicode-behavior code and tests, where the non-ASCII IS the tested
  behavior: `LocaleCollation.kt`, `LocaleCollationTest.kt`,
  `NetworkUtilsTest.kt` (sanitizes accented filenames), `TracksHeaderLabelTest.kt`
  (asserts the localized middle-dot separator),
  `YouTubeMusicSearchParser.kt` + `YouTubeMusicSearchParserTest.kt`
  (YouTube Music sends a literal bullet separator),
  `GenreSubTags.kt` (real Bandcamp tag slugs with accents).
- Binary assets (png/webp/jar/jks/...).

## Legacy branch policy (this branch: legacy-android8)

This branch ships as the default APK (dustvalve_next.apk) for Android 8.0
(API 26) through Android 16.x. Non-negotiable deltas vs master:

- app/build.gradle.kts: minSdk = 26, versionNameSuffix = "-legacy",
  isCoreLibraryDesugaringEnabled = true + coreLibraryDesugaring dep
  (all library modules also pin minSdk = 26).
- versionCode/versionName always match the latest master; only the
  "-legacy" suffix differentiates the installed builds.
- Manifest gates: FOREGROUND_SERVICE_MEDIA_PLAYBACK (minSdkVersion 34),
  READ_MEDIA_AUDIO (minSdkVersion 33), READ_EXTERNAL_STORAGE
  (maxSdkVersion 32).
- Runtime Build.VERSION.SDK_INT guards are REQUIRED here (master's
  "no SDK checks, raise minSdk" rule does not apply); see
  util/LegacyPermissions.kt::legacyAudioPermission().
- Backports from master are squashed replays (see commit cfdedec);
  never rebase or force-push this branch.
