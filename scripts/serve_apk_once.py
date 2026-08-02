#!/usr/bin/env python3
"""One-shot HTTP server for one or more release APKs.

Serves GET /<basename> for each --apk path, bound to a specific host and port.
Exits after every listed APK has been downloaded once, after --timeout seconds,
or on SIGTERM/SIGINT (next build.sh invocation).
"""

from __future__ import annotations

import argparse
import os
import signal
import sys
import time
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path

CONTENT_TYPE = "application/vnd.android.package-archive"


class _ApkServer(HTTPServer):
    allow_reuse_address = True

    def __init__(self, server_address, apk_by_path: dict[str, bytes]):
        self.apk_by_path = apk_by_path
        self.downloaded_paths: set[str] = set()
        super().__init__(server_address, _ApkHandler)

    @property
    def all_downloaded(self) -> bool:
        return self.downloaded_paths >= set(self.apk_by_path)


class _ApkHandler(BaseHTTPRequestHandler):
    server: _ApkServer

    def log_message(self, fmt: str, *args) -> None:
        sys.stderr.write("[%s] %s\n" % (self.log_date_time_string(), fmt % args))

    def _path_only(self) -> str:
        return self.path.split("?", 1)[0]

    def _lookup(self) -> tuple[str, bytes] | None:
        path = self._path_only()
        data = self.server.apk_by_path.get(path)
        if data is None:
            return None
        return path, data

    def do_HEAD(self) -> None:  # noqa: N802
        found = self._lookup()
        if found is None:
            self.send_error(404, "Not Found")
            return
        path, data = found
        filename = path.lstrip("/")
        self.send_response(200)
        self.send_header("Content-Type", CONTENT_TYPE)
        self.send_header("Content-Length", str(len(data)))
        self.send_header(
            "Content-Disposition",
            'attachment; filename="%s"' % filename,
        )
        self.end_headers()

    def do_GET(self) -> None:  # noqa: N802
        found = self._lookup()
        if found is None:
            self.send_error(404, "Not Found")
            return
        path, data = found
        filename = path.lstrip("/")
        self.send_response(200)
        self.send_header("Content-Type", CONTENT_TYPE)
        self.send_header("Content-Length", str(len(data)))
        self.send_header(
            "Content-Disposition",
            'attachment; filename="%s"' % filename,
        )
        self.end_headers()
        try:
            self.wfile.write(data)
            self.wfile.flush()
        except (BrokenPipeError, ConnectionResetError):
            # Incomplete transfer: keep serving for another client.
            return
        self.server.downloaded_paths.add(path)


def _write_pid(pid_path: Path) -> None:
    pid_path.write_text(str(os.getpid()) + "\n", encoding="ascii")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--host", required=True, help="Bind address (NetBird IP)")
    parser.add_argument(
        "--url-host",
        default="",
        help="Hostname shown in the printed URL (NetBird FQDN). Defaults to --host.",
    )
    parser.add_argument("--port", type=int, default=8765)
    parser.add_argument(
        "--apk",
        required=True,
        action="append",
        type=Path,
        help="APK file to serve (repeat for multiple; URL is /<basename>)",
    )
    parser.add_argument("--pidfile", required=True, type=Path)
    parser.add_argument("--timeout", type=int, default=600, help="Seconds until exit")
    args = parser.parse_args()
    url_host = args.url_host.strip() or args.host

    apk_by_path: dict[str, bytes] = {}
    for raw in args.apk:
        apk_path = raw.resolve()
        if not apk_path.is_file():
            print("ERROR: APK not found: %s" % apk_path, file=sys.stderr)
            return 1
        url_path = "/" + apk_path.name
        if url_path in apk_by_path:
            print(
                "ERROR: duplicate APK basename (URL collision): %s" % apk_path.name,
                file=sys.stderr,
            )
            return 1
        apk_by_path[url_path] = apk_path.read_bytes()

    pid_path = args.pidfile.resolve()
    _write_pid(pid_path)

    def _cleanup(*_args) -> None:
        try:
            if pid_path.is_file() and pid_path.read_text(encoding="ascii").strip() == str(
                os.getpid()
            ):
                pid_path.unlink(missing_ok=True)
        except OSError:
            pass

    def _on_signal(signum, _frame) -> None:
        print("APK HTTP serve: received signal %s, stopping." % signum, file=sys.stderr)
        _cleanup()
        sys.exit(0)

    signal.signal(signal.SIGTERM, _on_signal)
    signal.signal(signal.SIGINT, _on_signal)

    try:
        server = _ApkServer((args.host, args.port), apk_by_path)
    except OSError as exc:
        print("ERROR: could not bind %s:%s: %s" % (args.host, args.port, exc), file=sys.stderr)
        _cleanup()
        return 1

    for url_path, data in apk_by_path.items():
        url = "http://%s:%s%s" % (url_host, args.port, url_path)
        print("APK HTTP serve: %s (%d bytes)" % (url, len(data)))
    print(
        "APK HTTP serve: stops after all APKs downloaded once, %ds timeout, or next build.sh."
        % args.timeout
    )
    sys.stdout.flush()

    server.timeout = 1.0
    deadline = time.monotonic() + args.timeout
    try:
        while time.monotonic() < deadline and not server.all_downloaded:
            server.handle_request()
    finally:
        server.server_close()
        _cleanup()

    if server.all_downloaded:
        print("APK HTTP serve: all downloads complete, stopped.")
    else:
        print("APK HTTP serve: timeout reached, stopped.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
