#!/usr/bin/env python3
"""Live stress-test SoundCloud api-v2 paths the app uses."""

from __future__ import annotations

import json
import re
import sys
import urllib.parse
import urllib.request
from typing import Any

UA = (
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
)
API = "https://api-v2.soundcloud.com"
FAILS: list[str] = []
OKS: list[str] = []


def fail(msg: str) -> None:
    FAILS.append(msg)
    print(f"FAIL: {msg}")


def ok(msg: str) -> None:
    OKS.append(msg)
    print(f"OK:   {msg}")


def http_get(url: str) -> Any:
    req = urllib.request.Request(
        url,
        headers={
            "User-Agent": UA,
            "Accept": "application/json",
            "Origin": "https://soundcloud.com",
            "Referer": "https://soundcloud.com/",
        },
    )
    with urllib.request.urlopen(req, timeout=45) as resp:
        return json.loads(resp.read().decode())


def scrape_client_id() -> str:
    html = urllib.request.urlopen(
        urllib.request.Request("https://soundcloud.com", headers={"User-Agent": UA}),
        timeout=30,
    ).read().decode("utf-8", "ignore")
    scripts = re.findall(r'src="(https://[^"]+/assets/[^"]+\.js)"', html)
    for src in scripts[-8:]:
        try:
            body = urllib.request.urlopen(
                urllib.request.Request(src, headers={"User-Agent": UA}),
                timeout=30,
            ).read().decode("utf-8", "ignore")
        except Exception:
            continue
        m = re.search(r'client_id\s*[:=]\s*"([A-Za-z0-9]{32})"', body)
        if m:
            return m.group(1)
        m = re.search(r'"client_id"\s*:\s*"([A-Za-z0-9]{32})"', body)
        if m:
            return m.group(1)
    raise RuntimeError("client_id not found")


def api(path: str, client_id: str, **params: str) -> Any:
    q = {"client_id": client_id, **params}
    url = f"{API}/{path.lstrip('/')}?{urllib.parse.urlencode(q)}"
    return http_get(url)


def api_abs(url: str, client_id: str) -> Any:
    parsed = urllib.parse.urlparse(url)
    q = dict(urllib.parse.parse_qsl(parsed.query))
    q["client_id"] = client_id
    rebuilt = parsed._replace(query=urllib.parse.urlencode(q)).geturl()
    return http_get(rebuilt)


