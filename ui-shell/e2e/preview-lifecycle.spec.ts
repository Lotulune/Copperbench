import { test, expect, type Page } from '@playwright/test';

const manifest = { schemaVersion: '1', id: 'workspace-tests-v1', identityKind: 'gametest_resource', minimumTests: 1,
  cases: [{ className: '', name: 'example:business_behavior' }], sha256: 'a'.repeat(64) };

async function fixture(page: Page, generatorId: string) {
  await page.addInitScript(({ generatorId, manifest }) => {
    const corePromise = import('/src/mock/mockBridge.ts').then(({ MockCoreBridge }) => {
      const core = new MockCoreBridge(); core.loadScenario('ready');
      const state = core.getState();
      state.workbench!.workspace.generator.id = generatorId;
      state.workbench!.workspace.generator.minecraftVersion = generatorId.split('-').slice(1).join('-');
      return core;
    });
    const requests: any[] = []; (window as any).lifecycleRequests = requests;
    const finishedTasks: Record<string, any> = {};
    window.copperbenchHost = {
      workspaceId: '11111111-1111-4111-8111-111111111111', onEvent: () => () => {},
      async invoke(raw) {
        const core = await corePromise, request = JSON.parse(raw); requests.push(request);
        if (request.messageType === 'preview') {
          const result = await core.sendPreview(request.workspaceId, request.request); result.requestId = request.requestId;
          for (const target of result.targets ?? []) {
            if (target.generatorId !== generatorId) continue;
            for (const operation of target.capabilities.operations ?? []) {
              operation.ready = true; operation.available = target.selected; operation.reasonCode = '';
              operation.accessReasonCode = target.selected ? '' : 'PREVIEW_OPT_IN_REQUIRED';
            }
          }
          const scoped = request.request;
          const acceptance = scoped.operation === 'acceptance' ? scoped.payload : null;
          if (acceptance?.operation === 'catalog') {
            result.acceptance = { contract: 'required_cases', schemaVersion: '1', selectedVersion: '1', requestId: request.requestId,
              status: 'succeeded', manifests: [manifest] };
          } else if (scoped.operation === 'command' || acceptance) {
            const command = acceptance ? acceptance.operation === 'run' ? 'run_gametest' : 'export_workspace' : scoped.command;
            const response = await core.sendCommand({ messageType: 'command', schemaVersion: '1.0', requestId: request.requestId,
              workspaceId: request.workspaceId, expectedRevision: acceptance?.expectedRevision ?? scoped.expectedRevision,
              operation: command, payload: acceptance?.payload ?? scoped.payload });
            if (command === 'run_gametest' && response.task) {
              finishedTasks[response.task.id] = { ...response.task, state: 'succeeded', progress: 1,
                verification: { status: 'passed', reasonCode: 'GAMETEST_PASSED', discovered: 1, executed: 1,
                  passed: 1, failed: 0, skipped: 0, artifactSha256: 'b'.repeat(64), cases: [], sourceCurrentAtCompletion: true } };
            }
            result.status = response.status; result.diagnostics = response.diagnostics; result.revision = response.newRevision;
            if (acceptance) result.acceptance = { contract: 'required_cases', schemaVersion: '1', selectedVersion: '1',
              requestId: request.requestId, status: response.status, core: response };
            else result.core = response;
          }
          return JSON.stringify(result);
        }
        if (request.messageType === 'acceptance') return JSON.stringify({ contract: 'required_cases', schemaVersion: '1',
          selectedVersion: '1', requestId: request.requestId, status: 'succeeded', core: { workspaceId: request.workspaceId },
          coverageDiagnostics: { schemaVersion: '1', taskId: request.request.payload.taskId, state: 'passed_current_input',
            reasonCode: 'GAMETEST_PASSED', evidenceScope: 'required_cases', inputScope: 'current_at_completion',
            required: 1, minimumTests: 1, uniqueExecuted: 1, shortfall: 0, duplicates: [], missingCases: [] } });
        if (request.operation === 'get_task' && finishedTasks[request.payload.taskId]) return JSON.stringify({
          messageType: 'query_result', schemaVersion: '1.0', requestId: request.requestId, workspaceId: request.workspaceId,
          operation: request.operation, status: 'succeeded', revision: core.getState().workbench!.workspace.revision,
          data: { task: finishedTasks[request.payload.taskId], logs: [], diagnostics: [] }, diagnostics: [] });
        return JSON.stringify(request.messageType === 'handshake' ? await core.negotiateHandshake(request)
          : request.messageType === 'command' ? await core.sendCommand(request) : await core.sendQuery(request));
      }
    };
  }, { generatorId, manifest });
  await page.goto('/'); await page.getByTestId('nav-elements').click();
}

