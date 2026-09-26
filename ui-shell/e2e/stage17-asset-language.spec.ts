import { test, expect } from '@playwright/test';

test('loaded asset labels change language without reloading the projection or losing filters', async ({ page }) => {
  await page.addInitScript(() => {
    const modulePath = '/src/mock/mockBridge.ts';
    const ready = import(modulePath).then(({ MockCoreBridge }) => new MockCoreBridge());
    window.copperbenchHost = {
      workspaceId: '11111111-1111-4111-8111-111111111111', onEvent() { return () => {}; },
      async invoke(raw) {
        const request = JSON.parse(raw), core = await ready;
        if (request.operation === 'list_assets') sessionStorage.setItem('assetReads', String(Number(sessionStorage.getItem('assetReads') ?? 0) + 1));
        return JSON.stringify(request.messageType === 'handshake' ? await core.negotiateHandshake(request)
          : request.messageType === 'command' ? await core.sendCommand(request) : await core.sendQuery(request));
      }
    };
  });
  await page.goto('/');
  await page.getByTestId('nav-assets').click();
  await page.getByTestId('asset-category-texture').click();
  await page.getByTestId('asset-search').fill('copper_lamp');
  const id = await page.getByTestId('asset-stable-id').innerText();
  const reads = await page.evaluate(() => sessionStorage.getItem('assetReads'));
  await page.getByTestId('ui-language-select').selectOption('en');
  await expect(page.getByTestId('asset-category-texture')).toContainText('Textures');
  await expect(page.getByTestId('asset-details')).toContainText('Texture');
  await expect(page.getByTestId('asset-details')).toContainText('Workspace');
  await expect(page.getByTestId('asset-search')).toHaveValue('copper_lamp');
  await expect(page.getByTestId('asset-category-texture')).toHaveAttribute('aria-pressed', 'true');
  await expect(page.getByTestId('asset-stable-id')).toHaveText(id);
  expect(await page.evaluate(() => sessionStorage.getItem('assetReads'))).toBe(reads);
  await page.getByTestId('ui-language-select').selectOption('zh');
  await expect(page.getByTestId('asset-details')).toContainText('纹理');
  await expect(page.getByTestId('asset-stable-id')).toHaveText(id);
});

