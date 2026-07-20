# Workflow test procedures

How Dustvalve Next verifies that standard user workflows actually work,
introduced after v0.5.0 shipped with local playback dead (stuck at 0:00) and
instant crashes on Bandcamp / YouTube Music - none of which any existing test
exercised.

## The four automated tiers

1. **Tier 1 - JVM workflow tests** (`app/src/test/.../workflow/`):
   Robolectric + media3-test-utils drive the REAL PlaybackManager /
   QueueManager / PlayerViewModel with a REAL ExoPlayer decoding checked-in
   audio fixtures, and boot the REAL MainActivity + Hilt graph to click
   through every screen. Pinned regressions: playback position must advance
   past 0:00; a failed YouTube stream resolve must skip playback with a
   snackbar (never crash); every provider tab and detail screen must compose
   without crashing. Runs in `testDebugUnitTest` (CI: check job).
   Local run: `./build.sh --workflow-tests`

2. **Tier 2 - emulator smoke** (`app/src/androidTest/.../smoke/`):
   Gradle Managed Device `pixel7aApi37` (16 KB page-size Google APIs image -
   the only published API 37 phone image). Installs the real APK, seeds
   MediaStore with bundled tones, taps a track, and asserts the elapsed
   label leaves 0:00 AND a real MediaController reports progress; opens
   Bandcamp / YouTube Music without crashing. CI: emulator-smoke job.
   Local run: `./build.sh --smoke`

3. **Tier 3 - emulator E2E** (`app/src/androidTest/.../e2e/`):
   Full user workflows: playback controls, playlists per provider,
   mini/full player, settings persistence, deep links, downloads. Hermetic
   and `@LiveNetwork` (real Bandcamp/YouTube - a deliberate decision so
   scraper breakage is caught) run as separate CI invocations so a service
   outage never masks a hermetic regression. CI: emulator-e2e job.
   Local run: `./build.sh --e2e` / `./build.sh --e2e-live`

4. **Tier 4 - shipped-config smoke** (`shippedsmoke/`):
   The other three tiers all instrument an APK that is not the one users
   install. Tiers 1-2 use debug builds; the release lane
   (`-PtestReleaseBuild`, CI: emulator-smoke-release) must apply
   `app/proguard-test-support.pro` so the instrumentation APK can link
   against app-provided classes, and that keeps every non-app class - so it
   proves app-code minification works but says nothing about library
   minification in the shipped APK. Since Hilt, Room and
   kotlinx-serialization are reflection- and codegen-heavy, that is exactly
   where a missing keep rule hides.
   `:shippedsmoke` is a SELF-INSTRUMENTING `com.android.test` module: it
   carries its own dependencies, runs in its OWN process, and reaches the app
   only through UiAutomator by resource id, so `:app`'s release variant can be
   built with `proguard-rules.pro` ALONE. The
   `android.experimental.self-instrumenting` property is load-bearing, not
   decoration: without it AGP deduplicates app-provided classes out of this
   APK and runs the instrumentation inside the app's process, so the runner
   itself dies in `AndroidJUnitRunner.onCreate` with NoClassDefFoundError
   (kotlin.LazyKt) on library classes the shipped APK strips - the same
   dedup-then-strip trap that broke the `-PtestReleaseBuild` lane.
   Deliberately shallow - cold start reaches a
   composed nav bar, and Settings opens (Hilt graph + DataStore +
   serialization) - because its job is only to catch an APK that dies on
   launch or on first touching a reflective subsystem.
   CI: emulator-smoke-shipped job. Never pass `-PtestReleaseBuild` to it;
   that re-introduces the test-support keeps and defeats the point.

Plus the opt-in JVM live smokes gated on `DUSTVALVE_LIVE_NET=1`
(`./build.sh --live-net`) and the manual `docs/RELEASE_CHECKLIST.md`.

## Scenario catalogs (the backlog)

Hyper-exhaustive per-area scenario lists; every automated test cites its
catalog id, and unimplemented ids are the prioritized backlog:

- catalog-local.md (130) - local library, playback, queue, player shell
- catalog-bandcamp.md (148) - discover, search, album/artist, downloads,
  account
- catalog-youtube.md (78) - deep links, search/discover, streams, mixes
- catalog-youtube-music.md (95) - home shelves, chips, search, delegation
- catalog-settings.md (~110) - every toggle, navigation, persistence

## Live-network policy

- `@LiveNetwork` tests retry up to 2 extra times (RetryRule, 5s backoff).
- Known-flaky live tests go in `app/src/androidTest/resources/quarantine.txt`
  (failures become skips, passes still pass). Quarantining REQUIRES a filed
  issue; review the list before every release.
- During a documented service incident, dispatch check.yml manually with
  `live_soft_fail: true`.

## Notes

- The GMD needs an emulator able to boot API 36+ images; some bleeding-edge
  host distros segfault QEMU - use the CI jobs in that case.
- Instrumentation runs against the REAL app object graph (no
  HiltTestApplication): what the tests exercise is what users run.
