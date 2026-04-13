# AGENTS.md

## Build & Development Commands
- `npm install` - sync JS dependencies (once)
- `clj -M:dev-all` - boot backend + shadow-cljs watcher together
- `npx shadow-cljs watch app` - frontend-only (outputs to `public/js`)
- `clj -M:run-server` - API only
- `npx shadow-cljs release app` - production bundle

## Live REPL Access
- **Backend**: `bb scripts/repl_client.clj "(expr)"`
- **Frontend**: `REPL_PORT_FILE=.shadow-cljs/nrepl.port REPL_CLJS_BUILD=app bb scripts/repl_client.clj "(cljs-expr)"`
- **Restart Backend**: `bb scripts/repl_client.clj "(do (require 'clojure.tools.namespace.repl) (clojure.tools.namespace.repl/refresh))"`
- **Frontend Build**: Do NOT run compilation commands. Check `.shadow-cljs/build.log` for status.

## Working Style
- One change at a time; ask user to test before moving on
- Be collaborative, don't get too far ahead with suggestions
- Minimal or no summaries of changes
- If Edit tool errors, suggest changes in chat instead
- For single-character fixes (e.g. missing paren), suggest in chat rather than using Edit
- Use jsonista for JSON processing
- Stage and commit when work is complete; imperative, concise subjects, no trailing periods
- Propose ad-hoc Clojure scripts (in `scripts/wine_cellar/scripts/`) for data tasks rather than manual DB manipulations
- Run `clj -M:clj-kondo --lint src/<FILE CHANGED>` after each change

## UI/UX
- **Taxonomy**: `Classification` = Terroir/Site (Grand Cru). `Designation` = Style/Aging (Riserva)
- **Cards**: Minimalist, label-free, dot-separated metadata
- **Theme**: Dark burgundy. Ratings 95+ = Gold (#FFD54F), 90-94 = Pinkish (#E8C3C8)

## Testing
- **Playwright**: `dev/ui_check.js`; shared helpers in `dev/test_helpers.js`
- **Auth**: generate JWT via backend REPL (`auth/create-jwt-token`), inject as `auth-token` cookie
- **Frontend state**: query `@wine-cellar.core/app-state` via Shadow-CLJS REPL

## Credentials
Local dev uses `pass` password manager (paths like `wine-cellar/anthropic-api-key`). See `docs/environment-variables.md`.
