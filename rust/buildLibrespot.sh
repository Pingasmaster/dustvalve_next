#!/bin/bash
# Build librespot-ffi for Android targets and copy .so files to jniLibs.
# Requires: Android NDK, Rust with Android targets (run prepare.sh first).

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
JNI_LIBS_DIR="$PROJECT_ROOT/app/src/main/jniLibs"

cd "$SCRIPT_DIR"

# Build for arm64-v8a (most modern devices)
echo "Building for arm64-v8a..."
cargo ndk -t arm64-v8a --platform 21 build --release

# Build for armeabi-v7a (older 32-bit devices)
echo "Building for armeabi-v7a..."
cargo ndk -t armeabi-v7a --platform 21 build --release

# Copy .so files to jniLibs
echo "Copying .so files..."
mkdir -p "$JNI_LIBS_DIR/arm64-v8a" "$JNI_LIBS_DIR/armeabi-v7a"
cp target/aarch64-linux-android/release/liblibrespot_ffi.so "$JNI_LIBS_DIR/arm64-v8a/"
cp target/armv7-linux-androideabi/release/liblibrespot_ffi.so "$JNI_LIBS_DIR/armeabi-v7a/"

echo "Done. Native libraries copied to $JNI_LIBS_DIR"
