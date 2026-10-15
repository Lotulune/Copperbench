import { test, expect, type Page } from '@playwright/test';

const workspaceId = '11111111-1111-4111-8111-111111111111';
const taskId = '44444444-4444-4444-8444-4444444444c3'; // Fixture identity; the live C3 evidence remains separate.
const manualPath = 'src/main/java/com/example/acceptance/WorkflowNote.java';
const generatedPath = 'src/main/java/dev/coppertrails/procedure/AnnounceTrailProcedure.java';
const sourceFor = (ownership: 'manual' | 'generated', current = false) => {
  const packageName = ownership === 'manual' ? 'com.example.acceptance' : 'dev.coppertrails.procedure';
  const className = ownership === 'manual' ? 'WorkflowNote' : 'AnnounceTrailProcedure';
  return `package ${packageName};\r\n\r\npublic final class ${className} {\r\n`
    + `    public static final String NOTE = ${current ? '"workspace now"' : ''};\r\n}\r\n`;
};

async function installSourceFixture(page: Page, ownership: 'manual' | 'generated',
  sourceFailure: 'rejected' | 'transport' | null = null, lineNumbers: number[] = [4]) {
  const relativePath = ownership === 'manual' ? manualPath : generatedPath;
  const multipleSource = (current: boolean) => [
    'package com.example.acceptance;', '', 'public final class WorkflowNote {',
    `    public static final String FIRST = ${current ? '"first now"' : ''};`, '', '', '',
    `    public static final String SECOND = ${current ? '"second now"' : ''};`, '}', ''
  ].join('\r\n');
  await page.addInitScript(({ workspaceId, taskId, ownership, relativePath, captured, current, sourceFailure, lineNumbers }) => {
    const modulePath = '/src/mock/mockBridge.ts';
    const diagnostic = { code: 'JAVA_COMPILE_ERROR', severity: 'error', recoverable: true, path: '/' + relativePath,
      message: { key: 'diagnostic.java_compile_error', fallback: 'Java compilation error: {message}',
        args: { message: 'Line 4: 非法的表达式开始' } },
      actions: [
        { id: 'open_generated_source', kind: 'open_source', target: '/' + relativePath,
          label: { key: 'action.open_source', fallback: 'View source' }, payload: { line: 4 } },
        { id: 'open_task_logs', kind: 'open_logs', target: taskId,
          label: { key: 'action.open_logs', fallback: 'View task logs' }, payload: { taskId } }
      ] };
    const diagnostics = lineNumbers.map(line => ({ ...diagnostic,
      message: { ...diagnostic.message, args: { message: `Line ${line}: 非法的表达式开始` } },
      actions: diagnostic.actions.map(action => action.kind === 'open_source' ? { ...action, payload: { line } } : action)
    }));
    const summary = { id: taskId, kind: 'build', state: 'failed', cancellable: false, progress: 1,
      stage: { key: 'task.failed', fallback: 'Fabric task failed', args: { backend: 'Fabric' } },
      startedAt: '2026-10-15T01:00:00Z', completedAt: '2026-10-15T01:00:01Z',
      diagnostics: { error: diagnostics.length, warning: 0, info: 0 } };
    const logs = [{ sequence: 1, timestamp: '2026-10-15T01:00:00Z', level: 'error',
      text: relativePath + ':4: 错误: 非法的表达式开始' }];
    let releaseStatusRead: () => void;
    const statusGate = new Promise<void>(resolve => { releaseStatusRead = resolve; });
    Object.assign(window, { releaseDiagnosticStatusRead: () => releaseStatusRead() });
    const ready = import(modulePath).then(({ MockCoreBridge }) => {
      const core = new MockCoreBridge();
      core.loadScenario('compile-diagnostic');
      Object.assign(core.getState(), { tasks: { [taskId]: summary }, taskLogs: { [taskId]: logs },
        taskDiagnostics: { [taskId]: diagnostics }, diagnostics });
      core.sourceFiles.set(relativePath, { relativePath, name: relativePath.split('/').at(-1), language: 'java',
        size: new TextEncoder().encode(current).length, ownership, editable: ownership === 'manual',
        reasonCode: ownership === 'generated' ? 'WORKSPACE_SOURCE_GENERATED' : null, content: current, sha256: '' });
      return core;
    });
    window.copperbenchHost = {
      workspaceId, onEvent() { return () => {}; },
      async invoke(raw) {
        const request = JSON.parse(raw);
        const requests = JSON.parse(sessionStorage.getItem('sourceDiagnosticRequests') ?? '[]');
        sessionStorage.setItem('sourceDiagnosticRequests', JSON.stringify([...requests, request]));
        const core = await ready;
        const revision = core.getState().workbench.workspace.revision;
        const receipt = (status: string, data: unknown, diagnostics: unknown[] = []) => ({
          messageType: 'query_result', schemaVersion: '1.0', requestId: request.requestId, workspaceId,
          operation: request.operation, status, revision, data, diagnostics
        });
        if (request.operation === 'get_task') {
          if (request.payload.taskId !== taskId) throw new Error('Unexpected task identity');
          if ('sourcePath' in request.payload) {
            if (request.payload.sourcePath !== '/' + relativePath) throw new Error('Unexpected diagnostic source path');
            if (sourceFailure === 'transport') throw new Error('Raw source transport failure 中文');
            if (sourceFailure === 'rejected') return JSON.stringify(receipt('rejected', null, [{
              code: 'TASK_SOURCE_NOT_FOUND', severity: 'error', recoverable: true, path: '/sourcePath', actions: [],
              message: { key: 'diagnostic.task_source_not_found', fallback: 'The requested task source preview is unavailable.',
                args: { detail: 'Raw source receipt 中文', requestedPath: '/' + relativePath } }
            }]));
            return JSON.stringify(receipt('succeeded', { task: summary, logs, diagnostics, source: {
              path: '/' + relativePath, language: 'java', line: lineNumbers.at(-1), content: captured,
              size: new TextEncoder().encode(captured).length
            } }));
          }
          if (sessionStorage.getItem('failDiagnosticStatusReads') === 'true') {
            await statusGate;
            return JSON.stringify(receipt('failed', null, [{
              code: 'TASK_STATUS_UNAVAILABLE', severity: 'error', recoverable: true, path: '/taskId', actions: [],
              message: { key: 'fixture.status_failure', fallback: 'Raw original status failure 中文' }
            }]));
          }
          return JSON.stringify(receipt('succeeded', { task: summary, logs, diagnostics }));
        }
        const result = request.messageType === 'handshake' ? await core.negotiateHandshake(request)
          : request.messageType === 'command' ? await core.sendCommand(request) : await core.sendQuery(request);
        if (request.operation === 'get_workbench') {
          result.data.activeTasks = [];
          result.data.recentTasks = [summary];
        }
        return JSON.stringify(result);
      }
    };
  }, { workspaceId, taskId, ownership, relativePath,
    captured: lineNumbers.length > 1 ? multipleSource(false) : sourceFor(ownership),
    current: lineNumbers.length > 1 ? multipleSource(true) : sourceFor(ownership, true), sourceFailure, lineNumbers });
}
const requests = (page: Page) => page.evaluate(() => JSON.parse(sessionStorage.getItem('sourceDiagnosticRequests') ?? '[]'));
async function bridgeObservation(page: Page) {
  return page.evaluate(async id => {
    const modulePath = '/src/bridge/index.ts';
    const { coreBridge } = await import(modulePath);
    const state = coreBridge.getState();
    return { taskIds: Object.keys(state.tasks), state: state.tasks[id].state, logs: state.taskLogs[id],
      diagnostics: state.taskDiagnostics[id], readError: state.taskReadErrors?.[id] ?? null };
  }, taskId);
}
async function openFailedTask(page: Page) {
  await page.goto('/');
  await page.getByTestId('recent-tasks-button').click();
  await expect(page.getByTestId('task-current-state')).toHaveText('失败');
  await expect(page.getByTestId('task-diagnostic-JAVA_COMPILE_ERROR').first()).toBeVisible();
}

