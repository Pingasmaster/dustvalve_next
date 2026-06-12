#!/usr/bin/env bash
#
# Nightly dependency update + build + commit + push for
# /home/user/temp/dustvalve_next.
#
# Invoked by the dustvalve-next-nightly-update.service systemd unit at 03:00.
#
# Phases:
#   1. Snapshot the version catalog.
#   2. Spawn a Claude agent (`mini`) to research latest dep versions
#      AND bump the safe ones in gradle/libs.versions.toml (and direct
#      deps in app/build.gradle.kts if they exist).
#   3. Run ./build.sh (ktlintCheck + detekt + lintRelease + assemble* +
#      testDebugUnitTest, with abortOnError = true). On failure, loop
#      up to MAX_ITER times: spawn a Claude agent that prefers to FIX
#      code over reverting the dep, since detekt autoCorrect can
#      invalidate baselines (per project memory) and the user wants
#      aggressive bumps that survive.
#   4. If the build is green and the working tree has changes, commit
#      and push to origin/master.
#
# On unrecoverable failure the working tree is restored to the
# snapshotted catalog; nothing is committed; nothing is pushed.

set -euo pipefail

# -----------------------------------------------------------------------------
# Paths & logging
# -----------------------------------------------------------------------------
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
LOG_DIR="${XDG_STATE_HOME:-$HOME/.local/state}/dustvalve-next-nightly-update"
SNAP_DIR="${XDG_RUNTIME_DIR:-/tmp}/dustvalve-next-nightly-update"
mkdir -p "$LOG_DIR" "$SNAP_DIR"
RUN_ID="$(date -u +%Y%m%dT%H%M%SZ)"
LOG="$LOG_DIR/nightly-update.${RUN_ID}.log"
exec >>"$LOG" 2>&1
echo
echo "=================================================================="
echo "Nightly run starting: $RUN_ID"
echo "REPO: $REPO_DIR"
echo "=================================================================="

# -----------------------------------------------------------------------------
# Resolve `mini` (the user's bashrc alias for `claude`)
#
# `mini` in ~/.bashrc expands (after the claude alias) to:
#   claude --allow-dangerously-skip-permissions --permission-mode plan \
#          --settings ~/.claude/minimax-settings.json
#
# Two problems for a non-interactive shell:
#   (a) .bashrc's standard `[[ $- != *i* ]] && return` guard fires before
#       any alias line, so the alias is invisible here.
#   (b) `--permission-mode plan` is read-only — the agent would only
#       propose changes, never make them. Useless for an autonomous run.
#       We substitute `bypassPermissions` so the agent can actually
#       edit files at 03:00 without a human at the keyboard.
#
# We solve both by defining `mini` as a shell function that mirrors the
# alias but uses bypassPermissions. Keep this in sync if you change the
# alias.
# -----------------------------------------------------------------------------
CLAUDE_BIN="/home/user/.local/bin/claude"
CLAUDE_SETTINGS="$HOME/.claude/minimax-settings.json"
if [ ! -x "$CLAUDE_BIN" ]; then
  echo "FATAL: claude binary not found at $CLAUDE_BIN" >&2
  exit 1
fi
if [ ! -f "$CLAUDE_SETTINGS" ]; then
  echo "WARN: settings file $CLAUDE_SETTINGS not found; continuing without it." >&2
  CLAUDE_SETTINGS=""
fi

mini() {
  local -a args=(
    --allow-dangerously-skip-permissions
    --permission-mode bypassPermissions
  )
  if [ -n "$CLAUDE_SETTINGS" ]; then
    args+=( --settings "$CLAUDE_SETTINGS" )
  fi
  args+=( "$@" )
  "$CLAUDE_BIN" "${args[@]}"
}
export -f mini

# Belt-and-suspenders: scope the agent's tool surface so even with
# --allow-dangerously-skip-permissions it can only touch what we expect.
# Write is included so the agent can also create new files when needed
# (e.g. updating a baseline XML for a freshly-revealed detekt finding).
# Workflow + Agent are included so the agent can launch the workflow-
# driven dep audit the user asked for (fan-out research, fan-in apply).
ALLOWED_TOOLS="Bash Read Edit Write Workflow Agent WebFetch WebSearch"

# -----------------------------------------------------------------------------
# Acquire a separate nightly lock so two nightly runs don't trample
# each other. We deliberately do NOT take build.sh's .build.lock —
# build.sh manages that itself and a single nightly run needs to call
# build.sh many times (once per iteration of the recovery loop).
# -----------------------------------------------------------------------------
NIGHTLY_LOCK="$SNAP_DIR/nightly.lock"
exec 9>"$NIGHTLY_LOCK"
if ! flock -n 9; then
  echo "Another nightly run is already in progress. Exiting cleanly."
  exit 0
