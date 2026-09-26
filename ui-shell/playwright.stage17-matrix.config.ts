import { defineConfig } from '@playwright/test';
import base from './playwright.config';

export default defineConfig({
  ...base,
  testMatch: /stage17-language\.spec\.ts/,
  projects: [1, 1.5].flatMap(deviceScaleFactor => [
    { width: 1280, height: 720 }, { width: 1920, height: 1080 }
  ].map(viewport => ({
    name: `stage17-${viewport.width}x${viewport.height}-dpr${deviceScaleFactor}`,
    use: { browserName: 'chromium' as const, viewport, deviceScaleFactor }
  })))
});
