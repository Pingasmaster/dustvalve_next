#!/usr/bin/env bash
# Start/stop the one-shot NetBird APK HTTP server used by build.sh.
#
# Usage:
#   scripts/apk_http_serve.sh stop
#   scripts/apk_http_serve.sh start <apk-file> [apk-file...]
#
# Serves http://<netbird-fqdn>:8765/<basename> for each APK until every listed
# APK has been downloaded once, 10 minutes, or the next build.sh invocation
# (stop) - whichever happens first. Binds on the NetBird IP; advertises the
# stable DNS name.
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
PIDFILE="$ROOT_DIR/.apk-http-serve.pid"
PORT=8765
TIMEOUT_SEC=600
SERVE_PY="$SCRIPT_DIR/serve_apk_once.py"

detect_netbird_ip() {
    local ip=""
    if command -v netbird >/dev/null 2>&1; then
        ip="$(netbird status 2>/dev/null | sed -n 's/^NetBird IP: \([0-9][0-9.]*\).*/\1/p' | head -1 || true)"
    fi
    if [[ -z "$ip" ]] && command -v ip >/dev/null 2>&1; then
        ip="$(ip -4 -o addr show wt0 2>/dev/null | awk '{print $4}' | cut -d/ -f1 | head -1 || true)"
    fi
    printf '%s' "$ip"
}

detect_netbird_fqdn() {
    local fqdn=""
    if command -v netbird >/dev/null 2>&1; then
        fqdn="$(netbird status 2>/dev/null | sed -n 's/^FQDN:[[:space:]]*//p' | head -1 || true)"
        fqdn="${fqdn%%[[:space:]]*}"
    fi
    printf '%s' "$fqdn"
}

stop_serve() {
    if [[ -f "$PIDFILE" ]]; then
        local pid
        pid="$(tr -d '[:space:]' <"$PIDFILE" 2>/dev/null || true)"
        if [[ -n "${pid:-}" ]] && [[ "$pid" =~ ^[0-9]+$ ]] && kill -0 "$pid" 2>/dev/null; then
            kill "$pid" 2>/dev/null || true
            # Wait briefly for clean exit; escalate if needed.
            for _ in 1 2 3 4 5; do
                kill -0 "$pid" 2>/dev/null || break
                sleep 0.2
            done
            if kill -0 "$pid" 2>/dev/null; then
                kill -9 "$pid" 2>/dev/null || true
            fi
        fi
        rm -f "$PIDFILE"
    fi
    # Free the dedicated port if a stray listener remains (other repo / leftover).
    if command -v fuser >/dev/null 2>&1; then
        fuser -k "${PORT}/tcp" >/dev/null 2>&1 || true
    fi
}

start_serve() {
    if [[ "$#" -lt 1 ]]; then
        echo "Usage: $0 start <apk-file> [apk-file...]" >&2
        exit 2
    fi

    local -a apk_args=()
    local -a url_names=()
    local apk
    for apk in "$@"; do
        if [[ "$apk" != /* ]]; then
            apk="$ROOT_DIR/$apk"
        fi
        if [[ ! -f "$apk" ]]; then
            echo "APK HTTP serve: skipped (APK missing: $apk)" >&2
            return 0
        fi
        apk_args+=(--apk "$apk")
        url_names+=("$(basename "$apk")")
    done

    local bind_host url_host
    bind_host="$(detect_netbird_ip)"
    if [[ -z "$bind_host" ]]; then
        echo "APK HTTP serve: skipped (no NetBird IP on this host)." >&2
        return 0
    fi
    url_host="$(detect_netbird_fqdn)"
    if [[ -z "$url_host" ]]; then
        url_host="$bind_host"
    fi

    stop_serve

    # Detach so the server outlives build.sh. stdout/stderr go to a log.
    local log="$ROOT_DIR/.apk-http-serve.log"
    nohup python3 "$SERVE_PY" \
        --host "$bind_host" \
        --url-host "$url_host" \
        --port "$PORT" \
        "${apk_args[@]}" \
        --pidfile "$PIDFILE" \
        --timeout "$TIMEOUT_SEC" \
        >"$log" 2>&1 &
    disown || true

    # Wait until pidfile exists (python may need a beat to bind + write).
    # Also accept a successful banner already in the log - pidfile write can
    # race our poll under load.
    local i
    for i in $(seq 1 50); do
        if [[ -f "$PIDFILE" ]]; then
            break
        fi
        if grep -q "APK HTTP serve: http://" "$log" 2>/dev/null; then
            break
        fi
        sleep 0.1
    done

    if [[ -f "$PIDFILE" ]] || grep -q "APK HTTP serve: http://" "$log" 2>/dev/null; then
        local name
        for name in "${url_names[@]}"; do
            echo "APK HTTP serve: http://${url_host}:${PORT}/${name}"
        done
        echo "APK HTTP serve: stops after all downloads, ${TIMEOUT_SEC}s, or next ./build.sh."
    else
        echo "APK HTTP serve: failed to start (see $log)" >&2
        if [[ -f "$log" ]]; then
            tail -n 20 "$log" >&2 || true
        fi
        return 1
    fi
}

cmd="${1:-}"
case "$cmd" in
    stop) stop_serve ;;
    start)
        shift
        start_serve "$@"
        ;;
    *)
        echo "Usage: $0 {stop|start <apk-file> [apk-file...]}" >&2
        exit 2
        ;;
esac
