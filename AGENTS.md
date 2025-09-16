# Repository Guidelines

## Project Structure & Module Organization
The backend Clojure source lives in `src/clj/wine_cellar` (config, routes, db, auth modules), while shared code sits in `src/cljc` and the ClojureScript UI in `src/cljs/wine_cellar`. Static assets compile to `public/` and backend resources to `resources/`. Development entry points are under `dev/`, infra playbooks under `automation/`, and reusable tooling scripts under `scripts/`. Docs and screenshots reside in `docs/`.

## Build, Test, and Development Commands
Run `npm install` once to sync JS dependencies. Use `clj -M:dev-all` to boot backend + shadow-cljs watcher together via `wine-cellar.dev`. For a frontend-only loop, run `npx shadow-cljs watch app` (outputs to `public/js`). Start just the API with `clj -M:run-server`. Package a production bundle through `npx shadow-cljs release app` before deploying.

## Coding Style & Naming Conventions
Stick to 2-space indentation in `.clj`/`.cljs` files and align maps for readability. Namespaces mirror directory layout (`wine-cellar.<area>`); functions and vars use kebab-case, records and React components use PascalCase. Format code with `clj -M:format` (zprint wrapper) or `scripts/format-clj.sh` prior to committing. Prefer immutable data structures and threading macros for request pipelines.

## Testing Guidelines
Backend code should include `clojure.test`-based specs alongside each namespace (create matching files under a future `test/clj/wine_cellar` tree) and exercise DB logic against the fixtures in `resources/`. For UI and integration coverage, author Playwright specs (default location `tests/e2e/`) and run them with `npx playwright test`. Document any manual verification steps when automated coverage is impractical.

## Commit & Pull Request Guidelines
Follow the existing Git log style: imperative, concise subjects such as “Add store and purchase date to AI summary”, without trailing periods. Each PR must describe the change, note schema migrations, list new environment variables, and link tracking issues. Include screenshots or CLI output for UI or data-flow changes, and confirm that formatters and tests ran locally.

## Security & Configuration Tips
Do not commit secrets; store local creds with `pass` as outlined in `docs/environment-variables.md`. Keep Fly.io settings in sync with `fly.toml.template`, and update Ansible automation in `automation/postgresql.yml` when DB requirements shift. Review OAuth redirects and JWT signing keys whenever domains change.
