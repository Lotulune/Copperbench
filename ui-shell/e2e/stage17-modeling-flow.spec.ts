import { test, expect } from '@playwright/test';

test('replacement modeling task takes focus ahead of cancelled history and remains discoverable after reopen', async ({ page }) => {
  await page.addInitScript(() => {
    const modulePath = '/src/mock/mockBridge.ts';
    const ready = import(modulePath).then(({ MockCoreBridge }) => new MockCoreBridge());
    let elementId = '';
    let replacement: any = JSON.parse(sessionStorage.getItem('replacement-task') ?? 'null');
    const cancelled = () => ({ taskId: 'cancelled-history', state: 'cancelled', targetRelativePath: 'models/blockbench/old.bbmodel',
      editPath: 'C:/fixture/old/edit/model.bbmodel', editSha256: 'a'.repeat(64), candidatePath: null,
      hasSavedChanges: true, sourceChanged: false, recoveryPointId: 'rp-old', createdAt: '2026-09-20T00:00:00Z',
      elementContext: { elementId, name: 'Previous copy', type: 'block', namespace: 'test', modelResource: 'test:custom/lamp', textureDirectory: 'textures/block' } });
    window.copperbenchHost = {
      workspaceId: '11111111-1111-4111-8111-111111111111', onEvent() { return () => {}; },
      async invoke(raw) {
        const request = JSON.parse(raw), core = await ready;
        const envelope = { schemaVersion: '1.0', requestId: request.requestId, workspaceId: request.workspaceId, operation: request.operation, diagnostics: [] };
        if (request.operation === 'list_blockbench_tasks') return JSON.stringify({ ...envelope, messageType: 'query_result', status: 'succeeded', revision: 42,
          data: { tasks: [cancelled(), ...(replacement ? [replacement] : [])] } });
        if (request.operation === 'begin_blockbench_task') {
          replacement = { ...cancelled(), taskId: request.payload.taskId, state: 'editing', createdAt: '2026-09-21T00:00:00Z',
            elementContext: { ...cancelled().elementContext, name: 'Replacement copy' } };
          sessionStorage.setItem('replacement-task', JSON.stringify(replacement));
          return JSON.stringify({ ...envelope, messageType: 'command_result', status: 'completed', newRevision: 42,
            recoveryPointId: 'rp-new', task: null, data: replacement, conflict: null, denial: null });
        }
        const result = request.messageType === 'handshake' ? await core.negotiateHandshake(request)
          : request.messageType === 'command' ? await core.sendCommand(request) : await core.sendQuery(request);
        if (request.operation === 'get_mod_element_editor') elementId = result.data.element.id;
        if (request.operation === 'get_blockbench_environment') result.data.onboardingDismissed = true;
        return JSON.stringify(result);
      }
    };
  });
  const open = async () => {
    await page.goto('/'); await page.getByTestId('nav-elements').click();
    await page.getByTestId('filter-type-block').click(); await page.locator('[data-element-id]').first().click();
    const panel = page.getByTestId('element-inspector').getByTestId('blockbench-tasks');
    await panel.locator(':scope > summary').click();
    return panel;
  };
  const panel = await open();
  await expect(panel.locator('.blockbench-task')).toContainText('已取消，文件保留');
  await expect(panel.locator('.blockbench-task')).not.toContainText('源模型候选：未生成');
  await expect(panel.locator('.blockbench-task')).toContainText('编辑副本与候选文件已保留。');
  await expect(panel.getByRole('button', { name: '确认磁盘保存并生成候选' })).toBeDisabled();
  await panel.locator('.modeling-extra-task > summary').click();
  await panel.getByRole('button', { name: '新建建模副本', exact: true }).click();
  const first = panel.locator('.blockbench-task').first();
  await expect(first).toContainText('Replacement copy');
  await expect(first).toBeFocused();
  await expect(first.getByRole('button', { name: '在 Blockbench 打开副本' })).toBeInViewport();
  await expect(panel.locator('.modeling-extra-task')).toHaveJSProperty('open', false);
  await page.reload();
  const reopened = await open();
  await expect(reopened.locator('.blockbench-task').first()).toContainText('Replacement copy');
  await expect(reopened.locator('.blockbench-task')).toHaveCount(2);
});

