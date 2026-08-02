#!/usr/bin/env bash
# Copy GMD-generated baseline + startup profiles into :app's release source set.
# Used by ./build.sh --baseline-profile after
# :baselineprofile:pixel7aApi37ReleaseAndroidTest.
#
# With includeInStartupProfile=true (androidx.benchmark 1.5+), the collector
# often emits only *-startup-prof*.txt (rules already carry SP/HSP markers).
# Treat a non-empty startup profile as the baseline when no separate
# baseline-prof file is produced.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

out=$(find baselineprofile/build/outputs -type d -name '*additional*output*' 2>/dev/null | head -1 || true)
if [[ -z "$out" ]]; then
    echo "ERROR: no baselineprofile additional-output directory produced." >&2
    exit 1
fi

# Prefer non-timestamped copies (stable names from the collector).
baseline=$(find "$out" -name '*baseline-prof.txt' ! -name '*-20*' 2>/dev/null | head -1 || true)
if [[ -z "$baseline" ]]; then
    baseline=$(find "$out" -name '*baseline-prof*.txt' 2>/dev/null | head -1 || true)
fi

startup=$(find "$out" -name '*startup-prof.txt' ! -name '*-20*' 2>/dev/null | head -1 || true)
if [[ -z "$startup" ]]; then
    startup=$(find "$out" -name '*startup-prof*.txt' 2>/dev/null | head -1 || true)
fi

if [[ (-z "$baseline" || ! -s "$baseline") && (-n "$startup" && -s "$startup") ]]; then
    echo "Note: no separate baseline-prof; using startup-prof as baseline (includeInStartupProfile)."
    baseline="$startup"
fi

if [[ -z "$baseline" || ! -s "$baseline" ]]; then
    echo "ERROR: no non-empty baseline/startup profile generated under $out" >&2
    ls -la "$out" >&2 || true
    exit 1
fi

mkdir -p app/src/release
cp "$baseline" app/src/release/baseline-prof.txt
if [[ -n "$startup" && -s "$startup" ]]; then
    cp "$startup" app/src/release/startup-prof.txt
else
    # Same content: AGP still reads startup-prof.txt for dex-layout opts.
    cp "$baseline" app/src/release/startup-prof.txt
fi

echo "Installed profiles:"
wc -l app/src/release/baseline-prof.txt
wc -l app/src/release/startup-prof.txt
