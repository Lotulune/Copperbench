import { test, expect, type Page } from '@playwright/test';
import { readFileSync } from 'node:fs';

// Core-generated contract and editor projection; only workspace/element identity is normalized.
const projections = Object.fromEntries(['fabric', 'neoforge'].map(loader => [loader, {
  editor: JSON.parse(readFileSync(new URL(`../../ui-core/fixtures/extensions/preview-blocks/${loader}-block-editor.json`, import.meta.url), 'utf8')),
  newEditor: JSON.parse(readFileSync(new URL(`../../ui-core/fixtures/extensions/preview-blocks/${loader}-block-new-editor.json`, import.meta.url), 'utf8'))
}]));

async function fixture(page: Page, loader: 'fabric' | 'neoforge', drift = false) {
  const { editor, newEditor } = projections[loader];
  const targetId = `${loader}-26.3`;
  await page.addInitScript(({ editor, newEditor, targetId, loader, drift }) => {
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
    (window as any).previewBlockRequests = requests;
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
              reasonCode: '', available: target.selected, accessReasonCode: target.selected ? '' : 'PREVIEW_OPT_IN_REQUIRED', evidence: 'core-block-fixture' }));
            target.capabilities.elementTypes = target.capabilities.elementTypes?.map(type => type.elementType === 'block'
              ? { ...type, supported: true, reasonCode: '', fields: editor.fieldContract.fields.map((field: any) => field.path.substring(1)) } : type);
          }
          if (request.request.operation === 'command') {
            const scoped = request.request;
            const command = { messageType: 'command' as const, schemaVersion: '1.0' as const, requestId: request.requestId,
              workspaceId: request.workspaceId, expectedRevision: scoped.expectedRevision, operation: scoped.command,
              payload: { ...scoped.payload, clientMutationId: request.requestId } };
            const response = drift && scoped.command === 'apply_workspace_plan'
              ? { messageType: 'command_result', schemaVersion: '1.0', requestId: request.requestId, workspaceId: request.workspaceId,
                operation: scoped.command, status: 'committed', newRevision: ++core.getState().workbench!.workspace.revision,
                data: null, task: null, diagnostics: [] } as any : await core.sendCommand(command);
            if (scoped.command === 'create_mod_element' && response.data?.element) {
              currentEditor = structuredClone(newEditor);
              currentEditor.element = response.data.element;
              if (drift) {
                const operation = { operation: 'update_mod_element', payload: { elementId: currentEditor.element.id,
                  changes: [{ path: '/hardness', value: 1 }, { path: '/configurationResolution',
                    value: { mode: 'adopt_definition', sourceFingerprint: 'a'.repeat(64) } }] } };
                currentEditor.configuration = { status: 'drift', differences: [{ path: '/hardness', declared: 3, effective: 1 }],
                  planOperations: { adopt_definition: operation, reapply_declared: operation } };
              }
            }
            if (scoped.command === 'apply_workspace_plan' && currentEditor) currentEditor.configuration = { status: 'consistent' };
            if (scoped.command === 'update_mod_element' && currentEditor) {
              for (const change of scoped.payload.changes) for (const section of currentEditor.sections)
                for (const field of section.fields) if (field.path === change.path) field.value = change.value;
            }
            result.core = response; result.status = response.status; result.revision = response.newRevision;
            result.diagnostics = response.diagnostics;
          }
          return JSON.stringify(result);
        }
        if (drift && request.operation === 'plan_workspace_changes') return JSON.stringify({
          messageType: 'query_result', schemaVersion: request.schemaVersion, requestId: request.requestId, workspaceId: request.workspaceId,
          operation: request.operation, status: 'succeeded', revision: core.getState().workbench!.workspace.revision,
          data: { workspaceId: request.workspaceId, baseRevision: request.payload.expectedRevision,
            operations: request.payload.operations, semanticDiff: [], safety: { ready: true } }, diagnostics: [] });
        if (request.operation === 'get_mod_element_editor' && currentEditor) return JSON.stringify({
          messageType: 'query_result', schemaVersion: request.schemaVersion, requestId: request.requestId, workspaceId: request.workspaceId,
          operation: request.operation, status: 'succeeded', revision: core.getState().workbench!.workspace.revision,
          data: currentEditor, diagnostics: [] });
        return JSON.stringify(request.messageType === 'handshake' ? await core.negotiateHandshake(request)
          : request.messageType === 'command' ? await core.sendCommand(request) : await core.sendQuery(request));
      }
    };
  }, { editor, newEditor, targetId, loader, drift });
  await page.goto('/');
  await page.getByTestId('nav-elements').click();
  await expect(page.getByTestId('workspace-preview-choice')).toBeVisible();
}

