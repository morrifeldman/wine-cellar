const { test, expect } = require('@playwright/test');

const { getAuthToken } = require('../dev/test_helpers.js');

// WSET sections are collapsed; the toggle is the button beside the heading.
async function expandSection(page, name) {
  await page.getByRole('heading', { name, exact: true })
    .locator('xpath=ancestor::*[.//button][1]').getByRole('button').first().click();
}

test('Blind Tasting Flow with WSET Display', async ({ page, context }) => {
  // Mints a JWT through the backend REPL, like the dev helpers do.
  await context.addCookies([{
    name: 'auth-token',
    value: getAuthToken(),
    domain: 'localhost',
    path: '/',
    httpOnly: true,
    secure: false,
    sameSite: 'Lax'
  }]);

  await page.goto('/');
  await page.getByRole('button', { name: 'Blind', exact: true }).waitFor();

  // Click "Blind" button to go to blind tastings
  await page.getByRole('button', { name: 'Blind', exact: true }).click();

  // Verify we are on Blind Tastings page
  await expect(page.getByRole('heading', { name: 'Blind Tastings' })).toBeVisible();

  // Click "New Blind Tasting"
  await page.getByRole('button', { name: 'New Blind Tasting' }).click();

  // Wait for dialog
  await expect(page.getByRole('heading', { name: 'New Blind Tasting' })).toBeVisible();

  // Fill the form
  // Rating
  await page.getByLabel('Rating (1-100)').fill('95');
  
  // Select Wine Style: Red
  await page.getByLabel('Wine Style (for Palate/Color)').click();
  await page.getByRole('option', { name: 'Red', exact: true }).click();
  
  // WSET Appearance - Select Clarity: CLEAR
  await page.getByRole('dialog').getByRole('radio', { name: 'Clear', exact: true }).click();
  
  // Add unique observation
  const uniqueId = `Test Run ${Date.now()}`;
  await page.getByRole('dialog').getByLabel('Other Observations').first().fill(uniqueId);

  // Expand NOSE section
  await expandSection(page, 'NOSE');

  // WSET Nose - Condition: CLEAN
  await page.getByRole('dialog').getByRole('radio', { name: 'Clean', exact: true }).click();

  // Expand CONCLUSIONS section
  await expandSection(page, 'CONCLUSIONS');

  // WSET Conclusions - Quality: OUTSTANDING
  await page.getByRole('dialog').getByRole('radio', { name: 'Outstanding', exact: true }).click();
  
  // Guessed Country - Autocomplete
  // Type 'Fra' and wait for suggestion 'France' if available, or just verify free solo accepts it.
  // Note: Test environment might not have seeded classifications, so free solo is safer unless we seed.
  // Assuming free solo works even without suggestions.
  await page.getByLabel('Guessed Country').fill('France');
  // If suggestions appear, we could click one, but free solo accepts the text.
  // Let's try to click the body to blur and set the value if needed
  await page.getByRole('dialog').click();

  // Guessed Region - Autocomplete
  await page.getByLabel('Guessed Region').fill('Bordeaux');
  await page.getByRole('dialog').click();

  // Guessed Vintage - Number Field
  await page.getByLabel('Guessed Vintage').fill('2019');

  // Save
  await page.getByRole('button', { name: 'Save Blind Tasting' }).click();

  // Wait for dialog to close
  await expect(page.getByRole('heading', { name: 'New Blind Tasting' })).not.toBeVisible();

  // Find the card with our unique ID
  const card = page.locator('.MuiPaper-root').filter({ hasText: uniqueId });
  await expect(card).toBeVisible();

  // Verify it appears in the list (scoped to card)
  await expect(card.getByText('Rating: 95/100')).toBeVisible();

  // Verify the WSET summary (scoped to card)
  await expect(card.getByRole('heading', { name: 'Appearance' })).toBeVisible();
  await expect(card.getByText('Clear', { exact: true })).toBeVisible();
  await expect(card.getByText('Clean', { exact: true })).toBeVisible();
  await expect(card.getByText('Quality: Outstanding')).toBeVisible();

  // Clean up: this runs against the dev database. The note route ignores the
  // wine id, and blind notes have none, so any number will do.
  const deleted = await page.evaluate(async (marker) => {
    const api = 'http://localhost:3000/api';
    const notes = await fetch(`${api}/blind-tastings`, { credentials: 'include' })
      .then(r => r.json());
    const ours = notes.filter(n => JSON.stringify(n).includes(marker));
    for (const n of ours) {
      await fetch(`${api}/wines/by-id/0/tasting-notes/${n.id}`,
        { method: 'DELETE', credentials: 'include' });
    }
    return ours.length;
  }, uniqueId);
  expect(deleted).toBe(1);
});
