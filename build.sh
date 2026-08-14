#!/usr/bin/env bash
#
# Shared Android-apps build.sh. Keep this file aligned across dustvalve_next,
# calc, compass, and core. Only the PROJECT CONFIG block
# (and any extra flags/lanes it declares) may differ. When you change shared
# behavior, port it to the other three the same day.
#
# Usage:
#   ./build.sh                    # RELEASE path: regen baseline+startup profiles (GMD),
#                                 # bump version, clean + ASCII + ktlint + detekt + lint
#                                 # + tests + assemble dual APKs, then GMD shippedsmoke
#                                 # + smoke + e2e, then one-shot NetBird APK HTTP serve
#                                 # for all four root APKs. Requires /dev/kvm.
#   ./build.sh --debug            # DEV path: same static gates + unit tests + assemble,
#                                 # but skip release-only steps (baseline regen, version
#                                 # bump, GMD device gates), then copy the debug APKs
#                                 # to the repo root and serve those
#   ./build.sh --clean            # gradle clean + remove root APKs + exit
#   ./build.sh --format           # ktlintFormat + exit (no build)
#   ./build.sh --build-health     # full build + dependency-analysis buildHealth report
#   ./build.sh --smoke            # GMD Pixel 7a API 37 @SmokeTest (future flavor)
#   ./build.sh --e2e              # GMD hermetic androidTest (see PROJECT CONFIG filter)
#   ./build.sh --smoke-shipped    # :shippedsmoke release lane (future flavor)
#   ./build.sh --macrobenchmark   # advisory emulator macrobenchmarks (future)
#   ./build.sh --publish          # serve existing root release + debug APKs over
#                                 # NetBird HTTP + exit
#   ./build.sh --publish-debug    # serve existing root debug APKs over NetBird HTTP + exit
#   ./build.sh --block-on-outdated
#                                 # refuse to build when any catalog pin is behind
#                                 # (default is to auto-bump pins, then continue)
#   ./build.sh --workflow-tests   # Tier 1 JVM workflow tests only (fast) + exit
#   ./build.sh --smoke-release    # Tier 2 smoke against the MINIFIED release APK + exit
#   ./build.sh --e2e-live         # Tier 3 LIVE E2E (real Bandcamp/YouTube) + exit
#   ./build.sh --live-net         # DUSTVALVE_LIVE_NET=1 gated JVM live smokes + exit

#
# Every build mode first runs scripts/check_latest_deps.py --apply, which bumps
# any version in gradle/libs.versions.toml that is behind the newest release
# published to Google Maven / Maven Central / the Gradle Plugin Portal, prints
# what changed, then continues. Pre-releases count: alphas, betas and RCs are
# all valid "latest" targets. Pass --block-on-outdated to keep the old
# refuse-to-build behavior instead of rewriting the catalog.
#
# After a successful full build, scripts/apk_http_serve.sh publishes all four
# root APKs until each is downloaded once, 10 minutes, or the next ./build.sh
# invocation: app-release.apk / app-release-future.apk (compat + future release)
# and app-debug.apk / app-debug-future.apk (compat + future debug). A --debug
# build serves only the debug pair so it never clobbers release root artifacts.
# --publish re-serves the same four root files from the last full build.
#
# User-facing speed: default builds ALWAYS regenerate baseline-prof.txt +
# startup-prof.txt (needs KVM) so release APKs ship fresh AOT hints. R8
# minify + resource shrink already run on assemble*Release. Default ./build.sh
# always runs shippedsmoke + smoke + e2e on GMD (needs KVM). --debug skips
# those device gates. Macrobenchmark only measures - it does not speed up
# users, so it stays opt-in.
#
# IMPORTANT: Do NOT manually remove the global Android-apps build lock unless
# you have user approval and have confirmed no process is using it (check with
# `fuser ~/.cache/android-apps/build.lock` or `lsof` on that path). The lock is
# shared across dustvalve_next, calc, compass, and core so
# only one of those builds/cleans runs at a time. Deleting the file while a
# holder is alive can break flock (new openers get a new inode).
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

