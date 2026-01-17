# Refactoring Plan: `aoc` -> `appellation`

## Objective
Rename the `aoc` field to `appellation` across the full stack to better represent global wine regions (AVA, DOC, etc.) and prepare for the `appellation_tier` addition.

## Hierarchy Context
1.  **`appellation`** (Target): The specific named place (e.g., "Pauillac", "Napa Valley"). formerly `aoc`.
2.  **`appellation_tier`** (Future): The regulatory level (e.g., "AOC", "AVA").
3.  **`classification`**: The rank of the estate/site (e.g., "Grand Cru").
4.  **`designation`**: The process/aging (e.g., "Riserva").

## Execution Plan

### Phase 1: Database Schema & Migration
- [x] Update `src/clj/wine_cellar/db/schema.clj`:
    - Rename `:aoc` column to `:appellation` in `wines` table.
    - Rename `:aoc` column to `:appellation` in `wine_classifications` table.
    - Add migration definitions (`migrate-wines-aoc-to-appellation`, `migrate-classifications-aoc-to-appellation`).
- [x] Update `src/clj/wine_cellar/db/setup.clj`:
    - Add migration execution steps to `ensure-tables`.

### Phase 2: Shared Specs & Logic
- [x] Update `src/clj/wine_cellar/routes.clj`:
    - Rename `::aoc` spec to `::appellation`.
    - Update all composite specs (`create-wine-spec`, `classification-spec`, etc.).

### Phase 3: Backend Logic (API & DB Access)
- [x] Update `src/clj/wine_cellar/db/api.clj`:
    - Rename `:aoc` to `:appellation` in queries and helper functions.
- [x] Update `src/clj/wine_cellar/handlers.clj`:
    - Update extraction from request parameters.
    - Update `create-wine` classification construction logic.

### Phase 4: AI & Automation
- [x] Update `src/clj/wine_cellar/ai/prompts.clj`:
    - Rename "aoc" field in JSON schemas and descriptions.
- [x] Update `src/clj/wine_cellar/ai/gemini.clj`, `anthropic.clj`, `openai.clj`:
    - Update tool definitions/function signatures if they explicitly name `aoc`.

### Phase 5: Frontend UI & State
- [x] Update `src/cljs/wine_cellar/views/wines/form.cljs`:
    - Rename form field key.
    - Update label (e.g., "Appellation" instead of "AOC").
- [x] Update `src/cljs/wine_cellar/views/wines/detail.cljs`:
    - Update display logic.
- [x] Update `src/cljs/wine_cellar/views/components.cljs`:
    - Update any shared input components using this key.
- [x] Update `src/cljs/wine_cellar/utils/formatting.cljs`:
    - Update string formatting helpers.

## Progress Log
- **[DATE]**: Plan created.
- **[DATE]**: Phase 1, 2, 3, 5 complete.
- **[DATE]**: Phase 4 complete. Codebase fully refactored.
