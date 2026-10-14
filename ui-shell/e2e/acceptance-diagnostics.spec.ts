import { test, expect } from '@playwright/test';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

for (const name of ['threshold-0-1', 'threshold-5-6', 'threshold-6-6', 'threshold-7-6', 'skipped', 'framework', 'failed', 'duplicate', 'missing', 'stale', 'broken', 'running', 'historical']) {
  test(`acceptance ${name} keeps machine parameters and performs no language-triggered product operations`, async ({ page }, testInfo) => {
    const coverage = JSON.parse(readFileSync(resolve('../ui-core/fixtures/extensions/acceptance-diagnostics', name + '.json'), 'utf8'));
    await page.addInitScript(({ coverage }) => {
      const modulePath = '/src/mock/mockBridge.ts';
      const ready = import(modulePath).then(({ MockCoreBridge }) => { const core = new MockCoreBridge(); core.loadScenario('ready'); return core; });
      const passed = coverage.state.startsWith('passed');
      const running = coverage.state === 'running';
      const historical = coverage.state === 'passed_historical_input';
      const task = { id: coverage.taskId, kind: historical ? 'export' : 'run_gametest', state: running ? 'running' : passed ? 'succeeded' : 'failed',
        cancellable: false, progress: running ? 0.5 : 1, stage: { key: 'fixture.acceptance', fallback: 'Acceptance fixture' },
        startedAt: '2026-10-12T00:00:00Z', completedAt: running ? null : '2026-10-12T00:00:01Z', diagnostics: { error: 0, warning: 0, info: 0 },
        ...historical ? { verifiedExport: { exportDirectory: 'fixture/export', artifactSha256: 'a'.repeat(64), status: 'passed_historical_input' } }
        : { verification: { schemaVersion: '1.0', status: running ? 'pending' : passed ? 'passed' : 'failed', reasonCode: 'GAMETEST_NO_TESTS',
          discovered: coverage.uniqueExecuted ?? 0, executed: coverage.uniqueExecuted ?? 0, passed: coverage.uniqueExecuted ?? 0,
          failed: 0, skipped: 0, acceptanceExecuted: coverage.uniqueExecuted ?? 0, cases: [] } } };
      window.copperbenchHost = { workspaceId: '11111111-1111-4111-8111-111111111111', onEvent() { return () => {}; },
        async invoke(raw) {
          const core = await ready, request = JSON.parse(raw);
          if (request.messageType === 'acceptance') {
            sessionStorage.setItem('coverageReads', String(Number(sessionStorage.getItem('coverageReads') ?? 0) + 1));
            if (request.request.operation !== 'get' || JSON.stringify(request.request.diagnosticVersions) !== '["1"]') throw new Error('Only negotiated diagnostic reads are expected');
            return JSON.stringify({ contract: 'required_cases', selectedVersion: '1', schemaVersion: '1', requestId: request.requestId, status: 'succeeded', coverageDiagnostics: coverage,
              core: { messageType: 'query_result', schemaVersion: '1.0', requestId: request.requestId, workspaceId: request.workspaceId,
                operation: 'get_task', status: 'succeeded', revision: 0, data: { task, logs: [], diagnostics: [] }, diagnostics: [] } });
          }
          if (request.messageType === 'command') {
            sessionStorage.setItem('productCommands', String(Number(sessionStorage.getItem('productCommands') ?? 0) + 1));
            throw new Error('No product command is expected while reading or translating a task');
          }
          if (request.operation === 'get_task') return JSON.stringify({ messageType: 'query_result', schemaVersion: '1.0', requestId: request.requestId,
            workspaceId: request.workspaceId, operation: 'get_task', status: 'succeeded', revision: 0,
            data: { task, logs: [], diagnostics: [] }, diagnostics: [] });
          const result = request.messageType === 'handshake' ? await core.negotiateHandshake(request) : await core.sendQuery(request);
          if (request.operation === 'get_workbench') { result.data.activeTasks = running ? [task] : []; result.data.recentTasks = [task]; }
          return JSON.stringify(result);
        }
      };
    }, { coverage });
    await page.goto('/');
    await page.getByRole('button', { name: '总览', exact: true }).click();
    const health = page.getByTestId('workspace-health-panel').locator('details').first();
    if (await health.getAttribute('open') === null) await health.locator('summary').first().click();
    await page.getByTestId('workspace-health-tasks').click();
    const summary = page.getByTestId('acceptance-summary');
    await expect(summary).toHaveAttribute('data-state', coverage.state);
    for (const [id, value] of [['minimum', coverage.minimumTests], ['executed', coverage.uniqueExecuted], ['shortfall', coverage.shortfall]])
      await expect(page.getByTestId(`acceptance-${id}`)).toHaveText(value === null ? '尚未确定' : String(value));
    if (name === 'duplicate' || name === 'missing') {
      const list = page.getByTestId(name === 'duplicate' ? 'acceptance-duplicates' : 'acceptance-missing');
      await list.locator('summary').focus();
      await page.keyboard.press('Enter');
      await expect(list).toContainText(name === 'duplicate' ? 'copperbench_camp_kit:t01' : 'copperbench_camp_kit:t12');
    }
    const before = await page.evaluate(async () => {
      const path = '/src/bridge/index.ts'; const { coreBridge } = await import(path);
      return { revision: coreBridge.getState().workbench?.workspace.revision, tasks: JSON.stringify(coreBridge.getState().tasks), reads: sessionStorage.getItem('coverageReads') };
    });
    if (['threshold-5-6', 'duplicate', 'missing', 'historical'].includes(name)) await page.screenshot({ path: testInfo.outputPath(`${name}-zh.png`), animations: 'disabled' });
    await page.getByTestId('ui-language-select').selectOption('en');
    await expect(summary).toContainText('Minimum business tests');
    for (const [id, value] of [['minimum', coverage.minimumTests], ['executed', coverage.uniqueExecuted], ['shortfall', coverage.shortfall]])
      await expect(page.getByTestId(`acceptance-${id}`)).toHaveText(value === null ? 'Not yet known' : String(value));
    expect(await summary.evaluate(element => element.scrollWidth <= element.clientWidth + 1)).toBe(true);
    const after = await page.evaluate(async () => {
      const path = '/src/bridge/index.ts'; const { coreBridge } = await import(path);
      return { revision: coreBridge.getState().workbench?.workspace.revision, tasks: JSON.stringify(coreBridge.getState().tasks), reads: sessionStorage.getItem('coverageReads') };
    });
    expect(after).toEqual(before);
    expect(await page.evaluate(() => sessionStorage.getItem('productCommands'))).toBeNull();
    if (['threshold-5-6', 'duplicate', 'missing', 'historical'].includes(name)) await page.screenshot({ path: testInfo.outputPath(`${name}-en.png`), animations: 'disabled' });
  });
}
