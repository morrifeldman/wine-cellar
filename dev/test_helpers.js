const { chromium } = require('playwright');
const { execSync } = require('child_process');
const path = require('path');

const PROJECT_ROOT = path.resolve(__dirname, '..');

function getAuthToken() {
  const raw = execSync(
    'bb scripts/repl_client.clj "(wine-cellar.auth.core/create-jwt-token {:email \\"test@example.com\\" :name \\"Test User\\"})"',
    { cwd: PROJECT_ROOT }
  ).toString().trim();
  // nREPL returns Clojure string representation with surrounding quotes
  return raw.startsWith('"') ? raw.slice(1, -1) : raw;
}

async function makePageWithAuth({ headless = false, width = 1280, height = 800 } = {}) {
  const token = getAuthToken();
  const browser = await chromium.launch({ headless });
  const context = await browser.newContext({ viewport: { width, height }, hasTouch: false });
  await context.addCookies([{ name: 'auth-token', value: token, domain: 'localhost', path: '/' }]);
  const page = await context.newPage();
  await page.addInitScript(() =>
    Object.defineProperty(navigator, 'maxTouchPoints', { get: () => 0 })
  );
  page.on('console', msg => { if (msg.type() === 'error') console.log('CONSOLE ERROR:', msg.text()); });
  page.on('pageerror', err => console.log('PAGE ERROR:', err.message));
  return { browser, page };
}

async function gotoApp(page, path = '/') {
  await page.goto(`http://localhost:8080${path}`);
  await page.waitForLoadState('networkidle');
}

// Use instead of waitForURL for SPA pushState navigation
async function waitForPath(page, pathname, timeout = 5000) {
  await page.waitForFunction(
    p => window.location.pathname === p,
    pathname,
    { timeout }
  );
}

module.exports = { getAuthToken, makePageWithAuth, gotoApp, waitForPath };
