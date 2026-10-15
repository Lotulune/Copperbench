import { test, expect, type Page } from '@playwright/test';

async function installAssets(page: Page, mode: 'lost' | 'rejected' = 'lost') {
  await page.addInitScript(({ mode }) => {
    const path = '/src/mock/mockBridge.ts';
    const ready = import(path).then(({ MockCoreBridge }) => new MockCoreBridge());
    let projection: any;
    const previews = new Map<string, any>();
    const fixture = { writes: [] as any[], calls: [] as string[], committed: false, failRead: false, mode };
    Object.assign(window, { assetRecovery: fixture });
    const diagnostic = (code: string, reason: string) => ({ code, severity: 'error',
      message: { key: 'fixture.asset', fallback: reason, args: { original: 'machine reason 中文' } },
      path: '/assets/original', elementId: null, recoverable: true, actions: [] });
    window.copperbenchHost = {
      workspaceId: '11111111-1111-4111-8111-111111111111', onEvent() { return () => {}; },
      async invoke(raw) {
        const request = JSON.parse(raw), core = await ready;
        fixture.calls.push(request.operation ?? 'handshake');
        if (['import_asset', 'import_asset_batch', 'move_asset'].includes(request.operation)) {
          fixture.writes.push(structuredClone(request));
          if (mode === 'rejected') return JSON.stringify({ messageType: 'command_result', schemaVersion: '1.0',
            requestId: request.requestId, workspaceId: request.workspaceId, operation: request.operation,
            status: 'rejected', newRevision: core.getState().workbench.workspace.revision,
            recoveryPointId: null, task: null, data: null, conflict: null, denial: null,
            diagnostics: [diagnostic('ASSET_REVIEW_REJECTED', 'The reviewed operation was rejected.')] });
        }
        const result = request.messageType === 'handshake' ? await core.negotiateHandshake(request)
          : request.messageType === 'command' ? await core.sendCommand(request) : await core.sendQuery(request);
        if (request.workspaceId) result.workspaceId = request.workspaceId;
        if (request.operation === 'list_assets') {
          if (!projection) projection = structuredClone(result.data);
          if (fixture.failRead) {
            result.status = 'rejected'; result.data = null;
            result.diagnostics = [diagnostic('ASSET_CONFIRMATION_READ_FAILED', 'Asset index read unavailable.')];
          } else result.data = structuredClone(projection);
        }
        if (request.operation?.startsWith('preview_asset_') && result.data) {
          result.data.planToken += '::read-' + fixture.calls.length;
          if (fixture.committed && request.operation === 'preview_asset_move') {
            result.status = 'rejected'; result.data = null;
            result.diagnostics = [diagnostic('ASSET_MOVE_SOURCE_MISSING', 'The original source is no longer at that path.')];
          } else if (fixture.committed && request.operation === 'preview_asset_import') {
            const existing = projection.assets.find((asset: any) => asset.relativePath === request.payload.targetRelativePath);
            if (existing) Object.assign(result.data, { conflict: 'IDENTICAL', targetSha256: existing.sha256,
              canApply: false, issueCodes: [], requiresReplacementConfirmation: false });
          } else if (fixture.committed && request.operation === 'preview_asset_import_batch') {
            result.data.items = result.data.items.map((item: any) => ({ ...item, conflict: 'IDENTICAL',
              targetSha256: item.sourceSha256, canApply: false, issueCodes: [] }));
            Object.assign(result.data, { canApply: false, createCount: 0, replaceCount: 0,
              identicalCount: result.data.items.length, changedCount: 0, issueCodes: [] });
          }
          if (result.data) previews.set(result.data.planToken, structuredClone(result.data));
        }
        if (['import_asset', 'import_asset_batch', 'move_asset'].includes(request.operation) && !fixture.committed) {
          if (result.status !== 'committed') throw new Error('The fixture did not commit.');
          const preview = previews.get(request.payload.planToken);
          const sample = projection.assets.find((asset: any) => asset.category === 'TEXTURE');
          if (request.operation === 'move_asset') {
            projection.assets = projection.assets.map((asset: any) => asset.id === preview.sourceAssetId
              ? { ...asset, id: preview.targetAssetId, relativePath: preview.targetRelativePath, name: 'moved.png' } : asset);
          } else {
            const items = request.operation === 'import_asset_batch' ? preview.items : [preview];
            for (const item of items) {
              projection.assets = projection.assets.filter((asset: any) => asset.relativePath !== item.targetRelativePath);
              projection.assets.push({ ...sample, id: 'asset:fixture:' + item.targetRelativePath,
                relativePath: item.targetRelativePath, name: item.targetRelativePath.split('/').at(-1), sha256: item.sourceSha256 });
            }
          }
          fixture.committed = true;
          throw new Error('Asset commit completed, but the response was lost.');
        }
        return JSON.stringify(result);
      }
    };
  }, { mode });
}
async function openReview(page: Page, kind: 'import' | 'batch' | 'move') {
  await page.goto('/');
  await page.getByTestId('nav-assets').click();
  await expect(page.getByTestId('asset-browser')).toBeVisible();
  if (kind === 'import') await page.getByTestId('asset-import-button').click();
  if (kind === 'batch') {
    await page.getByLabel('更多资产操作', { exact: true }).click();
    await page.getByTestId('asset-batch-import-button').click();
  }
  if (kind === 'move') {
    await page.getByTestId('asset-category-texture').click();
    await page.getByLabel('文件操作', { exact: true }).click();
    await page.getByTestId('asset-move-button').click();
    await page.getByTestId('asset-move-target').fill('assets/coppertrails/textures/block/confirmed_move.png');
    await page.getByTestId('asset-move-preview').click();
  }
  await expect(page.getByTestId('asset-' + kind + '-commit')).toBeEnabled();
}

