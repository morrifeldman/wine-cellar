# Code Quality & Architecture Review (2026-10)

A full-repo review, split by area: backend handlers/routes/DB, frontend API and
state, frontend views, auth, validation and the SQL model, and the AI layer. It
replaces the older version of this file. Items from that version that are still
open are folded in: API calls mixed with state updates, the editable-field
helper, and the oversized wine detail page.

Items marked **✔ verified** were checked by reading the code. The rest come
from the review reports; check line numbers before acting on them.

## Progress

Tick an item in the same commit that does it, and keep the IDs stable so
commits and sessions can refer to them. Section numbers point at the details
below.

**Phase 0: safety net**
- [x] T1 Test runner with a throwaway-database fixture (`clojure -M:test`)
- [x] T2 e2e test brought up to date with the current UI (`npm run test:e2e`)
- [x] T3 Cloud-session setup hook (`.claude/hooks/session-start.sh`)
- [x] B17 A fresh database failed to seed classifications (`:designations` has no column)

**Phase 1: bugs (§0)**
- [x] B4 Restock never updates `original_quantity`
- [x] B5 Frontend fallback error message is never shown
- [x] B6 Undefined request specs; duplicate `::notes` / `::vineyard`
- [x] B18 Every request-validation error came back as an opaque 500 (`tap-middleware` rewrapped exceptions)
- [x] B19 A bulk job's failure message was wiped by the `fetch-wines` it starts, which cleared `:error`
- [x] B20 `fetch-wines` merged old state over fresh data, so in-app refetches (e.g. after a bulk job) showed stale values
- [x] B21 The wine list and detail reads omitted the open-bottle columns, so an open Coravin bottle vanished from the UI on reload
- [x] B22 Failures in the bar, sensors, blind tastings and verbose-logging calls went to error keys no view displays (and three bar calls dropped failures entirely), so they failed silently
- [x] B23 A failing AI title call failed the save of the user's chat message
- [x] B24 An AI provider's own status reached the browser: a bad or revoked API key came back as 401, which the app treats as a lost session and redirects to login. The raw http-kit response in those errors also carried the API key into the logs. Provider failures are now 502s carrying only the provider's status and message.
- [x] B7 `drop-tables` skips the bar, report and inventory history tables
- [x] B8 Image MIME type hard-coded to JPEG: not a live bug, since the browser re-encodes every image as JPEG (`file->jpeg-data-url`). The provider-neutral image format is still part of 5.4.
- [x] B9 Check the tasting-window CHECK constraint: correct as is. HoneySQL renders `[:= col]` as `col IS NULL`.
- [x] B10 Recipe rating range: spec allows 1–100, DB allows 1–10
- [x] B2 JWT `iat`/`exp` in milliseconds: fixed for user tokens
- [x] B2b Same for device tokens: done. The sensor may need re-approval if its clock is off at boot; a firmware change to refresh on a 401 before wiping would remove that risk.
- [x] B13 `create-wine` not atomic; returns raw image bytes
- [x] B11 SSRF guard in `web_fetch`
- [x] B12 Raw SQL search regex in `list-conversations-for-user`
- [x] B14 `pass` subprocess on every request in dev
- [x] B15 Leaky `tap>` logs; Gemini key in URL (raw PostgreSQL messages sent to clients move to 1.1)
- [x] B16 `:secure` cookies; Anthropic key check
- [ ] B1/B3 Allowlist, `require-admin`, device-token scope (low priority, see §0)

**Phase 2: backend plumbing (§1)**
- [x] 1.1 One error-handling path + response helpers (`wine-cellar.http`). Error bodies were also never JSON-encoded, because the exception middleware sat outside muuntaja.
- [x] 1.3 Shared route `:responses`; merge duplicate route entries
- [x] 1.2 CRUD helpers in handlers and `db/api`. The `updated_at` gap was not real: classifications, grape varieties and bar inventory have no such column.
- [x] 1.6 Merge the two bulk jobs
- [x] 1.4 `->jsonb` and shared `q-one`/`q-many`. Not done: a global timestamp `ReadableColumn`, which would change every timestamp's JSON format the frontend reads. The codec maps weren't worth it once `->jsonb` existed.
- [x] 1.5 Inventory mutations share `record-history!`
- [x] 1.7 Smaller backend items: sensor ingestion, reports provider, recipe-links namespace, sensor series columns, aliases. Skipped: a `current-user` helper (the ways the user is read differ for good reasons) and the claim-code 401-vs-422 split (device-facing vs admin-facing).

