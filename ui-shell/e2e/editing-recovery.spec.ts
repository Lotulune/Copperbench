import { test, expect, type Page } from '@playwright/test';

async function createElement(page: Page, type: string, name: string) {
  await page.getByTestId('nav-elements').click();
  await page.getByTestId('create-element-btn').click();
  await page.getByTestId(`create-element-type-${type}`).click();
  await page.getByTestId('create-element-name-input').fill(name);
  await page.getByTestId('create-element-submit-btn').click();
}
async function navigateBack(page: Page) {
  await page.getByTestId('nav-assets').click();
  await page.getByTestId('nav-elements').click();
}

for (const [type, input, save] of [['gui', 'gui-width', 'gui-save-btn'], ['overlay', 'overlay-grid-sx', 'overlay-save-btn']] as const) {
  test(`${type}: a numeric draft survives navigation and language without a default value or write`, async ({ page }) => {
    await page.goto('/');
    await createElement(page, type, `recovery_${type}`);
    const number = page.getByTestId(input);
    await expect(number).toBeVisible();
    await number.fill('-');
    await expect(page.getByTestId(save)).toBeDisabled();
    await navigateBack(page);
    await expect(number).toHaveValue('-');
    await page.evaluate(async () => { const modulePath = '/src/i18n/locale.ts'; await (await import(modulePath)).setUiLocale('en'); });
    await expect(number).toHaveValue('-');
    await number.fill('20.');
    await navigateBack(page);
    await expect(number).toHaveValue('20.');
    await expect(page.getByTestId(save)).toBeEnabled();
    await page.getByTestId(save).click();
    await expect(number).toHaveValue('20');
    await expect(page.getByTestId(save)).toBeDisabled();
  });
}

test('loot number input keeps its partial text and valid zero is never replaced by a default', async ({ page }) => {
  await page.goto('/');
  await createElement(page, 'loottable', 'recovery_loot');
  const number = page.getByTestId('pool-minrolls-input');
  await expect(number).toBeVisible();
  await number.fill('-');
  await expect(page.getByTestId('loottable-save-btn')).toBeDisabled();
  await navigateBack(page);
  await expect(number).toHaveValue('-');
  await number.fill('0');
  await expect(number).toHaveValue('0');
  await expect(page.getByTestId('loottable-save-btn')).toBeEnabled();
});

test('Procedure navigation restores the actual Blockly draft rather than rebuilding the Core baseline', async ({ page }) => {
  await page.goto('/');
  await createElement(page, 'procedure', 'recovery_procedure');
  await page.getByLabel('筛选过程节点分类').selectOption('value');
  await page.getByLabel('搜索过程节点').fill('数值');
  await page.getByRole('button', { name: /^数值 值/ }).click();
  await expect(page.locator('.procedure-save')).toBeEnabled();
  await navigateBack(page);
  await page.getByLabel('搜索当前过程图').fill('数值');
  await expect(page.getByTestId('procedure-graph-navigation')).toContainText('1 个匹配');
  await expect(page.locator('.procedure-save')).toBeEnabled();
  await page.locator('.procedure-save').click();
  await expect(page.locator('.procedure-save')).toBeDisabled();
  await navigateBack(page);
  await page.getByLabel('搜索当前过程图').fill('数值');
  await expect(page.getByTestId('procedure-graph-navigation')).toContainText('1 个匹配');
  await expect(page.locator('.procedure-save')).toBeDisabled();
});

test('application settings retain invalid numeric input across navigation and locale without saving', async ({ page }) => {
  await page.addInitScript(() => {
    const observed = window as typeof window & { preferenceSaveCount: number };
    observed.preferenceSaveCount = 0;
    const snapshot = { schemaVersion: '1.0' as const, revision: 'settings-recovery-1', entries: [
      { key: 'ide.fontSize', section: 'ide' as const, type: 'integer' as const, value: 13, min: 8, max: 96 }
    ] };
    window.__COPPERBENCH_WINDOW_HOST__ = { systemFrame: false, preferencesAvailable: true, preferencesSchemaVersion: '1.0',
      getPreferences: async () => structuredClone(snapshot),
      savePreferences: async patch => { observed.preferenceSaveCount++; snapshot.entries[0].value = Number(patch.changes['ide.fontSize']); return structuredClone(snapshot); },
      invoke: async () => {} };
  });
  await page.goto('/');
  await page.getByTestId('nav-settings').click();
  const view = page.getByTestId('settings-view');
  await view.getByRole('button', { name: '代码编辑器', exact: true }).click();
  const input = page.locator('[id="preference-ide.fontSize"]');
  await input.fill('-');
  await expect(view.getByRole('button', { name: '保存更改', exact: true })).toBeDisabled();
  await page.getByTestId('nav-assets').click();
  await page.getByTestId('nav-settings').click();
  await view.getByRole('button', { name: '代码编辑器', exact: true }).click();
  await expect(input).toHaveValue('-');
  await page.evaluate(async () => { const modulePath = '/src/i18n/locale.ts'; await (await import(modulePath)).setUiLocale('en'); });
  await expect(input).toHaveValue('-');
  await expect(view.getByRole('button', { name: 'Save changes', exact: true })).toBeDisabled();
  expect(await page.evaluate(() => (window as typeof window & { preferenceSaveCount: number }).preferenceSaveCount)).toBe(0);
});
