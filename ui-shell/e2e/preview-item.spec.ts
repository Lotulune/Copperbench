import { test, expect, type Page } from '@playwright/test';
import { readFileSync } from 'node:fs';

// Core-generated contract and editor projection; only workspace/element identity is normalized.
const projections = Object.fromEntries(['fabric', 'neoforge'].map(loader => [loader, {
  editor: JSON.parse(readFileSync(new URL(`../../ui-core/fixtures/extensions/preview-items/${loader}-item-editor.json`, import.meta.url), 'utf8')),
  newEditor: JSON.parse(readFileSync(new URL(`../../ui-core/fixtures/extensions/preview-items/${loader}-item-new-editor.json`, import.meta.url), 'utf8'))
}]));

async function fixture(page: Page, loader: 'fabric' | 'neoforge') {
  const { editor, newEditor } = projections[loader];
  const targetId = `${loader}-26.3`;
  await page.addInitScript(({ editor, newEditor, targetId, loader }) => {
    const corePromise = import('/src/mock/mockBridge.ts').then(({ MockCoreBridge }) => {
      const core = new MockCoreBridge(); core.loadScenario('ready');
      const state = core.getState();
      state.workbench!.workspace.generator.id = targetId;
      state.workbench!.workspace.generator.loader = loader;
      state.workbench!.workspace.generator.minecraftVersion = '26.3';
      state.elements = []; state.workbench!.recentElements = [];
      state.workbench!.elementCounts.total = 0;
      return core;
    });
    const requests: any[] = [];
    (window as any).previewItemRequests = requests;
    let currentEditor: any = null;
    window.copperbenchHost = {
      workspaceId: '11111111-1111-4111-8111-111111111111', onEvent: () => () => {},
      async invoke(raw) {
        const core = await corePromise, request = JSON.parse(raw);
        requests.push(request);
        if (request.messageType === 'preview') {
          const result = await core.sendPreview(request.workspaceId, request.request);
          result.requestId = request.requestId;
          for (const target of result.targets ?? []) {
            if (target.generatorId !== targetId) continue;
            const operations = ['create_mod_element', 'update_mod_element', 'generate_workspace', 'build_workspace'];
            target.capabilities.operations = operations.map(operation => ({ operation, ready: true,
              reasonCode: '', available: target.selected, accessReasonCode: target.selected ? '' : 'PREVIEW_OPT_IN_REQUIRED', evidence: 'core-item-fixture' }));
            target.capabilities.elementTypes = target.capabilities.elementTypes?.map(type => type.elementType === 'item'
              ? { ...type, supported: true, reasonCode: '', fields: editor.fieldContract.fields.map((field: any) => field.path.substring(1)) } : type);
          }
          if (request.request.operation === 'command') {
            const scoped = request.request;
            const command = { messageType: 'command' as const, schemaVersion: '1.0' as const, requestId: request.requestId,
              workspaceId: request.workspaceId, expectedRevision: scoped.expectedRevision, operation: scoped.command,
              payload: { ...scoped.payload, clientMutationId: request.requestId } };
            const response = await core.sendCommand(command);
            if (scoped.command === 'create_mod_element' && response.data?.element) {
              currentEditor = structuredClone(newEditor);
              currentEditor.element = response.data.element;
            }
            if (scoped.command === 'update_mod_element' && currentEditor) {
              for (const change of scoped.payload.changes) for (const section of currentEditor.sections)
                for (const field of section.fields) if (field.path === change.path) field.value = change.value;
            }
            result.core = response; result.status = response.status; result.revision = response.newRevision;
            result.diagnostics = response.diagnostics;
          }
          return JSON.stringify(result);
        }
        if (request.operation === 'get_mod_element_editor' && currentEditor) return JSON.stringify({
          messageType: 'query_result', schemaVersion: request.schemaVersion, requestId: request.requestId, workspaceId: request.workspaceId,
          operation: request.operation, status: 'succeeded', revision: core.getState().workbench!.workspace.revision,
          data: currentEditor, diagnostics: [] });
        return JSON.stringify(request.messageType === 'handshake' ? await core.negotiateHandshake(request)
          : request.messageType === 'command' ? await core.sendCommand(request) : await core.sendQuery(request));
      }
    };
  }, { editor, newEditor, targetId, loader });
  await page.goto('/');
  await page.getByTestId('nav-elements').click();
  await expect(page.getByTestId('workspace-preview-choice')).toBeVisible();
}

