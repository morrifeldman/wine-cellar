const { chromium } = require('playwright');
const AUTH_TOKEN = 'eyJhbGciOiJIUzI1NiJ9.eyJlbWFpbCI6InRlc3RAZXhhbXBsZS5jb20iLCJuYW1lIjoiVGVzdCBVc2VyIiwiaWF0IjoxNzcxNzc3NTM4NTY0LCJleHAiOjE3NzIzODIzMzg1NjR9.2SX6Z5NWPHXJ3sn5YuO7dOfE4v7e9p9YyXfz_isX16A';

async function makePageWithAuth() {
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({ viewport: { width: 1280, height: 900 }, hasTouch: false });
  await context.addCookies([{ name: 'auth-token', value: AUTH_TOKEN, domain: 'localhost', path: '/' }]);
  const page = await context.newPage();
  await page.addInitScript(() => Object.defineProperty(navigator, 'maxTouchPoints', { get: () => 0 }));
  page.on('console', msg => { if (msg.type() === 'error') console.log('CONSOLE ERROR:', msg.text()); });
  return { browser, page };
}

async function openInsights(page) {
  await page.goto('http://localhost:8080');
  await page.waitForLoadState('networkidle');
  await page.waitForTimeout(1000);
  await page.locator('button:has-text("Insights")').click();
  await page.waitForTimeout(2500);
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

async function runAll() {
  // === Test 1: Wine link navigation ===
  console.log('=== Test 1: Wine link navigation ===');
  {
    const { browser, page } = await makePageWithAuth();
    try {
      await openInsights(page);
      const clicked = await clickFirstWineLink(page);
      console.log(`Clicked: "${clicked}"`);
      await page.waitForTimeout(2000);
      await page.screenshot({ path: '/tmp/insights-t1-after-click.png' });
      const onDetail = await page.locator('button:has-text("Back")').count() > 0;
      console.log(onDetail ? 'PASS: Navigated to detail page' : 'FAIL: Not on detail page');
    } finally { await browser.close(); }
  }

  // === Test 2: Back button returns to insights ===
  console.log('\n=== Test 2: Back button returns to insights ===');
  {
    const { browser, page } = await makePageWithAuth();
    try {
      await openInsights(page);
      const clicked = await clickFirstWineLink(page);
      await page.waitForTimeout(2000);
      const backBtn = page.locator('button:has-text("Back")').first();
      if (await backBtn.count() > 0) {
        await backBtn.click();
        await page.waitForTimeout(1500);
        await page.screenshot({ path: '/tmp/insights-t2-after-back.png' });
        const modalBack = await page.locator('text=Cellar Insights').isVisible();
        console.log(modalBack ? 'PASS: Insights modal restored after back' : 'FAIL: Modal not restored');
      } else {
        console.log('FAIL: No Back button on detail page');
      }
    } finally { await browser.close(); }
  }

  // === Test 3: Left/right nav arrows ===
  console.log('\n=== Test 3: Left/right navigation arrows ===');
  {
    const { browser, page } = await makePageWithAuth();
    try {
      await openInsights(page);
      const left = page.locator('button').filter({ hasText: '‹' });
      const right = page.locator('button').filter({ hasText: '›' });
      console.log(`Arrows present: ‹=${await left.count()} ›=${await right.count()}`);
      const rightDisabled = await right.first().isDisabled();
      console.log(`Right disabled at newest: ${rightDisabled} ${rightDisabled ? 'PASS' : 'FAIL'}`);
      const leftDisabled = await left.first().isDisabled();
      const date1 = await page.locator('text=/Report generated on/i').textContent().catch(() => '?');
      if (!leftDisabled) {
        await left.first().click();
        await page.waitForTimeout(1500);
        const date2 = await page.locator('text=/Report generated on/i').textContent().catch(() => '?');
        console.log(`${date1} → ${date2}`);
        console.log(date1 !== date2 ? 'PASS: Older report loaded' : 'FAIL: Date unchanged');
        await right.first().click();
        await page.waitForTimeout(1500);
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