fi
cleanup() {
  rm -f "$SNAP_DIR/libs.versions.toml.snapshot"
  if [ -n "${DID_STASH:-}" ]; then
    echo "Restoring auto-stash."
    git -C "$REPO_DIR" stash pop >/dev/null 2>&1 || echo "WARN: stash pop failed; resolve manually."
  fi
}
trap cleanup EXIT

# -----------------------------------------------------------------------------
# Working-tree guard. The nightly script's job is dep updates, not
# cleaning up after a human. If the tree is dirty, refuse to run
# (default) or stash + restore (opt-in via DIRTY_TREE_POLICY=stash).
# -----------------------------------------------------------------------------
DIRTY_TREE_POLICY="${DIRTY_TREE_POLICY:-error}"
cd "$REPO_DIR"
if ! git diff --quiet HEAD 2>/dev/null || [ -n "$(git ls-files --others --exclude-standard)" ]; then
  case "$DIRTY_TREE_POLICY" in
    error)
      echo "Working tree is dirty (uncommitted changes or untracked files)."
      git status --short
      echo "Commit or stash your in-progress work before the nightly runs."
      exit 0
      ;;
    stash)
      echo "Working tree is dirty; stashing before run."
      git stash push -u -m "nightly-update.sh auto-stash @ $(date -u +%FT%TZ)" >/dev/null
      DID_STASH=1
      ;;
    ignore)
      echo "WARN: working tree is dirty and DIRTY_TREE_POLICY=ignore."
      ;;
    *)
      echo "Unknown DIRTY_TREE_POLICY=$DIRTY_TREE_POLICY (accepted: error, stash, ignore)"
      exit 2
      ;;
  esac
fi

# -----------------------------------------------------------------------------
# Sync with origin. Fast-forward only — never destroy local work.
# -----------------------------------------------------------------------------
git fetch origin
if ! git pull --ff-only; then
  echo "Non-FF pull (local commits or diverged). Leaving for manual review."
  exit 1
fi

# -----------------------------------------------------------------------------
# Snapshot the version catalog so Phase 3 can revert individual [versions]
# keys rather than the whole file.
# -----------------------------------------------------------------------------
cp gradle/libs.versions.toml "$SNAP_DIR/libs.versions.toml.snapshot"
echo "Snapshot: $SNAP_DIR/libs.versions.toml.snapshot"

# -----------------------------------------------------------------------------
# Phase 1: dep research & update
# -----------------------------------------------------------------------------
echo
echo "=== Phase 1: dep research & update ==="
set +e
mini -p "
You are the dependency update agent for the Android Kotlin project at
$REPO_DIR (package: dustvalve_next, AGGRESSIVE bump policy).

POLICY (per project memory — this is the opposite of conservative):
- Bump EVERY dep that has a newer published version, including alphas
  and betas. Do NOT preemptively skip 'risky' or 'cascading' bumps.
- AGP, Kotlin, KSP, Hilt plugin are all in scope. Bump them.
- Compose versions move as a set: compose-bom + compose + material3 +
  material3-adaptive + graphics-shapes. Bump all or none of them.
- Detekt 2.0.0-alpha.3 and ktlint 14.2.0 are pinned only because
  alpha N+1 isn't published; if a newer version is published, bump it.

MANDATORY: You MUST launch a Workflow via the Workflow tool. The
user has explicitly asked for a workflow-driven audit. Do not do the
research serially. The Workflow tool takes a JS script inline; the
script uses agent() and parallel() to fan out work.

IMPORTANT: do NOT pass a schema option to agent() (StructuredOutput
has a known issue with top-level array schemas). Have each subagent
return PLAIN TEXT — one finding per line, formatted like
'lib=<name> current=<v> latest=<v> breaking=<notes or none>'. Lines
starting with # are comments.

