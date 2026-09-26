import { test, expect, type Page } from '@playwright/test';

const cases = [
  { name: 'zero', errors: 0, warnings: 0, invalid: 0 },
  { name: 'one-element-many-errors', errors: 3, warnings: 0, invalid: 1 },
  { name: 'assets-only', errors: 2, warnings: 0, invalid: 0 },
  { name: 'mixed', errors: 4, warnings: 2, invalid: 1 },
  { name: 'historical-task-only', errors: 0, warnings: 0, invalid: 0 },
  { name: 'index-pending', errors: 0, warnings: 1, invalid: 0 }
];

async function fixture(page: Page, scenario: typeof cases[number]) {
  await page.addInitScript(scenario => {
    const modulePath = '/src/mock/mockBridge.ts';
    const ready = import(modulePath).then(({ MockCoreBridge }) => new MockCoreBridge());
    const historicalTask = { id: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaa17', kind: 'build', state: 'failed',
      cancellable: false, progress: 1, startedAt: '2026-09-20T10:00:00Z',
      stage: { key: 'task.build.failed', fallback: 'Build failed' },
      diagnostics: { error: 8, warning: 0, info: 0 }, restoredFromHistory: true };
    window.copperbenchHost = {
      workspaceId: '11111111-1111-4111-8111-111111111111', onEvent() { return () => {}; },
      async invoke(raw) {
        const core = await ready, request = JSON.parse(raw);
        const result = request.messageType === 'handshake' ? await core.negotiateHandshake(request)
          : request.messageType === 'command' ? await core.sendCommand(request) : await core.sendQuery(request);
        if (request.operation === 'get_workbench') {
          result.data.elementCounts.invalid = scenario.invalid;
          result.data.activeTasks = [];
          result.data.recentTasks = scenario.name === 'historical-task-only' ? [historicalTask] : [];
        }
        if (request.operation === 'get_task' && request.payload.taskId === historicalTask.id) {
          result.status = 'succeeded'; result.diagnostics = [];
          result.data = { task: historicalTask, diagnostics: [], logs: [{ sequence: 1,
            timestamp: historicalTask.startedAt, level: 'error', text: 'Historical failure evidence 原始日志' }] };
        }
        if (request.operation === 'get_workspace_health') {
          result.data.elements.invalid = scenario.invalid;
          result.data.assets.summary.missingReferences = scenario.name === 'assets-only' ? 2 : scenario.name === 'mixed' ? 1 : 0;
          result.data.assets.summary.unusedAssets = 0;
          result.data.diagnostics = { total: scenario.errors + scenario.warnings, error: scenario.errors,
            warning: scenario.warnings, info: 0, scope: 'workspace_current', snapshotId: scenario.name,
            collectionState: scenario.name === 'index-pending' ? 'partial' : 'complete', items: [] };
          if (scenario.name === 'index-pending') result.data.assets = { indexed: false, reasonCode: 'ASSET_INDEX_NOT_READY' };
          if (scenario.name === 'historical-task-only') result.data.tasks.recentFailed = [];
        }
        return JSON.stringify(result);
      }
    };
  }, scenario);
}

