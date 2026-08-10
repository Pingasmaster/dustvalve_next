#!/usr/bin/env bash
# Run ./gradlew with known-non-actionable toolchain chatter stripped.
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$ROOT"
set +e
./gradlew "$@" 2>&1 | ./scripts/filter_gradle_noise.sh
rc=${PIPESTATUS[0]}
set -e
exit "$rc"
