#!/usr/bin/env python3
"""Fail the build when gradle/libs.versions.toml is not on the newest release.

Every version key in [versions] that is referenced by a [libraries] or
[plugins] entry is resolved to its Maven coordinates, and the newest version
published to Google Maven / Maven Central / the Gradle Plugin Portal is
compared against the pinned one. Pre-releases count: alphas, betas, RCs and
EAP builds are all candidates, because this project deliberately tracks the
bleeding edge.

Usage:
    scripts/check_latest_deps.py            # exit 1 if anything is stale
    scripts/check_latest_deps.py --json     # machine-readable report
    scripts/check_latest_deps.py --quiet    # only print problems

Holding a version back:
    Add a trailing "# hold: <reason>" comment on the [versions] line. The key
    is then skipped, and the reason is echoed on every run so the hold stays
    visible instead of rotting silently.

        slf4jApi = "2.0.17" # hold: Gradle 9.6.x TAPI pins slf4j-api 2.0.17.

Versions with no [libraries] / [plugins] entry:
    Some versions are consumed straight from the build scripts (for instance
    the ktlint CLI that the ktlint Gradle extension downloads). Point those at
    their artifact with a trailing "# coord: <group:artifact>" comment so they
    are checked too:

        ktlint-engine = "1.8.0" # coord: com.pinterest.ktlint:ktlint-cli
"""

from __future__ import annotations

import argparse
import json
import re
import sys
import tomllib
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

REPOS = (
    ("google", "https://dl.google.com/dl/android/maven2"),
    ("central", "https://repo1.maven.org/maven2"),
    ("plugins", "https://plugins.gradle.org/m2"),
)

TIMEOUT = 30
RETRIES = 3

# Ranking for the qualifier that follows the numeric core of a version.
# Anything unrecognised sorts below alpha so a stray tag can never win.
QUALIFIER_RANK = {
    "snapshot": -3,
    "dev": -2,
    "eap": -1,
    "alpha": 0,
    "milestone": 1,
    "m": 1,
    "beta": 2,
    "rc": 3,
    "": 4,
}

VERSION_RE = re.compile(r"<version>([^<]+)</version>")
CORE_RE = re.compile(r"^(\d+(?:\.\d+)*)(.*)$")
QUALIFIER_RE = re.compile(r"^[-_.]?([A-Za-z]+)[-_.]?(\d*)")
HOLD_RE = re.compile(
    r"""^\s*(?P<key>[A-Za-z0-9_.-]+)\s*=\s*['"][^'"]*['"]\s*#\s*hold:\s*(?P<reason>.+?)\s*$"""
)
COORD_RE = re.compile(
    r"""^\s*(?P<key>[A-Za-z0-9_.-]+)\s*=\s*['"][^'"]*['"]\s*#\s*coord:\s*(?P<coord>[\w.-]+:[\w.-]+)\s*$"""
)


def parse_version(raw):
    """Return a sortable key for a Maven version string."""
    core_match = CORE_RE.match(raw.strip())
    if not core_match:
        return ((0,), -99, 0, raw)
    core = tuple(int(p) for p in core_match.group(1).split("."))
    rest = core_match.group(2)
    if not rest:
        return (core, QUALIFIER_RANK[""], 0, raw)
    qualifier_match = QUALIFIER_RE.match(rest)
    if not qualifier_match:
        return (core, -99, 0, raw)
    word = qualifier_match.group(1).lower()
    number = int(qualifier_match.group(2)) if qualifier_match.group(2) else 0
    return (core, QUALIFIER_RANK.get(word, -99), number, raw)


def compare_key(raw):
    core, rank, number, text = parse_version(raw)
    # Pad the numeric core so 4.17 and 4.16.1 compare on equal footing.
    padded = core + (0,) * (6 - len(core)) if len(core) < 6 else core[:6]
    return (padded, rank, number, text)


def fetch(url):
    last = None
    for _ in range(RETRIES):
        try:
            request = urllib.request.Request(
                url, headers={"User-Agent": "check-latest-deps/1"}
            )
            with urllib.request.urlopen(request, timeout=TIMEOUT) as response:
                return response.read().decode("utf-8", "replace")
        except urllib.error.HTTPError as exc:
            if exc.code == 404:
                return None
            last = exc
        except Exception as exc:  # noqa: BLE001 - network flake, retry
            last = exc
    if last is not None:
        raise RuntimeError(str(last))
    return None


def coordinate_versions(coordinate):
    """Union the published versions of one coordinate across all repositories.

    The union matters: com.google.devtools.ksp is mirrored on Google Maven at a
    long-abandoned 1.5.x line while the live releases only land on Central.
    """
    group, artifact = coordinate.split(":", 1)
    path = "%s/%s/maven-metadata.xml" % (group.replace(".", "/"), artifact)
    versions = set()
    reachable = False
    errors = []
    for _name, base in REPOS:
        try:
            body = fetch("%s/%s" % (base, path))
        except RuntimeError as exc:
            errors.append(str(exc))
            continue
        reachable = True
        if body:
            versions.update(VERSION_RE.findall(body))
    if not reachable:
        raise RuntimeError(
            "no repository reachable for %s (%s)" % (coordinate, "; ".join(errors))
        )
    return versions