**Phase 3: frontend plumbing (§2)**
- [x] 2.1 `request!` helper
- [x] 2.2 `replace-by-id` / `remove-by-id` / `prepend`, plus `bar-change!` for the bar lists. A full resource table wasn't worth it once `request!` made each call a few lines.
- [x] 2.3 One error / loading / toast convention: errors go to the app-wide banner, except in Devices, chat and photo import, which cover the banner and show their own. Loading flags are `request!`'s `:loading`.
- [x] 2.4 Chat, job polling and AI-call duplication in `api.cljs`. Left: `send-chat-message` keeps its raw channel for cancelling; page lifecycle (`load-wine-detail-page`) still lives in `api.cljs`.
- [x] 2.5 Memoized `filtered-sorted-wines`. Not done: regrouping app-state keys, which would touch most views for little gain now that `request!` owns the loading and error keys.

**Phase 4: shared UI (§3)**
- [x] 3.1 Theme tokens (`theme/tint`, `theme/gold`) + `theme/chip-sx` for the bar chips. Chart palettes, wine-colour swatches and the dev debug panel keep their own colours.
- [x] 3.2 `confirm!` dialog
- [x] 3.3 `form-dialog` + `ai-button`
- [x] 3.4 Wine detail field table; split `detail.cljs` into `detail/{fields,cellar,drinking_window,history}.cljs`
- [x] 3.5 `list-page`, `empty-state`, `detail-actions`, `summary-card`
- [x] 3.6–3.10 Dates: `date-input` and local `today-iso` (3.7). Long components: admin menu as a data table, chat search derived instead of stored, chat actions own `[:chat :messages]` (3.9). Recipe cards follow the card rule (3.10). Not done: one bar editing paradigm (3.6) and moving bar filters into app-state (3.8); both change how the bar behaves for no code saving, and `recipe-display` / `filter-header` are long but not duplicated.

**Phase 5: single source of truth for rules (§4)**
- [x] 4.1 Range predicates in `common.cljc`
- [ ] 4.2 Backend enum checks
- [ ] 4.3 Specs namespace + column coverage test
- [ ] 4.4 Migration tool
- [ ] 4.5 Indexes and schema hygiene

**Phase 6: AI layer (§5)**
- [ ] 5.2 One schema per output, rendered per provider
- [ ] 5.1 Task table instead of `case provider`
- [ ] 5.3 Shared HTTP layer
- [ ] 5.4 Neutral image format
- [ ] 5.5 AI config + capabilities
- [ ] 5.6 Prompt field lists generated from the schemas

---

## 0. Bugs and security issues found along the way

Fix these before any refactoring. Most are small.

Threat model: a personal app with two users. The Google OAuth app is in Testing mode, so only listed test users can log in. The auth findings below are ranked with that in mind.

