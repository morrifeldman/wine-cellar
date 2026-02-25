const { makePageWithAuth, gotoApp, waitForPath } = require('./test_helpers');

async function openInsights(page) {
  await gotoApp(page);
  await page.locator('button:has-text("Insights")').click();
  await page.waitForTimeout(2000);
}

async function clickFirstWineLink(page) {
  return page.evaluate(() => {
    const overlay = Array.from(document.querySelectorAll('div')).find(el => {
      const s = window.getComputedStyle(el);
      return s.position === 'fixed' && s.zIndex === '1300';
    });
    if (!overlay) return null;
    const span = Array.from(overlay.querySelectorAll('span')).find(s => {
      const st = window.getComputedStyle(s);
      return st.cursor === 'pointer' && st.textDecoration.includes('underline');
    });
    if (span) { span.click(); return span.textContent.trim(); }
    return null;
  });
}

async function waitForWineDetail(page) {
  await page.waitForFunction(() => window.location.pathname.startsWith('/wine/'), { timeout: 5000 });
}

async function runAll() {
  // === Test 1: Wine link navigation ===
  console.log('=== Test 1: Wine link navigation ===');
  {
    const { browser, page } = await makePageWithAuth({ headless: true });
    try {
      await openInsights(page);
      const clicked = await clickFirstWineLink(page);
      console.log(`Clicked: "${clicked}"`);
      await waitForWineDetail(page);
      await page.screenshot({ path: '/tmp/insights-t1-after-click.png' });
      const onDetail = await page.locator('button:has-text("Back")').count() > 0;
      console.log(onDetail ? 'PASS: Navigated to detail page' : 'FAIL: Not on detail page');
    } finally { await browser.close(); }
  }

  // === Test 2: App back button goes to wine list (not insights) ===
  console.log('\n=== Test 2: App back button goes to wine list ===');
  {
    const { browser, page } = await makePageWithAuth({ headless: true });
    try {
      await openInsights(page);
      await clickFirstWineLink(page);
      await waitForWineDetail(page);
      const backBtn = page.locator('button:has-text("Back to List")');
      await backBtn.waitFor({ state: 'visible', timeout: 10000 });
      await backBtn.click();
      await waitForPath(page, '/');
      await page.screenshot({ path: '/tmp/insights-t2-after-back.png' });
      const insightsVisible = await page.locator('text=Cellar Insights').isVisible();
      console.log(!insightsVisible ? 'PASS: Back to wine list (insights modal closed)' : 'FAIL: Insights modal still open');
    } finally { await browser.close(); }
  }

  // === Test 3: Browser back from wine detail returns to /insights ===
  console.log('\n=== Test 3: Browser back returns to insights ===');
  {
    const { browser, page } = await makePageWithAuth({ headless: true });
    try {
      await openInsights(page);
      await clickFirstWineLink(page);
      await waitForWineDetail(page);
      await page.goBack();
      await waitForPath(page, '/insights');
      await page.screenshot({ path: '/tmp/insights-t3-browser-back.png' });
      const modalBack = await page.locator('text=Cellar Insights').isVisible();
      console.log(modalBack ? 'PASS: Insights modal visible after browser back' : 'FAIL: Modal not restored');
    } finally { await browser.close(); }
  }

  // === Test 4: Left/right nav arrows ===
  console.log('\n=== Test 4: Left/right navigation arrows ===');
  {
    const { browser, page } = await makePageWithAuth({ headless: true });
    try {
      await openInsights(page);
      const left = page.locator('button').filter({ hasText: '‹' });
      const right = page.locator('button').filter({ hasText: '›' });
      console.log(`Arrows present: ‹=${await left.count()} ›=${await right.count()}`);
      const rightDisabled = await right.first().isDisabled();
      console.log(`Right disabled at newest: ${rightDisabled} ${rightDisabled ? 'PASS' : 'FAIL'}`);
      if (!await left.first().isDisabled()) {
        const date1 = await page.locator('text=/Report generated on/i').textContent().catch(() => '?');
        await left.first().click();
        await page.waitForTimeout(1000);
        const date2 = await page.locator('text=/Report generated on/i').textContent().catch(() => '?');
        console.log(`${date1} → ${date2}`);
        console.log(date1 !== date2 ? 'PASS: Older report loaded' : 'FAIL: Date unchanged');
        await right.first().click();
        await page.waitForTimeout(1000);
        const date3 = await page.locator('text=/Report generated on/i').textContent().catch(() => '?');
        console.log(date3 === date1 ? 'PASS: Returned to newest report' : `FAIL: got "${date3}"`);
      } else {
        console.log('NOTE: Only one report in DB');
      }
    } finally { await browser.close(); }
  }

  console.log('\n=== Done ===');
}

runAll().catch(console.error);
