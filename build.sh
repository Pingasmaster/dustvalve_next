#!/usr/bin/env bash
#
# Usage:
#   ./build.sh                    # RELEASE path: regen baseline+startup profiles (GMD),
#                                 # bump version, clean + ASCII + ktlint + detekt + lint
#                                 # + tests + assemble harnesses + assemble APKs,
#                                 # then one-shot NetBird APK HTTP serve for both
#   ./build.sh --debug            # DEV path: same gates/assemble, but skip release-only
#                                 # steps (baseline regen + version bump), then copy the
#                                 # debug APKs to the repo root and serve those
#   ./build.sh --clean            # gradle clean + remove APKs + exit
#   ./build.sh --format           # ktlintFormat + exit (no build)
#   ./build.sh --build-health     # full build + dependency-analysis buildHealth report
#   ./build.sh --workflow-tests   # Tier 1 JVM workflow tests only (fast) + exit
#   ./build.sh --smoke            # Tier 2 on-device smoke on GMD pixel7aApi37 + exit
#   ./build.sh --smoke-release    # Tier 2 smoke against the MINIFIED release APK + exit
#   ./build.sh --smoke-shipped    # Tier 4 smoke against the APK AS SHIPPED + exit
#   ./build.sh --e2e              # Tier 3 hermetic E2E on GMD pixel7aApi37 + exit
#   ./build.sh --e2e-live         # Tier 3 LIVE E2E (real Bandcamp/YouTube) + exit
#   ./build.sh --live-net         # DUSTVALVE_LIVE_NET=1 gated JVM live smokes + exit
#   ./build.sh --macrobenchmark   # advisory emulator macrobenchmarks
#   ./build.sh --publish          # serve existing root release APKs over NetBird HTTP + exit
#   ./build.sh --publish-debug    # serve existing root debug APKs over NetBird HTTP + exit
#   ./build.sh --block-on-outdated
#                                 # refuse to build when any catalog pin is behind
#                                 # (default is to auto-bump pins, then continue)
#
# Every build mode first runs scripts/check_latest_deps.py --apply, which bumps
# any version in gradle/libs.versions.toml that is behind the newest release
# published to Google Maven / Maven Central / the Gradle Plugin Portal, prints
# what changed, then continues. Pre-releases count: alphas, betas and RCs are
# all valid "latest" targets. Pass --block-on-outdated to keep the old
# refuse-to-build behavior instead of rewriting the catalog.
#
# After a successful full build, scripts/apk_http_serve.sh publishes both
# http://<netbird-fqdn>:8765/app-release.apk (compat, Android 8+) and
# http://<netbird-fqdn>:8765/app-release-future.apk (future, Android 17) until
# both are downloaded once, 10 minutes, or the next ./build.sh invocation.
# A --debug build (and --publish-debug) serve the same way under
# app-debug.apk / app-debug-future.apk so the URLs stay stable.
#
# User-facing speed: default builds ALWAYS regenerate baseline-prof.txt +
# startup-prof.txt (needs KVM) so release APKs ship fresh AOT hints. R8
# minify + resource shrink already run on assemble*Release. Macrobenchmark
# only measures - it does not speed up users, so it stays opt-in.
#
# IMPORTANT: Do NOT manually remove the global Android-apps build lock unless
# you have user approval and have confirmed no process is using it (check with
# `fuser ~/.cache/android-apps/build.lock` or `lsof` on that path). The lock is
# shared across dustvalve_next, calc, compass, and STT_premium so only one of
# those builds/cleans runs at a time. Deleting the file while a holder is
# alive can break flock (new openers get a new inode).
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

DO_CLEAN_ONLY=0
DO_FORMAT=0
DO_BUILD_HEALTH=0
DO_WORKFLOW_TESTS=0
DO_SMOKE=0
DO_SMOKE_RELEASE=0
DO_SMOKE_SHIPPED=0
DO_E2E=0
DO_E2E_LIVE=0
DO_LIVE_NET=0
DO_MACROBENCHMARK=0
DO_PUBLISH=0
DO_PUBLISH_DEBUG=0
DO_DEBUG=0
BLOCK_ON_OUTDATED=0

ROOT_APK_COMPAT="app-release.apk"
ROOT_MAPPING_COMPAT="app-release-mapping.txt"
ROOT_APK_FUTURE="app-release-future.apk"
ROOT_MAPPING_FUTURE="app-release-future-mapping.txt"