| # | Issue | Where | Notes |
|---|---|---|---|
| B1 | **✔ verified: no admin check or login allowlist in code.** `/api` only checks "logged in"; nothing reads `ADMIN_EMAIL` (`auth/config.clj:34`). | `routes.clj:348,537-598`, `auth/core.clj:110-136` | **Low priority.** Safe today because the Google OAuth app is in Testing mode with two test users, so Google itself is the allowlist. Optional insurance: a 3-line email allowlist in `handle-successful-auth`, in case the OAuth app is ever published. |
| B2 | **✔ verified: JWT `iat`/`exp` are in milliseconds** (`inst-ms`). buddy-sign checks seconds, so tokens never expire on the server. | `auth/core.clj:106-107`, `devices.clj:35-36` | Low risk given the user base, but it is a one-line correctness fix. It also makes the device refresh-token rotation actually work. |
| B3 | Device tokens pass `require-authentication`, so a sensor's token works on every `/api` route. | `auth/core.clj:188-217`, `handlers.clj:59-63` | Low priority, since only approved devices get tokens. Worth fixing as part of a tidier auth layer: one `:identity` and `require-user` / `require-device`. |
| B4 | **✔ verified: restock casing mismatch.** The frontend sends `"restock"`, but the update map tests `"Restock"`, so `wines.original_quantity` is never updated on restock. | `db/api.clj:531` vs `:535` | Use one constant from `common.cljc`. |
| B5 | **✔ verified: the frontend's fallback error message is dead code.** `(try (get-in …) (catch …))` cannot throw, so on a network failure or an HTML 502 the `:error` is nil, and the "Failed to X" message passed in at about 70 call sites is never shown. | `api.cljs:62-69` | `(or (and (map? body) (:error body)) error-msg)` |
| B6 | **✔ verified: request specs that were never defined.** `::adjustment ::occurred_at ::change_amount ::wine_ids ::tokens_used ::is_user ::content` are used in `:req-un`, but there is no `s/def` for them, so only the key's presence is checked. `::notes` is defined twice and the later definition wins. `::vineyard` appears twice in `wine-update-schema`. | `routes.clj:73,112-126,709,734,259` | |
| B7 | **✔ verified: `drop-tables` skips** spirits, bar_inventory_items, cocktail_recipes, cellar_reports and inventory_history, so the admin reset leaves them behind. | `db/setup.clj:182-207` | |
| B8 | **✔ verified: image MIME type hard-coded to `image/jpeg`** when reading stored images back and for chat images. Any PNG or WebP is labeled wrongly, and `strip-image-data-url` only strips jpeg and png prefixes. | `db/api.clj:78`, `ai/prompts.clj:403-407,452-454` | Parse the data URL once into `{:media-type :data}`. |
| B9 | **To check: the tasting-window CHECK may be malformed.** `[:= :drink_from_year]` has one argument; it was probably meant as an `IS NULL` test. | `db/schema.clj:45-48` | Look at the generated DDL with `\d wines`. |
| B10 | Recipe rating: the DB CHECK is 1–10, but the route uses the shared 1–100 `::rating`, so `rating 50` passes the spec and then fails in the DB with a 500. | `routes.clj:73,220,225`, `setup.clj:~96` | |
| B11 | SSRF guard in `web_fetch` works on host-name prefixes: `"fd"` blocks `fdic.gov`, IPv6 / `0.0.0.0` / decimal IPs get through, and redirects are followed without checking the new host again. | `utils/web_fetch.clj` | Resolve the host and use `InetAddress` `isSiteLocal`/`isLoopback`/…; check each redirect. |
| B12 | `list-conversations-for-user` builds its search regex into raw SQL by escaping it by hand. | `db/api.clj:~263` | Pass it as a bound parameter. |
| B13 | The `create-wine` handler creates the classification and then the wine outside one transaction. `create-wine` returns `label_image` as raw bytes, while `update-wine!` converts it. | `handlers.clj:156-171`, `db/api.clj:~218` | |
| B14 | The secret lookup runs `pass` as a subprocess on every request in dev: `get-jwt-secret` is called on every `verify-token`. | `config_utils.clj`, `auth/core.clj` | Read the config once into a `defstate`. |
| B15 | Leaks: `tap>` logs the OAuth `code` and the full userinfo. Whole AI request bodies, base64 images included, are tapped. The Gemini key goes in the URL query string. Raw PostgreSQL messages are returned to the client. | `auth/core.clj:102,117,138`, `ai/*.clj`, `handlers.clj:853` | |
| B16 | Cookies are missing `:secure` in production. `ensure-api-key!` exists for OpenAI and Gemini but not Anthropic. | `auth/core.clj:121`, `server.clj:30` | |
| B18 | **✔ fixed: every request-validation error came back as a 500** with an opaque body. `tap-middleware` rewrapped all exceptions in a fresh `ex-info` without `:type`, so the exception middleware never reached the coercion handler. | `routes.clj:23-51` | Rethrow the original. Coercion errors now return 400 `{:details …}`. |

---

## 1. Backend (Clojure)

### 1.1 One error-handling path instead of six (HIGH, about −150–200 LOC)
Errors are handled in several ways: `with-server-error`, `with-ai-error`, raw `try/catch` that maps `PSQLException` to 400 (but only in some create/update handlers), `ExceptionInfo` mapped to 400, `handle-chat-error`, and hand-written try blocks in the admin handlers. The same bad input gets 400 from `update-wine` but 500 from `create-wine`, grape-variety, spirit and recipe.
**Change:** one reitit exception handler. `PSQLException` maps to 400; `ex-info` with `:status` in its data maps to that status; anything else maps to 500 with a generic message. Domain code just throws `(ex-info msg {:status 400})`. Add response helpers `ok` / `created` / `no-content` / `not-found`; today four different 201 idioms are in use.

