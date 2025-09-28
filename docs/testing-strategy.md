# Testing Strategy for Summary & Chat Context

## Goals
- Ensure the shared cellar summary (`summary.cljc`) produces correct aggregates across edge cases.
- Verify backend chat handler respects the "summary only" default and includes wines only when users opt in.
- Cover frontend state defaults and UI wiring so the toggle behaves as expected.
- Provide optional end-to-end coverage using browser automation (Playwright MCP) to sanity check the full flow.
- Keep the seed script representative and guard against accidental regressions in sample data used for manual validation.

## Scope & Test Types
1. **Pure Function Unit Tests (CLJC)**
   - Target: `wine-cellar.summary/collection-stats`, `condensed-summary`, `optimal-window-timeline`, etc.
   - Location: `test/cljc/wine_cellar/summary_test.cljc`.
   - Approach:
     - Build lightweight fixtures covering: in-stock vs out-of-stock, `:original_quantity`, missing `:latest_internal_rating`, `nil` prices, no varieties, future/past/no drinking windows, ready-to-drink breakdown, vintage bands, price bands.
     - Assert both total metrics (counts, bottles, value, ratings) and categorical breakdown contents.
     - Include regression tests for `optimal-window-timeline` to ensure group-by handling remains intact.

2. **Backend Handler Tests (CLJ)**
   - Target: `wine-cellar.handlers/chat-with-ai` payload shaping.
   - Location: `test/clj/wine_cellar/handlers/chat_test.clj`.
   - Approach:
     - Use `with-redefs` to stub `ai/chat-about-wines`.
     - Verify default requests omit `:wine-ids` / selected wines, while `:include-visible-wines? true` adds them.
     - Assert condensed summary is always present.

3. **Frontend State/Logic Tests (CLJS)**
   - Target: Chat state defaults, `context-wines`, toggle behavior.
   - Location: `test/cljs/wine_cellar/views/wine_chat_test.cljs` (Node/Shadow target).
   - Approach:
     - Instantiate app state atoms; confirm default `:include-visible-wines?` is `false`.
     - Assert context label renders “Summary only” when toggle is off.
     - Toggle state and confirm context sync functions pass wine IDs.

4. **End-to-End / Integration (Optional)**
   - Tool: Playwright MCP script (`scripts/playwright/chat-context.spec.ts`).
   - Coverage:
     - Load seeded app, verify toggle default off.
     - Intercept `/api/chat` to confirm payload lacks `wine-ids` initially and includes them after enabling.
     - Validate summary text appears in first message.

5. **Seed Script Regression Guard (CLJ)**
   - Add `test/clj/wine_cellar/dev/seed_sample_wines_test.clj`.
   - Strategy:
     - Run `seed!` inside a transaction/temporary DB (mock or transactional rollback).
     - Assert resulting wines cover every status bucket (ready, hold, past, none) and at least one wine lacks ratings/prices.

## Execution
- **Alias updates**: add a `:test` alias in `deps.edn` if missing for JVM tests; configure Shadow `:test` build for CLJS.
- **Commands**:
  - JVM: `clj -M:test` (after adding aliases).
  - CLJS: `npx shadow-cljs compile test` or `watch test`.
  - E2E: `npm run test:e2e` (define script invoking Playwright MCP).
- Integrate both JVM and CLJS test commands into CI workflow.

## Next Steps Checklist
1. Create test fixtures module shared across test namespaces (`test/cljc/wine_cellar/fixtures.cljc`).
2. Implement `summary_test.cljc` with assertions for totals/breakdowns.
3. Write backend handler tests stubbing AI client.
4. Add CLJS state test verifying toggle and context wiring.
5. Update `deps.edn` and Shadow config for test aliases/builds.
6. Script optional Playwright test if desired.
7. Add regression test for `seed!` to ensure sample catalog stays representative.
