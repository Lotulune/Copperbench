import { test, expect } from '@playwright/test';

for (const locale of ['zh', 'en']) {
  test(`creation preserves input and shows field repair details (${locale})`, async ({ page }, testInfo) => {
    await page.addInitScript(({ locale }) => {
      localStorage.setItem('copperbench.ui.locale', locale);
      const modulePath = '/src/mock/mockBridge.ts';
      const ready = import(modulePath).then(({ MockCoreBridge }) => {
        const core = new MockCoreBridge(); core.loadScenario('ready'); return core;
      });
      window.copperbenchHost = {
        workspaceId: '11111111-1111-4111-8111-111111111111', onEvent() { return () => {}; },
        async invoke(raw) {
          const core = await ready, request = JSON.parse(raw);
          if (request.operation === 'create_mod_element' && request.payload.elementType === 'specialentity') {
            return JSON.stringify({ messageType: 'command_result', schemaVersion: '1.0',
              requestId: request.requestId, workspaceId: request.workspaceId, operation: request.operation,
              status: 'rejected', newRevision: request.expectedRevision, recoveryPointId: null,
              task: null, data: null, conflict: null, denial: null,
              diagnostics: ['entityTexture'].map(field => ({ code: 'FIELD_REQUIRED',
                severity: 'error', path: `/initialValues/${field}`, elementId: null, recoverable: true, actions: [],
                message: { key: 'diagnostic.input_preflight', fallback: '{field}: expected {expectedType}. {repairHint}',
                  args: { field: `/initialValues/${field}`, expectedType: 'string', actualType: 'missing',
                    repairHint: 'Import or select a texture in Assets, then supply its resource name.',
                    missingFields: ['/initialValues/entityTexture'] } } })) });
          }
          return JSON.stringify(request.messageType === 'handshake' ? await core.negotiateHandshake(request)
            : request.messageType === 'command' ? await core.sendCommand(request) : await core.sendQuery(request));
        }
      };
    }, { locale });
    await page.goto('/');
    await page.getByTestId('nav-elements').click();
    await page.getByTestId('create-element-btn').click();
    await page.getByTestId('create-element-name-input').fill('first_boat');
    await page.getByTestId('create-element-type-specialentity').click();
    await page.getByTestId('create-element-submit-btn').click();
    const dialog = page.getByTestId('create-element-modal');
    const error = dialog.getByRole('alert');
    await expect(error).toContainText('/initialValues/entityTexture');
    await expect(error).not.toContainText('/initialValues/itemTexture');
    await expect(error).toContainText('string');
    await expect(error).toContainText('Import or select');
    await expect(page.getByTestId('create-element-name-input')).toHaveValue('first_boat');
    await expect(page.getByTestId('create-element-name-input')).toHaveAttribute('aria-invalid', 'false');
    await page.screenshot({ path: testInfo.outputPath('creation-field-diagnostics.png'), animations: 'disabled' });
    await page.getByTestId('create-element-type-block').click();
    await page.getByTestId('create-element-name-input').fill('first_block');
    await page.getByTestId('create-element-submit-btn').click();
    await expect(dialog).toBeHidden();
    await expect(page.getByTestId('element-inspector')).toBeVisible();
  });
}
