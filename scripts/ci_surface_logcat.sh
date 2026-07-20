#!/usr/bin/env bash
# Surfaces the app's emulator logcat in CI - on SUCCESS and failure alike -
# so playback/provider bugs that do not fail a test are still visible:
#  1. Full excerpts land in the job's step summary (public web UI).
#  2. The highest-signal lines (crashes, FGS denials, player errors) are
#     emitted as ::warning workflow commands, which become check-run
#     annotations readable via the public API without auth.
# Called from .github/workflows/check.yml after every GMD test step.
# Usage: ci_surface_logcat.sh [results-dir]  (defaults to :app's)
set -u

RESULTS_DIR="${1:-app/build/outputs/androidTest-results}"

# High-signal patterns: process death, playback failures, permission/FGS
# denials, unresolvable streams. Extend as new failure classes appear.
SIGNAL='FATAL EXCEPTION|AndroidRuntime|ForegroundServiceStartNotAllowed|SecurityException|ExoPlaybackException|PlaybackException|MediaCodec.*error|AudioTrack.*error|E ExoPlayer|E MediaSession|Playback error|has crashed|ANR in'

# App-scoped context worth reading even when green.
CONTEXT='dustvalve|Dustvalve|ExoPlayer|PlaybackService|PlaybackManager|MediaSession'

mapfile -t LOGS < <(find "$RESULTS_DIR" -type f -name '*logcat*' 2>/dev/null | sort)

# Public, API-readable count so a lane that produces no logcat at all is
# distinguishable from a lane whose logs are simply clean.
echo "::notice title=logcat-file-count::${#LOGS[@]} logcat files under $RESULTS_DIR"

{
  echo "## Emulator app log excerpts"
  if [ "${#LOGS[@]}" -eq 0 ]; then
    echo "No logcat files found under $RESULTS_DIR."
  fi
  for f in "${LOGS[@]}"; do
    echo ""
    echo "<details><summary>${f#"$RESULTS_DIR"/} ($(wc -l < "$f") lines total)</summary>"
    echo ""
    echo '```'
    # Signal lines first (all of them), then the last app-context lines.
    grep -E "$SIGNAL" "$f" | head -n 200 || true
    echo '--- app context (tail) ---'
    grep -E "$CONTEXT" "$f" | tail -n 200 || true
    echo '```'
    echo "</details>"
  done
} >> "${GITHUB_STEP_SUMMARY:-/dev/stdout}"

# Public annotations: GitHub keeps at most 10 warnings per step - emit the
# 8 most severe distinct lines so they are API-readable on any conclusion.
if [ "${#LOGS[@]}" -gt 0 ]; then
  # Headline lines only - drop stack frames ('at ...') so the 8 slots carry
  # 8 distinct problems, not one exception's backtrace.
  grep -hE "$SIGNAL" "${LOGS[@]}" 2>/dev/null \
    | grep -vE '^[[:space:]]*at |: [[:space:]]*at |\.\.\. [0-9]+ more' \
    | sed 's/%/%25/g' | cut -c1-500 | sort -u | head -n 8 \
    | while IFS= read -r line; do
        echo "::warning title=app-logcat::${line}"
      done
fi

exit 0
