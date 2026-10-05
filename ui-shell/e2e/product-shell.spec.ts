import { expect, test } from '@playwright/test';

test('opening a view reveals its active tab in a narrow window', async ({ page }) => {
  await page.setViewportSize({ width: 320, height: 800 });
  await page.goto('/');
  for (const view of ['assets', 'history', 'source']) await page.getByTestId(`nav-${view}`).click();
  const list = page.getByRole('tablist', { name: '工作区视图' });
  await expect.poll(async () => {
    const viewport = await list.boundingBox();
    const active = await page.getByTestId('workbench-tab-source').boundingBox();
    return !!viewport && !!active && active.x >= viewport.x - 1 && active.x + active.width <= viewport.x + viewport.width + 1;
  }).toBe(true);
});

test('project and tool navigation keeps existing workbenches reachable with source and settings', async ({ page }) => {
  const errors: string[] = [];
  page.on('pageerror', error => errors.push(error.message));
  await page.goto('/');
  const targets = [
    ['elements', 'elements-workbench'], ['assets', 'asset-browser'], ['relations', 'relationship-canvas'],
    ['source', 'source-workbench'], ['data', 'creator-data-view'], ['history', 'history-view'],
    ['tracks', 'tracks-view'], ['ai', 'approval-queue'], ['python', 'python-workbench'],
    ['plugins', 'installed-plugin-inventory'], ['help', 'help-view'], ['settings', 'settings-view'],
    ['new-workspace', 'new-workspace-view'], ['hub', 'workbench-main']
  ];
  for (const [route, panel] of targets) {
    if (['tracks', 'ai', 'python', 'plugins'].includes(route) && !await page.getByTestId(`nav-${route}`).isVisible()) await page.getByTestId('nav-tools-toggle').click();
    await page.getByTestId(`nav-${route}`).click();
    await expect(page.locator('.app-content-canvas')).toHaveAttribute('data-active-view', route);
    await expect(page.getByTestId(panel)).toBeVisible();
    await expect(page.getByTestId(`workbench-tab-${route}`)).toHaveAttribute('aria-selected', 'true');
  }
  expect(errors).toEqual([]);
});

test('command palette keyboard navigation opens real views, restores focus and finds elements', async ({ page }) => {
  await page.goto('/');
  const trigger = page.getByTestId('workbench-command-trigger');
  await trigger.focus();
  await page.keyboard.press('Control+k');
  const search = page.getByRole('combobox', { name: '搜索元素与工具' });
  await expect(search).toBeFocused();
  await search.fill('源码'); await search.press('Enter');
  await expect(page.getByTestId('source-workbench')).toBeVisible();
  await expect(trigger).toBeFocused();
  await page.keyboard.press('Control+k'); await search.fill('Copper Lamp'); await search.press('Enter');
  const editor = page.getByTestId('field-displayName');
  await expect(editor).toBeVisible(); await editor.fill('Persistent shell draft');
  await page.getByTestId('nav-assets').click();
  await page.getByTestId('workbench-tab-elements').click();
  await expect(editor).toHaveValue('Persistent shell draft');
  await page.getByTestId('workbench-tab-elements').focus(); await page.keyboard.press('ArrowRight');
  await expect(page.getByTestId('workbench-tab-assets')).toBeFocused();
  await expect(page.locator('.app-content-canvas')).toHaveAttribute('data-active-view', 'assets');
  await trigger.click(); await page.keyboard.press('Escape');
  await expect(page.getByTestId('workbench-command-palette')).toBeHidden(); await expect(trigger).toBeFocused();
});

test('Core source entry opens the matching file and view tabs preserve the source draft', async ({ page }) => {
  await page.goto('/');
  const entry = page.getByTestId('hub-source-entries').locator('[data-source-path]').first();
  await expect(entry).toBeVisible();
  const path = await entry.getAttribute('data-source-path');
  await entry.click();
  await expect(page.getByTestId('source-workbench')).toBeVisible();
  await expect(page.getByTestId('source-editor')).not.toHaveValue('');
  await expect(page.getByTestId('source-workbench')).toContainText(path!);
  await page.getByTestId('source-editor').fill('source navigation draft');
  await page.getByTestId('nav-settings').click();
  await page.getByTestId('workbench-tab-source').click();
  await expect(page.getByTestId('source-editor')).toHaveValue('source navigation draft');
  await page.getByRole('button', { name: '关闭视图：源码', exact: true }).click();
  await page.getByTestId('nav-source').click();
  await expect(page.getByTestId('source-editor')).toHaveValue('source navigation draft');
});

test('task drawer occupies the bottom area without covering the active editor', async ({ page }) => {
  await page.goto('/'); await page.getByTestId('nav-source').click();
  await page.getByTestId('recent-tasks-button').click();
  await expect(page.getByTestId('task-drawer')).toBeVisible();
  const content = await page.locator('.workbench-view-content').boundingBox();
  const drawer = await page.getByTestId('task-drawer').boundingBox();
  expect(content!.height).toBeGreaterThan(150);
  expect(content!.y + content!.height).toBeLessThanOrEqual(drawer!.y + 1);
  await page.getByTestId('task-drawer-close').click();
  await expect(page.getByTestId('source-workbench')).toBeVisible();
});

