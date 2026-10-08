const { test, expect } = require('@playwright/test');
const { getAuthToken } = require('../dev/test_helpers.js');

// Loads every page and fails on any API error or uncaught page error, so a
// refactor that breaks a request shape shows up without a dedicated test.
// /insights is left out: it asks the AI for a report.
const pages = ['/', '/add-wine', '/grape-varieties', '/classifications',
  '/sensors', '/devices', '/blind-tastings', '/admin/sql', '/bar',
  '/bar/recipes', '/bar/spirits', '/bar/inventory'];

test.beforeEach(async ({ context }) => {
  await context.addCookies([{
    name: 'auth-token', value: getAuthToken(), domain: 'localhost', path: '/',
  }]);
});

function watchErrors(page) {
  const errors = [];
  page.on('response', r => {
    if (r.url().includes('/api/') && r.status() >= 400) {
      errors.push(`${r.status()} ${r.request().method()} ${r.url()}`);
    }
  });
  page.on('pageerror', e => errors.push(`page error: ${e.message}`));
  return errors;
}

for (const path of pages) {
  test(`loads ${path}`, async ({ page }) => {
    const errors = watchErrors(page);
    await page.goto(path);
    await page.waitForLoadState('networkidle');
    expect(errors).toEqual([]);
  });
}

test('loads a wine detail page', async ({ page }) => {
  const errors = watchErrors(page);
  await page.goto('/');
  await page.waitForLoadState('networkidle');
  const ids = await page.evaluate(() =>
    fetch('http://localhost:3000/api/wines/list', { credentials: 'include' })
      .then(r => r.json()).then(ws => ws.map(w => w.id)));
  test.skip(ids.length === 0, 'no wines in the dev database');
  await page.goto(`/wine/${ids[0]}`);
  await page.waitForLoadState('networkidle');
  expect(errors).toEqual([]);
});
