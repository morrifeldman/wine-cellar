// Runs against the dev stack (scripts/start-dev.sh): app on :8080, API on :3000.
// Run with: npm run test:e2e
module.exports = {
  testDir: __dirname,
  timeout: 30000,
  use: {
    baseURL: 'http://localhost:8080',
    launchOptions: {
      args: ['--disable-dev-shm-usage', '--disable-gpu'],
      // Cloud sessions ship a Chromium that doesn't match the pinned
      // Playwright build; see dev/test_helpers.js.
      executablePath: process.env.CHROMIUM_PATH || undefined,
    },
  },
};
