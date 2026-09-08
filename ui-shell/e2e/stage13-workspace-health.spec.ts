import { test, expect } from '@playwright/test';

test.describe('Stage 13: Workspace Health', () => {
  test('renders Core-owned project facts and routes to specialist views', async ({ page }) => {
    await page.goto('/');
    await page.waitForSelector('[data-testid="app-shell"]');

    const panel = page.locator('[data-testid="workspace-health-panel"]');
    await expect(panel).toBeVisible();
    await expect(panel).toContainText('项目健康');
    await expect(page.locator('[data-testid="workspace-health-diagnostics"]')).toContainText('诊断');
    await expect(page.locator('[data-testid="workspace-health-elements"]')).toContainText('无效');
    await expect(page.locator('[data-testid="workspace-health-assets"]')).toContainText('未使用');
    await expect(page.locator('[data-testid="workspace-health-generator"]')).toContainText('可生成');
    await expect(page.locator('[data-testid="workspace-health-recovery"]')).toContainText('3 个恢复点');
    await expect(page.locator('[data-testid="workspace-health-risk"]')).toContainText('5+ 操作标记高影响');

    await page.click('[data-testid="workspace-health-assets"]');
    await expect(page.locator('[data-testid="asset-browser"]')).toBeVisible();
  });
});
