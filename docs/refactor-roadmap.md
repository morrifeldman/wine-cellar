# Refactor Roadmap

Architecture review from 2026-10. Work top to bottom; tiers are ordered by
risk and payoff, and later tiers get easier once earlier ones land.

**Working agreement**
- One item (or one tight group) per commit; tick the box in the same commit.
- Each commit ships per the `ship` skill. Lint with clj-kondo, check the
  change in the REPL, and run the `verify` skill for UI changes.
- New findings get appended to the right tier, not fixed in passing.
- Line numbers are as of the review and will drift; search by name.

## Tier 1: bugs and security (small, do first)

- [ ] **Login allow-list.** Any Google account can sign in and gets the whole
  API, including `/api/admin/sql` and `/reset-database`. Check
  `email_verified` and an allowed-emails list in `handle-successful-auth`
  (`auth/core.clj`). Gate `/api/admin/*` on `get-admin-email`, which is unused.
- [ ] **Device tokens are full user tokens.** `verify-token` ignores
  `:aud`/`:type`, so the ESP32 token can call any `/api` route. Only accept
  device tokens on sensor ingest, and reject user tokens there.
- [ ] **Restock casing bug.** `adjust-quantity` (`db/api.clj` ~531/536)
  compares `"restock"` once and `"Restock"` once, so `wines.original_quantity`
  never bumps. Use one `restock?` binding.
- [ ] **tap-middleware re-wraps exceptions** (`routes.clj` ~42). It throws an
  `ex-info` with no `:type`, so coercion errors likely become 500 instead of
  400. Confirm with a bad POST, then rethrow the original.
- [ ] **Frontend error fallback never used.** `handle-api-response`
  (`api.cljs` ~62) wraps `get-in` in `try`, so failures surface as
  `:error nil`. Use `(or (get-in …) error-msg)`.
- [ ] **Spirit label schemas omit "vermouth".** `anthropic.clj`,
  `gemini.clj` and `openai.clj` hard-code the category list; use
  `common/spirit-categories`.
- [ ] **Chat images mislabelled as JPEG** (`prompts.clj` ~403/453). Parse the
  data URL once and reuse `infer-media-type`.
- [ ] **`drop-tables` is incomplete.** It misses `inventory_history`,
  `cellar_reports`, `spirits`, `bar_inventory_items` and `cocktail_recipes`.
- [ ] **`valid_tasting_window` CHECK.** `[:= :drink_from_year]` has one arg;
  check the rendered SQL and use `[:= … nil]`.
- [ ] **New `wine_style` values never reach existing DBs.** Add
  `ALTER TYPE … ADD VALUE IF NOT EXISTS` for each of `common/wine-styles`.

## Tier 2: backend error path and auth model

- [ ] One exception middleware: `ex-info` `:status` → that status,
  `PSQLException` → 400, everything else → 500 with `{:error …}`. Details go
  in the response only in dev. Coercion errors get the same shape.
- [ ] Response helpers (`ok-or-404`, `created`, `deleted-or-404`). Delete
  `with-server-error`, `with-ai-error`, `if-wine`, `if-device` and the
  per-handler try/catch.
- [ ] Route-data auth (`:auth :public | :user | :device-ingest`), run before
  coercion, replacing the positional `/api` middleware.
- [ ] Shared error `:responses` in router `:data`. Add a `crud-routes` factory
  for spirits, recipes, grape varieties, classifications and bar inventory.
- [ ] Spec hygiene: define the missing specs (`::adjustment`, `::wine_ids`,
  `::content`, …), remove the duplicate `::notes`, and move specs out of
  `routes.clj`.
- [ ] Split `handlers.clj` by domain (wines, bar, conversations, devices,
  admin, ai).
- [ ] Logging: one `log/event` helper with redaction. Stop tapping JWT user
  info and full bodies in verbose mode, and remove duplicate taps.

## Tier 3: shared cljc validation and enums

- [ ] `wine-cellar.validation` (cljc): vintage range, tasting window,
  name-or-producer, quantity ≥ 0, price/ABV ranges. Used by `wines/form.cljs`
  and the route specs.
- [ ] Separate rating ranges: wine 1–100, recipe 1–10, as constants in
  `common` used by the specs, the forms and the DB CHECK.
- [ ] One drinking-window classifier in cljc (`vintage.cljs`
  `tasting-window-status` vs `summary.cljc` `drinking-window-status`).
- [ ] Canonical sets for `inventory-reasons`, `chat-types` and
  `confidence-levels`. Move the "which reasons open or close a bottle" rule to
  cljc (it is duplicated in `detail.cljs` and `db/api.clj`).
