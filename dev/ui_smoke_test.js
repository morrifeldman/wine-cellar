const { chromium } = require('playwright');

(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage();
  
  try {
    console.log('Navigating to http://localhost:8080...');
    await page.goto('http://localhost:8080', { timeout: 10000 });
    
    // Wait for the title or a specific element to ensure the app loaded
    await page.waitForSelector('title', { timeout: 5000 });
    const title = await page.title();
    console.log(`Page title: ${title}`);

    // Check for some text that should be there (e.g., "Wine")
    const content = await page.textContent('body');
    if (content.includes('Wine')) {
      console.log('SUCCESS: "Wine" found on page.');
    } else {
      console.log('WARNING: "Wine" not found on page, but page loaded.');
    }

  } catch (error) {
    console.error('FAILURE: Could not reach or load the app:', error.message);
    process.exit(1);
  } finally {
    await browser.close();
  }
})();
