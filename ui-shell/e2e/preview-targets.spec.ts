import { test, expect, type Page } from '@playwright/test';

async function fixture(page: Page, ready: boolean, generatorId = 'fabric-26.3') {
  await page.addInitScript(({ ready }) => {
    const corePromise = import('/src/mock/mockBridge.ts').then(({ MockCoreBridge }) => {
      const core = new MockCoreBridge(); core.loadScenario('ready'); return core;
    });
    const requests: unknown[] = [];
    (window as any).previewRequests = requests;
    window.copperbenchHost = {
      workspaceId: '11111111-1111-4111-8111-111111111111', onEvent: () => () => {},
      async invoke(raw) {
        const core = await corePromise, request = JSON.parse(raw);
        if (request.messageType === 'preview') {
          requests.push(request.request);
          const result = await core.sendPreview(request.workspaceId, request.request);
          result.requestId = request.requestId;
          const qualified = new Set(['create_workspace', 'update_workspace_file', 'validate_workspace',
            'generate_workspace', 'build_workspace', 'run_server']);
          for (const target of result.targets ?? []) for (const operation of target.capabilities.operations ?? []) {
            operation.ready = ready && qualified.has(operation.operation);
            operation.available = operation.ready && target.selected;
            operation.reasonCode = operation.ready ? '' : 'PREVIEW_NATIVE_OPERATION_UNVERIFIED';
          }
          if (request.request.operation === 'command' && ready) {
            result.status = 'committed';
            result.core = { messageType: 'command_result', schemaVersion: '1.0', requestId: request.requestId,
              workspaceId: request.workspaceId, operation: 'create_workspace', status: 'committed', newRevision: 0,
              recoveryPointId: null, task: null, data: { workspaceFile: 'D:/preview/camp/camp.mcreator', generatorId: request.request.generatorId },
              diagnostics: [], conflict: null, denial: null };
          }
          return JSON.stringify(result);
        }
        return JSON.stringify(request.messageType === 'handshake' ? await core.negotiateHandshake(request)
          : request.messageType === 'command' ? await core.sendCommand(request) : await core.sendQuery(request));
      }
    };
  }, { ready });
  await page.goto('/');
  await page.getByTestId('nav-new-workspace').click();
  await page.getByTestId(`generator-option-${generatorId}`).click();
  await page.getByTestId('new-workspace-mod-name-input').fill('Camp');
  await page.getByTestId('new-workspace-mod-id-input').fill('camp');
  await page.getByTestId('new-workspace-folder-input').fill('D:/preview/camp');
  await page.getByTestId('confirm-create-workspace-checkbox').check();
}

test('unready preview is visible, opt-in stays scoped and cannot create', async ({ page }, info) => {
  await fixture(page, false);
  await expect(page.getByTestId('preview-capabilities')).toContainText('已登记');
  await expect(page.getByTestId('preview-readiness-reason')).toContainText('尚未通过产品验证');
  await expect(page.getByTestId('create-workspace-submit-btn')).toBeDisabled();
  await page.getByTestId('preview-opt-in').check();
  await expect(page.getByTestId('preview-capabilities')).toContainText('已选择预览');
  await expect(page.getByTestId('create-workspace-submit-btn')).toBeDisabled();
  await page.getByTestId('preview-opt-in').uncheck();
  await expect(page.getByTestId('preview-capabilities')).toContainText('尚未选择预览');
  await page.getByTestId('preview-capabilities').screenshot({ path: info.outputPath('unready-preview.png') });
  const before = await page.evaluate(() => (window as any).previewRequests.length);
  await page.getByTestId('ui-language-select').selectOption('en');
  await expect(page.getByTestId('preview-readiness-reason')).toContainText('not passed product verification');
  await expect(page.getByTestId('new-workspace-mod-name-input')).toHaveValue('Camp');
  expect(await page.evaluate(() => (window as any).previewRequests.length)).toBe(before);
  await page.getByTestId('preview-capabilities').screenshot({ path: info.outputPath('unready-preview-english.png') });
  const writes = await page.evaluate(() => (window as any).previewRequests.filter((r: any) => r.operation === 'command'));
  expect(writes).toEqual([]);
});

for (const generatorId of ['fabric-26.3', 'neoforge-26.3']) {
test(`${generatorId} ready fixture requires explicit choice, revokes on destination change and sends the scoped command`, async ({ page }, info) => {
  await fixture(page, true, generatorId);
  await expect(page.getByTestId('create-workspace-submit-btn')).toBeDisabled();
  await page.getByTestId('preview-opt-in').check();
  await expect(page.getByTestId('create-workspace-submit-btn')).toBeEnabled();
  await page.getByTestId('new-workspace-folder-input').fill('D:/preview/other');
  await expect(page.getByTestId('preview-opt-in')).not.toBeChecked();
  await expect(page.getByTestId('create-workspace-submit-btn')).toBeDisabled();
  await expect.poll(() => page.evaluate(() => (window as any).previewRequests.filter((r: any) => r.operation === 'revoke').length)).toBe(1);
  await page.getByTestId('preview-opt-in').check();
  await expect(page.getByTestId('create-workspace-submit-btn')).toBeEnabled();
  await page.getByTestId('preview-capabilities').screenshot({ path: info.outputPath('selected-preview.png') });
  await page.getByTestId('create-workspace-submit-btn').click();
  await expect(page.getByTestId('workspace-created-banner')).toBeVisible();
  const writes = await page.evaluate(() => (window as any).previewRequests.filter((r: any) => r.operation === 'command'));
  expect(writes).toHaveLength(1);
  expect(writes[0]).toMatchObject({ generatorId, command: 'create_workspace',
    payload: { generatorId, workspaceFolderPath: 'D:/preview/other', userApproved: true } });
});
}

test('historical exact targets remain discoverable through advanced choices', async ({ page }) => {
  await page.goto('/'); await page.getByTestId('nav-new-workspace').click();
  await expect(page.getByTestId('generator-option-fabric-26.3')).toBeVisible();
  await expect(page.getByTestId('generator-option-fabric-26.1.2')).toHaveCount(0);
  await page.getByRole('checkbox', { name: '显示历史精确版本' }).check();
  await page.getByTestId('generator-option-fabric-26.1.2').click();
  await expect(page.getByTestId('selected-generator-info')).toContainText('fabric-26.1.2');
});