for (const loader of ['fabric', 'neoforge']) test(`preview lifecycle and explicit acceptance (${loader})`, async ({ page }, info) => {
  await fixture(page, `${loader}-26.3`);
  await page.getByTestId('ui-language-select').selectOption('en');
  await page.getByTestId('workspace-preview-choice').getByRole('checkbox').check();
  await page.getByTestId('titlebar-run-btn').click();
  const requests = () => page.evaluate(() => (window as any).lifecycleRequests);
  await expect.poll(async () => (await requests()).some((r: any) => r.request?.command === 'run_client')).toBe(true);
  page.once('dialog', dialog => dialog.accept());
  await page.getByTestId('compact-run-menu').click();
  await page.getByRole('button', { name: 'Run isolated dedicated server', exact: true }).click();
  await expect.poll(async () => (await requests()).some((r: any) => r.request?.command === 'run_server')).toBe(true);
  await page.getByTestId('compact-run-menu').click();
  await page.getByRole('button', { name: 'Run staged data generation', exact: true }).click();
  await expect.poll(async () => (await requests()).some((r: any) => r.request?.command === 'run_datagen')).toBe(true);
  await page.getByTestId('compact-run-menu').click();
  await page.getByRole('button', { name: 'Run existing GameTests', exact: true }).click();
  const dialog = page.getByTestId('acceptance-run-dialog');
  await expect(dialog).toBeVisible();
  await expect(dialog.getByRole('button', { name: 'Run tests', exact: true })).toBeDisabled();
  await dialog.getByLabel('Published manifest').selectOption(manifest.id);
  await dialog.getByText('Required cases (1)', { exact: true }).click();
  await expect(dialog.getByText('example:business_behavior', { exact: true })).toBeVisible();
  await dialog.getByRole('checkbox').uncheck();
  await expect(dialog.getByRole('button', { name: 'Run tests', exact: true })).toBeDisabled();
  await dialog.getByRole('checkbox').check();
  await dialog.screenshot({ path: info.outputPath('acceptance-dialog.png') });
  if (loader === 'neoforge') {
    await page.emulateMedia({ colorScheme: 'dark', reducedMotion: 'reduce' });
    await page.getByTestId('ui-language-select').selectOption('zh');
    await expect(dialog.getByRole('button', { name: '运行测试', exact: true })).toBeEnabled();
    await dialog.screenshot({ path: info.outputPath('acceptance-dialog-zh-dark.png') });
    await page.getByTestId('ui-language-select').selectOption('en');
  }
  await dialog.getByRole('button', { name: 'Run tests', exact: true }).click();
  await expect(dialog).toBeHidden();
  await expect(page.getByRole('button', { name: 'Export this verified JAR', exact: true })).toBeVisible();
  const run = (await requests()).find((r: any) => r.request?.payload?.operation === 'run');
  expect(run.request).toMatchObject({ operation: 'acceptance', generatorId: `${loader}-26.3`,
    payload: { supportedVersions: ['1'], operation: 'run', payload: { requiredCases: manifest } } });
  await page.getByRole('button', { name: 'Export this verified JAR', exact: true }).click();
  await expect.poll(async () => (await requests()).some((r: any) => r.request?.payload?.operation === 'export')).toBe(true);
  expect((await requests()).find((r: any) => r.request?.payload?.operation === 'export').request.payload.payload)
    .toMatchObject({ allowHistorical: false, verifiedTaskId: expect.any(String) });
  expect((await requests()).filter((r: any) => r.messageType === 'command'
    && ['run_client', 'run_server', 'run_datagen', 'run_gametest', 'export_workspace'].includes(r.operation))).toEqual([]);
});

test('preview acceptance imports custom cases and rejects malformed manifests', async ({ page }) => {
  await fixture(page, 'fabric-26.3');
  await page.getByTestId('ui-language-select').selectOption('en');
  await page.getByTestId('workspace-preview-choice').getByRole('checkbox').check();
  await page.getByTestId('compact-run-menu').click();
  await page.getByRole('button', { name: 'Run existing GameTests', exact: true }).click();
  const dialog = page.getByTestId('acceptance-run-dialog');
  const file = dialog.getByLabel('Or load your own manifest');
  await file.setInputFiles({ name: 'invalid.json', mimeType: 'application/json', buffer: Buffer.from('{}') });
  await expect(dialog.getByRole('alert')).toBeVisible();
  await expect(dialog.getByRole('button', { name: 'Run tests', exact: true })).toBeDisabled();
  const custom = { ...manifest, id: 'my-mod-v1', cases: [{ className: '', name: 'my_mod:keeps_data' }] };
  await file.setInputFiles({ name: 'custom.json', mimeType: 'application/json', buffer: Buffer.from(JSON.stringify(custom)) });
  await dialog.getByRole('button', { name: 'Run tests', exact: true }).click();
  await expect(dialog).toBeHidden();
  const run = await page.evaluate(() => (window as any).lifecycleRequests.find((r: any) => r.request?.payload?.operation === 'run'));
  expect(run.request.payload.payload.requiredCases).toEqual(custom);
});

test('stable workspace retains the legacy GameTest route', async ({ page }) => {
  await fixture(page, 'fabric-26.2');
  await page.getByTestId('ui-language-select').selectOption('en');
  await page.getByTestId('compact-run-menu').click();
  await page.getByRole('button', { name: 'Run existing GameTests', exact: true }).click();
  await expect(page.getByTestId('acceptance-run-dialog')).toBeHidden();
  await expect.poll(() => page.evaluate(() => (window as any).lifecycleRequests.some((r: any) =>
    r.messageType === 'command' && r.operation === 'run_gametest'))).toBe(true);
});
