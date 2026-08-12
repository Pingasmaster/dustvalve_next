#!/usr/bin/env bash
# Start/stop the one-shot NetBird APK HTTP server used by build.sh.
#
# Usage:
#   scripts/apk_http_serve.sh stop
#   scripts/apk_http_serve.sh start [--optional] <apk-file> [apk-file...]
#
# Serves http://<netbird-fqdn>:<port>/<basename> for each APK until every listed
# APK has been downloaded once, 10 minutes, or the next build.sh invocation
# (stop) - whichever happens first. Prefers port 8765; if that is already taken
# on the NetBird IP, picks the next free port. Binds on the NetBird IP;
# advertises the stable DNS name.
#
# Without --optional, missing APKs or a missing NetBird IP are hard errors
# (exit 1). With --optional (post-build handoff), a missing NetBird IP soft-
# skips so the build still succeeds offline; missing APKs stay hard errors.
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
PIDFILE="$ROOT_DIR/.apk-http-serve.pid"
PREFERRED_PORT=8765
TIMEOUT_SEC=600
SERVE_PY="$SCRIPT_DIR/serve_apk_once.py"
# Set after a successful start to the port python actually bound (may differ
# from PREFERRED_PORT when that one was already taken on the NetBird IP).
PORT="$PREFERRED_PORT"

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

# Kill only listeners on the NetBird IP for the preferred port and a small
# fallback window (serve_apk_once.py may have bound elsewhere when preferred
# was taken). Never touch 127.0.0.1 - other local services (e.g. llama-server)
# reuse the same port numbers on loopback.
kill_netbird_listeners() {
    local bind_host="$1"
    [[ -n "$bind_host" ]] || return 0
    command -v ss >/dev/null 2>&1 || return 0

    local port pids pid
    for port in $(seq "$PREFERRED_PORT" $((PREFERRED_PORT + 31))); do
        pids="$(ss -ltnp 2>/dev/null | awk -v addr="${bind_host}:${port}" '
            index($0, addr) {
                while (match($0, /pid=[0-9]+/)) {
                    print substr($0, RSTART + 4, RLENGTH - 4)
                    $0 = substr($0, RSTART + RLENGTH)
                }
            }
        ' | sort -u)"
        for pid in $pids; do
            if [[ "$pid" =~ ^[0-9]+$ ]] && kill -0 "$pid" 2>/dev/null; then
                kill "$pid" 2>/dev/null || true
                for _ in 1 2 3 4 5; do
                    kill -0 "$pid" 2>/dev/null || break
                    sleep 0.2
                done
                if kill -0 "$pid" 2>/dev/null; then
                    kill -9 "$pid" 2>/dev/null || true
                fi
            fi
        done
    done
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
    # Free a leftover NetBird-bound server from another repo / crashed run.
    # Deliberately not `fuser -k PORT/tcp`: that also kills loopback listeners
    # that share only the port number (llama-server on 127.0.0.1:8765).
    kill_netbird_listeners "$(detect_netbird_ip)"
}

start_serve() {
    local optional=0
    if [[ "${1:-}" == "--optional" ]]; then
        optional=1
        shift
    fi

    if [[ "$#" -lt 1 ]]; then
        echo "Usage: $0 start [--optional] <apk-file> [apk-file...]" >&2
        exit 2
    fi

    local -a apk_args=()
    local -a url_names=()
    local -a missing=()
    local apk
    for apk in "$@"; do
        if [[ "$apk" != /* ]]; then
            apk="$ROOT_DIR/$apk"
        fi
        if [[ ! -f "$apk" ]]; then
            missing+=("$apk")
            continue
        fi
        apk_args+=(--apk "$apk")
        url_names+=("$(basename "$apk")")
    done

    if [[ "${#missing[@]}" -gt 0 ]]; then
        local path
        for path in "${missing[@]}"; do
            echo "ERROR: APK missing: $path" >&2
        done
        echo "ERROR: run ./build.sh --debug (or a full release build) first." >&2
        return 1
    fi

    local bind_host url_host
    bind_host="$(detect_netbird_ip)"
    if [[ -z "$bind_host" ]]; then
        if [[ "$optional" -eq 1 ]]; then
            echo "APK HTTP serve: skipped (no NetBird IP on this host)." >&2
            return 0
        fi
        echo "ERROR: no NetBird IP on this host; cannot publish APKs." >&2
        echo "Connect NetBird (wt0 / netbird status), then re-run." >&2
        return 1
    fi
    url_host="$(detect_netbird_fqdn)"
    if [[ -z "$url_host" ]]; then
        url_host="$bind_host"
    fi

    stop_serve

    # Detach into a new session so the server outlives build.sh / the terminal.
    # PYTHONUNBUFFERED so the log shows requests and stop reasons immediately.
    local log="$ROOT_DIR/.apk-http-serve.log"
    rm -f "$PIDFILE"
    : >"$log"
    # Close flock FD 9 inherited from build.sh so the shared android-apps
    # build.lock is released when build.sh exits (setsid does not drop FDs).
    setsid env PYTHONUNBUFFERED=1 python3 "$SERVE_PY" \
        --host "$bind_host" \
        --url-host "$url_host" \
        --port "$PREFERRED_PORT" \
        "${apk_args[@]}" \
        --pidfile "$PIDFILE" \
        --timeout "$TIMEOUT_SEC" \
        </dev/null >"$log" 2>&1 9<&- &

    # Wait until pidfile exists (written only after a successful bind) or the
    # process has already failed and left an error in the log.
    local i
    for i in $(seq 1 100); do
        if [[ -f "$PIDFILE" ]]; then
            break
        fi
        if grep -q '^ERROR:' "$log" 2>/dev/null; then
            break
        fi
        sleep 0.1
    done

    local serve_pid=""
    if [[ -f "$PIDFILE" ]]; then
        serve_pid="$(tr -d '[:space:]' <"$PIDFILE" 2>/dev/null || true)"
    fi

    # Prefer the port printed by python (may have fallen back).
    local bound_port=""
    bound_port="$(sed -n 's|^APK HTTP serve: http://[^:]*:\([0-9][0-9]*\)/.*|\1|p' "$log" 2>/dev/null | head -1 || true)"
    if [[ -n "$bound_port" ]] && [[ "$bound_port" =~ ^[0-9]+$ ]]; then
        PORT="$bound_port"
    else
        PORT="$PREFERRED_PORT"
    fi

    if [[ -n "$serve_pid" ]] && [[ "$serve_pid" =~ ^[0-9]+$ ]] \
        && kill -0 "$serve_pid" 2>/dev/null \
        && grep -q "APK HTTP serve: http://" "$log" 2>/dev/null; then
        local name
        for name in "${url_names[@]}"; do
            echo "APK HTTP serve: http://${url_host}:${PORT}/${name}"
        done
        if [[ "$PORT" != "$PREFERRED_PORT" ]]; then
            echo "APK HTTP serve: preferred port ${PREFERRED_PORT} was busy; bound ${PORT}."
        fi
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
        echo "Usage: $0 {stop|start [--optional] <apk-file> [apk-file...]}" >&2
        exit 2
        ;;
esac
