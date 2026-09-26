import { test, expect, type Page } from '@playwright/test';

async function installFixture(page: Page, mode: 'saveFailure' | 'invalidJson' | 'sourceUnavailable' | 'repairStale' | 'invalidRevision', invalidRevision?: unknown) {
  await page.addInitScript(({ mode, invalidRevision }) => {
    const modulePath = '/src/mock/mockBridge.ts';
    const ready = import(modulePath).then(({ MockCoreBridge }) => {
      const core = new MockCoreBridge();
      core.loadScenario(mode === 'sourceUnavailable' ? 'compile-diagnostic' : mode === 'repairStale' ? 'generator-repair' : 'ready');
      return core;
    });
    window.copperbenchHost = {
      workspaceId: '11111111-1111-4111-8111-111111111111', onEvent() { return () => {}; },
      async invoke(raw) {
        const core = await ready, request = JSON.parse(raw);
        if (request.messageType === 'command') {
          const calls = JSON.parse(sessionStorage.getItem('diagnosticCommands') ?? '[]');
          sessionStorage.setItem('diagnosticCommands', JSON.stringify([...calls, request.operation]));
        }
        if (request.operation === 'update_mod_element' && (mode === 'saveFailure' || mode === 'invalidRevision')) {
          return JSON.stringify({ messageType: 'command_result', schemaVersion: '1.0', requestId: request.requestId,
            workspaceId: request.workspaceId, operation: request.operation, status: 'rejected',
            newRevision: mode === 'invalidRevision' ? invalidRevision : request.expectedRevision,
            task: null, data: null, conflict: null, denial: null, diagnostics: [{ code: 'FIELD_FIXTURE', severity: 'error',
              recoverable: true, path: null, actions: [], message: { key: 'diagnostic.field_contract_invalid',
                fallback: '{field}: {reason}', args: { field: 'user_field', reason: 'Raw reason 中文' } } }] });
        }
        const result = request.messageType === 'handshake' ? await core.negotiateHandshake(request)
          : request.messageType === 'command' ? await core.sendCommand(request) : await core.sendQuery(request);
        if (request.operation === 'get_workbench') {
          result.data.activeTasks = [];
          result.data.recentTasks = Object.values(core.getState().tasks);
        }
        if (mode === 'invalidJson' && request.operation === 'get_mod_element_editor') {
          result.data.sections[0].fields = [...result.data.sections[0].fields.filter((field: { path: string }) => field.path !== '/fields/customJson'), { path: '/fields/customJson', label: { key: 'extension.custom_json', fallback: 'Custom JSON' },
            control: 'json', value: { user: '中文' }, readOnly: false, required: false, options: [], diagnostics: [] }];
        }
        if (request.operation === 'get_task') {
          if (mode === 'sourceUnavailable' && request.payload.sourcePath) {
            sessionStorage.setItem('sourceReads', String(Number(sessionStorage.getItem('sourceReads') ?? 0) + 1));
            result.data.source = null;
          }
          if (mode === 'repairStale') for (const diagnostic of result.data.diagnostics) {
            for (const action of diagnostic.actions) if (action.kind === 'preview_repair') action.payload.expectedRevision = 0;
          }
        }
        return JSON.stringify(result);
      }
    };
  }, { mode, invalidRevision });
}

test('failed field save retains its draft and raw diagnostic arguments across languages', async ({ page }, testInfo) => {
  await installFixture(page, 'saveFailure');
  await page.goto('/');
  await page.getByTestId('nav-elements').click();
  await page.locator('[data-element-id="22222222-2222-4222-8222-222222222221"]').first().click();
  const field = page.getByTestId('field-displayName');
  await field.fill('User draft 中文');
  await page.getByTestId('inspector-save-btn').click();
  await expect(page.getByTestId('validation-alert')).toContainText('user_field：Raw reason 中文');
  await page.getByTestId('ui-language-select').selectOption('en');
  await expect(page.getByTestId('validation-alert')).toContainText('Validation failed');
  await expect(page.getByTestId('validation-alert')).toContainText('user_field: Raw reason 中文');
  await expect(page.getByLabel('Hardness', { exact: false })).toHaveValue('2');
  await expect(field).toHaveValue('User draft 中文');
  await expect(page.getByTestId('element-change-preview')).toContainText('1 field');
  await expect(page.getByTestId('titlebar-workspace')).toContainText('42');
  expect(await page.evaluate(() => JSON.parse(sessionStorage.getItem('diagnosticCommands') ?? '[]'))).toEqual(['update_mod_element']);
  for (const [width, height] of [[1280, 720], [1920, 1080]]) {
    await page.setViewportSize({ width, height });
    const alert = page.getByTestId('validation-alert');
    expect(await alert.evaluate(el => el.scrollWidth <= el.clientWidth + 1)).toBe(true);
    await page.evaluate(() => new Promise<void>(resolve => requestAnimationFrame(() => requestAnimationFrame(() => resolve()))));
    await page.screenshot({ path: testInfo.outputPath(`english-field-error-${width}.png`), animations: 'disabled' });
  }
});

for (const [name, revision] of [['missing', undefined], ['null', null], ['string', '43'], ['negative', -1], ['fractional', 1.5], ['unsafe', 9007199254740992]] as const) {
  test(`malformed ${name} command revision cannot corrupt workspace state`, async ({ page }) => {
    await installFixture(page, 'invalidRevision', revision);
    await page.goto('/');
    await page.getByTestId('nav-elements').click();
    await page.locator('[data-element-id="22222222-2222-4222-8222-222222222221"]').first().click();
    await page.getByTestId('field-displayName').fill('Preserved draft');
    await page.getByTestId('inspector-save-btn').click();
    await expect(page.getByTestId('validation-alert')).toContainText('保存失败');
    await page.getByTestId('ui-language-select').selectOption('en');
    await expect(page.getByTestId('validation-alert')).toContainText('Save failed.');
    await expect(page.getByTestId('titlebar-workspace')).toContainText('42');
    await expect(page.getByTestId('titlebar-workspace')).not.toContainText('NaN');
    await expect(page.getByTestId('field-displayName')).toHaveValue('Preserved draft');
  });
}