for (const ownership of ['manual', 'generated'] as const) {
  test(`compiler diagnostic opens the captured ${ownership} source then its exact current file and line`, async ({ page }, testInfo) => {
    await installSourceFixture(page, ownership);
    await openFailedTask(page);
    const relativePath = ownership === 'manual' ? manualPath : generatedPath;
    const action = page.getByTestId('task-diag-action-open_generated_source');
    await expect(action).toHaveText('查看源码');
    await action.focus();
    await page.keyboard.press('Enter');
    await expect(page.getByTestId('task-source-content')).toBeVisible();
    expect(await page.getByTestId('task-source-content').textContent()).toBe(sourceFor(ownership));
    await expect(page.getByTestId('task-source-preview')).toContainText('/' + relativePath + ':4');
    await expect(page.getByTestId('task-status-unavailable')).toHaveCount(0);
    await page.getByTestId('ui-language-select').selectOption('en');
    await expect(action).toHaveText('View source');
    await expect(page.getByTestId('task-source-preview')).toHaveAttribute('aria-label', 'Source preview');
    await expect(page.getByTestId('task-current-state')).toHaveText('Failed');
    expect(await page.getByTestId('task-source-content').textContent()).toBe(sourceFor(ownership));
    for (const [width, height] of [[1280, 720], [1920, 1080]]) {
      await page.setViewportSize({ width, height });
      expect(await page.getByTestId('task-source-preview').evaluate(el => el.scrollWidth <= el.clientWidth + 1)).toBe(true);
      await page.screenshot({ path: testInfo.outputPath(`compiler-source-${ownership}-${width}.png`), animations: 'disabled' });
    }
    await page.getByTestId('task-source-open-editor').focus();
    await page.keyboard.press('Enter');
    await expect(page.getByTestId('task-drawer')).toHaveCount(0);
    await expect(page.getByTestId('source-workbench')).toBeVisible();
    const editor = page.getByTestId('source-editor');
    await expect(editor).toHaveValue(sourceFor(ownership, true).replace(/\r\n/g, '\n'));
    await expect(editor).toBeFocused();
    expect(await editor.evaluate((input: HTMLTextAreaElement) => input.value.slice(input.selectionStart, input.selectionEnd)))
      .toBe('    public static final String NOTE = "workspace now";');
    if (ownership === 'manual') {
      await expect(editor).toBeEditable();
    } else {
      await expect(editor).toHaveAttribute('readonly', '');
      await expect(page.getByTestId('source-workbench')).toContainText('Generated file · Read only');
      await expect(page.getByTestId('source-save')).toBeDisabled();
    }
    const observed = await bridgeObservation(page);
    expect(observed.taskIds).toEqual([taskId]);
    expect(observed.state).toBe('failed');
    expect(observed.diagnostics[0].code).toBe('JAVA_COMPILE_ERROR');
    expect(observed.readError).toBeNull();
    const journal = await requests(page);
    expect(journal.filter((request: { payload?: { sourcePath?: string } }) => request.payload?.sourcePath).map((request: { payload: unknown }) => request.payload))
      .toEqual([{ taskId, afterLogSequence: 1, sourcePath: '/' + relativePath }]);
    expect(journal.filter((request: { operation: string }) => request.operation === 'read_workspace_file').map((request: { payload: unknown }) => request.payload))
      .toEqual([{ relativePath }]);
    expect(journal.filter((request: { messageType: string }) => request.messageType === 'command')).toEqual([]);
  });
}

