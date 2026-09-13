import { test, expect } from '@playwright/test';

for (const scheme of ['light', 'dark'] as const) {
  test(`first launch follows the ${scheme} system theme`, async ({ page }) => {
    await page.emulateMedia({ colorScheme: scheme });
    await page.goto('/');
    await expect(page.locator('html')).toHaveAttribute('data-theme', scheme);
    await expect(page.locator('html')).toHaveAttribute('data-theme-preference', 'system');
    await expect(page.getByTestId('theme-toggle-btn')).toHaveAccessibleName('当前跟随系统；切换到亮色主题');
  });
}

test('system changes apply live until the user picks a fixed theme; returning to system restores updates', async ({ page }) => {
  await page.emulateMedia({ colorScheme: 'light' });
  await page.goto('/');
  await page.emulateMedia({ colorScheme: 'dark' });
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');
  await page.getByTestId('theme-toggle-btn').click();
  await expect(page.locator('html')).toHaveAttribute('data-theme-preference', 'light');
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'light');
  await page.reload();
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'light');
  await page.getByTestId('theme-toggle-btn').click();
  await expect(page.locator('html')).toHaveAttribute('data-theme-preference', 'dark');
  await page.emulateMedia({ colorScheme: 'light' });
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');
  await page.reload();
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');
  await page.getByTestId('theme-toggle-btn').click();
  await expect(page.locator('html')).toHaveAttribute('data-theme-preference', 'system');
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'light');
  await page.reload();
  await expect(page.locator('html')).toHaveAttribute('data-theme-preference', 'system');
  await page.emulateMedia({ colorScheme: 'dark' });
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');
});

test('unavailable preference storage does not block startup or session theme changes', async ({ page }) => {
  await page.addInitScript(() => {
    Storage.prototype.getItem = () => { throw new Error('Storage disabled'); };
    Storage.prototype.setItem = () => { throw new Error('Storage disabled'); };
  });
  await page.emulateMedia({ colorScheme: 'light' });
  await page.goto('/');
  await expect(page.getByTestId('app-shell')).toBeVisible();
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'light');
  await page.getByTestId('theme-toggle-btn').click();
  await page.getByTestId('theme-toggle-btn').click();
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');
});

test('invalid saved preferences fall back to system', async ({ page }) => {
  await page.addInitScript(() => localStorage.setItem('copperbench.theme', 'invalid'));
  await page.emulateMedia({ colorScheme: 'light' });
  await page.goto('/');
  await expect(page.locator('html')).toHaveAttribute('data-theme-preference', 'system');
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'light');
});