for (const scenario of cases) {
  test(`${scenario.name}: bilingual current diagnostic counts and keyboard navigation`, async ({ page }, testInfo) => {
    await fixture(page, scenario);
    await page.goto('/');
    const counts = page.getByTestId('workspace-health-diagnostics');
    const badge = page.getByTestId('diagnostics-badge');
    await expect(counts).toContainText(`${scenario.errors + scenario.warnings} 条 · ${scenario.errors} 错误`);
    await expect(badge).toContainText(`${scenario.errors} 错误，${scenario.warnings} 警告`);
    // The matrix starts at each supported viewport/DPR before switching language.
    for (const card of await page.locator('[data-testid^="workspace-health-"]').all()) {
      expect(await card.evaluate(el => el.scrollWidth <= el.clientWidth + 1), await card.getAttribute('data-testid')).toBe(true);
    }
    expect(await page.getByTestId('status-footer').evaluate(el => el.scrollWidth <= el.clientWidth + 1)).toBe(true);
    await page.getByTestId('ui-language-select').selectOption('en');
    await expect(page.locator('html')).toHaveAttribute('lang', 'en');
    await expect(counts).toContainText(`${scenario.errors + scenario.warnings} total · ${scenario.errors} errors`);
    await expect(badge).toContainText(`${scenario.errors} errors, ${scenario.warnings} ${scenario.warnings === 1 ? 'warning' : 'warnings'}`);
    if (scenario.name === 'index-pending') {
      await expect(badge).toContainText('(partial checks)');
      await expect(page.getByTestId('workspace-health-panel').getByRole('status')).toContainText('Some checks are incomplete.');
    }
    for (const [width, height] of [[1280, 720], [1920, 1080]]) {
      await page.setViewportSize({ width, height });
      // Check each health card's complete text rather than only the document scrollbar.
      for (const card of await page.locator('[data-testid^="workspace-health-"]').all()) {
        expect(await card.evaluate(el => el.scrollWidth <= el.clientWidth + 1), await card.getAttribute('data-testid')).toBe(true);
      }
      expect(await page.getByTestId('status-footer').evaluate(el => el.scrollWidth <= el.clientWidth + 1)).toBe(true);
      const language = await page.getByTestId('ui-language-select').boundingBox();
      expect(language!.x + language!.width).toBeLessThanOrEqual(width);
      if (scenario.name === 'mixed') await page.screenshot({ path: testInfo.outputPath(`english-overview-${width}.png`) });
    }
    await page.getByTestId('nav-assets').click();
    await badge.focus();
    await page.keyboard.press('Enter');
    await expect(page.getByTestId('workspace-health-panel')).toBeVisible();
    await expect(page.getByTestId('workspace-health-panel')).toBeFocused();
    if (scenario.name === 'historical-task-only') {
      await page.getByTestId('recent-tasks-button').click();
      await expect(page.getByTestId('task-drawer')).toContainText('Historical failure evidence 原始日志');
      await expect(badge).toContainText('0 errors, 0 warnings');
    }
    await page.reload();
    await expect(page.getByTestId('ui-language-select')).toHaveValue('en');
    await expect(counts).toContainText(`${scenario.errors + scenario.warnings} total · ${scenario.errors} errors`);
  });
}

test('switching languages preserves the selected element and its unsaved field draft', async ({ page }) => {
  await page.goto('/');
  await page.getByTestId('nav-elements').click();
  await page.getByTestId('create-element-btn').click();
  await page.getByTestId('create-element-name-input').fill('language_draft');
  await page.getByTestId('create-element-submit-btn').click();
  const field = page.getByTestId('element-inspector').getByRole('textbox').first();
  await field.fill('Draft 中文 user content');
  await page.getByTestId('ui-language-select').selectOption('en');
  await expect(field).toHaveValue('Draft 中文 user content');
  await expect(page.getByTestId('nav-elements')).toContainText('Mod elements');
  await page.getByTestId('ui-language-select').selectOption('zh');
  await expect(field).toHaveValue('Draft 中文 user content');
  await expect(page.getByTestId('nav-elements')).toContainText('模组元素');
});

test('language remains usable when preference storage is unavailable', async ({ page }) => {
  await page.addInitScript(() => {
    const original = Storage.prototype.setItem;
    Storage.prototype.setItem = function(key, value) {
      if (key === 'copperbench.ui.locale') throw new DOMException('Storage unavailable', 'SecurityError');
      return original.call(this, key, value);
    };
  });
  await page.goto('/');
  await page.getByTestId('ui-language-select').selectOption('en');
  await expect(page.getByTestId('nav-hub')).toContainText('Overview');
  await expect(page.locator('html')).toHaveAttribute('lang', 'en');
});