### 1.2 Generic CRUD helpers in handlers and `db/api` (HIGH, about −250–350 LOC)
Classification, grape-variety, tasting-note, spirit, bar-inventory, cocktail-recipe, blind-tasting and device all repeat get/create/update/delete. They also disagree with each other:
- **delete:** some 404 on a missing row, some never 404, some fetch first and then delete (a race), some check `update-count`.
- **`updated_at`:** set by spirit, recipe, tasting note and wine; not set by classification, grape-variety or bar-inventory.
- `:returning :*` is repeated about 25 times.

**Change:** private `get-by-id` / `insert!` / `update-by-id!` (which always sets `updated_at`) / `delete-by-id!`, where delete returns a boolean from `update-count`. Handler combinators `get-or-404`, `update-or-404`, `delete-or-404`.

### 1.3 Route table driven by data (HIGH, about −250 LOC, low risk)
- `{200 … 404 {:body map?} 500 {:body map?}}` is copy-pasted about 70 times. Put shared `:responses` in the router `:data`.
- Merge the duplicate sibling entries for `"/classifications"`, `"/wines"` and `"/by-id/:id"`.
- Generate `wine-update-schema` from one list of wine fields. Today every field is listed twice, once inside a giant `(or …)`.
- Make URL shapes consistent, e.g. `DELETE /devices/:id/delete` → `DELETE /devices/:id`.
- Move the specs into their own namespace (see §4).

### 1.4 `db/api` conversion layer (MED, about −60 LOC)
- `(sql-cast :jsonb (json/write-value-as-string v))` is written out 11 times. Add `->jsonb`, or a `SettableParameter` extension so writes match the reads already handled in `db/connection.clj`.
- Replace the 12 hand-written `x->db-x` / `db-x->x` converters with small codec maps. Use `contains?` rather than `cond->` truthiness, so that writing nil to clear a field works.
- Timestamps: add one `ReadableColumn` extension and delete the manual `timestamp->iso-string` conversions.
- Make `q-one` / `q-many` public in `db/connection` and use them in `reports/core.clj` and `setup.clj`, which call raw `jdbc/execute!` about 15 times.

### 1.5 Inventory mutations (MED, about −50 LOC)
`adjust-quantity`, `coravin-pour` and `finish-open-bottle` each read the wine, insert an `inventory_history` row with the same six columns, then update `wines`.
- Extract `record-history!` and `close-open-bottle!`.
- Fixes B4.
- `adjust-quantity` doesn't guard against a negative quantity and returns the `update-count` map as its response.
- The `if-wine` guard adds an extra `wine-exists?` query to eight handlers.

### 1.6 Bulk jobs (HIGH value, low risk, about −70 LOC)
`start-drinking-window-job` and `start-wine-summary-job` are about 85% the same. Replace them with `run-wine-job! {:job-type :process-wine}`. Also:
- Job ids come from `currentTimeMillis`, which can collide; use a UUID.
- `active-jobs` is never cleaned up.
- `doall (map-indexed …)` is used only for its side effects.

### 1.7 Smaller items
- **Device handlers:** the device lookup and blocked-device check are each repeated three times. A bad claim code gets 401 in one place and 422 in another. Split `ingest-sensor-reading` into a guard and a flat `cond`. `merge-sensor-config!` swallows all exceptions.
- **`current-user` / `user-email` helper:** the code reaches into `(:user request)` in six different ways.
- **`reports/core.clj`:** the five `fetch-*` functions differ only in their where/order. The provider is hard-coded to `:anthropic`. The report is re-selected after saving instead of using `:returning`.
- **`sensor-reading-series`:** 84 lines of SQL built with `format`. Generate the metric columns from a vector.
- **Recipe-link merging:** move it out of `handlers.clj` (`merge-recipe-links` and its neighbours, about 90 lines) into a `recipes.links` namespace with unit tests.
- **Namespace aliases:** `handlers.clj` calls `bulk-operations` and `ai.core` by fully-qualified name even though aliases exist.

---

## 2. Frontend data layer (`api.cljs`, 1721 lines → ~1000)

