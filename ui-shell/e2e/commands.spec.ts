import { test, expect } from '@playwright/test';

test.describe('Interactive UI-Core Commands & Mutations', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/');
    await page.waitForSelector('[data-testid="app-shell"]');
    // Ensure on ready scenario
    await page.click('[data-testid="scenario-switcher-trigger"]');
    await page.click('[data-testid="scenario-btn-ready"]');
  });

  test('create_mod_element adds item to list and advances revision', async ({ page }) => {
    // Open Create Modal
    await page.click('[data-testid="empty-primary-action"]');
    await expect(page.locator('[data-testid="create-element-modal"]')).toBeVisible();

    // Fill identifier
    await page.fill('[data-testid="create-element-name-input"]', 'ruby_ore');
    await page.click('[data-testid="create-element-submit-btn"]');

    // Modal closes and new element appears in Elements view
    await expect(page.locator('[data-testid="create-element-modal"]')).not.toBeVisible();
    await expect(page.getByText('Ruby Ore').first()).toBeVisible();
    await expect(page.locator('[data-testid="element-inspector"]')).toBeVisible();
  });

  test('Stage 11 exposes all Java element types and creates a long-tail element', async ({ page }) => {
    await page.click('[data-testid="nav-elements"]');
    await page.click('[data-testid="create-element-btn"]');

    const typeButtons = page.locator('[data-testid="create-element-modal"] button[aria-pressed]');
    await expect(typeButtons).toHaveCount(37);
    await page.locator('[data-testid="create-element-modal"] button').filter({ hasText: '生物实体' }).click();
    await page.fill('[data-testid="create-element-name-input"]', 'copper_guardian');
    await page.click('[data-testid="create-element-submit-btn"]');

    await expect(page.locator('[data-testid="create-element-modal"]')).not.toBeVisible();
    await expect(page.locator('[data-testid="element-inspector"]')).toBeVisible();
    await expect(page.locator('[data-testid="field-displayName"]')).toHaveValue('Copper Guardian');
    await expect(page.locator('[data-testid="field-displayName"]')).toBeEditable();
  });

  test('update_mod_element with invalid value triggers validation error', async ({ page }) => {
    await page.click('[data-testid="nav-elements"]');
    await page.locator('[data-element-id="22222222-2222-4222-8222-222222222221"]').first().click();

    // Fill invalid hardness (e.g. 200)
    await page.fill('[data-testid="field-hardness"]', '200');
    await page.click('[data-testid="inspector-save-btn"]');

    // Expect validation alert
    await expect(page.locator('[data-testid="validation-alert"]')).toBeVisible();
    await expect(page.getByText('硬度必须在 0 到 100 之间。').first()).toBeVisible();
  });

  test('build_workspace triggers task progress and completes', async ({ page }) => {
    await page.click('[data-testid="titlebar-build-btn"]');

    // Task drawer should open
    await expect(page.locator('[data-testid="task-drawer"]')).toBeVisible();
    await expect(page.locator('[data-testid="task-log-stream"]')).toBeVisible();
    await expect(page.getByText('Initiating build workflow for Fabric 1.21.1...')).toBeVisible();

    // Wait for completion
    await expect(page.getByText('任务完成').first()).toBeVisible({ timeout: 5000 });
  });

  test('generate_workspace triggers task progress and completes', async ({ page }) => {
    await page.click('[data-testid="titlebar-generate-btn"]');

    await expect(page.locator('[data-testid="task-drawer"]')).toBeVisible();
    await expect(page.locator('[data-testid="task-log-stream"]')).toBeVisible();
    await expect(page.getByText('任务完成').first()).toBeVisible({ timeout: 5000 });
  });

  test('cycling MCP permission updates status and allows elevation', async ({ page }) => {
    const permBtn = page.locator('[data-testid="permission-alert"]');
    await expect(permBtn).toContainText('MCP: WORKSPACE');

    await permBtn.click();
    await expect(permBtn).toContainText('MCP: FULL_ACCESS');

    await permBtn.click();
    await expect(permBtn).toContainText('MCP: READ_ONLY');
  });
});
