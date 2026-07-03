# Dustvalve Next — agent guidelines

## Protected branches — DO NOT DELETE OR FORCE-PUSH

When asked to clean up "dangling" branches, worktrees, or any other git
state, the following branches are **legitimate and must never be erased**:

- `master`     — the canonical default branch.
- `legacy-android8` — the long-lived Android 8–16 backport branch that
  ships as the **default APK** on every GitHub release.

They are NOT dangling, not orphaned, and not stale — even if a sweep
finds no recent commits on them, that does NOT make them safe to delete.
Before deleting any branch whose name you are not 100% sure about,
stop and ask the user.

This rule applies regardless of how the cleanup was framed
("delete dangling branches", "prune stale refs", "wipe worktrees",
`git push origin --delete ...`, `git branch -D`, `git worktree remove`,
etc.).
