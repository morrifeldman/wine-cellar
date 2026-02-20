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

// ── Web Fetch / Deal Site Preset Tests ──────────────────────────────────────

const AUTH_TOKEN = 'eyJhbGciOiJIUzI1NiJ9.eyJlbWFpbCI6InRlc3RAZXhhbXBsZS5jb20iLCJuYW1lIjoiVGVzdCBVc2VyIiwiaWF0IjoxNzcxNTQ5NTczMjc3LCJleHAiOjE3NzIxNTQzNzMyNzd9.qemzvdYEzrwF9X0kSbhu2LfsVUZGMdoGvoGVHEzzFJQ';

async function makePageWithAuth() {
  const browser = await chromium.launch({ headless: false });
  const context = await browser.newContext({ viewport: { width: 1280, height: 800 }, hasTouch: false });
  await context.addCookies([{ name: 'auth-token', value: AUTH_TOKEN, domain: 'localhost', path: '/' }]);
  const page = await context.newPage();
  await page.addInitScript(() => Object.defineProperty(navigator, 'maxTouchPoints', { get: () => 0 }));
  page.on('console', msg => { if (msg.type() === 'error') console.log('CONSOLE ERROR:', msg.text()); });
  page.on('pageerror', err => console.log('PAGE ERROR:', err.message));
  return { browser, page };
}

async function navigateToChat(page) {
  await page.goto('http://localhost:8080');
  await page.waitForLoadState('networkidle');
  // Click the Chat / AI button in the nav
  await page.locator('button:has-text("Chat")').first().click();
  await page.waitForTimeout(800);
}

async function testDealsPresetButton() {
  console.log('\n=== Test: Deals preset button ===');
  const { browser, page } = await makePageWithAuth();
  try {
    await navigateToChat(page);
    await page.screenshot({ path: '/tmp/chat-01-loaded.png' });
    console.log('Screenshot: /tmp/chat-01-loaded.png');

    // Click the Deals button
    console.log('Clicking Deals button...');
    await page.locator('button:has-text("Deals")').click();
    await page.waitForTimeout(400);
    await page.screenshot({ path: '/tmp/chat-02-deals-menu.png' });
    console.log('Screenshot: /tmp/chat-02-deals-menu.png');

    // Verify menu items appear
    const lastBottleItem = page.locator('li:has-text("Last Bottle")');
    const lastBubblesItem = page.locator('li:has-text("Last Bubbles")');
    const lbVisible = await lastBottleItem.isVisible();
    const lbubVisible = await lastBubblesItem.isVisible();
    console.log(`Last Bottle menu item visible: ${lbVisible}`);
    console.log(`Last Bubbles menu item visible: ${lbubVisible}`);

    // Click Last Bottle
    console.log('Selecting Last Bottle...');
    await lastBottleItem.click();
    await page.waitForTimeout(400);
    await page.screenshot({ path: '/tmp/chat-03-preset-selected.png' });
    console.log('Screenshot: /tmp/chat-03-preset-selected.png');

    // Verify textarea contains the preset message with URL
    const textarea = page.locator('textarea').first();
    const value = await textarea.inputValue();
    console.log(`Textarea value: "${value.substring(0, 80)}..."`);
    const hasUrl = value.includes('lastbottlewines.com');
    console.log(`Contains lastbottlewines.com URL: ${hasUrl}`);
    if (!hasUrl) console.log('FAIL: URL not in textarea');
    else console.log('PASS: Preset populated correctly');

  } finally {
    await page.waitForTimeout(3000);
    await browser.close();
  }
}

async function testUrlFetchInChat() {
  console.log('\n=== Test: URL in chat message triggers fetch ===');
  const { browser, page } = await makePageWithAuth();
  try {
    await navigateToChat(page);

    // Type a message with a URL into the textarea
    const textarea = page.locator('textarea').first();
    const testMessage = "What's on sale today? https://lastbottlewines.com/";
    await textarea.fill(testMessage);
    await page.waitForTimeout(300);
    await page.screenshot({ path: '/tmp/chat-04-url-typed.png' });
    console.log('Screenshot: /tmp/chat-04-url-typed.png');

    // Click Send
    console.log('Sending message with URL...');
    await page.locator('button:has-text("Send")').click();

    // Wait for AI response (up to 30s — fetch + AI call)
    console.log('Waiting for AI response (up to 30s)...');
    await page.waitForTimeout(2000);
    await page.screenshot({ path: '/tmp/chat-05-sending.png' });
    console.log('Screenshot: /tmp/chat-05-sending.png');

    await page.waitForSelector('button:has-text("Send")', { timeout: 30000 });
    await page.waitForTimeout(500);
    await page.screenshot({ path: '/tmp/chat-06-response.png' });
    console.log('Screenshot: /tmp/chat-06-response.png');

    // Check that a response appeared (assistant message)
    const messages = page.locator('[class*="message"], [class*="Message"], .MuiPaper-root').filter({ hasText: /wine|offer|bottle/i });
    const count = await messages.count();
    console.log(`Response messages found with wine/offer/bottle content: ${count}`);
    if (count > 0) console.log('PASS: AI responded with wine-related content');
    else console.log('NOTE: Could not verify wine content in response - check screenshots');

  } finally {
    await browser.close();
  }
}

// Run whichever test you want:
// testDealsPresetButton().catch(console.error);
// testUrlFetchInChat().catch(console.error);