for (const kind of ['import', 'batch', 'move'] as const) {
  const reviewId = kind === 'batch' ? 'asset-batch-import-review' : 'asset-' + kind + '-review';
  const targetId = kind === 'batch' ? 'asset-batch-target-0' : 'asset-' + kind + '-target';
  test(kind + ': a committed but lost operation survives closing, navigation, locale and failed reads without replay', async ({ page }) => {
    await installAssets(page);
    await openReview(page, kind);
    const originalTarget = await page.getByTestId(targetId).inputValue();
    await page.getByTestId('asset-' + kind + '-commit').click();
    await expect(page.getByTestId('asset-' + kind + '-unknown')).toBeVisible();
    await expect(page.getByTestId('asset-' + kind + '-commit')).toBeDisabled();
    await page.keyboard.press('Escape');
    await expect(page.getByTestId(reviewId)).toHaveCount(0);
    await expect(page.getByTestId('asset-pending-recovery')).toContainText('结果待确认');
    await page.getByTestId('nav-elements').click();
    await page.getByTestId('nav-assets').click();
    await page.evaluate(async () => { const path = '/src/i18n/locale.ts'; (await import(path)).setUiLocale('en'); });
    await expect(page.getByTestId('asset-pending-recovery')).toContainText('result is unknown');
    await page.getByTestId('asset-' + kind + '-resume').focus();
    await page.keyboard.press('Enter');
    await expect(page.getByTestId(targetId)).toHaveValue(originalTarget);
    await page.evaluate(() => { (window as any).assetRecovery.failRead = true; });
    const preview = page.getByTestId('asset-' + kind + '-preview');
    await preview.focus();
    await page.keyboard.press('Enter');
    await expect(preview).toBeEnabled();
    await expect(page.getByTestId('asset-' + kind + '-unknown')).toBeVisible();
    await expect(page.getByTestId(targetId)).toHaveValue(originalTarget);
    const review = page.getByTestId(reviewId);
    await review.getByText('Original diagnostics', { exact: true }).click();
    await expect(review.locator('pre').last()).toContainText('ASSET_CONFIRMATION_READ_FAILED');
    await expect(review.locator('pre').last()).toContainText('machine reason 中文');
    expect(await page.evaluate(() => (window as any).assetRecovery.writes.length)).toBe(1);
    await page.evaluate(() => { (window as any).assetRecovery.failRead = false; });
    await preview.click();
    if (kind === 'move') {
      // A moved source is no longer readable under its old identity. Keep unknown
      // and the original diagnostic; do not invent a new source owner or success.
      await expect(page.getByTestId('asset-move-unknown')).toBeVisible();
      await expect(review.locator('pre').last()).toContainText('ASSET_MOVE_SOURCE_MISSING');
      await expect(review.locator('pre').last()).toContainText('ASSET_CONFIRMATION_READ_FAILED');
    } else {
      await expect(page.getByTestId('asset-' + kind + '-unknown')).toHaveCount(0);
      await expect(review).toContainText(kind === 'import' ? 'Target content is identical' : 'Identical / skip');
    }
    await expect(page.getByTestId('asset-' + kind + '-commit')).toBeDisabled();
    expect(await page.evaluate(() => (window as any).assetRecovery.writes.length)).toBe(1);
    if (kind === 'move') {
      await page.getByTestId('asset-move-discard').focus();
      await page.keyboard.press('Enter');
      await expect(page.getByTestId(reviewId)).toHaveCount(0);
      await expect(page.getByTestId('asset-pending-recovery')).toHaveCount(0);
      await expect(page.getByTestId('asset-search')).toBeFocused();
      expect(await page.evaluate(() => (window as any).assetRecovery.writes.length)).toBe(1);
    }
    const calls = await page.evaluate(() => (window as any).assetRecovery.calls);
    expect(calls.filter((operation: string) => operation === (kind === 'batch' ? 'import_asset_batch' : kind === 'move' ? 'move_asset' : 'import_asset'))).toHaveLength(1);
  });

  test(kind + ': an explicit rejection keeps machine diagnostics and remains distinct from an unknown write', async ({ page }) => {
    await installAssets(page, 'rejected');
    await openReview(page, kind);
    await page.getByTestId('asset-' + kind + '-commit').click();
    await expect(page.getByTestId('asset-' + kind + '-unknown')).toHaveCount(0);
    const review = page.getByTestId(reviewId);
    await expect(review).toContainText('The reviewed operation was rejected.');
    await review.getByText('原始诊断', { exact: true }).click();
    await expect(review.locator('pre')).toContainText('ASSET_REVIEW_REJECTED');
    await page.evaluate(async () => { const path = '/src/i18n/locale.ts'; (await import(path)).setUiLocale('en'); });
    await expect(review.locator('pre')).toContainText('machine reason 中文');
    await expect(page.getByTestId('asset-' + kind + '-commit')).toBeEnabled();
    expect(await page.evaluate(() => (window as any).assetRecovery.writes.length)).toBe(1);
  });
}

