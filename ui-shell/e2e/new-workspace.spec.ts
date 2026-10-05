import { test, expect } from '@playwright/test';

test.describe('New Workspace (product shell native flow)', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/');
    await page.waitForSelector('[data-testid="app-shell"]');
  });

  test('renders the four-track mod catalog and the standalone resource-pack generator', async ({ page }) => {
    await page.click('[data-testid="nav-new-workspace"]');
    await expect(page.locator('[data-testid="new-workspace-view"]')).toBeVisible();

    // Four track groups with two loaders each
    await expect(page.locator('[data-testid="generator-track-latest_stable"]')).toBeVisible();
    await expect(page.locator('[data-testid="generator-track-previous_stable"]')).toBeVisible();
    await expect(page.locator('[data-testid="generator-track-minecraft_1_21_1"]')).toBeVisible();
    await expect(page.locator('[data-testid="generator-track-minecraft_1_20_1"]')).toBeVisible();
    await expect(page.locator('[data-testid="generator-option-fabric-26.2"]')).toBeVisible();
    await expect(page.locator('[data-testid="generator-option-neoforge-26.2"]')).toBeVisible();
    await expect(page.locator('[data-testid="generator-option-fabric-1.21.1"]')).toBeVisible();
    await expect(page.locator('[data-testid="generator-option-neoforge-1.20.1"]')).toBeVisible();
    await expect(page.locator('[data-testid="generator-track-resource_pack"]')).toBeVisible();
    await expect(page.locator('[data-testid="generator-option-resourcepack-1.21.1"]')).toBeVisible();

    // Default selection follows the first available generator in the Core catalog.
    await expect(page.locator('[data-testid="selected-generator-info"]')).toContainText('fabric-26.2');

    // Suggested workspace folders root is surfaced from the catalog
    await expect(page.getByText('MCreatorWorkspaces').first()).toBeVisible();
  });

  test('switching generators updates the selection info', async ({ page }) => {
    await page.click('[data-testid="nav-new-workspace"]');
    await page.click('[data-testid="generator-option-neoforge-26.2"]');
    await expect(page.locator('[data-testid="selected-generator-info"]')).toContainText('neoforge-26.2');
  });

  test('desktop columns share a bottom edge without stretching form controls', async ({ page }, testInfo) => {
    await page.click('[data-testid="nav-new-workspace"]');
    await expect(page.getByTestId('generator-option-fabric-26.2')).toBeVisible();

    for (const viewport of [{ width: 1382, height: 956 }, { width: 1366, height: 768 }]) {
      await page.setViewportSize(viewport);
      // Read both cards in one frame while their shared entrance animation runs.
      const cards = await page.locator('.new-workspace-card').evaluateAll(nodes =>
        nodes.map(node => node.getBoundingClientRect().toJSON()));
      expect(cards).toHaveLength(2);
      const [catalog, information] = cards;
      expect(information!.x).toBeGreaterThan(catalog!.x + catalog!.width);
      expect(Math.abs(catalog!.y + catalog!.height - information!.y - information!.height)).toBeLessThan(1);
      expect((await page.getByTestId('new-workspace-mod-name-input').boundingBox())!.height).toBeLessThan(40);
      await page.screenshot({ path: testInfo.outputPath(`new-workspace-${viewport.width}.png`), animations: 'disabled' });
    }

    await page.getByTestId('generator-option-resourcepack-1.21.1').click();
    await expect(page.getByTestId('new-workspace-package-input')).toHaveCount(0);
    const cardHeights = await page.locator('.new-workspace-card').evaluateAll(cards => cards.map(card => card.getBoundingClientRect().height));
    expect(Math.abs(cardHeights[0] - cardHeights[1])).toBeLessThan(1);
  });

  test('narrow columns stack and keep path hints and validation inside the form', async ({ page }, testInfo) => {
    await page.click('[data-testid="nav-new-workspace"]');
    await expect(page.getByTestId('generator-option-fabric-26.2')).toBeVisible();

    for (const width of [720, 520]) {
      await page.setViewportSize({ width, height: 900 });
      const cards = await page.locator('.new-workspace-card').evaluateAll(nodes =>
        nodes.map(node => node.getBoundingClientRect().toJSON()));
      expect(cards).toHaveLength(2);
      const [catalog, information] = cards;
      expect(information!.y).toBeGreaterThan(catalog!.y + catalog!.height);
      expect(Math.abs(information!.x - catalog!.x)).toBeLessThan(1);
      await page.getByTestId('new-workspace-mod-name-input').fill('Copper Trails');
      await page.getByTestId('new-workspace-mod-id-input').fill('1nv@lid');
      await page.getByTestId('new-workspace-folder-input').fill('C:\\Users\\example\\MCreatorWorkspaces\\demo');
      await page.getByTestId('confirm-create-workspace-checkbox').check();
      await page.getByTestId('create-workspace-submit-btn').click();
      await expect(page.locator('#new-workspace-mod-id-error')).toContainText('模组 ID 必须为');
      await page.getByTestId('new-workspace-info').scrollIntoViewIfNeeded();
      const overflow = await page.getByTestId('new-workspace-view').evaluate(form => form.scrollWidth - form.clientWidth);
      expect(overflow).toBeLessThanOrEqual(1);
      const card = await page.getByTestId('new-workspace-info').boundingBox();
      for (const selector of ['#new-workspace-folder-help', '#new-workspace-mod-id-error']) {
        const message = await page.locator(selector).boundingBox();
        expect(message!.x).toBeGreaterThanOrEqual(card!.x);
        expect(message!.x + message!.width).toBeLessThanOrEqual(card!.x + card!.width);
      }
      await page.screenshot({ path: testInfo.outputPath(`new-workspace-${width}.png`), animations: 'disabled' });
    }
  });

  test('resource-pack selection uses pack terminology and does not require a Java package', async ({ page }) => {
    await page.click('[data-testid="nav-new-workspace"]');
    await page.click('[data-testid="generator-option-resourcepack-1.21.1"]');
    await expect(page.locator('[data-testid="selected-generator-info"]')).toContainText('resourcepack-1.21.1');
    await expect(page.getByText('资源包名称')).toBeVisible();
    await expect(page.getByText('资源包 ID（命名空间）')).toBeVisible();
    await expect(page.locator('[data-testid="new-workspace-package-input"]')).toHaveCount(0);
  });

  test('mod id drives package autofill and the suggested folder path', async ({ page }) => {
    await page.click('[data-testid="nav-new-workspace"]');
    await page.fill('[data-testid="new-workspace-mod-id-input"]', 'copper_trails');
    await expect(page.locator('[data-testid="new-workspace-package-input"]')).toHaveValue(
      'net.mcreator.copper_trails'
    );
    // Suggested folder hint follows the mod id
    await expect(page.getByText(/copper_trails/).first()).toBeVisible();
  });

  test('submit is gated behind the explicit approval checkbox and shows diagnostics on invalid input', async ({ page }) => {
    await page.click('[data-testid="nav-new-workspace"]');

    const submitBtn = page.locator('[data-testid="create-workspace-submit-btn"]');
    await expect(submitBtn).toBeDisabled();

    // Fill the form
    await page.fill('[data-testid="new-workspace-mod-name-input"]', 'Copper Trails');
    await page.fill('[data-testid="new-workspace-mod-id-input"]', 'copper_trails');
    await page.fill('[data-testid="new-workspace-folder-input"]', 'C:\\Users\\example\\MCreatorWorkspaces\\copper_trails');

    // Still disabled without approval
    await expect(submitBtn).toBeDisabled();

    await page.check('[data-testid="confirm-create-workspace-checkbox"]');
    await expect(submitBtn).toBeEnabled();
    await submitBtn.click();

    // The mock bridge commits and surfaces the created workspace banner
    await expect(page.locator('[data-testid="workspace-created-banner"]')).toBeVisible();
    await expect(page.locator('[data-testid="workspace-created-banner"]')).toContainText(
      'copper_trails.mcreator'
    );
  });

  test('invalid mod id surfaces the typed MOD_ID_INVALID diagnostic from the core', async ({ page }) => {
    await page.click('[data-testid="nav-new-workspace"]');
    await page.fill('[data-testid="new-workspace-mod-name-input"]', 'Copper Trails');
    await page.fill('[data-testid="new-workspace-mod-id-input"]', '1nv@lid');
    await page.fill('[data-testid="new-workspace-folder-input"]', 'C:\\Users\\example\\MCreatorWorkspaces\\demo');
    await page.check('[data-testid="confirm-create-workspace-checkbox"]');
    await page.click('[data-testid="create-workspace-submit-btn"]');

    await expect(page.locator('[data-testid="workspace-rejected-banner"]')).toBeVisible();
    await expect(page.locator('[data-testid="workspace-rejected-banner"]')).toContainText('MOD_ID_INVALID');
    await expect(page.locator('[data-testid="workspace-rejected-banner"]')).toBeFocused();
    await expect(page.locator('[data-testid="new-workspace-mod-id-input"]')).toHaveAttribute('aria-invalid', 'true');
    await expect(page.locator('#new-workspace-mod-id-error')).toContainText('模组 ID 必须为');
    await page.locator('[data-testid="workspace-rejected-banner"] a[href="#new-workspace-mod-id"]').click();
    await expect(page.locator('[data-testid="new-workspace-mod-id-input"]')).toBeFocused();
  });
});
