# Release checklist

Manual gate before publishing any GitHub release. Automated tiers must be
green first; this list covers what CI cannot exercise.

## Preflight

- [ ] master CI fully green, INCLUDING emulator-smoke and emulator-e2e jobs
      (both hermetic and live passes).
- [ ] `app/src/androidTest/resources/quarantine.txt` reviewed: every entry
      has an open issue; delist anything that passed 3 consecutive runs.
- [ ] Version bumped (versionCode + versionName) on master AND
      legacy-android8; both branches pushed.

## Install matrix (real device)

- [ ] Fresh install of the signed release APK: first-run flow, notification
      permission prompt, empty states.
- [ ] Upgrade-in-place over the previous release: library, playlists,
      downloads, favorites, and settings all survive.

## Playback on hardware (all three providers)

- [ ] Local file plays; position advances past 0:00; pause/resume/seek/skip.
- [ ] Bandcamp album streams; YouTube Music song streams.
- [ ] Bluetooth output; disconnect pauses (becoming-noisy).
- [ ] Incoming call / alarm ducks or pauses and resumes (audio focus).
- [ ] Lockscreen + notification controls (play/pause/next/prev/favorite).
- [ ] Wired/BT headset button toggles play/pause.

## Auth flows (not CI-automatable)

- [ ] Bandcamp login via WebView; collection playlist syncs; purchased album
      offers HQ download formats; sign out clears state.
- [ ] YouTube Music login connects and disconnects cleanly.

## System integration

- [ ] Deep links from a real browser: youtube.com/watch, youtu.be,
      music.youtube.com, *.bandcamp.com album/artist; share-sheet text.
- [ ] Per-app language switch (Settings > App languages) relabels the UI.
- [ ] Dynamic color follows wallpaper; theme toggles; OLED black.
- [ ] Rotation and process-death mid-playback: UI reattaches to the session.

## Downloads

- [ ] Download over Wi-Fi uses the preferred format; metered uses MP3-320
      when save-data is on.
- [ ] Airplane mode: downloaded tracks play offline.
- [ ] Remove all downloads clears storage indicator and files.

## Update + crash flows

- [ ] Check for updates against the real GitHub release (older build).
- [ ] Post-crash prompt appears after a forced crash; Share log and GitHub
      issue actions work; dismiss deletes the log.

## Sign-off

| Check | Device / Android version | Tester | Date |
|-------|--------------------------|--------|------|
|       |                          |        |      |
