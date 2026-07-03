#!/usr/bin/env bash
# nightly-deps-agent.sh — nightly runner for the dustvalve_next Android project.
#
# Spawns a `claude` TUI session with the golden-honesty-rule prompt,
# captures output, writes a run report, exits with a status cron records.
#
# Mirrors the spawn pattern of headroom-rust/scripts/headroom-deps-agent.sh
# and efrei.app/scripts/prod-readiness-watcher.sh: tmux preferred (so a
# human can attach to watch), FIFO + script(1) fallback when tmux is
# missing.
#
# Guardrails:
#   - single-instance (flock on agent.lock)
#   - if the working tree is dirty, auto-commit it as a `wip:` snapshot
#     before spawning the agent (the agent's own commit will land on top,
#     and the push will carry both) — so the agent always runs against a
#     clean tree, but the user's uncommitted work is never lost
#   - refuses to start if the repo is in a merge state with unresolved
#     conflicts (can't auto-commit those safely)
#   - 90-minute hard cap per run
#   - DRY_RUN=1 mode writes the would-be prompt to a session file and
#     spawns nothing
#   - all env-vars overridable
set -uo pipefail

# ── config (env-overridable) ────────────────────────────────────────────
REPO="${NIGHTLY_DEPS_REPO:-/home/user/dustvalve_next}"
BRANCH="${NIGHTLY_DEPS_BRANCH:-master}"
STATE_DIR="${NIGHTLY_DEPS_STATE:-$HOME/.local/share/dustvalve-next/nightly-deps-agent}"
REPORT_DIR="$STATE_DIR/reports"
LOG_DIR="$STATE_DIR/logs"
PROMPT_FILE="$REPO/scripts/nightly-deps-agent-prompt.md"
LOCK="$STATE_DIR/agent.lock"
PIDFILE="$STATE_DIR/agent.pid"
DONE_FILE="$STATE_DIR/agent.done"

CLAUDE_BIN="${CLAUDE_BIN:-/home/user/.local/bin/claude}"
MODEL="${NIGHTLY_DEPS_AGENT_MODEL:-opus}"
DURATION_CAP_SECS="${NIGHTLY_DEPS_AGENT_MAX_SECS:-5400}"
TUI_WARMUP="${NIGHTLY_DEPS_AGENT_TUI_WARMUP:-15}"
DONE_POLL="${NIGHTLY_DEPS_AGENT_DONE_POLL:-30}"

DRY_RUN="${NIGHTLY_DEPS_DRY_RUN:-0}"

export PATH="/home/user/.local/bin:/home/user/.cargo/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"

mkdir -p "$STATE_DIR" "$REPORT_DIR" "$LOG_DIR"

log() {
  printf '%s  %s\n' "$(date '+%Y-%m-%dT%H:%M:%S')" "$*" \
    | tee -a "$STATE_DIR/agent.log" "$STATE_DIR/cron.log" >&2
}

validate_model() {
  case "${1-}" in
    *[!A-Za-z0-9._:-]*)
      log "refusing to launch: bad MODEL '$1' (allowed: [A-Za-z0-9._:-])"
      return 1
      ;;
  esac
  [ -n "${1-}" ] || { log "refusing to launch: empty MODEL"; return 1; }
  return 0
}

exec 9>"$LOCK"
if ! flock -n 9; then
  log "another agent run is already in progress; aborting."
  exit 0
fi

[ -f "$PROMPT_FILE" ] || { log "missing prompt: $PROMPT_FILE"; exit 2; }

if [ -n "$(git -C "$REPO" status --porcelain 2>/dev/null)" ]; then
  log "repo $REPO has uncommitted changes — auto-committing as 'wip:' snapshot before agent run"
  if [ -n "$(git -C "$REPO" ls-files --unmerged 2>/dev/null)" ]; then
    log "repo $REPO is in a merge state with unmerged paths; refusing to run (resolve conflicts first)"
    exit 1
  fi
  if ! git -C "$REPO" add -A 2>>"$STATE_DIR/agent.log"; then
    log "git add -A failed; refusing to run agent"
    exit 1
  fi
  ts_snap=$(date '+%Y-%m-%dT%H:%M:%S')
  if ! git -C "$REPO" commit -m "wip: pre-nightly-deps-agent state $ts_snap

Auto-committed by nightly-deps-agent.sh because the working tree
was dirty when the agent started. The agent will make its own
commit on top of this one for dep bumps and fixes; both commits
land in the same push.

If this commit contains unintended content (e.g. accidental
saves), amend or drop it before the agent pushes." 2>>"$STATE_DIR/agent.log"; then
    log "wip commit FAILED; refusing to run agent"
    exit 1
  fi
  log "wip commit landed: $(git -C "$REPO" rev-parse --short HEAD); proceeding with agent run"
