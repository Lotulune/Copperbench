import { expect, test } from '@playwright/test';

test('workbench remains stable across the release viewport and DPI matrix', async ({ page }) => {
  await page.goto('/');
  await expect(page.locator('[data-testid="app-shell"]')).toBeVisible();
  await expect(page.locator('[data-testid="frameless-titlebar"]')).toBeVisible();

  const layout = await page.evaluate(() => {
    const titlebar = document.querySelector<HTMLElement>('[data-testid="frameless-titlebar"]');
    const controls = Array.from(titlebar?.querySelectorAll<HTMLElement>('button') ?? []);
    const viewport = { width: window.innerWidth, height: window.innerHeight };
    return {
      viewport,
      documentWidth: document.documentElement.scrollWidth,
      documentHeight: document.documentElement.scrollHeight,
      titlebar: titlebar?.getBoundingClientRect().toJSON(),
      controls: controls.map((control) => control.getBoundingClientRect().toJSON())
    };
  });

  expect(layout.documentWidth).toBeLessThanOrEqual(layout.viewport.width);
  expect(layout.documentHeight).toBeLessThanOrEqual(layout.viewport.height);
  expect(layout.titlebar).toBeTruthy();
  for (const control of layout.controls) {
    expect(control.left).toBeGreaterThanOrEqual(0);
    expect(control.right).toBeLessThanOrEqual(layout.viewport.width);
    expect(control.top).toBeGreaterThanOrEqual(0);
    expect(control.bottom).toBeLessThanOrEqual(layout.viewport.height);
  }

  await expect(page).toHaveScreenshot('workbench-ready.png', {
    animations: 'disabled',
    caret: 'hide',
    fullPage: true,
    maxDiffPixelRatio: 0.002
  });
});
