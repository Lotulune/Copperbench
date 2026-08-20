import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: 0,
  workers: 1,
  reporter: 'list',
  use: {
    baseURL: 'http://localhost:5173',
    trace: 'on-first-retry',
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
    command: 'npm run dev',
    url: 'http://localhost:5173',
    reuseExistingServer: !process.env.CI,
    timeout: 15000
  }
});
