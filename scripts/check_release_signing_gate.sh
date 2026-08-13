#!/usr/bin/env bash
#
# Structural gate for the release-signing story (P0 #2 / P1 #114):
#   - buildTypes.debug must NEVER assign the release signingConfig - a debug
#     build must not be able to carry the production signature.
#   - buildTypes.release must assign the release signingConfig.
#   - signingConfigs.create("release") must still read
#     dustvalve.requireReleaseSigning and hard-fail (throw GradleException)
#     when the keystore is missing, so a real release build can never
#     silently fall back to debug signing.
#   - build.sh's release path (everything except --debug) must actually pass
#     -Pdustvalve.requireReleaseSigning=true, or the Gradle-side gate above
#     is never armed.
#
# This is a static/structural check, not a Gradle TestKit run: this repo has
# no buildSrc / build-logic module, and adding one only to host a single
# testable Kotlin helper was judged not worth a whole extra module. Kept in
# scripts/ next to check_ascii.sh and wired into build.sh the same way: a
# hard-fail gate that runs on every build, not a comment or a doc note.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
BUILD_GRADLE="$ROOT_DIR/app/build.gradle.kts"
BUILD_SH="$ROOT_DIR/build.sh"

fail() {
    echo "ERROR: $1" >&2
    exit 1
}

[[ -f "$BUILD_GRADLE" ]] || fail "$BUILD_GRADLE not found"
[[ -f "$BUILD_SH" ]] || fail "$BUILD_SH not found"

# Prints the balanced-brace block starting at the first line in file $1
# matching regex $2, up to (and including) the line where the brace depth
# returns to zero. Depends on the Kotlin DSL braces being well-formed, which
# ktlint/detekt already enforce elsewhere in build.sh.
extract_block() {
    awk -v pat="$2" '
        BEGIN { depth = 0; capturing = 0 }
        capturing == 0 && $0 ~ pat { capturing = 1 }
        capturing == 1 {
            print
            line = $0
            opens = gsub(/\{/, "{", line)
            closes = gsub(/\}/, "}", line)
            depth += opens - closes
            if (depth == 0) exit
        }
    ' "$1"
}

# Double-backslash: these are passed through `awk -v`, which applies its own
# string-escape processing before the result is used as a dynamic regex, so
# a single backslash here would reach the regex compiler stripped bare -
# turning "\(" into an unescaped (and therefore metacharacter) "(".
DEBUG_BLOCK="$(extract_block "$BUILD_GRADLE" '^[[:space:]]*debug \\{')"
[[ -n "$DEBUG_BLOCK" ]] || fail "could not locate buildTypes.debug {} in $BUILD_GRADLE"

if echo "$DEBUG_BLOCK" | grep -q 'signingConfigs\.getByName("release")'; then
    fail "buildTypes.debug in $BUILD_GRADLE assigns the release signingConfig - debug builds must use AGP debug signing only (P0 #2)."
fi

RELEASE_BLOCK="$(extract_block "$BUILD_GRADLE" '^[[:space:]]*release \\{')"
[[ -n "$RELEASE_BLOCK" ]] || fail "could not locate buildTypes.release {} in $BUILD_GRADLE"

echo "$RELEASE_BLOCK" | grep -q 'signingConfigs\.getByName("release")' \
    || fail "buildTypes.release in $BUILD_GRADLE no longer assigns the release signingConfig"

SIGNING_BLOCK="$(extract_block "$BUILD_GRADLE" '^[[:space:]]*create\\("release"\\) \\{')"
[[ -n "$SIGNING_BLOCK" ]] || fail "could not locate signingConfigs.create(\"release\") {} in $BUILD_GRADLE"

echo "$SIGNING_BLOCK" | grep -q 'gradleProperty("dustvalve.requireReleaseSigning")' \
    || fail "signingConfigs release block no longer reads dustvalve.requireReleaseSigning"

echo "$SIGNING_BLOCK" | grep -q 'throw GradleException' \
    || fail "signingConfigs release block no longer hard-fails when the keystore is missing"

grep -q -- '-Pdustvalve.requireReleaseSigning=true' "$BUILD_SH" \
    || fail "$BUILD_SH no longer passes -Pdustvalve.requireReleaseSigning=true on the release path"

echo "check_release_signing_gate: OK (debug never release-signed, release keystore hard-fail wired)."
