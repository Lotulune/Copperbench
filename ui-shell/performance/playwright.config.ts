import { defineConfig, devices } from '@playwright/test';
import { fileURLToPath } from 'node:url';

if (!process.env.COPPERBENCH_PERF_OUTPUT) throw new Error('Use scripts/performance/measure.mjs to select and retain performance samples.');
const port = Number(process.env.COPPERBENCH_PERF_PORT ?? '5179');
if (!Number.isInteger(port) || port < 1024 || port > 65535) throw new Error('Invalid performance fixture port.');
const baseURL = `http://127.0.0.1:${port}`;
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
  webServer: {
    command: `node node_modules/vite/bin/vite.js --host 127.0.0.1 --port ${port} --strictPort`,
    cwd: fileURLToPath(new URL('../', import.meta.url)),
    url: baseURL, reuseExistingServer: false, timeout: 30000,
  },
});