./scripts/apk_http_serve.sh stop || true

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

DO_CLEAN_ONLY=0
DO_FORMAT=0
DO_BUILD_HEALTH=0
DO_SMOKE=0
DO_E2E=0
DO_SMOKE_SHIPPED=0
DO_MACROBENCHMARK=0
DO_PUBLISH=0
DO_PUBLISH_DEBUG=0
DO_DEBUG=0
BLOCK_ON_OUTDATED=0
# Extra flags used only by some apps; stay 0 elsewhere so shared omit-logic
# can test them without an "unbound variable" under set -u.
DO_WORKFLOW_TESTS=0
DO_SMOKE_RELEASE=0
DO_E2E_LIVE=0
DO_LIVE_NET=0

ROOT_APK_COMPAT="app-release.apk"
ROOT_MAPPING_COMPAT="app-release-mapping.txt"
ROOT_APK_FUTURE="app-release-future.apk"
ROOT_MAPPING_FUTURE="app-release-future-mapping.txt"

# Gradle debug outputs; --debug and the full release path copy them to the
# root names below.
DEBUG_APK_COMPAT="app/build/outputs/apk/compat/debug/app-compat-debug.apk"
DEBUG_APK_FUTURE="app/build/outputs/apk/future/debug/app-future-debug.apk"

# Root debug APKs (same role as ROOT_APK_* for release). Full builds and
# --debug copy here; --publish serves these alongside the release pair so the
# NetBird URLs stay app-debug.apk / app-debug-future.apk. Deliberately NOT the
# release names: a --debug build must never clobber the artifacts a release
# build published there.
ROOT_APK_DEBUG_COMPAT="app-debug.apk"
ROOT_APK_DEBUG_FUTURE="app-debug-future.apk"

GRADLE_APK_COMPAT="app/build/outputs/apk/compat/release/app-compat-release.apk"
GRADLE_MAPPING_COMPAT="app/build/outputs/mapping/compatRelease/mapping.txt"
GRADLE_APK_FUTURE="app/build/outputs/apk/future/release/app-future-release.apk"
GRADLE_MAPPING_FUTURE="app/build/outputs/mapping/futureRelease/mapping.txt"
BUILD_GRADLE="app/build.gradle.kts"

# Defaults; PROJECT CONFIG overrides.
HAS_COMPAT_SMOKE=0
HAS_ASR_FIXTURE=0
ELF_CHECK_PASS_APKS=1
E2E_NOT_ANNOTATION=""
PROJECT_EXTRA_FLAGS_HELP=""
BASELINE_ASSEMBLE_TASK=":baselineprofile:assembleFutureRelease"
BASELINE_TEST_TASK=":baselineprofile:pixel7aApi37FutureReleaseAndroidTest"
BASELINE_ASSERT_COUNT=1
SMOKE_ASSERT_COUNT=1
COMPAT_SMOKE_ASSERT_COUNT=1
E2E_ASSERT_COUNT=1
SHIPPED_SMOKE_ASSERT_COUNT=1
MACROBENCHMARK_ASSERT_COUNT=1

project_handle_arg() { return 1; }
project_run_extra_standalone() { return 0; }

# =============================================================================
# PROJECT CONFIG - this block is the only part that should differ per app.
# =============================================================================

REQUIRE_RELEASE_SIGNING_FLAG=(-Pdustvalve.requireReleaseSigning=true)
SMOKE_ANNOTATION="com.dustvalve.next.android.testing.SmokeTest"
E2E_NOT_ANNOTATION="com.dustvalve.next.android.testing.LiveNetwork"
HAS_COMPAT_SMOKE=0
HAS_ASR_FIXTURE=0
ELF_CHECK_PASS_APKS=0
BASELINE_ASSEMBLE_TASK=":baselineprofile:assembleFutureNonMinifiedRelease"
BASELINE_TEST_TASK=":baselineprofile:pixel7aApi37FutureNonMinifiedReleaseAndroidTest"
BASELINE_ASSERT_COUNT=1
SMOKE_ASSERT_COUNT=5
E2E_ASSERT_COUNT=11
SHIPPED_SMOKE_ASSERT_COUNT=1
MACROBENCHMARK_ASSERT_COUNT=1
PROJECT_EXTRA_FLAGS_HELP=", --workflow-tests, --smoke-release, --e2e-live, --live-net"