# Gradle debug outputs; --debug copies them to the root names below.
DEBUG_APK_COMPAT="app/build/outputs/apk/compat/debug/app-compat-debug.apk"
DEBUG_APK_FUTURE="app/build/outputs/apk/future/debug/app-future-debug.apk"

# Root debug APKs (same role as ROOT_APK_* for release). --debug copies here;
# --publish-debug serves these so the NetBird URLs stay app-debug.apk /
# app-debug-future.apk. Deliberately NOT the release names: a dev build must
# never clobber the artifacts a release build published there.
ROOT_APK_DEBUG_COMPAT="app-debug.apk"
ROOT_APK_DEBUG_FUTURE="app-debug-future.apk"

for arg in "$@"; do
    case "$arg" in
        --clean)             DO_CLEAN_ONLY=1 ;;
        --format)            DO_FORMAT=1 ;;
        --build-health)      DO_BUILD_HEALTH=1 ;;
        --workflow-tests)    DO_WORKFLOW_TESTS=1 ;;
        --smoke)             DO_SMOKE=1 ;;
        --smoke-release)     DO_SMOKE_RELEASE=1 ;;
        --smoke-shipped)     DO_SMOKE_SHIPPED=1 ;;
        --e2e)               DO_E2E=1 ;;
        --e2e-live)          DO_E2E_LIVE=1 ;;
        --live-net)          DO_LIVE_NET=1 ;;
        --macrobenchmark)    DO_MACROBENCHMARK=1 ;;
        --publish)           DO_PUBLISH=1 ;;
        --publish-debug)     DO_PUBLISH_DEBUG=1 ;;
        --debug)             DO_DEBUG=1 ;;
        --block-on-outdated) BLOCK_ON_OUTDATED=1 ;;
        *)
            echo "Unknown arg: $arg (accepted: --clean, --format, --build-health," \
                "--workflow-tests, --smoke, --smoke-release, --smoke-shipped," \
                "--e2e, --e2e-live, --live-net, --macrobenchmark," \
                "--publish, --publish-debug, --debug, --block-on-outdated)" >&2
            exit 2
            ;;
    esac
done

# Serve-only modes: no JDK, no catalog rewrite. Fail hard if APKs or NetBird
# are missing (apk_http_serve.sh used to soft-skip with exit 0, which made
# --publish-debug look like a successful no-op right after reboot).
./scripts/apk_http_serve.sh stop || true
if [[ "$DO_PUBLISH" -eq 1 ]]; then
    ./scripts/apk_http_serve.sh start "$ROOT_APK_COMPAT" "$ROOT_APK_FUTURE"
    exit 0
fi
if [[ "$DO_PUBLISH_DEBUG" -eq 1 ]]; then
    ./scripts/apk_http_serve.sh start "$ROOT_APK_DEBUG_COMPAT" "$ROOT_APK_DEBUG_FUTURE"
    exit 0
fi

# Prefer a real JDK 26, then wrap it so Gradle Worker Daemons (ktlint) also
# get the JEP 498 / JEP 472 opt-in flags. Arch ships JDK 26 as
# extra/jdk-openjdk -> /usr/lib/jvm/java-26-openjdk (also via default).
if [[ -z "${JAVA_HOME:-}" || "${JAVA_HOME}" == "${SCRIPT_DIR}/.jdk26-home" ]]; then
    unset JAVA_HOME
    for candidate in \
        /usr/lib/jvm/java-26-openjdk \
        /usr/lib/jvm/default \
        /usr/lib/jvm/java-26-openjdk-amd64 \
        /usr/lib/jvm/temurin-26-jdk-amd64 \
        "${HOME}/.jdks/jdk-26"; do
        if [[ -x "${candidate}/bin/java" ]]; then
            ver="$("${candidate}/bin/java" -version 2>&1 | head -1 || true)"
            if [[ "$ver" == *'"26'* || "$ver" == *' 26.'* ]]; then
                export JAVA_HOME="$candidate"
                break
            fi
        fi
    done
fi
# shellcheck source=scripts/ensure-jdk26-home.sh
source "$SCRIPT_DIR/scripts/ensure-jdk26-home.sh"

export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:+$JAVA_TOOL_OPTIONS }--sun-misc-unsafe-memory-access=allow --enable-native-access=ALL-UNNAMED"

