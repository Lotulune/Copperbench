import { test, expect } from '@playwright/test';

test.describe('U3 asset browser', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/');
    await page.waitForSelector('[data-testid="app-shell"]');
    await page.click('[data-testid="nav-assets"]');
    await expect(page.locator('[data-testid="asset-browser"]')).toBeVisible();
  });

  test('filters by category and exposes stable ID/reference metadata', async ({ page }) => {
    await expect(page.locator('[data-testid="asset-card-asset:1111111111111111111111111111111111111111111111111111111111111111"]')).toBeVisible();
    await expect(page.locator('[data-testid="asset-stable-id"]')).toHaveText('asset:1111111111111111111111111111111111111111111111111111111111111111');
    await expect(page.locator('[data-testid="asset-details"]')).toContainText('引用关系');

    await page.click('[data-testid="asset-category-texture"]');
    await expect(page.locator('[data-testid="asset-card-asset:2222222222222222222222222222222222222222222222222222222222222222"]')).toBeVisible();
    await expect(page.locator('[data-testid="asset-card-asset:1111111111111111111111111111111111111111111111111111111111111111"]')).not.toBeVisible();
    await expect(page.locator('[data-testid="asset-stable-id"]')).toHaveText('asset:2222222222222222222222222222222222222222222222222222222222222222');
  });

  test('search is keyboard reachable and supports no-results recovery', async ({ page }) => {
    const search = page.locator('[data-testid="asset-search"]');
    await search.focus();
    await expect(search).toBeFocused();
    await search.fill('does-not-exist');
    await expect(page.locator('[data-testid="asset-browser-no-results"]')).toBeVisible();
    await page.getByRole('button', { name: '清除筛选' }).click();
    await expect(page.locator('[data-testid="asset-card-asset:1111111111111111111111111111111111111111111111111111111111111111"]')).toBeVisible();
  });

  test('reports an explicit unavailable state when Blockbench is not configured', async ({ page }) => {
    await page.getByRole('button', { name: '在 Blockbench 打开' }).click();
    await expect(page.locator('[data-testid="asset-notice"]')).toContainText('尚未配置 Blockbench');
  });

  test('empty, loading and error scenario states remain explicit', async ({ page }) => {
    await page.click('[data-testid="scenario-switcher-trigger"]');
    await page.click('[data-testid="scenario-btn-loading-workbench"]');
    await page.click('[data-testid="nav-assets"]');
    await expect(page.locator('[data-testid="asset-browser-loading"]')).toBeVisible();

    await page.click('[data-testid="scenario-switcher-trigger"]');
    await page.click('[data-testid="scenario-btn-empty-workspace"]');
    await page.click('[data-testid="nav-assets"]');
    await expect(page.locator('[data-testid="asset-browser-empty"]')).toBeVisible();

    await page.click('[data-testid="scenario-switcher-trigger"]');
    await page.click('[data-testid="scenario-btn-validation-failed"]');
    await page.click('[data-testid="nav-assets"]');
    await expect(page.locator('[data-testid="asset-browser-error"]')).toBeVisible();
  });
});
