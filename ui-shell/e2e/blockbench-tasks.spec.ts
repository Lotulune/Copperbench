import { test, expect } from '@playwright/test';

// Installed JCEF uses a custom scheme without crypto.randomUUID.
test.beforeEach(async ({ page }) => {
  await page.addInitScript(() => Object.defineProperty(crypto, 'randomUUID', { value: undefined }));
});

test('native task UI refreshes a saved hash before completion and preserves cancelled tasks', async ({ page }, testInfo) => {
  await page.addInitScript(() => {
    const listeners = new Set<(raw: string) => void>();
    const modulePath = '/src/mock/mockBridge.ts';
    const ready = import(modulePath).then(({ MockCoreBridge }) => new MockCoreBridge());
    let task: any = null;
    let hash = 'a'.repeat(64);
    (window as any).saveModel = () => { hash = 'b'.repeat(64); };
    (window as any).changeCandidate = () => { task.candidateChanged = true; };
    window.copperbenchHost = {
      workspaceId: '11111111-1111-4111-8111-111111111111',
      onEvent(listener) { listeners.add(listener); return () => listeners.delete(listener); },
      async invoke(raw) {
        const core = await ready;
        const request = JSON.parse(raw);
        if (request.operation === 'list_blockbench_tasks') return JSON.stringify({
          messageType: 'query_result', schemaVersion: '1.0', requestId: request.requestId, workspaceId: request.workspaceId,
          operation: request.operation, status: 'succeeded', revision: 42, diagnostics: [], data: { tasks: task ? [{ ...task, editSha256: hash }] : [] }
        });
        if (['begin_blockbench_task', 'finish_blockbench_task', 'cancel_blockbench_task'].includes(request.operation)) {
          const stale = request.operation === 'finish_blockbench_task' && request.payload.savedSha256 !== hash;
          if (request.operation === 'begin_blockbench_task') task = {
            taskId: request.payload.taskId, targetRelativePath: request.payload.targetRelativePath,
            state: 'editing', editPath: 'C:/fixture/edit/model.bbmodel', candidatePath: null, editSha256: hash,
            sourceChanged: false, hasSavedChanges: true, recoveryPointId: 'recovery-modeling-test'
          };
          if (!stale && request.operation === 'finish_blockbench_task') { task.state = 'ready_to_import'; task.candidatePath = 'C:/fixture/candidate.bbmodel'; }
          if (request.operation === 'cancel_blockbench_task') task.state = 'cancelled';
          return JSON.stringify({ messageType: 'command_result', schemaVersion: '1.0', requestId: request.requestId,
            workspaceId: request.workspaceId, operation: request.operation, status: stale ? 'failed' : 'completed', newRevision: 42,
            recoveryPointId: 'recovery-modeling-test', task: null, conflict: null, denial: null, data: task,
            diagnostics: stale ? [{ code: 'MODEL_EDIT_CHANGED', severity: 'error', message: { key: 'diagnostic.blockbench_task_failed', fallback: '', args: {} }, path: null, elementId: null, recoverable: true, actions: [] }] : []
          });
        }
        return JSON.stringify(request.messageType === 'handshake' ? await core.negotiateHandshake(request)
          : request.messageType === 'command' ? await core.sendCommand(request) : await core.sendQuery(request));
      }
    };
  });
  await page.goto('/');
  await page.getByTestId('nav-assets').click();
  const panel = page.getByTestId('blockbench-tasks');
  await panel.locator('summary').click();
  await panel.getByRole('button', { name: '新建建模副本' }).click();
  await expect(panel).toContainText('编辑副本已准备');
  await page.evaluate(() => (window as any).saveModel());
  await panel.getByRole('button', { name: '确认磁盘保存并生成候选' }).click();
  await expect(panel.getByRole('status')).toContainText('MODEL_EDIT_CHANGED');
  await panel.getByRole('button', { name: '刷新任务与保存状态' }).click();
  await panel.getByRole('button', { name: '确认磁盘保存并生成候选' }).click();
  await expect(panel).toContainText('候选已保存，待回导');
  await expect(panel).toContainText('尚未导出或回导到游戏');
  await page.screenshot({ path: testInfo.outputPath('modeling-candidate.png') });
  await page.evaluate(() => (window as any).changeCandidate());
  await panel.getByRole('button', { name: '刷新任务与保存状态' }).click();
  await expect(panel.getByRole('alert')).toContainText('候选或编辑副本在完成后发生变化');
  await panel.getByRole('button', { name: '取消任务并保留文件' }).click();
  await expect(panel).toContainText('已取消，文件保留');
  await expect(panel.getByRole('button', { name: '确认磁盘保存并生成候选' })).toBeDisabled();
});

test('preview mode does not pretend to create files or choose an installation', async ({ page }) => {
  await page.goto('/');
  await page.getByTestId('nav-assets').click();
  await page.getByTestId('blockbench-tasks').locator('summary').click();
  await expect(page.getByRole('button', { name: '新建建模副本' })).toBeDisabled();
  await page.getByTestId('blockbench-setup').locator('summary').click();
  await page.getByRole('button', { name: '选择安装位置' }).click();
  await expect(page.getByTestId('blockbench-setup').getByRole('status')).toContainText('请在桌面产品中选择安装位置');
});
