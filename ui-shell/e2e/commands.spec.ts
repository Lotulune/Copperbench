import { test, expect } from '@playwright/test';

test.describe('Interactive UI-Core Commands & Mutations', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/');
    await page.waitForSelector('[data-testid="app-shell"]');
    // Ensure on ready scenario
    await page.click('[data-testid="scenario-switcher-trigger"]');
    await page.click('[data-testid="scenario-btn-ready"]');
  });

  test('create_mod_element adds item to list and advances revision', async ({ page }) => {
    // Open Create Modal
    await page.click('[data-testid="empty-primary-action"]');
    await expect(page.locator('[data-testid="create-element-modal"]')).toBeVisible();

    // Fill identifier
    await page.fill('[data-testid="create-element-name-input"]', 'ruby_ore');
    await page.click('[data-testid="create-element-submit-btn"]');

    // Modal closes and new element appears in Elements view
    await expect(page.locator('[data-testid="create-element-modal"]')).not.toBeVisible();
    await expect(page.getByText('Ruby Ore').first()).toBeVisible();
    await expect(page.locator('[data-testid="element-inspector"]')).toBeVisible();
  });

  test('Stage 11 exposes all Java element types and creates a long-tail element', async ({ page }) => {
    await page.click('[data-testid="nav-elements"]');
    await page.click('[data-testid="create-element-btn"]');

    const typeButtons = page.locator('[data-testid="create-element-modal"] button[aria-pressed]');
    await expect(typeButtons).toHaveCount(37);
    await page.locator('[data-testid="create-element-modal"] button').filter({ hasText: '生物实体' }).click();
    await page.fill('[data-testid="create-element-name-input"]', 'copper_guardian');
    await page.click('[data-testid="create-element-submit-btn"]');

    await expect(page.locator('[data-testid="create-element-modal"]')).not.toBeVisible();
    await expect(page.locator('[data-testid="element-inspector"]')).toBeVisible();
    await expect(page.locator('[data-testid="field-displayName"]')).toHaveValue('Copper Guardian');
    await expect(page.locator('[data-testid="field-displayName"]')).toBeEditable();

    await expect(page.locator('[data-testid="inspector-save-btn"]')).toBeDisabled();
    await page.fill('[data-testid="field-displayName"]', 'Copper Guardian Prime');
    await expect(page.locator('[data-testid="element-change-preview"]')).toBeVisible();
    await expect(page.locator('[data-testid="element-change-preview"]')).toContainText('更改影响预览');
    await expect(page.locator('[data-testid="element-change-preview"]')).toContainText('保存后需重新生成当前元素');
    await expect(page.locator('[data-testid="inspector-save-btn"]')).toBeEnabled();
    await page.click('[data-testid="inspector-save-btn"]');
    await expect(page.locator('[data-testid="element-change-preview"]')).not.toBeVisible();
    await expect(page.locator('[data-testid="inspector-save-btn"]')).toBeDisabled();
  });

  test('Stage 12 GUI workbench edits component tree and layout preview', async ({ page }) => {
    await page.click('[data-testid="nav-elements"]');
    await page.click('[data-testid="create-element-btn"]');
    await page.locator('[data-testid="create-element-modal"] button').filter({ hasText: '界面' }).click();
    await page.fill('[data-testid="create-element-name-input"]', 'control_panel');
    await page.click('[data-testid="create-element-submit-btn"]');

    await expect(page.locator('[data-testid="gui-workbench"]')).toBeVisible();
    await expect(page.locator('[data-testid="gui-component-tree"]')).toBeVisible();
    await expect(page.locator('[data-testid="gui-layout-preview"]')).toBeVisible();
    await expect(page.locator('[data-testid="gui-component-0"]')).toBeVisible();
    await expect(page.locator('[data-testid="gui-save-btn"]')).toBeDisabled();

    await page.fill('[data-testid="gui-width"]', '200');
    await expect(page.locator('[data-testid="gui-generation-impact"]')).toContainText('ui_layout');
    await page.selectOption('[data-testid="gui-add-component-type"]', 'button');
    await page.click('[data-testid="gui-add-component-btn"]');
    await expect(page.locator('[data-testid="gui-component-1"]')).toBeVisible();
    await page.fill('[data-testid="gui-component-field-x"]', '420');
    await expect(page.locator('[data-testid="gui-layout-diagnostics"]')).toContainText('超出 MCreator 427×240');
    await expect(page.locator('[data-testid="gui-save-btn"]')).toBeDisabled();
    await page.fill('[data-testid="gui-component-field-x"]', '300');
    await expect(page.locator('[data-testid="gui-layout-diagnostics"]')).not.toBeVisible();

    await page.selectOption('[data-testid="gui-add-component-type"]', 'label');
    await page.click('[data-testid="gui-add-component-btn"]');
    await expect(page.locator('[data-testid="gui-component-2"]')).toContainText('label_3');
    await page.fill('[data-testid="gui-component-field-label-text"]', 'Copper Console');
    await expect(page.locator('[data-testid="gui-preview-component-2"]')).toContainText('Copper Console');

    await page.selectOption('[data-testid="gui-add-component-type"]', 'image');
    await page.click('[data-testid="gui-add-component-btn"]');
    await expect(page.locator('[data-testid="gui-component-3"]')).toBeVisible();

    await page.selectOption('[data-testid="gui-type"]', '1');
    await page.selectOption('[data-testid="gui-add-component-type"]', 'inputslot');
    await page.click('[data-testid="gui-add-component-btn"]');
    await expect(page.locator('[data-testid="gui-component-4"]')).toContainText('inputslot_0');
    await expect(page.locator('[data-testid="gui-component-field-id"]')).toHaveValue('0');

    await page.selectOption('[data-testid="gui-add-component-type"]', 'outputslot');
    await page.click('[data-testid="gui-add-component-btn"]');
    await expect(page.locator('[data-testid="gui-component-5"]')).toContainText('outputslot_1');
    await page.selectOption('[data-testid="gui-type"]', '0');
    await expect(page.locator('[data-testid="gui-layout-diagnostics"]')).toContainText('槽位组件要求 GUI 类型为 With slots');
    await expect(page.locator('[data-testid="gui-save-btn"]')).toBeDisabled();
    await page.selectOption('[data-testid="gui-type"]', '1');
    await expect(page.locator('[data-testid="gui-layout-diagnostics"]')).not.toBeVisible();
    await expect(page.locator('[data-testid="gui-save-btn"]')).toBeEnabled();
    await page.click('[data-testid="gui-save-btn"]');
    await expect(page.locator('[data-testid="gui-save-btn"]')).toBeDisabled();
  });

  test('Stage 12 Dimension uses typed block and biome reference pickers', async ({ page }) => {
    await page.click('[data-testid="nav-elements"]');
    await page.click('[data-testid="create-element-btn"]');
    await page.locator('[data-testid="create-element-modal"] button').filter({ hasText: '维度' }).click();
    await page.fill('[data-testid="create-element-name-input"]', 'copper_realm');
    await page.click('[data-testid="create-element-submit-btn"]');

    await expect(page.locator('[data-testid="element-inspector"]')).toBeVisible();
    await expect(page.locator('[data-testid="field-mainFillerBlock"]')).toHaveValue('Blocks.STONE#0');
    await expect(page.locator('[data-testid="field-fluidBlock"]')).toHaveValue('Blocks.WATER');
    await expect(page.locator('[data-testid="field-portalFrame"]')).toBeDisabled();
    await expect(page.locator('[data-testid="field-condition-portalFrame"]')).toContainText('条件未启用');
    await page.check('[data-testid="field-enablePortal"]');
    await expect(page.locator('[data-testid="field-portalFrame"]')).toBeEnabled();
    await expect(page.locator('[data-testid="field-condition-portalFrame"]')).toContainText('条件已启用');
    await expect(page.locator('[data-testid="validation-alert"]')).toContainText('启用 enablePortal 时必须填写 portalFrame');
    await expect(page.locator('[data-testid="inspector-save-btn"]')).toBeDisabled();
    await page.uncheck('[data-testid="field-enablePortal"]');
    await expect(page.locator('[data-testid="field-portalFrame"]')).toBeDisabled();
    await expect(page.locator('[data-testid="validation-alert"]')).not.toBeVisible();
    await expect(page.locator('[data-testid="field-biomesInDimension"]')).toBeVisible();
    await page.fill('[data-testid="field-biomesInDimension"]', '#is_overworld');
    await page.locator('[data-testid="field-biomesInDimension"]').press('Enter');
    await expect(page.locator('[data-testid="field-biomesInDimension-values"]')).toContainText('#is_overworld');

    await page.fill('[data-testid="field-mainFillerBlock"]', 'Blocks.OBSIDIAN');
    await expect(page.locator('[data-testid="inspector-save-btn"]')).toBeEnabled();
    await page.click('[data-testid="inspector-save-btn"]');
    await expect(page.locator('[data-testid="inspector-save-btn"]')).toBeDisabled();
    await expect(page.locator('[data-testid="field-biomesInDimension-values"]')).toContainText('#is_overworld');
  });

  test('Stage 12 Overlay workbench uses the upstream overlay component subset and grid', async ({ page }) => {
    await page.click('[data-testid="nav-elements"]');
    await page.click('[data-testid="create-element-btn"]');
    await page.locator('[data-testid="create-element-modal"] button').filter({ hasText: '覆盖层' }).click();
    await page.fill('[data-testid="create-element-name-input"]', 'copper_hud');
    await page.click('[data-testid="create-element-submit-btn"]');

    await expect(page.locator('[data-testid="overlay-workbench"]')).toBeVisible();
    await expect(page.locator('[data-testid="overlay-target"]')).toHaveValue('Ingame');
    await expect(page.locator('[data-testid="overlay-priority"]')).toHaveValue('NORMAL');
    await expect(page.locator('[data-testid="overlay-grid-sx"]')).toHaveValue('18');
    await expect(page.locator('[data-testid="overlay-grid-ox"]')).toHaveValue('11');
    await expect(page.locator('[data-testid="overlay-add-component-type"] option[value="button"]')).toHaveCount(0);
    await expect(page.locator('[data-testid="overlay-add-component-type"] option[value="sprite"]')).toHaveCount(1);
    await expect(page.locator('[data-testid="overlay-add-component-type"] option[value="entitymodel"]')).toHaveCount(1);

    await page.selectOption('[data-testid="overlay-add-component-type"]', 'label');
    await page.click('[data-testid="overlay-add-component-btn"]');
    await expect(page.locator('[data-testid="overlay-component-0"]')).toContainText('label_1');
    await page.fill('[data-testid="overlay-component-field-label-text"]', 'Copper HUD');
    await expect(page.locator('[data-testid="overlay-preview-component-0"]')).toContainText('Copper HUD');

    await page.check('[data-testid="overlay-grid-snap"]');
    await page.fill('[data-testid="overlay-grid-sx"]', '20');
    await page.selectOption('[data-testid="overlay-priority"]', 'HIGH');
    await expect(page.locator('[data-testid="overlay-generation-impact"]')).toContainText('ui_overlay');
    await expect(page.locator('[data-testid="overlay-save-btn"]')).toBeEnabled();
    await page.click('[data-testid="overlay-save-btn"]');
    await expect(page.locator('[data-testid="overlay-save-btn"]')).toBeDisabled();
  });

  test('update_mod_element with invalid value triggers validation error', async ({ page }) => {
    await page.click('[data-testid="nav-elements"]');
    await page.locator('[data-element-id="22222222-2222-4222-8222-222222222221"]').first().click();

    // Fill invalid hardness (e.g. 200)
    await page.fill('[data-testid="field-hardness"]', '200');
    await page.click('[data-testid="inspector-save-btn"]');

    // Expect validation alert
    await expect(page.locator('[data-testid="validation-alert"]')).toBeVisible();
    await expect(page.getByText('硬度必须在 0 到 100 之间。').first()).toBeVisible();
  });

  test('build_workspace triggers task progress and completes', async ({ page }) => {
    await page.click('[data-testid="titlebar-build-btn"]');

    // Task drawer should open
    await expect(page.locator('[data-testid="task-drawer"]')).toBeVisible();
    await expect(page.locator('[data-testid="task-log-stream"]')).toBeVisible();
    await expect(page.getByText('Initiating build workflow for Fabric 1.21.1...')).toBeVisible();

    // Wait for completion
    await expect(page.getByText('任务完成').first()).toBeVisible({ timeout: 5000 });
  });

  test('generate_workspace triggers task progress and completes', async ({ page }) => {
    await page.click('[data-testid="titlebar-generate-btn"]');

    await expect(page.locator('[data-testid="task-drawer"]')).toBeVisible();
    await expect(page.locator('[data-testid="task-log-stream"]')).toBeVisible();
    await expect(page.getByText('任务完成').first()).toBeVisible({ timeout: 5000 });
  });

  test('MCP permission status does not fake elevation when the desktop runtime is unavailable', async ({ page }) => {
    const permBtn = page.locator('[data-testid="permission-alert"]');
    await expect(permBtn).toContainText('MCP: WORKSPACE');

    await permBtn.click();
    await expect(permBtn).toContainText('MCP: WORKSPACE');

    await permBtn.click();
    await expect(permBtn).toContainText('MCP: WORKSPACE');
  });
});
