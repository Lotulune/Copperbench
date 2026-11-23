import { expect, test, type Page } from '@playwright/test';

/** Serves a real 16 × 16 PNG for every texture preview and counts the reads. */
async function stubTexturePreviews(page: Page) {
  await page.evaluate(async () => {
    const bridgeModule = performance.getEntriesByType('resource').map(entry => entry.name)
      .find(url => new URL(url).pathname === '/src/bridge/index.ts') ?? '/src/bridge/index.ts';
    const { coreBridge } = await import(bridgeModule);
    const original = coreBridge.sendQuery.bind(coreBridge);
    const canvas = document.createElement('canvas'); canvas.width = 16; canvas.height = 16;
    const context = canvas.getContext('2d')!;
    context.fillStyle = '#d88744'; context.fillRect(2, 2, 12, 12);
    const base64 = canvas.toDataURL('image/png').split(',')[1];
    const reads: unknown[] = [];
    (window as unknown as { __THUMBNAIL_READS__: unknown[] }).__THUMBNAIL_READS__ = reads;
    coreBridge.sendQuery = async (query: { operation: string; workspaceId: string; requestId: string; payload: { assetId: string; expectedSha256: string } }) => {
      if (query.operation !== 'get_asset_preview') return original(query);
      reads.push(query.payload.assetId);
      const image = { assetId: query.payload.assetId, relativePath: 'assets/coppertrails/textures/block/copper_lamp.png',
        sha256: query.payload.expectedSha256, mediaType: 'image/png', size: atob(base64).length, base64, width: 16, height: 16 };
      return { messageType: 'query_result', schemaVersion: '1.0', requestId: query.requestId, workspaceId: query.workspaceId,
        operation: query.operation, status: 'succeeded', revision: 42, diagnostics: [],
        data: { schemaVersion: '1.0', assetId: query.payload.assetId, relativePath: image.relativePath, sha256: image.sha256,
          mediaType: 'image/png', size: image.size, kind: 'image', image, textures: [], linkedTextureCount: 0 } };
    };
  });
}

test.describe('Resource fields pick workspace textures by thumbnail', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/');
    await page.waitForSelector('[data-testid="app-shell"]');
    await page.click('[data-testid="scenario-switcher-trigger"]');
    await page.click('[data-testid="scenario-btn-ready"]');
    await stubTexturePreviews(page);
    await page.click('[data-testid="nav-elements"]');
    await page.click('[data-testid="create-element-btn"]');
    await page.click('[data-testid="create-element-type-plant"]');
    await page.fill('[data-testid="create-element-name-input"]', 'copper_double_plant');
    await page.click('[data-testid="create-element-submit-btn"]');
    await page.selectOption('[data-testid="field-plantType"]', 'double');
  });

  test('textures of the field type show thumbnails, filter by search and store the MCreator name', async ({ page }, testInfo) => {
    const browse = page.getByTestId('field-textureBottom-browse');
    await expect(page.getByTestId('field-textureBottom')).toHaveAttribute('aria-describedby', /help/);
    await expect(browse).toHaveAttribute('aria-expanded', 'false');
    await browse.click();
    const panel = page.getByTestId('field-textureBottom-panel');
    await expect(panel).toBeVisible();
    await expect(page.getByTestId('field-textureBottom-search')).toBeFocused();

    // Only block textures belong to a block texture field; the GUI texture is not offered.
    const options = panel.getByTestId('field-textureBottom-option');
    await expect(options).toHaveCount(1);
    const thumbnail = options.first().locator('img');
    await expect(thumbnail).toHaveAttribute('src', /^data:image\/png;base64,/);
    await expect.poll(() => thumbnail.evaluate((image: HTMLImageElement) => image.naturalWidth)).toBe(16);
    await panel.screenshot({ path: testInfo.outputPath('resource-picker-panel.png'), animations: 'disabled' });

    await page.getByTestId('field-textureBottom-search').fill('obsolete');
    await expect(options).toHaveCount(0);
    await expect(panel.getByRole('status')).toBeVisible();
    await page.getByTestId('field-textureBottom-search').fill('lamp');
    await options.first().click();

    await expect(panel).toHaveCount(0);
    await expect(browse).toBeFocused();
    await expect(page.getByTestId('field-textureBottom')).toHaveValue('copper_lamp');
    // The chosen texture is shown beside the field, reusing the cached read.
    await expect(page.locator('.resource-picker-row img').first()).toHaveAttribute('src', /^data:image\/png;base64,/);
    const reads = await page.evaluate(() => (window as unknown as { __THUMBNAIL_READS__: unknown[] }).__THUMBNAIL_READS__.length);
    expect(reads).toBe(1);
  });

  test('Escape closes the panel and a typed legacy .png name still matches its texture', async ({ page }) => {
    await page.getByTestId('field-textureBottom-browse').click();
    await page.keyboard.press('Escape');
    await expect(page.getByTestId('field-textureBottom-panel')).toHaveCount(0);
    await expect(page.getByTestId('field-textureBottom-browse')).toBeFocused();

    await page.getByTestId('field-textureBottom').fill('copper_lamp.png');
    await expect(page.locator('.resource-picker-help')).toContainText('assets/coppertrails/textures/block/copper_lamp.png');
    await page.getByTestId('field-textureBottom-browse').click();
    await expect(page.getByTestId('field-textureBottom-option').first()).toHaveAttribute('aria-pressed', 'true');
  });
});