# Keep dependencies on the newest published release. Default: auto-bump every
# referenced key in gradle/libs.versions.toml (Google Maven / Maven Central /
# Gradle Plugin Portal; alphas/betas/RCs count), print what changed, continue.
# --block-on-outdated restores the old refuse-to-build gate. Held pins still
# use a "# hold: <reason>" comment on the catalog line.
check_dependency_freshness() {
    if [[ "$BLOCK_ON_OUTDATED" -eq 1 ]]; then
        if ! python3 ./scripts/check_latest_deps.py; then
            echo "ERROR: dependencies are not on their latest versions (see above)." >&2
            echo "Re-run without --block-on-outdated to auto-update, or add a '# hold:'." >&2
            exit 1
        fi
        return 0
    fi
    if ! python3 ./scripts/check_latest_deps.py --apply; then
        echo "ERROR: dependency freshness check failed (see above)." >&2
        exit 1
    fi
}

check_dependency_freshness

acquire_lock() {
    local lock_dir="${XDG_CACHE_HOME:-$HOME/.cache}/android-apps"
    mkdir -p "$lock_dir"
    LOCKFILE="$lock_dir/build.lock"
    exec 9>"$LOCKFILE"
    if ! flock -n 9; then
        echo "Another Android app build/clean is already running" \
            "(dustvalve_next/calc/compass/STT_premium share $LOCKFILE). Exiting."
        exit 1
    fi
}

GMD_GPU=(-Pandroid.testoptions.manageddevices.emulator.gpu=swiftshader_indirect)

regenerate_baseline_profiles() {
    if [[ ! -e /dev/kvm ]]; then
        echo "ERROR: /dev/kvm missing; GMD baseline generation needs KVM." >&2
        echo "Use ./build.sh --debug to skip baselines for a non-release build." >&2
        exit 1
    fi
    ./gradlew :baselineprofile:pixel7aApi37Setup "${GMD_GPU[@]}"
    ./gradlew :baselineprofile:pixel7aApi37FutureReleaseAndroidTest "${GMD_GPU[@]}" \
        -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=baselineprofile
    ./scripts/assert_tests_ran.sh 1 baselineprofile
    chmod +x ./scripts/install_baseline_profiles.sh
    ./scripts/install_baseline_profiles.sh
}

if [[ "$DO_CLEAN_ONLY" -eq 1 ]]; then
    acquire_lock
    ./gradlew clean
    rm -f "$ROOT_APK_COMPAT" "$ROOT_MAPPING_COMPAT" "$ROOT_APK_FUTURE" "$ROOT_MAPPING_FUTURE"
    rm -f "$ROOT_APK_DEBUG_COMPAT" "$ROOT_APK_DEBUG_FUTURE"
    echo "Clean complete."
    exit 0
fi

if [[ "$DO_FORMAT" -eq 1 ]]; then
    acquire_lock
    ./gradlew ktlintFormat
    echo "ktlintFormat complete. Re-run ./build.sh without --format to verify."
    exit 0
fi

if [[ "$DO_WORKFLOW_TESTS" -eq 1 ]]; then
    acquire_lock
    ./gradlew :app:testFutureDebugUnitTest --tests 'com.dustvalve.next.android.workflow.*'
    echo "Workflow tests complete."
    exit 0
fi

if [[ "$DO_LIVE_NET" -eq 1 ]]; then
    acquire_lock
    DUSTVALVE_LIVE_NET=1 ./gradlew :app:testFutureDebugUnitTest --tests '*Live*'
    echo "Live-network JVM smokes complete."
    exit 0
fi

if [[ "$DO_SMOKE" -eq 1 ]]; then
    acquire_lock
    ./gradlew :app:pixel7aApi37Setup "${GMD_GPU[@]}"
    ./gradlew :app:pixel7aApi37FutureDebugAndroidTest "${GMD_GPU[@]}" \
        -Pandroid.testInstrumentationRunnerArguments.annotation=com.dustvalve.next.android.testing.SmokeTest
    ./scripts/assert_tests_ran.sh 1 app
    echo "Smoke suite complete."
    exit 0
fi

if [[ "$DO_SMOKE_RELEASE" -eq 1 ]]; then
    acquire_lock
    ./gradlew :app:pixel7aApi37Setup -PtestReleaseBuild "${GMD_GPU[@]}"
    ./gradlew :app:pixel7aApi37FutureReleaseAndroidTest -PtestReleaseBuild "${GMD_GPU[@]}"
    ./scripts/assert_tests_ran.sh 1 app
    echo "Release smoke suite complete."
    exit 0
