import { test, expect, type Page } from '@playwright/test';

async function setup(page: Page, locale: string, failDiscovery = false, emptyAssets = false) {
  await page.addInitScript(({ locale, failDiscovery, emptyAssets }) => {
    localStorage.setItem('copperbench.ui.locale', locale);
    const modulePath = '/src/mock/mockBridge.ts';
    const ready = import(modulePath).then(({ MockCoreBridge }) => {
      const core = new MockCoreBridge(); core.loadScenario('ready'); return core;
    });
    const state = window as unknown as { creationRequests: any[]; discoveryFailed: boolean; entityTextures: string[] };
    state.creationRequests = []; state.discoveryFailed = !failDiscovery;
    state.entityTextures = emptyAssets ? [] : ['stale_skin.png', 'copper_skin.png'];
    window.copperbenchHost = {
      workspaceId: '11111111-1111-4111-8111-111111111111', onEvent() { return () => {}; },
      async invoke(raw) {
        const core = await ready, request = JSON.parse(raw);
        const queryResult = (data: unknown) => JSON.stringify({ messageType: 'query_result', schemaVersion: '1.0',
          requestId: request.requestId, workspaceId: request.workspaceId, operation: request.operation,
          revision: 0, status: 'succeeded', data, diagnostics: [] });
        if (request.operation === 'get_mod_element_field_contract') {
          if (!state.discoveryFailed) { state.discoveryFailed = true; throw new Error('Temporary discovery failure'); }
          const type = request.payload.elementType;
          return queryResult({ elementType: type, availability: 'available', complete: true,
            fields: [{ path: type === 'livingentity' ? '/mobModelTexture' : '/entityTexture', requiredOnCreate: true }] });
        }
        if (request.operation === 'list_assets') {
          const existing = await core.sendQuery(request);
          const template = existing.data.assets.find((asset: { category: string }) => asset.category === 'TEXTURE');
          if (!template) throw new Error('The asset fixture must contain a complete texture descriptor');
          existing.data.assets = [
            ...state.entityTextures.map(name => ({ ...template, id: name, sourceAvailable: true,
              relativePath: `src/main/resources/assets/coppertrails/textures/entities/${name}`,
              health: { ...template.health, assetId: name, status: 'READY', issueCodes: [] } })),
            { ...template, id: 'item', relativePath: 'src/main/resources/assets/coppertrails/textures/item/other.png' }
          ];
          return JSON.stringify(existing);
        }
        if (request.operation === 'create_mod_element') {
          state.creationRequests.push(request.payload);
          const fields = request.payload.initialValues.fields ?? {};
          if (String(fields.entityTexture ?? fields.mobModelTexture).startsWith('stale_skin')) {
            const field = request.payload.elementType === 'livingentity' ? 'mobModelTexture' : 'entityTexture';
            return JSON.stringify({ messageType: 'command_result', schemaVersion: '1.0',
              requestId: request.requestId, workspaceId: request.workspaceId, operation: request.operation,
              status: 'rejected', newRevision: request.expectedRevision, recoveryPointId: null,
              task: null, data: null, conflict: null, denial: null,
              diagnostics: [{ code: 'FIELD_REFERENCE_INVALID', severity: 'error',
                path: `/initialValues/fields/${field}`, elementId: null, recoverable: true, actions: [],
                message: { key: 'diagnostic.input_preflight', fallback: '{field}: expected {expectedType}. {repairHint}',
                  args: { field, expectedType: 'string', repairHint: 'Select an existing texture.' } } }] });
          }
        }
        return JSON.stringify(request.messageType === 'handshake' ? await core.negotiateHandshake(request)
          : request.messageType === 'command' ? await core.sendCommand(request) : await core.sendQuery(request));
      }
    };
  }, { locale, failDiscovery, emptyAssets });
  await page.goto('/');
  await page.getByTestId('nav-elements').click();
  await page.getByTestId('create-element-btn').click();
  await page.getByTestId('create-element-name-input').fill('first_entity');
}

