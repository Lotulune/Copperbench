import { test, expect, type Page } from '@playwright/test';

async function installAssetHost(page: Page, empty = false) {
  await page.addInitScript(({ empty }) => {
    const modulePath = '/src/mock/mockBridge.ts';
    const ready = import(modulePath).then(({ MockCoreBridge }) => new MockCoreBridge());
    const fixture = { empty, fail: false, pause: false, release: () => {} };
    (window as any).assetFixture = fixture;
    window.copperbenchHost = {
      workspaceId: '11111111-1111-4111-8111-111111111111',
      onEvent() { return () => {}; },
      async invoke(raw) {
        const request = JSON.parse(raw), core = await ready;
        if (request.operation === 'list_assets' && fixture.pause) {
          await new Promise<void>(resolve => { fixture.release = resolve; });
        }
        const result = request.messageType === 'handshake' ? await core.negotiateHandshake(request)
          : request.messageType === 'command' ? await core.sendCommand(request) : await core.sendQuery(request);
        if (request.operation === 'get_workbench' && result.data?.workspace) {
          result.data.workspace.id = window.copperbenchHost!.workspaceId;
        }
        if (request.operation === 'list_assets') {
          if (fixture.fail) { result.status = 'rejected'; result.data = null; }
          else if (fixture.empty) { result.data.assets = []; result.data.references = []; }
        }
        if (request.operation === 'list_blockbench_tasks') { result.status = 'succeeded'; result.data = { tasks: [] }; }
        if (request.operation === 'import_asset' && result.status === 'committed') fixture.empty = false;
        if (request.workspaceId) result.workspaceId = request.workspaceId;
        return JSON.stringify(result);
      }
    };
  }, { empty });
}

test('asset refresh, failure and retry preserve the open modeling draft', async ({ page }) => {
  await installAssetHost(page);
  await page.goto('/');
  await page.getByTestId('nav-assets').click();
  await page.getByTestId(`asset-card-asset:${'1'.repeat(64)}`).click();
  await expect(page.getByTestId('asset-details')).toBeVisible();
  const modeling = page.getByTestId('asset-modeling-disclosure');
  await modeling.locator(':scope > summary').click();
  const panel = page.getByTestId('blockbench-tasks');
  await panel.locator(':scope > summary').click();
  const draft = panel.getByLabel('新模型目标路径');
  await draft.fill('models/blockbench/unfinished.bbmodel');
  await page.evaluate(() => {
    (window as any).assetFixture.pause = true;
    window.dispatchEvent(new Event('focus'));
  });
  await expect(page.getByTestId('asset-browser-loading')).toBeVisible();
  await expect(panel).toHaveCount(1);
  await expect(modeling).toHaveAttribute('open', '');
  await expect(panel).toHaveAttribute('open', '');
  await expect(draft).toBeVisible();
  await expect(draft).toHaveValue('models/blockbench/unfinished.bbmodel');
  await page.evaluate(() => {
    const fixture = (window as any).assetFixture;
    fixture.fail = true; fixture.pause = false; fixture.release();
  });
  await expect(page.getByTestId('asset-browser-error')).toBeVisible();
  await expect(draft).toBeVisible();
  await expect(draft).toHaveValue('models/blockbench/unfinished.bbmodel');
  await page.evaluate(() => { (window as any).assetFixture.fail = false; });
  await page.getByRole('button', { name: '重新读取', exact: true }).click();
  await expect(page.getByTestId('asset-details')).toBeVisible();
  await expect(panel).toHaveAttribute('open', '');
  await expect(draft).toBeVisible();
  await expect(draft).toHaveValue('models/blockbench/unfinished.bbmodel');
});

test('an empty workspace can review and import its first asset without losing the modeling draft', async ({ page }) => {
  await installAssetHost(page, true);
  await page.goto('/');
  await page.getByTestId('nav-assets').click();
  await expect(page.getByTestId('asset-browser-empty')).toBeVisible();
  const modeling = page.getByTestId('asset-modeling-disclosure');
  await modeling.locator(':scope > summary').click();
  const panel = page.getByTestId('blockbench-tasks');
  await panel.locator(':scope > summary').click();
  await panel.getByLabel('新模型目标路径').fill('models/first.bbmodel');
  await page.getByTestId('asset-import-empty').click();
  await expect(page.getByTestId('asset-import-review')).toBeVisible();
  await expect(page.getByTestId('asset-import-preview-summary')).toBeVisible();
  await page.getByTestId('asset-import-commit').click();
  await expect(page.getByTestId('asset-import-review')).not.toBeVisible();
  await expect(page.getByTestId('asset-details')).toBeVisible();
  await expect(modeling).toHaveAttribute('open', '');
  await expect(panel).toHaveAttribute('open', '');
  await expect(panel.getByLabel('新模型目标路径')).toBeVisible();
  await expect(panel.getByLabel('新模型目标路径')).toHaveValue('models/first.bbmodel');
});

