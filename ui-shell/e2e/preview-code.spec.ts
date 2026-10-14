import { test, expect, type Page } from '@playwright/test';
import { readFileSync } from 'node:fs';

// Core-generated contract and editor projection; only workspace/element identity is normalized.
const projections = Object.fromEntries(['fabric', 'neoforge'].map(loader => [loader, {
  editor: JSON.parse(readFileSync(new URL(`../../ui-core/fixtures/extensions/preview-code/${loader}-code-editor.json`, import.meta.url), 'utf8')),
  newEditor: JSON.parse(readFileSync(new URL(`../../ui-core/fixtures/extensions/preview-code/${loader}-code-new-editor.json`, import.meta.url), 'utf8'))
}]));

async function fixture(page: Page, loader: 'fabric' | 'neoforge') {
  const { editor, newEditor } = projections[loader];
  const contract = JSON.parse(readFileSync(new URL(`../../ui-core/fixtures/extensions/preview-code/${loader}-code-contract.json`, import.meta.url), 'utf8'));
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
    (window as any).previewCodeRequests = requests;
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
              reasonCode: '', available: target.selected, accessReasonCode: target.selected ? '' : 'PREVIEW_OPT_IN_REQUIRED', evidence: 'core-code-fixture' }));
            target.capabilities.elementTypes = target.capabilities.elementTypes?.map(type => type.elementType === 'code'
              ? { ...type, supported: true, reasonCode: '', fields: contract.fields } : type);
          }
          if (request.request.operation === 'command') {
            const scoped = request.request;
            const command = { messageType: 'command' as const, schemaVersion: '1.0' as const, requestId: request.requestId,
              workspaceId: request.workspaceId, expectedRevision: scoped.expectedRevision, operation: scoped.command,
              payload: { ...scoped.payload, clientMutationId: request.requestId } };
            const response = (window as any).rejectCodeSave && scoped.command === 'update_mod_element'
              ? { messageType: 'command_result', schemaVersion: '1.0', requestId: request.requestId, workspaceId: request.workspaceId,
                  operation: scoped.command, status: 'rejected', newRevision: scoped.expectedRevision, data: null,
                  diagnostics: [{ code: 'SOURCE_CONTENT_CONFLICT', severity: 'error', path: '/code', recoverable: true, actions: [],
                    message: { key: 'diagnostic.source_content_conflict', fallback: 'The source file changed outside Copperbench after this edit was based on it. Reload the source and retry.', args: {} } }] }
              : await core.sendCommand(command);
            if (scoped.command === 'create_mod_element' && response.data?.element) {
              currentEditor = structuredClone(newEditor);
              currentEditor.element = response.data.element;
            }
            if (scoped.command === 'update_mod_element' && response.status === 'committed' && currentEditor) {
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
test(`manual code preview saves a bundle with the draft's source fingerprint (${loader}, ${language})`, async ({ page }, info) => {
  await fixture(page, loader);
  await page.getByTestId('ui-language-select').selectOption(language);
  await page.getByTestId('create-element-btn').click();
  const dialog = page.getByTestId('create-element-modal');
  await dialog.getByTestId('create-element-type-code').click();
  await dialog.getByTestId('create-element-name-input').fill('manual_code');
  await expect(dialog.getByTestId('create-element-submit-btn')).toBeDisabled();
  await dialog.getByRole('checkbox').check();
  await dialog.getByTestId('create-element-submit-btn').click();
  const inspector = page.getByTestId('element-inspector');
  await expect(inspector).toBeVisible();
  await expect(inspector.getByTestId('field-code')).toHaveValue('');
  const body = ['package net.mcreator.code_contract;', 'public class manual_code { public static int value() { return 41; } }', ''].join(String.fromCharCode(10));
  const files = [{ path: 'parts/Helper.java', code: 'package net.mcreator.code_contract.parts; public class Helper {}' }];
  await inspector.getByTestId('field-code').fill(body);
  await inspector.getByTestId('field-codeFiles').fill(JSON.stringify(files, null, 2));
  await expect(inspector.getByTestId('field-$primary')).toHaveCount(0);
  await expect(inspector.getByTestId('inspector-save-btn')).toBeEnabled();
  await inspector.getByTestId('workspace-preview-choice').getByRole('checkbox').uncheck();
  await expect(inspector.getByTestId('inspector-save-btn')).toBeDisabled();
  await expect(inspector.getByTestId('field-code')).toHaveValue(body);
  await inspector.getByTestId('workspace-preview-choice').getByRole('checkbox').check();
  await inspector.getByTestId('field-code').evaluate(element => element.scrollIntoView({ block: 'center' }));
  await expect(inspector.getByTestId('element-change-preview')).toContainText(language === 'zh' ? '手写源码直接保存' : 'Handwritten sources are saved directly');
  await inspector.screenshot({ path: info.outputPath(`manual-code-${language}.png`) });
  await inspector.getByTestId('inspector-save-btn').click();
  const writes = () => page.evaluate(() => (window as any).previewCodeRequests.filter((r: any) => r.messageType === 'preview' && r.request.command === 'update_mod_element'));
  await expect.poll(async () => (await writes()).length).toBe(1);
  const update = (await writes())[0].request;
  const fingerprint = projections[loader].newEditor.sections.flatMap((s: any) => s.fields).find((f: any) => f.path === '/sourceFingerprints/$primary');
  expect(update).toMatchObject({ generatorId: `${loader}-26.3`, payload: { changes: expect.arrayContaining([
    { path: '/code', value: body }, { path: '/codeFiles', value: files }, { path: fingerprint.path, value: fingerprint.value }
  ]) } });
  await inspector.getByTestId('field-description').fill('Metadata only');
  await inspector.getByTestId('inspector-save-btn').click();
  await expect.poll(async () => (await writes()).length).toBe(2);
  expect((await writes())[1].request.payload.changes).toEqual([{ path: '/description', value: 'Metadata only' }]);
  await expect(inspector.getByTestId('field-code')).toHaveValue(body);
  const count = (await writes()).length;
  await page.getByTestId('ui-language-select').selectOption(language === 'zh' ? 'en' : 'zh');
  expect((await writes()).length).toBe(count);
  await page.getByTestId('compact-run-menu').click();
  await page.getByTestId('titlebar-generate-btn').click();
  await expect.poll(() => page.evaluate(() => (window as any).previewCodeRequests.filter((r: any) => r.request?.command === 'generate_workspace').length)).toBe(1);
});

for (const loader of ['fabric', 'neoforge'] as const)
test(`a stale source rejection retains the manual code draft and does not retry the write (${loader})`, async ({ page }) => {
  await fixture(page, loader);
  await page.getByTestId('ui-language-select').selectOption('en');
  await page.getByTestId('create-element-btn').click();
  const dialog = page.getByTestId('create-element-modal');
  await dialog.getByTestId('create-element-type-code').click();
  await dialog.getByTestId('create-element-name-input').fill('manual_code');
  await dialog.getByRole('checkbox').check();
  await dialog.getByTestId('create-element-submit-btn').click();
  const inspector = page.getByTestId('element-inspector');
  const draft = 'public class manual_code { public static int value() { return 99; } }';
  await inspector.getByTestId('field-code').fill(draft);
  await page.evaluate(() => { (window as any).rejectCodeSave = true; });
  await inspector.getByTestId('inspector-save-btn').click();
  await expect(inspector.getByTestId('validation-alert').getByText(/source file changed outside Copperbench/i)).toBeVisible();
  await expect(inspector.getByTestId('field-code')).toHaveValue(draft);
  const count = () => page.evaluate(() => (window as any).previewCodeRequests.filter((r: any) => r.request?.command === 'update_mod_element').length);
  expect(await count()).toBe(1);
  await page.getByTestId('ui-language-select').selectOption('zh');
  await expect(inspector.getByTestId('field-code')).toHaveValue(draft);
  expect(await count()).toBe(1);
});
