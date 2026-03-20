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

**Multi-source playback**
- Stream and download from **Bandcamp** — browse hot releases, search, manage favorites, purchase downloads
- Play and download from **YouTube** via NewPipe Extractor — no API key or Google account needed
- Scan and play **local audio files** (mp3, flac, m4a, ogg, wav, opus, aac, wma, alac) via folder picker or device-wide MediaStore scanning
- Multiple local music folder support with per-folder track association

**Downloads & caching**
- Progressive download: stream at preview quality, then seamlessly upgrade to full quality in the background
- Download quality presets: **Best quality** (preferred format always) or **Economical** (AAC on metered networks)
- Unified download behavior across YouTube and Bandcamp sources
- HQ format selection for purchased Bandcamp content (FLAC, MP3 320, MP3 V0, AAC, Ogg Vorbis)
- 512 MB LRU media cache with configurable storage limits and cache eviction

**Player**
- Queue management with shuffle and repeat (off / all / one)
- Volume control with inline slider and full-screen volume sheet
- Audio output device switching
- Media session notifications and lock screen controls
- Seamless hot-swap from stream to local file mid-playback

**Library**
- Favorites for tracks, albums, and artists
- Custom playlists with drag-to-reorder
- System playlists: Downloads, Recent, Collection, Favorites, Local
- Search history across all sources

**Design**
- Material You 3 Expressive theming with `MotionScheme` spring animations
- Dynamic color from your wallpaper (Android 12+)
- Album art color theming — theme the app using colors from the playing track
- Dark, light, and system theme modes
- OLED pure black option
- Wavy animated seek bar
- Responsive layout — bottom nav on phones, navigation rail on tablets

**Gesture-driven**
- **Tap** album art to play/pause, **double-tap** to favorite
- **Swipe** album art left or right to skip tracks
- **Long-press** album art to browse upcoming tracks in a carousel
- **Drag** the seek bar or volume slider to scrub and adjust
- **Swipe** queue items or playlist tracks to remove them
- **Long-press and drag** handles to reorder queue and playlists
- **Long-press** library items for context menus
- **Tap** stacked covers behind the album art to jump to upcoming tracks

**Lightweight**
- ~4 MB release APK — minimal dependencies, no bloat
- Instant startup with zero splash screen delay

**Privacy-respecting**
- No telemetry or tracking
- YouTube access through NewPipe Extractor (no Google services dependency)
- Bandcamp rate limiting to be respectful to servers

## Tech Stack

| | |
|---|---|
| **Language** | Kotlin 2.3 |
| **UI** | Jetpack Compose 1.11 + Material 3 Expressive 1.5 |
| **Architecture** | MVVM · Hilt · Coroutines + StateFlow |
| **Navigation** | Jetpack Navigation 3 |
| **Database** | Room 2.8 |
| **Playback** | Media3 / ExoPlayer 1.10 |
| **Networking** | OkHttp 5.3 · Jsoup 1.22 |
| **YouTube** | NewPipe Extractor 0.26 |
| **Images** | Coil 3.4 |
| **Build** | Gradle (Kotlin DSL) · AGP 9.2 · KSP · Java 21 |

## Building from source

**Requirements:** Java 21, Android SDK 36

```bash
git clone https://github.com/Pingasmaster/dustvalve_next.git
cd dustvalve_next
./build.sh
```

The build script runs lint, assembles debug and release APKs, copies the release APK to the project root as `app-release.apk`, and auto-increments the version.

To build manually:

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew assembleDebug
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

## Automated Releases (for forks)

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

Go to **Releases > Draft a new release**, create a tag (e.g. `v0.0.69`), write release notes, and click **Publish release**. The workflow will build the release APK and attach it as a downloadable asset.

> **Note:** The workflow only triggers on published releases — not on pushes or pull requests. You can monitor builds in the **Actions** tab.

## Contributing

Contributions are welcome. Please open an issue to discuss larger changes before submitting a PR.

## License

Dustvalve Next is licensed under the [GNU General Public License v3.0](https://www.gnu.org/licenses/gpl-3.0.html).

```
Copyright (C) 2026 Dustvalve Next contributors

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.
```
