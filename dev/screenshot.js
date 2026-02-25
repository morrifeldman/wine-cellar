#!/usr/bin/env node
/**
 * Usage: node dev/screenshot.js <jwt-token> [path] [url]
 *
 * Takes a screenshot of the app after auth injection.
 * Defaults to http://localhost:8080, saves to /tmp/screenshot.png
 */

const { chromium } = require('playwright');

const [,, token, outPath = '/tmp/screenshot.png', url = 'http://localhost:3000'] = process.argv;

if (!token) {
  console.error('Usage: node dev/screenshot.js <jwt-token> [output-path] [url]');
  process.exit(1);
}

(async () => {
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({
    viewport: { width: 1400, height: 900 },
  });

  await context.addCookies([{
    name: 'auth-token',
    value: token,
    domain: 'localhost',
    path: '/'
  }]);

  const page = await context.newPage();
  page.on('console', msg => {
    if (msg.type() === 'error') console.error('CONSOLE ERROR:', msg.text());
  });

  await page.goto(url, { timeout: 60000, waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(2000);

  // CLICK env var: CSS selector to click before screenshotting
  const clickSel = process.env.CLICK;
  if (clickSel) {
    await page.locator(clickSel).first().click();
    await page.waitForTimeout(500);
  }

  const clipArg = process.env.CLIP; // format: "x,y,w,h"
  const clip = clipArg
    ? (([x,y,w,h]) => ({ x: +x, y: +y, width: +w, height: +h }))(clipArg.split(','))
    : undefined;
  await page.screenshot({ path: outPath, fullPage: false, ...(clip ? { clip } : {}) });
  console.log(outPath);

  await browser.close();
})().catch(err => { console.error(err); process.exit(1); });
