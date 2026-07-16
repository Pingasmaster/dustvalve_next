# YouTube Music provider - E2E test scenario catalog (95 scenarios)

Backlog + traceability for the workflow test suites. Each implemented
test references its id here; mark ids [x] with the test class as waves
land. Tiers: JVM (Robolectric/Compose), emulator-smoke, emulator-E2E.


Boundary: YTM is a sub-tab of the YouTube tab (YouTubeSource.YouTubeMusic).
Home/search use YouTubeMusicInnertubeClient (WEB_REMIX,
music.youtube.com/youtubei/v1). Playback/track-info/downloads delegate to the
plain-YouTube stack (canonical www.youtube.com/watch URL + YouTubeInnertube
Client.player). Hermetic seams: YouTubeMusicInnertubeClient.baseUrl and
YouTubeMusicVisitorDataFetcher.landingUrl/fallbackLandingUrl are protected
open (MockWebServer-able); Room-seeded home cache.

Group 1: Home feed and shelves (20)
- [ ] ytm-home-first-load-skeleton [smoke, hermetic]: skeleton (hero block + chip
  bones + tile bones) while ytmHome==null, replaced by feed.
- [ ] ytm-home-loads-shelves [E2E, live/hermetic]: parallax hero, chip row,
  shelves in server order (QuickPicks 4-row paged grid, tile carousels,
  artist spotlight + shaped row).
- [ ] ytm-home-hero-donor-shelf-remainder [JVM/E2E, hermetic]: hero = item[0] of
  first non-empty hero shelf; donor shelf renders items[1..n]; 1-item donor
  disappears.
- [ ] ytm-home-hero-parallax-fade [smoke, hermetic]: hero translates 0.5x scroll,
  alpha floor 0.4, restores on scroll back.
- [ ] ytm-home-hero-play-video [E2E, live]: hero with videoId -> track info via
  watch URL, plays, player expands.
- [ ] ytm-home-hero-open-playlist [E2E, live/hermetic]: hero with playlistId ->
  CollectionDetail (VL stripped), no playback.
- [ ] ytm-home-hero-neither-id-snackbar [JVM/smoke, hermetic]: both ids null ->
  failed-to-load snackbar, no crash.
- [ ] ytm-home-quickpicks-row-click [E2E, live]: row body plays (same as button).
- [ ] ytm-home-quickpicks-play-button [smoke, live/hermetic]: tonal button same
  outcome as row click.
- [ ] ytm-home-quickpicks-paging [smoke, hermetic]: chunked 316dp pages of 4,
  page keys = first videoId; all songs reachable.
- [ ] ytm-home-first-tile-shelf-immersive [smoke, hermetic]: only FIRST tile
  shelf is 240dp immersive carousel; later ones 150dp card rows.
- [ ] ytm-home-tile-song-click-plays [E2E, live]: SONG/VIDEO tile ->
  onPlayVideoId, player expands.
- [ ] ytm-home-tile-album-click-opens-collection [E2E, live]: ALBUM/PLAYLIST tile
  -> CollectionDetail (VL stripped).
- [ ] ytm-home-artist-spotlight-open [E2E, live]: spotlight card -> ArtistDetail
  on channel URL with name + thumbnail.
- [ ] ytm-home-artist-shape-row-open [smoke, hermetic]: shaped-row artists
  navigate; shapes cycle Cookie9/Clover/Sunny/Arch.
- [ ] ytm-home-mixed-tile-shelf-filters-artists [JVM, hermetic]: mixed shelf
  filters artist placeholders; all-artist shelf becomes Artists shelf.
- [ ] ytm-home-empty-shelf-dropped [JVM, hermetic]: zero-item shelf + header not
  rendered.
- [ ] ytm-home-tastebuilder-promo-skipped [JVM, hermetic]: tastebuilder/notifier
  renderers silently skipped.
- [ ] ytm-home-itemsection-flattening [JVM, hermetic]: shelves nested in
  itemSectionRenderer parsed as top-level.
- [ ] ytm-home-twocolumn-response [JVM, hermetic]: twoColumnBrowseResultsRenderer
  variant parses; also ytm-home-thumbless-items-placeholder [smoke,
  hermetic]: missing thumbnails -> placeholder, no Coil crash.

Group 2: Mood chips and home cache (9)
- [ ] ytm-chip-select-loads-mood-home [E2E, live]: chip checked, wavy progress,
  chips disabled while refreshing; browse(FEmusic_home, params).
