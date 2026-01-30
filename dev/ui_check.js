const { chromium } = require('playwright');

(async () => {
  const authToken = process.env.TEST_AUTH_TOKEN;

  if (!authToken) {
    console.error('ERROR: TEST_AUTH_TOKEN environment variable is not set.');
    process.exit(1);
  }

  const browser = await chromium.launch();
  const context = await browser.newContext();

  // Inject the auth token as a cookie
  await context.addCookies([{
    name: 'auth-token',
    value: authToken,
    domain: 'localhost',
    path: '/',
    httpOnly: true, // usually true for auth tokens
    secure: false,  // false for localhost
    sameSite: 'Lax'
  }]);

  const page = await context.newPage();
  
  try {
    console.log('Navigating to http://localhost:8080 with injected auth token...');
    await page.goto('http://localhost:8080', { timeout: 10000 });
    
    // Wait for the app to load. We expect NOT to be redirected to Google.
    // We look for a specific element that exists only in the main app.
    // "Add New Wine" button text is a good candidate from main.cljs
    // Or just the main wrapper.
    
    await page.waitForSelector('#app', { timeout: 5000 });
    const title = await page.title();
    console.log(`Page title: ${title}`);

    // Check if we are still on localhost
    const url = page.url();
    if (!url.includes('localhost')) {
        throw new Error(`Redirected to external URL: ${url}`);
    }

    console.log('SUCCESS: Stayed on localhost.');

    // Look for "Add New Wine" or "Wine List" to confirm we are inside the app
    const content = await page.content();
    if (content.includes('Add New Wine') || content.includes('Wine List')) {
        console.log('SUCCESS: Found app content (Add New Wine/Wine List).');
    } else {
        console.warn('WARNING: App loaded but specific text not found. Snapshotting...');
        // In a real scenario, we might take a screenshot here
    }

  } catch (error) {
    console.error('FAILURE:', error.message);
    process.exit(1);
  } finally {
    await browser.close();
  }
})();