test('an edited import target retains the submitted target and requires a successful read and fresh preview before a new deliberate commit', async ({ page }) => {
  await installAssets(page);
  await openReview(page, 'import');
  const originalTarget = await page.getByTestId('asset-import-target').inputValue();
  await page.getByTestId('asset-import-commit').click();
  await expect(page.getByTestId('asset-import-unknown')).toBeVisible();
  const nextTarget = 'assets/coppertrails/textures/imported/new_deliberate_target.png';
  await page.getByTestId('asset-import-target').fill(nextTarget);
  await expect(page.getByTestId('asset-import-commit')).toBeDisabled();
  await page.getByTestId('asset-import-review').getByText('原请求目标与读取结果', { exact: true }).click();
  await expect(page.getByTestId('asset-import-review')).toContainText(originalTarget);
  await page.getByTestId('asset-import-preview').click();
  await expect(page.getByTestId('asset-import-unknown')).toHaveCount(0);
  await expect(page.getByTestId('asset-import-target')).toHaveValue(nextTarget);
  await expect(page.getByTestId('asset-import-commit')).toBeEnabled();
  expect(await page.evaluate(() => (window as any).assetRecovery.writes.length)).toBe(1);
  await page.getByTestId('asset-import-commit').click();
  await expect(page.getByTestId('asset-import-review')).toHaveCount(0);
  const requests = await page.evaluate(() => (window as any).assetRecovery.writes);
  expect(requests).toHaveLength(2);
  expect(requests[1].payload.planToken).not.toBe(requests[0].payload.planToken);
  expect(requests[1].expectedRevision).toBeGreaterThan(requests[0].expectedRevision);
});
