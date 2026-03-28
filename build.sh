#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Ensure only one build runs at a time
LOCKFILE="$SCRIPT_DIR/.build.lock"
exec 9>"$LOCKFILE"
if ! flock -n 9; then
    echo "Another build is already running. Exiting."
    exit 1
fi

GRADLE_APK="app/build/outputs/apk/release/app-release.apk"
ROOT_APK="app-release.apk"
BUILD_GRADLE="app/build.gradle.kts"

# Clean + Build
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew clean lint assembleDebug assembleRelease

# Replace root APK with fresh build
rm -f "$ROOT_APK"
cp "$GRADLE_APK" "$ROOT_APK"
echo "Copied release APK to $ROOT_APK"

# Bump version
CURRENT_CODE=$(sed -n 's/.*versionCode = \([0-9]*\).*/\1/p' "$BUILD_GRADLE")
CURRENT_NAME=$(sed -n 's/.*versionName = "\([^"]*\)".*/\1/p' "$BUILD_GRADLE")

NEW_CODE=$((CURRENT_CODE + 1))
# Bump the last numeric segment of versionName
NEW_NAME=$(echo "$CURRENT_NAME" | awk -F. -v OFS=. '{$NF=$NF+1; print}')

sed -i "s/versionCode = $CURRENT_CODE/versionCode = $NEW_CODE/" "$BUILD_GRADLE"
sed -i "s/versionName = \"$CURRENT_NAME\"/versionName = \"$NEW_NAME\"/" "$BUILD_GRADLE"

echo "Bumped version: $CURRENT_NAME ($CURRENT_CODE) -> $NEW_NAME ($NEW_CODE)"
