# Refactor: Add Appellation Tier

**Goal**: Add `appellation_tier` to the wine data model to capture regulatory status (e.g., AOC, DOCG, AVA) distinct from the specific place name (`appellation`) and estate ranking (`classification`).

## Status
- [x] **Phase 1: Shared Constants**
    - [x] Add `appellation_tier` description to `src/cljc/wine_cellar/common.cljc`.

- [x] **Phase 2: Database**
    - [x] Update `wines` table schema in `src/clj/wine_cellar/db/schema.clj`.
    - [x] Define migration `wines-add-appellation-tier-column`.
    - [x] Update `enriched-wines-view-schema` to include `appellation_tier` (Already covered by `w.*`).
    - [x] Update `src/clj/wine_cellar/db/setup.clj` to execute migration.

- [x] **Phase 3: Backend**
    - [x] Update `src/clj/wine_cellar/db/api.clj`:
        - [x] Update `wine->db-wine` conversion (Auto-handled).
        - [x] Update `db-wine->wine` conversion (Auto-handled).
        - [x] Update `wine-list-fields` to include `appellation_tier`.
    - [x] Update `src/clj/wine_cellar/handlers.clj` (Auto-handled by passthrough).
    - [x] Update `src/clj/wine_cellar/routes.clj` specs to include `::appellation_tier`.

- [x] **Phase 4: AI Integration**
    - [x] Update `src/clj/wine_cellar/ai/prompts.clj`:
        - [x] Add `appellation_tier` to system prompt.
    - [x] Update tool schemas in:
        - [x] `src/clj/wine_cellar/ai/anthropic.clj`
        - [x] `src/clj/wine_cellar/ai/gemini.clj`
        - [x] `src/clj/wine_cellar/ai/openai.clj`

- [x] **Phase 5: Frontend**
    - [x] Update `src/cljs/wine_cellar/views/wines/form.cljs`:
        - [x] Add `appellation_tier` field.
    - [x] Update `src/cljs/wine_cellar/views/wines/detail.cljs`:
        - [x] Add `editable-appellation-tier` component.
        - [x] Display the field.
    - [x] Update `src/cljs/wine_cellar/views/components/classification_fields.cljs` (Skipped for now).
