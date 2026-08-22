import { test, expect } from '@playwright/test';

test.describe('New Workspace (product shell native flow)', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/');
    await page.waitForSelector('[data-testid="app-shell"]');
  });

  test('renders the 4-track x 2-loader generator catalog with a default selection', async ({ page }) => {
    await page.click('[data-testid="nav-new-workspace"]');
    await expect(page.locator('[data-testid="new-workspace-view"]')).toBeVisible();

    // Four track groups with two loaders each
    await expect(page.locator('[data-testid="generator-track-latest_stable"]')).toBeVisible();
    await expect(page.locator('[data-testid="generator-track-previous_stable"]')).toBeVisible();
    await expect(page.locator('[data-testid="generator-track-minecraft_1_21_1"]')).toBeVisible();
    await expect(page.locator('[data-testid="generator-track-minecraft_1_20_1"]')).toBeVisible();
    await expect(page.locator('[data-testid="generator-option-fabric-26.2"]')).toBeVisible();
    await expect(page.locator('[data-testid="generator-option-neoforge-26.2"]')).toBeVisible();
    await expect(page.locator('[data-testid="generator-option-fabric-1.21.1"]')).toBeVisible();
    await expect(page.locator('[data-testid="generator-option-neoforge-1.20.1"]')).toBeVisible();

    // Default selection is the recommended 1.21.1 maintenance track
    await expect(page.locator('[data-testid="selected-generator-info"]')).toContainText('fabric-1.21.1');

    // Suggested workspace folders root is surfaced from the catalog
    await expect(page.getByText('MCreatorWorkspaces').first()).toBeVisible();
  });

  test('switching generators updates the selection info', async ({ page }) => {
    await page.click('[data-testid="nav-new-workspace"]');
    await page.click('[data-testid="generator-option-neoforge-26.2"]');
    await expect(page.locator('[data-testid="selected-generator-info"]')).toContainText('neoforge-26.2');
  });

  test('mod id drives package autofill and the suggested folder path', async ({ page }) => {
    await page.click('[data-testid="nav-new-workspace"]');
    await page.fill('[data-testid="new-workspace-mod-id-input"]', 'copper_trails');
    await expect(page.locator('[data-testid="new-workspace-package-input"]')).toHaveValue(
      'net.mcreator.copper_trails'
    );
    // Suggested folder hint follows the mod id
    await expect(page.getByText(/copper_trails/).first()).toBeVisible();
  });

  test('submit is gated behind the explicit approval checkbox and shows diagnostics on invalid input', async ({ page }) => {
    await page.click('[data-testid="nav-new-workspace"]');

    const submitBtn = page.locator('[data-testid="create-workspace-submit-btn"]');
    await expect(submitBtn).toBeDisabled();

    // Fill the form
    await page.fill('[data-testid="new-workspace-mod-name-input"]', 'Copper Trails');
    await page.fill('[data-testid="new-workspace-mod-id-input"]', 'copper_trails');
    await page.fill('[data-testid="new-workspace-folder-input"]', 'C:\\Users\\example\\MCreatorWorkspaces\\copper_trails');

    // Still disabled without approval
    await expect(submitBtn).toBeDisabled();

    await page.check('[data-testid="confirm-create-workspace-checkbox"]');
    await expect(submitBtn).toBeEnabled();
    await submitBtn.click();

    // The mock bridge commits and surfaces the created workspace banner
    await expect(page.locator('[data-testid="workspace-created-banner"]')).toBeVisible();
    await expect(page.locator('[data-testid="workspace-created-banner"]')).toContainText(
      'copper_trails.mcreator'
    );
  });

  test('invalid mod id surfaces the typed MOD_ID_INVALID diagnostic from the core', async ({ page }) => {
    await page.click('[data-testid="nav-new-workspace"]');
    await page.fill('[data-testid="new-workspace-mod-name-input"]', 'Copper Trails');
    await page.fill('[data-testid="new-workspace-mod-id-input"]', '1nv@lid');
    await page.fill('[data-testid="new-workspace-folder-input"]', 'C:\\Users\\example\\MCreatorWorkspaces\\demo');
    await page.check('[data-testid="confirm-create-workspace-checkbox"]');
    await page.click('[data-testid="create-workspace-submit-btn"]');

    await expect(page.locator('[data-testid="workspace-rejected-banner"]')).toBeVisible();
    await expect(page.locator('[data-testid="workspace-rejected-banner"]')).toContainText('MOD_ID_INVALID');
  });
});
