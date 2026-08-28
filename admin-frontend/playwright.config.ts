import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  workers: 1,
  retries: 0,
  timeout: 60_000,
  expect: { timeout: 10_000 },
  reporter: [['line']],
  outputDir: process.env.PLAYWRIGHT_OUTPUT_DIR ?? 'test-results',
  use: {
    browserName: 'chromium',
    channel: process.env.PLAYWRIGHT_CHROME_CHANNEL ?? 'chrome',
    headless: process.env.PLAYWRIGHT_HEADED !== 'true',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
});
