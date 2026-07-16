# YouTube provider - E2E test scenario catalog (78 scenarios)

Backlog + traceability for the workflow test suites. Each implemented
test references its id here; mark ids [x] with the test class as waves
land. Tiers: JVM (Robolectric/Compose), emulator-smoke, emulator-E2E.


Code boundary notes:
- YouTubeScreen.kt hosts both sub-tabs via YouTubeUiState.activeSource
  (YouTubeSource.YouTube vs YouTubeMusic, enum YouTubeViewModel.kt:49).
  Plain-YouTube surface = YouTubeSourceContent (discover feed) + search when
  activeSource == YouTube. YouTubeMusicHome + youtubeMusicRepository = YTM.
- Data boundary: data/.../remote/youtube/innertube/ + YouTubeRepositoryImpl.kt
  + YouTubeSource.kt are plain YouTube; data/.../remote/youtubemusic/ is YTM.
- music.youtube.com watch deep links canonicalize to www.youtube.com and play
  through the PLAIN YouTube repository (DeepLinkRouter.routeYouTube).
- onPlayItem (YouTubeScreen.kt:178) and onPlayVideoId (:190) both funnel into
  viewModel.getTrackInfo -> playerViewModel.playTrack ->
  resolveTrackForPlayback (PlayerViewModel.kt:166): downloads checked first,
  else youtubeRepository.getStreamUrl; failure -> snackbar_audio_stream_failed
  + streamUrl=null -> playback skipped (PlayerViewModel.kt:511).
- Live gate pattern: assumeTrue(System.getenv("DUSTVALVE_LIVE_NET") == "1")
  (LiveYtcfgFetcherSmokeTest.kt). Hermetic fixtures:
  app/src/test/resources/fixtures/youtube/ + MockWebServer via
  YouTubeInnertubeClient.baseUrlWww/baseUrlM overrides.

Group 1: Deep links and link routing (22)
- [ ] yt-deeplink-watch-url [emulator-E2E, live]: open watch URL externally ->
  PlayYouTubeVideo, navigates to YouTube tab, playback starts, mini-player
  shows title.
- [ ] yt-deeplink-youtu-be [E2E + JVM router, live/hermetic]: youtu.be?si= ->
  si stripped, id extracted, plays.
- [ ] yt-deeplink-shorts [E2E, live]: /shorts/<id> plays audio.
- [ ] yt-deeplink-m-host [JVM + smoke, hermetic router]: m.youtube.com accepted.
- [ ] yt-deeplink-music-host-is-video-play [JVM, hermetic]: music.youtube.com
  watch -> canonical www URL reaches getTrackInfo (plain repo).
- [ ] yt-deeplink-embed-live-v-e-watchpath [JVM, hermetic]: /embed,/live,/v,/e,
  /watch/ID all classify VIDEO (manifest only registers /watch,/shorts,
  /playlist,/channel,/@*; others are pasted-link-only).
- [ ] yt-deeplink-nocookie [JVM, hermetic]: youtube-nocookie.com/embed matches.
- [ ] yt-deeplink-country-tld [JVM, hermetic]: youtube.co.uk accepted.
- [ ] yt-deeplink-v-beats-list [JVM+E2E, hermetic router]: watch?v=..&list=..
  plays the single video.
- [ ] yt-deeplink-playlist-url [E2E + JVM, live content]: /playlist?list=PL ->
  Navigate(CollectionDetail sourceId=youtube), tracks via getPlaylistTracks.
- [ ] yt-deeplink-bare-list-param [JVM, hermetic]: list= without v= -> playlist.
- [ ] yt-deeplink-browse-vl [JVM, hermetic]: /browse/VLPLx -> VL stripped.
- [ ] yt-deeplink-channel-forms [JVM + one E2E, hermetic]: /channel/UC, /@handle,
  /c/, /user/, /browse/UC -> ArtistDetail(sourceId=youtube).
- [ ] yt-deeplink-redirector-unwrap [JVM, hermetic]: consent.youtube.com
  continue=, google.com/url?q=, attribution_link?u= unwrap (max depth 3).
- [ ] yt-deeplink-unwrap-depth-cap [JVM, hermetic]: 4 nested redirectors -> stop,
  no infinite loop.
- [ ] yt-deeplink-invalid-id-rejected [JVM/Robolectric, hermetic]: 10/12-char ids,
  ftp://, multi-word text -> detect null; unsupported-link notice
  (unsupportedLinkEvents).
- [ ] yt-deeplink-share-intent [E2E, live]: SEND text/plain watch URL -> plays.
- [ ] yt-deeplink-share-text-with-noise [JVM + smoke, hermetic]: URL embedded in
  prose - DeepLinkRouter.parse rejects whitespace; document intended behavior.
