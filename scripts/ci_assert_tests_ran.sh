#!/usr/bin/env bash
# Thin wrapper: prefer scripts/assert_tests_ran.sh (shared name across sister apps).
exec "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/assert_tests_ran.sh" "$@"
