import { test, expect } from '@playwright/test';

test('settings remain unavailable in browser preview and the product icon loads', async ({ page }) => {
  await page.goto('/');
  await expect(page.getByTestId('nav-settings')).toBeDisabled();
  const icon = page.getByTestId('product-brand-icon');
  await expect(icon).toBeVisible();
  await expect.poll(() => icon.evaluate((image: HTMLImageElement) => image.naturalWidth)).toBeGreaterThan(0);
});

test('workspace settings open through the desktop host without replacing the active view', async ({ page }) => {
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
  await expect.poll(() => page.evaluate(() => sessionStorage.getItem('window-action'))).toBe('open_preferences');
  await expect(page.getByTestId('help-view')).toBeVisible();
  await page.evaluate(() => sessionStorage.setItem('fail-settings', 'true'));
  await page.getByTestId('nav-settings').click();
  await expect(page.getByRole('alert')).toContainText('无法打开设置');
  await expect(page.getByTestId('help-view')).toBeVisible();
});
