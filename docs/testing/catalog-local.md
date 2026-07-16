# Local music + shared player - E2E test scenario catalog (130 scenarios)

Backlog + traceability for the workflow test suites. Each implemented
test references its id here; mark ids [x] with the test class as waves
land. Tiers: JVM (Robolectric/Compose), emulator-smoke, emulator-E2E.


How local discovery works (drives seeding):
- Default: MediaStoreScanner queries MediaStore.Audio.Media.EXTERNAL_CONTENT_
  URI (IS_MUSIC != 0), needs READ_MEDIA_AUDIO (requested from Local screen
  CTA). Rows -> Room tracks table: id "local_ms_<mediaId>", source "local",
  folderUri "mediastore", streamUrl content://media/..., artUrl albumart URI.
  Track number decodes DTTT disc encoding (raw % 1000 when > 1000).
- Alternate: SAF folder mode (OpenDocumentTree, LocalMusicScanner per folder
  URI, localMusicUseMediaStore=false). Folder removal deletes tracks + cached
  art (filesDir/local_art/) + releases permission.
- Re-sync: LocalMusicSyncWorker periodic (6h/2h flex, idle+battery). Guard:
  scan returning 0 files while DB has tracks skips deletion.
- Playback: single-MediaItem model. QueueManager (pure Kotlin) owns queue;
  PlaybackManager.playTrack builds MediaItem from streamUrl (bare path ->
  file://, content:// as-is), sets metadata (trackNumber=queueIndex+1),
  ensureServiceStarted() (FGS PlaybackService = MediaSessionService wrapping
  QueueForwardingPlayer), setMediaItem/prepare/play. Repeat implemented
  manually in handlePlaybackEnded (ExoPlayer repeat always OFF). Position
  polled every 200ms -> currentPosition (v0.5.0 regression surface).
- Emulator seeding: adb push mp3/ogg to /sdcard/Music/ + media scan
  broadcast; pm grant READ_MEDIA_AUDIO. JVM: insert TrackEntity into
  in-memory Room / Robolectric ShadowContentResolver; file:// fixtures let
  real ExoPlayer decode under Robolectric with media3-test-utils
  (TestPlayerRunHelper) - NOTE media3-test-utils NOT yet in
  gradle/libs.versions.toml, must be added.

Group A - Onboarding, permissions, scanning (14)
- [ ] local-enable-first-scan [E2E]: Enable CTA + grant + 3 seeded files ->
  scanning indicator, 3 rows, header "3 songs", DB rows source=local.
- [ ] local-enable-permission-denied [E2E]: deny -> no scan, empty state, no
  crash, 0 rows.
- [ ] local-empty-library-after-grant [smoke/JVM]: zero files ->
  local_no_local_music empty state, FAB absent.
- [ ] local-scan-clears-previous [JVM + E2E]: re-enable -> clearAll (rows +
  local_art) then rescan, no dup ids.
- [ ] local-rescan-idempotent [JVM]: second scan added=0 removed=0.
- [ ] local-scan-removes-deleted-file [JVM + E2E]: removed=1, row gone.
- [ ] local-scan-zero-results-guard [JVM]: 0-row scan with N in DB -> deletion
  skipped (permission-loss protection).
- [ ] local-scan-unknown-artist-album [JVM]: <unknown> -> "Unknown Artist/Album".
- [ ] local-scan-dttt-tracknumber [JVM]: TRACK=2005 -> trackNumber 5.
- [ ] local-scan-missing-title-fallback [JVM]: null TITLE -> display name minus
  extension.
- [ ] local-sync-worker-scheduled [JVM]: unique periodic work local_music_sync
  (6h, idle + battery-not-low) via WorkManager test API.
- [ ] local-saf-folder-add [E2E]: tree picked -> chip listed, rows added with
  folderUri=tree.
- [ ] local-saf-folder-remove [JVM + E2E]: tracks + art deleted, permission
  released, list updates live.
- [ ] local-disable-local-music [smoke]: toggle off -> enable-CTA state, sync
  work cancelled.

Group B - Library browse, sort, filter (21)
- [ ] local-list-renders-metadata [JVM]: MusicRow title/artist/art or
  TrackArtPlaceholder; header count.
- local-sort-title-az-default, local-sort-artist-az, local-sort-album-az
  (album then trackNumber), local-sort-shortest-longest, local-sort-date-
  added (desc), local-sort-release-year (desc, ties by title) [all JVM].
- [ ] local-sort-reverse-toggle [JVM]: order inverts immediately.
- [ ] local-sort-persistence [JVM]: keep-sort setting -> restored after restart.
- [ ] local-filter-artist-single [JVM]: chip label = artist, header "X of N".
- [ ] local-filter-artist-multi [JVM]: union, label "2 artists".
- [ ] local-filter-album [JVM]; local-filter-duration [JVM]: boundaries 2:59
  UNDER_3, 3:00/5:00 THREE_TO_FIVE, 5:01 OVER_5.
- [ ] local-filter-favorites [JVM]; local-filter-folder [JVM]: chip only when >1
  folder, label = segment after ':'.
- [ ] local-filter-unknown-artist-chip [JVM]: sorted last, filters blank-artist.
- [ ] local-filter-combined [JVM]: AND across categories.
- [ ] local-filter-clear-via-artist-nav [E2E + JVM]: player artist tap ->
  requestLocalArtistFilter replaces whole filter state, consumed once.
- [ ] local-filter-persistence [JVM]: keep-filters restored from DataStore.
- [ ] local-fastscrollbar-threshold [JVM]: present at 16 tracks, absent at 15.
- [ ] local-empty-filter-result [JVM]: 0 matches -> empty list, "0 of N", FAB
  hidden, no crash.

Group C - Search on Local tab (10)
- [ ] local-search-debounce [JVM]: ~300ms, isSearching transitions, DB
  searchLocalTracks.
- local-search-filter-tracks / -artists / -albums [JVM]: per-chip match
  fields (locale-aware lowercase).
- [ ] local-search-no-results [JVM]; local-search-clear [JVM]: reset + recents.
- [ ] local-search-play-result [E2E]: queue = searchResults, playback +
  onExpandPlayer.
- [ ] local-search-recent-saved [JVM]: source "local", 8 shown/20 kept, tap
  re-runs.
- [ ] local-search-recent-remove-clear [JVM]; local-search-history-disabled
  [JVM]: nothing saved, recents hidden.

Group D - Starting playback (v0.5.0 regression core) (12)
- [ ] local-play-track-starts [JVM media3-test-utils + smoke]: THE regression
  test - isPlaying true, state READY, duration>0, currentPosition STRICTLY
  INCREASES within 1-2s; mini player appears with title/artist.
- [ ] local-play-sets-queue [JVM]: queue == filteredTracks, index = tapped row.
- [ ] local-play-respects-filter-sort [JVM]: queue order = visible order.
- [ ] local-play-content-uri [JVM]: content:// passed unchanged, READY.
- [ ] local-play-bare-path-conversion [JVM]: /sdcard path -> file:// scheme.
- [ ] local-play-null-streamurl [JVM]: warn logged, IDLE, no service start, NO
  crash.
- [ ] local-play-starts-foreground-service [JVM + smoke]: startForegroundService
  exactly once per session; FGS + media notification visible.
- [ ] local-play-metadata-in-mediaitem [JVM]: title/artist/album, trackNumber=
  index+1, totalTrackCount, artworkUri only when artUrl valid.
- [ ] local-play-mix-fab [JVM + E2E]: shuffled copy of filteredTracks, all ids
  once, position advances.
- [ ] local-play-addtorecent [JVM]: libraryRepository.addToRecent called.
- [ ] local-play-same-track-again [smoke]: restarts from 0, advances.
- [ ] local-play-while-other-playing [JVM + smoke]: B replaces A from 0, queue
  replaced, mini player swaps.

Group E - Transport controls (15)
- [ ] local-pause-resume [JVM + smoke]: pause freezes position (poller stopped,
  snapshot taken); resume continues from same value, not 0.
- [ ] local-toggleplaypause-idempotent [JVM]: rapid double-tap consistent, no dup
  service starts.
- [ ] local-seek-forward [JVM + smoke]: clamps, optimistic position update,
  advancing resumes <=500ms (seekInProgress cleared).
- [ ] local-seek-while-paused [JVM]: not stuck (500ms fallback); resume from seek
  point.
- [ ] local-seek-beyond-duration [JVM]: clamped to duration; local-seek-negative
  [JVM]: clamped to 0.
- [ ] local-skip-next [JVM + smoke]: index+1, plays from 0, notification 2/3.
- [ ] local-skip-next-at-end-repeat-off [JVM]: no-op, keeps playing, no crash.
- [ ] local-skip-next-at-end-repeat-all [JVM]: wraps to 0.
- [ ] local-skip-prev-restart-rule [JVM + smoke]: >3s restarts track; <3s goes to
  prior item.
- [ ] local-skip-prev-at-start [JVM]: index 0 <3s -> seek 0, no crash.
- [ ] local-track-auto-advance [JVM media3 + E2E]: STATE_ENDED handled once
  (dedup guard), next auto-plays, position resets + advances.
- [ ] local-queue-end-repeat-off [JVM]: stops, currentTrack retained in mini.
- [ ] local-play-after-ended-repeat-off [JVM]: play replays current from 0.
- [ ] local-play-after-ended-repeat-all [JVM]: restarts queue at 0.

Group F - Shuffle and repeat (9)
- [ ] local-shuffle-on [JVM]: current moved to index 0, rest shuffled, no
  re-prepare.
- [ ] local-shuffle-off-restores [JVM]: original order, index follows track id.
- [ ] local-shuffle-edit-invalidates-restore [JVM]: edit while shuffled -> no
  restore (originalQueue cleared), no crash.
- [ ] local-shuffle-single-track [JVM]: no-op.
- [ ] local-repeat-cycle-ui [JVM]: OFF->ALL->ONE->OFF; ExoPlayer repeatMode stays
  OFF (custom handling).
- [ ] local-repeat-one-loops [JVM media3 + E2E]: track restarts, index unchanged.
- [ ] local-repeat-all-wraps [JVM]; local-repeat-off-advance [JVM].
- [ ] local-shuffle-favorite-patch [JVM]: favorite during shuffle; unshuffle
  works and restored entries carry updated isFavorite.

Group G - Queue management (13)
- [ ] local-queue-sheet-open [smoke/JVM]: "Up next (N)" = queue.size - index - 1.
- [ ] local-queue-skip-to-index [JVM + E2E]: skipToQueueIndex both directions,
  addToRecent.
- [ ] local-queue-remove-after-current [JVM]: index unchanged, uninterrupted.
- [ ] local-queue-remove-before-current [JVM]: index decremented.
- [ ] local-queue-remove-current-last [JVM]: index clamps, no crash, behavior
  defined.
- [ ] local-queue-remove-only-item [JVM]: empty queue, index -1, mini hides, no
  crash.
- [ ] local-queue-reorder-drag [JVM + E2E]: moveItem math across current/self/
  range; drag gesture on emulator.
- [ ] local-play-next [JVM + smoke]: inserted at index+1, snackbar "Playing
  next: <title>".
- [ ] local-play-next-empty-queue [JVM]: queue [track] index 0, no auto-start,
  mini appears.
- [ ] local-add-to-queue [JVM]: appended, snackbar "Added 1 to queue".
- [ ] local-add-all-to-queue [JVM]: plural snackbar "Added 3 to queue".
- [ ] local-queue-clear-on-dismiss [E2E]: mini swipe-down -> stop, items + queue
  cleared, position/duration 0, notification gone.

Group H - Mini player and full player (17)
- [ ] local-miniplayer-appears [JVM + smoke]: visible iff currentTrack != null;
  title/artist/art/progress ratio.
- [ ] local-miniplayer-controls [JVM + smoke]: prev/play-pause/next route to
  PlaybackManager, icon swaps, haptics.
- [ ] local-miniplayer-tap-expands [E2E]: shared-element transition, same track/
  position.
- [ ] local-miniplayer-drag-up-expands [E2E]: >25% threshold + haptic commits;
  early release snaps back.
- [ ] local-miniplayer-swipe-down-dismiss [E2E]: >50%/fling stops + dismisses;
  small drag snaps back.
- [ ] local-miniplayer-shape-morph [smoke]: art morphs/rotates only while
  playing; no crash.
- [ ] local-fullplayer-collapse [E2E]: collapse/back -> mini; state unaffected.
- [ ] local-fullplayer-position-labels [smoke]: elapsed label leaves "0:00"
  within 2s of play (regression assert), total = duration.
- [ ] local-fullplayer-favorite-toggle [JVM + E2E]: favorites row (type track),
  icon fills, filter + queue isFavorite patch reactively.
- [ ] local-fullplayer-download-icon-local [JVM]: "local file" state, tap
  no-ops (onDownloadTrack early-returns for isLocal).
- [ ] local-fullplayer-artist-click [E2E]: Local tab artist filter set, player
  collapses.
- [ ] local-fullplayer-playback-info [smoke]: "Local file", source path, id
  local_ms_*.
- [ ] local-fullplayer-no-track-state [JVM]: "No track playing", controls safe.
- [ ] local-fullplayer-volume [JVM]: ShadowAudioManager setStreamVolume
  STREAM_MUSIC; slider vs maxVolumeLevel.
- [ ] local-fullplayer-output-device [JVM + smoke]: setPreferredAudioDevice /
  null for Automatic; unplug callback clears selection.
- [ ] local-fullplayer-add-to-playlist [JVM + E2E]: playlist_tracks row +
  snackbar.
- [ ] local-progressbar-style-setting [JVM]: wavy<->linear swap, size applies to
  full player only.

Group I - Playlists with local tracks (12)
- [ ] local-playlist-create-via-sheet [JVM + E2E]: create with name/shape/icon,
  row + link, snackbar, visible in Library.
- [ ] local-playlist-add-existing [JVM]: appended at end position.
- [ ] local-playlist-add-duplicate [JVM]: defined behavior, row count asserted,
  no crash.
- [ ] local-playlist-detail-lists-tracks [smoke]; local-playlist-play [E2E]:
  queue = stored position order, position advances.
- [ ] local-playlist-remove-track [JVM]: row deleted, positions compacted,
  library track untouched.
- [ ] local-playlist-reorder [JVM + E2E]: moveTrackInPlaylist persists, survives
  reopen.
- [ ] local-playlist-rename [JVM]: rename + appearance update reflected.
- [ ] local-playlist-delete [JVM]: playlist + links gone, tracks remain, current
  queue playback continues.
- [ ] local-playlist-pin [JVM]: isPinned ordering in Library.
- [ ] local-playlist-downloadall-skips-local [JVM]: no-op, no network, no crash.
- [ ] local-playlist-deleted-track-row [E2E]: track deleted from library ->
  playlist play does not crash.

Group J - MediaSession, notification, service lifecycle, audio focus (11)
- [ ] local-notification-appears [E2E]: media notification, functional prev/next
  (QueueForwardingPlayer exposes SEEK_TO_NEXT/PREVIOUS with single
  MediaItem).
- [ ] local-notification-playpause [E2E]: state mirrors, position freezes/
  resumes.
- [ ] local-notification-skip-next-prev [E2E + JVM unit]: routes through custom
  queue; buttons disabled at edges per hasNext/hasPrevious.
- [ ] local-mediasession-controller [E2E]: adb media dispatch / KEYCODE_MEDIA_
  PLAY_PAUSE toggles.
- [ ] local-notification-dismiss-idle [E2E]: stop -> notification removed
  (idle player NEVER shown).
- [ ] local-idle-stop-5min [JVM + E2E]: paused 5min -> player stopped + stopSelf;
  resume restarts service cleanly.
- [ ] local-service-restart-after-release [JVM]: reinitialize() after idle-stop
  release; play works (cousin of v0.5.0 "play does nothing").
- [ ] local-task-removed-paused [E2E]: paused + swipe recents -> service stops,
  no zombie notification; playing -> continues.
- [ ] local-audio-becoming-noisy [E2E]: headphone unplug broadcast pauses.
- [ ] local-audio-focus-loss [E2E]: focus loss pauses; transient ducks/resumes.
- [ ] local-rotation-process-death [E2E]: rotation + don't-keep-activities: state
  survives, UI reattaches to session.

Group K - Edge cases, corruption, deletion (12)
- [ ] local-corrupt-file [JVM media3 + E2E]: onPlayerError -> IDLE, no crash,
  next track playable.
- [ ] local-file-deleted-while-queued [E2E]: auto-advance hits missing file ->
  error, app usable, manual skip works.
- [ ] local-file-deleted-current [E2E]: graceful, no crash on later seek/skip.
- [ ] local-delete-track-in-app [JVM + E2E]: confirm dialog -> contentResolver
  delete + DB row, animated removal, count decrements; cancel intact.
- [ ] local-delete-currently-playing [E2E]: defined, crash-free.
- [ ] local-delete-dialog-text [JVM]: title/body/error-styled confirm.
- [ ] local-permission-revoked-runtime [E2E]: zero-scan guard keeps library;
  graceful playback failures; CTA re-offered; no crash.
- [ ] local-zero-duration-track [JVM]: renders, rawProgress guarded, real
  duration from READY.
- [ ] local-very-long-titles [JVM]: 200-char marquee/ellipsize, no crash.
- [ ] local-large-library [E2E perf]: 2000 tracks scroll/sort/filter no ANR; Mix
  shuffles all.
- [ ] local-concurrent-scan-and-play [E2E]: rescan during playback: queue
  untouched, no dup rows.
- [ ] local-rapid-track-taps [JVM + smoke]: 5 taps in 1s -> last wins (playJob
  cancellation), no orphaned playback.

Key files: LocalScreen.kt, LocalViewModel.kt, PlaybackManager.kt,
QueueManager.kt, QueueForwardingPlayer.kt, PlaybackService.kt,
PlayerViewModel.kt, MiniPlayer.kt, FullPlayer.kt, MediaStoreScanner.kt,
LocalMusicRepositoryImpl.kt, PlaylistRepository.kt, PlaylistDao.kt.

---