Template (adapt to this project):

  export const meta = {
    name: 'dep-update-audit',
    description: 'Fan-out dep research, fan-in apply',
    phases: [{title: 'Research'}, {title: 'Apply'}],
  };

  phase('Research');
  const findings = await parallel([
    () => agent(
      'Read gradle/libs.versions.toml. Also grep app/build.gradle.kts
       for any direct coordinates (e.g. com.github.* jitpack deps).
       For each entry from Maven Central (Kotlin, JetBrains, kotlinx,
       coroutines, Hilt, etc.), look up the latest published version
       (alpha/beta/rc/stable all count) on Maven Central via WebFetch
       (try https://repo1.maven.org/maven2/<path>/maven-metadata.xml).
       Return a plain-text list, ONE FINDING PER LINE in the exact
       format: lib=<name> current=<v> latest=<v> breaking=<notes or
       none>. Lines starting with # are comments. Do not return JSON.',
      {phase: 'Research'}
    ),
    () => agent(
      'Read gradle/libs.versions.toml. For each entry from Google
       Maven (androidx, material3, room, datastore, activity-compose,
       lifecycle, work, palette, coil, media3, etc.), look up the
       latest published version on Google Maven via WebFetch (try
       https://dl.google.com/android/maven2/<path>/maven-metadata.xml).
       Return plain text, one finding per line: lib=<name> current=<v>
       latest=<v> breaking=<notes or none>.',
      {phase: 'Research'}
    ),
    () => agent(
      'Read gradle/libs.versions.toml. For each entry from GitHub
       Releases (ktlint, detekt, compose-rules, slack-lint-checks,
       slack-compose-lints, dependency-analysis, AGP, KSP, kotlin-
       compose, kotlin serialization). Also grep app/build.gradle.kts
       for com.github.* jitpack deps and look up their latest GitHub
       release tag (e.g. NewPipeExtractor v0.26.x). Return plain text,
       one finding per line: lib=<name> current=<v> latest=<v>
       breaking=<notes or none>.',
      {phase: 'Research'}
    ),
  ]);

  phase('Apply');
  // Parse the text findings. Apply aggressive-bump policy: for EVERY
  // entry where latest is NEWER than current (regardless of
  // pre-release vs stable), Edit gradle/libs.versions.toml (one Edit
  // per version string). For any direct dep in app/build.gradle.kts,
  // Edit the version literal there. Honor the Compose set rule. Return
  // { bumped, skipped }.

After the workflow returns, exit 0 if anything was bumped, exit 1 if
nothing was safe to bump.

Do NOT touch any other file. Do NOT commit or push. Do NOT add new
dependencies, only bump existing ones.
" \
  --add-dir "$REPO_DIR" \
  --allowedTools "$ALLOWED_TOOLS"
PHASE1_RC=$?
set -e
echo "Phase 1 exit code: $PHASE1_RC"

# -----------------------------------------------------------------------------
# Phase 2 + 3: build, with up to MAX_ITER rounds of failure recovery.
# -----------------------------------------------------------------------------
echo
echo "=== Phase 2: build + recovery ==="
MAX_ITER=5
for i in $(seq 1 $MAX_ITER); do
  echo
  echo "--- build attempt $i/$MAX_ITER ---"
  if ./build.sh; then
    echo "Build PASS on attempt $i."
    break
  fi
  if [ "$i" -eq "$MAX_ITER" ]; then
    echo "Build FAILED after $MAX_ITER attempts. Reverting all dep bumps this run."
    cp "$SNAP_DIR/libs.versions.toml.snapshot" gradle/libs.versions.toml
    if ./build.sh; then
      echo "Restored snapshot builds clean. Aborting nightly run with no commit."
    else
      echo "Even snapshot fails to build — pre-existing broken state, not touching."
    fi
    exit 1
  fi

  echo
  echo "=== Phase 3: failure recovery (iter $i) ==="
  set +e
  mini -p "
You are the build-failure recovery agent for the Android Kotlin project at
$REPO_DIR. The latest './build.sh' run failed.

POLICY (per project memory):
- This project tracks bleeding-edge deps. Build.sh runs detekt with
  autoCorrect, which rewrites source files and invalidates the
  detekt/lint baselines — so failures often appear in files you didn't
  touch, and the right answer is to fix the code, NOT to revert the dep.
- Only revert a dep bump if the build genuinely cannot be made green
  with a code/config fix.

MANDATORY: You MUST launch a Workflow via the Workflow tool to
diagnose the failure. Do not read the log and decide on your own.

IMPORTANT: do NOT pass a schema option to agent() (same array-vs-
object quirk as Phase 1). Subagents return PLAIN TEXT.

Template (adapt to this project):

  export const meta = {
    name: 'build-failure-diagnosis',
    description: 'Fan-out log analysis, fan-in apply',
    phases: [{title: 'Diagnose'}, {title: 'Apply'}],
  };

  phase('Diagnose');
  const diagnosis = await parallel([
    () => agent('Read the most recent log under $LOG_DIR (sort by
                  name, newest is the last). Focus on the
                  ktlintCheck and detekt output. If a detekt
                  autoCorrect rewrote a signature and a baseline
                  entry no longer matches, that is the cause.
                  Return a plain-text one-line in the exact format:
                    failing_phase=<phase> root_cause=<one line>
                    offending_dep=<lib key in libs.versions.toml or none>
                    offending_files=<comma-separated paths or none>
                    evidence=<short quote or file:line>',
                 {phase: 'Diagnose'}),
    () => agent('Read the most recent log under $LOG_DIR. Focus on
                  the lintRelease output. The slack-compose-lints
                  rule may fire under two engines with different ids
                  (detekt ViewModelForwarding AND Android-Lint
                  ComposeViewModelForwarding). Return a plain-text
                  one-line in the same format as agent #1.',
                 {phase: 'Diagnose'}),
    () => agent('Read the most recent log under $LOG_DIR. Focus on
                  assembleDebug+assembleRelease and testDebugUnitTest
                  output. If a dep bump caused a binary-incompatible
                  change, that is the offending_dep. Return a
                  plain-text one-line in the same format as agent #1.',
                 {phase: 'Diagnose'}),
  ]);

  phase('Apply');
  // Pick the most specific diagnosis. PREFER FIXING THE CODE. Most
  // failures will be one of:
  //   - detekt autoCorrect introduced a signature shift; baseline
  //     entry no longer matches. Re-run
  //     './gradlew detektBaseline' to refresh the baseline, or
  //     re-suppress with a justifying comment if intrinsic
  //     (LongMethod, CyclomaticComplexMethod on a router/screen
  //     composable, etc.).
  //   - slack-compose-lints rule (ViewModelForwarding under two
  //     engines). Fix by self-injecting via hiltViewModel() default;
  //     do NOT forward.
  //   - RawDispatchersUse → inject @Dispatcher; runCatching → catch
  //     Throwable with structured logging.
  //   - ktlint import-order → run './gradlew ktlintFormat'.
  // Only if the cause is unambiguously a dep bump that breaks the
  // build in a way code can't fix, restore ONLY that one [versions]
  // key in gradle/libs.versions.toml to its value from
  // $SNAP_DIR/libs.versions.toml.snapshot. If multiple keys are
  // causally linked, restore them as a set.

Steps (semantic — adapt as needed):
3. PREFER FIXING THE CODE. Most failures will be one of the patterns
   above.
4. If the cause is unambiguously a dep bump, restore ONLY that one
   [versions] key in gradle/libs.versions.toml to its value from
   $SNAP_DIR/libs.versions.toml.snapshot. To do this, read both files
   with Read, find the offending key, and Edit the working file to set
   it back to the snapshotted value. Do not touch other keys.
5. If multiple bumps are implicated, revert only the ones that are
   causally linked (e.g. compose set).
6. Exit 0 once you have made any change (fix or revert). Exit 1 only if
   you cannot identify the cause at all.

Do NOT commit or push. Do NOT bump any other dep. Do NOT add new
dependencies.
" \
    --add-dir "$REPO_DIR" \
    --allowedTools "$ALLOWED_TOOLS"
  PHASE3_RC=$?
  set -e
  echo "Phase 3 exit code: $PHASE3_RC"
  if [ "$PHASE3_RC" -ne 0 ]; then
    echo "Recovery agent could not identify cause. Reverting all dep bumps this run."
    cp "$SNAP_DIR/libs.versions.toml.snapshot" gradle/libs.versions.toml
    exit 1
  fi
done

# -----------------------------------------------------------------------------
# Phase 4: commit & push
# -----------------------------------------------------------------------------
echo
echo "=== Phase 4: commit & push ==="
if git diff --quiet; then
  echo "No changes to commit."
  echo "Nightly run complete (no-op)."
  exit 0
fi

git add -A
SUMMARY="$(git diff --cached --stat | tail -5)"
# Keep the existing git identity (the user already has user.name/email
# configured in this repo per the harness). The -c flags are belt-and-
# suspenders in case git is run from a context without those set.
GIT_AUTHOR_NAME="$(git config user.name)"
GIT_AUTHOR_EMAIL="$(git config user.email)"
git -c user.name="$GIT_AUTHOR_NAME" \
    -c user.email="$GIT_AUTHOR_EMAIL" \
    commit -m "chore(deps): nightly update (${RUN_ID})

${SUMMARY}
"
echo "Commit created."

# Push only if upstream exists and the push will succeed.
if ! git rev-parse --abbrev-ref --symbolic-full-name '@{u}' >/dev/null 2>&1; then
  echo "No upstream configured; skipping push. Commit left locally."
  exit 0
fi

# Use the user's ed25519 key directly so this works without ssh-agent.
# ssh-agent is not always running at 03:00, especially on a remote
# session.
GIT_SSH_COMMAND="ssh -i $HOME/.ssh/id_ed25519 -o IdentitiesOnly=yes -o StrictHostKeyChecking=accept-new" \
  git push
echo "Push complete."
echo "Nightly run complete."
