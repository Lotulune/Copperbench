import { expect, test, type Page } from '@playwright/test';

async function settingsHost(page: Page, rejectSave = false) {
  await page.addInitScript(({ rejectSave }) => {
    localStorage.setItem('copperbench.ui.locale', 'en');
    const state = {
      schemaVersion: '1.0' as const, revision: 'preferences-1',
      entries: [
        { key: 'ui.nativeFileChooser', section: 'ui' as const, type: 'boolean' as const, value: true },
        { key: 'ide.editorTheme', section: 'ide' as const, type: 'choice' as const, value: 'MCreator', options: ['MCreator', 'Dark'] },
        { key: 'ide.fontSize', section: 'ide' as const, type: 'integer' as const, value: 12, min: 5, max: 48 },
        { key: 'gradle.Xmx', section: 'gradle' as const, type: 'integer' as const, value: 3072, min: 128, max: 8192 },
        { key: 'gradle.offline', section: 'gradle' as const, type: 'boolean' as const, value: false }
      ]
    };
    const hostWindow = window as unknown as { __COPPERBENCH_WINDOW_HOST__: unknown; __SETTINGS_PATCHES__: unknown[] };
    hostWindow.__SETTINGS_PATCHES__ = [];
    hostWindow.__COPPERBENCH_WINDOW_HOST__ = {
      systemFrame: true, preferencesAvailable: true, preferencesSchemaVersion: '1.0',
      invoke: async () => {},
      getPreferences: async () => structuredClone(state),
      savePreferences: async (patch: { revision: string; changes: Record<string, string | number | boolean> }) => {
        hostWindow.__SETTINGS_PATCHES__.push(structuredClone(patch));
        if (rejectSave) throw new Error('PREFERENCES_CONFLICT: Preferences changed; reload before saving');
        for (const entry of state.entries) {
          if (Object.hasOwn(patch.changes, entry.key)) Object.assign(entry, { value: patch.changes[entry.key] });
        }
        state.revision = 'preferences-2';
        return structuredClone(state);
      }
    };
  }, { rejectSave });
}

async function openSettings(page: Page) {
  await page.goto('/');
  await page.getByTestId('nav-settings').click();
  return page.getByTestId('settings-view');
}

test('settings save only explicit changed fields and reload persisted values', async ({ page }, testInfo) => {
  await settingsHost(page);
  const settings = await openSettings(page);
  await page.evaluate(() => window.addEventListener('copperbench:preferences-saved', event => {
    (window as unknown as { __SAVED_PREFERENCES__: unknown }).__SAVED_PREFERENCES__ = (event as CustomEvent).detail;
  }));
  await expect(settings.getByRole('heading', { name: 'Settings', exact: true })).toBeVisible();
  await settings.getByRole('checkbox', { name: 'System file picker' }).uncheck();
  await settings.getByRole('button', { name: 'Code editor', exact: true }).click();
  await settings.getByRole('spinbutton', { name: 'Code font size', exact: true }).fill('18');
  await expect(settings.getByText('Unsaved changes', { exact: true })).toBeVisible();
  expect(await page.evaluate(() => (window as unknown as { __SETTINGS_PATCHES__: unknown[] }).__SETTINGS_PATCHES__)).toEqual([]);
  await settings.getByRole('button', { name: 'Save changes', exact: true }).click();
  await expect(settings.getByText('Settings saved', { exact: true })).toBeVisible();
  expect(await page.evaluate(() => (window as unknown as { __SAVED_PREFERENCES__: { revision: string } }).__SAVED_PREFERENCES__.revision)).toBe('preferences-2');
  expect(await page.evaluate(() => (window as unknown as { __SETTINGS_PATCHES__: unknown[] }).__SETTINGS_PATCHES__)).toEqual([
    { revision: 'preferences-1', changes: { 'ui.nativeFileChooser': false, 'ide.fontSize': 18 } }
  ]);
  await settings.getByRole('button', { name: 'Reload', exact: true }).click();
  await expect(settings.getByRole('spinbutton', { name: 'Code font size', exact: true })).toHaveValue('18');
  await expect(settings.getByRole('button', { name: 'Save changes', exact: true })).toBeDisabled();
  if (testInfo.project.name === 'compact-1366') await page.screenshot({ path: testInfo.outputPath('settings.png') });
});

test('host ranges govern validation and search finds settings across categories', async ({ page }) => {
  await settingsHost(page);
  const settings = await openSettings(page);
  await settings.getByRole('searchbox', { name: 'Search settings' }).fill('memory');
  const memory = settings.getByRole('spinbutton', { name: 'Maximum build memory (MB)', exact: true });
  await memory.fill('8193');
  await expect(memory).toHaveAttribute('aria-invalid', 'true');
  await expect(settings.getByText('Enter a whole number in range: 128–8192')).toBeVisible();
  await expect(settings.getByRole('button', { name: 'Save changes', exact: true })).toBeDisabled();
  await memory.fill('4096');
  await expect(settings.getByRole('button', { name: 'Save changes', exact: true })).toBeEnabled();
  await settings.getByRole('button', { name: 'Discard & reload', exact: true }).click();
  await expect(memory).toHaveValue('3072');
  expect(await page.evaluate(() => (window as unknown as { __SETTINGS_PATCHES__: unknown[] }).__SETTINGS_PATCHES__)).toEqual([]);
});

test('rejected save retains draft and never announces success', async ({ page }) => {
  await settingsHost(page, true);
  const settings = await openSettings(page);
  await settings.getByRole('button', { name: 'Code editor', exact: true }).click();
  await settings.getByRole('spinbutton', { name: 'Code font size', exact: true }).fill('20');
  await settings.getByRole('button', { name: 'Save changes', exact: true }).click();
  await expect(settings.getByRole('alert')).toContainText('PREFERENCES_CONFLICT');
  await expect(settings.getByRole('spinbutton', { name: 'Code font size', exact: true })).toHaveValue('20');
  await expect(settings.getByText('Settings saved', { exact: true })).toHaveCount(0);
  await settings.getByRole('button', { name: 'Discard & reload', exact: true }).click();
  await expect(settings.getByRole('spinbutton', { name: 'Code font size', exact: true })).toHaveValue('12');
});

test('browser without preferences host shows availability instead of fake controls', async ({ page }) => {
  await page.addInitScript(() => localStorage.setItem('copperbench.ui.locale', 'en'));
  const settings = await openSettings(page);
  await expect(settings.getByRole('heading', { name: 'Application settings are unavailable' })).toBeVisible();
  await expect(settings.getByRole('button', { name: 'Save changes' })).toHaveCount(0);
  await expect(settings.getByRole('button', { name: 'Advanced settings' })).toHaveCount(0);
});
