import { expect, test, type Page } from '@playwright/test';

async function desktopHost(page: Page, locale: 'zh' | 'en', version: string | null = null) {
  await page.addInitScript(({ locale, version }) => {
    localStorage.setItem('copperbench.ui.locale', locale);
    localStorage.setItem('copperbench.theme', 'light');
    (window as any).updateActions = [];
    window.__COPPERBENCH_WINDOW_HOST__ = {
      systemFrame: true, updatesAvailable: true, appearanceAvailable: true, availableUpdateVersion: version,
      invoke: async action => { (window as any).updateActions.push(action); }
    };
  }, { locale, version });
}

for (const locale of ['zh', 'en'] as const) test(`updates belong to Help & about, with a quiet live notice (${locale})`, async ({ page }, info) => {
  await desktopHost(page, locale);
  await page.goto('/');
  const notice = page.getByTestId('product-update-notice');
  await expect(notice).toHaveCount(0);
  await page.getByTestId('nav-settings').click();
  await expect(page.getByTestId('settings-check-updates')).toHaveCount(0);
  await page.getByTestId('nav-help').click();
  await expect(page.getByRole('heading', { name: locale === 'zh' ? '帮助与关于' : 'Help & about', exact: true })).toBeVisible();
  await page.getByTestId('help-check-updates').click();
  await expect.poll(() => page.evaluate(() => (window as any).updateActions)).toContain('check_updates');
  await page.evaluate(() => {
    Object.assign(window.__COPPERBENCH_WINDOW_HOST__!, { availableUpdateVersion: '0.1.10' });
    window.dispatchEvent(new CustomEvent('copperbench:update-available'));
  });
  await expect(notice.getByRole('button')).toHaveText(locale === 'zh' ? '更新至 0.1.10' : 'Update to 0.1.10');
  const bounds = await notice.boundingBox();
  expect(bounds!.x).toBeLessThan(50);
  expect(bounds!.y).toBeGreaterThan(page.viewportSize()!.height / 2);
  await notice.getByRole('button').click();
  expect(await page.evaluate(() => (window as any).updateActions.filter((a: string) => a === 'check_updates').length)).toBe(2);
  await page.screenshot({ path: info.outputPath(`help-update-${locale}-light.png`), animations: 'disabled' });
  await page.getByTestId('theme-toggle-btn').click();
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');
  await expect.poll(() => page.evaluate(() => (window as any).updateActions)).toContain('set_theme_dark');
  await expect(notice.getByRole('button')).toBeVisible();
  await page.screenshot({ path: info.outputPath(`help-update-${locale}-dark.png`), animations: 'disabled' });
  await page.evaluate(() => {
    Object.assign(window.__COPPERBENCH_WINDOW_HOST__!, { availableUpdateVersion: null });
    window.dispatchEvent(new CustomEvent('copperbench:update-available'));
  });
  await expect(notice).toHaveCount(0);
});

test('cached update is visible on first render and a failed action gives localized feedback', async ({ page }) => {
  await desktopHost(page, 'zh', '0.2.0');
  await page.goto('/');
  const notice = page.getByTestId('product-update-notice');
  await expect(notice).toContainText('更新至 0.2.0');
  await page.evaluate(() => { window.__COPPERBENCH_WINDOW_HOST__!.invoke = async () => { throw new Error('transport unavailable'); }; });
  await notice.getByRole('button').click();
  await expect(notice.getByRole('alert')).toHaveText('暂时无法打开更新，请重试。');
  await expect(page.getByRole('dialog')).toHaveCount(0);
});
