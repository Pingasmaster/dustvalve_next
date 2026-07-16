#!/usr/bin/env python3
"""Fetch and parse maven-metadata.xml for a list of artifacts."""

import sys
import urllib.request
import xml.etree.ElementTree as ET
from pathlib import Path
from concurrent.futures import ThreadPoolExecutor, as_completed

MAVEN_CENTRAL = "https://repo1.maven.org/maven2"
GOOGLE_MAVEN = "https://dl.google.com/android/maven2"

# (group, artifact, repo) - repo is "central" or "google"
ARTIFACTS = [
    # Core plugins (Google Maven)
    ("com.android.tools.build", "gradle", "google"),
    ("com.android.application", "com.android.application.gradle.plugin", "google"),
    ("com.android.library", "com.android.library.gradle.plugin", "google"),
    # Kotlin / KSP (Maven Central, plugin marker for KSP)
    ("org.jetbrains.kotlin", "kotlin-gradle-plugin", "central"),
    ("org.jetbrains.kotlin.android", "org.jetbrains.kotlin.android.gradle.plugin", "central"),
    ("org.jetbrains.kotlin.plugin.compose", "org.jetbrains.kotlin.plugin.compose.gradle.plugin", "central"),
    ("org.jetbrains.kotlin.plugin.serialization", "org.jetbrains.kotlin.plugin.serialization.gradle.plugin", "central"),
    ("com.google.devtools.ksp", "com.google.devtools.ksp.gradle.plugin", "central"),
    # Hilt
    ("com.google.dagger", "hilt-android", "central"),
    ("com.google.dagger", "hilt-android-gradle-plugin", "central"),
    ("com.google.dagger", "hilt-android-compiler", "central"),
    ("androidx.hilt", "hilt-android", "google"),
    ("androidx.hilt", "hilt-android-gradle-plugin", "google"),
    ("androidx.hilt", "hilt-compiler", "google"),
    ("androidx.hilt", "hilt-work", "google"),
    ("androidx.hilt", "hilt-lifecycle-viewmodel-compose", "google"),
    # Compose
    ("androidx.compose", "compose-bom", "google"),
    ("androidx.compose.runtime", "runtime", "google"),
    ("androidx.compose.ui", "ui", "google"),
    ("androidx.compose.material3", "material3", "google"),
    ("androidx.compose.foundation", "foundation", "google"),
    ("androidx.graphics", "graphics-shapes", "google"),
    # AndroidX core
    ("androidx.activity", "activity-compose", "google"),
    ("androidx.lifecycle", "lifecycle-runtime-ktx", "google"),
    ("androidx.lifecycle", "lifecycle-runtime-compose", "google"),
    ("androidx.lifecycle", "lifecycle-viewmodel-compose", "google"),
    ("androidx.core", "core-ktx", "google"),
    # WorkManager / Room / DataStore
    ("androidx.work", "work-runtime-ktx", "google"),
    ("androidx.room", "room-runtime", "google"),
    ("androidx.datastore", "datastore-preferences", "google"),
    ("androidx.documentfile", "documentfile", "google"),
    ("androidx.palette", "palette-ktx", "google"),
    # OkHttp / Jsoup
    ("com.squareup.okhttp3", "okhttp", "central"),
    ("com.squareup.okhttp3", "okhttp-brotli", "central"),
    ("com.squareup.okhttp3", "mockwebserver", "central"),
    ("org.jsoup", "jsoup", "central"),
    # Coil
    ("io.coil-kt.coil3", "coil-compose", "central"),
    # MaterialKolor
    ("com.materialkolor", "material-kolor", "central"),
    # Media3
    ("androidx.media3", "media3-exoplayer", "google"),
    # Kotlinx
    ("org.jetbrains.kotlinx", "kotlinx-serialization-json", "central"),
    ("org.jetbrains.kotlinx", "kotlinx-coroutines-android", "central"),
    ("org.jetbrains.kotlinx", "kotlinx-collections-immutable", "central"),
    # Reorderable
    ("sh.calvin.reorderable", "reorderable", "central"),
    # Test
    ("junit", "junit", "central"),
    ("com.google.truth", "truth", "central"),
    ("io.mockk", "mockk", "central"),
    ("org.robolectric", "robolectric", "central"),
    ("androidx.test", "core", "google"),
    ("androidx.test.ext", "junit", "google"),
    ("app.cash.turbine", "turbine", "central"),
    # Static analysis
    ("org.jlleitschuh.gradle.ktlint", "org.jlleitschuh.gradle.ktlint.gradle.plugin", "central"),
    ("com.pinterest.ktlint", "ktlint-cli", "central"),
    ("dev.detekt", "detekt-gradle-plugin", "central"),
    ("io.nlopez.compose.rules", "detekt", "central"),
    ("com.slack.lint", "slack-lint-checks", "central"),
    ("com.slack.lint.compose", "compose-lint-checks", "central"),
    ("com.autonomousapps", "dependency-analysis-gradle-plugin", "central"),
]


def fetch_metadata(group: str, artifact: str, repo: str) -> tuple[str, str, str, list[str], str | None]:
    base = GOOGLE_MAVEN if repo == "google" else MAVEN_CENTRAL
    group_path = group.replace(".", "/")
    url = f"{base}/{group_path}/{artifact}/maven-metadata.xml"
    try:
        with urllib.request.urlopen(url, timeout=20) as resp:
            data = resp.read()
    except Exception as e:
        return (group, artifact, repo, [], f"FETCH_ERROR: {e}")
    try:
        root = ET.fromstring(data)
    except ET.ParseError as e:
        return (group, artifact, repo, [], f"PARSE_ERROR: {e}")
    versions_el = root.find("versioning")
    if versions_el is None:
        return (group, artifact, repo, [], "NO_VERSIONING")
    versions_els = versions_el.findall("version")
    versions = [v.text for v in versions_els if v.text]
    latest_el = versions_el.find("latest")
    latest = latest_el.text if latest_el is not None else None
    return (group, artifact, repo, versions, latest)


def main():
    out_dir = Path("/tmp/dep_metadata")
    out_dir.mkdir(exist_ok=True)
    print(f"Fetching metadata for {len(ARTIFACTS)} artifacts...")
    with ThreadPoolExecutor(max_workers=12) as ex:
        futures = {ex.submit(fetch_metadata, g, a, r): (g, a) for g, a, r in ARTIFACTS}
        results = {}
        for fut in as_completed(futures):
            res = fut.result()
            g, a, repo, versions, latest = res
            key = f"{g}:{a}"
            results[key] = (repo, versions, latest)
            status = f"latest={latest}" if latest else f"{len(versions)} versions"
            err = ""
            if not versions and latest is None:
                err = "  <-- ERROR"
            print(f"  {key:80s} [{repo:6s}] {len(versions):4d} versions, {status}{err}")
    # Save to disk for inspection
    import json
    (out_dir / "metadata.json").write_text(json.dumps(results, indent=2))
    print(f"Saved to {out_dir / 'metadata.json'}")


if __name__ == "__main__":
    main()