project_handle_arg() {
    case "$1" in
        --workflow-tests) DO_WORKFLOW_TESTS=1 ;;
        --smoke-release)  DO_SMOKE_RELEASE=1 ;;
        --e2e-live)       DO_E2E_LIVE=1 ;;
        --live-net)       DO_LIVE_NET=1 ;;
        *) return 1 ;;
    esac
    return 0
}

project_run_extra_standalone() {
    if [[ "$DO_WORKFLOW_TESTS" -eq 1 ]]; then
        acquire_lock
        gradle :app:testFutureDebugUnitTest --tests 'com.dustvalve.next.android.workflow.*'
        echo "Workflow tests complete."
        exit 0
    fi
    if [[ "$DO_LIVE_NET" -eq 1 ]]; then
        acquire_lock
        DUSTVALVE_LIVE_NET=1 gradle :app:testFutureDebugUnitTest --tests '*Live*'
        echo "Live-network JVM smokes complete."
        exit 0
    fi
    if [[ "$DO_SMOKE_RELEASE" -eq 1 ]]; then
        acquire_lock
        gradle :app:pixel7aApi37Setup -PtestReleaseBuild "${GMD_GPU[@]}"
        gradle :app:pixel7aApi37FutureReleaseAndroidTest -PtestReleaseBuild "${GMD_GPU[@]}"
        ./scripts/assert_tests_ran.sh 1 app
        echo "Release smoke suite complete."
        exit 0
    fi
    if [[ "$DO_E2E_LIVE" -eq 1 ]]; then
        acquire_lock
        echo "WARNING: this suite hits the real Bandcamp and YouTube services." >&2
        gradle :app:pixel7aApi37Setup "${GMD_GPU[@]}"
        gradle :app:pixel7aApi37FutureDebugAndroidTest "${GMD_GPU[@]}" \
            -Pandroid.testInstrumentationRunnerArguments.annotation=com.dustvalve.next.android.testing.LiveNetwork
        ./scripts/assert_tests_ran.sh 1 app
        echo "Live E2E suite complete."
        exit 0
    fi
}

# Libraries have no api flavors - their unit tests stay testDebugUnitTest.
GRADLE_TASKS=(
    ktlintCheck
    detekt
    lintCompatRelease
    lintFutureRelease
    testDebugUnitTest
    testCompatDebugUnitTest
    testFutureDebugUnitTest
    :macrobenchmark:assembleFutureRelease
    :baselineprofile:assembleFutureNonMinifiedRelease
    :shippedsmoke:assembleFutureRelease
    assembleCompatDebug
    assembleFutureDebug
    assembleCompatRelease
    assembleFutureRelease
)

# =============================================================================
# END PROJECT CONFIG
# =============================================================================

for arg in "$@"; do
    case "$arg" in
        --clean)             DO_CLEAN_ONLY=1 ;;
        --format)            DO_FORMAT=1 ;;
        --build-health)      DO_BUILD_HEALTH=1 ;;
        --smoke)             DO_SMOKE=1 ;;
        --e2e)               DO_E2E=1 ;;
        --smoke-shipped)     DO_SMOKE_SHIPPED=1 ;;
        --macrobenchmark)    DO_MACROBENCHMARK=1 ;;
        --publish)           DO_PUBLISH=1 ;;
        --publish-debug)     DO_PUBLISH_DEBUG=1 ;;
        --debug)             DO_DEBUG=1 ;;
        --block-on-outdated) BLOCK_ON_OUTDATED=1 ;;
        *)
            if ! project_handle_arg "$arg"; then
                echo "Unknown arg: $arg (accepted: --clean, --format, --build-health," \
                    "--smoke, --e2e, --smoke-shipped, --macrobenchmark," \
                    "--publish, --publish-debug, --debug, --block-on-outdated${PROJECT_EXTRA_FLAGS_HELP})" >&2
                exit 2
            fi
            ;;
    esac