- [ ] yt-deeplink-provider-disabled-gate [E2E + JVM, live playback]: provider
  disabled -> pendingLinkConfirmation dialog; confirm enables + plays;
  dismiss -> nothing.
- [ ] yt-deeplink-resolution-failure-silent [smoke + JVM, hermetic]: failing
  getTrackInfo -> NavigationViewModel.execute swallows; navigates, no crash,
  no playback.
- [ ] yt-deeplink-paste-chip [E2E, live/hermetic chip]: pasting URL in search bar
  shows PastedLinkChip; tap plays.
- [ ] yt-deeplink-search-enter-on-url [E2E + JVM, hermetic]: Enter on a URL query
  -> onOpenLink, not text search.
- [ ] yt-deeplink-cold-vs-warm-start [E2E, live]: same deep link cold vs
  backgrounded (singleTop) -> exactly one playback; consumeDeepLink prevents
  duplicates on config change.

Group 2: Search and discover feed (19)
- [ ] yt-search-basic-tracks [E2E + JVM parser, live/hermetic]: debounced 400ms
  search shows YOUTUBE_TRACK rows.
- [ ] yt-search-debounce-cancel [JVM, hermetic]: rapid retype -> one search,
  searchJob cancelled.
- [ ] yt-search-filter-chips [JVM + smoke, hermetic]: Artists/Playlists/Tracks/All
  client-side filter, searchGeneration bumps.
- [ ] yt-search-pagination-single-page [JVM, hermetic]: nextPage=null -> hasMore
  false, no loadMore call, no infinite spinner.
- [ ] yt-search-result-tap-plays [E2E, live]: track row tap -> resolve, search
  collapses, player expands, PLAYING.
- [ ] yt-search-result-tap-playlist [E2E, live]: playlist/album row -> Collection
  Detail.
- [ ] yt-search-result-tap-artist [E2E, live]: artist row -> ArtistDetail.
- [ ] yt-search-play-failure-snackbar [smoke/Robolectric, hermetic]: resolve fails
  -> common_failed_to_play snackbar, overlay stays, no crash.
- [ ] yt-search-empty-results [JVM/smoke, hermetic]: gibberish -> "No results"
  state, no error snackbar.
- [ ] yt-search-error-empty-state [JVM, hermetic]: network fail + no results ->
  centered error text (YouTubeScreen.kt:169 snackbar needs non-empty results).
- [ ] yt-search-error-snackbar-with-results [JVM, hermetic]: failing follow-up
  search -> snackbar + clearError, results retained.
- [ ] yt-search-recents-lifecycle [E2E + JVM DAO, hermetic]: recents max 8 shown /
  20 kept, source=youtube; tap/remove/clear; both searchHistoryEnabled toggles
  gate saving.
- [ ] yt-search-source-switch-cancels [JVM, hermetic]: toggling to YTM cancels
  searchJob, clears results, bumps generation - no stale results.
- [ ] yt-discover-initial-feed [E2E + JVM, live/hermetic]: trending shelf + 6
  shuffled genre shelves, staggered 150ms; recommendations absent when
  lastYoutubeVideoId null.
- [ ] yt-discover-recommendations-after-play [E2E + JVM, live/hermetic]: after
  playing + relaunch, "Because you listened to <title>" shelf from /next;
  generic title when track row missing.
- [ ] yt-discover-shelf-tap-plays [E2E, live]: carousel/hero tap -> plays;
  failure -> snackbar.
- [ ] yt-discover-genre-infinite-scroll [JVM + smoke, hermetic]: loadMoreGenres
  batches of 4 until ~48 genres, loader disappears, no dup indices.
- [ ] yt-discover-section-retry [smoke + JVM, hermetic]: retrySection re-fetches
  only that section (trending/genre_N/recommendations).
- [ ] yt-discover-mood-select-deselect [E2E + JVM, live/hermetic]: mood chip
  select/deselect/switch mid-load; moodJob cancelled; error card retry.
- [ ] yt-default-source-setting [E2E + JVM, hermetic]: default source setting
  applies on every tab entry; manual toggle per-visit. (counted in group)

Group 3: Playback and stream resolution (15)
- [ ] yt-play-resolves-googlevideo [JVM fixture + E2E live]: getStreamUrl ->
  ANDROID_VR /player -> https googlevideo audio URL; PLAYING;
  currentPlaybackFormat/currentSourcePath null when streamed.
- [ ] yt-play-client-cascade-fallback [JVM, hermetic]: ANDROID_VR no-audio ->
  IOS fallback; exactly 2 POSTs, per-client headers.
