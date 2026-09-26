import { test, expect } from '@playwright/test';

test('language change preserves a draft without reloading and persists after reload', async ({ page }) => {
  await page.goto('/');
  await page.getByTestId('nav-new-workspace').click();
  await page.getByTestId('new-workspace-mod-name-input').fill('My unsaved project');
  await page.evaluate(() => { (window as unknown as { draftSession: string }).draftSession = 'original'; });
  await page.getByTestId('ui-language-select').selectOption('en');
  await expect(page.getByTestId('ui-language-select')).toHaveValue('en');
  await expect(page.getByTestId('new-workspace-mod-name-input')).toHaveValue('My unsaved project');
  expect(await page.evaluate(() => (window as unknown as { draftSession: string }).draftSession)).toBe('original');
  await expect(page.locator('html')).toHaveAttribute('lang', 'en');
  await expect(page.getByTestId('nav-hub')).toHaveText('Overview');
  await page.reload();
  await expect(page.getByTestId('ui-language-select')).toHaveValue('en');
  await page.getByTestId('ui-language-select').selectOption('zh');
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
  await page.getByTestId('ui-language-select').selectOption('zh');
  await expect(page.getByTestId('ui-language-select')).toHaveValue('en');
  await expect(page.getByTestId('new-workspace-mod-name-input')).toHaveValue('Keep this draft');
});

test('native persistence completes before switching and preserves the editor session', async ({ page }) => {
  await page.addInitScript(() => {
    window.__COPPERBENCH_UI_LOCALE__ = 'zh';
    window.__COPPERBENCH_SET_LOCALE__ = async locale => {
      await new Promise(resolve => setTimeout(resolve, 100));
      localStorage.setItem('native-locale-test', locale);
    };
  });
  await page.goto('/');
  await page.getByTestId('nav-new-workspace').click();
  await page.getByTestId('new-workspace-mod-name-input').fill('Native draft');
  await page.getByTestId('ui-language-select').selectOption('en');
  await expect(page.getByTestId('ui-language-select')).toHaveValue('en');
  expect(await page.evaluate(() => localStorage.getItem('native-locale-test'))).toBe('en');
  await expect(page.getByTestId('new-workspace-mod-name-input')).toHaveValue('Native draft');
});