fi

validate_model "$MODEL" || exit 2

ts=$(date '+%Y%m%d-%H%M%S')
logfile="$LOG_DIR/$ts.log"
session_prompt="$STATE_DIR/prompt-$ts.md"
start=$(date +%s)
rm -f "$DONE_FILE"

cp "$PROMPT_FILE" "$session_prompt"

if [ "$DRY_RUN" = "1" ]; then
  log "DRY_RUN: prompt -> $PROMPT_FILE (copied to $session_prompt) ; model=$MODEL ; nothing spawned."
  exit 0
fi

directive="Read the canonical prompt at $PROMPT_FILE (also copied to $session_prompt on this host) and execute it fully and autonomously, without ever asking for confirmation or entering plan mode. Repo: $REPO on branch $BRANCH. Touch $DONE_FILE when fully done so the runner can exit."

# Strip the headroom-proxy env vars inherited from the parent shell
# (the `mini` / `glm` bashrc aliases route claude through the local
# proxy with a custom settings file; we MUST NOT use that path). The
# runner calls the real `claude` by absolute path so this array is
# load-bearing — without it, the agent would silently compress every
# request through the local proxy.
proxy_strip_env=(
  -u ANTHROPIC_API_KEY
  -u ANTHROPIC_AUTH_TOKEN
  -u ANTHROPIC_BASE_URL
  -u OPENAI_BASE_URL
  -u OPENAI_API_BASE
  -u CLAUDE_CODE_SAFE_MODE
)

if command -v tmux >/dev/null 2>&1; then
  sess="nightly-deps-$ts"
  env "${proxy_strip_env[@]}" \
    setsid tmux new-session -d -s "$sess" -x 220 -y 50 \
      "cd '$REPO' && exec '$CLAUDE_BIN' --permission-mode bypassPermissions --model '$MODEL'"
  tmux pipe-pane -t "$sess" "cat >> '$logfile'" 2>/dev/null || true
  tmux list-panes -t "$sess" -F '#{pane_pid}' 2>/dev/null | head -n1 > "$PIDFILE" 2>/dev/null || echo $$ > "$PIDFILE"
  sleep "$TUI_WARMUP"
  tmux send-keys -t "$sess" "$directive"; sleep 1; tmux send-keys -t "$sess" Enter
  while [ ! -f "$DONE_FILE" ] && tmux has-session -t "$sess" 2>/dev/null; do
    sleep "$DONE_POLL"
    now=$(date +%s)
    if [ $(( now - start )) -ge "$DURATION_CAP_SECS" ]; then
      log "agent $ts hit max duration ($DURATION_CAP_SECS s); killing tmux session"
      tmux kill-session -t "$sess" 2>/dev/null || true
      break
    fi
  done
  tmux kill-session -t "$sess" 2>/dev/null || true
else
  fifo="$STATE_DIR/agent-$ts.fifo"
  rm -f "$fifo"; mkfifo "$fifo"
  inner=$(printf "cd %s && exec script -qfec '%s --permission-mode bypassPermissions --model %s' %s < %s" \
    "$REPO" "$CLAUDE_BIN" "$MODEL" "$logfile" "$fifo")
  env "${proxy_strip_env[@]}" setsid bash -c "$inner" &
  drv=$!
  echo "$drv" > "$PIDFILE"
  exec {wfd}>"$fifo"
  sleep "$TUI_WARMUP"
  printf '%s\r' "$directive" >&$wfd
  while [ ! -f "$DONE_FILE" ] && kill -0 "$drv" 2>/dev/null; do
    sleep "$DONE_POLL"
    now=$(date +%s)
    if [ $(( now - start )) -ge "$DURATION_CAP_SECS" ]; then
      log "agent $ts hit max duration ($DURATION_CAP_SECS s); closing FIFO"
      break
    fi
  done
  printf '/exit\r' >&$wfd 2>/dev/null || true
  sleep 2
  exec {wfd}>&- 2>/dev/null || true
  kill "$drv" 2>/dev/null || true
  rm -f "$fifo"
fi

short_sha=$(git -C "$REPO" rev-parse --short HEAD 2>/dev/null || echo "unknown")

if [ -f "$DONE_FILE" ]; then
  log "agent $ts completed (sentinel present). head=$short_sha ; logfile=$logfile"
  echo "OK $short_sha"
else
  log "agent $ts ended WITHOUT completion sentinel (timeout/crash). logfile=$logfile"
  echo "FAIL timeout-or-crash"
fi

rm -f "$PIDFILE"