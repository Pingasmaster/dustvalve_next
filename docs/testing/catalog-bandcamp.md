# Bandcamp provider - E2E test scenario catalog (148 scenarios)

Backlog + traceability for the workflow test suites. Each implemented test
references its id here. Tiers: JVM (Robolectric/Compose), emulator-smoke,
emulator-E2E. Network: hermetic-possible (MockWebServer + fixtures under
data/src/test/resources/fixtures/bandcamp/) or live.

Status legend: [ ] backlog, [x] implemented (test class#method noted).

## A. Browse / Discover

- [ ] bc-home-tab-visible-when-enabled [JVM, hermetic]: enabling the provider
  adds the tab; tab shows "Discover" header + search bar.
  (Partially: ProviderScreensCrashRegressionTest#bandcampTab_opensWithoutCrash)
- [ ] bc-home-genre-tiles-render [JVM, hermetic]: all 27 built-in genre tiles
  in order + "Add custom genre" row last.
- [ ] bc-home-genre-previews-fan-in [JVM, hermetic]: up to 3 tilted covers per
  tile; failed previews render name-only.
- [ ] bc-home-preview-concurrency [JVM, hermetic]: max 5 discover requests in
  flight (Semaphore), ~27 total.
- [ ] bc-home-preview-failure-isolated [JVM, hermetic]: one genre 500 -> only
  that tile art-less, no error UI.
- [ ] bc-category-sheet-opens-loading [JVM, hermetic]: sheet + loading state.
- [ ] bc-category-sheet-content [JVM, hermetic]: 10-album carousel + "More"
  list; art errors fall back to ic_album.
  (Live variant: LiveProvidersE2eTest#bandcamp_genreSheet_loadsAlbums)
- [ ] bc-category-sheet-album-click [JVM, hermetic]: sheet dismisses ->
  AlbumDetail.
- [ ] bc-category-sheet-error-retry [JVM, hermetic]: error + Retry recovers.
- [ ] bc-category-sheet-dismiss-cancels [JVM, hermetic]: in-flight job
  cancelled, no stale albums on reopen.
- [ ] bc-best-selling-empty-tag [JVM, hermetic]: genre=null request, no
  sub-tag chips.

## B. Sub-tag filtering

- [ ] bc-subtags-shown-for-known-genres [JVM, hermetic]: "All" + GenreSubTags.
- [ ] bc-subtag-select-reloads [JVM, hermetic]: list clears, reload with tag.
- [ ] bc-subtag-all-restores-parent [JVM, hermetic].
- [ ] bc-subtag-error-retry-uses-subtag [JVM, hermetic]: retry re-requests the
  SUB-TAG, not the parent.
- [ ] bc-subtag-rapid-switch-cancels [JVM, hermetic]: only last selection
  lands.

## C. Custom genres

- [ ] bc-custom-genre-add-dialog [JVM, hermetic]: Add disabled while blank.
- [ ] bc-custom-genre-add-success [JVM, hermetic]: validating spinner ->
  persisted tile, survives restart.
- [ ] bc-custom-genre-slugify [JVM, hermetic]: "Drum & Bass!!" -> slug
  "drum-bass", label keeps original.
- [ ] bc-custom-genre-duplicate-builtin [JVM, hermetic]: inline exists-error,
  no network call.
- [ ] bc-custom-genre-duplicate-custom [JVM, hermetic].
- [ ] bc-custom-genre-not-found [JVM, hermetic]: 0 albums -> not persisted.
- [ ] bc-custom-genre-network-error [JVM, hermetic]: error, re-interactable.
- [ ] bc-custom-genre-dialog-locked-while-validating [JVM, hermetic].
- [ ] bc-custom-genre-tile-opens-sheet [JVM, hermetic]: color cycles palette.
- [ ] bc-custom-genre-remove-longpress [JVM, hermetic]: haptic + confirm
  dialog; Remove deletes, Cancel keeps.

## D. Search (in-tab overlay)

- [ ] bc-search-expand-overlay [JVM, hermetic]: type chips All/Artists/Albums/
  Tracks + recents.
- [ ] bc-search-debounced-typing [JVM, hermetic]: exactly one request ~400ms
  after last keystroke.
- [ ] bc-search-results-render [JVM, hermetic]: per-type rows, subtitle,
  genre suffix, 48dp thumbnail with per-type shape.
- [ ] bc-search-type-filter-artist [JVM, hermetic]: search_filter "b".
- [ ] bc-search-type-filter-album-track [JVM, hermetic]: "a" / "t"; scroll
  resets via searchGeneration.
- [ ] bc-search-empty-results [JVM, hermetic]: no-results state, no spinner.
- [ ] bc-search-error-first-page [JVM, hermetic]: centered error.
- [ ] bc-search-pagination-footer-and-noop [JVM, hermetic]: loadMore fires
  once, page 2 empty -> hasMore=false, footer gone.
- [ ] bc-search-pagination-error-snackbar [JVM, hermetic]: snackbar, results
  kept, error cleared.
- [ ] bc-search-dedup-on-loadmore [JVM, hermetic]: URL-keyed dedup, no
  LazyColumn key crash.
- [ ] bc-search-clear-button [JVM, hermetic]: clears, recents reappear, no
  blank-query request.
- [ ] bc-search-local-results-merged [JVM, hermetic]: LOCAL_TRACK rows first;
  tapping plays the local file.
- [ ] bc-search-result-album-click [JVM, hermetic]: collapse -> AlbumDetail.
- [ ] bc-search-result-artist-click [JVM, hermetic]: ArtistDetail bandcamp.
- [ ] bc-search-result-track-click-plays [smoke, hermetic]: resolve album,
  match track by title, play + expand.
- [ ] bc-search-result-track-title-fallback [JVM, hermetic]: no title match ->
  first track plays.
- [ ] bc-search-result-track-resolve-fails-silently [JVM, hermetic]: nothing
  plays, no crash (documented silent path).

## E. Recent searches

- [ ] bc-recent-search-saved-on-submit [JVM, hermetic]: source "bandcamp",
  8 shown / 20 kept, tap re-runs.
- [ ] bc-recent-search-not-saved-on-debounce [JVM, hermetic]: only IME submit
  saves.
- [ ] bc-recent-search-remove-and-clear [JVM, hermetic]: bandcamp-only rows
  deleted, YouTube recents untouched.
- [ ] bc-recent-search-disabled-by-setting [JVM, hermetic]: global or
  per-source toggle off -> nothing saved or shown.

## F. Long-press context actions

- [ ] bc-context-sheet-track-options [JVM, hermetic]: Play next / Add to
  queue / Add to playlist / Share / Open in browser.
- [ ] bc-context-sheet-album-options [JVM, hermetic]: Play all / Add all to
  queue / Share / Open in browser.
- [ ] bc-context-play-next [JVM, hermetic]: loading snackbar, inserted after
  current.
- [ ] bc-context-add-to-queue [JVM, hermetic]: appended at end.
- [ ] bc-context-play-all-album [smoke, hermetic]: scrape -> track 1 plays,
  player expands.
- [ ] bc-context-enqueue-all-album [JVM, hermetic]: all appended, playback
  state unchanged.
- [ ] bc-context-action-resolve-failure [JVM, hermetic]: snackbar_failed_load,
  queue unchanged.
- [ ] bc-context-share [smoke, hermetic]: ACTION_SEND with URL + name.
- [ ] bc-context-open-in-browser [smoke, hermetic]: ACTION_VIEW.
- [ ] bc-context-local-track-no-sheet [JVM, hermetic]: LOCAL_TRACK guard.

## G. Add to playlist

- [ ] bc-playlist-add-existing [JVM, hermetic]: resolve + AddToPlaylistSheet;
  track id <albumId>_<trackKey> row added.
- [ ] bc-playlist-create-and-add [JVM, hermetic]: create with name/shape/icon;
  trackCount 1 in Library.
- [ ] bc-playlist-play-bandcamp-track [E2E, live]: streams persisted mp3-128.
- [ ] bc-playlist-download-with-bandcamp-tracks [E2E, hermetic]: PlaylistWork
  downloads each; notification progress.

## H. Pasted links, deep links, sniffer

- [ ] bc-paste-album-link-chip / bc-paste-track-link-chip /
  bc-paste-artist-link-chip [JVM, hermetic]: chip label per type; track pages
  route to AlbumDetail; artist root -> ArtistDetail.
- [ ] bc-paste-apex-daily-rejected [JVM, hermetic]: bandcamp.com apex/daily ->
  no chip; Enter -> unsupported snackbar.
- [ ] bc-enter-on-url-opens-not-searches [JVM, hermetic].
- [ ] bc-custom-domain-sniff-album [JVM, hermetic]: sniff GET only on Enter;
  canonical og:url navigation.
- [ ] bc-custom-domain-sniff-artist-root [JVM, hermetic]: data-band marker.
- [ ] bc-custom-domain-sniff-not-bandcamp [JVM, hermetic]: unsupported
  snackbar.
- [ ] bc-sniff-network-failure [JVM, hermetic]: treated unsupported, no crash.
- [ ] bc-os-deeplink-album [E2E, hermetic]: VIEW intent -> AlbumDetail.
- [ ] bc-os-deeplink-track [E2E, live]: track page redirects to parent album.
- [ ] bc-os-deeplink-artist [E2E, live]: discography grid.
- [ ] bc-share-intent-text [E2E, hermetic]: ACTION_SEND routes like VIEW.
- [x] bc-deeplink-provider-disabled-dialog
  (NavigationDeepLinkE2eTest#bandcampDeepLink_withProviderDisabled_showsEnableDialog)
- [ ] bc-deeplink-invalid-url-guard [JVM, hermetic]: non-https refused
  silently (isValidHttpsUrl).
- [ ] bc-google-redirect-unwrap [JVM, hermetic]: google.com/url?q= unwrapped
  (max 3 levels).

## I. Album detail

- [ ] bc-album-load-render [JVM, hermetic]: full layout (top bar, hero art,
  action bar, tracks header, rows, about, tags).
- [ ] bc-album-load-error-retry [JVM, hermetic].
- [ ] bc-album-cache-hit-instant [JVM, hermetic]: cached-with-tracks never
  refetched; offline reopen renders.
- [ ] bc-album-stub-offline-error [JVM, hermetic]: stub row + offline ->
  error + Retry.
- [ ] bc-album-url-normalization [JVM, hermetic]: slash/query/fragment
  variants -> one cache row.
- [ ] bc-album-track-page-redirect [JVM, hermetic]: item_type track ->
  album_url followed (max 3).
- [ ] bc-album-single-track-release [JVM, hermetic]: 1-track album,
  trackNumber coerced 1.
- [ ] bc-album-null-track-ids [JVM, hermetic]: id-null entries keyed
  <albumId>_idx<N>, no duplicate-key crash.
- [ ] bc-album-missing-artist-fallbacks [JVM, hermetic]: byArtist /
  band-name-location / og:site_name chain -> worst case "Unknown Artist".
- [x] bc-album-play-all [E2E, live]
  (LiveProvidersE2eTest#bandcamp_albumDeepLink_playAll_positionAdvances)
- [ ] bc-album-shuffle [smoke, hermetic]: permutation of all tracks.
- [ ] bc-album-row-click-plays-from-index [smoke, hermetic]: index 2 current,
  next -> index 3.
- [ ] bc-album-open-artist-button [JVM, hermetic]: artistUrl navigation;
  disabled when blank.
- [ ] bc-album-favorite-toggle [JVM, hermetic]: optimistic heart; favorite
  row type "album"; Library reflects.
- [ ] bc-album-favorite-rollback-on-error [JVM, hermetic].
- [ ] bc-track-favorite-toggle-and-sync [JVM, hermetic]: reactive merge, no
  re-scrape.
- [ ] bc-album-tracks-header-label [JVM, hermetic]: tracksHeaderLabel output.
- [ ] bc-album-about-expand [JVM, hermetic]: 4-line collapse toggle.

## J. Prices and Buy CTA

- [ ] bc-buy-button-shown-for-bandcamp-only [JVM, hermetic].
- [ ] bc-buy-price-label [JVM, hermetic]: "$8.00" + browser open.
- [ ] bc-buy-no-price-fallback-label [JVM, hermetic]: free/NYP/no-JSON-LD/
  malformed fixtures -> generic label, no crash.
- [ ] bc-buy-menu-gift [JVM, hermetic]: chevron menu, gift opens browser.
- [ ] bc-buy-menu-discography-switch [JVM, hermetic]: bundle price/URL swap
  and revert.
- [ ] bc-buy-menu-single-track-switch [JVM, hermetic]: track price, album URL.
- [ ] bc-per-track-prices-fill-in [JVM, hermetic]: progressive price suffixes;
  navigation cancels fan-out.
- [ ] bc-per-track-price-never-errors [JVM, hermetic]: all 404 -> no suffix,
  no snackbar.

## K. Artist detail

- [ ] bc-artist-load-grid [JVM, hermetic]: hero, name, location, bio,
  2-column discography (fixtures incl. mobile + single-album layouts).
- [ ] bc-artist-single-album-layout [JVM, hermetic].
- [ ] bc-artist-album-click [JVM, hermetic].
- [ ] bc-artist-play-mix [smoke, hermetic]: loading state; one-track-per-album
  interleave plays.
- [ ] bc-artist-favorite [JVM, hermetic]: type "artist" persisted.
- [ ] bc-artist-download-all [E2E, hermetic]: ArtistWork; icon flips when all
  albums downloaded.
- [ ] bc-artist-delete-all-downloads [JVM, hermetic]: confirm dialog; files +
  rows removed.
- [ ] bc-artist-buy-discography-cta [JVM, hermetic].
- [ ] bc-artist-error-retry [JVM, hermetic].
- [ ] bc-artist-empty-discography [JVM, hermetic]: detail_no_releases empty
  state.

## L. Playback / queue / player

- [x] bc-playback-stream-mp3-128 [E2E, live] (LiveProvidersE2eTest)
- [ ] bc-playback-null-streamurl-skipped [JVM, hermetic]: logged skip, no
  crash. (Covered at manager level:
  PlaybackPositionAdvancesTest#playTrack_nullStreamUrl_noopsWithoutCrash)
- [ ] bc-playback-next-prev-across-album [E2E, live]: mini player + media
  notification transitions.
- [ ] bc-playback-downloaded-file-preferred [E2E, hermetic]: offline playback
  from file:// URI.
- [ ] bc-playback-background-service [E2E, live]: FGS keeps playing 30s+.
- [ ] bc-queue-mixed-sources [E2E, live]: transitions across local/BC/YT.

## M. Downloads

- [ ] bc-download-single-track [E2E, hermetic]: in-flight row state, snackbar,
  DownloadEntity(format mp3-128), file exists.
- [ ] bc-download-track-failure-retry [JVM, hermetic]: 404 -> error snackbar
  with Retry; retry succeeds after fix.
- [ ] bc-download-album [E2E, hermetic]: batch notification progress;
  null-streamUrl tracks skipped without failing batch.
- [ ] bc-download-album-delete [JVM, hermetic]: confirm; files + rows gone.
- [ ] bc-download-track-delete [JVM, hermetic]: per-track confirm + snackbar.
- [ ] bc-download-survives-navigation [E2E, hermetic]: controller-scope work
  continues.
- [ ] bc-download-pause-resume-notification [E2E, hermetic]: Range resume from
  byte offset.
- [ ] bc-download-cancel-all [E2E, hermetic]: queue cleared, partials deleted,
  FGS ends.
- [ ] bc-download-dedup [JVM, hermetic]: one work item per track id.
- [ ] bc-download-stale-tmp-purge [JVM, hermetic]: .tmp purged on cold start.
- [ ] bc-auto-download-favorites [E2E, hermetic]: favorite -> auto-enqueue;
  unfavorite does not delete.
- [ ] bc-auto-download-album-flag [JVM, hermetic]: autoDownload=true persisted
  for future re-scrapes.

## N. Account, collection, HQ downloads

- [ ] bc-login-webview-flow [E2E, live]: FLAG_SECURE; identity cookie captured;
  Settings shows account.
- [ ] bc-login-blocks-foreign-domains [E2E, live].
- [ ] bc-login-page-error [smoke, hermetic]: offline error text.
- [ ] bc-signout [JVM, hermetic]: keys cleared, WebView cookies expired,
  snackbar.
- [ ] bc-collection-sync-playlist [E2E, hermetic]: fan_id POST; system
  Collection playlist synced; purchase info persisted.
- [ ] bc-collection-pagination [JVM, hermetic]: older_than_token followed.
- [ ] bc-auto-download-collection-toggle [E2E, hermetic]: sequential
  best-effort album downloads.
- [ ] bc-hq-download-purchased [E2E, live]: download page -> preferred format
  (FLAC -> MP3_320 -> V0 -> AAC -> OGG).
- [ ] bc-hq-download-fallback-to-mp3128 [JVM, hermetic]: silent fallback.

## O. Library integration

- [ ] bc-library-favorite-album-listed [JVM, hermetic]: offline-openable from
  cache.
- [ ] bc-library-favorite-track-artist-listed [JVM, hermetic]: reactive
  unfavorite round-trip.
- [ ] bc-library-downloaded-section [smoke, hermetic].

## P. Provider gating

- [ ] bc-provider-disable-hides-tab [JVM, hermetic]: redirect to Local home,
  stack reset.
- [ ] bc-provider-reenable-fresh-stack [JVM, hermetic]: lands on BandcampHome.
- [ ] bc-search-history-source-toggle-visibility [JVM, hermetic].

## Q. Errors / edge / robustness

- [ ] bc-offline-discover [smoke, hermetic]: name-only tiles; sheet error +
  Retry.
- [ ] bc-offline-search [smoke, hermetic].
- [ ] bc-offline-cached-album-playback-downloaded [E2E, hermetic].
- [ ] bc-scrape-http-error-codes [JVM, hermetic]: 404/410/503 -> error +
  Retry, no retry storm.
- [ ] bc-scrape-no-tralbum-data [JVM, hermetic]: IllegalStateException ->
  error UI.
- [ ] bc-track-redirect-loop-guard [JVM, hermetic]: "Too many redirects".
- [ ] bc-stream-resolver-refetch [JVM, hermetic]: DustvalveStreamResolver
  title/trackNum fallback; null on parse failure. NOTE: currently no
  production call site (PlaybackManager plays track.streamUrl directly).
- [ ] bc-unicode-metadata-rendering [JVM, hermetic]: accented fixtures render;
  filenames sanitized.
- [ ] bc-price-jsonld-array-and-invalid-first [JVM, hermetic]: array offers,
  invalid-first, multiple-offers fixtures.
- [ ] bc-rotation-state-retention [smoke, hermetic]: sheet/dialog/buy-mode/
  download survive rotation.
- [ ] bc-back-navigation-stack [JVM, hermetic]: reverse traversal; stack cap
  20.
- [ ] bc-rapid-tab-switching-mid-load [smoke, hermetic]: no preview storms
  beyond semaphore.
