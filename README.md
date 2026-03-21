<p align="center">
  <img src="logo.png" width="100" alt="Dustvalve Next icon"/>
</p>

<h1 align="center">Dustvalve Next</h1>

<p align="center">
  <b>An emotional music player for Android, made with intention</b><br/>
  Touch, swipe, drag, double-tap, and slide your way through Bandcamp, YouTube, and your local library.
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white" alt="Min SDK 26"/>
  <img src="https://img.shields.io/badge/Kotlin-2.3-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin"/>
  <img src="https://img.shields.io/badge/Jetpack_Compose-1.11-4285F4?logo=jetpackcompose&logoColor=white" alt="Compose"/>
  <img src="https://img.shields.io/badge/Material_3-Expressive-E8DEF8" alt="M3 Expressive"/>
  <img src="https://img.shields.io/badge/License-GPLv3-blue" alt="GPLv3"/>
</p>

---

## About

Dustvalve Next is a multi-source music player that lets you browse Bandcamp, search YouTube, and play your local files from a single, cohesive interface. It's built entirely in Kotlin with Jetpack Compose and follows Material You 3 Expressive design — spring-based animations, dynamic color theming, and expressive shapes throughout.

> **Pre-alpha** — under active development. Expect breaking changes and rough edges.

## Features

Play your music from a single, snappy and emotional interface no matter where it comes from: bandcamp, youtube music, or just your local music folder. With progressive downloads on cellular, automatic caching (never redownloads a song more than once) and optional downloads for all platforms,
See your favorite artists, albums, playlists and songs in a single page and add songs to your playlist without worrying about where they come from. We have no tracking or bullshit, you choose whether you want to allow music access storage permissions or per-folder access which does not require any storage permissions at all, GPLv3, this software is designed to repesct you and your privacy. We provide instructions to build the app yourself from this repo in the build section.
We heavily follow all Material you 3 Expressive guidelines and recommendations with a touch of expressiveness on top to make it all as much android-native and intentional as possible, though I'm a dev not a designer so feel free to create some Issues for any suggestions. This app apk is around 4Mb, it takes around 40Mb of storage space for the app itself, has configurable caching storage limits and most importantly starts up instantly, no splash screen for 5 seconds on slow devices.
We will add more sources once we get out of alpha and have unified the player features.

**Gestures**
- **Tap** album art to play/pause, **double-tap** to favorite
- **Swipe** album art left or right to skip tracks
- **Long-press** album art to browse upcoming tracks in a carousel
- **Drag** the seek bar or volume slider to scrub and adjust
- **Swipe** queue items or playlist tracks to remove them
- **Long-press and drag** handles to reorder queue and playlists
- **Long-press** library items for context menus
- **Tap** stacked covers behind the album art to jump to upcoming tracks

## Building from source

**Requirements:** Java 21, Android SDK 36

If you just want an apk to install to your device:

```bash
git clone https://github.com/Pingasmaster/dustvalve_next.git
cd dustvalve_next
# You have to generate bogus keys to assemble a release apk, you can skip this step for a debug apk but it'll be way slower and larger
keytool -genkey -v -keystore release-keystore.jks -keyalg RSA -keysize 2048 -validity 10000 -alias dustvalve
# Store the password you just chose so the build script can find it
echo "your-password-here" > .password-signing-keys

# Then you can build
./gradlew assembleRelease
```

For devs who want to modify, fork and test the app, we have a build script which runs lint, assembles debug and release APKs, copies sthe release APK to the project root as `app-release.apk`, and auto-increments the version.

```bash
./build.sh
```

## Project Structure

```
app/src/main/java/com/dustvalve/next/android/
├── cache/              Cache eviction policy & storage tracking
├── data/
│   ├── local/          Room database, DAOs, entities, DataStore
│   ├── remote/         Bandcamp scrapers, YouTube extractor, HTTP client
│   └── repository/     Repository implementations
├── di/                 Hilt modules
├── domain/
│   ├── model/          Domain models (Track, Album, AudioFormat, etc.)
│   ├── repository/     Repository interfaces
│   └── usecase/        Use cases (download, cache management)
├── player/             ExoPlayer wrapper, queue, playback service
├── ui/
│   ├── components/     Shared composables
│   ├── navigation/     Route definitions
│   ├── screens/
│   │   ├── album/      Album detail
│   │   ├── artist/     Artist profile & discography
│   │   ├── bandcamp/   Browse & search
│   │   ├── library/    Favorites, playlists, downloads
│   │   ├── local/      Local music browser
│   │   ├── player/     Full & mini player
│   │   ├── playlist/   Playlist detail & reorder
│   │   ├── settings/   Settings & account login
│   │   └── youtube/    YouTube search & playback
│   └── theme/          Colors, typography, shapes, motion
└── util/               Network utils, file helpers, encryption
```

## Automated releases (for forks)

This repository includes a GitHub Actions workflow that builds a signed release APK and attaches it to GitHub Releases automatically. To set it up on your fork:

### 1. Generate a signing keystore

If you don't already have one:

```bash
keytool -genkey -v -keystore release-keystore.jks -keyalg RSA -keysize 2048 -validity 10000 -alias dustvalve
```

### 2. Add repository secrets

Go to your fork on GitHub: **Settings > Secrets and variables > Actions > New repository secret**, and add:

| Secret | Value |
|---|---|
| `KEYSTORE_BASE64` | Base64-encoded keystore. Generate with: `base64 -w 0 release-keystore.jks` |
| `KEYSTORE_PASSWORD` | The password you used when creating the keystore |

### 3. Publish a release

Go to **Releases > Draft a new release**, create a tag (e.g. `v1.0.0`), write release notes, and click **Publish release**. The workflow will build the release APK and attach it as a downloadable asset.

> **Note:** The workflow only triggers on published releases, not on pushes or pull requests. You can monitor builds in the **Actions** tab.

## Contributing

Contributions are welcome, but this music player was originally built for myself because I found the bandcamp app to be lacking in design and speed. If it makes sense I'll merge it.

## License

Dustvalve Next is licensed under the [GNU General Public License v3.0](https://www.gnu.org/licenses/gpl-3.0.html).

<p align="center">
  <img src="gplv3.png" alt="GPLv3"/>
</p>