test('English resource evidence retains raw paths and supports keyboard expansion at both widths', async ({ page }, testInfo) => {
  await page.addInitScript(() => {
    const modulePath = '/src/mock/mockBridge.ts';
    const ready = import(modulePath).then(({ MockCoreBridge }) => new MockCoreBridge());
    window.copperbenchHost = {
      workspaceId: '11111111-1111-4111-8111-111111111111', onEvent() { return () => {}; },
      async invoke(raw) {
        const request = JSON.parse(raw), core = await ready;
        const result = request.messageType === 'handshake' ? await core.negotiateHandshake(request) : await core.sendQuery(request);
        if (request.operation === 'list_assets') {
          const asset = result.data.assets[0];
          result.data.references = [
            ...result.data.references.filter((reference: { sourceAssetId: string }) => reference.sourceAssetId !== asset.id),
            { sourceAssetId: asset.id, sourcePath: asset.relativePath, sourcePointer: '/parent', rawValue: 'minecraft:block/cube_all',
              targetPath: 'assets/minecraft/models/block/cube_all.json', targetAssetId: null, kind: 'RESOURCE_ID',
              resolution: 'vanilla_resolved', resourceSource: 'minecraft-client.jar sha256=' + 'a'.repeat(64), resourceVersion: '1.21.1' },
            { sourceAssetId: asset.id, sourcePath: asset.relativePath, sourcePointer: '/textures/中文', rawValue: 'thirdparty:block/lamp',
              targetPath: 'assets/thirdparty/textures/block/' + 'long_directory/'.repeat(7) + 'lamp.png', targetAssetId: null,
              kind: 'RESOURCE_ID', resolution: 'unverified', resourceSource: 'external_catalog_unavailable', resourceVersion: '1.21.1' }
          ];
          asset.health.outboundCount = 2;
        }
        return JSON.stringify(result);
      }
    };
  });
  await page.goto('/');
  await page.getByTestId('ui-language-select').selectOption('en');
  await page.getByTestId('nav-assets').click();
  const references = page.getByTestId('asset-outgoing-references');
  await expect(references).toContainText('Resolved in vanilla');
  for (const summary of await references.locator('summary').all()) {
    await summary.focus(); await page.keyboard.press('Enter');
  }
  await expect(references).toContainText('Reference location: /parent');
  await expect(references).toContainText('/textures/中文');
  await expect(references).toContainText('Resource version: 1.21.1');
  await expect(references).toContainText('This check did not download external resources.');
  await expect(references).toContainText('a'.repeat(64));
  for (const [width, height] of [[1280, 720], [1920, 1080]]) {
    await page.setViewportSize({ width, height });
    for (const selector of ['.asset-browser-header', '.asset-header-actions', '.asset-list-toolbar', '.asset-details-panel', '.asset-resource-resolution']) {
      for (const element of await page.locator(selector).all()) {
        expect(await element.evaluate(el => el.scrollWidth <= el.clientWidth + 1), selector).toBe(true);
      }
    }
    for (const label of await page.locator('.asset-import-inline-btn span, .asset-status-badge').all()) {
      const bounds = await label.boundingBox();
      expect(bounds!.height).toBeLessThan(25);
    }
    const toolbar = await page.locator('.asset-list-toolbar').boundingBox();
    const controls = await page.locator('.asset-toolbar-controls').boundingBox();
    const firstCard = await page.locator('.asset-card').first().boundingBox();
    expect(controls!.y + controls!.height).toBeLessThanOrEqual(toolbar!.y + toolbar!.height + 1);
    expect(firstCard!.y).toBeGreaterThanOrEqual(toolbar!.y + toolbar!.height);
    await references.scrollIntoViewIfNeeded();
    await expect(page.getByTestId('asset-browser')).toHaveCSS('opacity', '1');
    await expect(page.getByRole('heading', { name: 'Assets and models', exact: true })).toBeInViewport();
    // A viewport resize and nested scrolling both require a completed browser paint.
    await page.evaluate(() => new Promise<void>(resolve => requestAnimationFrame(() => requestAnimationFrame(() => resolve()))));
    await page.screenshot({ path: testInfo.outputPath(`english-assets-${width}.png`), animations: 'disabled' });
  }
});

test('import and move drafts survive language changes and notices translate after completion', async ({ page }, testInfo) => {
  await page.goto('/');
  await page.getByTestId('nav-assets').click();
  await page.getByTestId('asset-import-button').click();
  const target = 'assets/coppertrails/textures/imported/language_draft.png';
  await page.getByTestId('asset-import-target').fill(target);
  await page.getByTestId('ui-language-select').selectOption('en');
  await expect(page.getByTestId('asset-import-target')).toHaveValue(target);
  await expect(page.getByTestId('asset-import-commit')).toBeDisabled();
  await page.getByTestId('asset-import-preview').click();
  await expect(page.getByTestId('asset-import-conflict')).toHaveText('Create asset');
  await page.screenshot({ path: testInfo.outputPath('english-import-review.png'), animations: 'disabled' });
  await page.getByTestId('asset-import-commit').click();
  await expect(page.getByTestId('asset-notice')).toContainText(`Imported ${target}`);
  await page.getByTestId('ui-language-select').selectOption('zh');
  await expect(page.getByTestId('asset-notice')).toContainText(`已导入 ${target}`);
  await page.getByTestId('asset-move-button').click();
  const moved = 'assets/coppertrails/models/block/renamed_language.json';
  await page.getByTestId('asset-move-target').fill(moved);
  await page.getByTestId('ui-language-select').selectOption('en');
  await expect(page.getByTestId('asset-move-target')).toHaveValue(moved);
  await expect(page.getByTestId('asset-move-commit')).toBeDisabled();
  await expect(page.getByTestId('asset-move-review')).toContainText('Review the new path and each reference rewrite.');
});

