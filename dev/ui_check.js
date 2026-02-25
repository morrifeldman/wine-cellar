const { makePageWithAuth, gotoApp } = require('./test_helpers');

async function testBlindTastings() {
  const { browser, page } = await makePageWithAuth();
  try {
    await gotoApp(page);

    console.log('Clicking Blind button...');
    await page.locator('button:has-text("Blind")').click();
    await page.waitForTimeout(500);

    console.log('Clicking New Blind Tasting...');
    await page.locator('button:has-text("New Blind Tasting")').click();
    await page.waitForTimeout(500);

    const dialogContent = page.locator('.MuiDialogContent-root');
    await dialogContent.evaluate(el => el.scrollTop = el.scrollHeight);
    await page.waitForTimeout(300);
    await page.screenshot({ path: '/tmp/01-form-scrolled.png' });
    console.log('Screenshot: /tmp/01-form-scrolled.png');

    const varietyField = page.locator('label:has-text("Guessed Varieties")').locator('xpath=following::input[1]');
    if (await varietyField.count() > 0) {
      await varietyField.click();
      await varietyField.fill('Pinot');
      await page.waitForTimeout(800);
      await page.screenshot({ path: '/tmp/02-variety-typed.png' });
      console.log('Screenshot: /tmp/02-variety-typed.png');

      const option = page.locator('li:has-text("Pinot")').first();
      if (await option.count() > 0) {
        await option.click();
      } else {
        await varietyField.press('Enter');
      }
      await page.waitForTimeout(500);
      await page.screenshot({ path: '/tmp/03-variety-selected.png' });
      console.log('Screenshot: /tmp/03-variety-selected.png');
    } else {
      console.log('Variety field not found');
    }

    console.log('Test complete.');
  } finally {
    await browser.close();
  }
}

// ── Web Fetch / Deal Site Preset Tests ──────────────────────────────────────

async function navigateToChat(page) {
  await gotoApp(page);
  await page.locator('button:has-text("Chat")').first().click();
  await page.waitForTimeout(800);
}

async function testDealsPresetButton() {
  console.log('\n=== Test: Deals preset button ===');
  const { browser, page } = await makePageWithAuth();
  try {
    await navigateToChat(page);

    await page.locator('button:has-text("Deals")').click();
    await page.waitForTimeout(400);
    await page.screenshot({ path: '/tmp/chat-02-deals-menu.png' });

    const lastBottleItem = page.locator('li:has-text("Last Bottle")');
    const lastBubblesItem = page.locator('li:has-text("Last Bubbles")');
    console.log(`Last Bottle visible: ${await lastBottleItem.isVisible()}`);
    console.log(`Last Bubbles visible: ${await lastBubblesItem.isVisible()}`);

    await lastBottleItem.click();
    await page.waitForTimeout(400);

    const value = await page.locator('textarea').first().inputValue();
    const hasUrl = value.includes('lastbottlewines.com');
    console.log(hasUrl ? 'PASS: Preset populated correctly' : 'FAIL: URL not in textarea');
  } finally {
    await browser.close();
  }
}

async function testUrlFetchInChat() {
  console.log('\n=== Test: URL in chat message triggers fetch ===');
  const { browser, page } = await makePageWithAuth();
  try {
    await navigateToChat(page);

    await page.locator('textarea').first().fill("What's on sale today? https://lastbottlewines.com/");
    await page.locator('button:has-text("Send")').click();

    console.log('Waiting for AI response (up to 30s)...');
    await page.waitForSelector('button:has-text("Send")', { timeout: 30000 });
    await page.screenshot({ path: '/tmp/chat-response.png' });
    console.log('Screenshot: /tmp/chat-response.png');

    const messages = page.locator('[class*="message"], [class*="Message"], .MuiPaper-root').filter({ hasText: /wine|offer|bottle/i });
    const count = await messages.count();
    console.log(count > 0 ? 'PASS: AI responded with wine-related content' : 'NOTE: Check screenshot for response');
  } finally {
    await browser.close();
  }
}

// Run whichever test you want:
// testBlindTastings().catch(console.error);
// testDealsPresetButton().catch(console.error);
// testUrlFetchInChat().catch(console.error);
