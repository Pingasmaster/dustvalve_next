#!/usr/bin/env bash
#
# Usage:
#   ./build.sh                    # clean + ktlintCheck + detekt + lintRelease + test + assemble + bump version
#   ./build.sh --clean            # gradle clean + remove APK + exit
#   ./build.sh --format           # ktlintFormat + exit (no build)
#   ./build.sh --build-health     # full build + dependency-analysis buildHealth report
#   ./build.sh --workflow-tests   # Tier 1 JVM workflow tests only (fast) + exit
#   ./build.sh --smoke            # Tier 2 on-device smoke on GMD pixel7aApi37 + exit
#   ./build.sh --smoke-release    # Tier 2 smoke against the MINIFIED release APK + exit
#   ./build.sh --e2e              # Tier 3 hermetic E2E on GMD pixel7aApi37 + exit
#   ./build.sh --e2e-live         # Tier 3 LIVE E2E (real Bandcamp/YouTube) + exit
#   ./build.sh --live-net         # DUSTVALVE_LIVE_NET=1 gated JVM live smokes + exit
#
# The emulator tiers (--smoke/--e2e/--e2e-live) boot a Gradle Managed Device;
# budget ~2 GB of RAM beyond the Gradle daemon. If the host QEMU cannot boot
# modern system images (some bleeding-edge distros), run these tiers in CI
# (check.yml: emulator-smoke / emulator-e2e) instead.
#
# IMPORTANT: Do NOT manually remove .build.lock unless you have user approval
# and have confirmed no process is currently using it (check with `fuser
# .build.lock` or `lsof .build.lock`). The lock ensures only one build or
# clean runs at a time. Removing it without checking can corrupt builds.
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# JEP 498 opt-in for every forked JVM (ktlint workers, Kotlin daemon):
# kotlin-compiler-embeddable 2.2.x still uses sun.misc.Unsafe and JDK 25
# warns otherwise. Remove once ktlint bundles a compiler without it.
export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:+$JAVA_TOOL_OPTIONS }--sun-misc-unsafe-memory-access=allow"

# Argument parsing
DO_CLEAN_ONLY=0
DO_FORMAT=0
DO_BUILD_HEALTH=0
DO_WORKFLOW_TESTS=0
DO_SMOKE=0
DO_E2E=0
DO_E2E_LIVE=0
DO_LIVE_NET=0

ROOT_APK="dustvalve_next-future.apk"
for arg in "$@"; do
    case "$arg" in
        --clean)        DO_CLEAN_ONLY=1 ;;
        --format)       DO_FORMAT=1 ;;
        --build-health) DO_BUILD_HEALTH=1 ;;
        --workflow-tests) DO_WORKFLOW_TESTS=1 ;;
        --smoke)        DO_SMOKE=1 ;;
        --smoke-release) DO_SMOKE_RELEASE=1 ;;
        --e2e)          DO_E2E=1 ;;
        --e2e-live)     DO_E2E_LIVE=1 ;;
        --live-net)     DO_LIVE_NET=1 ;;
        *) echo "Unknown arg: $arg (accepted: --clean, --format, --build-health, --workflow-tests, --smoke, --smoke-release, --e2e, --e2e-live, --live-net)" >&2; exit 2 ;;
    esac
done

# Lock helper: acquire or exit
acquire_lock() {
    LOCKFILE="$SCRIPT_DIR/.build.lock"
    exec 9>"$LOCKFILE"
    if ! flock -n 9; then
        echo "Another build or clean is already running. Exiting."
        exit 1
    fi
    trap 'rm -f "$LOCKFILE"' EXIT
}

# --clean: just run gradle clean and exit, do nothing else
if [[ "$DO_CLEAN_ONLY" -eq 1 ]]; then
    acquire_lock
    ./gradlew clean
    rm -f "$ROOT_APK"
    echo "Clean complete."
    exit 0
fi

# --format: ktlintFormat only, no build, no version bump
if [[ "$DO_FORMAT" -eq 1 ]]; then
    acquire_lock
    ./gradlew ktlintFormat
    echo "ktlintFormat complete. Re-run ./build.sh without --format to verify."
    exit 0
fi

GMD_GPU="-Pandroid.testoptions.manageddevices.emulator.gpu=swiftshader_indirect"

# --workflow-tests: Tier 1 JVM workflow suite only (the fast regression net)
if [[ "$DO_WORKFLOW_TESTS" -eq 1 ]]; then
    acquire_lock
    ./gradlew :app:testDebugUnitTest --tests 'com.dustvalve.next.android.workflow.*'
    echo "Workflow tests complete."
    exit 0
fi