test('changing workspace identity resets the modeling draft and expansion', async ({ page }) => {
  await installAssetHost(page);
  await page.goto('/');
  await page.getByTestId('nav-assets').click();
  await page.getByTestId(`asset-card-asset:${'1'.repeat(64)}`).click();
  await expect(page.getByTestId('asset-details')).toBeVisible();
  const modeling = page.getByTestId('asset-modeling-disclosure');
  await modeling.locator(':scope > summary').click();
  const panel = page.getByTestId('blockbench-tasks');
  await panel.locator(':scope > summary').click();
  await panel.getByLabel('新模型目标路径').fill('models/old-workspace.bbmodel');
  await page.evaluate(async () => {
    const workspaceId = '22222222-2222-4222-8222-222222222222';
    (window.copperbenchHost as any).workspaceId = workspaceId;
    const modulePath = '/src/bridge/index.ts';
    const { coreBridge } = await import(modulePath);
    await coreBridge.sendQuery({ messageType: 'query', schemaVersion: '1.0', requestId: crypto.randomUUID(),
      workspaceId, operation: 'get_workbench', payload: {} });
  });
  await expect(modeling).not.toHaveAttribute('open', '');
  await expect(panel).not.toHaveAttribute('open', '');
  await modeling.locator(':scope > summary').click();
  await panel.locator(':scope > summary').click();
  await expect(panel.getByLabel('新模型目标路径')).toHaveValue('models/blockbench/new_model.bbmodel');
});

test.describe('clipboard feedback', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/');
    await page.getByTestId('nav-assets').click();
    await page.getByTestId(`asset-card-asset:${'1'.repeat(64)}`).click();
    await page.getByTestId('asset-metadata-disclosure').locator(':scope > summary').click();
    await expect(page.getByTestId('asset-stable-id')).toBeVisible();
  });

  test('confirms only a completed write and ignores completion after selection changes', async ({ page }) => {
    await page.evaluate(() => Object.defineProperty(navigator, 'clipboard', { configurable: true,
      value: { writeText: (id: string) => new Promise<void>(resolve => {
        (window as any).copiedText = id; (window as any).finishCopy = resolve;
      }) } }));
    const originalId = await page.getByTestId('asset-stable-id').textContent();
    await page.getByRole('button', { name: '复制稳定标识' }).click();
    await expect(page.getByTestId('asset-copy-feedback')).toHaveCount(0);
    expect(await page.evaluate(() => (window as any).copiedText)).toBe(originalId);
    await page.evaluate(() => (window as any).finishCopy());
    await expect(page.getByTestId('asset-copy-feedback')).toHaveText('已复制');
    await page.getByRole('button', { name: '复制稳定标识' }).click();
    await page.getByTestId('asset-category-texture').click();
    await page.getByTestId(`asset-card-asset:${'2'.repeat(64)}`).click();
    await expect(page.getByTestId('asset-stable-id')).toHaveText(`asset:${'2'.repeat(64)}`);
    await page.evaluate(() => (window as any).finishCopy());
    await expect(page.getByTestId('asset-copy-feedback')).toHaveCount(0);
  });

  for (const unavailable of [false, true]) {
    test(unavailable ? 'reports an unavailable clipboard' : 'reports a rejected write and allows retry', async ({ page }) => {
      await page.evaluate(unavailable => Object.defineProperty(navigator, 'clipboard', { configurable: true,
        value: unavailable ? undefined : { writeText: async () => { throw new Error('Permission denied'); } } }), unavailable);
      await page.getByRole('button', { name: '复制稳定标识' }).click();
      await expect(page.getByTestId('asset-copy-feedback')).toHaveText('复制失败，请手动复制。');
      await expect(page.getByTestId('asset-copy-feedback')).toHaveAttribute('role', 'alert');
      await page.evaluate(() => Object.defineProperty(navigator, 'clipboard', { configurable: true,
        value: { writeText: async () => {} } }));
      await page.getByRole('button', { name: '复制稳定标识' }).click();
      await expect(page.getByTestId('asset-copy-feedback')).toHaveText('已复制');
      await expect(page.getByTestId('asset-copy-feedback')).toHaveCount(0, { timeout: 3000 });
    });
  }
});