for (const loader of ['fabric', 'neoforge'] as const) for (const language of ['zh', 'en'])
test(`bounded item preview create/edit/generate (${loader}, ${language})`, async ({ page }, info) => {
  await fixture(page, loader);
  await page.getByTestId('ui-language-select').selectOption(language);
  await page.getByTestId('create-element-btn').click();
  const dialog = page.getByTestId('create-element-modal');
  await dialog.getByTestId('create-element-name-input').fill('trail_kit');
  await expect(dialog.getByTestId('create-element-type-item')).toBeEnabled();
  await expect(dialog.getByTestId('create-element-type-recipe')).toBeDisabled();
  await expect(dialog.getByTestId('create-element-submit-btn')).toBeDisabled();
  await dialog.getByRole('checkbox').check();
  await expect(dialog.getByTestId('create-element-submit-btn')).toBeEnabled();
  await dialog.screenshot({ path: info.outputPath(`create-item-${language}.png`) });
  await dialog.getByTestId('create-element-submit-btn').click();
  await expect(dialog).toBeHidden();
  const inspector = page.getByTestId('element-inspector');
  await expect(inspector).toBeVisible();
  await expect(inspector.getByTestId('field-enableRanged')).toHaveCount(0);
  await expect(inspector.getByTestId('field-fuelBurnTime')).toHaveValue('0');
  await expect(inspector.getByTestId('field-stackSize')).toHaveValue('64');
  await expect(inspector.getByTestId('field-compostingLayers')).toHaveValue('0');
  await inspector.getByTestId('field-displayName').fill('Trail Kit');
  await inspector.getByTestId('field-stackSize').fill('16');
  await inspector.getByTestId('field-rarity').selectOption('RARE');
  await inspector.getByTestId('field-immuneToFire').check();
  await inspector.getByTestId('field-fuelBurnTime').fill('400');
  await inspector.getByTestId('field-compostingLayers').fill('2');
  await inspector.getByTestId('field-texture').selectOption('minecraft:coal');
  await inspector.locator('.element-editor-form').evaluate(element => { element.scrollTop = element.scrollHeight; });
  const compostBox = await inspector.getByTestId('field-compostingLayers').boundingBox();
  const formBox = await inspector.locator('.element-editor-form').boundingBox();
  expect(compostBox!.y + compostBox!.height).toBeLessThanOrEqual(formBox!.y + formBox!.height);
  await inspector.screenshot({ path: info.outputPath(`edit-item-${language}.png`) });
  const writes = () => page.evaluate(() => (window as any).previewItemRequests.filter((r: any) => r.messageType === 'preview' && r.request.operation === 'command'));
  const before = (await writes()).length;
  await page.getByTestId('ui-language-select').selectOption(language === 'zh' ? 'en' : 'zh');
  expect((await writes()).length).toBe(before);
  await expect(inspector.getByTestId('field-fuelBurnTime')).toHaveValue('400');
  await expect(inspector.getByTestId('inspector-delete-btn')).toBeDisabled();
  await inspector.getByTestId('workspace-preview-choice').getByRole('checkbox').uncheck();
  await expect(inspector.getByTestId('inspector-save-btn')).toBeDisabled();
  await expect(inspector.getByTestId('field-fuelBurnTime')).toHaveValue('400');
  await inspector.getByTestId('workspace-preview-choice').getByRole('checkbox').check();
  await inspector.getByTestId('inspector-save-btn').click();
  await expect.poll(async () => (await writes()).filter((r: any) => r.request.command === 'update_mod_element').length).toBe(1);
  const update = (await writes()).find((r: any) => r.request.command === 'update_mod_element');
  expect(update.request).toMatchObject({ generatorId: `${loader}-26.3`, payload: { changes: expect.arrayContaining([
    { path: '/stackSize', value: 16 }, { path: '/rarity', value: 'RARE' }, { path: '/immuneToFire', value: true },
    { path: '/fuelBurnTime', value: 400 }, { path: '/compostingLayers', value: 2 }, { path: '/texture', value: 'minecraft:coal' }
  ]) } });
  const legacyWrites = await page.evaluate(() => (window as any).previewItemRequests.filter((r: any) => r.messageType === 'command' && ['create_mod_element', 'update_mod_element'].includes(r.operation)));
  expect(legacyWrites).toEqual([]);
  await page.getByTestId('compact-run-menu').click();
  await page.getByTestId('titlebar-generate-btn').click();
  await expect.poll(async () => (await writes()).filter((r: any) => r.request.command === 'generate_workspace').length).toBe(1);
  expect((await writes()).find((r: any) => r.request.command === 'generate_workspace').request.generatorId).toBe(`${loader}-26.3`);

  // A fresh browser/product session has no inherited opt-in.
  await page.reload();
  await page.getByTestId('nav-elements').click();
  await expect(page.getByTestId('workspace-preview-choice').getByRole('checkbox')).not.toBeChecked();
});