test('invalid JSON remains editable and blocks save in either language', async ({ page }) => {
  await installFixture(page, 'invalidJson');
  await page.goto('/');
  await page.getByTestId('nav-elements').click();
  await page.locator('[data-element-id="22222222-2222-4222-8222-222222222221"]').first().click();
  const json = page.getByTestId('field-customJson');
  await json.fill('{ invalid 中文');
  await expect(page.getByTestId('validation-alert')).toContainText('JSON 字段格式无效');
  await page.getByTestId('ui-language-select').selectOption('en');
  await expect(page.getByTestId('validation-alert')).toContainText('Invalid JSON field');
  await expect(json).toHaveValue('{ invalid 中文');
  await expect(page.getByTestId('inspector-save-btn')).toBeDisabled();
  expect(await page.evaluate(() => JSON.parse(sessionStorage.getItem('diagnosticCommands') ?? '[]'))).toEqual([]);
});

test('English compiler diagnostics open source and locate the owning element', async ({ page }, testInfo) => {
  await page.goto('/');
  await page.getByTestId('scenario-switcher-trigger').click();
  await page.getByTestId('scenario-btn-compile-diagnostic').click();
  await page.getByTestId('open-failed-task-logs-btn').click();
  await page.getByTestId('task-diag-action-open_generated_source').click();
  const source = await page.getByTestId('task-source-content').innerText();
  await page.getByTestId('ui-language-select').selectOption('en');
  await expect(page.getByTestId('task-source-preview')).toHaveAttribute('aria-label', 'Generated source preview');
  await expect(page.getByTestId('task-source-content')).toHaveText(source);
  await expect(page.getByTestId('task-source-preview')).toContainText('CopperLampElement.java:42');
  for (const [width, height] of [[1280, 720], [1920, 1080]]) {
    await page.setViewportSize({ width, height });
    expect(await page.getByTestId('task-diagnostics').evaluate(el => el.scrollWidth <= el.clientWidth + 1)).toBe(true);
    await page.evaluate(() => new Promise<void>(resolve => requestAnimationFrame(() => requestAnimationFrame(() => resolve()))));
    await page.screenshot({ path: testInfo.outputPath(`english-compiler-${width}.png`), animations: 'disabled' });
  }
  await page.getByTestId('task-diag-action-locate_compile_element').focus();
  await page.keyboard.press('Enter');
  await expect(page.getByTestId('field-displayName')).toHaveValue('Copper Lamp');
  await expect(page.getByTestId('field-name')).toHaveValue('copper_lamp');
});

test('source preview failure changes language without another source request', async ({ page }) => {
  await installFixture(page, 'sourceUnavailable');
  await page.goto('/');
  await page.getByTestId('recent-tasks-button').click();
  await page.getByTestId('task-diag-action-open_generated_source').click();
  await expect(page.getByTestId('task-source-error')).toContainText('生成源码预览不可用');
  await page.getByTestId('ui-language-select').selectOption('en');
  await expect(page.getByTestId('task-source-error')).toContainText('Generated source preview is unavailable.');
  expect(await page.evaluate(() => sessionStorage.getItem('sourceReads'))).toBe('1');
});

test('stale repair remains blocked after changing language', async ({ page }) => {
  await installFixture(page, 'repairStale');
  await page.goto('/');
  await page.getByTestId('recent-tasks-button').click();
  await page.getByTestId('task-diag-action-preview_generator_repair').click();
  await expect(page.getByTestId('task-repair-error')).toContainText('旧');
  await page.getByTestId('ui-language-select').selectOption('en');
  await expect(page.getByTestId('task-repair-error')).toContainText('older workspace revision');
  await expect(page.getByTestId('task-repair-apply')).toHaveCount(0);
  expect(await page.evaluate(() => JSON.parse(sessionStorage.getItem('diagnosticCommands') ?? '[]'))).toEqual([]);
});

test('English datagen publication keeps explicit confirmation and keyboard cancellation', async ({ page }, testInfo) => {
  await page.goto('/');
  await page.getByTestId('ui-language-select').selectOption('en');
  await page.getByRole('button', { name: 'Run staged data generation', exact: true }).click();
  await expect(page.getByRole('button', { name: 'View staged changes', exact: true })).toBeVisible({ timeout: 5000 });
  await page.getByRole('button', { name: 'View staged changes', exact: true }).click();
  await expect(page.getByTestId('datagen-preview')).toContainText('Staged changes: 1');
  await page.getByTestId('datagen-publish-btn').click();
  const dialog = page.getByTestId('datagen-publish-dialog');
  await expect(dialog).toHaveAccessibleName('Publish generated data');
  await expect(dialog).toContainText('recovery point');
  await expect(dialog).toContainText('Write 1 staged file to this workspace.');
  await page.screenshot({ path: testInfo.outputPath('english-datagen-confirmation.png'), animations: 'disabled' });
  await page.keyboard.press('Escape');
  await expect(dialog).toHaveCount(0);
  await expect(page.getByTestId('datagen-preview')).toContainText('Staged changes: 1');
  await page.getByTestId('datagen-publish-btn').click();
  await page.getByTestId('datagen-confirm-publish').click();
  await expect(page.getByTestId('datagen-preview')).toContainText('Generated results published');
});