- [ ] yt-play-all-clients-fail [JVM + smoke, hermetic]: cascade exhausted ->
  IllegalStateException -> snackbar_audio_stream_failed, playTrack early
  return :511, no ExoPlayer error.
- [ ] yt-play-age-restricted [JVM + E2E, both]: age-gate fails cascade ->
  graceful skip + snackbar.
- [ ] yt-play-unavailable-video [JVM, hermetic]: deleted/private -> skip +
  snackbar with playabilityStatus in message.
- [ ] yt-play-region-blocked [JVM, hermetic]: UNPLAYABLE -> skip, no retry loop.
- [ ] yt-play-livestream [E2E, live]: /live/ID - HLS-only likely fails cascade ->
  snackbar skip; no crash either way (document actual).
- [ ] yt-play-track-cache-vs-stream-freshness [JVM, hermetic]: metadata from
  videoCache but streamUrl always re-resolved (cached streamUrl null);
  second playback issues new /player call.
- [ ] yt-play-queue-advance-reresolve [E2E, live]: 3 queued YT tracks; advance
  re-resolves each; failing track 2 skipped with snackbar, track 3 plays.
- [ ] yt-play-visitor-data-flow [JVM, hermetic + live smoke]: visitorData +
  clientVersion extracted; X-Goog-Visitor-Id on /player. Live = existing
  LiveYtcfgFetcherSmokeTest.
- [ ] yt-play-no-cookies-sent [JVM, hermetic]: CookieJar.NO_COOKIES - no Cookie
  header on innertube calls.
- [ ] yt-play-bad-video-id-input [JVM, hermetic]: no valid id ->
  IllegalArgumentException -> failed-to-play snackbar.
- [ ] yt-play-bare-id-accepted [JVM, hermetic]: bare 11-char id accepted.
- [ ] yt-play-media-controls [E2E, live]: notification pause/resume/seek,
  background, screen off; metadata correct.
- [ ] yt-play-album-lookup-crossover [JVM, hermetic]: getTrackInfo populates
  albumUrl via YTM lookupAlbumPlaylistForVideo; lookup failure suppressed
  (resolveAlbumOnce -> ""); albumLookupDone upgraded once.

Group 4: Queue and context actions (7)
- [ ] yt-queue-play-next [E2E, live]: long-press -> Play Next -> loading snackbar,
  track after current in queue.
- [ ] yt-queue-add-to-queue [E2E, live]: appended to end; failure ->
  snackbar_failed_load.
- [ ] yt-queue-add-to-playlist [E2E, live resolve / hermetic persist]:
  AddToPlaylistSheet -> addTrackToPlaylist /
  createPlaylistAndAddArbitraryTrack; visible in playlist after.
- [ ] yt-queue-playall-playlist-result [E2E, live]: Play All -> resolvePlaylist
  Tracks -> playAlbum(tracks, 0); empty resolution -> no player expansion.
- [ ] yt-queue-enqueueall-playlist-result [E2E, live]: addAllToQueue; playback
  uninterrupted.
- [ ] yt-queue-share-and-browser [smoke, hermetic]: share sheet with URL; browser
  VIEW intent (note self-match loop risk).
- [ ] yt-queue-context-failure [JVM/Robolectric, hermetic]: getTrackInfo fails ->
  snackbar_failed_load, queue unchanged.

Group 5: Playlists, mixes, channels (9)
- [ ] yt-playlist-open-detail [E2E + JVM, live/hermetic]: VL<id> MWEB browse,
  title + tracks (fixture playlist_mweb.json).
- [ ] yt-playlist-pagination-cap [JVM, hermetic]: continuations followed, hard cap
  20 pages (~2k tracks).
- [ ] yt-playlist-import [E2E, live]: import icon -> tracks inserted, local
  playlist + favorite row (type youtube_playlist), snackbar, visible in
  Library.
- [ ] yt-playlist-import-failure [JVM, hermetic]: error_import_playlist snackbar,
  no partial rows.
- [ ] yt-playlist-cache-hit-and-revalidate [JVM, hermetic]: <24h cache hit zero
  network; stale -> instant cache + silent background refresh; partial video
  cache -> synchronous refetch.
- [ ] yt-mix-radio-playlist [E2E + JVM, live/hermetic]: RD mix via /next,
  MixContinuation paging, seenVideoIds dedupe, empty page ends mix.
- [ ] yt-channel-artist-page [E2E + JVM, live/hermetic]: getChannelVideos WEB
  browse, paginated via ChannelPageToken (fixture channel_web_videos.json).
- [ ] yt-channel-album-unsupported [JVM, hermetic]: UnsupportedSourceOperation
  (youtube, ALBUM) -> graceful error, no crash.
