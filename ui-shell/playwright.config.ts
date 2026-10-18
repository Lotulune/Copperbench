import { defineConfig, devices } from '@playwright/test';
import { fileURLToPath } from 'node:url';

const rawPort = process.env.COPPERBENCH_E2E_PORT ?? '5183';
const port = Number(rawPort);
if (!/^\d+$/.test(rawPort) || !Number.isInteger(port) || port < 1024 || port > 65535)
  throw new Error('COPPERBENCH_E2E_PORT must be a port between 1024 and 65535.');
const testUrl = `http://127.0.0.1:${port}`;

export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: 0,
  workers: 1,
  reporter: 'list',
  use: {
    locale: 'zh-CN',
    baseURL: testUrl,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure'
  },
  projects: [
    {
      name: 'chromium',
      testIgnore: /visual-matrix\.spec\.ts/,
      use: { ...devices['Desktop Chrome'], viewport: { width: 1920, height: 1080 } }
    },
    {
      name: 'compact-1366',
      testIgnore: /visual-matrix\.spec\.ts/,
      use: { ...devices['Desktop Chrome'], viewport: { width: 1366, height: 768 } }
    },
    {
      name: 'visual-1366-125',
      testMatch: /visual-matrix\.spec\.ts/,
      use: { ...devices['Desktop Chrome'], viewport: { width: 1093, height: 614 }, deviceScaleFactor: 1.25 }
    },
    {
      name: 'visual-1920-150',
      testMatch: /visual-matrix\.spec\.ts/,
      use: { ...devices['Desktop Chrome'], viewport: { width: 1280, height: 720 }, deviceScaleFactor: 1.5 }
    },
    {
      name: 'visual-2560-175',
      testMatch: /visual-matrix\.spec\.ts/,
      use: { ...devices['Desktop Chrome'], viewport: { width: 1463, height: 823 }, deviceScaleFactor: 1.75 }
    },
    {
      name: 'visual-3840-200',
      testMatch: /visual-matrix\.spec\.ts/,
      use: { ...devices['Desktop Chrome'], viewport: { width: 1920, height: 1080 }, deviceScaleFactor: 2 }
    }
  ],
  webServer: {
    command: `node node_modules/vite/bin/vite.js --host 127.0.0.1 --port ${port} --strictPort`,
    cwd: fileURLToPath(new URL('.', import.meta.url)),
    url: testUrl,
    reuseExistingServer: false,
    timeout: 15000
  }
});
