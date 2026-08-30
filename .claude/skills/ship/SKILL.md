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
2. **Format and bump** — `scripts/format-and-bump.sh`. It runs `jj fix` and
   increments the patch version in `public/version.json`. `jj fix` formats only
   the files your revision changed, which is why it beats a whole-tree pass. Add
   `--all` to format every source file anyway (`jj fix
   --include-unchanged-files`), about four seconds for the repo.
3. **Commit and push** —
   `jj commit -m "..."` → `jj bookmark set main -r @-` → `jj git push -b main`.

For a change that touches no app code — docs, scripts, this skill — run `jj fix`
on its own instead of the script. Bumping the version there would prompt every
user to reload for nothing.

Before running `jj fix`, glance at `jj log`: its default revset is every mutable
revision reachable from `@`, so it will reformat unpushed commits too, not just
the working copy. That's usually what you want, but not always.

## What `jj fix` needs installed

Two things, and neither fails quietly:

- **zprint on PATH.** The script checks first and points you at the project.
- **`jj-config.toml` linked into jj's repo-scoped config,** which is what tells
  jj to run zprint at all. jj keeps that file outside the repo — ask for its path
  with `jj config path --repo` rather than assuming `.jj/repo/config.toml`, which
  is where older jj put it. A fresh clone has no such file and `jj fix` exits 1
  with "No `fix.tools` are configured"; the script recognises that message, runs
  `scripts/setup-jj-config.sh`, and retries, so a clone repairs itself on first
  run.

`jj-config.toml` is the only place formatting is configured — both the zprint
settings and which files they apply to. Its patterns cover `src`, `dev`,
`scripts`, `deps.edn` and `shadow-cljs.edn`, deliberately leaving out the
vendored configs in `.clj-kondo/imports` and the hand-laid-out data in
`resources`.

## Why the version bump matters

The running app fetches `/version.json` every five minutes and prompts the user
to refresh when the version changes; the service worker keys its asset cache on
the same value. A deploy that reuses the version number leaves everyone on the
old bundle with no prompt — the app looks fine and is simply stale.

## Where a push goes

Pushing `main` triggers `.github/workflows/deploy.yml`, which deploys to Fly.io.
Any other bookmark just pushes. So a push to main is a production deploy.
