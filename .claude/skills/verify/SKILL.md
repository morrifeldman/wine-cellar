---
name: verify
description: Drive the running wine-cellar app with Playwright to verify frontend changes end-to-end.
---

# Verifying wine-cellar changes

## Launch
Dev stack runs in tmux session `wine-dev` (backend :3000, shadow-cljs :8080). Health-check with `tmux ls` + curl before starting anything; `scripts/start-dev.sh` only if down. Never run shadow-cljs compile commands — check `.shadow-cljs/build.log` for compile status (watcher picks up edits in ~1s).

## Drive
Write a plain Node script (scratchpad for one-offs, `dev/` if worth keeping) using the shared helpers:

```js
const { makePageWithAuth, gotoApp } = require('/home/morri/wine-cellar/dev/test_helpers.js');
const { browser, page } = await makePageWithAuth({ headless: true }); // Crostini flags included
await gotoApp(page, '/bar'); // app served from :8080; auth cookie injected via backend REPL JWT
```

- API is cross-origin at `localhost:3000`; from page context use `fetch('http://localhost:3000/api/...', { credentials: 'include' })` (e.g. to clean up test data).
- White-box state read/write via `page.evaluate` on `wine_cellar.core.app_state` with `cljs.core.get_in`/`swap_BANG_`/`cljs.core.keyword` (dev build, no name munging surprises). Useful for entering states with no easy UI path (e.g. `[:bar :editing-recipe-id]`, `[:chat :save-recipe]`).
- MUI icons here have NO `data-testid` — select by label (`page.getByLabel`), role, or structural CSS (e.g. `.MuiTabs-root + button` for the bar-page add button, `form .MuiIconButton-colorError` for row-delete buttons).
- `pressSequentially` puts the caret where the click landed — press `Control+End` first when appending to a prefilled field.
- Creating/deleting records hits the real local dev DB; name test records distinctively (`ZZ ...`) and DELETE them at the end.

## Gotchas
- `makePageWithAuth` already wires console/pageerror logging — watch for `CONSOLE ERROR:` lines.
- `form-field-style` in `views/components.cljs` sets `:width "75%"` and wins over `:fullWidth` — screenshot layout changes, don't assume.
