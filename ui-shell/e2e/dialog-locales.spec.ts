import { test, expect } from '@playwright/test';

for (const [systemLanguage, expected] of [['zh-TW', 'zh'], ['en-US', 'en'], ['de-DE', 'en']] as const) {
  test(`first launch matches ${systemLanguage} and keeps an explicit choice`, async ({ browser }) => {
    const context = await browser.newContext({ locale: systemLanguage });
    const page = await context.newPage();
    await page.goto('http://localhost:5173');
    await expect(page.getByTestId('ui-language-select')).toHaveValue(expected);
    const chosen = expected === 'en' ? 'zh' : 'en';
    await page.getByTestId('ui-language-select').selectOption(chosen);
    await page.reload();
    await expect(page.getByTestId('ui-language-select')).toHaveValue(chosen);
    await context.close();
  });
}

for (const locale of ['zh', 'en'] as const) {
  test(`conflict actions in ${locale} never claim a nonexistent copy`, async ({ page }) => {
    await page.addInitScript(value => localStorage.setItem('copperbench.ui.locale', value), locale);
    await page.goto('/');
    await page.getByTestId('scenario-switcher-trigger').click();
    await page.getByTestId('scenario-btn-revision-conflict').click();
    const dialog = page.getByTestId('revision-conflict-dialog');
    await expect(dialog).toBeVisible();
    await expect(dialog.getByRole('button')).toHaveCount(3);
    await expect(dialog).not.toContainText(locale === 'en' ? 'Clone' : '克隆');
    await expect(dialog).not.toContainText('MCP');
    await page.screenshot({ path: `../output/ux-implementation/conflict-${locale}.png` });
    await dialog.getByRole('button', { name: locale === 'en' ? 'Keep editing' : '继续编辑' }).click();
    await expect(dialog).not.toBeVisible();
  });
}

test('native locale broadcasts keep the open draft intact', async ({ page }) => {
  await page.goto('/');
  await page.getByTestId('nav-new-workspace').click();
  await page.getByTestId('new-workspace-mod-name-input').fill('Native event draft');
  await page.evaluate(() => window.dispatchEvent(new CustomEvent('copperbench:locale-changed', { detail: 'en' })));
  await expect(page.getByTestId('ui-language-select')).toHaveValue('en');
  await expect(page.getByTestId('new-workspace-mod-name-input')).toHaveValue('Native event draft');
  await expect(page.locator('html')).toHaveAttribute('lang', 'en');
});

test('reviewing a conflict retains new elements and the open draft', async ({ page }) => {
  await page.goto('/');
  await page.getByTestId('nav-elements').click();
  await page.getByTestId('create-element-btn').click();
  await page.getByTestId('create-element-modal').getByRole('button', { name: '物品（item）', exact: true }).click();
  await page.getByTestId('create-element-name-input').fill('keep_after_refresh');
  await page.getByTestId('create-element-submit-btn').click();
  await page.getByTestId('nav-new-workspace').click();
  await page.getByTestId('new-workspace-mod-name-input').fill('Keep during refresh');
  await page.evaluate(async () => {
    const bridgePath = '/src/bridge/index.ts';
    const { coreBridge } = await import(/* @vite-ignore */ bridgePath);
    coreBridge.simulateConflict();
  });
  await page.getByTestId('conflict-refresh-btn').click();
  await expect(page.getByTestId('revision-conflict-dialog')).not.toBeVisible();
  await expect(page.getByTestId('new-workspace-mod-name-input')).toHaveValue('Keep during refresh');
  await page.getByTestId('nav-elements').click();
  await expect(page.getByText('keep_after_refresh', { exact: true }).first()).toBeVisible();
});

test('failed refresh keeps the conflict open with actionable feedback', async ({ page }) => {
  await page.goto('/');
  await page.evaluate(async () => {
    const bridgePath = '/src/bridge/index.ts';
    const { coreBridge } = await import(/* @vite-ignore */ bridgePath);
    coreBridge.negotiateHandshake = async () => { throw new Error('connection lost'); };
    coreBridge.simulateConflict();
  });
  await page.getByTestId('conflict-refresh-btn').click();
  const dialog = page.getByTestId('revision-conflict-dialog');
  await expect(dialog).toBeVisible();
  await expect(dialog.getByRole('alert')).toContainText('暂时无法读取最新状态');
  await expect(page.getByTestId('conflict-refresh-btn')).toBeEnabled();
});