for (const locale of ['zh', 'en']) for (const type of ['livingentity', 'specialentity']) {
  test(`entity creation repairs the same draft and submits its texture (${locale}, ${type})`, async ({ page }, testInfo) => {
    await setup(page, locale);
    await page.getByTestId(`create-element-type-${type}`).click();
    const select = page.getByTestId('create-element-texture');
    await expect(select).toBeVisible();
    await expect(select.locator('option')).toHaveCount(3);
    await page.getByTestId('create-element-submit-btn').click();
    await expect(select).toHaveAttribute('aria-invalid', 'true');
    expect(await page.evaluate(() => (window as any).creationRequests.length)).toBe(0);
    await select.selectOption('stale_skin.png');
    await page.getByTestId('create-element-submit-btn').click();
    await expect(select).toHaveAttribute('aria-invalid', 'true');
    await expect(page.getByTestId('create-element-name-input')).toHaveValue('first_entity');
    await expect(select).toHaveValue('stale_skin.png');
    await select.selectOption('copper_skin.png');
    await page.screenshot({ path: testInfo.outputPath('entity-texture-selection.png'), animations: 'disabled' });
    await page.getByTestId('create-element-submit-btn').click();
    await expect(page.getByTestId('create-element-modal')).toBeHidden();
    await expect(page.getByTestId('element-inspector')).toBeVisible();
    const requests = await page.evaluate(() => (window as any).creationRequests);
    expect(requests).toHaveLength(2);
    expect(requests[1].elementType).toBe(type);
    expect(requests[1].initialValues).toEqual({ fields: type === 'livingentity'
      ? { mobModelTexture: 'copper_skin.png' } : { entityTexture: 'copper_skin' } });
  });
}

test('asset import navigation preserves the entity draft and reloads new textures', async ({ page }) => {
  await setup(page, 'en', false, true);
  await page.getByTestId('create-element-type-specialentity').click();
  await expect(page.getByTestId('create-element-texture').locator('option')).toHaveCount(1);
  await page.getByRole('button', { name: 'Import in Assets', exact: true }).click();
  await expect(page.getByTestId('create-element-modal')).toBeHidden();
  await page.evaluate(() => { (window as any).entityTextures = ['new_boat.png']; });
  await page.getByTestId('nav-elements').click();
  await page.getByTestId('create-element-btn').click();
  await expect(page.getByTestId('create-element-name-input')).toHaveValue('first_entity');
  await expect(page.getByTestId('create-element-type-specialentity')).toHaveAttribute('aria-pressed', 'true');
  await page.getByTestId('create-element-texture').selectOption('new_boat.png');
  await page.getByTestId('create-element-submit-btn').click();
  await expect(page.getByTestId('create-element-modal')).toBeHidden();
});

test('discovery errors can retry without losing the draft and block creation stays independent', async ({ page }) => {
  await setup(page, 'en', true);
  await page.getByTestId('create-element-type-livingentity').click();
  await expect(page.getByRole('alert')).toContainText('Could not load creation requirements');
  await expect(page.getByTestId('create-element-submit-btn')).toBeDisabled();
  await page.getByRole('button', { name: 'Refresh textures', exact: true }).click();
  await expect(page.getByTestId('create-element-texture')).toBeVisible();
  await expect(page.getByTestId('create-element-name-input')).toHaveValue('first_entity');
  await page.getByTestId('create-element-type-block').click();
  await page.getByTestId('create-element-submit-btn').click();
  await expect(page.getByTestId('create-element-modal')).toBeHidden();
  const requests = await page.evaluate(() => (window as any).creationRequests);
  expect(requests[0].initialValues).toEqual({});
});

test('Escape closes the texture menu before its creation dialog and keeps the draft', async ({ page }, testInfo) => {
  await setup(page, 'zh');
  await page.getByTestId('create-element-type-livingentity').click();
  const select = page.getByTestId('create-element-texture');
  const modal = page.getByTestId('create-element-modal');
  await select.click();
  await expect(page.getByRole('listbox')).toBeVisible();
  await select.press('Escape');
  await expect(page.getByRole('listbox')).toHaveCount(0);
  await expect(modal).toBeVisible();
  await expect(select).toBeFocused();
  await expect(page.getByTestId('create-element-name-input')).toHaveValue('first_entity');
  await select.click();
  await page.screenshot({ path: testInfo.outputPath('entity-menu.png') });
  await page.getByRole('listbox').getByRole('option', { name: 'copper_skin.png', exact: true }).click();
  await expect(select).toHaveValue('copper_skin.png');
  await expect(modal).toBeVisible();
  await select.press('Escape');
  await expect(modal).toBeHidden();
});
