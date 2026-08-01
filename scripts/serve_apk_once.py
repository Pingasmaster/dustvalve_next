#!/usr/bin/env python3
"""One-shot HTTP server for a release APK.

Serves GET /app-release.apk from a given file path, bound to a specific host
and port. Exits after the first complete download, after --timeout seconds, or
on SIGTERM/SIGINT (next build.sh invocation).
"""

from __future__ import annotations

import argparse
import os
import signal
import sys
import time
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path

URL_PATH = "/app-release.apk"
CONTENT_TYPE = "application/vnd.android.package-archive"


class _ApkServer(HTTPServer):
    allow_reuse_address = True

    def __init__(self, server_address, apk_path: Path):
        self.apk_path = apk_path
        self.apk_bytes = apk_path.read_bytes()
        self.download_completed = False
        super().__init__(server_address, _ApkHandler)


class _ApkHandler(BaseHTTPRequestHandler):
    server: _ApkServer

    def log_message(self, fmt: str, *args) -> None:
        sys.stderr.write("[%s] %s\n" % (self.log_date_time_string(), fmt % args))

    def _path_only(self) -> str:
        return self.path.split("?", 1)[0]

    def do_HEAD(self) -> None:  # noqa: N802
        if self._path_only() != URL_PATH:
            self.send_error(404, "Not Found")
            return
        self.send_response(200)
        self.send_header("Content-Type", CONTENT_TYPE)
        self.send_header("Content-Length", str(len(self.server.apk_bytes)))
        self.send_header(
            "Content-Disposition",
            'attachment; filename="app-release.apk"',
        )
        self.end_headers()

    def do_GET(self) -> None:  # noqa: N802
        if self._path_only() != URL_PATH:
            self.send_error(404, "Not Found")
            return
        data = self.server.apk_bytes
        self.send_response(200)
        self.send_header("Content-Type", CONTENT_TYPE)
        self.send_header("Content-Length", str(len(data)))
        self.send_header(
            "Content-Disposition",
            'attachment; filename="app-release.apk"',
        )
        self.end_headers()
        try:
            self.wfile.write(data)
            self.wfile.flush()
        except (BrokenPipeError, ConnectionResetError):
            # Incomplete transfer: keep serving for another client.
            return
        self.server.download_completed = True


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
    parser.add_argument("--apk", required=True, type=Path, help="APK file to serve")
    parser.add_argument("--pidfile", required=True, type=Path)
    parser.add_argument("--timeout", type=int, default=600, help="Seconds until exit")
    args = parser.parse_args()
    url_host = args.url_host.strip() or args.host

    apk_path = args.apk.resolve()
    if not apk_path.is_file():
        print("ERROR: APK not found: %s" % apk_path, file=sys.stderr)
        return 1

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
        server = _ApkServer((args.host, args.port), apk_path)
    except OSError as exc:
        print("ERROR: could not bind %s:%s: %s" % (args.host, args.port, exc), file=sys.stderr)
        _cleanup()
        return 1

    url = "http://%s:%s%s" % (url_host, args.port, URL_PATH)
    print("APK HTTP serve: %s (%d bytes)" % (url, len(server.apk_bytes)))
    print(
        "APK HTTP serve: stops after first complete download, %ds timeout, or next build.sh."
        % args.timeout
    )
    sys.stdout.flush()

    server.timeout = 1.0
    deadline = time.monotonic() + args.timeout
    try:
        while time.monotonic() < deadline and not server.download_completed:
            server.handle_request()
    finally:
        server.server_close()
        _cleanup()

    if server.download_completed:
        print("APK HTTP serve: first download complete, stopped.")
    else:
        print("APK HTTP serve: timeout reached, stopped.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