test('element guided modeling preserves its target across reopen and separates import, binding and verification', async ({ page }, testInfo) => {
  await page.addInitScript(() => {
    const modulePath = '/src/mock/mockBridge.ts';
    const ready = import(modulePath).then(({ MockCoreBridge }) => new MockCoreBridge());
    let task: any = JSON.parse(sessionStorage.getItem('guided-task') ?? 'null');
    let failBinding = true;
    const calls: any[] = [];
    (window as any).guidedCalls = calls;
    const save = () => sessionStorage.setItem('guided-task', JSON.stringify(task));
    (window as any).saveModelWithoutFocus = () => { task.editSha256 = 'b'.repeat(64); save(); };
    (window as any).__COPPERBENCH_BLOCKBENCH_HOST__ = { schemaVersion: '1.0',
      openTask: async (taskId: string) => { calls.push({ operation: 'open_task', taskId }); return { state: 'running' }; } };
    window.copperbenchHost = {
      workspaceId: '11111111-1111-4111-8111-111111111111', onEvent() { return () => {}; },
      async invoke(raw) {
        const request = JSON.parse(raw), core = await ready;
        if (request.messageType === 'command') calls.push(request);
        const envelope = { schemaVersion: '1.0', requestId: request.requestId, workspaceId: request.workspaceId, operation: request.operation, diagnostics: [] };
        if (request.operation === 'list_blockbench_tasks') return JSON.stringify({ ...envelope, messageType: 'query_result', status: 'succeeded', revision: 42, data: { tasks: task ? [task] : [] } });
        if (request.operation === 'get_blockbench_task') {
          calls.push(request);
          return JSON.stringify({ ...envelope, messageType: 'query_result', status: 'succeeded', revision: 42, data: task });
        }
        if (request.operation === 'preview_blockbench_import') {
          calls.push(request);
          return JSON.stringify({ ...envelope, messageType: 'query_result', status: 'succeeded', revision: 42, data: {
            planToken: 'guided-preview', canApply: true, requiresReplacementConfirmation: false,
            outputs: [{ sourceRelativePath: 'lamp.json', targetRelativePath: 'src/main/resources/assets/test/models/custom/lamp.json' }],
            items: [{ targetRelativePath: 'src/main/resources/assets/test/models/custom/lamp.json', conflict: 'CREATE', sourceSha256: 'a'.repeat(64) }]
          } });
        }
        if (['begin_blockbench_task', 'finish_blockbench_task', 'import_blockbench_task', 'bind_blockbench_model'].includes(request.operation)) {
          if (request.operation === 'begin_blockbench_task') task = {
            taskId: request.payload.taskId, state: 'editing', targetRelativePath: 'models/blockbench/lamp.bbmodel',
            editPath: 'C:/fixture/edit/model.bbmodel', editSha256: 'a'.repeat(64), candidatePath: null, sourceChanged: false,
            hasSavedChanges: true, recoveryPointId: 'rp-guided',
            elementContext: { elementId: request.payload.elementId, name: 'Guided Lamp', type: 'block', namespace: 'test',
              modelResource: 'test:custom/lamp', textureDirectory: 'src/main/resources/assets/test/textures/block' }, binding: { state: 'unbound' }
          };
          if (request.operation === 'import_blockbench_task') { task.state = 'imported'; task.importFiles = [{ targetRelativePath: 'src/main/resources/assets/test/models/custom/lamp.json' }]; }
          const staleFinish = request.operation === 'finish_blockbench_task' && request.payload.savedSha256 !== task.editSha256;
          if (request.operation === 'finish_blockbench_task' && !staleFinish) {
            task.state = 'ready_to_import'; task.candidatePath = 'C:/fixture/candidate.bbmodel';
          }
          const fail = staleFinish || request.operation === 'bind_blockbench_model' && failBinding;
          if (request.operation === 'bind_blockbench_model') {
            failBinding = false;
            if (!fail) task.binding = { state: 'bound', modelResource: 'test:custom/lamp' };
          }
          save();
          return JSON.stringify({ ...envelope, messageType: 'command_result', status: fail ? 'failed' : 'completed', newRevision: 42,
            recoveryPointId: 'rp-guided', task: null, data: task, conflict: null, denial: null,
            diagnostics: fail ? [{ code: staleFinish ? 'MODEL_EDIT_CHANGED' : 'MODEL_BINDING_RETRY', severity: 'error', message: { key: 'diagnostic.blockbench_task_failed', fallback: staleFinish ? 'Refresh the saved file' : 'Retry binding' } }] : [] });
        }
        const result = request.messageType === 'handshake' ? await core.negotiateHandshake(request)
          : request.messageType === 'command' ? await core.sendCommand(request) : await core.sendQuery(request);
        if (request.operation === 'get_blockbench_environment') result.data.onboardingDismissed = true;
        return JSON.stringify(result);
      }
    };
  });
  await page.goto('/');
  await page.getByTestId('nav-elements').click();
  await page.getByTestId('filter-type-block').click();
  const element = page.locator('[data-element-id]').first();
  const elementId = await element.getAttribute('data-element-id');
  await element.click();
  const panel = page.getByTestId('element-inspector').getByTestId('blockbench-tasks');
  await panel.locator(':scope > summary').focus(); await page.keyboard.press('Enter');
  await expect(panel.getByLabel('新模型目标路径')).toHaveCount(0);
  await panel.getByRole('button', { name: '新建建模副本' }).click();
  await expect(panel).toContainText('test:custom/lamp');
  await expect(panel.locator('.modeling-task-details')).toHaveJSProperty('open', false);
  await expect(panel.getByRole('button', { name: '在 Blockbench 打开副本' })).toBeInViewport();
  await panel.getByRole('button', { name: '在 Blockbench 打开副本' }).click();
  await expect(panel.getByRole('status')).toContainText('已打开副本。请在编辑目录保存并导出 JSON 和 PNG。');
  await page.evaluate(() => (window as any).saveModelWithoutFocus());
  await panel.getByRole('button', { name: '确认磁盘保存并生成候选' }).click();
  await expect(panel.locator('.blockbench-task > strong')).toContainText('候选已保存，待回导');
  const details = panel.locator('.modeling-task-details');
  await details.locator(':scope > summary').click();
  await expect(details.getByText('C:/fixture/candidate.bbmodel', { exact: true })).toBeVisible();
  await expect(details).toContainText('test:custom/lamp');
  await details.locator(':scope > summary').click();
  const finishCalls = await page.evaluate(() => (window as any).guidedCalls);
  const finish = finishCalls.filter((call: any) => call.operation === 'finish_blockbench_task');
  expect(finish).toHaveLength(1);
  expect(finish[0].payload.savedSha256).toBe('b'.repeat(64));
  expect(finishCalls.findIndex((call: any) => call.operation === 'get_blockbench_task'))
    .toBeLessThan(finishCalls.findIndex((call: any) => call.operation === 'finish_blockbench_task'));
  await page.reload();
  await page.getByTestId('nav-assets').click();
  await page.getByTestId('asset-modeling-disclosure').locator(':scope > summary').click();
  const restored = page.getByTestId('blockbench-tasks');
  await restored.locator(':scope > summary').click();
  await expect(restored).toContainText('Guided Lamp');
  await expect(restored.locator('.blockbench-task > strong')).toContainText('候选已保存，待回导');
  await restored.locator('.modeling-task-details > summary').click();
  await expect(restored.getByText('C:/fixture/candidate.bbmodel', { exact: true })).toBeVisible();
  await expect(restored).toContainText('test:custom/lamp');
  await restored.locator('.modeling-task-details > summary').click();
  await restored.locator('.modeling-import-review summary').click();
  await expect(restored.getByLabel('目标元素')).toBeDisabled();
  await restored.getByRole('button', { name: '识别并预览回导' }).click();
  const preview = await page.evaluate(() => (window as any).guidedCalls.find((call: any) => call.operation === 'preview_blockbench_import'));
  expect(preview.payload.outputs).toBeUndefined();
  expect(preview.payload.elementId).toBe(elementId);
  expect(preview.payload.taskId).toBe(finish[0].payload.taskId);
  await restored.getByRole('button', { name: '应用回导', exact: true }).click();
  await expect(restored.getByLabel('导入的游戏模型')).toHaveValue('test:custom/lamp');
  await expect(restored.getByRole('button', { name: '构建工作区', exact: true })).toBeDisabled();
  await restored.getByRole('button', { name: '关联所选模型' }).click();
  await expect(restored).toContainText('MODEL_BINDING_RETRY');
  await expect(restored).toContainText('文件已回导，关联尚未确认');
  await restored.getByRole('button', { name: '关联所选模型' }).click();
  await expect(restored.getByRole('button', { name: '构建工作区', exact: true })).toBeEnabled();
  await expect(restored).toContainText('元素定义已关联');
  const importCommands = await page.evaluate(() => (window as any).guidedCalls
    .filter((call: any) => call.messageType === 'command'));
  expect(importCommands.map((call: any) => call.operation)).toEqual([
    'import_blockbench_task', 'bind_blockbench_model', 'bind_blockbench_model'
  ]);
  for (const call of importCommands) expect(call.payload.taskId).toBe(finish[0].payload.taskId);
  for (const call of importCommands.slice(1)) {
    expect(call.payload.elementId).toBe(elementId);
    expect(call.payload.modelResource).toBe('test:custom/lamp');
  }
  expect(await restored.evaluate(element => element.scrollWidth <= element.clientWidth)).toBe(true);
  await page.screenshot({ path: testInfo.outputPath('guided-modeling-bound.png') });
  await page.reload(); await page.getByTestId('nav-assets').click();
  await page.getByTestId('asset-modeling-disclosure').locator(':scope > summary').click();
  await restored.locator(':scope > summary').click();
  await expect(restored.getByRole('button', { name: '构建工作区', exact: true })).toBeEnabled();
  await expect(restored).toContainText('下一步：构建并在游戏内验证。');
});