for (const failure of ['rejected', 'transport'] as const) {
  test(`a ${failure} source read retains its original failure only in the preview and survives language changes`, async ({ page }) => {
    await installSourceFixture(page, 'manual', failure);
    await openFailedTask(page);
    const before = await bridgeObservation(page);
    await page.getByTestId('task-diag-action-open_generated_source').click();
    const error = page.getByTestId('task-source-error');
    await expect(error).toContainText(failure === 'rejected' ? 'Raw source receipt 中文' : 'Raw source transport failure 中文');
    await expect(page.getByTestId('task-status-unavailable')).toHaveCount(0);
    await expect(page.getByTestId('task-current-state')).toHaveText('失败');
    await expect(page.getByTestId('task-source-open-editor')).toHaveCount(0);
    if (failure === 'rejected') await expect(error).toContainText('请求的任务源码预览不可用');
    await page.getByTestId('ui-language-select').selectOption('en');
    await expect(error).toContainText(failure === 'rejected' ? 'The requested task source preview is unavailable.' : 'Raw source transport failure 中文');
    if (failure === 'rejected') {
      await error.getByText('Original diagnostics', { exact: true }).click();
      const raw = error.locator('details pre');
      await expect(raw).toContainText('TASK_SOURCE_NOT_FOUND');
      await expect(raw).toContainText('"path": "/sourcePath"');
      await expect(raw).toContainText('"requestedPath": "/' + manualPath + '"');
      await expect(raw).toContainText('"detail": "Raw source receipt 中文"');
    }
    await expect(page.getByTestId('task-current-state')).toHaveText('Failed');
    await expect(page.getByTestId('task-diagnostic-JAVA_COMPILE_ERROR')).toContainText('Line 4: 非法的表达式开始');
    expect(await bridgeObservation(page)).toEqual(before);
    const journal = await requests(page);
    expect(journal.filter((request: { payload?: { sourcePath?: string } }) => request.payload?.sourcePath)).toHaveLength(1);
    expect(journal.filter((request: { messageType: string }) => request.messageType === 'command')).toEqual([]);
  });
}