# --live-net: opt-in JVM tests that hit the real services
if [[ "$DO_LIVE_NET" -eq 1 ]]; then
    acquire_lock
    DUSTVALVE_LIVE_NET=1 ./gradlew :app:testDebugUnitTest --tests '*Live*'
    echo "Live-network JVM smokes complete."
    exit 0
fi

# --smoke: Tier 2 on-device smoke (GMD)
if [[ "$DO_SMOKE" -eq 1 ]]; then
    acquire_lock
    ./gradlew :app:pixel7aApi37Setup "$GMD_GPU"
    ./gradlew :app:pixel7aApi37DebugAndroidTest "$GMD_GPU" \
        -Pandroid.testInstrumentationRunnerArguments.annotation=com.dustvalve.next.android.testing.SmokeTest
    echo "Smoke suite complete."
    exit 0
fi

# --smoke-release: Tier 2 smoke against the MINIFIED release APK (R8 full
# mode). Catches release-only breakage the debug lanes cannot see.
if [[ "${DO_SMOKE_RELEASE:-0}" -eq 1 ]]; then
    acquire_lock
    ./gradlew :app:pixel7aApi37Setup -PtestReleaseBuild "$GMD_GPU"
    ./gradlew :app:pixel7aApi37ReleaseAndroidTest -PtestReleaseBuild "$GMD_GPU" \
        -Pandroid.testInstrumentationRunnerArguments.annotation=com.dustvalve.next.android.testing.SmokeTest
    echo "Release smoke suite complete."
    exit 0
fi

# --e2e: Tier 3 hermetic E2E (GMD, no live network tests)
if [[ "$DO_E2E" -eq 1 ]]; then
    acquire_lock
    ./gradlew :app:pixel7aApi37Setup "$GMD_GPU"
    ./gradlew :app:pixel7aApi37DebugAndroidTest "$GMD_GPU" \
        -Pandroid.testInstrumentationRunnerArguments.notAnnotation=com.dustvalve.next.android.testing.LiveNetwork
    echo "Hermetic E2E suite complete."
    exit 0
fi

# --e2e-live: Tier 3 live E2E against real Bandcamp/YouTube
if [[ "$DO_E2E_LIVE" -eq 1 ]]; then
    acquire_lock
    echo "WARNING: this suite hits the real Bandcamp and YouTube services." >&2
    ./gradlew :app:pixel7aApi37Setup "$GMD_GPU"
    ./gradlew :app:pixel7aApi37DebugAndroidTest "$GMD_GPU" \
        -Pandroid.testInstrumentationRunnerArguments.annotation=com.dustvalve.next.android.testing.LiveNetwork
    echo "Live E2E suite complete."
    exit 0
fi

# Default: full build (always starts with clean, includes test if available)
acquire_lock

# ASCII-only source policy: warn locally, CI enforces (see CLAUDE.md)
./scripts/check_ascii.sh --warn

GRADLE_APK="app/build/outputs/apk/release/app-release.apk"
BUILD_GRADLE="app/build.gradle.kts"

# Build tasks: lint stages first (cheap, fail fast), then assemble.
GRADLE_TASKS=(clean ktlintCheck detekt lintRelease assembleDebug assembleRelease)

# Add test task if project has test sources
if [[ -d "app/src/test" ]]; then
    GRADLE_TASKS+=(testDebugUnitTest)
fi

./gradlew "${GRADLE_TASKS[@]}"

# Optional: dependency-analysis report (informational; not a build gate).
if [[ "$DO_BUILD_HEALTH" -eq 1 ]]; then
    ./gradlew buildHealth || true
    REPORT="build/reports/dependency-analysis/build-health-report.txt"
    [[ -f "$REPORT" ]] && echo "Dependency-analysis report: $REPORT"
fi

# Replace root APK with fresh build
rm -f "$ROOT_APK"
cp "$GRADLE_APK" "$ROOT_APK"
echo "Copied release APK to $ROOT_APK"

# Always bump version
CURRENT_CODE=$(sed -n 's/.*versionCode = \([0-9]*\).*/\1/p' "$BUILD_GRADLE")
CURRENT_NAME=$(sed -n 's/.*versionName = "\([^"]*\)".*/\1/p' "$BUILD_GRADLE")

NEW_CODE=$((CURRENT_CODE + 1))
NEW_NAME=$(echo "$CURRENT_NAME" | awk -F. -v OFS=. '{$NF=$NF+1; print}')

sed -i "s/versionCode = $CURRENT_CODE/versionCode = $NEW_CODE/" "$BUILD_GRADLE"
sed -i "s/versionName = \"$CURRENT_NAME\"/versionName = \"$NEW_NAME\"/" "$BUILD_GRADLE"

echo "Bumped version: $CURRENT_NAME ($CURRENT_CODE) -> $NEW_NAME ($NEW_CODE)"
