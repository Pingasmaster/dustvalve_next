#!/usr/bin/env bash
# Strip known non-actionable toolchain chatter from Gradle / GMD transcripts.
# Keeps real task failures, ERROR lines, and test assertion output.
set -euo pipefail

# Match whole lines only. Do not drop lines that contain "error" inside a
# real failure summary (What went wrong / FAILED tasks stay).
grep -vE \
    -e '^WARNING: A terminally deprecated method in sun\.misc\.Unsafe' \
    -e '^WARNING: sun\.misc\.Unsafe::' \
    -e '^WARNING: Please consider reporting this to the maintainers of class' \
    -e '^WARNING: sun\.misc\.Unsafe::arrayBaseOffset will be removed' \
    -e 'Consider enabling configuration cache to speed up this build:' \
    -e '^\[Incubating\] Problems report is available at:' \
    -e '^Test execution completed\. See the report at:' \
    -e '^The device "pixel7aApi37" does not specify a "testedAbi"\.' \
    -e '^This currently defaults to "x86_64", but will change to "arm64-v8a" in AGP 10\.0\.' \
    -e '^In AGP 10\.0, this device will rely on NDK translation to run tests\.' \
    -e '^To keep the current behavior \(avoiding NDK translation\),' \
    -e '^explicitly set the ABI: testedAbi = "x86_64"' \
    -e '^> Get more help at https://' \
    -e '^Picked up JAVA_TOOL_OPTIONS:' \
    -e '^release-keystore\.jks or \.password-signing-keys missing' \
    -e 'https?://' \
    -e 'file://' \
    -e '^Problem found: Kotlin compiler (info|debug)' \
    -e '^  Kotlin compiler (info|debug)$' \
    -e '^    #1 retrying connecting to the daemon' \
    -e '^    is not a known package; using default' \
    || true