done

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

# Mandatory on every mode except serve-only publish flags (must not rewrite the
# catalog while handing out already-built APKs).
if [[ "$DO_PUBLISH" -eq 0 && "$DO_PUBLISH_DEBUG" -eq 0 ]]; then
    check_dependency_freshness
fi

acquire_lock() {
    local lock_dir="${XDG_CACHE_HOME:-$HOME/.cache}/android-apps"
    mkdir -p "$lock_dir"
    LOCKFILE="$lock_dir/build.lock"
    exec 9>"$LOCKFILE"
    if ! flock -n 9; then
        echo "Another Android app build/clean is already running" \
            "(dustvalve_next/calc/compass/core share $LOCKFILE). Exiting."
        exit 1
    fi
}

GMD_GPU=(-Pandroid.testoptions.manageddevices.emulator.gpu=swiftshader_indirect)

# Route Gradle through scripts/run_gradle.sh when the repo has a noise filter;
# otherwise call ./gradlew directly. Keep this as an array so `timeout` can
# exec it (timeout cannot invoke a shell function).
GRADLE_CMD=(./gradlew)
if [[ -x ./scripts/run_gradle.sh ]]; then
    GRADLE_CMD=(./scripts/run_gradle.sh)
fi
gradle() {
    "${GRADLE_CMD[@]}" "$@"
}

require_kvm() {
    if [[ ! -e /dev/kvm ]]; then
        echo "ERROR: /dev/kvm missing; GMD instrumented tests need KVM." >&2
        echo "Use ./build.sh --debug to skip GMD for a non-release build." >&2
        return 1
    fi
}

start_asr_fixture_push_waiter() {
    local wait_secs="${1:-240}"
    ./scripts/push_asr_android_test_fixture.sh --wait-secs "$wait_secs" &
    ASR_FIXTURE_PUSH_PID=$!
}

await_asr_fixture_push_waiter() {
    if [[ -n "${ASR_FIXTURE_PUSH_PID:-}" ]]; then
        wait "$ASR_FIXTURE_PUSH_PID" || true
        unset ASR_FIXTURE_PUSH_PID
    fi
}

gmd_pre_android_test() {
    if [[ "$HAS_ASR_FIXTURE" -eq 1 ]]; then
        start_asr_fixture_push_waiter 240
    fi
}

gmd_post_android_test() {
    if [[ "$HAS_ASR_FIXTURE" -eq 1 ]]; then
        await_asr_fixture_push_waiter
    fi
}

# Production release path requires the real keystore. --debug omits this so
# local assemble without release-keystore.jks / .password-signing-keys still
# works (AGP debug signing fallback in app/build.gradle.kts). Standalone
# --smoke / --e2e / --e2e-live use debug androidTest APKs and also omit it.
# --smoke-shipped / --smoke-release / --macrobenchmark omit it so a missing
# keystore can still drive a debug-signed release variant; default ./build.sh
# still fails assembleRelease.
REQUIRE_RELEASE_SIGNING_ARGS=()
if [[ "$DO_DEBUG" -eq 0 && "$DO_SMOKE" -eq 0 && "$DO_E2E" -eq 0 && "$DO_SMOKE_SHIPPED" -eq 0 && "$DO_PUBLISH" -eq 0 && "$DO_PUBLISH_DEBUG" -eq 0 && "$DO_MACROBENCHMARK" -eq 0 && "$DO_SMOKE_RELEASE" -eq 0 && "$DO_E2E_LIVE" -eq 0 ]]; then
    REQUIRE_RELEASE_SIGNING_ARGS=("${REQUIRE_RELEASE_SIGNING_FLAG[@]}")
