import { test, expect, type Page } from '@playwright/test';

async function openFunction(page: Page, mode: 'absent' | 'readOnly' | 'nested' | 'failed' | 'allReadOnly') {
  await page.addInitScript(({ mode }) => {
    const ready = import('/src/mock/mockBridge.ts').then(({ MockCoreBridge }) => {
      const core = new MockCoreBridge(); core.loadScenario('ready'); return core;
    });
    let savedCode = '# New Copperbench function\n';
    window.copperbenchHost = {
      workspaceId: '11111111-1111-4111-8111-111111111111', onEvent() { return () => {}; },
      async invoke(raw) {
        const core = await ready, request = JSON.parse(raw);
        const prefix = mode === 'nested' ? '/fields' : '';
        if (request.operation === 'get_mod_element_editor' && mode === 'failed') throw new Error('Projection unavailable');
        if (request.operation === 'update_mod_element') {
          const changes = request.payload.changes;
          sessionStorage.setItem('functionChanges', JSON.stringify(changes));
          if (changes.some((change: { path: string }) => ![`${prefix}/code`, `${prefix}/namespace`].includes(change.path))) {
            return JSON.stringify({ messageType: 'command_result', schemaVersion: '1.0', requestId: request.requestId,
              workspaceId: request.workspaceId, operation: request.operation, status: 'rejected', newRevision: request.expectedRevision,
              diagnostics: [{ code: 'FIELD_UNSUPPORTED', severity: 'error', recoverable: true, path: '/tags', actions: [],
                message: { key: 'fixture.unsupported', fallback: 'Unsupported function field' } }] });
          }
          savedCode = changes.find((change: { path: string }) => change.path === `${prefix}/code`)?.value ?? savedCode;
        }
        const result = structuredClone(request.messageType === 'handshake' ? await core.negotiateHandshake(request)
          : request.messageType === 'command' ? await core.sendCommand(request) : await core.sendQuery(request));
        if (request.operation === 'get_mod_element_editor') {
          for (const section of result.data.sections) {
            section.fields = section.fields.filter((field: { path: string }) => mode === 'readOnly' || field.path !== '/tags');
            for (const field of section.fields) {
              if (field.path === '/tags') { field.value = ['extension:preserved']; field.readOnly = true; }
              if (field.path === '/code') field.value = savedCode;
              if (mode === 'allReadOnly') field.readOnly = true;
              if (mode === 'nested') field.path = '/fields' + field.path;
            }
          }
        }
        return JSON.stringify(result);
      }
    };
  }, { mode });
  await page.goto('/');
  await page.getByTestId('nav-elements').click();
  await page.getByTestId('create-element-btn').click();
  await page.getByTestId('create-element-modal').getByRole('button', { name: '函数（Function）', exact: true }).click();
  await page.getByTestId('create-element-name-input').fill('stable_persistence');
  await page.getByTestId('create-element-submit-btn').click();
  await expect(page.getByTestId('function-workbench')).toBeVisible();
}

for (const mode of ['absent', 'readOnly', 'nested'] as const) {
  test(`${mode} tags: supported code saves and survives reopening without writing unsupported fields`, async ({ page }) => {
    await openFunction(page, mode);
    const editor = page.getByTestId('function-code-editor');
    await expect(editor).toHaveValue('# New Copperbench function\n');
    await editor.fill('say Stage17 stable persistence\n');
    await page.getByTestId('function-save-btn').click();
    await expect(page.getByTestId('function-dirty-badge')).not.toBeVisible();
    const changes = await page.evaluate(() => JSON.parse(sessionStorage.getItem('functionChanges') ?? '[]'));
    expect(changes.map((change: { path: string }) => change.path)).toContain(mode === 'nested' ? '/fields/code' : '/code');
    expect(changes.some((change: { path: string }) => change.path.endsWith('/tags'))).toBe(false);
    await page.getByTestId('function-tab-tags').click();
    await expect(page.getByTestId('function-add-tag-input')).toBeDisabled();
    if (mode === 'readOnly') await expect(page.getByText('#extension:preserved', { exact: true })).toBeVisible();
    else await expect(page.getByTestId('function-tab-tags')).toContainText('(0)');
    await page.getByTestId('function-back-btn').click();
    await page.locator('[data-element-id]').filter({ hasText: 'stable_persistence' }).first().click();
    await expect(editor).toHaveValue('say Stage17 stable persistence\n');
  });
}

test('unavailable editor projection cannot save invented default values', async ({ page }) => {
  await openFunction(page, 'failed');
  await expect(page.getByTestId('function-save-btn')).toBeDisabled();
  await expect(page.getByTestId('function-code-editor')).not.toBeEditable();
  expect(await page.evaluate(() => sessionStorage.getItem('functionChanges'))).toBeNull();
});

test('read-only function code and namespace stay inspectable without any write controls', async ({ page }) => {
  await openFunction(page, 'allReadOnly');
  await expect(page.getByTestId('function-code-editor')).toHaveValue('# New Copperbench function\n');
  await expect(page.getByTestId('function-code-editor')).not.toBeEditable();
  await expect(page.getByTestId('function-namespace-input')).not.toBeEditable();
  await expect(page.getByTestId('snippet-execute')).toBeDisabled();
  await expect(page.getByTestId('function-save-btn')).toBeDisabled();
});
