#!/usr/bin/env bash
#
# Gradle Managed Devices are created from the Pixel 7a hardware profile but
# sdklib EmulatedProperties caps guest RAM at 2-3G and ncore at 2, regardless
# of that profile (see EmulatedProperties.java in studio-2025.3.2 sdklib).
# A real Pixel 7a is 8G / 8 cores. The google_apis image still runs Chrome,
# Photos, TTS, and Settings Intelligence on top of that.
#
# androidx.benchmark MacrobenchmarkScope.killProcessAndFlushArtProfiles then
# sleeps 5s (ART wants the app in the foreground) and broadcasts
# androidx.profileinstaller.action.SAVE_PROFILE. If LMK already killed the
# process, saveProfilesForAllProcesses returns processCount=0 and
# CompilationMode.Partial throws:
#   Process <pkg> never flushed profiles in any process
#
# Raising the guest to 4G / 6 cores is the actual fix. Retrying the collector
# is not. qemu reads RAM/CPU at cold boot, so a live 3G guest must be stopped
# after this patch (snapshots too: they freeze the old machine).
#
# AGP Setup creates-and-starts in one task. Call this BEFORE Setup (existing
# AVD) and AFTER Setup (brand-new AVD still at the cap). ./build.sh gmd_setup
# does both. The root gmdEnsureAvd Gradle task covers a bare ./gradlew Setup.
#
# Identical copy lives in dustvalve_next, calc, compass, and core.
#
set -euo pipefail

RAM_MB="${GMD_AVD_RAM_MB:-4096}"
NCORE="${GMD_AVD_NCORE:-6}"
STAMP="${XDG_CACHE_HOME:-$HOME/.cache}/android-apps/gmd-avd.changed"

avd_home="${ANDROID_AVD_HOME:-${ANDROID_USER_HOME:-$HOME/.android}/avd}"
root="$avd_home/gradle-managed"

mkdir -p "$(dirname "$STAMP")"
echo 0 > "$STAMP"

if [[ ! -d "$root" ]]; then
    echo "gmd_ensure_avd: no $root yet (Setup will create the AVD)"
    echo "GMD_AVD_CHANGED=0"
    exit 0
fi

changed=0

patch_ini() {
    local file="$1"
    local key="$2"
    local value="$3"
    [[ -f "$file" ]] || return 0
    local tmp
    tmp="$(mktemp)"
    awk -v k="$key" -v v="$value" '
        BEGIN { found = 0 }
        {
            line = $0
            if (match(line, "^" k "[[:space:]]*=")) {
                print k " = " v
                found = 1
                next
            }
            print
        }
        END {
            if (!found) print k " = " v
        }
    ' "$file" > "$tmp"
    if ! cmp -s "$file" "$tmp"; then
        cat "$tmp" > "$file"
        echo "gmd_ensure_avd: $file -> $key=$value"
        changed=1
        local_avd_changed=1
    fi
    rm -f "$tmp"
}

kill_gradle_managed_qemu() {
    local pid cmdline killed=0
    for proc in /proc/[0-9]*; do
        pid="${proc#/proc/}"
        [[ -r "$proc/cmdline" ]] || continue
        cmdline="$(tr '\0' ' ' < "$proc/cmdline" 2>/dev/null || true)"
        case "$cmdline" in
            *qemu-system*"gradle-managed"*|*"gradle-managed"*qemu-system*)
                echo "gmd_ensure_avd: stopping qemu pid $pid (RAM/CPU changed; must cold-boot)"
                kill "$pid" 2>/dev/null || true
                killed=1
                ;;
        esac
    done
    if [[ "$killed" -eq 1 ]]; then
        local n=0
        while (( n < 40 )); do
            local still=0
            for proc in /proc/[0-9]*; do
                pid="${proc#/proc/}"
                [[ -r "$proc/cmdline" ]] || continue
                cmdline="$(tr '\0' ' ' < "$proc/cmdline" 2>/dev/null || true)"
                case "$cmdline" in
                    *qemu-system*"gradle-managed"*|*"gradle-managed"*qemu-system*)
                        still=1
                        ;;
                esac
            done
            [[ "$still" -eq 0 ]] && break
            sleep 0.1
            n=$((n + 1))
        done
        for proc in /proc/[0-9]*; do
            pid="${proc#/proc/}"
            [[ -r "$proc/cmdline" ]] || continue
            cmdline="$(tr '\0' ' ' < "$proc/cmdline" 2>/dev/null || true)"
            case "$cmdline" in
                *qemu-system*"gradle-managed"*|*"gradle-managed"*qemu-system*)
                    echo "gmd_ensure_avd: SIGKILL qemu pid $pid"
                    kill -9 "$pid" 2>/dev/null || true
                    ;;
            esac
        done
    fi
}

while IFS= read -r -d '' avd; do
    local_avd_changed=0
    patch_ini "$avd/config.ini" "hw.ramSize" "$RAM_MB"
    patch_ini "$avd/config.ini" "hw.cpu.ncore" "$NCORE"
    patch_ini "$avd/hardware-qemu.ini" "hw.ramSize" "$RAM_MB"
    patch_ini "$avd/hardware-qemu.ini" "hw.cpu.ncore" "$NCORE"
    if [[ "$local_avd_changed" -eq 1 ]]; then
        rm -rf "$avd/snapshots"
        echo "gmd_ensure_avd: dropped snapshots under $avd"
    fi
done < <(find "$root" -type d -name '*.avd' -print0)

if [[ "$changed" -eq 1 ]]; then
    kill_gradle_managed_qemu
    echo 1 > "$STAMP"
    echo "gmd_ensure_avd: guest is now ${RAM_MB}MB RAM / ${NCORE} cores"
    echo "GMD_AVD_CHANGED=1"
else
    echo "gmd_ensure_avd: already ${RAM_MB}MB RAM / ${NCORE} cores"
    echo "GMD_AVD_CHANGED=0"
fi
