import { test, expect, type Page } from '@playwright/test';
import { readFileSync } from 'node:fs';

// Core-generated contract and editor projection; only workspace/element identity is normalized.
const projections = Object.fromEntries(['fabric', 'neoforge'].map(loader => [loader, {
  editor: JSON.parse(readFileSync(new URL(`../../ui-core/fixtures/extensions/preview-functions/${loader}-function-editor.json`, import.meta.url), 'utf8')),
  newEditor: JSON.parse(readFileSync(new URL(`../../ui-core/fixtures/extensions/preview-functions/${loader}-function-new-editor.json`, import.meta.url), 'utf8'))
}]));

async function fixture(page: Page, loader: 'fabric' | 'neoforge') {
  const { editor, newEditor } = projections[loader];
  const contract = JSON.parse(readFileSync(new URL(`../../ui-core/fixtures/extensions/preview-functions/${loader}-function-contract.json`, import.meta.url), 'utf8'));
  const targetId = `${loader}-26.3`;
  await page.addInitScript(({ editor, newEditor, targetId, loader, contract }) => {
    const corePromise = import('/src/mock/mockBridge.ts').then(({ MockCoreBridge }) => {
      const core = new MockCoreBridge(); core.loadScenario('ready');
      const state = core.getState();
      state.workbench!.workspace.generator.id = targetId;
      state.workbench!.workspace.generator.loader = loader;
      state.workbench!.workspace.generator.minecraftVersion = '26.3';
      state.workbench!.workspace.generator.displayName = `${loader === 'fabric' ? 'Fabric' : 'NeoForge'} 26.3`;
      const original = state.elements.find(element => element.type === 'item')!;
      const legacy = { ...original, id: '33333333-3333-4333-8333-333333333318', type: 'item' as const,
        name: 'actual_trail_kit', displayName: 'Legacy Kit', identity: { internalName: 'LegacyKit', registryName: 'actual_trail_kit', source: 'generator_definition' as const } };
      state.elements = [legacy]; state.workbench!.recentElements = [legacy];
      state.workbench!.elementCounts.total = 1;
      return core;
    });
    const requests: any[] = [];
    (window as any).previewFunctionRequests = requests;
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
              reasonCode: '', available: target.selected, accessReasonCode: target.selected ? '' : 'PREVIEW_OPT_IN_REQUIRED', evidence: 'core-recipe-fixture' }));
            target.capabilities.elementTypes = target.capabilities.elementTypes?.map(type => type.elementType === 'function'
              ? { ...type, supported: true, reasonCode: '', fields: contract.fields } : type);
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
  }, { editor, newEditor, targetId, loader, contract });
  await page.goto('/');
  await page.getByTestId('nav-elements').click();
  await expect(page.getByTestId('workspace-preview-choice')).toBeVisible();
}


for (const loader of ['fabric', 'neoforge'] as const) for (const language of ['zh', 'en'])
test(`function preview edits, saves and preserves its draft (${loader}, ${language})`, async ({ page }, info) => {
  await fixture(page, loader);
  await page.getByTestId('ui-language-select').selectOption(language);
  await page.getByTestId('create-element-btn').click();
  const dialog = page.getByTestId('create-element-modal');
  await dialog.getByTestId('create-element-type-function').click();
  await dialog.getByTestId('create-element-name-input').fill('camp_signal');
  await expect(dialog.getByTestId('create-element-submit-btn')).toBeDisabled();
  await dialog.getByRole('checkbox').check();
  await dialog.getByTestId('create-element-submit-btn').click();
  await expect(dialog).toBeHidden();
  const workbench = page.getByTestId('function-workbench');
  const editor = page.getByTestId('function-code-editor');
  await expect(workbench).toBeVisible();
  await expect(editor).toHaveValue('# New Copperbench function\n');
  const body = '# Camp signal 中文\nscoreboard objectives add camp_count dummy\nscoreboard players set camp_probe camp_count 11\n';
  await editor.fill(body);
  const writes = () => page.evaluate(() => (window as any).previewFunctionRequests.filter((r: any) => r.messageType === 'preview' && r.request.operation === 'command'));
  const before = (await writes()).length;
  await page.getByTestId('ui-language-select').selectOption(language === 'zh' ? 'en' : 'zh');
  await expect(editor).toHaveValue(body);
  expect((await writes()).length).toBe(before);
  await page.getByTestId('ui-language-select').selectOption(language);
  await workbench.getByTestId('workspace-preview-choice').getByRole('checkbox').uncheck();
  await expect(page.getByTestId('function-save-btn')).toBeDisabled();
  await expect(editor).toHaveValue(body);
  await workbench.getByTestId('workspace-preview-choice').getByRole('checkbox').check();
  await expect(page.getByTestId('function-save-btn')).toBeEnabled();
  await page.screenshot({ path: info.outputPath(`function-editor-${language}.png`) });
  await page.getByTestId('function-save-btn').click();
  await expect(page.getByTestId('function-dirty-badge')).toBeHidden();
  const update = (await writes()).find((r: any) => r.request.command === 'update_mod_element');
  expect(update.request).toMatchObject({ generatorId: `${loader}-26.3`, payload: { changes: [{ path: '/code', value: body }] } });
  await page.getByTestId('function-tab-tags').click();
  await expect(page.getByTestId('function-add-tag-input')).toBeDisabled();
  await page.getByTestId('function-tab-editor').click();
  await expect(page.getByTestId('function-namespace-input')).not.toBeEditable();
  await page.getByTestId('function-back-btn').click();
  await page.locator('[data-element-id]').filter({ hasText: 'camp_signal' }).first().click();
  await expect(editor).toHaveValue(body);
  await page.getByTestId('compact-run-menu').click();
  await page.getByTestId('titlebar-generate-btn').click();
  await expect.poll(async () => (await writes()).filter((r: any) => r.request.command === 'generate_workspace').length).toBe(1);
});
