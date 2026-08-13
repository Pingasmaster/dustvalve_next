#!/usr/bin/env bash
# Fail if any arm64 ELF shared object has a PT_LOAD Align below 16 KiB.
# Android 15+ devices with 16 KB pages reject under-aligned .so at load time.
#
# Runs AFTER the Gradle build (see build.sh). merged_native_libs is the only
# place our own CMake output (libblake3jni.so) and dependency libs
# (LiteRT-LM, androidx graphics-path, datastore) can be checked - checking
# only the source tree would have passed while the shipped APK was 4 KB-aligned.
#
# Optional argv: APK paths (build.sh passes GRADLE_APK_*). When present, also
# run `zipalign -c -P 16 4` on each APK if zipalign exists under Android SDK
# build-tools. Missing zipalign is a warning, not a hard fail.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PAGE=16384
READELF="${READELF:-readelf}"

if ! command -v "$READELF" >/dev/null 2>&1; then
    echo "ERROR: $READELF not found; cannot verify 16 KB ELF alignment." >&2
    exit 1
fi

# Source-tree jniLibs - everything else under build/, .git/ and .gradle/ is
# skipped here and scanned explicitly below.
mapfile -t SOS < <(
    find "$ROOT" \
        \( -path '*/build/*' -o -path '*/.git/*' -o -path '*/.gradle/*' \) -prune -o \
        -type f -name '*.so' -path '*/arm64-v8a/*' -print \
        2>/dev/null | sort
)

# Merged native libs of the variants this build produced: the union of our
# CMake outputs, packaged jniLibs, and every dependency's packaged .so.
if [[ -d "$ROOT/app/build/intermediates/merged_native_libs" ]]; then
    mapfile -t -O "${#SOS[@]}" SOS < <(
        find "$ROOT/app/build/intermediates/merged_native_libs" \
            -type f -name '*.so' -path '*/arm64-v8a/*' -print 2>/dev/null | sort
    )
fi

if [[ "${#SOS[@]}" -eq 0 ]]; then
    echo "ERROR: no arm64-v8a .so files found under $ROOT" \
        "(run after Gradle assemble)." >&2
    exit 1
fi

fail=0
for so in "${SOS[@]}"; do
    # Program headers: Align column is last field on LOAD lines (hex).
    while read -r align; do
        [[ -z "$align" ]] && continue
        align_dec=$((align))
        if (( align_dec < PAGE )); then
            echo "ERROR: $so PT_LOAD Align=$align (< ${PAGE}); not 16 KB page-safe." >&2
            fail=1
        fi
    done < <("$READELF" -lW "$so" 2>/dev/null | awk '/LOAD/ { print $NF }')
done

if [[ "$fail" -eq 0 ]]; then
    echo "ELF 16 KB alignment OK (${#SOS[@]} arm64-v8a .so)."
fi

locate_zipalign() {
    local sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
    if [[ -z "$sdk" && -f "$ROOT/local.properties" ]]; then
        sdk="$(sed -n 's/^sdk.dir=//p' "$ROOT/local.properties" | head -1 | tr -d '\r' | sed 's/\\:/:/g')"
    fi
    if [[ -z "$sdk" ]]; then
        sdk="${HOME}/Android/Sdk"
    fi
    local candidate=""
    if [[ -d "${sdk}/build-tools" ]]; then
        candidate="$(find "${sdk}/build-tools" -maxdepth 2 -type f -name zipalign 2>/dev/null | sort -V | tail -1 || true)"
    fi
    if [[ -n "$candidate" && -x "$candidate" ]]; then
        printf '%s\n' "$candidate"
        return 0
    fi
    return 1
}

if [[ "$#" -gt 0 ]]; then
    ZIPALIGN=""
    ZIPALIGN="$(locate_zipalign || true)"
    if [[ -z "$ZIPALIGN" ]]; then
        echo "WARN: zipalign not found under Android SDK build-tools;" \
            "skipping APK zip alignment check." >&2
    else
        for apk in "$@"; do
            if [[ ! -f "$apk" ]]; then
                echo "ERROR: APK not found: $apk" >&2
                fail=1
                continue
            fi
            if ! "$ZIPALIGN" -c -P 16 4 "$apk"; then
                echo "ERROR: $apk failed zipalign -c -P 16 4." >&2
                fail=1
            else
                echo "APK 16 KB zip alignment OK: $apk"
            fi
        done
    fi
fi

if [[ "$fail" -ne 0 ]]; then
    exit 1
fi