- [ ] Move the pure filter predicates from `utils/filters.cljs` to cljc, with
  case-normalised style matching.

## Tier 4: data layer

- [ ] Table registry: a coercion spec per table, plus generic `by-id`,
  `list-all`, `insert!`, `update!` and `delete!`. Use consistent delete
  returns and timestamp serialisation.
- [ ] `schema_migrations` table and an ordered migrations vector (or
  Migratus), replacing the `ensure-tables` guard pile.
- [ ] Indexes: `tasting_notes(wine_id)`, `inventory_history(wine_id,
  occurred_at)`, `ai_conversation_messages(conversation_id, created_at)`,
  `ai_conversations(user_email, chat_type, last_message_at)`.
- [ ] `set_updated_at()` trigger. Normalise timestamps to `timestamptz`.
- [ ] Shared history-insert and open-bottle helpers for `adjust-quantity`,
  `coravin-pour` and `finish-open-bottle`.
- [ ] Scope conversation queries by `user_email` in SQL, not only in the
  handler.
- [ ] Use `q-one`/`q-many` everywhere: reports, thumbnails, seeders and raw
  SQL strings. Pass the conversation-search regex as a bound parameter.

## Tier 5: frontend

- [ ] `request!` helper in `api.cljs` (busy key, error path, on-success,
  promise), plus `upsert-by-id`/`remove-by-id`. Inject the AI provider in one
  place.
- [ ] Declare all transient UI keys in `initial-app-state` (grouped under
  `:ui` or per feature). Reset becomes `(reset! app-state initial-app-state)`.
- [ ] `confirm-dialog` component; replace the two copy-pasted dialogs and the
  `js/confirm` calls.
- [ ] Field-spec table driving the editable views and the create forms
  (`detail.cljs`, `spirits.cljs`, `recipes.cljs`, `technical_data.cljs`).
  Move the editors into `components/editable.cljs`.
- [ ] `with-form-draft` + `chip-autocomplete`, shared by the tasting-note and
  blind-tasting forms.
- [ ] Split `detail.cljs` and `recipes.cljs` along their existing sections.
- [ ] One `fmt-n`, one dot-separator helper, one date formatter in
  `utils/formatting`.

## Tier 6: AI layer

- [ ] `ai/schemas.clj`: each JSON schema written once, with a small adapter
  per provider.
- [ ] `ai/http.clj`: shared POST with timeout, retry on 429/5xx and uniform
  `ex-info`. Check truncation and refusal for every provider.
- [ ] `ai/json.clj`: one `parse-model-json`.
- [ ] Provider protocol (`complete`) plus provider-neutral `ai/tasks.clj`,
  removing the 7 `case provider` switches. The recipe extractors then work
  with every provider.
- [ ] Per-task tier and token config in `ai-models.edn`. Reports use the
  selected provider instead of hard-coded `:anthropic`.

## Long functions to break up

Most of these shrink on their own once tiers 3 and 5 land.

- [ ] `wines/form.cljs` `wine-form` (348 lines)
- [ ] `chat/core.cljs` `chat-dialog` (218)
- [ ] `bar/spirits.cljs` `spirit-detail` (179) and `spirit-create-form` (126)
- [ ] `ai/prompts.clj` `format-wine-summary` (137), to be made table-driven
- [ ] `bar/recipes.cljs` `recipe-display`, `recipes-tab`, `recipe-form`,
  `save-recipe-dialog`
- [ ] `api.cljs` `poll-job-status` (113): drop the test-failure scaffolding
- [ ] `components.cljs` `editable-field-wrapper` (111)
- [ ] `wset_shared.cljs` `wset-display`, `wset_palate.cljs`, to be driven from
  `common/wset-lexicon`
- [ ] `main.cljs` `admin-menu-items`, `core.cljs` `on-navigate`
- [ ] `db/api.clj` `sensor-reading-series`: generate the metric columns

## Cleanups (any time)

- [ ] Delete `views/components/portal_debug.cljs`, `latest-sensor-reading`,
  `activate-device!`, `combine-wine-lists`, `slider-field`, `switch-field`,
  `validation-message`, and the leftover `tap>`/`console.log` calls.
- [ ] Move `dev/seed_*.clj` out of `src/`.
- [ ] `deps.edn`: add `jsonista` explicitly and align the cider-nrepl versions.
- [ ] `verify` skill: use a relative path instead of `/home/morri/...`. Add a
  `playwright.config` and an `npm test` script.
- [ ] Archive the stale feature write-ups in `docs/`.