- [ ] ytm-chip-reselect-toggles-off [smoke, hermetic]: same chip -> deselect,
  default home reloaded.
- [ ] ytm-chip-switch-cancels-inflight [JVM, hermetic]: chip B cancels job A;
  no stale feed.
- [ ] ytm-chips-disabled-while-refreshing [smoke, hermetic].
- [ ] ytm-home-cache-hit-instant [E2E, hermetic]: <1h cache renders offline from
  Room key "home".
- [ ] ytm-home-cache-stale-background-revalidate [JVM + E2E, hermetic]: stale
  snapshot renders first, silent background refresh, errors swallowed.
- [ ] ytm-home-cache-corrupt-refetch [JVM, hermetic]: garbage feedJson swallowed,
  live fetch + re-cache.
- [ ] ytm-mood-cache-keyed-per-chip [JVM + E2E, hermetic]: keys "home",
  "mood:<paramsA>", "mood:<paramsB>" each render offline.
- [ ] ytm-home-no-chips-renders-feed-only [JVM, hermetic]: no chipCloudRenderer
  -> chip row omitted, shelves render.

Group 3: Search (21)
- [ ] ytm-search-debounced-typing [E2E, live]: 400ms debounce, hits
  youtubeMusicRepository.search (WEB_REMIX), not plain YT.
- [ ] ytm-search-enter-immediate [smoke, hermetic]: IME search cancels debounce,
  runs now, saves recent (source "youtube").
- [ ] ytm-search-default-filter-is-songs [JVM, hermetic]: null filter sends
  SONGS_PARAMS.
- [ ] ytm-search-filter-artists [E2E + JVM, live/hermetic]: ARTISTS_PARAMS,
  YOUTUBE_ARTIST rows with channel URLs.
- [ ] ytm-search-filter-playlists [E2E + JVM, live/hermetic]: PLAYLISTS_PARAMS,
  YOUTUBE_PLAYLIST rows, VL stripped.
- [ ] ytm-search-filter-tracks-maps-to-songs [JVM, hermetic]: "songs" + unknown
  filters -> SONGS_PARAMS.
- [ ] ytm-search-filter-change-resets-results [JVM + smoke, hermetic]: results
  cleared, generation bumped, fresh page-1.
- [ ] ytm-search-no-pagination [JVM + smoke, hermetic]: hasMore false (nextPage
  always null), no footer spinner.
- [ ] ytm-search-top-result-card [JVM, hermetic]: musicCardShelfRenderer parsed
  as first row; watch->TRACK, ARTIST pageType->channel, ALBUM->playlist.
- [ ] ytm-search-row-kind-label-stripped [JVM, hermetic]: "Song * Artist * Album"
  kind label + separators dropped, joined ", ".
- [ ] ytm-search-video-row-playlistitemdata-fallback [JVM, hermetic]: videoId
  fallback order itemWatch -> playlistItemData -> titleRun.
- [ ] ytm-search-result-dedupe-by-url [JVM, hermetic]: duplicate URLs filtered on
  merge; LazyColumn keyed by url never crashes.
- [ ] ytm-search-track-click-plays [E2E, live]: collapse + resolve + play +
  expand; failure -> failed-to-play snackbar.
- [ ] ytm-search-album-click-opens-collection [E2E, live].
- [ ] ytm-search-artist-click-opens-artist [E2E, live].
- [ ] ytm-search-empty-results [smoke, hermetic]: "no results" state, no stuck
  spinner.
- [ ] ytm-search-error-empty-state [JVM + smoke, hermetic]: 500 -> centered
  error; with results -> snackbar, results retained.
- [ ] ytm-search-clear-query-resets [smoke, hermetic].
- [ ] ytm-search-recent-shared-with-yt [E2E, hermetic]: single "youtube" bucket
  (20 stored / 8 shown); history toggle gates recording.
- [ ] ytm-search-source-switch-cancels [JVM, hermetic]: switch to YT cancels
  in-flight, resets state, no leak.
- [ ] ytm-search-pasted-link-chip [smoke, hermetic]: music.youtube.com watch URL
  -> SONG chip, routes via onOpenLink.

Group 4: Playback and stream resolution (11)
- [ ] ytm-play-resolves-live-stream [E2E, live]: download miss -> getStreamUrl
  live; plays; currentPlaybackFormat/SourcePath null.