### 2.1 One `request!` helper (HIGH, about −350–450 LOC)
About 60 functions repeat "set flag → `go` → `<!` → on success `swap!`, else `assoc :error`". The proposed helper:
```clojure
(request! app-state {:method :get :url "/api/spirits"
                     :loading [:requests :spirits]
                     :on-success #(assoc-in %1 [:bar :spirits] %2)
                     :error-msg "Failed to load spirits"})
```
It sets and clears the loading flag itself, writes errors to a single place, and always returns a Promise. Today return types are mixed: go channels, hand-rolled `js/Promise.` in 8 places even though `promise-from-channel` exists, callbacks, or nothing.

### 2.2 Resource table and collection helpers (HIGH, about −200 LOC)
The replace-by-id pattern appears 12 times, remove-by-id 7 times and prepend 4 times. Some use lazy `map`/`remove`, others `mapv`/`filterv`, so `:wines` sometimes holds a lazy seq. Add `replace-by-id`, `remove-by-id` and `upsert-by-id` that always return vectors. Define a `resources` map (`:spirit`, `:recipe`, `:grape-variety`, `:classification`, …) with generic `fetch!`/`create!`/`update!`/`delete!`, and keep the existing public functions as thin aliases so views don't change.

### 2.3 Error, loading and toast conventions (MED-HIGH)
- **Errors** land in `:error`, `[:bar :error]`, `[:chat :error]`, `[:blind-tastings :error]`, `[:sensor-readings :error]` or `:devices/approve-error`. Only the top-level `:error` is shown as a toast, so check whether the others appear anywhere.
- **Loading flags:** there are 15+ ad-hoc keys (`:submitting-note?`, `:analyzing-label?`, …), and some error branches forget to clear theirs.
- **Change:** add `notify!` for toasts and a `[:requests key]` status map managed by `request!`.
- **401 handling:** return `{:auth? true}` instead of the text match on "Authentication required" in `poll-job-status`, and guard the double-redirect.

### 2.4 Other duplication in `api.cljs`
- **Chat conversations:** the upsert + provider-sync + clear-error sequence is repeated four times. Rename, pin and set-provider are the same PUT; merge them into `patch-conversation!`. The two extract-recipe functions are about 90% the same.
- **Job polling:** `poll-job-status` is 112 lines; split it into a pure `next-poll-action` plus an effects dispatcher, and move the failure-test simulation into a dev namespace. `regenerate-filtered-drinking-windows` and `regenerate-filtered-wine-summaries` are 95% the same.
- **AI calls:** the four "analyze / suggest" functions should share one `ai-post!`. Add an `ai-provider` selector; it is read in 5+ places.
- **Wine-in-state updates:** there are four different semantics (replace, merge, select-keys merge, client-side arithmetic). Use one `update-wine-in-state`, and always merge the server response.
- **Misplaced code:** move navigation (`nav/replace-wines!`, `go-bar-recipe!`) and page lifecycle (`load-wine-detail-page`, `exit-wine-detail-page`) out of `api.cljs`. `/version.json` is fetched by two separate implementations.

### 2.5 App-state shape (MED)
- **Undeclared keys:** `initial-app-state` declares about 40 keys, but more than 40 others are added on the fly. `reset-database` resets to the initial state, which leaves those inconsistent.
- **Proposed grouping:** `:wine-detail {…}` (so `exit-wine-detail-page` becomes one `dissoc` instead of eight), `:forms {…}`, `:ui {…}`, `:requests {…}`. Move the `:devices/*` namespaced keys into a `:devices` map.
- **`filtered-sorted-wines`:** a full filter and sort that runs from seven call sites, up to three times per render, and takes the atom so it can't be memoized. Make it an `r/track` or reaction.
- **Legacy filter keys:** remove the singular/plural pairs `:style`/`:styles` and `:variety`/`:varieties`.

---

## 3. Frontend views

1. **Theme tokens + `tint-chip` (HIGH, about −250 LOC).**
   - About 50 raw rgba/hex literals in `bar/recipes.cljs` alone. The same chip style (light background tint, coloured text, coloured border) is rebuilt about 15 times across recipes, spirits, inventory and `wine_card`.
   - `#E8C3C8` is hard-coded more than 30 times; make it `primary.light`. Gold, green and amber should be palette tokens too.
   - There are two blue selection colors that don't fit the theme (`wines/list.cljs:60-73`, `wine_card.cljs:377`).
   - Move `filter-chip-sx` and `category-filter-bar` out of `spirits.cljs`; recipes currently imports them from there.