def library_coordinate(entry):
    if "module" in entry:
        return entry["module"]
    if "group" in entry and "name" in entry:
        return "%s:%s" % (entry["group"], entry["name"])
    return None


def version_ref(entry):
    version = entry.get("version")
    if isinstance(version, dict):
        return version.get("ref")
    return None


def collect(catalog_path):
    """Map every referenced version key to the coordinates that use it."""
    raw = catalog_path.read_text(encoding="utf-8")
    data = tomllib.loads(raw)

    holds = {}
    groups = {}
    in_versions = False
    for line in raw.splitlines():
        stripped = line.strip()
        if stripped.startswith("["):
            in_versions = stripped == "[versions]"
            continue
        if not in_versions:
            continue
        match = HOLD_RE.match(line)
        if match:
            holds[match.group("key")] = match.group("reason")
            continue
        match = COORD_RE.match(line)
        if match:
            groups.setdefault(match.group("key"), set()).add(match.group("coord"))

    for entry in data.get("libraries", {}).values():
        if not isinstance(entry, dict):
            continue
        ref = version_ref(entry)
        coordinate = library_coordinate(entry)
        if ref and coordinate:
            groups.setdefault(ref, set()).add(coordinate)

    for entry in data.get("plugins", {}).values():
        if not isinstance(entry, dict):
            continue
        ref = version_ref(entry)
        plugin_id = entry.get("id")
        if ref and plugin_id:
            # Gradle plugin marker artifact.
            groups.setdefault(ref, set()).add(
                "%s:%s.gradle.plugin" % (plugin_id, plugin_id)
            )

    return data.get("versions", {}), groups, holds


def newest_for_group(coordinates):
    """Newest version published for *every* coordinate sharing a version key."""
    common = None
    for coordinate in sorted(coordinates):
        versions = coordinate_versions(coordinate)
        common = versions if common is None else (common & versions)
    if not common:
        return None
    return max(common, key=compare_key)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--catalog", default="gradle/libs.versions.toml")
    parser.add_argument("--json", action="store_true", help="emit a JSON report")
    parser.add_argument("--quiet", action="store_true", help="only print problems")
    args = parser.parse_args()

    root = Path(__file__).resolve().parent.parent
    catalog_path = root / args.catalog
    if not catalog_path.is_file():
        print("ERROR: no version catalog at %s" % catalog_path, file=sys.stderr)
        return 2

    versions, groups, holds = collect(catalog_path)

    checkable = sorted(key for key in groups if key in versions and key not in holds)
    skipped = sorted(key for key in versions if key not in groups and key not in holds)

    def resolve(key):
        try:
            return key, newest_for_group(groups[key]), None
        except RuntimeError as exc:
            return key, None, str(exc)

    with ThreadPoolExecutor(max_workers=8) as pool:
        results = list(pool.map(resolve, checkable))

    stale = []
    unresolved = []
    for key, newest, error in results:
        current = versions[key]
        if error:
            unresolved.append({"key": key, "error": error})
            continue
        if newest is None:
            unresolved.append(
                {
                    "key": key,
                    "error": "no version common to %s"
                    % ", ".join(sorted(groups[key])),
                }
            )
            continue
        if compare_key(newest) > compare_key(current):
            stale.append(
                {
                    "key": key,
                    "current": current,
                    "latest": newest,
                    "coordinates": sorted(groups[key]),
                }
            )

    if args.json:
        print(
            json.dumps(
                {
                    "checked": len(checkable),
                    "stale": stale,
                    "unresolved": unresolved,
                    "holds": holds,
                    "unreferenced": skipped,
                },
                indent=2,
                sort_keys=True,
            )
        )
        return 1 if (stale or unresolved) else 0

    for key, reason in sorted(holds.items()):
        print("HOLD  %-28s %-18s %s" % (key, versions.get(key, "?"), reason))

    if unresolved:
        print()
        for item in unresolved:
            print("ERROR %-28s %s" % (item["key"], item["error"]), file=sys.stderr)

    if stale:
        print()
        print("Out-of-date dependencies in %s:" % args.catalog)
        width = max(len(item["key"]) for item in stale)
        for item in stale:
            print(
                "  %-*s %s -> %s" % (width, item["key"], item["current"], item["latest"])
            )
            for coordinate in item["coordinates"]:
                print("      %s" % coordinate)
        print()
        print(
            "Bump the versions above (or add a '# hold: <reason>' comment), "
            "then re-run."
        )
        print("To build anyway, pass --continue-without-updates to ./build.sh.")

    if stale or unresolved:
        return 1

    if not args.quiet:
        print(
            "Dependency freshness: %d version keys are on the newest published "
            "release (pre-releases included)." % len(checkable)
        )
        if skipped:
            print(
                "  (%d catalog versions are not referenced by any library or "
                "plugin: %s)" % (len(skipped), ", ".join(skipped))
            )
    return 0


if __name__ == "__main__":
    sys.exit(main())