fi

# Device lanes used by the default release path and by the standalone --smoke /
# --e2e / --smoke-shipped modes. Kept as functions so release and opt-in share
# one assertion floor.
run_smoke_tests() {
    require_kvm || return 1
    gradle :app:pixel7aApi37Setup "${GMD_GPU[@]}" || return 1
    gmd_pre_android_test
    local app_timeout_sec="${APP_ANDROID_TEST_TIMEOUT_SEC:-600}"
    local rc=0
    timeout --foreground "${app_timeout_sec}s" \
        "${GRADLE_CMD[@]}" :app:pixel7aApi37FutureDebugAndroidTest "${GMD_GPU[@]}" \
            -Pandroid.testInstrumentationRunnerArguments.annotation="$SMOKE_ANNOTATION" \
        || {
            rc=$?
            if [[ "$rc" -eq 124 ]]; then
                echo "ERROR: :app smoke GMD androidTest exceeded ${app_timeout_sec}s" >&2
            fi
        }
    gmd_post_android_test
    [[ "$rc" -eq 0 ]] || return "$rc"
    ./scripts/assert_tests_ran.sh "$SMOKE_ASSERT_COUNT" app || return 1
}

run_compat_smoke_tests() {
    require_kvm || return 1
    gradle :app:pixel7aApi34Setup "${GMD_GPU[@]}" || return 1
    gmd_pre_android_test
    local app_timeout_sec="${APP_ANDROID_TEST_TIMEOUT_SEC:-600}"
    local rc=0
    timeout --foreground "${app_timeout_sec}s" \
        "${GRADLE_CMD[@]}" :app:pixel7aApi34CompatDebugAndroidTest "${GMD_GPU[@]}" \
            -Pandroid.testInstrumentationRunnerArguments.annotation="$SMOKE_ANNOTATION" \
        || {
            rc=$?
            if [[ "$rc" -eq 124 ]]; then
                echo "ERROR: :app compat smoke GMD androidTest exceeded ${app_timeout_sec}s" >&2
            fi
        }
    gmd_post_android_test
    [[ "$rc" -eq 0 ]] || return "$rc"
    ./scripts/assert_tests_ran.sh "$COMPAT_SMOKE_ASSERT_COUNT" app || return 1
}

run_e2e_tests() {
    require_kvm || return 1
    gradle :app:pixel7aApi37Setup "${GMD_GPU[@]}" || return 1
    gmd_pre_android_test
    local app_timeout_sec="${APP_ANDROID_TEST_TIMEOUT_SEC:-900}"
    local -a e2e_args=()
    if [[ -n "$E2E_NOT_ANNOTATION" ]]; then
        e2e_args+=(-Pandroid.testInstrumentationRunnerArguments.notAnnotation="$E2E_NOT_ANNOTATION")
    fi
    local rc=0
    timeout --foreground "${app_timeout_sec}s" \
        "${GRADLE_CMD[@]}" :app:pixel7aApi37FutureDebugAndroidTest "${GMD_GPU[@]}" \
            "${e2e_args[@]}" \
        || {
            rc=$?
            if [[ "$rc" -eq 124 ]]; then
                echo "ERROR: :app e2e GMD androidTest exceeded ${app_timeout_sec}s" >&2
            fi
        }
    gmd_post_android_test
    [[ "$rc" -eq 0 ]] || return "$rc"
    ./scripts/assert_tests_ran.sh "$E2E_ASSERT_COUNT" app || return 1
}

run_shipped_smoke_tests() {
    require_kvm || return 1
    gradle :shippedsmoke:pixel7aApi37Setup "${GMD_GPU[@]}" || return 1
    local smoke_timeout_sec="${SHIPPED_SMOKE_TIMEOUT_SEC:-600}"
    timeout --foreground "${smoke_timeout_sec}s" \
        "${GRADLE_CMD[@]}" :shippedsmoke:pixel7aApi37FutureReleaseAndroidTest "${GMD_GPU[@]}" \
        || {
            local rc=$?
            if [[ "$rc" -eq 124 ]]; then
                echo "ERROR: :shippedsmoke GMD androidTest exceeded ${smoke_timeout_sec}s" >&2
            fi
            return "$rc"
        }
    ./scripts/assert_tests_ran.sh "$SHIPPED_SMOKE_ASSERT_COUNT" shippedsmoke || return 1
}

