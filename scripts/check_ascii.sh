#!/usr/bin/env bash
#
# Enforce the ASCII-only source policy (see CLAUDE.md).
#
# Scans all git-tracked files for non-ASCII bytes, excluding the documented
# exceptions (localization resources, captured test fixtures, files whose
# tests/parsers require real Unicode, generated files, binaries).
#
# Usage:
#   scripts/check_ascii.sh          # exit 1 if violations found (CI mode)
#   scripts/check_ascii.sh --warn   # print warnings only, always exit 0
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."

WARN_ONLY=0
[ "${1:-}" = "--warn" ] && WARN_ONLY=1

# Path prefixes/globs where non-ASCII is legitimate. Keep in sync with CLAUDE.md.
is_exempt() {
    case "$1" in
        # Localization resources, every locale including the default
        app/src/main/res/values*/*) return 0 ;;
        # Captured real server responses; bytes must stay faithful
        */src/test/resources/fixtures/*) return 0 ;;
        # Documents typographic quotes for translators
        TRANSLATIONS.md) return 0 ;;
        # Gradle-generated wrapper
        gradlew) return 0 ;;
        # Unicode-behavior code and tests: collation, sanitization, real
        # scraped tag slugs, YouTube Music's literal " <bullet> " separator
        core/common/src/main/java/com/dustvalve/next/android/util/LocaleCollation.kt) return 0 ;;
        app/src/test/java/com/dustvalve/next/android/util/LocaleCollationTest.kt) return 0 ;;
        app/src/test/java/com/dustvalve/next/android/util/NetworkUtilsTest.kt) return 0 ;;
        app/src/test/java/com/dustvalve/next/android/ui/util/TracksHeaderLabelTest.kt) return 0 ;;
        app/src/test/java/com/dustvalve/next/android/data/remote/youtubemusic/YouTubeMusicSearchParserTest.kt) return 0 ;;
        data/src/main/java/com/dustvalve/next/android/data/remote/youtubemusic/YouTubeMusicSearchParser.kt) return 0 ;;
        data/src/main/java/com/dustvalve/next/android/data/remote/GenreSubTags.kt) return 0 ;;
        # Binaries
        *.png|*.webp|*.jpg|*.jks|*.jar|*.apk|*.ico|*.gif) return 0 ;;
    esac
    return 1
}

violations=0
while IFS= read -r f; do
    is_exempt "$f" && continue
    [ -f "$f" ] || continue
    if LC_ALL=C grep -qP '[^\x00-\x7F]' "$f" 2>/dev/null; then
        violations=$((violations + 1))
        echo "non-ASCII characters in: $f"
        LC_ALL=C grep -nP '[^\x00-\x7F]' "$f" | head -5 | sed 's/^/    /'
    fi
done < <(git ls-files)

if [ "$violations" -gt 0 ]; then
    if [ "$WARN_ONLY" = 1 ]; then
        echo "WARNING: $violations file(s) contain non-ASCII characters (policy: ASCII-only outside localization files, see CLAUDE.md)."
        exit 0
    fi
    echo "ERROR: $violations file(s) contain non-ASCII characters. ASCII-only policy (see CLAUDE.md): use -, ->, ..., etc. instead of typographic characters."
    exit 1
fi
[ "$WARN_ONLY" = 1 ] || echo "ASCII check passed."
exit 0
