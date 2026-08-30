---
name: ship
description: Finish a wine-cellar change — lint, format, bump the app version, commit, push. Use whenever a task is done and the work is about to be committed.
---

# Shipping a wine-cellar change

Two things have to happen before a commit and nothing in the repo does them for
you: Clojure sources get formatted, and `public/version.json` gets a new version
number. This repo is driven with jj, and jj has no commit hooks — skip these and
nothing fails, you just ship unformatted code that never prompts anyone to
refresh.

## The sequence

1. **Lint what you touched** — `clj -M:clj-kondo --lint src/<file>` for each
   changed source file.
2. **Format and bump** — `.claude/skills/ship/format-and-bump.sh`. It runs
   `jj fix` (zprint; settings live in `jj-config.toml`) and increments the patch
   version in `public/version.json`.
3. **Commit and push** —
   `jj commit -m "..."` → `jj bookmark set main -r @-` → `jj git push -b main`.

Skip step 2 for a change that touches no app code — docs, scripts, this skill.
Bumping the version there would prompt every user to reload for nothing.

Before running `jj fix`, glance at `jj log`: its default revset is every mutable
revision reachable from `@`, so it will reformat unpushed commits too, not just
the working copy. That's usually what you want, but not always.

## Why the version bump matters

The running app fetches `/version.json` every five minutes and prompts the user
to refresh when the version changes; the service worker keys its asset cache on
the same value. A deploy that reuses the version number leaves everyone on the
old bundle with no prompt — the app looks fine and is simply stale.

## Where a push goes

Pushing `main` triggers `.github/workflows/deploy.yml`, which deploys to Fly.io.
Any other bookmark just pushes. So a push to main is a production deploy.
