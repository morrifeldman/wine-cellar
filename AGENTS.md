# AGENTS.md

## Project Structure
Backend Clojure source lives in `src/clj/wine_cellar`, shared code in `src/cljc`, and ClojureScript UI in `src/cljs/wine_cellar`. Static assets compile to `public/`, resources to `resources/`. Dev entry points under `dev/`, infra playbooks under `automation/`, scripts under `scripts/`, docs in `docs/`. Embedded cellar sentinel firmware in `embedded/esp32-sentinel`.

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

## Working Style Preferences
- **Incremental Development**: One change at a time so changes can be tested before moving on
- **Pair Programming**: Be collaborative and don't get too far ahead with suggestions
- **Declarative Approach**: Minimize duplication and create reusable abstractions when sensible
- **Minimal Comments**: Code comments are generally not needed
- **Double Check Parentheses**: Keep function size short and ensure parentheses match in Clojure code
- **Minimal Summaries**: Provide only brief summaries of changes or none at all
- **Code Suggestions**: If code replacement tools encounter errors, suggest changes in chat instead
- **Small Edits**: For single character additions (like missing parentheses), suggest the fix in chat rather than using Edit tool
- **Json Processing**: Use jsonista for json processing
- **Git Operations**: Do NOT commit or stage code. User handles `git add` and `git commit` manually
- **Formatting**: Do NOT run code formatting tools. A pre-commit hook handles this
- **Migrations**: Propose ad-hoc Clojure scripts (in `scripts/wine_cellar/scripts/`) for data tasks rather than manual DB manipulations
- **Testing**: Ask user to test features before proposing finalization; do not assume they work
- **Verification**: Run `clj -M:clj-kondo --lint src/<FILE CHANGED>` after each change

## UI/UX Preferences
- **Taxonomy**: `Classification` = Terroir/Site (Grand Cru). `Designation` = Style/Aging (Riserva)
- **Cards**: Minimalist, label-free, dot-separated metadata
- **Theme**: Dark burgundy. Ratings 95+ = Gold (#FFD54F), 90-94 = Pinkish (#E8C3C8)

## UI Verification & Testing
- **Playwright**: Use `dev/ui_check.js` for E2E verification
- **Auth Injection**: To bypass Google OAuth in headless tests, generate a JWT via the backend REPL (`auth/create-jwt-token`) and inject it as an `auth-token` cookie in the Playwright browser context
- **Frontend REPL**: Use for "White Box" state introspection. Query `@wine-cellar.core/app-state` when a browser runtime is connected to Shadow-CLJS

## Coding Style
2-space indentation in `.clj`/`.cljs` files, align maps for readability. Namespaces mirror directory layout (`wine-cellar.<area>`); functions/vars use kebab-case, records/React components use PascalCase. Prefer immutable data structures and threading macros. Avoid shadowing core vars (e.g. `group-by`, `map`).

## Commit & PR Guidelines
Follow existing Git log style: imperative, concise subjects like "Add store and purchase date to AI summary", no trailing periods. PRs must describe the change, note schema migrations, list new env vars, and link issues. Include screenshots for UI changes.

## Security & Configuration
Do not commit secrets; store local creds with `pass` as outlined in `docs/environment-variables.md`. Keep Fly.io settings in sync with `fly.toml.template`. Review OAuth redirects and JWT signing keys when domains change.

## Embedded ESP32 Sentinel
Location: `embedded/esp32-sentinel` (ESP-IDF v5.x).
- Setup: install ESP-IDF, export env (`. $IDF_PATH/export.sh`), run `idf.py set-target esp32`
- Configure: copy `main/config.example.h` → `main/config.h`; set Wi-Fi, backend URL, device JWT, `DEVICE_ID`
- Build/flash: `idf.py build && idf.py -p /dev/ttyUSB0 flash`
- Monitor: `idf.py -p /dev/ttyUSB0 monitor` (Ctrl+] to exit)
- Sensors: BMP085/BMP180 over I²C GPIO21/22; optional SSD1306 OLED at `0x3C`

## Current Priorities
1. Implement AI-driven wine pairing features
2. Enable contextual AI conversations about wine selection
3. Improve the editor with pretty-print or Portal-like features
4. Use app-state :view state more broadly throughout the application
5. UI/UX improvements
