import { test, expect } from '@playwright/test';

async function focusInside(page: import('@playwright/test').Page, selector: string) {
  return page.evaluate((sel) => {
    const active = document.activeElement;
    return !!active && !!active.closest(sel);
  }, selector);
}

test.describe('Accessibility baseline (NFR-UI-08)', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/');
    await page.waitForSelector('[data-testid="app-shell"]');
  });

  test('create modal moves focus in, traps Tab and closes on Escape', async ({ page }) => {
    const trigger = page.locator('[data-testid="empty-primary-action"]');
    await trigger.focus();
    await trigger.click();
    const modal = page.locator('[data-testid="create-element-modal"]');
    await expect(modal).toBeVisible();

    // Focus is moved into the dialog on open
    expect(await focusInside(page, '[data-testid="create-element-modal"]')).toBe(true);

    // Tab cycling stays inside the dialog (focus trap)
    await page.keyboard.press('Tab');
    await page.keyboard.press('Shift+Tab');
    expect(await focusInside(page, '[data-testid="create-element-modal"]')).toBe(true);

    // Escape closes and focus handling does not leave the page stuck
    await page.keyboard.press('Escape');
    await expect(modal).not.toBeVisible();
    await expect(trigger).toBeFocused();
  });

  test('revision conflict dialog receives focus when it opens', async ({ page }) => {
    await page.click('[data-testid="scenario-switcher-trigger"]');
    await page.click('[data-testid="scenario-btn-revision-conflict"]');

    const dialog = page.locator('[data-testid="revision-conflict-dialog"]');
    await expect(dialog).toBeVisible();

    // Focus lands inside the arbitration dialog (dialog card or the
    // expectedUi focus target), never behind the overlay.
    expect(await focusInside(page, '[data-testid="revision-conflict-dialog"]')).toBe(true);

    await page.click('[data-testid="conflict-refresh-btn"]');
    await expect(dialog).not.toBeVisible();
  });

  test('scenario announcements are exposed via a polite live region', async ({ page }) => {
    await page.click('[data-testid="scenario-switcher-trigger"]');
    await page.click('[data-testid="scenario-btn-offline"]');

    const announcer = page.locator('[data-testid="global-announcer"]');
    await expect(announcer).toHaveAttribute('aria-live', 'polite');
    await expect(announcer).toContainText('network_offline');
  });

  test('task logs and diagnostics remain selectable assistive text', async ({ page }) => {
    await page.locator('[data-testid="titlebar-build-btn"]').click();
    const log = page.locator('[data-testid="task-log-stream"]');
    await expect(log).toBeVisible();
    await expect(log).toHaveAttribute('role', 'log');
    await expect(log).toHaveCSS('user-select', 'text');

    await page.locator('[data-testid="task-drawer-close"]').click();
    await page.click('[data-testid="scenario-switcher-trigger"]');
    await page.click('[data-testid="scenario-btn-permission-denied"]');
    const diagnostics = page.locator('[data-testid="global-diagnostics-banner"]');
    await expect(diagnostics).toBeVisible();
    await expect(diagnostics).toHaveCSS('user-select', 'text');
  });

  test('bridge recovery view is blocking and not dismissible via Escape', async ({ page }) => {
    await page.click('[data-testid="scenario-switcher-trigger"]');
    await page.click('[data-testid="scenario-btn-bridge-recovery"]');

    const recoveryView = page.locator('[data-testid="recovery-view"]');
    await expect(recoveryView).toBeVisible();

    // Verify facts displayed: last committed revision & uncommitted loss notice
    await expect(recoveryView).toContainText('最后提交修订');
    await expect(recoveryView).toContainText('未提交');

    // Repeated Escape presses must never dismiss the blocking recovery view
    await page.keyboard.press('Escape');
    await page.keyboard.press('Escape');
    await expect(recoveryView).toBeVisible();

    // Only the explicit reconcile action resolves it
    await page.click('[data-testid="recovery-reconcile-btn"]');
    await expect(recoveryView).not.toBeVisible();
  });

  test('protected-operation dialog moves focus in, traps Tab and closes on Escape', async ({ page }) => {
    await page.click('[data-testid="scenario-switcher-trigger"]');
    await page.click('[data-testid="scenario-btn-approval-required"]');
    await page.click('[data-testid="nav-ai"]');
    await page.click('[data-testid="review-approval"]');

    const dialog = page.locator('[data-testid="approval-dialog"]');
    await expect(dialog).toBeVisible();
    expect(await focusInside(page, '[data-testid="approval-dialog"]')).toBe(true);

    await page.keyboard.press('Tab');
    expect(await focusInside(page, '[data-testid="approval-dialog"]')).toBe(true);
    await page.keyboard.press('Escape');
    await expect(dialog).not.toBeVisible();
  });

  test('blocking schema dialog keeps focus contained until explicit recovery', async ({ page }) => {
    await page.click('[data-testid="scenario-switcher-trigger"]');
    await page.click('[data-testid="scenario-btn-schema-incompatible"]');

    const overlay = page.locator('[data-testid="schema-incompatible"]');
    const dialog = overlay.locator('[role="dialog"]');
    await expect(dialog).toBeVisible();
    expect(await focusInside(page, '[data-testid="schema-incompatible"]')).toBe(true);

    await page.keyboard.press('Escape');
    await expect(dialog).toBeVisible();
    expect(await focusInside(page, '[data-testid="schema-incompatible"]')).toBe(true);

    await dialog.getByRole('button', { name: /重置为兼容协议/ }).click();
    await expect(overlay).not.toBeVisible();
  });

  test('resource-pack batch dialog supports focus trapping and Escape recovery', async ({ page }) => {
    await page.click('[data-testid="nav-tracks"]');
    await page.click('[data-testid="tab-publish-batches"]');
    await page.click('[data-testid="new-batch-btn"]');

    const overlay = page.locator('[data-testid="new-batch-modal"]');
    const dialog = overlay.locator('[role="dialog"]');
    await expect(dialog).toBeVisible();
    expect(await focusInside(page, '[data-testid="new-batch-modal"]')).toBe(true);

    await page.keyboard.press('Tab');
    expect(await focusInside(page, '[data-testid="new-batch-modal"]')).toBe(true);
    await page.keyboard.press('Escape');
    await expect(overlay).not.toBeVisible();
  });

  test('datagen publish confirmation supports focus trapping and Escape recovery', async ({ page }) => {
    await page.click('[data-testid="scenario-switcher-trigger"]');
    await page.click('[data-testid="scenario-btn-ready"]');
    await page.getByRole('button', { name: '在暂存区运行数据生成' }).click();
    await expect(page.getByText('任务完成').first()).toBeVisible({ timeout: 5000 });
    await page.getByRole('button', { name: '查看暂存差异' }).click();
    await page.locator('[data-testid="datagen-publish-btn"]').click();

    const dialog = page.locator('[data-testid="datagen-publish-dialog"]');
    await expect(dialog).toBeVisible();
    expect(await focusInside(page, '[data-testid="datagen-publish-dialog"]')).toBe(true);

    await page.keyboard.press('Tab');
    expect(await focusInside(page, '[data-testid="datagen-publish-dialog"]')).toBe(true);
    await page.keyboard.press('Escape');
    await expect(dialog).not.toBeVisible();
  });

  test('interactive controls satisfy minimum hit target requirements (>= 32x32px)', async ({ page }) => {
    // Check titlebar interactive controls
    const titlebarButtons = await page.locator('[data-testid="frameless-titlebar"] button').all();
    for (const btn of titlebarButtons) {
      const box = await btn.boundingBox();
      if (box) {
        expect(Math.round(box.width)).toBeGreaterThanOrEqual(32);
        expect(Math.round(box.height)).toBeGreaterThanOrEqual(32);
      }
    }

    // Check nav rail buttons
    const navButtons = await page.locator('[data-testid="nav-rail"] button').all();
    for (const btn of navButtons) {
      const box = await btn.boundingBox();
      if (box) {
        expect(Math.round(box.width)).toBeGreaterThanOrEqual(32);
        expect(Math.round(box.height)).toBeGreaterThanOrEqual(32);
      }
    }

    // Check primary action buttons
    const primaryBtn = page.locator('[data-testid="empty-primary-action"]');
    const primaryBox = await primaryBtn.boundingBox();
    if (primaryBox) {
      expect(Math.round(primaryBox.width)).toBeGreaterThanOrEqual(32);
      expect(Math.round(primaryBox.height)).toBeGreaterThanOrEqual(32);
    }

    // Modal controls use the same minimum target contract. This specifically
    // guards native-JCEF regressions where icon buttons or text inputs can be
    // smaller than their Chromium harness counterparts.
    await primaryBtn.click();
    const modalControls = await page
      .locator('[data-testid="create-element-modal"] button, [data-testid="create-element-modal"] input')
      .all();
    for (const control of modalControls) {
      const box = await control.boundingBox();
      if (box) {
        expect(Math.round(box.width)).toBeGreaterThanOrEqual(32);
        expect(Math.round(box.height)).toBeGreaterThanOrEqual(32);
      }
    }
  });

  test('Procedure exposes keyboard-operable tabs and a readable node and port outline', async ({ page }) => {
    await page.click('[data-testid="nav-elements"]');
    await page.click('[data-testid="create-element-btn"]');
    await page.locator('[data-testid="create-element-modal"]').getByRole('button', { name: '过程', exact: true }).click();
    await page.fill('[data-testid="create-element-name-input"]', 'accessible_flow');
    await page.click('[data-testid="create-element-submit-btn"]');

    const workbench = page.locator('[data-testid="procedure-workbench"]');
    await expect(workbench).toBeVisible();

    const sourceTab = workbench.getByRole('tab', { name: '源码' });
    await sourceTab.focus();
    await page.keyboard.press('End');
    const nodesTab = workbench.getByRole('tab', { name: '节点' });
    await expect(nodesTab).toBeFocused();
    await expect(nodesTab).toHaveAttribute('aria-selected', 'true');

    const outline = page.locator('[data-testid="procedure-node-outline"]');
    await expect(outline).toBeVisible();
    await expect(outline.getByRole('button', { name: /入口触发器.*下一个/ })).toBeVisible();

    await page.getByLabel('搜索 Procedure 节点').fill('数值');
    await page.getByRole('button', { name: /^数值 value/ }).click();
    await page.getByRole('button', { name: /保存/ }).click();
    await expect(page.getByText(/已保存 1 项结构化变更/)).toBeVisible();
    await nodesTab.click();
    await expect(outline.getByRole('button', { name: /数值.*输出/ })).toBeVisible();

    const controls = await workbench.locator('button:visible, input:visible').all();
    for (const control of controls) {
      const box = await control.boundingBox();
      if (!box) continue;
      expect(Math.round(box.width)).toBeGreaterThanOrEqual(32);
      expect(Math.round(box.height)).toBeGreaterThanOrEqual(32);
      await expect(control).not.toHaveAccessibleName('');
    }
  });
});
