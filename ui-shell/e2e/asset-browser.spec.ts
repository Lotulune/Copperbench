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

  test('filters Core-owned asset health and identifies static unreferenced candidates', async ({ page }) => {
    await expect(page.locator('[data-testid="asset-health-summary"]')).toBeVisible();
    await page.locator('[data-testid="asset-health-unused"]').click();
    await expect(page.locator('[data-testid="asset-card-asset:3333333333333333333333333333333333333333333333333333333333333333"]')).toBeVisible();
    await expect(page.locator('[data-testid="asset-card-asset:1111111111111111111111111111111111111111111111111111111111111111"]')).not.toBeVisible();
    await expect(page.locator('[data-testid="asset-usage-status"]')).toContainText('静态未引用');
    await expect(page.locator('[data-testid="asset-outgoing-references"]')).toBeVisible();
  });

  test('separates conservatively safe cleanup candidates from static-unreferenced assets', async ({ page }) => {
    await expect(page.getByTestId('asset-health-safe-summary')).toContainText('1');
    await page.getByTestId('asset-health-safe-unused').click();
    await expect(page.getByTestId('asset-card-asset:7777777777777777777777777777777777777777777777777777777777777777')).toBeVisible();
    await expect(page.getByTestId('asset-card-asset:3333333333333333333333333333333333333333333333333333333333333333')).not.toBeVisible();
    await expect(page.getByTestId('asset-usage-status')).toContainText('可安全清理候选');
  });

  test('filters exact duplicate-content candidates from Core-owned health', async ({ page }) => {
    await page.getByTestId('asset-health-duplicates').click();
    await expect(page.getByTestId('asset-health-duplicate-summary')).toContainText('重复组 1');
    await expect(page.getByTestId('asset-card-asset:5555555555555555555555555555555555555555555555555555555555555555')).toBeVisible();
    await expect(page.getByTestId('asset-duplicate-paths')).toContainText('copper_chime.ogg');
    await expect(page.getByTestId('asset-health-issue-codes')).toContainText('DUPLICATE_ASSET_CONTENT');
  });

  test('previews a grant-scoped asset import before committing with recovery protection', async ({ page }) => {
    await page.getByTestId('asset-import-button').click();
    await expect(page.getByTestId('asset-import-review')).toBeVisible();
    await expect(page.getByTestId('asset-import-source')).toHaveText('imported_texture.png');
    await expect(page.getByTestId('asset-import-target')).toHaveValue(
      'assets/coppertrails/textures/imported/imported_texture.png'
    );
    await expect(page.getByTestId('asset-import-preview-summary')).toBeVisible();
    await expect(page.getByTestId('asset-import-conflict')).toHaveText('新建资产');

    await page.getByTestId('asset-import-commit').click();
    await expect(page.getByTestId('asset-import-review')).not.toBeVisible();
    await expect(page.getByTestId('asset-notice')).toContainText('已创建恢复点');
  });

  test('requires an explicit reviewed replacement action for an existing asset', async ({ page }) => {
    await page.getByTestId('asset-category-texture').click();
    await page.getByRole('button', { name: '替换文件' }).click();

    await expect(page.getByTestId('asset-import-review')).toBeVisible();
    await expect(page.getByTestId('asset-import-target')).toHaveValue(
      'assets/coppertrails/textures/block/copper_lamp.png'
    );
    await expect(page.getByTestId('asset-import-conflict')).toHaveText('将替换现有资产');
    await expect(page.getByTestId('asset-import-commit')).toHaveText('确认替换并导入');

    await page.getByTestId('asset-import-commit').click();
    await expect(page.getByTestId('asset-import-review')).not.toBeVisible();
    await expect(page.getByTestId('asset-notice')).toContainText('已安全替换');
    await expect(page.getByTestId('asset-notice')).toContainText('已创建恢复点');
  });

  test('reviews a mixed create/replace batch and commits it through one batch action', async ({ page }) => {
    await page.getByTestId('asset-batch-import-button').click();
    await expect(page.getByTestId('asset-batch-import-review')).toBeVisible();
    await expect(page.getByTestId('asset-batch-item-0')).toContainText('batch_texture.png');
    await expect(page.getByTestId('asset-batch-item-1')).toContainText('batch_icon.png');

    await page.getByTestId('asset-batch-target-1').fill('assets/coppertrails/textures/block/copper_lamp.png');
    await page.getByTestId('asset-batch-preview').click();
    await expect(page.getByTestId('asset-batch-create-count')).toHaveText('1');
    await expect(page.getByTestId('asset-batch-replace-count')).toHaveText('1');
    await expect(page.getByTestId('asset-batch-conflict-0')).toContainText('CREATE');
    await expect(page.getByTestId('asset-batch-conflict-1')).toContainText('REPLACE');
    await expect(page.getByTestId('asset-batch-commit')).toContainText('确认替换并批量导入');

    await page.getByTestId('asset-batch-commit').click();
    await expect(page.getByTestId('asset-batch-import-review')).not.toBeVisible();
    await expect(page.getByTestId('asset-notice')).toContainText('批量导入 2 个资产');
    await expect(page.getByTestId('asset-notice')).toContainText('一个恢复点');
  });

  test('blocks an intra-batch target collision before any batch write', async ({ page }) => {
    await page.getByTestId('asset-batch-import-button').click();
    const shared = 'assets/coppertrails/textures/imported/shared.png';
    await page.getByTestId('asset-batch-target-0').fill(shared);
    await page.getByTestId('asset-batch-target-1').fill(shared);
    await page.getByTestId('asset-batch-preview').click();

    await expect(page.getByTestId('asset-batch-issues')).toContainText('ASSET_IMPORT_BATCH_TARGET_CONFLICT');
    await expect(page.getByTestId('asset-batch-commit')).toBeDisabled();
  });

  test('keeps batch import review controls at the >=32px interaction target baseline', async ({ page }) => {
    await page.getByTestId('asset-batch-import-button').click();
    const controls = await page.getByTestId('asset-batch-import-review').locator('button:visible, input:visible').all();
    for (const control of controls) {
      const box = await control.boundingBox();
      if (!box) continue;
      expect(Math.round(box.width)).toBeGreaterThanOrEqual(32);
      expect(Math.round(box.height)).toBeGreaterThanOrEqual(32);
    }
  });

  test('reviews exact reference rewrites before a reference-safe asset move', async ({ page }) => {
    await page.getByTestId('asset-category-texture').click();
    await page.getByTestId('asset-move-button').click();
    await expect(page.getByTestId('asset-move-review')).toBeVisible();

    const target = 'assets/coppertrails/textures/block/copper_lamp_renamed.png';
    await page.getByTestId('asset-move-target').fill(target);
    await page.getByTestId('asset-move-preview').click();

    await expect(page.getByTestId('asset-move-preview-summary')).toBeVisible();
    await expect(page.getByTestId('asset-move-reference-count')).toHaveText('1');
    await expect(page.getByTestId('asset-move-rewrites')).toContainText('/textures/all');
    await expect(page.getByTestId('asset-move-rewrites')).toContainText('copper_lamp_renamed');
    await expect(page.getByTestId('asset-move-target-id')).toContainText('asset:');
    await expect(page.getByTestId('asset-move-commit')).toBeEnabled();

    await page.getByTestId('asset-move-commit').click();
    await expect(page.getByTestId('asset-move-review')).not.toBeVisible();
    await expect(page.getByTestId('asset-notice')).toContainText('更新 1 条引用并创建恢复点');
  });

  test('blocks an unchanged asset move before any write is possible', async ({ page }) => {
    await page.getByTestId('asset-category-texture').click();
    await page.getByTestId('asset-move-button').click();
    await page.getByTestId('asset-move-preview').click();

    await expect(page.getByTestId('asset-move-issues')).toContainText('ASSET_MOVE_TARGET_UNCHANGED');
    await expect(page.getByTestId('asset-move-commit')).toBeDisabled();
  });

  test('keeps asset move review controls at the >=32px interaction target baseline', async ({ page }) => {
    await page.getByTestId('asset-category-texture').click();
    await page.getByTestId('asset-move-button').click();
    const controls = await page.getByTestId('asset-move-review').locator('button:visible, input:visible').all();
    for (const control of controls) {
      const box = await control.boundingBox();
      if (!box) continue;
      expect(Math.round(box.width)).toBeGreaterThanOrEqual(32);
      expect(Math.round(box.height)).toBeGreaterThanOrEqual(32);
    }
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

test.describe('U3 managed Blockbench roundtrip', () => {
  test.beforeEach(async ({ page }) => {
    await page.addInitScript(() => {
      const running = {
        schemaVersion: '1.0', state: 'running',
        assetId: 'asset:1111111111111111111111111111111111111111111111111111111111111111',
        relativePath: 'assets/coppertrails/models/block/copper_lamp.bbmodel', processId: 4242,
        exitCode: null, openedSha256: 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
        currentSha256: 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
        blockbenchVersion: '5.1.6', diagnosticCode: null, recoveryPointId: 'rp-blockbench-1',
        workspaceRevision: null, changeCommitted: false
      };
      (window as unknown as { __blockbenchStatusCalls: number }).__blockbenchStatusCalls = 0;
      (window as unknown as Record<string, unknown>).__COPPERBENCH_BLOCKBENCH_HOST__ = {
        schemaVersion: '1.0',
        openAsset: async () => running,
        status: async () => {
          (window as unknown as { __blockbenchStatusCalls: number }).__blockbenchStatusCalls += 1;
          return {
            ...running,
            state: 'exited',
            exitCode: 0,
            currentSha256: 'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb',
            workspaceRevision: 7,
            changeCommitted: true
          };
        }
      };
    });
    await page.goto('/');
    await page.waitForSelector('[data-testid="app-shell"]');
    await page.click('[data-testid="nav-assets"]');
    await expect(page.locator('[data-testid="asset-browser"]')).toBeVisible();
  });

  test('refreshes Asset Center after a managed Blockbench save exits', async ({ page }) => {
    await page.getByTestId('asset-open-blockbench').click();
    await expect(page.getByTestId('asset-notice')).toContainText('revision 7');
    await expect(page.getByTestId('asset-notice')).toContainText('rp-blockbench-1');
    await expect.poll(() => page.evaluate(() =>
      (window as unknown as { __blockbenchStatusCalls: number }).__blockbenchStatusCalls)).toBeGreaterThan(0);
  });
});
