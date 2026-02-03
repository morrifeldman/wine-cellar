const { chromium } = require('playwright');

async function testBlindTastings() {
  const browser = await chromium.launch({ headless: false });
  const context = await browser.newContext({
    viewport: { width: 1280, height: 800 },
    hasTouch: false
  });

  // Inject auth token
  await context.addCookies([{
    name: 'auth-token',
    value: 'eyJhbGciOiJIUzI1NiJ9.eyJlbWFpbCI6InRlc3RAZXhhbXBsZS5jb20iLCJuYW1lIjoiVGVzdCBVc2VyIiwiaWF0IjoxNzcwMDgwMTIxMjUyLCJleHAiOjE3NzA2ODQ5MjEyNTJ9.IhlRLlMYURD5dQF_-2pO0wkDfOMZNZaW1Pmrf6wwdD4',
    domain: 'localhost',
    path: '/'
  }]);

  const page = await context.newPage();

  // Capture console errors
  page.on('console', msg => {
    if (msg.type() === 'error') {
      console.log('CONSOLE ERROR:', msg.text());
    }
  });

  page.on('pageerror', err => {
    console.log('PAGE ERROR:', err.message);
  });

  // Override maxTouchPoints to force desktop mode
  await page.addInitScript(() => {
    Object.defineProperty(navigator, 'maxTouchPoints', {
      get: () => 0
    });
  });

  console.log('Navigating to app...');
  await page.goto('http://localhost:8080');
  await page.waitForLoadState('networkidle');
  await page.waitForTimeout(1000);

  // Click Blind button
  console.log('Clicking Blind button...');
  await page.locator('button:has-text("Blind")').click();
  await page.waitForTimeout(1000);

  // Click New Blind Tasting
  console.log('Clicking New Blind Tasting...');
  await page.locator('button:has-text("New Blind Tasting")').click();
  await page.waitForTimeout(1000);

  // Scroll down in the dialog to find the Guesses section
  const dialogContent = page.locator('.MuiDialogContent-root');
  await dialogContent.evaluate(el => el.scrollTop = el.scrollHeight);
  await page.waitForTimeout(500);
  await page.screenshot({ path: '/tmp/01-form-scrolled.png' });
  console.log('Screenshot: /tmp/01-form-scrolled.png');

  // Find and click the Guessed Varieties autocomplete
  console.log('Looking for Guessed Varieties field...');
  const varietyInput = page.locator('input').filter({ hasText: '' }).locator('xpath=ancestor::div[contains(@class, "MuiAutocomplete")]//input').first();

  // Try finding by label
  const varietyField = page.locator('label:has-text("Guessed Varieties")').locator('xpath=following::input[1]');

  if (await varietyField.count() > 0) {
    console.log('Found variety field, clicking...');
    await varietyField.click();
    await page.waitForTimeout(500);
    await page.screenshot({ path: '/tmp/02-variety-clicked.png' });
    console.log('Screenshot: /tmp/02-variety-clicked.png');

    // Type a variety name
    console.log('Typing variety name...');
    await varietyField.fill('Pinot');
    await page.waitForTimeout(1000);
    await page.screenshot({ path: '/tmp/03-variety-typed.png' });
    console.log('Screenshot: /tmp/03-variety-typed.png');

    // Try to select from dropdown
    const option = page.locator('li:has-text("Pinot")').first();
    if (await option.count() > 0) {
      console.log('Selecting option...');
      await option.click();
      await page.waitForTimeout(1000);
      await page.screenshot({ path: '/tmp/04-variety-selected.png' });
      console.log('Screenshot: /tmp/04-variety-selected.png');
    } else {
      console.log('No dropdown options found, pressing Enter...');
      await varietyField.press('Enter');
      await page.waitForTimeout(1000);
      await page.screenshot({ path: '/tmp/04-variety-entered.png' });
      console.log('Screenshot: /tmp/04-variety-entered.png');
    }
  } else {
    console.log('Variety field not found by label');
    // Try finding any autocomplete
    const autocomplete = page.locator('.MuiAutocomplete-root input').first();
    if (await autocomplete.count() > 0) {
      console.log('Found autocomplete, clicking...');
      await autocomplete.click();
      await page.waitForTimeout(500);
      await autocomplete.fill('Pinot');
      await page.waitForTimeout(1000);
      await page.screenshot({ path: '/tmp/02-autocomplete.png' });
    }
  }

  console.log('Test complete. Closing in 10 seconds...');
  await page.waitForTimeout(10000);
  await browser.close();
}

testBlindTastings().catch(console.error);