regenerate_baseline_profiles() {
    if [[ ! -e /dev/kvm ]]; then
        echo "ERROR: /dev/kvm missing; GMD baseline generation needs KVM." >&2
        echo "Use ./build.sh --debug to skip baselines for a non-release build." >&2
        exit 1
    fi
    # Retry: GMD LMK can kill the app mid-flush ("never flushed profiles").
    local attempt=1
    local max_attempts=3
    gradle "${REQUIRE_RELEASE_SIGNING_ARGS[@]}" \
        :baselineprofile:pixel7aApi37Setup "${GMD_GPU[@]}"
    while true; do
        local attempt_log
        attempt_log="$(mktemp)"
        if gradle "${REQUIRE_RELEASE_SIGNING_ARGS[@]}" \
            "$BASELINE_TEST_TASK" "${GMD_GPU[@]}" \
            -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=baselineprofile \
            >"$attempt_log" 2>&1 \
            && ./scripts/assert_tests_ran.sh "$BASELINE_ASSERT_COUNT" baselineprofile; then
            cat "$attempt_log"
            rm -f "$attempt_log"
            break
        fi
        if [[ "$attempt" -ge "$max_attempts" ]]; then
            cat "$attempt_log" >&2 || true
            rm -f "$attempt_log"
            echo "ERROR: baseline profile generation failed after ${max_attempts} attempts." >&2
            return 1
        fi
        rm -f "$attempt_log"
        echo "Baseline profile attempt ${attempt}/${max_attempts} failed; retrying..." >&2
        attempt=$((attempt + 1))
        sleep 5
    done
    chmod +x ./scripts/install_baseline_profiles.sh
    ./scripts/install_baseline_profiles.sh
}

if [[ "$DO_PUBLISH" -eq 1 ]]; then
    missing=0
    for apk in \
        "$ROOT_APK_COMPAT" "$ROOT_APK_FUTURE" \
        "$ROOT_APK_DEBUG_COMPAT" "$ROOT_APK_DEBUG_FUTURE"; do
        if [[ ! -f "$apk" ]]; then
            echo "ERROR: missing $apk (need both release and debug root APKs)." >&2
            missing=1
        fi
    done
    if [[ "$missing" -ne 0 ]]; then
        echo "Run a full ./build.sh first (or ./build.sh --debug after a release)." >&2
        exit 1
    fi
    ./scripts/apk_http_serve.sh start \
        "$ROOT_APK_COMPAT" "$ROOT_APK_FUTURE" \
        "$ROOT_APK_DEBUG_COMPAT" "$ROOT_APK_DEBUG_FUTURE"
    exit 0
fi

if [[ "$DO_PUBLISH_DEBUG" -eq 1 ]]; then
    missing=0
    for apk in "$ROOT_APK_DEBUG_COMPAT" "$ROOT_APK_DEBUG_FUTURE"; do
        if [[ ! -f "$apk" ]]; then
            echo "ERROR: missing $apk." >&2
            missing=1
        fi
    done
    if [[ "$missing" -ne 0 ]]; then
        echo "Run ./build.sh --debug (or a full release build) first." >&2
        exit 1
    fi
    ./scripts/apk_http_serve.sh start "$ROOT_APK_DEBUG_COMPAT" "$ROOT_APK_DEBUG_FUTURE"
    exit 0
fi

