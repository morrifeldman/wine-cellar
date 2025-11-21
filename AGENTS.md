# Repository Guidelines

## Project Structure & Module Organization
The backend Clojure source lives in `src/clj/wine_cellar` (config, routes, db, auth modules), while shared code sits in `src/cljc` and the ClojureScript UI in `src/cljs/wine_cellar`. Static assets compile to `public/` and backend resources to `resources/`. Development entry points are under `dev/`, infra playbooks under `automation/`, and reusable tooling scripts under `scripts/`. Docs and screenshots reside in `docs/`. The embedded firmware prototype is in `embedded/esp32-sentinel` (ESP-IDF project for the cellar environment sensor).

## Build, Test, and Development Commands
Run `npm install` once to sync JS dependencies. Use `clj -M:dev-all` to boot backend + shadow-cljs watcher together via `wine-cellar.dev`. For a frontend-only loop, run `npx shadow-cljs watch app` (outputs to `public/js`). Start just the API with `clj -M:run-server`. Package a production bundle through `npx shadow-cljs release app` before deploying.

## Embedded ESP32 Sentinel
- Location: `embedded/esp32-sentinel` (ESP-IDF v5.x).
- One-time setup: install ESP-IDF, export env (`. $IDF_PATH/export.sh`), run `idf.py set-target esp32`.
- Configure: copy `main/config.example.h` → `main/config.h` (gitignored); set Wi-Fi SSID/PASS, backend URL (e.g. `http://<host>:3000/api/cellar-conditions`), device JWT from `device.jwt`, and `DEVICE_ID`. Add `CELLAR_API_CERT_PEM` if hitting HTTPS with a private CA.
- Build/flash/monitor:
  ```
  cd embedded/esp32-sentinel
  idf.py build
  idf.py -p /dev/ttyUSB0 flash
  idf.py -p /dev/ttyUSB0 monitor   # Ctrl+] to exit
  ```
- Sensors: currently drives BMP085/BMP180 over I²C GPIO21/22; optional SSD1306 OLED shares the same bus (default addr `0x3C`).
- API: sends placeholder JSON to `/api/cellar-conditions`; keep payload aligned with `docs/cellar-conditions.md` while wiring real sensors.

## Coding Style & Naming Conventions
Stick to 2-space indentation in `.clj`/`.cljs` files and align maps for readability. Namespaces mirror directory layout (`wine-cellar.<area>`); functions and vars use kebab-case, records and React components use PascalCase. Format code with `clj -M:format` (zprint wrapper) or `scripts/format-clj.sh` prior to committing. Prefer immutable data structures and threading macros for request pipelines. Avoid shadowing core vars (e.g. reusing names like `group-by` or `map`) when binding locals so that calls to the standard library continue to work without namespace qualifiers.

## Testing Guidelines
Run `clj -M:clj-kondo --lint src --report-level error` after every set of changes to catch syntax or lint issues early. Prefer to keep functions short and make incremental edits so mismatched parentheses are easier to spot; lint between small steps.

See `CLAUDE.md` for additional working-style preferences (incremental development, minimal comments, etc.); those guidelines apply here as well.

## Commit & Pull Request Guidelines
Follow the existing Git log style: imperative, concise subjects such as “Add store and purchase date to AI summary”, without trailing periods. Each PR must describe the change, note schema migrations, list new environment variables, and link tracking issues. Include screenshots or CLI output for UI or data-flow changes, and confirm that formatters and tests ran locally.

## Security & Configuration Tips
Do not commit secrets; store local creds with `pass` as outlined in `docs/environment-variables.md`. Keep Fly.io settings in sync with `fly.toml.template`, and update Ansible automation in `automation/postgresql.yml` when DB requirements shift. Review OAuth redirects and JWT signing keys whenever domains change.
