import { test, expect } from '@playwright/test';
import { readFileSync } from 'node:fs';

// Execute the native fixture's actual scripts against React. This checks locale/setup
// behavior only; browser/mock timings are not Windows JCEF performance evidence.
const nativeSource = readFileSync(new URL('../../src/test/java/dev/copperbench/shell/Stage9NativeJcefScaleGateTest.java', import.meta.url), 'utf8');
function script(name: string) {
  const match = nativeSource.match(new RegExp(`String ${name} = """([\\s\\S]*?)""";`));
  if (!match) throw new Error(`Missing native fixture script ${name}`);
  return match[1];
}
const localeSetup = script('FIXTURE_LOCALE_SETUP');
const refreshButton = script('REFERENCE_REFRESH_BUTTON');
const buttonWait = nativeSource.split('private static void awaitButton(')[1].match(/"""([\s\S]*?)"""/)![1];
const refreshReady = buttonWait.replace('%s', refreshButton);
const diagnostics = nativeSource.split('private static String diagnostics(')[1].match(/"""([\s\S]*?)"""/)![1];

for (const locale of ['en-US', 'zh-CN']) {
  test.describe(`native scale fixture on ${locale}`, () => {
    test.use({ locale });
    test('sets a session locale for refresh, save and return without changing preferences', async ({ page }) => {
      await page.goto('/');
      await expect(page.getByTestId('app-shell')).toBeVisible();
      await page.getByTestId('nav-data').click();
      await expect(page.getByTestId('creator-data-view')).toBeVisible();
      if (locale === 'en-US') {
        await expect(page.locator('html')).toHaveAttribute('lang', 'en');
        await expect(page.getByRole('button', { name: 'Refresh', exact: true })).toBeEnabled();
        expect(await page.evaluate(refreshReady)).toBe(false); // the old Chinese locator misses English
      }
      const preference = await page.evaluate(() => localStorage.getItem('copperbench.ui.locale'));
      await page.evaluate(localeSetup);
      await expect(page.locator('html')).toHaveAttribute('lang', 'zh-CN');
      await expect.poll(() => page.evaluate(refreshReady)).toBe(true);
      await page.getByRole('button', { name: '刷新', exact: true }).click();
      await expect.poll(() => page.evaluate(refreshReady)).toBe(true);

      await page.getByTestId('nav-elements').click();
      await page.getByTestId('create-element-btn').click();
      await page.getByTestId('create-element-modal').getByRole('button', { name: '过程（Procedure）', exact: true }).click();
      await page.getByTestId('create-element-name-input').fill('native_locale_probe');
      await page.getByTestId('create-element-submit-btn').click();
      await expect(page.getByTestId('procedure-workbench')).toBeVisible();
      await page.getByTestId('procedure-palette-search').fill('math_number');
      await page.locator('.procedure-node-list button:not([disabled])').first().click();
      await page.locator('button.procedure-save').click();
      await expect(page.locator('.procedure-message')).toContainText('已保存');
      await page.getByRole('button', { name: '返回元素列表', exact: true }).click();
      await expect(page.getByTestId('procedure-workbench')).toHaveCount(0);
      expect(await page.evaluate(() => localStorage.getItem('copperbench.ui.locale'))).toBe(preference);
    });
  });
}

test('native readiness and diagnostics distinguish missing, disabled and hidden refresh buttons', async ({ page }) => {
  await page.setContent('<div data-testid="creator-data-view"></div>');
  expect(await page.evaluate(refreshReady)).toBe(false);
  await page.locator('[data-testid=creator-data-view]').evaluate(root => {
    root.innerHTML = '<button disabled>刷新</button>';
  });
  expect(await page.evaluate(refreshReady)).toBe(false);
  expect(JSON.parse(await page.evaluate(diagnostics))).toMatchObject({
    dataView: true, procedureView: false, buttons: [{ text: '刷新', disabled: true, visible: true }]
  });
  await page.locator('button').evaluate(button => { (button as HTMLButtonElement).disabled = false; button.style.display = 'none'; });
  expect(await page.evaluate(refreshReady)).toBe(false);
  await page.locator('button').evaluate(button => { button.style.display = ''; });
  expect(await page.evaluate(refreshReady)).toBe(true);
});
