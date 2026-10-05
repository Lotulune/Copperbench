import { test, expect, type Page } from '@playwright/test';

const originalXml = '<xml xmlns="https://developers.google.com/blockly/xml"><block type="advancement_trigger" id="persisted"><next><block type="custom_trigger"><mutation extension="keep" /></block></next></block></xml>';

async function openAdvancement(page: Page, mode: 'normal' | 'nested' | 'absent' | 'readOnly' | 'failed' = 'normal') {
  await page.addInitScript(({ mode, originalXml }) => {
    const ready = import('/src/mock/mockBridge.ts').then(({ MockCoreBridge }) => {
      const core = new MockCoreBridge(); core.loadScenario('ready'); return core;
    });
    const values: Record<string, unknown> = { description: 'Original description', triggerxml: originalXml, parent: 'ROOT', rewardXP: 0, rewardFunction: null };
    window.copperbenchHost = {
      workspaceId: '11111111-1111-4111-8111-111111111111', onEvent() { return () => {}; },
      async invoke(raw) {
        const core = await ready, request = JSON.parse(raw);
        if (request.operation === 'get_mod_element_editor' && mode === 'failed') throw new Error('Projection unavailable');
        if (request.operation === 'update_mod_element') {
          const changes = request.payload.changes;
          sessionStorage.setItem('advancementChanges', JSON.stringify(changes));
          for (const change of changes) {
            const name = change.path.split('/').pop();
            if (name === 'criteria' || (mode === 'readOnly' && name === 'triggerxml')) throw new Error('FIELD_UNSUPPORTED');
            values[name] = change.value;
          }
        }
        const result = structuredClone(request.messageType === 'handshake' ? await core.negotiateHandshake(request)
          : request.messageType === 'command' ? await core.sendCommand(request) : await core.sendQuery(request));
        if (request.operation === 'get_mod_element_editor') {
          for (const section of result.data.sections) {
            section.fields = section.fields.filter((field: { path: string }) => mode !== 'absent' || field.path !== '/triggerxml');
            for (const field of section.fields) {
              const name = field.path.split('/').pop();
              if (Object.hasOwn(values, name)) field.value = values[name];
              if (mode === 'readOnly' && name === 'triggerxml') field.readOnly = true;
              if (mode === 'nested') field.path = '/fields' + field.path;
            }
          }
        }
        return JSON.stringify(result);
      }
    };
  }, { mode, originalXml });
  await page.goto('/');
  await page.getByTestId('nav-elements').click();
  await page.getByTestId('create-element-btn').click();
  await page.getByTestId('create-element-modal').getByRole('button', { name: '进度（Advancement / achievement）', exact: true }).click();
  await page.getByTestId('create-element-name-input').fill('persisted_advancement');
  await page.getByTestId('create-element-submit-btn').click();
  await expect(page.getByTestId('advancement-workbench')).toBeVisible();
}

for (const mode of ['normal', 'nested', 'absent', 'readOnly'] as const) {
  test(`${mode}: description saves without replacing persisted conditions or defaults`, async ({ page }) => {
    await openAdvancement(page, mode);
    await expect(page.getByTestId('advancement-desc-input')).toHaveValue('Original description');
    await page.getByTestId('advancement-desc-input').fill('Updated description');
    await page.getByTestId('advancement-save-btn').click();
    await expect(page.getByTestId('advancement-dirty-badge')).not.toBeVisible();
    const changes = await page.evaluate(() => JSON.parse(sessionStorage.getItem('advancementChanges') ?? '[]'));
    expect(changes).toEqual([{ path: mode === 'nested' ? '/fields/description' : '/description', value: 'Updated description' }]);
    await page.getByTestId('advancement-tab-criteria').click();
    await expect(page.getByTestId('advancement-trigger-xml')).toHaveValue(mode === 'absent' ? '' : originalXml);
    if (mode === 'absent' || mode === 'readOnly') await expect(page.getByTestId('advancement-trigger-xml')).not.toBeEditable();
    await page.getByTestId('advancement-back-btn').click();
    await page.locator('[data-element-id]').filter({ hasText: 'persisted_advancement' }).first().click();
    await expect(page.getByTestId('advancement-desc-input')).toHaveValue('Updated description');
  });
}

for (const mode of ['normal', 'nested'] as const) {
  test(`${mode}: actual trigger XML validates, saves and reopens`, async ({ page }) => {
    await openAdvancement(page, mode);
    await page.getByTestId('advancement-tab-criteria').click();
    const editor = page.getByTestId('advancement-trigger-xml');
    await expect(editor).toHaveValue(originalXml);
    await editor.fill('<xml>');
    await expect(page.getByTestId('advancement-save-btn')).toBeDisabled();
    const updated = originalXml.replace('persisted', 'updated');
    await editor.fill(updated);
    await page.getByTestId('advancement-save-btn').click();
    await expect(page.getByTestId('advancement-dirty-badge')).not.toBeVisible();
    expect(await page.evaluate(() => JSON.parse(sessionStorage.getItem('advancementChanges') ?? '[]')))
      .toEqual([{ path: mode === 'nested' ? '/fields/triggerxml' : '/triggerxml', value: updated }]);
    await page.getByTestId('advancement-back-btn').click();
    await page.locator('[data-element-id]').filter({ hasText: 'persisted_advancement' }).first().click();
    await page.getByTestId('advancement-tab-criteria').click();
    await expect(editor).toHaveValue(updated);
  });
}

test('failed projection cannot save invented defaults', async ({ page }) => {
  await openAdvancement(page, 'failed');
  await expect(page.getByTestId('advancement-save-btn')).toBeDisabled();
  await expect(page.getByTestId('advancement-desc-input')).toBeDisabled();
  expect(await page.evaluate(() => sessionStorage.getItem('advancementChanges'))).toBeNull();
});