fi

if [[ "$DO_SMOKE_SHIPPED" -eq 1 ]]; then
    acquire_lock
    ./gradlew :shippedsmoke:pixel7aApi37Setup "${GMD_GPU[@]}"
    ./gradlew :shippedsmoke:pixel7aApi37FutureReleaseAndroidTest "${GMD_GPU[@]}"
    ./scripts/assert_tests_ran.sh 1 shippedsmoke
    echo "Shipped-config smoke complete."
    exit 0
fi

if [[ "$DO_E2E" -eq 1 ]]; then
    acquire_lock
    ./gradlew :app:pixel7aApi37Setup "${GMD_GPU[@]}"
    ./gradlew :app:pixel7aApi37FutureDebugAndroidTest "${GMD_GPU[@]}" \
        -Pandroid.testInstrumentationRunnerArguments.notAnnotation=com.dustvalve.next.android.testing.LiveNetwork
    ./scripts/assert_tests_ran.sh 1 app
    echo "Hermetic E2E suite complete."
    exit 0
fi

if [[ "$DO_E2E_LIVE" -eq 1 ]]; then
    acquire_lock
    echo "WARNING: this suite hits the real Bandcamp and YouTube services." >&2
    ./gradlew :app:pixel7aApi37Setup "${GMD_GPU[@]}"
    ./gradlew :app:pixel7aApi37FutureDebugAndroidTest "${GMD_GPU[@]}" \
        -Pandroid.testInstrumentationRunnerArguments.annotation=com.dustvalve.next.android.testing.LiveNetwork
    ./scripts/assert_tests_ran.sh 1 app
    echo "Live E2E suite complete."
    exit 0
fi

if [[ "$DO_MACROBENCHMARK" -eq 1 ]]; then
    acquire_lock
    ./gradlew :macrobenchmark:pixel7aApi37Setup "${GMD_GPU[@]}"
    ./gradlew :macrobenchmark:pixel7aApi37FutureReleaseAndroidTest "${GMD_GPU[@]}" \
        -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.suppressErrors=EMULATOR
    ./scripts/assert_tests_ran.sh 1 macrobenchmark
    echo "Macrobenchmark complete (emulator numbers are advisory)."
    exit 0
fi

acquire_lock

./scripts/check_ascii.sh

GRADLE_APK_FUTURE="app/build/outputs/apk/future/release/app-future-release.apk"
GRADLE_MAPPING_FUTURE="app/build/outputs/mapping/futureRelease/mapping.txt"
GRADLE_APK_COMPAT="app/build/outputs/apk/compat/release/app-compat-release.apk"
GRADLE_MAPPING_COMPAT="app/build/outputs/mapping/compatRelease/mapping.txt"
BUILD_GRADLE="app/build.gradle.kts"

VERSION_BUMPED=0
CURRENT_CODE=""
CURRENT_NAME=""
NEW_CODE=""
NEW_NAME=""

revert_version_bump() {
    if [[ "$VERSION_BUMPED" -ne 1 ]]; then
        return 0
    fi
    sed -i "s/val baseVersionCode = $NEW_CODE/val baseVersionCode = $CURRENT_CODE/" "$BUILD_GRADLE"
    sed -i "s/val baseVersionName = \"$NEW_NAME\"/val baseVersionName = \"$CURRENT_NAME\"/" "$BUILD_GRADLE"
    echo "Build failed: reverted version to $CURRENT_NAME ($CURRENT_CODE)." >&2
}

if [[ "$DO_DEBUG" -eq 1 ]]; then
    echo "Debug build: skipping baseline profile regeneration and version bump."