test('handwritten element explains its restriction before creating or previewing a task', async ({ page }) => {
  await page.addInitScript(() => {
    const modulePath = '/src/mock/mockBridge.ts';
    const ready = import(modulePath).then(({ MockCoreBridge }) => new MockCoreBridge());
    const calls: string[] = [];
    (window as any).handwrittenModelingCalls = calls;
    window.copperbenchHost = {
      workspaceId: '11111111-1111-4111-8111-111111111111', onEvent() { return () => {}; },
      async invoke(raw) {
        const request = JSON.parse(raw), core = await ready;
        if (request.messageType === 'command' || request.operation === 'preview_blockbench_import') calls.push(request.operation);
        const result = request.messageType === 'handshake' ? await core.negotiateHandshake(request)
          : request.messageType === 'command' ? await core.sendCommand(request) : await core.sendQuery(request);
        if (request.operation === 'get_mod_element_editor') result.data.element.ownership = 'manual';
        if (request.operation === 'list_blockbench_tasks') { result.status = 'succeeded'; result.data = { tasks: [] }; }
        if (request.operation === 'get_blockbench_environment') result.data.onboardingDismissed = true;
        return JSON.stringify(result);
      }
    };
  });
  await page.goto('/'); await page.getByTestId('nav-elements').click();
  await page.getByTestId('filter-type-block').click(); await page.locator('[data-element-id]').first().click();
  const panel = page.getByTestId('element-inspector').getByTestId('blockbench-tasks');
  await panel.locator(':scope > summary').click();
  await expect(panel.getByRole('alert')).toContainText('此元素由源码管理，需在源码中关联模型。');
  await expect(panel.getByRole('button', { name: '新建建模副本' })).toBeDisabled();
  await expect(panel.getByRole('button', { name: '识别并预览回导' })).toHaveCount(0);
  expect(await page.evaluate(() => (window as any).handwrittenModelingCalls)).toEqual([]);
});
