import { expect, test, type Page } from '@playwright/test';

const textureId = `asset:${'2'.repeat(64)}`;
const modelId = `asset:${'1'.repeat(64)}`;

async function installProjection(page: Page, mode: 'ready' | 'stale' | 'unsupported' = 'ready') {
  await page.addInitScript(() => localStorage.setItem('copperbench.ui.locale', 'en'));
  await page.goto('/');
  await page.waitForSelector('[data-testid="app-shell"]');
  await page.evaluate(async mode => {
    // Use the module URL loaded by this page, including Vite's current HMR version.
    const bridgeModule = performance.getEntriesByType('resource').map(entry => entry.name)
      .find(url => new URL(url).pathname === '/src/bridge/index.ts') ?? '/src/bridge/index.ts';
    const { coreBridge } = await import(bridgeModule);
    const original = coreBridge.sendQuery.bind(coreBridge);
    const canvas = document.createElement('canvas'); canvas.width = 16; canvas.height = 16;
    const ctx = canvas.getContext('2d')!;
    ctx.fillStyle = '#d88744'; ctx.fillRect(3, 1, 10, 14); ctx.fillStyle = '#357b67'; ctx.fillRect(5, 3, 6, 10);
    const base64 = canvas.toDataURL('image/png').split(',')[1];
    const texture = { assetId: `asset:${'2'.repeat(64)}`, relativePath: 'assets/coppertrails/textures/item/test.png',
      sha256: '0'.repeat(64), mediaType: 'image/png', size: atob(base64).length, base64, width: 16, height: 16 };
    (window as unknown as { __PREVIEW_REQUESTS__: unknown[] }).__PREVIEW_REQUESTS__ = [];
    coreBridge.sendQuery = async (query: { operation: string; workspaceId: string; requestId: string; payload: { assetId: string; expectedSha256: string } }) => {
      if (query.operation !== 'get_asset_preview') return original(query);
      (window as unknown as { __PREVIEW_REQUESTS__: unknown[] }).__PREVIEW_REQUESTS__.push(query.payload);
      const data = { schemaVersion: '1.0', assetId: query.payload.assetId, relativePath: 'assets/test/models/item/test.json',
        sha256: mode === 'stale' ? '9'.repeat(64) : query.payload.expectedSha256, mediaType: 'application/json', size: 80,
        kind: mode === 'unsupported' ? 'unsupported' : query.payload.assetId === texture.assetId ? 'image' : 'model_json',
        image: texture, document: '{"textures":{"layer0":"coppertrails:item/test"}}',
        textures: mode === 'unsupported' ? [] : [{ ...texture, rawValue: 'coppertrails:item/test', sourcePointer: '/textures/layer0' }],
        linkedTextureCount: 1, reason: 'format_not_supported' };
      return { messageType: 'query_result', schemaVersion: '1.0', requestId: query.requestId, workspaceId: query.workspaceId,
        operation: query.operation, status: 'succeeded', revision: 42, data, diagnostics: [] };
    };
  }, mode);
  await page.getByTestId('nav-assets').click();
}

test('actual image bytes render and orbit controls change the projected camera', async ({ page }, testInfo) => {
  await page.addInitScript(() => Object.defineProperty(crypto, 'randomUUID', { value: undefined, configurable: true }));
  await installProjection(page);
  await page.getByTestId(`asset-card-${textureId}`).click();
  const preview = page.getByTestId('asset-content-preview');
  const img = preview.getByRole('img');
  await expect(img).toHaveAttribute('src', /^data:image\/png;base64,/);
  await expect.poll(() => img.evaluate((element: HTMLImageElement) => element.naturalWidth)).toBe(16);
  await preview.getByRole('button', { name: 'Texture extrusion', exact: true }).click();
  const orbit = page.getByTestId('asset-texture-orbit');
  await expect(orbit).toHaveAttribute('data-preview-state', 'ready');
  const drawing = () => orbit.locator('canvas').evaluate((canvas: HTMLCanvasElement) => canvas.toDataURL());
  const initial = await drawing();
  await orbit.getByRole('button', { name: 'Rotate left', exact: true }).click();
  await expect.poll(drawing).not.toBe(initial);
  await orbit.getByRole('button', { name: 'Zoom in', exact: true }).click();
  await expect(orbit.getByRole('status', { name: 'Zoom level' })).toHaveText('116%');
  await orbit.getByRole('button', { name: 'Reset view', exact: true }).click();
  await expect(orbit.getByRole('status', { name: 'Zoom level' })).toHaveText('100%');
  await expect(preview.getByRole('button', { name: 'Texture extrusion', exact: true })).toHaveAttribute('aria-pressed', 'true');
  expect(await page.evaluate(() => (window as unknown as { __PREVIEW_REQUESTS__: unknown[] }).__PREVIEW_REQUESTS__)).toContainEqual({ assetId: textureId, expectedSha256: '0'.repeat(64) });
  if (testInfo.project.name === 'compact-1366') await page.screenshot({ path: testInfo.outputPath('asset-orbit.png') });
});