else
    echo "Release build: regenerating baseline + startup profiles..."
    if ! regenerate_baseline_profiles; then
        exit 1
    fi
    echo "Baseline profiles installed under app/src/release/."

    CURRENT_CODE=$(sed -n 's/.*val baseVersionCode = \([0-9][0-9]*\).*/\1/p' "$BUILD_GRADLE" | head -1)
    CURRENT_NAME=$(sed -n 's/.*val baseVersionName = "\([^"]*\)".*/\1/p' "$BUILD_GRADLE" | head -1)

    if [[ -z "$CURRENT_CODE" || -z "$CURRENT_NAME" ]]; then
        echo "ERROR: could not parse baseVersionCode/baseVersionName from $BUILD_GRADLE" >&2
        exit 1
    fi

    NEW_CODE=$((CURRENT_CODE + 1))
    NEW_NAME=$(echo "$CURRENT_NAME" | awk -F. -v OFS=. '{$NF=$NF+1; print}')

    sed -i "s/val baseVersionCode = $CURRENT_CODE/val baseVersionCode = $NEW_CODE/" "$BUILD_GRADLE"
    sed -i "s/val baseVersionName = \"$CURRENT_NAME\"/val baseVersionName = \"$NEW_NAME\"/" "$BUILD_GRADLE"
    VERSION_BUMPED=1

    echo "Bumped version: $CURRENT_NAME ($CURRENT_CODE) -> $NEW_NAME ($NEW_CODE)"
fi

# `clean` runs as its own invocation. Inside one task graph Gradle is free to
# run it in parallel with ktlintCheck, and it does: ktlint's source walk then
# trips over app/build/ intermediates that :app:clean is deleting underneath it
# and fails with NoSuchFileException. Nothing declares an ordering between the
# two, so the only reliable fix is to finish the delete first.
if ! ./gradlew clean; then
    revert_version_bump
    exit 1
fi

GRADLE_TASKS=(
    ktlintCheck
    detekt
    lintCompatRelease
    lintFutureRelease
    # Libraries have no api flavors - their unit tests stay testDebugUnitTest.
    testDebugUnitTest
    testCompatDebugUnitTest
    testFutureDebugUnitTest
    :macrobenchmark:assembleFutureRelease
    :baselineprofile:assembleFutureRelease
    :shippedsmoke:assembleFutureRelease
    assembleCompatDebug
    assembleFutureDebug
    assembleCompatRelease
    assembleFutureRelease
)

if ! ./gradlew "${GRADLE_TASKS[@]}"; then
    revert_version_bump
    exit 1
fi

if [[ "$DO_BUILD_HEALTH" -eq 1 ]]; then
    ./gradlew buildHealth || true
    REPORT="build/reports/dependency-analysis/build-health-report.txt"
    [[ -f "$REPORT" ]] && echo "Dependency-analysis report: $REPORT"
fi

# A dev build exposes its own APKs and stops here: the release artifacts at the
# root belong to the last release build, and its version bump, so overwriting
# them with an unbumped dev build is how you end up serving the wrong APK.
if [[ "$DO_DEBUG" -eq 1 ]]; then
    rm -f "$ROOT_APK_DEBUG_COMPAT" "$ROOT_APK_DEBUG_FUTURE"
    cp "$DEBUG_APK_COMPAT" "$ROOT_APK_DEBUG_COMPAT"
    echo "Copied compat debug APK to $ROOT_APK_DEBUG_COMPAT"
    cp "$DEBUG_APK_FUTURE" "$ROOT_APK_DEBUG_FUTURE"
    echo "Copied future debug APK to $ROOT_APK_DEBUG_FUTURE"
    ./scripts/apk_http_serve.sh start --optional "$ROOT_APK_DEBUG_COMPAT" "$ROOT_APK_DEBUG_FUTURE"
    exit 0
fi

rm -f "$ROOT_APK_COMPAT" "$ROOT_MAPPING_COMPAT" "$ROOT_APK_FUTURE" "$ROOT_MAPPING_FUTURE"
cp "$GRADLE_APK_COMPAT" "$ROOT_APK_COMPAT"
echo "Copied compat release APK to $ROOT_APK_COMPAT"
if [[ -f "$GRADLE_MAPPING_COMPAT" ]]; then
    cp "$GRADLE_MAPPING_COMPAT" "$ROOT_MAPPING_COMPAT"
    echo "Copied compat release mapping to $ROOT_MAPPING_COMPAT"
fi
cp "$GRADLE_APK_FUTURE" "$ROOT_APK_FUTURE"
echo "Copied future release APK to $ROOT_APK_FUTURE"
if [[ -f "$GRADLE_MAPPING_FUTURE" ]]; then
    cp "$GRADLE_MAPPING_FUTURE" "$ROOT_MAPPING_FUTURE"
    echo "Copied future release mapping to $ROOT_MAPPING_FUTURE"
fi

# Serve both flavor APKs (compat + future). Soft-skip if NetBird is down.
./scripts/apk_http_serve.sh start --optional "$ROOT_APK_COMPAT" "$ROOT_APK_FUTURE"
