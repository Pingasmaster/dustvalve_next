#!/bin/bash
# Install Rust Android targets and cargo-ndk for cross-compilation.
# Run this once before buildLibrespot.sh.

set -e

echo "Installing Rust Android targets..."
rustup target add aarch64-linux-android armv7-linux-androideabi

echo "Installing cargo-ndk..."
cargo install cargo-ndk

echo "Done. You can now run ./buildLibrespot.sh"
