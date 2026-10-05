import { expect, test } from '@playwright/test';

test('overview keeps real recent elements and source entries primary while health stays on demand', async ({ page }) => {
  await page.goto('/');
  const overview = page.getByTestId('workbench-main');
  await expect(overview.getByRole('heading', { level: 1 })).toHaveText('Copper Trails');
  await expect(overview.getByTestId('hub-tracks-badge')).toContainText('Fabric');
  await expect(overview.locator('[data-element-id]')).toHaveCount(2);
  const source = page.getByTestId('hub-source-entries').locator('[data-source-path]').first();
  await expect(source).toBeVisible();
  const details = page.getByTestId('workspace-health-panel').locator('details');
  await expect(details).not.toHaveAttribute('open', '');
  await expect(page.getByTestId('workspace-health-diagnostics')).toBeVisible();
  await expect(page.getByTestId('workspace-health-assets')).toBeHidden();
  await details.locator('summary').focus(); await page.keyboard.press('Enter');
  await expect(page.getByTestId('workspace-health-assets')).toBeVisible();
  await page.getByTestId('workspace-health-assets').click();
  await expect(page.getByTestId('asset-browser')).toBeVisible();
  await page.getByTestId('nav-hub').click();
  await source.click(); await expect(page.getByTestId('source-workbench')).toBeVisible();
  await expect(page.getByTestId('source-editor')).not.toHaveValue('');
});

test('empty visual element workspace keeps a compact creation row beside real source access', async ({ page }, testInfo) => {
  await page.addInitScript(() => {
    const modulePath = '/src/mock/mockBridge.ts';
    const ready = import(modulePath).then(({ MockCoreBridge }) => new MockCoreBridge());
    window.copperbenchHost = {
      workspaceId: '11111111-1111-4111-8111-111111111111', onEvent() { return () => {}; },
      async invoke(raw) {
        const core = await ready; const request = JSON.parse(raw);
        const result = request.messageType === 'handshake' ? await core.negotiateHandshake(request)
          : request.messageType === 'command' ? await core.sendCommand(request) : await core.sendQuery(request);
        if (request.operation === 'get_workbench') {
          result.data.elementCounts = { total: 0, valid: 0, draft: 0, invalid: 0, unsupported: 0 };
          result.data.recentElements = [];
        }
        if (request.operation === 'list_mod_elements') result.data.items = [];
        return JSON.stringify(result);
      }
    };
  });
  await page.goto('/');
  const empty = page.getByTestId('hub-elements-empty');
  await expect(empty).toContainText('暂无模组元素');
  expect((await empty.boundingBox())!.height).toBeLessThanOrEqual(60);
  await expect(page.getByTestId('hub-source-entries').locator('[data-source-path]').first()).toBeVisible();
  await page.getByTestId('workbench-main').screenshot({ path: testInfo.outputPath('empty-overview.png'), animations: 'disabled' });
  await empty.getByRole('button', { name: '创建一个' }).click();
  await expect(page.getByRole('dialog')).toBeVisible();
});

test('compile failures and build actions remain usable without expanding health', async ({ page }) => {
  await page.goto('/');
  await page.getByTestId('hub-build-btn').click();
  await expect(page.getByTestId('task-drawer')).toBeVisible();
  await page.getByTestId('task-drawer-close').click();
  await page.getByTestId('scenario-switcher-trigger').click();
  await page.getByTestId('scenario-btn-compile-diagnostic').click();
  await expect(page.getByTestId('global-diagnostics-banner')).toBeVisible();
  await expect(page.getByTestId('task-failure')).toBeVisible();
  await page.getByTestId('open-failed-task-logs-btn').click();
  await expect(page.getByTestId('task-diagnostics')).toBeVisible();
});