test('diagnostic detail actions preserve element and asset targets after a language change', async ({ page }) => {
  await page.addInitScript(() => {
    const modulePath = '/src/mock/mockBridge.ts';
    const ready = import(modulePath).then(({ MockCoreBridge }) => new MockCoreBridge());
    window.copperbenchHost = {
      workspaceId: '11111111-1111-4111-8111-111111111111', onEvent() { return () => {}; },
      async invoke(raw) {
        const core = await ready, request = JSON.parse(raw);
        const result = request.messageType === 'handshake' ? await core.negotiateHandshake(request)
          : request.messageType === 'command' ? await core.sendCommand(request) : await core.sendQuery(request);
        if (request.operation === 'get_workspace_health') {
          const elements = await core.sendQuery({ ...request, operation: 'list_mod_elements', payload: {} });
          const assets = await core.sendQuery({ ...request, operation: 'list_assets', payload: {} });
          const elementId = elements.data.items.find((el: { type: string }) => el.type === 'block').id;
          const assetId = assets.data.assets[1].id;
          sessionStorage.setItem('diagnosticTargetAsset', assetId);
          result.data.diagnostics = { total: 2, error: 2, warning: 0, info: 0, scope: 'workspace_current',
            collectionState: 'complete', snapshotId: 'language-actions', items: [
              { code: 'ELEMENT_FIXTURE', severity: 'error', recoverable: true, path: '/fields/hardness', elementId,
                message: { key: 'field.hardness', fallback: 'Hardness' }, actions: [{ id: 'element', kind: 'open_field',
                  label: { key: 'action.open_field', fallback: 'Locate field' }, target: '/fields/hardness' }] },
              { code: 'ASSET_FIXTURE', severity: 'error', recoverable: true, path: null,
                message: { key: 'action.open_asset', fallback: 'Open asset' }, actions: [{ id: 'asset', kind: 'open_asset',
                  label: { key: 'action.open_asset', fallback: 'Open asset' }, target: assetId }] }
            ] };
        }
        return JSON.stringify(result);
      }
    };
  });
  await page.goto('/');
  await page.getByTestId('ui-language-select').selectOption('en');
  const summary = page.getByTestId('workspace-health-panel').locator('summary');
  await summary.focus();
  await page.keyboard.press('Enter');
  const fieldAction = page.getByRole('button', { name: 'Locate field', exact: true });
  await fieldAction.focus();
  await page.keyboard.press('Enter');
  await expect(page.getByTestId('element-inspector')).toBeVisible();
  await expect(page.locator('[data-field-path="/fields/hardness"]')).toBeFocused();
  await page.getByTestId('diagnostics-badge').click();
  await summary.click();
  await page.getByTestId('workspace-health-panel').getByRole('button', { name: 'Open asset', exact: true }).click();
  const assetId = await page.evaluate(() => sessionStorage.getItem('diagnosticTargetAsset'));
  await expect(page.locator(`[data-asset-id="${assetId}"]`)).toBeFocused();
  await expect(page.getByTestId('asset-stable-id')).toHaveText(assetId!);
});

test('language control is a native client region and does not initiate window dragging', async ({ page }) => {
  await page.addInitScript(() => {
    window.__COPPERBENCH_WINDOW_HOST__ = {
      systemFrame: false, chromeRegionSchemaVersion: '1.0', invoke: async () => undefined,
      reportChromeRegions: async snapshot => sessionStorage.setItem('languageChromeSnapshot', JSON.stringify(snapshot)),
      pointerGesture: gesture => { if (gesture.phase === 'begin') sessionStorage.setItem('unexpectedDrag', 'true'); }
    };
  });
  await page.goto('/');
  const language = page.getByTestId('ui-language-select');
  await language.click();
  await page.keyboard.press('Escape');
  await language.selectOption('en');
  await expect.poll(() => page.evaluate(() => {
    const snapshot = JSON.parse(sessionStorage.getItem('languageChromeSnapshot') ?? '{}');
    return snapshot.regions?.find((region: { id: string }) => region.id === 'language')?.kind;
  })).toBe('client');
  expect(await page.evaluate(() => sessionStorage.getItem('unexpectedDrag'))).toBeNull();
});

test('diagnostic overview remains readable in a browser at 150 percent density in both themes', async ({ browser }, testInfo) => {
  const context = await browser.newContext({ viewport: { width: 1280, height: 720 }, deviceScaleFactor: 1.5 });
  try {
    const page = await context.newPage();
    await fixture(page, cases.find(item => item.name === 'mixed')!);
    await page.goto('/');
    for (const scheme of ['light', 'dark'] as const) {
      await page.emulateMedia({ colorScheme: scheme });
      for (const locale of ['zh', 'en']) {
        await page.getByTestId('ui-language-select').selectOption(locale);
        await expect(page.locator('html')).toHaveAttribute('data-theme', scheme);
        for (const card of await page.locator('[data-testid^="workspace-health-"]').all()) {
          expect(await card.evaluate(el => el.scrollWidth <= el.clientWidth + 1)).toBe(true);
        }
        await page.screenshot({ path: testInfo.outputPath(`overview-150-${scheme}-${locale}.png`) });
      }
    }
  } finally { await context.close(); }
});
