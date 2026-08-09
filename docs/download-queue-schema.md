# Pending download queue schema (v1)

Durable snapshot of unfinished download work so [DownloadController] can
resume after process death. Stored in a dedicated Preferences DataStore
file (`pending_download_queue.preferences_pb`), not Room (Room schema
stays at database version 1).

## Location

- DataStore name: `pending_download_queue`
- Key: `queue_json` (string Preferences key)
- Writer/reader: `PendingDownloadQueueStore`

## Document shape (JSON)

```
{
  "version": 1,
  "items": [
    {
      "trackId": "string",
      "formatKey": "mp3-128" | null,
      "albumId": "string",
      "title": "string",
      "artist": "string",
      "artistUrl": "string",
      "trackNumber": 0,
      "duration": 0.0,
      "streamUrl": "https://..." | null,
      "artUrl": "string",
      "albumTitle": "string",
      "source": "bandcamp" | "youtube" | "soundcloud" | "local",
      "folderUri": "string",
      "dateAdded": 0,
      "year": 0,
      "albumUrl": "string",
      "bandcampTrackUrl": "https://..." | null
    }
  ]
}
```

## Semantics

- `version` must be `1`. Unknown versions are treated as empty (no restore).
- `items` is an ordered list of pending **track** downloads. Album / artist /
  playlist controller work is flattened to track items before persist.
- Duplicate `trackId`s are collapsed on write (first wins).
- Empty `items` removes the key.
- Track metadata is embedded so restore does not depend on TrackDao (a
  mid-album scrape may not be inserted until commit).
- `formatKey` is optional; null means the controller uses the user default
  download format.

## Lifecycle

1. Every queue mutation (enqueue, complete, fail, cancelAll) rewrites the
   snapshot (active work + remaining queue).
2. Cold start: after the downloads-tree purge, `DownloadController` loads
   the snapshot and re-enqueues each item as `TrackWork`.
3. Corruption / decode failure yields an empty queue (safe default).