2. **One confirm dialog (HIGH, about −120 LOC):** 11 native `js/confirm` calls plus 3 near-identical custom delete dialogs (grape varieties, classifications, wine varieties). Add `confirm!` backed by a single `:confirm` slot rendered once in `main.cljs`.
3. **`form-dialog` + `ai-button` (HIGH):** about 20 dialogs repeat `open/onClose/maxWidth/fullWidth` with Save/Cancel and error state. The "Analyzing…" button with spinner is written out 7 times.
4. **Wine detail page: data-driven editable fields (HIGH, about −250–350 LOC):** 18 `editable-*` wrappers (`detail.cljs:93-345`) with copy-pasted number validators. Add `num-field {:label :min :max :unit :int?}` and a field table. Split the 1400-line file into `wines/detail/{fields,provenance,history,coravin}.cljs`.
5. **List-page scaffolding:**
   - Add `list-page`, `empty-state` and `loading-block`. The empty-state copy is written out 14 times and the spinners are hand-rolled.
   - Add `detail-actions` for the Delete / spacer / Done row.
   - Add `summary-card`: `spirit-card` and `recipe-card` are the same component.
   - Add a `join-meta` helper for the " · " join, which is written out in several card components.
6. **Bar entities:** pick one editing paradigm. Spirits edit field by field inline (`spirit-detail` is 180 lines); recipes use a full form.
7. **Dates:** use `today-iso` and the `formatting` helpers everywhere; `.toISOString` and `toLocaleDateString` are called inline. Add a `date-field` component.
8. **State placement:** filter selections are local atoms in bar but app-state in wines. Dialog open-flags are split between local atoms and app-state. Rule: filters live in app-state; short-lived dialog state stays local.
9. **Long components:**
   - `chat-dialog` is about 220 lines and calls `swap!` during render.
   - `admin-menu-items` is 120 lines; turn it into a data vector.
   - `recipe-display` is 132 lines; `filter-header` is 106.
   - `chat/actions.cljs` passes the same 5 positional args to every action; bundle them into a session map and add a `set-messages!` helper.
10. **CLAUDE.md card rules:** mostly followed. `recipe-card` shows tag chips and a "★ n" rating rather than dot-separated text.

---

## 4. Validation and SQL model

`common.cljc` already holds most enums. The gaps are numeric ranges, rules that only one layer enforces, and migrations.

1. **Range rules in one place (HIGH, low risk).**
   - **Vintage:** 1800..current year in `utils/vintage.cljs`; 1900..2100 in the blind-tasting form; no range in the spec or the DB.
   - **Ratings:** the 1–100 rule is written in four places.
   - **No lower bound:** quantity and price accept negatives in the spec and the DB.
   - **Change:** add `valid-vintage?`, `rating-range`, `recipe-rating-range` and `valid-tasting-window?` to `common.cljc`. Use them from the specs and the frontend, and inline them into the `schema.clj` CHECKs, the same way `wine_style` already uses `common/wine-styles`. `common/valid-location?` already works this way and is the pattern to copy.
2. **Enums the backend doesn't enforce (MED-HIGH).**
   - **Spirit category:** the spec accepts any string; separate it from the bar-inventory categories, which are a different vocabulary and appear only in the seed SQL.
   - **Inventory `reason`:** strings like `"drunk"` and `"coravin_pour"` are written out across `db/api.clj`.
   - **`appellation_tier`:** not enum-checked, while `designation` is.
   - **`wset_data`:** not validated against `wset-lexicon`.
   - **New wine styles:** `wine_style` is a Postgres enum created `IF NOT EXISTS`, so a style added to `common` passes the spec but fails on an existing DB. Use `varchar` + a generated CHECK, or loop `ALTER TYPE … ADD VALUE IF NOT EXISTS`.
3. **Specs duplicate the column lists.** Move them into `wine-cellar.specs`. Add a test that every column is either covered by a spec or listed as excluded. Longer term, use a malli schema in cljc so the frontend forms validate with the same schema.
4. **Migrations.** `ensure-tables` reruns about 15 raw `ALTER`/`DO $$` blocks on every boot.
   - Adding a column to `schema.clj` does nothing on an existing DB.
   - The recipe-rating CHECK is dropped and re-added on every start.
   - The `DROP VIEW` runs twice.
   - **Change:** adopt `migratus` or `ragtime` (or a `schema_migrations` table) for new changes from now on.
