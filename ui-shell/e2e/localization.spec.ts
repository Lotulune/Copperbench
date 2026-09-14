import { test, expect } from '@playwright/test';

test('language change can be cancelled without losing a draft, then persists after reload', async ({ page }) => {
  await page.goto('/');
  await page.getByTestId('nav-new-workspace').click();
  await page.getByTestId('new-workspace-mod-name-input').fill('My unsaved project');
  page.once('dialog', dialog => dialog.dismiss());
  await page.getByTestId('language-select').selectOption('en');
  await expect(page.getByTestId('language-select')).toHaveValue('zh');
  await expect(page.getByTestId('new-workspace-mod-name-input')).toHaveValue('My unsaved project');
  page.once('dialog', dialog => dialog.accept());
  await page.getByTestId('language-select').selectOption('en');
  await expect(page.locator('html')).toHaveAttribute('lang', 'en');
  await expect(page.getByTestId('nav-hub')).toHaveText('Overview');
  await page.reload();
  await expect(page.getByTestId('language-select')).toHaveValue('en');
  page.once('dialog', dialog => dialog.accept());
  await page.getByTestId('language-select').selectOption('zh');
  await expect(page.getByTestId('nav-hub')).toHaveText('总览');
});

test('English editors retain literal user content and initialize Blockly in English', async ({ page }) => {
  await page.addInitScript(() => localStorage.setItem('copperbench.ui.locale', 'en'));
  await page.goto('/');
  await page.getByTestId('nav-elements').click();
  await page.getByTestId('create-element-btn').click();
  await page.getByTestId('create-element-modal').getByRole('button', { name: 'Procedure', exact: true }).click();
  await page.getByTestId('create-element-name-input').fill('lantern_tick');
  await page.getByTestId('create-element-submit-btn').click();
  await expect(page.getByTestId('procedure-workbench')).toBeVisible();
  await expect(page.locator('.blocklySvg').first()).toContainText('Trigger');
  await expect(page.getByLabel('Search procedure nodes')).toBeVisible();
  // Literal interpolation must not decode entities, translate user text, or replace nested placeholders.
  const result = await page.evaluate(async () => {
    const modulePath = '/src/i18n/locale.ts';
    const { tr, readLocale } = await import(/* @vite-ignore */ modulePath);
    const catalogPath = '/src/i18n/index.ts';
    const { t } = await import(/* @vite-ignore */ catalogPath);
    return {
      text: tr('重命名 {0}', ['总览 &lt; $& {1}']),
      unknown: tr('custom &lt;user&gt; text'),
      invalid: readLocale({ getItem: () => 'invalid' }),
      blocked: readLocale({ getItem: () => { throw new Error('blocked'); } }),
      nativeNode: t({ key: 'procedure.node.controls_if', fallback: '条件' }),
      nativeField: t({ key: 'field.displayName', fallback: '显示名称' })
    };
  });
  expect(result).toEqual({ text: 'Rename 总览 &lt; $& {1}', unknown: 'custom &lt;user&gt; text', invalid: 'zh', blocked: 'zh', nativeNode: 'If', nativeField: 'Display name' });
});

test('native language overrides browser cache and failed persistence does not reload', async ({ page }) => {
  await page.addInitScript(() => {
    localStorage.setItem('copperbench.ui.locale', 'zh');
    window.__COPPERBENCH_UI_LOCALE__ = 'en';
    window.__COPPERBENCH_SET_LOCALE__ = async () => { throw new Error('disk full'); };
  });
  await page.goto('/');
  await expect(page.getByTestId('nav-hub')).toHaveText('Overview');
  await page.getByTestId('nav-new-workspace').click();
  await page.getByTestId('new-workspace-mod-name-input').fill('Keep this draft');
  page.on('dialog', dialog => dialog.accept());
  await page.getByTestId('language-select').selectOption('zh');
  await expect(page.getByTestId('language-select')).toHaveValue('en');
  await expect(page.getByTestId('new-workspace-mod-name-input')).toHaveValue('Keep this draft');
});