def main() -> int:
    client_id = scrape_client_id()
    ok(f"client_id scraped ({client_id[:6]}...)")

    # Home charts
    charts = api("charts", client_id, kind="trending", genre="soundcloud:genres:all-music", limit="20")
    chart_tracks = [c.get("track") for c in charts.get("collection", []) if c.get("track")]
    if len(chart_tracks) < 5:
        fail(f"charts too small: {len(chart_tracks)}")
    else:
        ok(f"charts trending: {len(chart_tracks)} tracks")

    mixed = api("mixed-selections", client_id)
    shelves = mixed.get("collection") or mixed.get("selections") or []
    if not shelves:
        # mixed-selections shape varies; accept non-empty root keys
        if not mixed:
            fail("mixed-selections empty")
        else:
            ok(f"mixed-selections keys={list(mixed.keys())[:6]}")
    else:
        ok(f"mixed-selections shelves={len(shelves)}")

    # Search artists / playlists / tracks
    search = api("search", client_id, q="techno", limit="20", linked_partitioning="1")
    collection = search.get("collection") or []
    kinds = {}
    for item in collection:
        kinds[item.get("kind", "?")] = kinds.get(item.get("kind", "?"), 0) + 1
    if not collection:
        fail("search empty")
    else:
        ok(f"search kinds={kinds}")

    users = api("search/users", client_id, q="deadmau5", limit="5", linked_partitioning="1")
    user = (users.get("collection") or [None])[0]
    if not user:
        fail("artist search empty")
        return 1
    user_id = str(user["id"])
    user_url = user.get("permalink_url")
    ok(f"artist {user.get('username')} id={user_id}")

    # Artist tracks pagination (infinite feed)
    page1 = api(f"users/{user_id}/tracks", client_id, limit="20", linked_partitioning="1")
    t1 = page1.get("collection") or []
    next1 = page1.get("next_href")
    if len(t1) < 5:
        fail(f"artist tracks page1 too small: {len(t1)}")
    else:
        ok(f"artist tracks page1={len(t1)} next={bool(next1)}")
    total = len(t1)
    pages = 1
    href = next1
    while href and pages < 5 and total < 200:
        page = api_abs(href, client_id)
        batch = page.get("collection") or []
        if not batch:
            fail(f"artist page {pages+1} empty while next_href set")
            break
        total += len(batch)
        href = page.get("next_href")
        pages += 1
    ok(f"artist pagination drained {pages} pages -> {total} tracks")

    # Resolve artist permalink
    resolved_user = api("resolve", client_id, url=user_url)
    if str(resolved_user.get("id")) != user_id:
        fail("resolve artist mismatch")
    else:
        ok("resolve artist permalink")

    # Playlist search + hydrate stubs
    playlists = api("search/playlists", client_id, q="techno mix", limit="10", linked_partitioning="1")
    pl = None
    for candidate in playlists.get("collection") or []:
        track_count = candidate.get("track_count") or 0
        if track_count >= 30:
            pl = candidate
            break
    if pl is None:
        pl = (playlists.get("collection") or [None])[0]
    if not pl:
        fail("playlist search empty")
        return 1
    pl_url = pl["permalink_url"]
    pl_id = pl["id"]
    track_count = pl.get("track_count") or 0
    ok(f"playlist '{pl.get('title')}' tracks~={track_count} art={bool(pl.get('artwork_url'))}")

    resolved_pl = api("resolve", client_id, url=pl_url)
    stubs = resolved_pl.get("tracks") or []
    stub_ids = [str(t.get("id")) for t in stubs if t.get("id")]
    fullish = [t for t in stubs if t.get("title") and t.get("media")]
    if not stub_ids:
        fail("playlist resolve has no track stubs")
    else:
        ok(f"playlist resolve stubs={len(stub_ids)} fullish={len(fullish)}")

    # Hydrate like the app: /tracks?ids= chunks of 50, cap 5000
    cap = min(len(stub_ids), 5000)
    hydrated = 0
    missing_title = 0
    missing_art = 0
    for i in range(0, cap, 50):
        chunk = stub_ids[i : i + 50]
        payload = api("tracks", client_id, ids=",".join(chunk))
        # API may return array or {collection:[]}
        items = payload if isinstance(payload, list) else payload.get("collection") or []
        hydrated += len(items)
        for t in items:
            if not t.get("title"):
                missing_title += 1
            if not (t.get("artwork_url") or (t.get("user") or {}).get("avatar_url")):
                missing_art += 1
    if hydrated < min(20, cap):
        fail(f"hydrate too few: {hydrated}/{cap}")
    else:
        ok(f"hydrate {hydrated}/{cap} missing_title={missing_title} missing_art={missing_art}")

    # Stream a chart track
    stream_ok = 0
    stream_fail = 0
    for t in chart_tracks[:5]:
        tid = t.get("id")
        media = ((t.get("media") or {}).get("transcodings") or [])
        progressive = next(
            (x for x in media if (x.get("format") or {}).get("protocol") == "progressive"),
            None,
        )
        hls = next(
            (x for x in media if (x.get("format") or {}).get("protocol") == "hls"),
            None,
        )
        pick = progressive or hls
        if not pick:
            stream_fail += 1
            continue
        try:
            auth = t.get("track_authorization")
            stream_meta = api_abs(
                pick["url"] + (("&" if "?" in pick["url"] else "?") + f"track_authorization={auth}" if auth else ""),
                client_id,
            )
            if stream_meta.get("url"):
                stream_ok += 1
            else:
                stream_fail += 1
        except Exception as e:
            stream_fail += 1
            print(f"  stream err {tid}: {e}")
    if stream_ok == 0:
        fail(f"no streams resolved (fail={stream_fail})")
    else:
        ok(f"streams ok={stream_ok} fail={stream_fail}")

    # Album-like sets
    albums = api("search/albums", client_id, q="ep", limit="5", linked_partitioning="1")
    alb = (albums.get("collection") or [None])[0]
    if alb:
        ok(f"album search '{alb.get('title')}' set_type={alb.get('set_type')}")
        alb_resolved = api("resolve", client_id, url=alb["permalink_url"])
        alb_tracks = alb_resolved.get("tracks") or []
        ok(f"album resolve stubs={len(alb_tracks)} art={bool(alb_resolved.get('artwork_url'))}")
    else:
        fail("album search empty")

    print()
    print(f"Passed {len(OKS)}, failed {len(FAILS)}")
    for f in FAILS:
        print(f"  - {f}")
    return 1 if FAILS else 0


if __name__ == "__main__":
    sys.exit(main())
