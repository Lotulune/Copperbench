import { test, expect } from '@playwright/test';

test.describe('U3: Version Tracks, Loader Migration, Upstream Import, and Publish Batches', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/');
    await page.waitForSelector('[data-testid="app-shell"]');
  });

  test('renders 4-track version matrix with statuses, reason codes, and current generator', async ({ page }) => {
    await page.click('[data-testid="nav-tracks"]');
    await expect(page.locator('[data-testid="tracks-view"]')).toBeVisible();

    // Verify track cards exist
    await expect(page.locator('[data-testid="track-card-latest_stable"]')).toBeVisible();
    await expect(page.locator('[data-testid="track-card-previous_stable"]')).toBeVisible();
    await expect(page.locator('[data-testid="track-card-minecraft_1_21_1"]')).toBeVisible();
    await expect(page.locator('[data-testid="track-card-minecraft_1_20_1"]')).toBeVisible();

    await expect(page.locator('[data-testid="status-supported"]').first()).toBeVisible();
    await expect(page.getByText('TRACK_SUPPORTED').first()).toBeVisible();
    await expect(page.getByText('Minecraft 26.2').first()).toBeVisible();
  });

  test('previews loader migration with 5 disposition groups and requires explicit confirmation to execute', async ({ page }) => {
    await page.click('[data-testid="nav-tracks"]');
    await page.click('[data-testid="tab-loader-migration"]');

    await expect(page.locator('[data-testid="loader-migration-section"]')).toBeVisible();
    await expect(page.getByText('安全拷贝保证：')).toBeVisible();

    // Select NeoForge 1.21.1 and preview
    await page.selectOption('[data-testid="migration-target-select"]', 'neoforge-1.21.1');
    await page.click('[data-testid="preview-migration-btn"]');

    // Verify preview report renders with disposition groups
    await expect(page.locator('[data-testid="migration-preview-report"]')).toBeVisible();
    await expect(page.locator('[data-testid="preview-complete-badge"]')).toBeVisible();
    await expect(page.locator('[data-testid="disposition-group-supported"]')).toBeVisible();
    await expect(page.locator('[data-testid="disposition-group-substitute"]')).toBeVisible();
    await expect(page.locator('[data-testid="disposition-group-manual"]')).toBeVisible();

    // Execute button MUST be disabled before confirmation checkbox is checked
    const executeBtn = page.locator('[data-testid="execute-migration-btn"]');
    await expect(executeBtn).toBeDisabled();

    // Check confirmation checkbox
    await page.check('[data-testid="confirm-migration-checkbox"]');
    await expect(executeBtn).toBeEnabled();

    // Execute migration
    await executeBtn.click();
    await expect(page.locator('[data-testid="migration-success-banner"]')).toBeVisible();
    await expect(page.getByText('加载器迁移已完成！')).toBeVisible();
  });

  test('previews 26.1 migration showing partial capability notice (complete=false) without source corruption', async ({ page }) => {
    await page.click('[data-testid="nav-tracks"]');
    await page.click('[data-testid="tab-loader-migration"]');

    // Select Fabric 26.1.2 preview
    await page.selectOption('[data-testid="migration-target-select"]', 'fabric-26.1.2');
    await page.click('[data-testid="preview-migration-btn"]');

    await expect(page.locator('[data-testid="migration-preview-report"]')).toBeVisible();
    await expect(page.locator('[data-testid="preview-incomplete-badge"]')).toBeVisible();
    await expect(page.locator('[data-testid="disposition-group-lost"]')).toBeVisible();
    await expect(page.locator('[data-testid="disposition-group-blocked"]')).toBeVisible();

    // Check confirmation and execute: 26.1 is not migratable so execution is rejected/incomplete, never success
    await page.check('[data-testid="confirm-migration-checkbox"]');
    await page.click('[data-testid="execute-migration-btn"]');
    await expect(page.locator('[data-testid="migration-incomplete-banner"]')).toBeVisible();
    await expect(page.locator('[data-testid="migration-success-banner"]')).not.toBeVisible();
    await expect(page.getByText('源工作区保持只读未受任何修改 (sourceUnchanged: true)')).toBeVisible();
  });

  test('upstream workspace import displays desktop full access notice, denies non-elevated import, and succeeds on full access', async ({ page }) => {
    await page.click('[data-testid="nav-tracks"]');
    await page.click('[data-testid="tab-upstream-import"]');

    await expect(page.locator('[data-testid="upstream-import-section"]')).toBeVisible();
    await expect(page.getByText('环境约束说明：')).toBeVisible();
    await expect(page.locator('[data-testid="upstream-browse-btn"]')).toBeDisabled();

    // Preview upstream import
    await page.click('[data-testid="preview-upstream-btn"]');
    await expect(page.locator('[data-testid="upstream-preview-report"]')).toBeVisible();

    // Execute button disabled until confirmed
    const importBtn = page.locator('[data-testid="import-upstream-btn"]');
    await expect(importBtn).toBeDisabled();

    await page.check('[data-testid="confirm-upstream-checkbox"]');
    await expect(importBtn).toBeEnabled();

    // Non-elevated execution triggers PERMISSION_DENIED denial banner
    await importBtn.click();
    await expect(page.locator('[data-testid="upstream-denial-banner"]')).toBeVisible();
    await expect(page.getByText('PERMISSION_DENIED')).toBeVisible();
    await expect(page.locator('[data-testid="upstream-success-banner"]')).not.toBeVisible();

    // Elevate permission to Full Access and re-import
    await page.click('[data-testid="elevate-full-access-btn"]');
    await expect(importBtn).toBeEnabled();
    await importBtn.click();
    await expect(page.locator('[data-testid="upstream-success-banner"]')).toBeVisible();
  });

  test('creates resource pack publish batch and prepares test client with ready notice', async ({ page }) => {
    await page.click('[data-testid="nav-tracks"]');
    await page.click('[data-testid="tab-publish-batches"]');

    await expect(page.locator('[data-testid="publish-batches-section"]')).toBeVisible();

    // Create a new batch
    await page.click('[data-testid="new-batch-btn"]');
    await expect(page.locator('[data-testid="new-batch-modal"]')).toBeVisible();

    await page.fill('[data-testid="new-batch-name-input"]', 'copper_pack_v1');
    await page.click('[data-testid="confirm-create-batch-btn"]');

    // Verify batch list rendered
    await expect(page.locator('[data-testid="publish-batch-list"]')).toBeVisible();
    await expect(page.getByText('copper_pack_v1')).toBeVisible();

    // Prepare test client
    await page.locator('[data-testid^="prepare-client-"]').first().click();
    await expect(page.locator('[data-testid="client-preparation-notice"]')).toContainText('已就绪，尚未启动客户端');
  });

  test('workspace hub links directly to tracks view', async ({ page }) => {
    await page.click('[data-testid="nav-hub"]');
    await expect(page.locator('[data-testid="hub-tracks-badge"]')).toBeVisible();

    await page.click('[data-testid="hub-tracks-badge"]');
    await expect(page.locator('[data-testid="tracks-view"]')).toBeVisible();
  });
});
