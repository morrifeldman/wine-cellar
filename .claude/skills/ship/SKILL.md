---
name: ship
description: Finish a wine-cellar change — lint, format, bump the app version, commit, push. Use whenever a task is done and the work is about to be committed.
---

# Shipping a wine-cellar change

Two things have to happen before a commit that the code itself won't do for you:
Clojure files get formatted, and `public/version.json` gets a new version number.

There is a git `pre-commit` hook that does both (`scripts/pre-commit`, installed by
`npm install`). **It only runs on `git commit`.** This repo is normally driven with
jj, and jj has no commit hooks, so a jj commit skips formatting and the version bump
silently. That is how a run of commits shipped unformatted, all sharing one stale
version number.

## The sequence

1. **Lint what you touched** — `clj -M:clj-kondo --lint src/<file>` for each changed
   source file.
2. **Format** — `jj fix` (zprint via `jj-config.toml`; the default revset is the
   mutable revisions reachable from `@`, so check `jj log` first if you have more
   than the working copy uncommitted). Under plain git, `scripts/format-clj.sh <file>`
   per file instead, or just let the pre-commit hook do it.
3. **Bump the version** — `scripts/update-version.sh --increment`. This rewrites
   `public/version.json` with a new patch number plus the current commit and branch.
4. **Commit and push** — the jj workflow:
   `jj commit -m "..."` → `jj bookmark set main -r @-` → `jj git push -b main`.

Steps 2 and 3 are the ones to double-check, since nothing fails loudly when they're
skipped.

## Why the version bump matters

The running app fetches `/version.json` every five minutes and, when the version
changes, prompts the user to refresh (`core.cljs`, `version.cljs`, the service
worker). A deploy that reuses the old version number leaves everyone on the old
bundle with no prompt — the app looks fine and is simply stale.

The commit hash inside `version.json` is written *before* the commit exists, so it
names the previous commit. That's expected; the version number is what the refresh
prompt compares.

## Where a push goes

Pushing `main` triggers `.github/workflows/deploy.yml`, which deploys to Fly.io.
Any other bookmark just pushes. So a push to main is a production deploy — bump the
version there without fail.
