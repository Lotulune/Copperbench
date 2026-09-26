import { test, expect, type Page } from '@playwright/test';
import { readFileSync } from 'node:fs';

// Captured from the v34 shipped SDK after the real JCEF diagnostic action blanked the window.
const captured = JSON.parse(readFileSync(new URL('./fixtures/stage17-return-diagnostic.json', import.meta.url), 'utf8'));

async function openCaptured(page: Page, type: string) {
  await page.addInitScript(({ captured, type }) => {
    const modulePath = '/src/mock/mockBridge.ts';
    const ready = import(modulePath).then(({ MockCoreBridge }) => {
      const core = new MockCoreBridge(); core.loadScenario('ready'); return core;
    });
    window.copperbenchHost = {
      workspaceId: '11111111-1111-4111-8111-111111111111', onEvent() { return () => {}; },
      async invoke(raw) {
        const core = await ready, request = JSON.parse(raw);
        if (request.operation === 'update_procedure') sessionStorage.setItem('renderingEdits', JSON.stringify(request.payload.edits));
        const result = request.messageType === 'handshake' ? await core.negotiateHandshake(request)
          : request.messageType === 'command' ? await core.sendCommand(request) : await core.sendQuery(request);
        if (request.operation === 'get_procedure_editor') {
          result.data.ir = structuredClone(captured.ir);
          result.data.ir.nodes.find((node: { type: string }) => node.type === 'return_logic').type = type;
          result.data.sourcePreview = `Captured procedure: ${type} VALUE -> coord_x`;
          result.data.diagnostics = captured.diagnostics;
        }
        return JSON.stringify(result);
      }
    };
  }, { captured, type });
  await page.goto('/');
  await page.getByTestId('nav-elements').click();
  await page.getByTestId('create-element-btn').click();
  await page.getByTestId('create-element-modal').getByRole('button', { name: '过程（Procedure）', exact: true }).click();
  await page.getByTestId('create-element-name-input').fill('rendering_probe');
  await page.getByTestId('create-element-submit-btn').click();
  await expect(page.getByTestId('procedure-workbench')).toBeVisible();
}

for (const type of ['return_logic', 'return_number', 'return_string', 'return_itemstack', 'return_entity']) {
  test(`${type}: opening and unrelated edits preserve even an invalid value connection`, async ({ page }) => {
    const errors: string[] = []; page.on('pageerror', error => errors.push(error.message));
    await openCaptured(page, type);
    await expect(page.getByRole('tabpanel', { name: '源码' })).toContainText(type);
    await expect(page.locator('.procedure-save')).toBeDisabled();
    await page.getByLabel('搜索过程节点').fill('数值');
    await page.getByRole('button', { name: /^数值 值/ }).click();
    await page.locator('.procedure-save').click();
    await expect.poll(() => page.evaluate(() => sessionStorage.getItem('renderingEdits'))).not.toBeNull();
    const edits = await page.evaluate(() => JSON.parse(sessionStorage.getItem('renderingEdits')!));
    expect(edits.some((edit: { operation: string }) => edit.operation === 'add_node')).toBe(true);
    expect(edits.filter((edit: { operation: string }) => ['disconnect', 'delete_node'].includes(edit.operation))).toEqual([]);
    expect(errors).toEqual([]);
  });
}

test('an unavailable renderer retains source and navigation without allowing a partial save', async ({ page }) => {
  const errors: string[] = []; page.on('pageerror', error => errors.push(error.message));
  await openCaptured(page, 'future_backend_return');
  await expect(page.getByTestId('procedure-render-error')).toContainText('future_backend_return');
  await expect(page.getByRole('tabpanel', { name: '源码' })).toContainText('future_backend_return');
  await expect(page.locator('.procedure-save')).toBeDisabled();
  await expect(page.locator('.procedure-node-button').first()).toBeDisabled();
  await page.getByRole('button', { name: '返回元素列表', exact: true }).click();
  await expect(page.getByTestId('procedure-workbench')).toHaveCount(0);
  expect(errors).toEqual([]);
});
