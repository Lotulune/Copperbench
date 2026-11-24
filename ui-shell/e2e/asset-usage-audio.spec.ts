import { expect, test } from '@playwright/test';

const lampTexture = `asset:${'2'.repeat(64)}`;
const obsoletePanel = `asset:${'7'.repeat(64)}`;
const chime = `asset:${'5'.repeat(64)}`;

test.describe('Asset texture use and sound playback', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/');
    await page.waitForSelector('[data-testid="app-shell"]');
    await page.click('[data-testid="nav-assets"]');
    await expect(page.getByTestId('asset-browser')).toBeVisible();
    await page.waitForFunction(() => document.getAnimations().every(animation => (animation as CSSAnimation).animationName !== 'rise-in' || animation.playState !== 'running'));
  });

  test('textures filter by what they are for', async ({ page }) => {
    await expect(page.getByTestId('asset-texture-usages')).toHaveCount(0);
    await page.getByTestId('asset-category-texture').click();
    const usages = page.getByTestId('asset-texture-usages');
    await expect(usages.getByTestId('asset-usage-block')).toBeVisible();
    await expect(usages.getByTestId('asset-usage-gui')).toBeVisible();
    await expect(page.getByTestId(`asset-card-${lampTexture}`)).toContainText('方块');

    await usages.getByTestId('asset-usage-gui').click();
    await expect(page.getByTestId(`asset-card-${obsoletePanel}`)).toBeVisible();
    await expect(page.getByTestId(`asset-card-${lampTexture}`)).toHaveCount(0);

    // Changing the asset type resets the use filter.
    await page.getByTestId('asset-category-all').click();
    await expect(page.getByTestId('asset-texture-usages')).toHaveCount(0);
    await page.getByTestId('asset-category-texture').click();
    await expect(page.getByTestId('asset-usage-all')).toHaveAttribute('aria-pressed', 'true');
  });

  test('a sound plays in the preview', async ({ page }) => {
    await page.evaluate(async () => {
      const bridgeModule = performance.getEntriesByType('resource').map(entry => entry.name)
        .find(url => new URL(url).pathname === '/src/bridge/index.ts') ?? '/src/bridge/index.ts';
      const { coreBridge } = await import(bridgeModule);
      const original = coreBridge.sendQuery.bind(coreBridge);
      coreBridge.sendQuery = async (query: { operation: string; workspaceId: string; requestId: string; payload: { assetId: string; expectedSha256: string } }) => {
        if (query.operation !== 'get_asset_preview') return original(query);
        const audio = { assetId: query.payload.assetId, relativePath: 'assets/coppertrails/sounds/copper_chime.ogg',
          sha256: query.payload.expectedSha256, mediaType: 'audio/ogg', size: 4, base64: 'T2dnUw==' };
        return { messageType: 'query_result', schemaVersion: '1.0', requestId: query.requestId, workspaceId: query.workspaceId,
          operation: query.operation, status: 'succeeded', revision: 42, diagnostics: [],
          data: { schemaVersion: '1.0', ...audio, kind: 'audio', audio, textures: [] } };
      };
    });
    await page.getByTestId(`asset-card-${chime}`).click();
    const player = page.getByTestId('asset-audio-preview');
    await expect(player).toHaveAttribute('src', 'data:audio/ogg;base64,T2dnUw==');
    await expect(player).toHaveAttribute('controls', '');
  });
});
