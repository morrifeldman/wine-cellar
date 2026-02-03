const { test, expect } = require('@playwright/test');

test('Blind Tasting Flow with WSET Display', async ({ page, context }) => {
  const authToken = process.env.TEST_AUTH_TOKEN;
  if (!authToken) {
    throw new Error('TEST_AUTH_TOKEN environment variable is not set');
  }

  // Inject auth token
  await context.addCookies([{
    name: 'auth-token',
    value: authToken,
    domain: 'localhost',
    path: '/',
    httpOnly: true,
    secure: false,
    sameSite: 'Lax'
  }]);

  // Navigate to app
  await page.goto('http://localhost:8080');
  
  // Wait for app to load (look for the main action button)
  await page.waitForSelector('text=Add New Wine');

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
  await page.getByRole('dialog').getByText('CLEAR', { exact: true }).click();
  
  // Add unique observation
  const uniqueId = `Test Run ${Date.now()}`;
  await page.getByRole('dialog').getByLabel('Other Observations').first().fill(uniqueId);

  // Expand NOSE section
  // Find the grid container that has "NOSE" and click the button inside it
  await page.locator('.MuiGrid-container').filter({ hasText: /^NOSE$/ }).getByRole('button').click();

  // WSET Nose - Condition: CLEAN
  await page.getByRole('dialog').getByText('CLEAN', { exact: true }).click();

  // Expand CONCLUSIONS section
  await page.locator('.MuiGrid-container').filter({ hasText: /^CONCLUSIONS$/ }).getByRole('button').click();

  // WSET Conclusions - Quality: OUTSTANDING
  await page.getByRole('dialog').getByText('OUTSTANDING', { exact: true }).click();
  
  // Save
  await page.getByRole('button', { name: 'Save Blind Tasting' }).click();

  // Wait for dialog to close
  await expect(page.getByRole('heading', { name: 'New Blind Tasting' })).not.toBeVisible();

  // Find the card with our unique ID
  const card = page.locator('.MuiPaper-root').filter({ hasText: uniqueId });
  await expect(card).toBeVisible();

  // Verify it appears in the list (scoped to card)
  await expect(card.getByText('Rating: 95/100')).toBeVisible();
  
  // Verify WSET Display (scoped to card)
  await expect(card.getByText('Quality: OUTSTANDING')).toBeVisible();
  
  // "WSET Structured Tasting" header
  await expect(card.getByText('WSET Structured Tasting')).toBeVisible();

  // Verify Appearance "CLEAR"
  await expect(card.getByText('CLEAR', { exact: true })).toBeVisible();
});
