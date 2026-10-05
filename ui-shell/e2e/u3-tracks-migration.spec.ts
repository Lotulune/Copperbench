import { test, expect } from '@playwright/test';

test.describe('U3: Version Tracks, Loader Migration, Upstream Import, and Publish Batches', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/');
    await page.waitForSelector('[data-testid="app-shell"]');
    await page.getByRole('button', { name: '展开或收起工具' }).click();
  });

  test('shows actual versions and support status with secondary details collapsed', async ({ page }, testInfo) => {
    await page.click('[data-testid="nav-tracks"]');
    await expect(page.locator('[data-testid="tracks-view"]')).toBeVisible();
    const view = page.getByTestId('tracks-view');
    await expect(view.getByRole('heading', { name: '版本与迁移', exact: true })).toBeVisible();
    expect(await view.innerText()).not.toContain('前一稳定轨');
    expect(await view.innerText()).not.toContain('版本策略');
    await expect(page.getByTestId('refactor-workbench-section')).not.toBeVisible();
    await expect(page.getByTestId('upstream-import-section')).not.toBeVisible();

    // Verify track cards exist
    await expect(page.locator('[data-testid="track-card-latest_stable"]')).toBeVisible();
    await expect(page.locator('[data-testid="track-card-previous_stable"]')).toBeVisible();
    await expect(page.locator('[data-testid="track-card-minecraft_1_21_1"]')).toBeVisible();
    await expect(page.locator('[data-testid="track-card-minecraft_1_20_1"]')).toBeVisible();

    await expect(page.locator('[data-testid="status-supported"]').first()).toBeVisible();
    await expect(page.getByText('TRACK_SUPPORTED').first()).not.toBeVisible();
    await expect(page.getByText('Minecraft 26.2').first()).toBeVisible();
    await expect(view).toContainText('NeoForge');
    await expect(view).toContainText('Fabric');
    expect(await view.evaluate(element => element.scrollWidth <= element.clientWidth + 1)).toBe(true);
    await page.screenshot({ path: testInfo.outputPath('versions.png') });
    await page.getByText('支持详情', { exact: true }).first().click();
    await expect(page.getByText('TRACK_SUPPORTED').first()).toBeVisible();
    await page.getByTestId('tab-upstream-import').focus();
    await page.keyboard.press('Enter');
    await expect(page.getByTestId('upstream-import-section')).toBeVisible();
    await page.keyboard.press('Enter');
    await expect(page.getByTestId('upstream-import-section')).not.toBeVisible();
  });

  test('previews loader migration with 5 disposition groups and requires explicit confirmation to execute', async ({ page }) => {
    await page.click('[data-testid="nav-tracks"]');
    await page.click('[data-testid="tab-loader-migration"]');

    await expect(page.locator('[data-testid="loader-migration-section"]')).toBeVisible();
    await expect(page.getByText('安全拷贝保证：')).not.toBeVisible();

    // Select NeoForge 1.21.1 and preview
    await page.selectOption('[data-testid="migration-target-select"]', 'neoforge-1.21.1');
    await page.click('[data-testid="preview-migration-btn"]');

    // Verify preview report renders with disposition groups
    await expect(page.locator('[data-testid="migration-preview-report"]')).toBeVisible();
    await expect(page.getByText('迁移会创建新副本，原工作区不变。', { exact: true })).toBeVisible();
    await expect(page.locator('[data-testid="preview-complete-badge"]')).toBeVisible();
    await expect(page.locator('[data-testid="disposition-group-supported"]')).toBeVisible();
    await expect(page.locator('[data-testid="disposition-group-substitute"]')).toBeVisible();
    await expect(page.locator('[data-testid="disposition-group-manual"]')).toBeVisible();
    await expect(page.locator('[data-testid="migration-semantic-comparison"]')).not.toBeVisible();

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
    const semanticComparison = page.locator('[data-testid="migration-semantic-comparison"]');
    await expect(semanticComparison).toBeVisible();
    await expect(semanticComparison.getByText('已按计划切换')).toBeVisible();
    await expect(page.locator('[data-testid="migration-metadata-preserved"]')).toBeVisible();
    await expect(page.locator('[data-testid="migration-preserved-elements"]')).toHaveText('5');
    await expect(page.locator('[data-testid="migration-semantic-change"]')).toHaveCount(1);
    await expect(page.locator('[data-testid="migration-semantic-change"]')).toContainText('fabric-1.21.1 -> neoforge-1.21.1');
    await expect(page.locator('[data-testid="migration-diagnostics-banner"]')).toBeVisible();
    await expect(page.locator('[data-testid="migration-diagnostics-banner"]').getByText('LOADER_EXCLUSIVE_FIELDS_PRESERVED')).toBeVisible();
    await page.click('[data-testid="migration-diagnostics-banner-action-open_migration_element"]');
    await expect(page.locator('[data-testid="element-inspector"]')).toBeVisible();
    await expect(page.locator('[data-element-id="22222222-2222-4222-8222-222222222221"]').first()).toBeVisible();
    await expect(page.getByText('Copper Lamp').first()).toBeVisible();
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
    await expect(page.getByText('迁移未完成，原工作区未修改。请检查迁移报告中的阻断项和手动处理项。')).toBeVisible();
  });

  test('expands import on demand, preserves permission denial and succeeds on full access', async ({ page }) => {
    await page.click('[data-testid="nav-tracks"]');
    await page.click('[data-testid="tab-upstream-import"]');

    await expect(page.locator('[data-testid="upstream-import-section"]')).toBeVisible();
    await expect(page.getByText('文件选择不可用，请输入路径。', { exact: true })).toBeVisible();
    await expect(page.getByText('环境约束说明：')).not.toBeVisible();
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
