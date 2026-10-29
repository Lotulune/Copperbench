import { expect, test, type Page } from '@playwright/test';

async function harness(page: Page, mode = 'normal') {
  await page.goto('/');
  await page.evaluate(async mode => {
    const path = '/e2e/fixtures/DialogHarness.tsx';
    (await import(path)).mountDialogHarness(mode);
  }, mode);
  await page.getByTestId('fixture-launch').click();
}

test('K01–K05, K08: real React traps both directions, includes summary and restores focus', async ({ page }) => {
  await harness(page, 'container');
  const dialog = page.getByRole('dialog', { name: 'Parent fixture', exact: true });
  await expect(dialog).toBeFocused();
  await page.keyboard.press('Shift+Tab');
  await expect(dialog.locator('summary')).toBeFocused();
  await page.keyboard.press('Tab');
  await expect(dialog.getByLabel('Fixture input')).toBeFocused();
  await page.keyboard.press('Shift+Tab');
  await expect(dialog.locator('summary')).toBeFocused();
  await page.getByRole('button', { name: 'Close parent', exact: true }).focus();
  await page.keyboard.press('Tab');
  await expect(dialog.locator('summary')).toBeFocused();
  expect(await page.getByTestId('fixture-background').evaluate(element => Boolean(element.closest('[inert]')))).toBe(true);
  await page.keyboard.press('Escape');
  await expect(dialog).toHaveCount(0);
  await expect(page.getByTestId('fixture-launch')).toBeFocused();
  expect(await page.getByTestId('fixture-background').evaluate(element => Boolean(element.closest('[inert]')))).toBe(false);
});

test('K06: a blocking dialog owns Escape without dismissing', async ({ page }) => {
  await harness(page, 'blocking');
  await page.keyboard.press('Escape');
  await expect(page.getByRole('dialog', { name: 'Parent fixture', exact: true })).toBeVisible();
});

for (const mode of ['normal', 'simultaneous']) test(`K07: Escape closes only the top layer (${mode})`, async ({ page }) => {
  await harness(page, mode);
  if (mode === 'normal') await page.getByRole('button', { name: 'Open child', exact: true }).click();
  await expect(page.getByRole('dialog', { name: 'Child fixture' })).toBeVisible();
  await page.keyboard.press('Escape');
  await expect(page.getByRole('dialog', { name: 'Child fixture' })).toHaveCount(0);
  await expect(page.getByRole('dialog', { name: 'Parent fixture' })).toBeVisible();
  await page.keyboard.press('Escape');
  await expect(page.getByRole('dialog', { name: 'Parent fixture' })).toHaveCount(0);
});

test('K09: descendant autofocus survives StrictMode and an unmounted invoker has a fallback', async ({ page }) => {
  await harness(page, 'autofocus');
  await expect(page.getByRole('textbox', { name: 'Fixture input' })).toBeFocused();
  await page.keyboard.press('Escape');
  await page.reload();
  await harness(page, 'removed');
  await page.keyboard.press('Escape');
  await expect(page.getByTestId('dialog-fixture')).toBeFocused();
});

test('create dialog keeps autofocus and focuses the invalid identifier', async ({ page }) => {
  await page.goto('/');
  await page.getByTestId('empty-primary-action').click();
  await expect(page.getByTestId('create-element-name-input')).toBeFocused();
  await page.getByTestId('create-element-submit-btn').click();
  await expect(page.getByTestId('create-element-name-input')).toHaveAttribute('aria-invalid', 'true');
  await expect(page.getByTestId('create-element-name-input')).toBeFocused();
});

for (const reopen of [true, false]) test(`late creation preserves newer input and reports its own result (reopen=${reopen})`, async ({ page }) => {
  await page.goto('/');
  await page.evaluate(async () => {
    const path = '/src/bridge/index.ts'; const { coreBridge } = await import(path);
    const send = coreBridge.sendCommand.bind(coreBridge);
    coreBridge.sendCommand = async (command: any) => {
      if (command.operation === 'create_mod_element') await new Promise<void>(resolve => { (window as any).releaseAuditCreation = resolve; });
      return send(command);
    };
  });
  await page.getByTestId('empty-primary-action').click();
  await page.getByTestId('create-element-name-input').fill('audit_alpha');
  await page.getByTestId('create-element-submit-btn').click();
  await expect(page.getByTestId('create-element-submit-btn')).toBeDisabled();
  if (reopen) {
    await page.keyboard.press('Escape');
    await page.getByTestId('empty-primary-action').click();
    await expect(page.getByTestId('create-element-submit-btn')).toBeDisabled();
  }
  await page.getByTestId('create-element-name-input').fill('Invalid B');
  await page.getByTestId('create-element-submit-btn').click();
  const error = await page.locator('#create-element-error').innerText();
  await page.evaluate(() => (window as any).releaseAuditCreation());
  await expect(page.getByTestId('feedback-notice')).toContainText('audit_alpha');
  await expect(page.getByTestId('create-element-name-input')).toHaveValue('Invalid B');
  await expect(page.locator('#create-element-error')).toHaveText(error);
  await page.keyboard.press('Escape');
  await expect(page.getByTestId('feedback-notice')).toBeVisible();
});

test('type catalog supports keyboard search, purpose text and unavailable reasons', async ({ page }) => {
  await page.addInitScript(() => localStorage.setItem('copperbench.ui.locale', 'en'));
  await page.goto('/'); await page.getByTestId('empty-primary-action').click();
  await page.getByTestId('create-type-search').fill('villager profession');
  await expect(page.getByTestId('create-element-type-villagerprofession')).toBeVisible();
  await page.getByTestId('create-element-type-villagerprofession').focus();
  await page.keyboard.press('Space');
  await expect(page.getByTestId('create-element-type-villagerprofession')).toHaveAttribute('aria-pressed', 'true');
  await expect(page.getByTestId('create-element-type-villagerprofession')).toContainText('workstation');
  await page.evaluate(async () => {
    const path = '/src/bridge/index.ts'; const { coreBridge } = await import(path);
    coreBridge.getState().workbench.capabilities = [{ id: 'mod_elements.create', availability: 'unavailable', message: { key: 'fixture.unavailable', fallback: 'Fixture read-only workspace' }, reasonCode: 'READ_ONLY', affectedPaths: [] }];
    coreBridge.notifyState();
  });
  await expect(page.getByTestId('create-element-submit-btn')).toBeDisabled();
  await expect(page.getByTestId('create-element-modal')).toContainText('Fixture read-only workspace');
});
