import { test, expect } from '@playwright/test';

async function loadScenario(page: import('@playwright/test').Page, scenarioId: string) {
  await page.click('[data-testid="scenario-switcher-trigger"]');
  await page.click(`[data-testid="scenario-btn-${scenarioId}"]`);
}

async function focusInside(page: import('@playwright/test').Page, selector: string) {
  return page.evaluate((value) => !!document.activeElement?.closest(value), selector);
}

test.describe('Stage 2 local history and protected approvals', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/');
    await page.waitForSelector('[data-testid="app-shell"]');
  });

  test('creates, compares and explicitly restores a recovery point', async ({ page }) => {
    await loadScenario(page, 'history-ready');
    await page.click('[data-testid="nav-history"]');

    await expect(page.locator('[data-testid="history-view"]')).toBeVisible();
    await expect(page.locator('[data-testid="history-point"]')).toHaveCount(3);
    await expect(page.locator('[data-testid="history-change"]')).toHaveCount(4);
    await expect(page.getByText('elements/copper_lamp.mod.json')).toBeVisible();

    await page.click('[data-testid="create-recovery-point"]');
    const createDialog = page.locator('[data-testid="create-recovery-dialog"]');
    await expect(createDialog).toBeVisible();
    expect(await focusInside(page, '[data-testid="create-recovery-dialog"]')).toBe(true);
    await page.fill('[data-testid="recovery-label-input"]', 'Before texture import');
    await page.click('[data-testid="confirm-create-recovery"]');
    await expect(page.locator('[data-testid="history-point"]')).toHaveCount(4);

    await page.locator('[data-testid="history-point"]').nth(1).click();
    await page.click('[data-testid="restore-recovery-point"]');
    const restoreDialog = page.locator('[data-testid="restore-recovery-dialog"]');
    await expect(restoreDialog).toBeVisible();
    expect(await focusInside(page, '[data-testid="restore-recovery-dialog"]')).toBe(true);
    await page.keyboard.press('Escape');
    await expect(restoreDialog).not.toBeVisible();

    await page.click('[data-testid="restore-recovery-point"]');
    await page.click('[data-testid="confirm-restore-recovery"]');
    await expect(page.locator('[data-testid="history-status"]')).toContainText('已还原');
  });

  test('requires an explicit decision and blocks AI Java plugin enablement', async ({ page }) => {
    await loadScenario(page, 'approval-required');
    await page.click('[data-testid="nav-ai"]');

    const queue = page.locator('[data-testid="approval-queue"]');
    await expect(queue).toBeVisible();
    await expect(page.locator('[data-testid="approval-item"]')).toHaveCount(2);
    await expect(page.locator('[data-testid="approval-blocked"]')).toContainText('AI 无权批准');

    await page.click('[data-testid="review-approval"]');
    const dialog = page.locator('[data-testid="approval-dialog"]');
    await expect(dialog).toBeVisible();
    expect(await focusInside(page, '[data-testid="approval-dialog"]')).toBe(true);
    await page.click('[data-testid="deny-approval"]');

    await expect(dialog).not.toBeVisible();
    await expect(page.locator('[data-testid="approval-item"]')).toHaveCount(1);
    await expect(page.locator('[data-testid="approval-status"]')).toContainText('已拒绝');
  });
});
