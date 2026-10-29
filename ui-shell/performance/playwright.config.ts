import { defineConfig, devices } from '@playwright/test';
import { playwrightTestServer } from '../tests/helpers/playwright-server.mjs';

if (!process.env.COPPERBENCH_PERF_OUTPUT) throw new Error('Use scripts/performance/measure.mjs to select and retain performance samples.');
const webServer = playwrightTestServer('COPPERBENCH_PERF_PORT', 5179, 30000);
const baseURL = webServer.url;
const executablePath = process.env.COPPERBENCH_PERF_BROWSER_EXECUTABLE;

// This directory is outside e2e/ and is selected only by the dedicated runner.
export default defineConfig({
  testDir: '.', testMatch: 'interaction-baseline.spec.ts', fullyParallel: false,
  workers: 1, retries: 0, forbidOnly: true, reporter: 'list',
  outputDir: process.env.COPPERBENCH_PERF_PLAYWRIGHT_OUTPUT,
  use: {
    ...devices['Desktop Chrome'], baseURL, viewport: { width: 1366, height: 768 },
    deviceScaleFactor: 1, locale: process.env.COPPERBENCH_PERF_LOCALE === 'en' ? 'en-US' : 'zh-CN',
    colorScheme: 'light', serviceWorkers: 'block', trace: 'off', screenshot: 'only-on-failure',
    headless: process.env.COPPERBENCH_PERF_HEADED !== '1',
    launchOptions: executablePath ? { executablePath } : {},
  },
  webServer,
});