5. **Schema hygiene.**
   - **Missing indexes:** `tasting_notes(wine_id)` matters most, since the `enriched_wines` view runs a correlated subquery per wine. Also `inventory_history(wine_id)` and `ai_conversation_messages(conversation_id)`.
   - **Column types:** `timestamp` vs `timestamptz` and `decimal` vs `numeric` are mixed.
   - **No CHECK** on `devices.status` or `ai_conversations.provider`.
   - **Duplicated sensor data:** `sensor_readings.temperatures` jsonb duplicates the `sensor_temperatures` table.
   - **Unlinked ids:** recipe ingredients point to spirits and inventory items by id inside JSON, with no FK.
6. **Stale docs:** `schema-unification-datomic.md` and `ideal-taxonomy.md`.

---

## 5. AI layer

1. **Replace the `case provider` dispatch with a table of tasks (HIGH, about −250–300 LOC).**
   - `ai/core.clj` has seven `case provider` blocks, and each provider namespace repeats about seven functions per feature.
   - **Change:** shrink each provider to a single `complete!` (multimethod on provider), and describe each task once as data: `{:prompt-fn :schema :max-tokens {:default n :gemini m} :tier :providers}`.
   - Validate `provider` once and return 400 when it's unknown; today an unknown provider throws `IllegalArgumentException`.
2. **Define each output schema once (HIGH, about −150 LOC, fixes drift).**
   - The wine-label, spirit-label and drinking-window schemas are each written three times, once per provider, and the copies have drifted. Gemini has no `required`, hence the hack that strips the string `"null"`. OpenAI dropped the field descriptions.
   - The spirit category list is hard-coded three times even though `common/spirit-categories` exists.
   - **Change:** one neutral spec, rendered by `->json-schema` and `->gemini-schema`.
3. **Shared HTTP layer:**
   - **Error shapes differ:** OpenAI passes the upstream 429/401 straight through to the browser.
   - **Unhandled stop reasons:** only Anthropic checks for truncation and refusal; OpenAI `incomplete` and Gemini `finishReason` are not handled.
   - **Timeouts** are 300s, 60s and 180s.
   - **No retries** anywhere.
   - **Change:** one `post-json!` that throws a uniform `ex-info` and retries once on 429/5xx, plus a shared `parse-model-json`.
4. **Neutral image format:** prompts currently build Anthropic-shaped blocks, which OpenAI and Gemini then convert back. Use a neutral `{:media-type :data}`; this also fixes B8.
5. **Config and capabilities:**
   - Merge the 6+ `defstate`s for models and keys into one `ai-config`.
   - Have `get-model-info` return a capabilities map so the frontend stops checking `(= :anthropic provider)`.
   - Turn the silent gaps into explicit 400s: cocktail extraction is Anthropic-only and returns nil elsewhere.
6. **Prompts:** generate field bullet lists from the same spec. Drop the "return ONLY JSON" wording, since every provider now enforces a schema. Prompt changes are higher risk, so do this last.

---

## 6. Suggested order

1. **Correctness bugs (small, high value):** B4 (restock), B5 (error messages), B6 (undefined specs), B7 (drop-tables), B8 (image MIME), B10 (recipe rating), B9 (check the DDL), and B2 (JWT seconds, one line). With two users in Google OAuth Testing mode, the auth items (B1, B3) are housekeeping, not urgent.
2. **Backend plumbing:** error middleware + response helpers (§1.1) → shared route `:responses` (§1.3) → CRUD helpers (§1.2) → bulk-job merge (§1.6).
3. **Frontend plumbing:** `request!` + collection helpers (§2.1–2.2), migrating the bar, grape-variety and classification functions first. Then error/toast conventions (§2.3) and the `filtered-sorted-wines` reaction.
4. **Shared UI components:** theme tokens + `tint-chip`, `confirm!`, `form-dialog`, `ai-button`, `list-page`/`empty-state`, then the field table and file split for the wine detail page.
5. **Single source of truth for rules:** range predicates in `common.cljc` → enum specs → specs namespace → migration tool + indexes.
6. **AI layer:** schemas → task table → HTTP layer → config/capabilities → prompt generation.

Rough total, if everything is done: about 3,000 fewer lines out of ~26k.
