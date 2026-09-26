import { test, expect, type Page } from '@playwright/test';

async function openProcedure(page: Page, mode: 'available' | 'unavailable' | 'legacy') {
  await page.addInitScript(({ mode }) => {
    const modulePath = '/src/mock/mockBridge.ts';
    const ready = import(modulePath).then(({ MockCoreBridge }) => {
      const core = new MockCoreBridge(); core.loadScenario('ready'); return core;
    });
    window.copperbenchHost = {
      workspaceId: '11111111-1111-4111-8111-111111111111', onEvent() { return () => {}; },
      async invoke(raw) {
        const core = await ready, request = JSON.parse(raw);
        if (request.operation === 'update_procedure') sessionStorage.setItem('savedProcedureEdits', JSON.stringify(request.payload.edits));
        const result = request.messageType === 'handshake' ? await core.negotiateHandshake(request)
          : request.messageType === 'command' ? await core.sendCommand(request) : await core.sendQuery(request);
        if (request.operation === 'get_procedure_editor') {
          const current = mode === 'available' ? 'player_ticks' : 'legacy_plugin_trigger';
          result.data.sourcePreview = ['// Read-only Procedure IR preview', `trigger ${current}`,
            ...result.data.ir.nodes.map((node: { type: string; id: string }) => `${node.type} ${node.id}`)].join('\n');
          result.data.ir.trigger = current;
          for (const node of result.data.ir.nodes) if (node.type === 'event_trigger') node.fields.trigger = current;
          result.data.triggerCatalog = [
            { id: 'player_ticks', label: { key: 'fixture.player_ticks', fallback: '玩家更新' }, dependencies: [{ name: 'x', type: 'number' }] },
            { id: 'plugin_catalog_trigger', label: { key: 'fixture.plugin_trigger', fallback: '插件事件' }, dependencies: [] }
          ];
          if (mode === 'legacy') delete result.data.triggerCatalog;
        }
        return JSON.stringify(result);
      }
    };
  }, { mode });
  await page.goto('/');
  await page.getByTestId('nav-elements').click();
  await page.getByTestId('create-element-btn').click();
  await page.getByTestId('create-element-modal').getByRole('button', { name: '过程（Procedure）', exact: true }).click();
  await page.getByTestId('create-element-name-input').fill('trigger_catalog_probe');
  await page.getByTestId('create-element-submit-btn').click();
  await expect(page.getByTestId('procedure-workbench')).toBeVisible();
  await expect(page.locator('.procedure-save')).toBeDisabled();
  await expect(page.getByRole('tabpanel', { name: '源码' })).toContainText(`trigger ${mode === 'available' ? 'player_ticks' : 'legacy_plugin_trigger'}`);
}

test('uses backend trigger IDs, preserves the selected value and submits a catalog choice', async ({ page }, testInfo) => {
  await openProcedure(page, 'available');
  const field = page.getByRole('button', { name: '下拉选项: 玩家更新 (player_ticks)', exact: true });
  await expect(field).toBeVisible();
  await expect(page.getByTestId('procedure-trigger-notice')).toHaveCount(0);
  await field.click();
  const items = page.locator('.blocklyMenuItemContent');
  await expect(items).toContainText(['无外部触发器', '玩家更新 (player_ticks)', '插件事件 (plugin_catalog_trigger)']);
  await expect(items).toHaveCount(3);
  await page.screenshot({ path: testInfo.outputPath('backend-trigger-menu.png'), animations: 'disabled' });
  await items.filter({ hasText: 'plugin_catalog_trigger' }).click();
  await page.locator('.procedure-save').click();
  await expect.poll(async () => page.evaluate(() => JSON.parse(sessionStorage.getItem('savedProcedureEdits') ?? '[]')))
    .toContainEqual({ operation: 'set_trigger', trigger: 'plugin_catalog_trigger' });
});

for (const mode of ['unavailable', 'legacy'] as const) {
  test(`${mode} trigger survives unrelated edits without a silent replacement`, async ({ page }, testInfo) => {
    await openProcedure(page, mode);
    await expect(page.getByTestId('procedure-trigger-notice')).toContainText('legacy_plugin_trigger');
    const field = page.getByRole('button', { name: '下拉选项: 当前不可用 (legacy_plugin_trigger)', exact: true });
    await expect(field).toBeVisible();
    await field.click();
    const items = page.locator('.blocklyMenuItemContent');
    await expect(items).toHaveCount(mode === 'legacy' ? 2 : 4);
    await expect(items.filter({ hasText: 'on_block_right_clicked' })).toHaveCount(0);
    await items.filter({ hasText: 'legacy_plugin_trigger' }).click();
    await page.getByLabel('筛选过程节点分类').selectOption('value');
    await page.getByLabel('搜索过程节点').fill('数值');
    await page.getByRole('button', { name: /^数值 值/ }).click();
    await page.locator('.procedure-save').click();
    await expect.poll(async () => page.evaluate(() => sessionStorage.getItem('savedProcedureEdits'))).not.toBeNull();
    const edits = await page.evaluate(() => JSON.parse(sessionStorage.getItem('savedProcedureEdits') ?? '[]'));
    expect(edits.some((edit: { operation: string }) => edit.operation === 'set_trigger')).toBe(false);
    await expect(field).toBeVisible();
    await page.screenshot({ path: testInfo.outputPath(`${mode}-trigger-preserved.png`), animations: 'disabled' });
  });
}

test('repairing an unavailable trigger clears the notice and undo restores the original', async ({ page }) => {
  await openProcedure(page, 'unavailable');
  await page.getByRole('button', { name: '下拉选项: 当前不可用 (legacy_plugin_trigger)', exact: true }).click();
  await page.locator('.blocklyMenuItemContent').filter({ hasText: 'player_ticks' }).click();
  await expect(page.getByTestId('procedure-trigger-notice')).toHaveCount(0);
  await page.getByRole('button', { name: '撤销', exact: true }).click();
  await expect(page.getByTestId('procedure-trigger-notice')).toContainText('legacy_plugin_trigger');
  await expect(page.getByRole('button', { name: '下拉选项: 当前不可用 (legacy_plugin_trigger)', exact: true })).toBeVisible();
});
