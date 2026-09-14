import { test, expect } from '@playwright/test';

test('native external edits refresh clean fields and preserve dirty drafts', async ({ page }) => {
  await page.addInitScript(() => {
    const listeners = new Set<(raw: string) => void>();
    // Reuse the canonical Core fixture behind the real native JCEF bridge.
    const modulePath = '/src/mock/mockBridge.ts';
    const ready = import(modulePath).then(({ MockCoreBridge }) => {
      const core = new MockCoreBridge();
      core.onEvent((event: unknown) => listeners.forEach((listener) => listener(JSON.stringify(event))));
      return core;
    });
    window.copperbenchHost = {
      workspaceId: '11111111-1111-4111-8111-111111111111',
      onEvent(listener) { listeners.add(listener); return () => listeners.delete(listener); },
      async invoke(raw) {
        const core = await ready;
        const request = JSON.parse(raw);
        const result = request.messageType === 'handshake' ? await core.negotiateHandshake(request)
          : request.messageType === 'command' ? await core.sendCommand(request) : await core.sendQuery(request);
        return JSON.stringify(result);
      }
    };
    (window as any).pythonEdit = async (displayName: string) => {
      const core = await ready;
      const state = core.getState();
      const element = state.elements.find((item: any) => item.name === 'python_test_item');
      return core.sendCommand({
        messageType: 'command', schemaVersion: '1.0', requestId: crypto.randomUUID(),
        workspaceId: state.workbench.workspace.id, expectedRevision: state.workbench.workspace.revision,
        operation: 'update_mod_element',
        payload: { clientMutationId: crypto.randomUUID(), elementId: element.id,
          changes: [{ path: '/displayName', value: displayName }] }
      });
    };
  });
  await page.goto('/');
  await page.getByTestId('nav-elements').click();
  await page.getByTestId('create-element-btn').click();
  await page.getByTestId('create-element-name-input').fill('python_test_item');
  await page.getByTestId('create-element-submit-btn').click();
  const field = page.getByTestId('field-displayName');
  await expect(field).toBeVisible();
  await page.evaluate(() => (window as any).pythonEdit('Python First'));
  await expect(field).toHaveValue('Python First');
  await field.fill('Unsaved desktop draft');
  await page.evaluate(() => (window as any).pythonEdit('Python Second'));
  await expect(page.getByTestId('inspector-external-change')).toBeVisible();
  await expect(field).toHaveValue('Unsaved desktop draft');
  await expect(page.getByTestId('inspector-save-btn')).toBeDisabled();
  await page.getByTestId('inspector-reload-latest').click();
  await expect(field).toHaveValue('Python Second');
  await expect(page.getByTestId('inspector-external-change')).toBeHidden();
});
