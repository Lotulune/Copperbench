import { test, expect } from '@playwright/test';

async function loadScenario(page: import('@playwright/test').Page, scenarioId: string) {
  await page.click('[data-testid="scenario-switcher-trigger"]');
  await page.click(`[data-testid="scenario-btn-${scenarioId}"]`);
}

test.describe('Stage 13 history productivity', () => {
  test('filters the visible timeline without changing the underlying recovery history', async ({ page }) => {
    await page.goto('/');
    await page.waitForSelector('[data-testid="app-shell"]');
    await loadScenario(page, 'history-ready');
    await page.click('[data-testid="nav-history"]');

    await expect(page.locator('[data-testid="history-point"]')).toHaveCount(3);

    await page.selectOption('[data-testid="history-source-filter"]', 'workspace_plan');
    await expect(page.locator('[data-testid="history-point"]')).toHaveCount(1);
    await expect(page.locator('[data-testid="history-point"]')).toContainText('Before MCP batch edit');

    await page.selectOption('[data-testid="history-source-filter"]', '');
    await page.selectOption('[data-testid="history-actor-filter"]', 'ui');
    await expect(page.locator('[data-testid="history-point"]')).toHaveCount(1);
    await expect(page.locator('[data-testid="history-point"]')).toContainText('Copper Lamp validated');

    await page.selectOption('[data-testid="history-actor-filter"]', '');
    await page.fill('[data-testid="history-search"]', 'workspace_plan');
    await expect(page.locator('[data-testid="history-point"]')).toHaveCount(1);
    await expect(page.locator('[data-testid="history-point"]')).toContainText('Before MCP batch edit');

    await page.fill('[data-testid="history-search"]', 'no-such-history');
    await expect(page.locator('[data-testid="history-point"]')).toHaveCount(0);
    await expect(page.getByText('没有匹配的恢复点')).toBeVisible();
  });
});