if [[ "$DO_CLEAN_ONLY" -eq 1 ]]; then
    acquire_lock
    gradle clean
    rm -f "$ROOT_APK_COMPAT" "$ROOT_MAPPING_COMPAT" "$ROOT_APK_FUTURE" "$ROOT_MAPPING_FUTURE"
    rm -f "$ROOT_APK_DEBUG_COMPAT" "$ROOT_APK_DEBUG_FUTURE"
    echo "Clean complete."
    exit 0
fi

if [[ "$DO_FORMAT" -eq 1 ]]; then
    acquire_lock
    gradle ktlintFormat
    echo "ktlintFormat complete. Re-run ./build.sh without --format to verify."
    exit 0
fi

if [[ "$DO_SMOKE" -eq 1 ]]; then
    acquire_lock
    run_smoke_tests || exit $?
    echo "Smoke complete."
    exit 0
fi

if [[ "$DO_E2E" -eq 1 ]]; then
    acquire_lock
    run_e2e_tests || exit $?
    echo "E2E complete."
    exit 0
fi

if [[ "$DO_SMOKE_SHIPPED" -eq 1 ]]; then
    acquire_lock
    run_shipped_smoke_tests || exit $?
    echo "Shipped smoke complete."
    exit 0
fi

if [[ "$DO_MACROBENCHMARK" -eq 1 ]]; then
    acquire_lock
    gradle :macrobenchmark:pixel7aApi37Setup "${GMD_GPU[@]}"
    gradle :macrobenchmark:pixel7aApi37FutureReleaseAndroidTest "${GMD_GPU[@]}" \
        -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.suppressErrors=EMULATOR
    ./scripts/assert_tests_ran.sh "$MACROBENCHMARK_ASSERT_COUNT" macrobenchmark
    echo "Macrobenchmark complete (emulator numbers are advisory)."
    exit 0
fi

project_run_extra_standalone

acquire_lock

./scripts/check_ascii.sh
chmod +x ./scripts/check_release_signing_gate.sh
./scripts/check_release_signing_gate.sh
chmod +x ./scripts/check_elf_16k_alignment.sh

VERSION_BUMPED=0
CURRENT_CODE=""
CURRENT_NAME=""
NEW_CODE=""
NEW_NAME=""

