import { expect, test } from '@playwright/test';

test('tools reveal continuously, reverse cleanly, and exclude collapsed actions from keyboard focus', async ({ page }) => {
  await page.goto('/');
  const toggle = page.getByTestId('nav-tools-toggle');
  const tools = page.locator('#workbench-tools');
  await expect(page.getByTestId('nav-ai')).not.toBeVisible();
  const heights = await page.evaluate(async () => {
    const panel = document.querySelector('#workbench-tools')!;
    (document.querySelector('[data-testid=nav-tools-toggle]') as HTMLButtonElement).click();
    const samples: number[] = [];
    const start = performance.now();
    while (performance.now() - start < 340) {
      await new Promise(requestAnimationFrame);
      samples.push(panel.getBoundingClientRect().height);
    }
    return samples;
  });
  const end = heights.at(-1)!;
  expect(end).toBeGreaterThan(100);
  expect(heights.some(height => height > 1 && height < end - 1)).toBe(true);
  await expect(page.getByTestId('nav-ai')).toBeVisible();
  await toggle.click();
  await toggle.click();
  await toggle.click();
  await expect(toggle).toHaveAttribute('aria-expanded', 'false');
  await expect(tools).toHaveAttribute('inert', '');
  await expect(page.getByTestId('nav-ai')).not.toBeVisible();
  await toggle.focus();
  await page.keyboard.press('Tab');
  await expect(page.getByTestId('nav-help')).toBeFocused();
  await page.keyboard.press('Control+Shift+M');
  await expect(toggle).toHaveAttribute('aria-expanded', 'true');
  await expect(page.getByTestId('nav-ai')).toBeFocused();
});

test('reduced motion reveals tools immediately and preserves navigation', async ({ page }) => {
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await page.goto('/');
  await page.getByTestId('nav-tools-toggle').click();
  await expect(page.getByTestId('nav-ai')).toBeVisible();
  expect(await page.locator('#workbench-tools').evaluate(node => parseFloat(getComputedStyle(node).transitionDuration))).toBeLessThan(0.001);
  await page.getByTestId('nav-help').click();
  await expect(page.getByTestId('help-view')).toBeVisible();
  expect(await page.locator('.workbench-view-content').evaluate(node => node.getAnimations().length)).toBe(0);
});

test('view transitions preserve drafts and use the shared product icon', async ({ page }, info) => {
  await page.goto('/');
  await page.getByTestId('nav-new-workspace').click();
  await page.getByTestId('new-workspace-mod-name-input').fill('Copper workshop draft');
  await page.getByTestId('nav-help').click();
  await page.getByTestId('nav-new-workspace').click();
  await expect(page.getByTestId('new-workspace-mod-name-input')).toHaveValue('Copper workshop draft');
  const brand = page.locator('.titlebar-brand img');
  await expect(brand).toBeVisible();
  expect(await brand.evaluate((node: HTMLImageElement) => node.complete && node.naturalWidth > 0)).toBe(true);
  await expect(page.locator('link[rel=icon]')).toHaveAttribute('href', /icon\.png/);
  await page.getByTestId('nav-hub').click();
  await page.getByTestId('nav-tools-toggle').click();
  await expect(page.getByTestId('nav-ai')).toBeVisible();
  await page.screenshot({ path: info.outputPath('workbench-new-brand.png'), animations: 'disabled' });
});