for (const loader of ['fabric', 'neoforge'] as const) for (const language of ['zh', 'en'])
test(`bounded block preview create/edit/generate (${loader}, ${language})`, async ({ page }, info) => {
  await fixture(page, loader);
  await page.getByTestId('ui-language-select').selectOption(language);
  await page.getByTestId('create-element-btn').click();
  const dialog = page.getByTestId('create-element-modal');
  await dialog.getByTestId('create-element-name-input').fill('camp_post');
  await expect(dialog.getByTestId('create-element-type-block')).toBeEnabled();
  await expect(dialog.getByTestId('create-element-type-recipe')).toBeDisabled();
  await expect(dialog.getByTestId('create-element-submit-btn')).toBeDisabled();
  await dialog.getByRole('checkbox').check();
  await expect(dialog.getByTestId('create-element-submit-btn')).toBeEnabled();
  await dialog.screenshot({ path: info.outputPath(`create-block-${language}.png`) });
  await dialog.getByTestId('create-element-submit-btn').click();
  await expect(dialog).toBeHidden();
  const inspector = page.getByTestId('element-inspector');
  await expect(inspector).toBeVisible();
  await expect(inspector.getByTestId('field-hasInventory')).toHaveCount(0);
  await expect(inspector.getByTestId('field-hardness')).toHaveValue('0');
  await expect(inspector.getByTestId('field-maxStackSize')).toHaveValue('64');
  await expect(inspector.getByTestId('field-rotationMode')).toHaveValue('0');
  await expect(inspector.getByTestId('field-rotationMode').locator('option')).toHaveCount(2);
  await expect(inspector.getByTestId('field-rotationMode').locator('option[value="5"]')).toHaveText(language === 'zh' ? '柱形轴向（X/Y/Z）' : 'Pillar axis (X/Y/Z)');
  await inspector.getByTestId('field-displayName').fill('Camp Post');
  await inspector.getByTestId('field-maxStackSize').fill('16');
  await inspector.getByTestId('field-rarity').selectOption('UNCOMMON');
  await inspector.getByTestId('field-hardness').fill('5');
  await inspector.getByTestId('field-resistance').fill('10');
  await inspector.getByTestId('field-rotationMode').selectOption('5');
  await inspector.getByTestId('field-texture').selectOption('minecraft:cobblestone');
  await inspector.locator('.element-editor-form').evaluate(element => { element.scrollTop = element.scrollHeight; });
  await inspector.screenshot({ path: info.outputPath(`edit-block-${language}.png`) });
  const writes = () => page.evaluate(() => (window as any).previewBlockRequests.filter((r: any) => r.messageType === 'preview' && r.request.operation === 'command'));
  const before = (await writes()).length;
  await page.getByTestId('ui-language-select').selectOption(language === 'zh' ? 'en' : 'zh');
  expect((await writes()).length).toBe(before);
  await expect(inspector.getByTestId('field-hardness')).toHaveValue('5');
  await expect(inspector.getByTestId('inspector-delete-btn')).toBeDisabled();
  await inspector.getByTestId('workspace-preview-choice').getByRole('checkbox').uncheck();
  await expect(inspector.getByTestId('inspector-save-btn')).toBeDisabled();
  await expect(inspector.getByTestId('field-hardness')).toHaveValue('5');
  await inspector.getByTestId('workspace-preview-choice').getByRole('checkbox').check();
  await inspector.getByTestId('inspector-save-btn').click();
  await expect.poll(async () => (await writes()).filter((r: any) => r.request.command === 'update_mod_element').length).toBe(1);
  const update = (await writes()).find((r: any) => r.request.command === 'update_mod_element');
  expect(update.request).toMatchObject({ generatorId: `${loader}-26.3`, payload: { changes: expect.arrayContaining([
    { path: '/maxStackSize', value: 16 }, { path: '/rarity', value: 'UNCOMMON' },
    { path: '/hardness', value: 5 }, { path: '/resistance', value: 10 },
    { path: '/rotationMode', value: 5 }, { path: '/texture', value: 'minecraft:cobblestone' }
  ]) } });
  const legacyWrites = await page.evaluate(() => (window as any).previewBlockRequests.filter((r: any) => r.messageType === 'command' && ['create_mod_element', 'update_mod_element'].includes(r.operation)));
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

test('reviewed block recovery is applied through the preview envelope', async ({ page }) => {
  await fixture(page, 'fabric', true);
  await page.getByTestId('ui-language-select').selectOption('en');
  await page.getByTestId('create-element-btn').click();
  const create = page.getByTestId('create-element-modal');
  await create.getByTestId('create-element-name-input').fill('camp_post');
  await create.getByRole('checkbox').check();
  await create.getByTestId('create-element-submit-btn').click();
  await expect(create).toBeHidden();
  const inspector = page.getByTestId('element-inspector');
  await inspector.getByRole('button', { name: 'Preview: adopt current definition', exact: true }).click();
  await inspector.getByRole('button', { name: 'Apply reviewed resolution plan', exact: true }).click();
  await expect(inspector.getByText('Configuration differs from the generated definition', { exact: true })).toBeHidden();
  const requests = await page.evaluate(() => (window as any).previewBlockRequests);
  expect(requests.find((r: any) => r.request?.command === 'apply_workspace_plan').request)
    .toMatchObject({ operation: 'command', generatorId: 'fabric-26.3', payload: { plan: { operations: [{
      operation: 'update_mod_element', payload: { changes: expect.arrayContaining([{ path: '/configurationResolution',
        value: { mode: 'adopt_definition', sourceFingerprint: 'a'.repeat(64) } }]) }
    }] } } });
  expect(requests.filter((r: any) => r.messageType === 'command' && r.operation === 'apply_workspace_plan')).toEqual([]);
});
