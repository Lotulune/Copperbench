import { expect, test } from '@playwright/test';

const lampTexture = `asset:${'2'.repeat(64)}`;
const obsoletePanel = `asset:${'7'.repeat(64)}`;

test.describe('Asset deletion and cleanup', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/');
    await page.waitForSelector('[data-testid="app-shell"]');
    await page.click('[data-testid="nav-assets"]');
    await expect(page.getByTestId('asset-browser')).toBeVisible();
    await page.waitForFunction(() => document.getAnimations().every(animation => (animation as CSSAnimation).animationName !== 'rise-in' || animation.playState !== 'running'));
  });

  test('cleanup candidates delete without extra confirmation and leave the list', async ({ page }, testInfo) => {
    await page.getByTestId('asset-checks-disclosure').locator('summary').first().click();
    await page.getByTestId('asset-cleanup-candidates').click();
    const dialog = page.getByTestId('asset-delete-review');
    await expect(dialog).toBeVisible();
    await expect(page.getByTestId('asset-delete-files')).toContainText('assets/coppertrails/textures/gui/obsolete_panel.png');
    await expect(page.getByTestId('asset-delete-reference-count')).toHaveText('0');
    await expect(page.getByTestId('asset-delete-confirm')).toHaveCount(0);
    await expect(page.getByTestId('asset-delete-commit')).toBeEnabled();
    await dialog.screenshot({ path: testInfo.outputPath('asset-cleanup-review.png'), animations: 'disabled' });
    await page.getByTestId('asset-delete-commit').click();

    await expect(dialog).toHaveCount(0);
    await expect(page.getByTestId(`asset-card-${obsoletePanel}`)).toHaveCount(0);
    await expect(page.getByText(/已删除 1 个文件|Deleted 1 files/)).toBeVisible();
  });

  test('a referenced texture lists what breaks and needs confirmation before deletion', async ({ page }) => {
    await page.getByTestId(`asset-card-${lampTexture}`).click();
    await page.getByTestId('asset-details').locator('.asset-library-more summary').click();
    await page.getByTestId('asset-delete-button').click();

    const dialog = page.getByTestId('asset-delete-review');
    await expect(page.getByTestId('asset-delete-reference-count')).toHaveText('1');
    await expect(page.getByTestId('asset-delete-references')).toContainText('assets/coppertrails/models/block/copper_lamp.bbmodel');
    await expect(page.getByTestId('asset-delete-unassessed')).toBeVisible();
    const commit = page.getByTestId('asset-delete-commit');
    await expect(commit).toBeDisabled();
    await page.getByTestId('asset-delete-confirm').check();
    await expect(commit).toBeEnabled();
    await commit.click();

    await expect(dialog).toHaveCount(0);
    await expect(page.getByTestId(`asset-card-${lampTexture}`)).toHaveCount(0);
  });

  test('cancelling the review deletes nothing', async ({ page }) => {
    await page.getByTestId(`asset-card-${lampTexture}`).click();
    await page.getByTestId('asset-details').locator('.asset-library-more summary').click();
    await page.getByTestId('asset-delete-button').click();
    await expect(page.getByTestId('asset-delete-reference-count')).toHaveText('1');
    await page.keyboard.press('Escape');
    await expect(page.getByTestId('asset-delete-review')).toHaveCount(0);
    await expect(page.getByTestId(`asset-card-${lampTexture}`)).toBeVisible();
  });
});
