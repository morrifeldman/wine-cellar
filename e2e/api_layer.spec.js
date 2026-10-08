const { test, expect } = require('@playwright/test');
const { getAuthToken } = require('../dev/test_helpers.js');

// Drives wine_cellar.api functions in the page (white-box) and checks what
// they leave in app-state, so a change to the request layer is caught
// without clicking through every screen. Records are named "ZZ ..." and
// deleted again.

test.beforeEach(async ({ context, page }) => {
  await context.addCookies([{
    name: 'auth-token', value: getAuthToken(), domain: 'localhost', path: '/',
  }]);
  page.on('pageerror', e => { throw new Error(`page error: ${e.message}`); });
  await page.goto('/');
  await page.waitForLoadState('networkidle');
});

// Helpers installed in the page: kw, m (JS -> CLJS map), js (CLJS -> JS),
// get(path), call(fnName, ...args) awaiting the returned promise if any.
async function install(page) {
  await page.evaluate(() => {
    const c = cljs.core;
    window.t = {
      kw: k => c.keyword(k),
      m: o => c.js__GT_clj(o, c.keyword('keywordize-keys'), true),
      js: x => c.clj__GT_js(x),
      st: wine_cellar.core.app_state,
      get: path => c.clj__GT_js(c.get_in(c.deref(wine_cellar.core.app_state),
                                         c.vec(path.map(k => typeof k === 'string' ? c.keyword(k) : k)))),
      call: async (fn, ...args) => {
        const r = wine_cellar.api[fn](wine_cellar.core.app_state, ...args);
        if (r && typeof r.then === 'function') {
          try { return c.clj__GT_js(await r); } catch (e) { return { rejected: e }; }
        }
        return r;
      },
      settle: () => new Promise(r => setTimeout(r, 400)),
    };
  });
}

test('collections stay in sync with the server', async ({ page }) => {
  await install(page);
  const out = await page.evaluate(async () => {
    const r = {};
    // grape varieties
    const gv = await t.call('create_grape_variety', t.m({ variety_name: 'ZZ Grape' }));
    await t.settle();
    r.gvListed = t.get(['grape-varieties']).some(v => v.id === gv.id);
    await t.call('delete_grape_variety', gv.id);
    r.gvGone = !t.get(['grape-varieties']).some(v => v.id === gv.id);
    // spirits
    const sp = await t.call('create_spirit', t.m({ name: 'ZZ Gin', category: 'gin' }));
    r.spFirst = t.get(['bar', 'spirits'])[0].id === sp.id;
    await t.call('update_spirit', sp.id, t.m({ name: 'ZZ Gin 2' }));
    r.spRenamed = t.get(['bar', 'spirits']).find(s => s.id === sp.id).name;
    await t.call('delete_spirit', sp.id);
    r.spGone = !t.get(['bar', 'spirits']).some(s => s.id === sp.id);
    // bar inventory
    await t.call('create_bar_inventory_item', t.m({ name: 'ZZ Lime', category: 'juice' }));
    await t.settle();
    const item = t.get(['bar', 'inventory-items']).find(i => i.name === 'ZZ Lime');
    await t.call('toggle_bar_inventory_item', item.id, false);
    r.itemToggled = t.get(['bar', 'inventory-items']).find(i => i.id === item.id).have_it;
    await t.call('delete_bar_inventory_item', item.id);
    r.itemGone = !t.get(['bar', 'inventory-items']).some(i => i.id === item.id);
    // classifications
    await t.call('create_classification', t.m({ country: 'ZZ Land', region: 'ZZ Vale' }));
    await t.settle();
    const cl = t.get(['classifications']).find(c => c.country === 'ZZ Land');
    r.clListed = !!cl;
    await t.call('delete_classification', cl.id);
    await t.settle();
    r.clGone = !t.get(['classifications']).some(c => c.country === 'ZZ Land');
    r.error = t.get(['error']);
    return r;
  });
  expect(out).toEqual({
    gvListed: true, gvGone: true, spFirst: true, spRenamed: 'ZZ Gin 2',
    spGone: true, itemToggled: false, itemGone: true, clListed: true,
    clGone: true, error: null,
  });
});

test('wine edits, notes and stock changes update the wine in place', async ({ page }) => {
  await install(page);
  const out = await page.evaluate(async () => {
    const r = {};
    const wine = t.get(['wines']).find(w => w.quantity >= 1);
    const id = wine.id;
    await t.call('update_wine', id, t.m({ location: 'Z1' }));
    r.location = t.get(['wines']).find(w => w.id === id).location;
    await t.call('update_wine', id, t.m({ location: wine.location }));
    await t.call('adjust_wine_quantity', id, 1, t.m({ reason: 'restock' }));
    r.restocked = t.get(['wines']).find(w => w.id === id).quantity - wine.quantity;
    await t.call('adjust_wine_quantity', id, -1, t.m({ reason: 'correction' }));
    await t.settle();
    r.historyLoaded = Array.isArray(t.get(['inventory-history', id]));
    // tasting notes
    await t.call('fetch_tasting_notes', id);
    const note = await t.call('create_tasting_note', id, t.m({ notes: 'ZZ note', rating: 90 }), null);
    r.noteAdded = t.get(['tasting-notes']).some(n => n.id === note.id);
    await t.call('update_tasting_note', id, note.id, t.m({ notes: 'ZZ note 2' }));
    r.noteText = t.get(['tasting-notes']).find(n => n.id === note.id).notes;
    await t.call('delete_tasting_note', id, note.id);
    r.noteGone = !t.get(['tasting-notes']).some(n => n.id === note.id);
    r.error = t.get(['error']);
    return r;
  });
  expect(out).toEqual({
    location: 'Z1', restocked: 1, historyLoaded: true, noteAdded: true,
    noteText: 'ZZ note 2', noteGone: true, error: null,
  });
});

test('a failed request shows its message on the banner', async ({ page }) => {
  await install(page);
  const out = await page.evaluate(async () => {
    const res = await t.call('delete_spirit', 999999);
    return { rejected: res && res.rejected, banner: t.get(['error']) };
  });
  expect(out).toEqual({ rejected: 'Spirit not found', banner: 'Spirit not found' });
  await expect(page.getByText('Spirit not found')).toBeVisible();
});