- [ ] ytm-play-downloaded-uses-local-file [E2E, hermetic]: download hit -> local
  file offline, format + path set.
- [ ] ytm-play-stream-resolve-failure-skips [JVM + E2E, hermetic]:
  snackbar_audio_stream_failed, streamUrl null, watch URL never reaches
  ExoPlayer.
- [ ] ytm-play-track-id-prefix [JVM, hermetic]: id yt_<videoId>, source YOUTUBE,
  cached streamUrl null.
- [ ] ytm-track-metadata-cache-hit [JVM, hermetic]: second getTrackInfo zero
  /player + /next calls.
- [ ] ytm-album-lookup-on-first-play [JVM + E2E, live/hermetic]: /next MPREb ->
  /browse audioPlaylistId OLAK5uy_ -> albumUrl set.
- [ ] ytm-album-lookup-non-music-video [JVM, hermetic]: no ALBUM pageType ->
  albumUrl "" + albumLookupDone, never retried.
- [ ] ytm-album-lookup-failure-suppressed [JVM, hermetic]: lookup throws ->
  playback unaffected, no snackbar.
- [ ] ytm-album-lookup-upgrade-stale-cache-row [JVM, hermetic]: albumLookupDone
  =false row upgraded exactly once.
- [ ] ytm-play-updates-last-video-id [E2E, live]: YTM play seeds YT discover
  recommendations after restart.
- [ ] ytm-repo-resolvestreamurl-uses-yt-client [JVM, hermetic]: resolveStreamUrl
  hits the PLAIN YT innertube client, not WEB_REMIX.

Group 5: Queue and context actions (7)
- [ ] ytm-context-play-next [E2E, live]; ytm-context-add-to-queue [E2E, live];
- [ ] ytm-context-play-all-album [E2E, live]: resolvePlaylistTracks -> playAlbum;
  empty -> no-op no crash.
- [ ] ytm-context-enqueue-all [E2E, live];
- [ ] ytm-context-share [smoke, hermetic]: canonical youtube.com URL;
- [ ] ytm-context-open-in-browser [smoke, hermetic];
- [ ] ytm-queue-mixed-sources [E2E, live]: Bandcamp + YTM + local queue; each
  resolves via own branch; YTM failure skips only that entry.

Group 6: Downloads (5)
- [ ] ytm-download-track [E2E, live]: YOUTUBE branch, fresh /player resolve,
  file + row yt_<videoId>, notification completes.
- [ ] ytm-download-then-offline-playback [E2E, hermetic].
- [ ] ytm-progressive-download-after-play [E2E, live]: next play resolves local,
  no /player call.
- [ ] ytm-download-expired-stream-url [JVM + E2E, hermetic]: 403 mid-download ->
  RangeResumeDownloader retry/fail policy, no partial marked complete.
- [ ] ytm-download-delete-falls-back-to-stream [E2E, live].

Group 7: Playlists and import (6)
- [ ] ytm-import-playlist-from-search [E2E, live]: transactional insert, local
  playlist + favorite row (youtube_playlist), snackbar.
- [ ] ytm-import-album-as-playlist [E2E, live]: OLAK list, count matches album.
- [ ] ytm-import-failure-error [JVM + smoke, hermetic]: error state, transaction
  rollback, no half-written playlist.
- [ ] ytm-add-song-to-local-playlist [E2E, live]: AddToPlaylistSheet, create-flow
  with shape/icon.
- [ ] ytm-collection-screen-from-tile [E2E, live]: shared "youtube" source
  playlist parser; play works.
- [ ] ytm-source-adapter-capabilities [JVM, hermetic]: id "youtube_music",
  capabilities {SEARCH}, getArtist/getAlbum/getCollection/getArtistTracks all
  throw UnsupportedSourceOperation.

Group 8: Deep links music.youtube.com (13)
- [ ] ytm-deeplink-watch-plays [E2E, live]; ytm-deeplink-watch-with-list-plays-
  video [JVM + E2E, hermetic]: v= beats list=.
- [ ] ytm-deeplink-playlist [E2E, live]: CollectionDetail sourceId "youtube".
- [ ] ytm-deeplink-browse-vl-playlist [JVM, hermetic]; ytm-deeplink-browse-uc-
  artist [JVM + E2E, hermetic]; ytm-deeplink-channel-path [JVM, hermetic].
