import { test, expect } from '@playwright/test';

test.describe('Stage 13: unified Refactor Workbench', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/');
    await page.waitForSelector('[data-testid="app-shell"]');
    await page.click('[data-testid="nav-tracks"]');
    await page.click('[data-testid="tab-refactor-workbench"]');
    await expect(page.locator('[data-testid="refactor-workbench-section"]')).toBeVisible();
  });

  test('previews and applies a reference-aware registry rename through WorkspacePlan', async ({ page }) => {
    await expect(page.locator('[data-testid="refactor-registry-card"]')).toBeVisible();
    await expect(page.locator('[data-testid="refactor-procedure-note"]')).toContainText('Procedure');

    await expect(page.locator('[data-testid="refactor-registry-select"]'))
      .toHaveValue('7a4be662-5208-4cc7-8984-c08ae63a447a');
    await page.fill('[data-testid="refactor-registry-new-name"]', 'player_energy_v2');
    await page.click('[data-testid="preview-registry-refactor"]');

    const impact = page.locator('[data-testid="registry-refactor-impact"]');
    await expect(impact).toBeVisible();
    await expect(impact).toContainText('player_energy');
    await expect(impact).toContainText('player_energy_v2');
    await expect(impact).toContainText('恢复保护就绪');
    await expect(page.locator('[data-testid="apply-registry-refactor"]')).toBeEnabled();

    await page.click('[data-testid="apply-registry-refactor"]');
    await expect(page.locator('[data-testid="refactor-result"]')).toContainText('重构已提交');
    await expect(page.locator('[data-testid="registry-refactor-impact"]')).not.toBeVisible();
  });

  test('shows exact asset reference rewrites before applying a move', async ({ page }) => {
    await page.selectOption(
      '[data-testid="refactor-asset-select"]',
      'asset:2222222222222222222222222222222222222222222222222222222222222222'
    );
    await page.fill(
      '[data-testid="refactor-asset-target"]',
      'assets/coppertrails/textures/block/copper_lamp_v2.png'
    );
    await page.click('[data-testid="preview-asset-refactor"]');

    const impact = page.locator('[data-testid="asset-refactor-impact"]');
    await expect(impact).toBeVisible();
    await expect(impact).toContainText('assets/coppertrails/models/block/copper_lamp.bbmodel/textures/all');
    await expect(impact).toContainText('coppertrails:block/copper_lamp');
    await expect(impact).toContainText('coppertrails:block/copper_lamp_v2');
    await expect(page.locator('[data-testid="apply-asset-refactor"]')).toBeEnabled();

    await page.click('[data-testid="apply-asset-refactor"]');
    await expect(page.locator('[data-testid="refactor-result"]')).toContainText('重构已提交');
    await expect(page.locator('[data-testid="asset-refactor-impact"]')).not.toBeVisible();
  });
});
