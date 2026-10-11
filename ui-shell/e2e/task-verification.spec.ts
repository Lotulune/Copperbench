import { test, expect, type Page } from '@playwright/test';

async function installTask(page: Page, reason: string, legacy = false) {
  await page.addInitScript(({ reason, legacy }) => {
    const ready = import('/src/mock/mockBridge.ts').then(({ MockCoreBridge }) => {
      const core = new MockCoreBridge();
      const running = reason === 'running';
      const passed = reason.startsWith('passed_');
      const task = {
        id: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', kind: 'run_gametest', state: running ? 'running' : passed ? 'succeeded' : 'failed',
        cancellable: running, progress: running ? 0.5 : 1, stage: { key: 'fixture.task', fallback: 'GameTest' },
        startedAt: '2026-10-11T00:00:00Z', diagnostics: { errors: passed || running ? 0 : 1, warnings: 0, infos: 0 },
        verification: { schemaVersion: '1.0', status: running ? 'pending' : passed ? 'passed' : 'failed',
          reasonCode: reason === 'report_missing' ? 'GAMETEST_REPORT_MISSING' : reason === 'business_failed' ? 'GAMETEST_FAILED' : passed ? 'GAMETEST_PASSED' : 'GAMETEST_NO_TESTS',
          minimumTests: 6, acceptanceExecuted: passed ? 6 : 5, discovered: 5, executed: 5, passed: reason === 'business_failed' ? 4 : 5,
          failed: reason === 'business_failed' ? 1 : 0, skipped: 0, cases: [] }
      };
      core.getState().tasks = { [task.id]: task };
      core.getState().taskLogs = { [task.id]: [] };
      return { core, task };
    });
    window.copperbenchHost = {
      workspaceId: '11111111-1111-4111-8111-111111111111', onEvent() { return () => {}; },
      async invoke(raw: string) {
        const { core, task } = await ready, request = JSON.parse(raw);
        const requests = JSON.parse(sessionStorage.getItem('verificationRequests') ?? '[]');
        sessionStorage.setItem('verificationRequests', JSON.stringify([...requests, { kind: request.messageType, operation: request.operation }]));
        if (request.operation === 'get_task_verification') {
          const observed = ['running', 'report_missing'].includes(reason) ? null : task.verification.acceptanceExecuted;
          return JSON.stringify({ messageType: 'query_result', schemaVersion: '1.0', requestId: request.requestId,
            workspaceId: request.workspaceId, operation: request.operation, revision: 42, status: legacy ? 'rejected' : 'succeeded',
            diagnostics: [], data: legacy ? null : {
              resultVersion: '1', taskId: task.id, taskState: task.state, reasonCode: task.verification.reasonCode,
              subreason: reason, coverage: { minimumTests: 6, acceptanceExecuted: observed,
                shortfall: observed === null ? null : 6 - observed },
              message: { key: 'verification.' + reason, fallback: 'Retained result',
                args: { minimumTests: 6, acceptanceExecuted: task.verification.acceptanceExecuted, shortfall: 6 - task.verification.acceptanceExecuted } }
            } });
        }
        const result = request.messageType === 'handshake' ? await core.negotiateHandshake(request)
          : request.messageType === 'command' ? await core.sendCommand(request) : await core.sendQuery(request);
        if (request.operation === 'get_workbench') { result.data.activeTasks = []; result.data.recentTasks = [task]; }
        return JSON.stringify(result);
      }
    };
  }, { reason, legacy });
}

for (const [reason, zh, en] of [
  ['insufficient_business_tests', '实际执行 5 项，还差 1 项', '5 business tests executed; 1 missing'],
  ['business_failed', '存在失败用例', 'Test failures prevent acceptance'],
  ['report_missing', '找不到测试报告', 'The test report is missing'],
  ['running', '任务正在执行', 'The task is running'],
  ['passed_current_input', '当前输入已通过验收', 'The current input passed acceptance'],
  ['passed_historical_input', '仅历史输入已通过验收', 'Only the previously tested input passed acceptance']
]) {
  test(`${reason} keeps its task and raw message when switching language`, async ({ page }, testInfo) => {
    await installTask(page, reason);
    await page.goto('/');
    await page.getByTestId('recent-tasks-button').click();
    const result = page.getByTestId('task-verification-result');
    await expect(result).toContainText(zh);
    const before = await page.evaluate(() => {
      const calls = JSON.parse(sessionStorage.getItem('verificationRequests') ?? '[]');
      return calls.filter((call: { operation: string }) => call.operation === 'get_task_verification').length;
    });
    if (reason === 'insufficient_business_tests') await page.screenshot({ path: testInfo.outputPath('shortfall-zh.png') });
    await page.getByTestId('ui-language-select').selectOption('en');
    await expect(result).toContainText(en);
    if (reason === 'running' || reason === 'report_missing')
      await expect(page.getByRole('region', { name: 'GameTest verification report' }).locator('dd').first()).toHaveText('—');
    if (reason !== 'insufficient_business_tests') await expect(result.locator('..')).toContainText('Required business tests: 6');
    expect(await result.locator('..').evaluate(el => el.scrollWidth <= el.clientWidth + 1)).toBe(true);
    if (reason === 'insufficient_business_tests') {
      await page.screenshot({ path: testInfo.outputPath('shortfall-en.png') });
      await page.emulateMedia({ colorScheme: 'dark' });
      await page.screenshot({ path: testInfo.outputPath('shortfall-en-dark.png') });
    }
    const state = await page.evaluate(async () => {
      const { coreBridge } = await import('/src/bridge/index.ts');
      return { revision: coreBridge.getState().workbench?.workspace.revision,
        state: coreBridge.getState().tasks['aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'].state,
        calls: JSON.parse(sessionStorage.getItem('verificationRequests') ?? '[]') };
    });
    expect(state.revision).toBe(42);
    expect(state.state).toBe(reason === 'running' ? 'running' : reason.startsWith('passed_') ? 'succeeded' : 'failed');
    expect(state.calls.filter((call: { kind: string }) => call.kind === 'command')).toEqual([]);
    expect(state.calls.filter((call: { operation: string }) => call.operation === 'get_task_verification')).toHaveLength(before);
  });
}

test('an older host still renders the retained 5/6 result without inventing a current-input pass', async ({ page }) => {
  await installTask(page, 'insufficient_business_tests', true);
  await page.goto('/');
  await page.getByTestId('recent-tasks-button').click();
  await expect(page.getByTestId('task-verification-result')).toContainText('实际执行 5 项，还差 1 项');
});