test('an existing unavailable notice changes language without opening Blockbench again', async ({ page }) => {
  await page.addInitScript(() => {
    const snapshot = { schemaVersion: '1.0' as const, state: 'unavailable' as const, assetId: null, relativePath: null,
      processId: null, exitCode: null, openedSha256: null, currentSha256: null, blockbenchVersion: null,
      diagnosticCode: 'BLOCKBENCH_NOT_CONFIGURED', recoveryPointId: null, workspaceRevision: null, changeCommitted: false };
    window.__COPPERBENCH_BLOCKBENCH_HOST__ = {
      schemaVersion: '1.0', status: async () => snapshot,
      openAsset: async () => {
        sessionStorage.setItem('openAssetCalls', String(Number(sessionStorage.getItem('openAssetCalls') ?? 0) + 1));
        return snapshot;
      }
    };
  });
  await page.goto('/');
  await page.getByTestId('nav-assets').click();
  await page.getByTestId('asset-open-blockbench').click();
  await expect(page.getByTestId('asset-notice')).toContainText('尚未配置 Blockbench');
  await page.getByTestId('ui-language-select').selectOption('en');
  await expect(page.getByTestId('asset-notice')).toContainText('Blockbench is not configured.');
  expect(await page.evaluate(() => sessionStorage.getItem('openAssetCalls'))).toBe('1');
  await page.getByRole('button', { name: 'Dismiss notice' }).click();
  await expect(page.getByTestId('asset-notice')).toHaveCount(0);
});

test('a returned Core import diagnostic translates after failure without repeating the command', async ({ page }) => {
  await page.addInitScript(() => {
    const modulePath = '/src/mock/mockBridge.ts';
    const ready = import(modulePath).then(({ MockCoreBridge }) => new MockCoreBridge());
    window.copperbenchHost = {
      workspaceId: '11111111-1111-4111-8111-111111111111', onEvent() { return () => {}; },
      async invoke(raw) {
        const request = JSON.parse(raw), core = await ready;
        if (request.operation === 'import_asset') {
          sessionStorage.setItem('importCalls', String(Number(sessionStorage.getItem('importCalls') ?? 0) + 1));
          return JSON.stringify({ messageType: 'command_result', schemaVersion: '1.0', requestId: request.requestId,
            workspaceId: request.workspaceId, operation: request.operation, status: 'rejected', newRevision: request.expectedRevision, data: null,
            task: null, conflict: null, denial: null, diagnostics: [{ code: 'ASSET_IMPORT_REPLACE_CONFIRMATION_REQUIRED',
              severity: 'error', path: null, recoverable: true, actions: [], message: {
                key: 'diagnostic.asset_import_replace_confirmation_required',
                fallback: 'The target asset exists. Review the preview and explicitly confirm replacement.' } }] });
        }
        return JSON.stringify(request.messageType === 'handshake' ? await core.negotiateHandshake(request)
          : request.messageType === 'command' ? await core.sendCommand(request) : await core.sendQuery(request));
      }
    };
  });
  await page.goto('/');
  await page.getByTestId('nav-assets').click();
  await page.getByTestId('asset-import-button').click();
  await page.getByTestId('asset-import-commit').click();
  await expect(page.getByTestId('asset-import-error')).toContainText('必须明确确认替换');
  const target = await page.getByTestId('asset-import-target').inputValue();
  await page.getByTestId('ui-language-select').selectOption('en');
  await expect(page.getByTestId('asset-import-error')).toContainText('explicitly confirm replacement');
  await expect(page.getByTestId('asset-import-target')).toHaveValue(target);
  expect(await page.evaluate(() => sessionStorage.getItem('importCalls'))).toBe('1');
});

test('empty, loading, failed and no-results asset states have English recovery controls', async ({ page }) => {
  await page.goto('/');
  await page.getByTestId('ui-language-select').selectOption('en');
  await page.getByTestId('nav-assets').click();
  await page.getByTestId('asset-search').fill('no_such_asset');
  await expect(page.getByTestId('asset-browser-no-results')).toContainText('No matching assets');
  await page.getByRole('button', { name: 'Clear filters', exact: true }).click();
  await expect(page.getByTestId('asset-search')).toHaveValue('');
  for (const [scenario, testId, message] of [
    ['loading-workbench', 'loading', 'Loading workspace assets'],
    ['empty-workspace', 'empty', 'This workspace has no assets yet'],
    ['validation-failed', 'error', 'Asset data is temporarily unavailable']
  ]) {
    await page.getByTestId('scenario-switcher-trigger').click();
    await page.getByTestId(`scenario-btn-${scenario}`).click();
    await page.getByTestId('nav-assets').click();
    await expect(page.getByTestId(`asset-browser-${testId}`)).toContainText(message);
  }
  await expect(page.getByRole('button', { name: 'Reload', exact: true })).toBeVisible();
});