revert_version_bump() {
    if [[ "$VERSION_BUMPED" -ne 1 ]]; then
        return 0
    fi
    # Anchored replaces: unanchored " = 8" would also rewrite " = 81".
    sed -i -E "s/^([[:space:]]*val baseVersionCode = )${NEW_CODE}$/\1${CURRENT_CODE}/" "$BUILD_GRADLE"
    sed -i -E "s/^([[:space:]]*val baseVersionName = \")${NEW_NAME}(\")$/\1${CURRENT_NAME}\2/" "$BUILD_GRADLE"
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

    # Anchored replaces: unanchored " = 8" would also rewrite " = 81".
    sed -i -E "s/^([[:space:]]*val baseVersionCode = )${CURRENT_CODE}$/\1${NEW_CODE}/" "$BUILD_GRADLE"
    sed -i -E "s/^([[:space:]]*val baseVersionName = \")${CURRENT_NAME}(\")$/\1${NEW_NAME}\2/" "$BUILD_GRADLE"
    VERSION_BUMPED=1

    echo "Bumped version: $CURRENT_NAME ($CURRENT_CODE) -> $NEW_NAME ($NEW_CODE)"
fi

# `clean` runs as its own invocation. Inside one task graph Gradle is free to
# run it in parallel with ktlintCheck, and it does: ktlint's source walk then
# trips over app/build/ intermediates that :app:clean is deleting underneath it
# and fails with NoSuchFileException.
if ! gradle clean; then
    revert_version_bump
    exit 1
fi

if ! gradle "${REQUIRE_RELEASE_SIGNING_ARGS[@]}" "${GRADLE_TASKS[@]}"; then
    revert_version_bump
    exit 1
fi

# Hard gate: every arm64 .so we ship must be 16 KB page-aligned (Android 15+
# 16 KB page devices refuse under-aligned libs at load time).
if [[ "$ELF_CHECK_PASS_APKS" -eq 1 ]]; then
    if ! ./scripts/check_elf_16k_alignment.sh "$GRADLE_APK_COMPAT" "$GRADLE_APK_FUTURE"; then
        revert_version_bump
        exit 1
    fi
else
    if ! ./scripts/check_elf_16k_alignment.sh; then
        revert_version_bump
        exit 1
    fi
fi

# Release path always runs the GMD device lanes. --debug skips them so a
# compile/unit-test loop stays fast and KVM-free after assemble.
# Shippedsmoke first: release APK on a clean GMD before debug androidTests
# replace the install.
if [[ "$DO_DEBUG" -eq 0 ]]; then
    echo "Release build: running shippedsmoke + smoke + e2e on GMD..."
    if ! run_shipped_smoke_tests; then
        revert_version_bump
        exit 1
    fi
    if ! run_smoke_tests; then
        revert_version_bump
        exit 1
    fi
    if [[ "$HAS_COMPAT_SMOKE" -eq 1 ]]; then
        if ! run_compat_smoke_tests; then
            revert_version_bump
            exit 1
        fi
    fi
    if ! run_e2e_tests; then
        revert_version_bump
        exit 1
    fi
    echo "Device gates complete."
fi

if [[ "$DO_BUILD_HEALTH" -eq 1 ]]; then
    gradle buildHealth || true
    REPORT="build/reports/dependency-analysis/build-health-report.txt"
    [[ -f "$REPORT" ]] && echo "Dependency-analysis report: $REPORT"
fi

# Archive R8 mappings keyed by flavor+versionCode BEFORE the root copies are
# overwritten: devices in the field run historical versionCodes, and without
# the archive their stack traces become permanently un-deobfuscatable after
# the next build. mappings/ is gitignored but must never be deleted.
MAPPINGS_DIR="mappings"
if [[ ! -d "$MAPPINGS_DIR" ]]; then
    mkdir -p "$MAPPINGS_DIR"
    for prev in "$ROOT_MAPPING_COMPAT" "$ROOT_MAPPING_FUTURE"; do
        if [[ -f "$prev" ]]; then
            cp "$prev" "$MAPPINGS_DIR/unversioned-$(date +%Y%m%d%H%M%S)-$prev"
        fi
    done
fi
BUILT_CODE=$(sed -n 's/.*val baseVersionCode = \([0-9][0-9]*\).*/\1/p' "$BUILD_GRADLE" | head -1)
if [[ -n "$BUILT_CODE" ]]; then
    if [[ -f "$GRADLE_MAPPING_COMPAT" ]]; then
        cp "$GRADLE_MAPPING_COMPAT" "$MAPPINGS_DIR/compat-${BUILT_CODE}-mapping.txt"
    fi
    if [[ -f "$GRADLE_MAPPING_FUTURE" ]]; then
        cp "$GRADLE_MAPPING_FUTURE" "$MAPPINGS_DIR/future-$((1000000 + BUILT_CODE))-mapping.txt"
    fi
    echo "Archived R8 mappings for versionCode $BUILT_CODE under $MAPPINGS_DIR/."
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
rm -f "$ROOT_APK_DEBUG_COMPAT" "$ROOT_APK_DEBUG_FUTURE"
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
cp "$DEBUG_APK_COMPAT" "$ROOT_APK_DEBUG_COMPAT"
echo "Copied compat debug APK to $ROOT_APK_DEBUG_COMPAT"
cp "$DEBUG_APK_FUTURE" "$ROOT_APK_DEBUG_FUTURE"
echo "Copied future debug APK to $ROOT_APK_DEBUG_FUTURE"

# Serve release + debug for both flavors (compat + future). Soft-skip if
# NetBird is down so the build still succeeds offline.
./scripts/apk_http_serve.sh start --optional \
    "$ROOT_APK_COMPAT" "$ROOT_APK_FUTURE" \
    "$ROOT_APK_DEBUG_COMPAT" "$ROOT_APK_DEBUG_FUTURE"
