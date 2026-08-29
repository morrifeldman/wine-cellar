# AGENTS.md

## Build & Development Commands
- `npm install` - sync JS dependencies (once)
- `scripts/start-dev.sh` - full dev environment (backend + watcher + ngrok) in tmux session `wine-dev`

## Dev Environment Ownership
- The dev stack lives in tmux session `wine-dev` so it survives any single terminal or Claude session; start-dev also links it as a window into the user's most recently active tmux session for easy viewing
- Claude may health-check (ports 3000/8080) and restart it via `scripts/start-dev.sh` when it's down — check `tmux ls` and ports first, never double-start
- Read logs with `tmux capture-pane -pt wine-dev`; stop with `tmux send-keys -t wine-dev C-c` (dev-all's shutdown hook tears down every process it started)
- AI model defaults live in `resources/ai-models.edn`; env vars like `ANTHROPIC_SMALL_MODEL` override them at runtime

## Live REPL Access
- **Backend**: `bb scripts/repl_client.clj "(expr)"`
- **Frontend**: `REPL_PORT_FILE=.shadow-cljs/nrepl.port REPL_CLJS_BUILD=app bb scripts/repl_client.clj "(cljs-expr)"`
- **Restart Backend**: `bb scripts/repl_client.clj "(do (require 'clojure.tools.namespace.repl) (clojure.tools.namespace.repl/refresh))"`
- **Frontend Build**: Do NOT run compilation commands. Check `.shadow-cljs/build.log` for status.

## Conventions
- Run `clj -M:clj-kondo --lint src/<FILE CHANGED>` after each change
- Propose ad-hoc Clojure scripts (in `scripts/wine_cellar/scripts/`) for data tasks rather than manual DB manipulations
- When a task is finished, just commit and push it — don't wait for confirmation. Follow the `ship` skill: jj skips the git pre-commit hook, so formatting and the version bump have to be run by hand

## UI/UX
- **Cards**: Minimalist, label-free, dot-separated metadata
- **Theme**: Dark burgundy

## Credentials
Local dev uses `pass` password manager (paths like `wine-cellar/anthropic-api-key`). See `docs/environment-variables.md`.