test('successful source reads preserve a later original-status failure and its explicit recovery action', async ({ page }) => {
  await installSourceFixture(page, 'manual');
  await openFailedTask(page);
  await page.evaluate(async ({ workspaceId, taskId }) => {
    const modulePath = '/src/bridge/index.ts';
    const { coreBridge } = await import(modulePath);
    sessionStorage.setItem('failDiagnosticStatusReads', 'true');
    Object.assign(window, { pendingDiagnosticStatusRead: coreBridge.sendQuery({
      messageType: 'query', schemaVersion: '1.0', requestId: crypto.randomUUID(),
      workspaceId, operation: 'get_task', payload: { taskId }
    }) });
  }, { workspaceId, taskId });
  await page.getByTestId('task-diag-action-open_generated_source').click();
  await expect(page.getByTestId('task-source-content')).toBeVisible();
  await expect(page.getByTestId('task-status-unavailable')).toHaveCount(0);
  await page.evaluate(() => (window as unknown as { releaseDiagnosticStatusRead(): void }).releaseDiagnosticStatusRead());
  const status = page.getByTestId('task-status-unavailable');
  await expect(status).toBeVisible();
  await expect(status).toContainText('读取原任务状态');
  await page.getByTestId('ui-language-select').selectOption('en');
  await expect(status).toContainText('Task status is temporarily unavailable');
  await page.getByTestId('task-diag-action-open_generated_source').click();
  await expect(page.getByTestId('task-source-content')).toBeVisible();
  await expect.poll(async () => (await requests(page)).filter((request: { payload?: { sourcePath?: string } }) => request.payload?.sourcePath).length).toBe(2);
  await expect(status).toBeVisible();
  await expect(page.getByTestId('task-current-state')).toHaveText('Failed');
  await status.getByText('Read error details', { exact: true }).click();
  await expect(status.locator('pre')).toContainText('TASK_STATUS_UNAVAILABLE');
  await page.evaluate(() => sessionStorage.removeItem('failDiagnosticStatusReads'));
  await status.getByRole('button', { name: 'Read original task status', exact: true }).click();
  await expect(status).toHaveCount(0);
  await expect(page.getByTestId('task-source-content')).toBeVisible();
  expect((await bridgeObservation(page)).readError).toBeNull();
  expect((await requests(page)).filter((request: { messageType: string }) => request.messageType === 'command')).toEqual([]);
});


test('each error in one captured file opens its own diagnostic line instead of the shared preview line', async ({ page }) => {
  await installSourceFixture(page, 'manual', null, [4, 8]);
  await openFailedTask(page);
  let captured: string | null = null;
  for (const [index, line, selectedText] of [[0, 4, '    public static final String FIRST = "first now";'],
    [1, 8, '    public static final String SECOND = "second now";'],
    [1, 8, '    public static final String SECOND = "second now";']] as const) {
    await page.getByTestId('task-diag-action-open_generated_source').nth(index).click();
    await expect(page.getByTestId('task-source-preview')).toContainText('/' + manualPath + ':' + line);
    const content = await page.getByTestId('task-source-content').textContent();
    if (captured === null) captured = content; else expect(content).toBe(captured);
    await page.getByTestId('task-source-open-editor').click();
    const editor = page.getByTestId('source-editor');
    await expect(editor).toBeFocused();
    expect(await editor.evaluate((input: HTMLTextAreaElement) => input.value.slice(input.selectionStart, input.selectionEnd))).toBe(selectedText);
    await page.getByTestId('recent-tasks-button').click();
    await expect(page.getByTestId('task-current-state')).toHaveText('失败');
  }
  const journal = await requests(page);
  expect(journal.filter((request: { payload?: { sourcePath?: string } }) => request.payload?.sourcePath)
    .map((request: { payload: unknown }) => request.payload)).toEqual([
      { taskId, afterLogSequence: 1, sourcePath: '/' + manualPath },
      { taskId, afterLogSequence: 1, sourcePath: '/' + manualPath },
      { taskId, afterLogSequence: 1, sourcePath: '/' + manualPath }
    ]);
  expect(journal.filter((request: { messageType: string }) => request.messageType === 'command')).toEqual([]);
});