test('model JSON displays exact content and only Core-provided resolved textures', async ({ page }, testInfo) => {
  if (testInfo.project.name === 'chromium') await page.setViewportSize({width:1440,height:1000});
  await installProjection(page);
  await page.getByTestId(`asset-card-${modelId}`).click();
  const preview = page.getByTestId('asset-content-preview');
  await expect(preview.locator('pre')).toHaveText('{"textures":{"layer0":"coppertrails:item/test"}}');
  await expect(preview.getByTestId('asset-texture-orbit')).toHaveAttribute('data-preview-state', 'ready');
  await expect(preview.locator('details.asset-model-json')).not.toHaveAttribute('open', '');
  const listBox = await page.locator('.asset-library-files').boundingBox();
  const inspectorBox = await page.getByTestId('asset-details').boundingBox();
  expect(inspectorBox!.width).toBeGreaterThan(listBox!.width * 2);
  await expect(page.getByTestId('asset-checks-disclosure')).not.toHaveAttribute('open', '');
  await expect(page.getByTestId('asset-metadata-disclosure')).not.toHaveAttribute('open', '');
  await page.screenshot({path:testInfo.outputPath('assets-prototype-layout.png')});
  await preview.locator('details.asset-model-json > summary').click();
  await expect(preview.getByRole('img')).toHaveCount(1);
  await expect(preview).toContainText('coppertrails:item/test');
});

test('unsupported model retains Blockbench without fabricated preview geometry', async ({ page }) => {
  await installProjection(page, 'unsupported');
  await page.getByTestId(`asset-card-${modelId}`).click();
  const preview = page.getByTestId('asset-content-preview');
  await expect(preview).toContainText('This model has no inline preview.');
  await expect(preview.locator('canvas, img, pre')).toHaveCount(0);
  await expect(page.getByTestId('asset-details').getByRole('button', { name: /Blockbench/ }).first()).toBeVisible();
});

test('a stale hash is rejected instead of presenting different asset contents', async ({ page }) => {
  await installProjection(page, 'stale');
  await page.getByTestId(`asset-card-${textureId}`).click();
  const preview = page.getByTestId('asset-content-preview');
  await expect(preview.getByRole('alert')).toContainText('The asset changed. Refresh the asset list.');
  await expect(preview.locator('canvas, img, pre')).toHaveCount(0);
  await expect(preview.getByRole('button', { name: 'Retry preview' })).toBeVisible();
});

test('narrow asset layout keeps file search and preview controls reachable', async ({ page }, testInfo) => {
  await page.setViewportSize({ width: 720, height: 900 });
  await installProjection(page);
  await page.getByTestId(`asset-card-${modelId}`).click();
  await expect(page.getByTestId('asset-texture-orbit')).toHaveAttribute('data-preview-state', 'ready');
  await page.getByRole('button', { name: 'Zoom in', exact: true }).click();
  await expect(page.getByRole('status', { name: 'Zoom level' })).toHaveText('116%');
  const files = await page.locator('.asset-library-files').boundingBox();
  const inspector = await page.getByTestId('asset-details').boundingBox();
  expect(inspector!.y).toBeGreaterThanOrEqual(files!.y + files!.height - 1);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  await page.screenshot({ path: testInfo.outputPath('assets-narrow.png') });
});