- [ ] ytm-deeplink-consent-wrapped [JVM, hermetic]; ytm-deeplink-google-redirect-
  wrapped [JVM, hermetic].
- [ ] ytm-deeplink-share-intent [E2E, live].
- [ ] ytm-deeplink-provider-disabled-gate [E2E, hermetic]: confirm enables +
  executes; dismiss no-op.
- [ ] ytm-deeplink-track-resolve-failure-silent [smoke, hermetic]: navigation
  happens, failure silent, no crash.
- [ ] ytm-deeplink-bare-music-home-unsupported [JVM, hermetic]: music.youtube.com/
  -> unsupported-link event.
- [ ] ytm-deeplink-library-liked-unsupported [JVM, hermetic]: /library, /explore
  -> unsupported.

Group 9: Errors and edge cases (16)
- [ ] ytm-home-network-error-state [E2E, hermetic]: EmptyState + Retry.
- [ ] ytm-home-retry-recovers [E2E, hermetic]: retry preserves selected chip.
- [ ] ytm-home-error-with-stale-feed-keeps-feed [JVM + smoke, hermetic]: old feed
  stays when refresh fails.
- [ ] ytm-home-message-renderer-country-block [JVM, hermetic]: messageRenderer ->
  IllegalStateException with renderer text in error state.
- [ ] ytm-home-unrecognized-response-diagnostic [JVM, hermetic]: renderer
  type-tree diagnostic, no crash.
- [ ] ytm-http-error-includes-body-snippet [JVM, hermetic]: HTTP code + first 200
  chars; empty 200 -> "returned empty body".
- [ ] ytm-visitor-data-primary-scrape [JVM, hermetic]: ytcfg scrape ->
  X-Goog-Visitor-Id + 1.x version; cached in memory, one landing GET.
- [ ] ytm-visitor-data-fallback-landing [JVM, hermetic]: fallback www landing +
  DEFAULT_CLIENT_VERSION.
- [ ] ytm-visitor-data-both-fail [JVM + smoke, hermetic]: diagnostic error state;
  retry re-scrapes (no poisoned persistence).
- [ ] ytm-visitor-web-clientversion-rejected [JVM, hermetic]: "2.x" fails "1."
  prefix check -> DEFAULT_CLIENT_VERSION.
- [ ] ytm-requests-cookieless [JVM, hermetic]: jar cookies never sent; only
  manual SOCS/CONSENT headers (guards half-login placeholder regression).
- [ ] ytm-request-headers-contract [JVM, hermetic]: Origin/X-Origin/Referer =
  music.youtube.com, client-name 67, WEB_REMIX, hl=en gl=US.
- [ ] ytm-throttled-mid-session [E2E, live]: 429/captcha -> recoverable per-
  surface errors, cached home renders, no ANR/crash.
- [ ] ytm-offline-mid-scroll [E2E, hermetic]: in-memory feed browsable, play ->
  snackbar, reconnect retry works.
- [ ] ytm-process-death-restore [E2E, hermetic]: cache-first reload, chip resets
  to default, no stale-VM crash.
- [ ] ytm-unicode-metadata-roundtrip [E2E, live]: CJK/accents/RTL through Room +
  notification.

Group 10: Sub-tab switching and settings (5)
- [ ] ytm-default-source-setting [E2E, hermetic]: default source applies on every
  tab entry, home auto-loads.
- [ ] ytm-manual-switch-is-per-visit [E2E, hermetic]: override discarded on
  leave; feed retained in VM (no refetch).
- [ ] ytm-lazy-home-load-on-first-switch [JVM + smoke, hermetic]: FEmusic_home
  fires only on first switch; zero loads after.
- [ ] ytm-switch-animation-and-state-isolation [smoke, hermetic]: search state
  reset per source; feeds keep own scroll/data.
- [ ] ytm-mini-player-persists-across-subtabs [E2E, live]: playback + mini player
  survive sub-tab and tab switches.

Key files: YouTubeMusicHome.kt, YouTubeScreen.kt (YTM branch),
YouTubeViewModel.kt, data/.../remote/youtubemusic/ (client, visitor fetcher,
2 parsers), YouTubeMusicRepositoryImpl.kt, YouTubeMusicSource.kt,
PlayerViewModel.kt (:166), DeepLinkRouter.kt, AndroidManifest.xml (:80-89).

---