test('workspace icon presets, keyboard choice and normalized uploads persist per workspace', async ({ page }) => {
  await page.goto('/');
  const trigger = page.getByTestId('workspace-icon-chooser-trigger');
  await trigger.click();
  await expect(page.getByRole('radio')).toHaveCount(5);
  await page.getByRole('radio', { name: '文件夹', exact: true }).focus();
  await page.keyboard.press('ArrowRight');
  await expect(page.getByRole('radio', { name: '铜矿', exact: true })).toHaveAttribute('aria-checked', 'true');
  const preset = await page.getByRole('radio', { name: '铜矿', exact: true }).locator('img').getAttribute('src');
  await page.getByRole('button', { name: '使用此图标', exact: true }).click();
  await expect(trigger).toBeFocused();
  await expect(trigger.locator('img')).toHaveAttribute('src', preset!);
  await page.reload(); await expect(trigger.locator('img')).toHaveAttribute('src', preset!);
  await trigger.click();
  const png = await page.evaluate(() => { const canvas = document.createElement('canvas'); canvas.width = 16; canvas.height = 16;
    const context = canvas.getContext('2d')!; context.fillStyle = '#228855'; context.fillRect(0, 0, 16, 16); return canvas.toDataURL('image/png').split(',')[1]; });
  await page.getByLabel('上传工作区图标', { exact: true }).setInputFiles({ name: 'workspace.png', mimeType: 'image/png', buffer: Buffer.from(png, 'base64') });
  await expect(page.getByRole('radio', { name: '自定义', exact: true })).toHaveAttribute('aria-checked', 'true');
  await page.getByRole('button', { name: '使用此图标', exact: true }).click();
  await expect(trigger.locator('img')).toHaveAttribute('src', /^data:image\/png;base64,/);
  const stored = await page.evaluate(() => Object.keys(localStorage).filter(key => key.startsWith('copperbench.workspace-icon.')));
  expect(stored).toEqual(['copperbench.workspace-icon.11111111-1111-4111-8111-111111111111']);
  await trigger.click();
  await page.getByLabel('上传工作区图标', { exact: true }).setInputFiles({ name: 'invalid.svg', mimeType: 'image/svg+xml', buffer: Buffer.from('<svg/>') });
  await expect(page.getByRole('alert')).toContainText('PNG');
});

test('compiler source preview can navigate to the real source workbench', async ({ page }) => {
  await page.goto('/');
  await page.getByTestId('scenario-switcher-trigger').click();
  await page.getByTestId('scenario-btn-compile-diagnostic').click();
  await page.getByTestId('open-failed-task-logs-btn').click();
  await page.getByTestId('task-diag-action-open_generated_source').click();
  await page.getByTestId('task-source-open-editor').click();
  await expect(page.getByTestId('source-workbench')).toBeVisible();
  await expect(page.getByTestId('task-drawer')).toBeHidden();
  await expect(page.getByTestId('source-workbench')).toContainText('CopperLampElement.java');
});

test('window close protects source drafts while clean close reaches the host directly', async ({ page }) => {
  await page.addInitScript(() => {
    window.__COPPERBENCH_WINDOW_HOST__ = { systemFrame: false, invoke: async action => {
      if (action === 'close') sessionStorage.setItem('shell-close-count', String(Number(sessionStorage.getItem('shell-close-count') || '0') + 1));
    } };
  });
  await page.goto('/');
  const close = page.getByTestId('window-close-btn');
  await close.click();
  await expect.poll(() => page.evaluate(() => sessionStorage.getItem('shell-close-count'))).toBe('1');
  await page.getByTestId('nav-source').click();
  await page.locator('[data-source-path="src/main/java/example/ExampleMod.java"]').first().click();
  await page.getByTestId('source-editor').fill('unsaved window draft');
  await page.getByTestId('nav-hub').click();
  const cancel = page.waitForEvent('dialog');
  const cancelledClick = close.click();
  const confirmation = await cancel;
  expect(confirmation.type()).toBe('confirm');
  expect(confirmation.message()).toContain('未保存'); await confirmation.dismiss(); await cancelledClick;
  expect(await page.evaluate(() => sessionStorage.getItem('shell-close-count'))).toBe('1');
  const accept = page.waitForEvent('dialog'); const acceptedClick = close.click();
  await (await accept).accept(); await acceptedClick;
  await expect.poll(() => page.evaluate(() => sessionStorage.getItem('shell-close-count'))).toBe('2');
  await page.getByTestId('nav-source').click(); await expect(page.getByTestId('source-editor')).toHaveValue('unsaved window draft');
  await page.getByTestId('source-save').click(); await expect(page.getByTestId('source-save')).toBeDisabled();
  await close.click();
  await expect.poll(() => page.evaluate(() => sessionStorage.getItem('shell-close-count'))).toBe('3');
});
