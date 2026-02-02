# GEMINI.md

For architecture, project structure, build commands, and technical reference, see **[AGENTS.md](AGENTS.md)**.

## Workflow Preferences

- **Git Operations**: I do NOT commit or stage code. You handle `git add` and `git commit` manually.
- **Formatting**: I do NOT run code formatting tools. A pre-commit hook handles this.
- **Frontend Build**: I do NOT run compilation commands. I check `.shadow-cljs/build.log` for status.
- **Verification**: I MUST run `clj -M:clj-kondo --lint src` and check the shadow-cljs build log after every change.
- **Migrations**: I propose ad-hoc Clojure scripts (in `scripts/wine_cellar/scripts/`) for data tasks rather than manual DB manipulations.
- **Testing**: I ask you to test features before proposing any finalization; I do not assume they work.

## UI Verification & Testing

- **Playwright**: Use `dev/ui_check.js` for E2E verification.
- **Auth Injection**: To bypass Google OAuth in headless tests, generate a JWT via the backend REPL (`auth/create-jwt-token`) and inject it as an `auth-token` cookie in the Playwright browser context.
- **Frontend REPL**: Use for "White Box" state introspection. Query `@wine-cellar.core/app-state` when a browser runtime is connected to Shadow-CLJS.

## UI/UX Preferences

- **Taxonomy**: `Classification` = Terroir/Site (Grand Cru). `Designation` = Style/Aging (Riserva).
- **Cards**: Minimalist, label-free, dot-separated metadata.
- **Theme**: Dark burgundy. Ratings 95+ = Gold (#FFD54F), 90-94 = Pinkish (#E8C3C8).

## Live REPL Access

- **Backend**: `bb scripts/repl_client.clj "(expr)"`
- **Frontend**: `REPL_PORT_FILE=.shadow-cljs/nrepl.port REPL_CLJS_BUILD=app bb scripts/repl_client.clj "(cljs-expr)"`
- **Restart Backend**: `bb scripts/repl_client.clj "(do (require 'clojure.tools.namespace.repl) (clojure.tools.namespace.repl/refresh))"`
