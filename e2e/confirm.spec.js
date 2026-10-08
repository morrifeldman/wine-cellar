const { test, expect } = require('@playwright/test');
const { getAuthToken } = require('../dev/test_helpers.js');

// The app asks before deleting through one in-app dialog (confirm!), not the
// browser's js/confirm.

test.beforeEach(async ({ context }) => {
  await context.addCookies([{
    name: 'auth-token', value: getAuthToken(), domain: 'localhost', path: '/',
  }]);
});

const api = 'http://localhost:3000/api';

test('deleting a spirit asks first, and Cancel keeps it', async ({ page }) => {
  page.on('dialog', d => { throw new Error(`native dialog: ${d.message()}`); });
  await page.goto('/');
  const spirit = await page.evaluate(async (api) => {
    const r = await fetch(`${api}/spirits`, {
      method: 'POST', credentials: 'include',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ name: 'ZZ Confirm Gin', category: 'gin' }),
    });
    return r.json();
  }, api);

  await page.goto(`/bar/spirits/${spirit.id}`);
  await page.waitForLoadState('networkidle');
  const confirm = page.getByRole('dialog').filter({ hasText: 'Delete ZZ Confirm Gin?' });

  await page.getByRole('button', { name: 'Delete', exact: true }).click();
  await expect(confirm).toBeVisible();
  await confirm.getByRole('button', { name: 'Cancel' }).click();
  await expect(confirm).toBeHidden();
  const stillThere = await page.evaluate(async ([api, id]) =>
    (await fetch(`${api}/spirits/${id}`, { credentials: 'include' })).status, [api, spirit.id]);
  expect(stillThere).toBe(200);

  await page.getByRole('button', { name: 'Delete', exact: true }).click();
  await confirm.getByRole('button', { name: 'Delete', exact: true }).click();
  await expect(confirm).toBeHidden();
  await expect.poll(() => page.evaluate(async ([api, id]) =>
    (await fetch(`${api}/spirits/${id}`, { credentials: 'include' })).status, [api, spirit.id]))
    .toBe(404);
});
