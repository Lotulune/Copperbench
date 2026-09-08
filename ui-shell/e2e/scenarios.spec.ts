import { test, expect } from '@playwright/test';

test.describe('UI-Core v1.0 Contract Scenarios', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/');
    await page.waitForSelector('[data-testid="app-shell"]');
  });

  test('scenario: compile-diagnostic navigates from compiler failure to the owning element', async ({ page }) => {
    await page.click('[data-testid="scenario-switcher-trigger"]');
    await page.click('[data-testid="scenario-btn-compile-diagnostic"]');

    await expect(page.locator('[data-testid="global-diagnostics-banner"]')).toBeVisible();
    await expect(page.locator('[data-testid="task-failure"]')).toBeVisible();
    await page.click('[data-testid="open-failed-task-logs-btn"]');

    await expect(page.locator('[data-testid="task-diagnostics"]')).toBeVisible();
    await expect(page.locator('[data-testid="task-diagnostic-JAVA_COMPILE_ERROR"]')).toBeVisible();
    await expect(page.locator('[data-testid="task-diag-action-open_generated_source"]')).toBeVisible();
    await page.click('[data-testid="task-diag-action-open_generated_source"]');
    await expect(page.locator('[data-testid="task-source-preview"]')).toBeVisible();
    await expect(page.locator('[data-testid="task-source-preview"]')).toContainText('CopperLampElement.java:42');
    await expect(page.locator('[data-testid="task-source-content"]')).toContainText('CopperLampElement');
    await expect(page.locator('[data-testid="task-diag-action-locate_compile_element"]')).toBeVisible();
    await page.click('[data-testid="task-diag-action-locate_compile_element"]');

    await expect(page.locator('[data-testid="element-inspector"]')).toBeVisible();
    await expect(page.locator('[data-element-id="22222222-2222-4222-8222-222222222221"]').first()).toBeVisible();
    await expect(page.getByText('Copper Lamp').first()).toBeVisible();
  });

  test('scenario: generator-repair previews semantic diff before recovery-protected apply', async ({ page }) => {
    await page.click('[data-testid="scenario-switcher-trigger"]');
    await page.click('[data-testid="scenario-btn-generator-repair"]');

    await expect(page.locator('[data-testid="task-failure"]')).toBeVisible();
    await page.click('[data-testid="open-failed-task-logs-btn"]');
    await expect(page.locator('[data-testid="task-diagnostic-FABRIC_ITEM_STACK_INVALID"]')).toBeVisible();
    await expect(page.locator('[data-testid="task-diag-action-preview_generator_repair"]')).toBeVisible();
    await page.click('[data-testid="task-diag-action-preview_generator_repair"]');

    await expect(page.locator('[data-testid="task-repair-preview"]')).toBeVisible();
    await expect(page.locator('[data-testid="task-repair-summary"]')).toContainText('1');
    await expect(page.locator('[data-testid="task-repair-semantic-diff"]')).toContainText('maxStackSize');
    await expect(page.locator('[data-testid="task-repair-apply"]')).toBeEnabled();
    await page.click('[data-testid="task-repair-apply"]');
    await expect(page.locator('[data-testid="task-repair-preview"]')).toContainText('安全修复已应用');
  });

  test('scenario: ready renders healthy workbench and recent elements', async ({ page }) => {
    // Switch to ready scenario
    await page.click('[data-testid="scenario-switcher-trigger"]');
    await page.click('[data-testid="scenario-btn-ready"]');

    // Verify Titlebar & Workspace pill
    await expect(page.locator('[data-testid="frameless-titlebar"]')).toBeVisible();
    await expect(page.getByText('Copper Trails').first()).toBeVisible();
    await expect(page.getByText('Fabric 1.21.1').first()).toBeVisible();

    // Verify Element counts
    await expect(page.locator('[data-testid="workbench-main"]')).toBeVisible();
    await expect(page.getByText('元素总数')).toBeVisible();
    await expect(page.getByText('Copper Lamp').first()).toBeVisible();
  });

  test('scenario: empty-workspace displays primary call to action', async ({ page }) => {
    await page.click('[data-testid="scenario-switcher-trigger"]');
    await page.click('[data-testid="scenario-btn-empty-workspace"]');

    await expect(page.getByText('New Fabric Mod').first()).toBeVisible();
    await expect(page.locator('[data-testid="empty-primary-action"]')).toBeVisible();
  });

  test('scenario: loading-workbench displays loading state without false positive success', async ({ page }) => {
    await page.click('[data-testid="scenario-switcher-trigger"]');
    await page.click('[data-testid="scenario-btn-loading-workbench"]');

    await expect(page.locator('[data-testid="workbench-loading"]')).toBeVisible();
    await expect(page.getByText('正在加载工作区投影…')).toBeVisible();
  });

  test('scenario: validation-failed shows field diagnostic error', async ({ page }) => {
    await page.click('[data-testid="scenario-switcher-trigger"]');
    await page.click('[data-testid="scenario-btn-validation-failed"]');

    await page.click('[data-testid="nav-elements"]');
    await page.locator('[data-element-id="22222222-2222-4222-8222-222222222221"]').first().click();
    await expect(page.locator('[data-testid="element-inspector"]')).toBeVisible();
    await expect(page.locator('[data-testid="diag-action-open_invalid_field"]')).toBeVisible();
    await page.click('[data-testid="diag-action-open_invalid_field"]');
    await expect(page.locator('[data-field-path="/fields/hardness"]')).toBeFocused();
    await expect(page.locator('[data-testid="validation-alert"]')).toBeVisible();
    await expect(page.locator('[data-testid="validation-alert"]')).toContainText(
      '硬度必须在 0 到 100 之间。'
    );
  });

  test('scenario: revision-conflict triggers arbitration dialog', async ({ page }) => {
    await page.click('[data-testid="scenario-switcher-trigger"]');
    await page.click('[data-testid="scenario-btn-revision-conflict"]');

    await expect(page.locator('[data-testid="revision-conflict-dialog"]')).toBeVisible();
    await expect(page.getByText('版本并发写入冲突')).toBeVisible();
    await expect(page.locator('[data-testid="conflict-refresh-btn"]')).toBeVisible();

    // Click refresh to resolve
    await page.click('[data-testid="conflict-refresh-btn"]');
    await expect(page.locator('[data-testid="revision-conflict-dialog"]')).not.toBeVisible();
  });

  test('scenario: partial-capability preserves NeoForge field with read-only warning', async ({ page }) => {
    await page.click('[data-testid="scenario-switcher-trigger"]');
    await page.click('[data-testid="scenario-btn-partial-capability"]');

    // Open elements view & inspector
    await page.click('[data-testid="nav-elements"]');
    await page.locator('[data-element-id="22222222-2222-4222-8222-222222222221"]').first().click();

    await expect(page.locator('[data-testid="element-inspector"]')).toBeVisible();
    await expect(page.locator('[data-field-path="/loaderExtensions/neoforge/fireSpreadSpeed"]')).toBeVisible();
    await expect(page.getByText('NeoForge 加载器扩展')).toBeVisible();
    await expect(page.getByText('只读保留')).toBeVisible();
  });

  test('scenario: offline marks offline network while core remains connected', async ({ page }) => {
    await page.click('[data-testid="scenario-switcher-trigger"]');
    await page.click('[data-testid="scenario-btn-offline"]');

    await expect(page.locator('[data-testid="offline-status"]')).toContainText('离线模式');
    await expect(page.locator('[data-testid="core-status"]')).toContainText('核心：connected');
  });

  test('scenario: build-running displays progress and log stream', async ({ page }) => {
    await page.click('[data-testid="scenario-switcher-trigger"]');
    await page.click('[data-testid="scenario-btn-build-running"]');

    // Drawer should open or progress pill visible
    await expect(page.locator('[data-testid="status-footer"]')).toBeVisible();
    await expect(page.locator('[data-testid="running-task-pill"]')).toBeVisible();
  });

  test('scenario: bridge-recovery displays JCEF recovery screen', async ({ page }) => {
    await page.click('[data-testid="scenario-switcher-trigger"]');
    await page.click('[data-testid="scenario-btn-bridge-recovery"]');

    await expect(page.locator('[data-testid="recovery-view"]')).toBeVisible();
    await expect(page.getByText('渲染桥接异常恢复')).toBeVisible();
    await expect(page.locator('[data-testid="recovery-reconcile-btn"]')).toBeVisible();

    await page.click('[data-testid="recovery-reconcile-btn"]');
    await expect(page.locator('[data-testid="recovery-view"]')).not.toBeVisible();
  });

  test('scenario: permission-denied explains profile gap and offers elevation', async ({ page }) => {
    await page.click('[data-testid="scenario-switcher-trigger"]');
    await page.click('[data-testid="scenario-btn-permission-denied"]');

    // Top-level diagnostic banner explains current vs required permission
    await expect(page.locator('[data-testid="global-diagnostics-banner"]')).toBeVisible();
    await expect(page.getByText('需要工作区写入权限才能执行构建。').first()).toBeVisible();
    await expect(page.locator('[data-testid="permission-alert"]')).toContainText('MCP: READ_ONLY');

    // Request elevation resolves the denial
    await page.click('[data-testid="diag-action-request_workspace_permission"]');
    await expect(page.locator('[data-testid="global-diagnostics-banner"]')).not.toBeVisible();
    await expect(page.locator('[data-testid="permission-alert"]')).toContainText('MCP: WORKSPACE');
  });

  test('scenario: external-process-exited surfaces failure with log entry point', async ({ page }) => {
    await page.click('[data-testid="scenario-switcher-trigger"]');
    await page.click('[data-testid="scenario-btn-external-process-exited"]');

    await expect(page.locator('[data-testid="task-failure"]')).toBeVisible();
    await expect(page.getByText('RUN_CLIENT 任务失败').first()).toBeVisible();
    await expect(page.getByText('意外退出').first()).toBeVisible();

    // The task-level entry point opens the drawer and the payload taskId keeps the runtime diagnostic attached
    // even though open_logs.target remains the native application-log failureId.
    await page.click('[data-testid="open-failed-task-logs-btn"]');
    await expect(page.locator('[data-testid="task-drawer"]')).toBeVisible();
    await expect(page.getByText('RUN_CLIENT').first()).toBeVisible();
    await expect(page.locator('[data-testid="task-diagnostic-FABRIC_RUN_CLIENT_EXITED"]')).toBeVisible();
    await expect(page.locator('[data-testid="task-diag-action-open_client_logs"]')).toBeVisible();
  });

  test('scenario: procedure-node-diagnostic opens the exact Procedure node', async ({ page }) => {
    await page.click('[data-testid="scenario-switcher-trigger"]');
    await page.click('[data-testid="scenario-btn-procedure-node-diagnostic"]');

    await expect(page.locator('[data-testid="task-failure"]')).toBeVisible();
    await page.click('[data-testid="open-failed-task-logs-btn"]');
    await expect(page.locator('[data-testid="task-diagnostic-PROCEDURE_CALL_TARGET_REQUIRED"]')).toBeVisible();
    await page.click('[data-testid="task-diag-action-open_procedure_node"]');

    await expect(page.locator('[data-testid="procedure-workbench"]')).toBeVisible();
    await expect(page.locator('[data-testid="procedure-selected-location"]')).toContainText('调用 Procedure');
    await expect(page.locator('#procedure-tab-diagnostics')).toHaveAttribute('aria-selected', 'true');
  });

  test('scenario: element-created lists the committed block', async ({ page }) => {
    await page.click('[data-testid="scenario-switcher-trigger"]');
    await page.click('[data-testid="scenario-btn-element-created"]');

    await page.click('[data-testid="nav-elements"]');
    await expect(page.getByText('Signal Lantern').first()).toBeVisible();
  });

  test('scenario: schema-incompatible displays structured handshake error', async ({ page }) => {
    await page.click('[data-testid="scenario-switcher-trigger"]');
    await page.click('[data-testid="scenario-btn-schema-incompatible"]');

    await expect(page.locator('[data-testid="schema-incompatible"]')).toBeVisible();
    await expect(page.getByText('UI-Core 协议不兼容')).toBeVisible();
  });
});
