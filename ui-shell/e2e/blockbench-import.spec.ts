import { test, expect } from '@playwright/test';

// Installed JCEF uses a custom scheme without crypto.randomUUID.
test.beforeEach(async ({ page }) => {
  await page.addInitScript(() => Object.defineProperty(crypto, 'randomUUID', { value: undefined }));
});

test('reviewed replacements require confirmation and imported models can be bound', async ({ page }) => {
  await page.addInitScript(() => {
    const modulePath = '/src/mock/mockBridge.ts';
    const ready = import(modulePath).then(({ MockCoreBridge }) => new MockCoreBridge());
    let revision = 42;
    const task: any = { taskId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', state: 'ready_to_import',
      targetRelativePath: 'models/lamp.bbmodel', editPath: 'C:/fixture/edit/model.bbmodel', editSha256: 'a'.repeat(64),
      candidatePath: 'C:/fixture/candidate.bbmodel', hasSavedChanges: true, recoveryPointId: 'rp-import', sourceChanged: false };
    (window as any).modelingCalls = [];
    window.copperbenchHost = {
      workspaceId: '11111111-1111-4111-8111-111111111111', onEvent() { return () => {}; },
      async invoke(raw) {
        const core = await ready; const request = JSON.parse(raw);
        if (request.operation === 'list_blockbench_tasks' || request.operation === 'preview_blockbench_import') return JSON.stringify({
          messageType: 'query_result', schemaVersion: '1.0', requestId: request.requestId, workspaceId: request.workspaceId,
          operation: request.operation, status: 'succeeded', revision, diagnostics: [],
          data: request.operation === 'list_blockbench_tasks' ? { tasks: [task] } : {
            planToken: 'review-token', canApply: true, requiresReplacementConfirmation: true,
            items: [{ targetRelativePath: 'models/lamp.bbmodel', conflict: 'REPLACE', sourceSha256: 'a'.repeat(64) }]
          }
        });
        if (request.operation === 'import_blockbench_task' || request.operation === 'bind_blockbench_model') {
          (window as any).modelingCalls.push(request);
          revision++;
          if (request.operation === 'import_blockbench_task') {
            task.state = 'imported'; task.importFiles = [{ targetRelativePath: 'src/main/resources/assets/test/models/custom/lamp.json' }];
          }
          return JSON.stringify({ messageType: 'command_result', schemaVersion: '1.0', requestId: request.requestId,
            workspaceId: request.workspaceId, operation: request.operation, status: 'committed', newRevision: revision,
            recoveryPointId: 'rp-import', task: null, data: task, conflict: null, denial: null, diagnostics: [] });
        }
        const result = request.messageType === 'handshake' ? await core.negotiateHandshake(request)
          : request.messageType === 'command' ? await core.sendCommand(request) : await core.sendQuery(request);
        if (request.operation === 'get_workbench' && result.data?.workspace) { result.data.workspace.revision = revision; result.revision = revision; }
        return JSON.stringify(result);
      }
    };
  });
  await page.goto('/'); await page.getByTestId('nav-assets').click();
  const panel = page.getByTestId('blockbench-tasks'); await panel.locator('summary').first().click();
  await panel.getByRole('button', { name: '刷新任务与保存状态' }).click();
  await panel.locator('.modeling-import-review summary').click();
  await panel.getByLabel('编辑目录内的导出文件').fill('export/lamp.json');
  await panel.getByLabel('工作区目标路径').fill('src/main/resources/assets/test/models/custom/lamp.json');
  await panel.getByRole('button', { name: '预览回导' }).click();
  await expect(panel.getByRole('button', { name: '应用回导' })).toBeDisabled();
  await panel.getByRole('checkbox').check();
  await panel.getByRole('button', { name: '应用回导' }).click();
  await expect(panel).toContainText('文件已回导');
  await panel.getByLabel('关联元素').selectOption({ index: 1 });
  await panel.getByLabel('导入的游戏模型').selectOption('test:custom/lamp');
  await panel.getByRole('button', { name: '关联所选模型' }).click();
  await expect(panel).toContainText('模型已关联到元素');
  const calls = await page.evaluate(() => (window as any).modelingCalls);
  expect(calls[0].payload.confirmReplace).toBe(true);
  expect(calls[0].payload.planToken).toBe('review-token');
  expect(calls[1].payload.modelResource).toBe('test:custom/lamp');
});

test('first workbench modeling setup is optional and skipping persists through the native host', async ({ page }) => {
  await page.addInitScript(() => {
    const modulePath = '/src/mock/mockBridge.ts';
    const ready = import(modulePath).then(({ MockCoreBridge }) => new MockCoreBridge());
    (window as any).skippedModelingSetup = false;
    (window as any).__COPPERBENCH_BLOCKBENCH_HOST__ = { schemaVersion: '1.0',
      dismissSetup: async () => { (window as any).skippedModelingSetup = true; },
      status: async () => ({ state: 'unavailable' }), openAsset: async () => ({ state: 'unavailable' }) };
    window.copperbenchHost = { workspaceId: '11111111-1111-4111-8111-111111111111', onEvent() { return () => {}; },
      async invoke(raw) {
        const request = JSON.parse(raw); const core = await ready;
        const result = request.messageType === 'handshake' ? await core.negotiateHandshake(request) : await core.sendQuery(request);
        if (request.operation === 'get_blockbench_environment') result.data.onboardingDismissed = (window as any).skippedModelingSetup;
        return JSON.stringify(result);
      }
    };
  });
  await page.goto('/');
  await expect(page.getByTestId('blockbench-onboarding')).toBeVisible();
  await page.getByRole('button', { name: '稍后设置，继续制作模组' }).click();
  await expect(page.getByTestId('blockbench-onboarding')).toHaveCount(0);
  await page.getByTestId('nav-assets').click(); await page.getByTestId('nav-hub').click();
  await expect(page.getByTestId('blockbench-onboarding')).toHaveCount(0);
  expect(await page.evaluate(() => (window as any).skippedModelingSetup)).toBe(true);
});
