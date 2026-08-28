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
});
