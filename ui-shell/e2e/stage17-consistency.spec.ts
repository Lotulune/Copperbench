import { test, expect } from '@playwright/test';

test('asset resource count stays accurate and readable at supported window widths', async ({ page }, testInfo) => {
  await page.goto('/');
  await page.getByTestId('nav-assets').click();
  const header = page.locator('.asset-library-heading');
  const count = header.getByText(/^\d+ 个资源文件$/);
  await expect(count).toHaveText(/^[1-9]\d* 个资源文件$/);
  const assetCount = await page.getByTestId(/^asset-card-/).count();
  expect(assetCount).toBeGreaterThan(0);
  for (const [width, height] of [[1280, 720], [1366, 768], [1920, 1080]]) {
    await page.setViewportSize({ width, height });
    await expect(count).toHaveText(`${assetCount} 个资源文件`);
    await expect(count).toBeVisible();
    expect(await count.evaluate(element => element.scrollWidth <= element.clientWidth)).toBe(true);
    const text = await count.boundingBox(), bounds = await header.boundingBox();
    expect(text).not.toBeNull(); expect(bounds).not.toBeNull();
    expect(text!.width).toBeGreaterThan(40);
    expect(text!.height).toBeLessThan(25);
    expect(text!.x).toBeGreaterThanOrEqual(bounds!.x);
    expect(text!.y).toBeGreaterThanOrEqual(bounds!.y);
    expect(text!.x + text!.width).toBeLessThanOrEqual(bounds!.x + bounds!.width);
    expect(text!.y + text!.height).toBeLessThanOrEqual(bounds!.y + bounds!.height);
    await header.screenshot({ path: testInfo.outputPath(`asset-count-${width}.png`) });
  }
});

test('cold element shows saved definition and pending generation without blocking field editing', async ({ page }) => {
  await page.addInitScript(() => {
    const modulePath = '/src/mock/mockBridge.ts';
    const ready = import(modulePath).then(({ MockCoreBridge }) => new MockCoreBridge());
    let prepared = false;
    let revision = 0;
    const listeners = new Set<(raw: string) => void>();
    window.addEventListener('stage17-prepared', () => {
      prepared = true;
      const event = JSON.stringify({ messageType: 'event', schemaVersion: '1.0', sequence: 1, revision,
        workspaceId: '11111111-1111-4111-8111-111111111111', event: 'task_completed', payload: {
          task: { id: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaa03', kind: 'build', state: 'succeeded', cancellable: false,
            progress: 1, stage: { key: 'task.build.completed', fallback: 'Build completed' }, diagnostics: { error: 0, warning: 0, info: 0 } }
        } });
      listeners.forEach(listener => listener(event));
    });
    window.copperbenchHost = {
      workspaceId: '11111111-1111-4111-8111-111111111111', onEvent(listener) { listeners.add(listener); return () => listeners.delete(listener); },
      async invoke(raw) {
        const core = await ready, request = JSON.parse(raw);
        const result = request.messageType === 'handshake' ? await core.negotiateHandshake(request)
          : request.messageType === 'command' ? await core.sendCommand(request) : await core.sendQuery(request);
        if (typeof result.revision === 'number') revision = result.revision;
        if (request.operation === 'get_mod_element_editor' && result.data) {
          result.data.configuration = { status: 'consistent', generationState: prepared ? 'ready' : 'pending', differences: [] };
        }
        return JSON.stringify(result);
      }
    };
  });
  await page.goto('/');
  await page.getByTestId('nav-elements').click();
  await page.getByTestId('create-element-btn').click();
  await page.getByTestId('create-element-name-input').fill('cold_block');
  await page.getByTestId('create-element-submit-btn').click();
  const notice = page.getByTestId('generation-pending-notice');
  await expect(notice).toContainText('定义已保存，源码尚待生成');
  await expect(notice).toContainText('生成');
  await expect(notice).toHaveAttribute('role', 'status');
  expect(await notice.evaluate(element => element.scrollWidth <= element.clientWidth)).toBe(true);
  await expect(page.getByTestId('element-inspector').getByRole('textbox').first()).toBeEditable();
  const field = page.getByTestId('element-inspector').getByRole('textbox').first();
  await field.fill('Unsaved field draft');
  await page.evaluate(() => window.dispatchEvent(new Event('stage17-prepared')));
  await expect(notice).toHaveCount(0);
  await expect(field).toHaveValue('Unsaved field draft');
});