- [ ] yt-playlist-olak-album-as-playlist [JVM + E2E, hermetic/live]: OLAK5uy_
  album playlist routes as PLAYLIST, opens and plays.

Group 6: Downloads (11)
- [ ] yt-download-manual [E2E, live]: download -> getDownloadableStream, file
  yt_<id>.<ext>, DownloadEntity row with format, notifications.
- [ ] yt-download-plays-offline [E2E, live setup]: airplane mode -> plays from
  getDownloadInfo, no network; currentPlaybackFormat = stored format.
- [ ] yt-download-progressive [E2E, live]: progressive download after stream
  start; YT skips metered MP3_320 override; hot-swap to local file.
- [ ] yt-download-queue-track-googlevideo-url [JVM, hermetic]: resolved
  googlevideo streamUrl -> repo reconstructs watch URL from track.id.
- [ ] yt-download-no-redownload-same-quality [JVM, hermetic]: second download
  short-circuits (qualityRank + file exists).
- [ ] yt-download-https-enforced [JVM, hermetic]: http:// stream -> IOException
  "Download URL must use HTTPS".
- [ ] yt-download-saf-folder-mode [E2E, live]: SAF tree write via DocumentFile;
  playback resolves SAF URI.
- [ ] yt-download-failure-cleanup [smoke + JVM, hermetic]: mid-download network
  kill -> no final file, failure notification, no DB row, retry works.
- [ ] yt-download-resolution-failure [JVM, hermetic]: age-restricted download ->
  cascade throws, failure notification, no zero-byte file.
- [ ] yt-download-delete-then-stream [E2E, live]: delete download -> falls back
  to live getStreamUrl.
- [ ] yt-download-auto-favorites [E2E, live]: AutoDownloadFavoritesCoordinator
  schedules YT download on favorite; one failure does not block others.

Group 7: Errors, offline, edge cases (15)
- [ ] yt-offline-tab-open [E2E, hermetic]: FeedErrorCard per section with Retry;
  recovery after network restored.
- [ ] yt-offline-search [smoke, hermetic]: centered error; recents usable.
- [ ] yt-offline-play-cached-metadata [JVM + smoke, hermetic]: metadata from
  cache, getStreamUrl fails -> snackbar skip.
- [ ] yt-http-error-codes [JVM, hermetic]: 403/429/503/empty/malformed for
  /player /search /browse /next -> typed IllegalStateException, every UI
  entry converts to snackbar/error card.
- [ ] yt-visitor-fetch-failure [JVM, hermetic]: landing fetch fails -> fail fast,
  retry re-attempts (no poisoned cache).
- [ ] yt-parser-schema-drift [JVM, hermetic]: renamed/missing renderers -> skip
  or typed error, never NPE/ClassCast.
- [ ] yt-live-net-smoke-suite [JVM, live gated]: DUSTVALVE_LIVE_NET=1 live smokes
  for ytcfg + /player + /search + /browse(VL) + /next; skipped otherwise.
- [ ] yt-rapid-tap-no-double-play [smoke, live]: double-tap -> playJob supersedes,
  one track playing.
- [ ] yt-process-death-restore [E2E, live]: am kill + reopen; lastYoutubeVideoId
  persisted for recommendations.
- [ ] yt-rotation-mid-load [smoke, hermetic]: rotate during search/discover load
  and with sheet open - no dup requests, no crash.
- [ ] yt-ascii-and-unicode-titles [E2E + JVM, live/hermetic]: emoji/CJK titles
  render; sanitizeFileName; notification metadata intact.
- [ ] yt-very-long-playlist [E2E, live]: multi-thousand playlist capped ~20 pages,
  responsive, cache write best-effort.
- [ ] yt-empty-playlist [JVM, hermetic]: zero videos -> empty UI, no crash.
- [ ] yt-cache-write-failure-tolerated [JVM, hermetic]: DAO insert throws ->
  results still returned (cache writes swallowed).
- [ ] yt-concurrent-discover-and-search [smoke, live]: play from search while
  shelves load; independent jobs, no focus steal.

Key files: YouTubeScreen.kt (:178,:190), YouTubeViewModel.kt,
PlayerViewModel.kt (:166,:511), YouTubeRepositoryImpl.kt, YouTubeSource.kt
(:86 RD branch), YouTubeInnertubeClient.kt (:64-98 cascade), DeepLinkRouter.kt,
NavigationViewModel.kt (:155,:163), AndroidManifest.xml (:74-97),
DownloadRepositoryImpl.kt (:137), LiveYtcfgFetcherSmokeTest.kt.

---
