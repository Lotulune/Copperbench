import { test, expect } from '@playwright/test';

test('browser preview explains unavailable settings and the product icon loads', async ({ page }) => {
  await page.goto('/');
  await page.getByTestId('nav-settings').click();
  const settings = page.getByTestId('settings-view');
  await expect(settings.getByRole('heading', { name: '应用设置不可用' })).toBeVisible();
  await expect(settings.getByRole('button', { name: '保存更改' })).toHaveCount(0);
  await expect(settings.getByRole('button', { name: '高级设置' })).toHaveCount(0);
  const icon = page.getByTestId('product-brand-icon');
  await expect(icon).toBeVisible();
  await expect.poll(() => icon.evaluate((image: HTMLImageElement) => image.naturalWidth)).toBeGreaterThan(0);
});

test('advanced settings use the desktop host and preserve workbench views on failure', async ({ page }) => {
  await page.addInitScript(() => {
    window.__COPPERBENCH_WINDOW_HOST__ = {
      systemFrame: false,
      preferencesAvailable: true,
      invoke: async (action) => {
        if (sessionStorage.getItem('fail-settings')) throw new Error('Unavailable');
        sessionStorage.setItem('window-action', action);
      }
    };
  });
  await page.goto('/');
  await page.getByTestId('nav-help').click();
  await page.getByTestId('nav-settings').click();
  const settings = page.getByTestId('settings-view');
  const advanced = settings.getByRole('button', { name: '高级设置', exact: true });
  await advanced.click();
  await expect.poll(() => page.evaluate(() => sessionStorage.getItem('window-action'))).toBe('open_preferences');
  await expect(settings).toBeVisible();
  await page.evaluate(() => sessionStorage.setItem('fail-settings', 'true'));
  await advanced.click();
  await expect(settings.getByRole('alert')).toContainText('Unavailable');
  await expect(settings).toBeVisible();
  await page.evaluate(() => {
    sessionStorage.removeItem('fail-settings');
    sessionStorage.removeItem('window-action');
  });
  await advanced.click();
  await expect.poll(() => page.evaluate(() => sessionStorage.getItem('window-action'))).toBe('open_preferences');
  await expect(settings.getByRole('alert')).toHaveCount(0);
  await page.getByTestId('workbench-tab-help').click();
  await expect(page.getByTestId('help-view')).toBeVisible();
});