test('resource details distinguish vanilla resolution from unavailable dependencies and retain each location', async ({ page }) => {
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
            { sourceAssetId: asset.id, sourcePath: asset.relativePath, sourcePointer: '/parent', rawValue: 'minecraft:block/cube_all',
              targetPath: 'assets/minecraft/models/block/cube_all.json', targetAssetId: null, kind: 'RESOURCE_ID',
              resolution: 'vanilla_resolved', resourceSource: 'minecraft-client.jar sha256=' + 'a'.repeat(64), resourceVersion: '1.21.1' },
            { sourceAssetId: asset.id, sourcePath: asset.relativePath, sourcePointer: '/textures/all', rawValue: 'thirdparty:block/lamp',
              targetPath: 'assets/thirdparty/textures/block/lamp.png', targetAssetId: null, kind: 'RESOURCE_ID',
              resolution: 'unverified', resourceSource: 'external_catalog_unavailable', resourceVersion: '1.21.1' }
          ];
          asset.health.outboundCount = 2;
        }
        return JSON.stringify(result);
      }
    };
  });
  await page.goto('/');
  await page.getByTestId('nav-assets').click();
  await page.getByTestId(`asset-card-asset:${'1'.repeat(64)}`).click();
  await page.getByTestId('asset-references-disclosure').locator(':scope > summary').click();
  const references = page.getByTestId('asset-outgoing-references');
  await expect(references).toBeVisible();
  const vanilla = references.getByRole('listitem').filter({ hasText: 'minecraft:block/cube_all' });
  const dependency = references.getByRole('listitem').filter({ hasText: 'thirdparty:block/lamp' });
  await expect(vanilla.getByText('原版已解析', { exact: true })).toBeVisible();
  await expect(vanilla.getByText('assets/minecraft/models/block/cube_all.json', { exact: true })).toBeVisible();
  await vanilla.locator('summary').focus();
  await page.keyboard.press('Enter');
  await expect(vanilla.locator('details')).toHaveAttribute('open', '');
  await expect(vanilla.getByText('位置：/parent', { exact: true })).toBeVisible();
  await expect(vanilla.getByText('版本：1.21.1', { exact: true })).toBeVisible();
  await expect(vanilla.getByText('minecraft-client.jar sha256=' + 'a'.repeat(64), { exact: true })).toBeVisible();
  await expect(dependency.getByText('尚未验证', { exact: true })).toBeVisible();
  await expect(dependency.getByText('已证实缺失', { exact: true })).toHaveCount(0);
  await expect(dependency.getByText('assets/thirdparty/textures/block/lamp.png', { exact: true })).toBeVisible();
  await dependency.locator('summary').click();
  await expect(dependency.getByText('位置：/textures/all', { exact: true })).toBeVisible();
  await expect(dependency.getByText('版本：1.21.1', { exact: true })).toBeVisible();
  await expect(dependency.getByText('external_catalog_unavailable', { exact: true })).toBeVisible();
  expect(await references.evaluate(element => element.scrollWidth <= element.clientWidth)).toBe(true);
});

test('global diagnostic counts share the Core snapshot instead of invalid element count', async ({ page }) => {
  await page.addInitScript(() => {
    const modulePath = '/src/mock/mockBridge.ts';
    const ready = import(modulePath).then(({ MockCoreBridge }) => new MockCoreBridge());
    window.copperbenchHost = {
      workspaceId: '11111111-1111-4111-8111-111111111111', onEvent() { return () => {}; },
      async invoke(raw) {
        const core = await ready; const request = JSON.parse(raw);
        const result = request.messageType === 'handshake' ? await core.negotiateHandshake(request)
          : request.messageType === 'command' ? await core.sendCommand(request) : await core.sendQuery(request);
        if (request.operation === 'get_workbench' && result.data?.elementCounts) result.data.elementCounts.invalid = 0;
        if (request.operation === 'get_workspace_health') {
          result.data.diagnostics = { total: 7, error: 5, warning: 2, info: 0,
            scope: 'workspace_current', collectionState: 'partial', snapshotId: 'stage17-fixture', items: [] };
        }
        return JSON.stringify(result);
      }
    };
  });
  await page.goto('/');
  await expect(page.getByTestId('workspace-health-diagnostics')).toContainText('7 条 · 5 错误');
  await expect(page.getByTestId('diagnostics-badge')).toContainText('5 错误，2 警告（部分检查）');
  await expect(page.getByText('错误诊断', { exact: true }).locator('..')).toContainText('5');
  await page.getByTestId('nav-assets').click();
  await page.getByTestId('diagnostics-badge').click();
  await expect(page.getByTestId('workspace-health-panel')).toBeVisible();
});

test('restored task observations are discoverable and selecting one loads its persisted logs', async ({ page }) => {
  await page.addInitScript(() => {
    const modulePath = '/src/mock/mockBridge.ts';
    const ready = import(modulePath).then(({ MockCoreBridge }) => new MockCoreBridge());
    const tasks = ['aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaa01', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaa02'].map((id, i) => ({
      id, kind: 'export', state: 'failed', cancellable: false, progress: 1,
      stage: { key: 'task.export.completed', fallback: 'Restored observation' }, startedAt: `2026-09-20T10:0${i}:00Z`,
      diagnostics: { error: 1, warning: 0, info: 0 }, restoredFromHistory: true
    }));
    window.copperbenchHost = {
      workspaceId: '11111111-1111-4111-8111-111111111111', onEvent() { return () => {}; },
      async invoke(raw) {
        const core = await ready; const request = JSON.parse(raw);
        const result = request.messageType === 'handshake' ? await core.negotiateHandshake(request)
          : request.messageType === 'command' ? await core.sendCommand(request) : await core.sendQuery(request);
        if (request.operation === 'get_workbench') { result.data.activeTasks = []; result.data.recentTasks = tasks; }
        if (request.operation === 'get_task' && tasks.some(task => task.id === request.payload.taskId)) {
          result.status = 'succeeded'; result.diagnostics = [];
          result.data = { task: tasks.find(task => task.id === request.payload.taskId), diagnostics: [],
            logs: [{ sequence: 1, timestamp: '2026-09-20T10:00:00Z', level: 'info', text: `Persisted evidence ${request.payload.taskId}` }] };
        }
        return JSON.stringify(result);
      }
    };
  });
  await page.goto('/');
  await page.getByTestId('recent-tasks-button').click();
  await expect(page.getByTestId('task-drawer')).toContainText('Persisted evidence aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaa01');
  await page.getByRole('combobox', { name: '最近任务' }).selectOption('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaa02');
  await expect(page.getByTestId('task-drawer')).toContainText('Persisted evidence aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaa02');
});
