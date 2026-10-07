import { test, expect, type Page } from '@playwright/test';

async function installGuard(page: Page) {
  await page.addInitScript(() => {
    Object.assign(window, { draftReports: [] as number[], closeRequests: 0 });
    window.__COPPERBENCH_WINDOW_HOST__ = {
      systemFrame: false, unsavedChangesSchemaVersion: '1.0',
      async reportUnsavedChanges(count) { (window as any).draftReports.push(count); },
      async invoke(action) { if (action === 'close') (window as any).closeRequests++; }
    };
  });
}
const dirtyCount = (page: Page) => page.evaluate(() => (window as any).draftReports.at(-1));
async function createFunction(page: Page, name: string) {
  await page.getByTestId('nav-elements').click();
  await page.getByTestId('create-element-btn').click();
  await page.getByTestId('create-element-modal').getByRole('button', { name: '函数（Function）', exact: true }).click();
  await page.getByTestId('create-element-name-input').fill(name);
  await page.getByTestId('create-element-submit-btn').click();
  await expect(page.getByTestId('function-code-editor')).toBeEditable();
}

test('function drafts survive navigation and reopening, and save clears the shared close guard', async ({ page }) => {
  await installGuard(page);
  await page.goto('/');
  await createFunction(page, 'retained_function');
  await page.getByTestId('function-code-editor').fill('say retained draft\n');
  await expect.poll(() => dirtyCount(page)).toBe(1);
  await page.getByTestId('nav-assets').click();
  await page.getByTestId('workbench-tab-elements').click();
  await expect(page.getByTestId('function-code-editor')).toHaveValue('say retained draft\n');
  await page.getByTestId('function-back-btn').click();
  await page.locator('.element-card').filter({ hasText: 'retained_function' }).click();
  await expect(page.getByTestId('function-code-editor')).toHaveValue('say retained draft\n');
  await page.getByTestId('function-save-btn').click();
  await expect(page.getByTestId('function-save-btn')).toBeDisabled();
  await expect.poll(() => dirtyCount(page)).toBe(0);
  await page.getByTestId('nav-assets').click();
  await page.getByTestId('workbench-tab-elements').click();
  await expect(page.getByTestId('function-code-editor')).toHaveValue('say retained draft\n');
});

test('ordinary element and source drafts are counted together after leaving both editors', async ({ page }) => {
  await installGuard(page);
  await page.goto('/');
  await page.getByTestId('nav-elements').click();
  await page.locator('.element-card').filter({ hasText: 'Copper Lamp' }).click();
  await page.getByTestId('field-displayName').fill('Unsaved lamp');
  await expect.poll(() => dirtyCount(page)).toBe(1);
  await page.getByTestId('nav-source').click();
  await page.locator('[data-source-path="gradle.properties"]').click();
  await page.getByTestId('source-editor').fill('mod_version=2.0.0\n');
  await expect.poll(() => dirtyCount(page)).toBe(2);
  await page.getByTestId('nav-hub').click();
  await page.getByTestId('window-close-btn').click();
  await expect.poll(() => page.evaluate(() => (window as any).closeRequests)).toBe(1);
  await expect.poll(() => dirtyCount(page)).toBe(2);
  await page.getByTestId('nav-elements').click();
  await expect(page.getByTestId('field-displayName')).toHaveValue('Unsaved lamp');
  expect(await page.evaluate(() => {
    const event = new Event('beforeunload', { cancelable: true }); window.dispatchEvent(event); return event.defaultPrevented;
  })).toBe(true);
});

test('a pending function save remains locked across navigation and clears its draft on completion', async ({ page }) => {
  await installGuard(page);
  await page.addInitScript(() => {
    const ready = import('/src/mock/mockBridge.ts').then(({ MockCoreBridge }) => new MockCoreBridge());
    window.copperbenchHost = {
      workspaceId: '11111111-1111-4111-8111-111111111111', onEvent() { return () => {}; },
      async invoke(raw) {
        const core = await ready, request = JSON.parse(raw);
        if (request.operation === 'update_mod_element') await new Promise<void>(resolve => { (window as any).finishFunctionSave = resolve; });
        return JSON.stringify(request.messageType === 'handshake' ? await core.negotiateHandshake(request)
          : request.messageType === 'command' ? await core.sendCommand(request) : await core.sendQuery(request));
      }
    };
  });
  await page.goto('/');
  await createFunction(page, 'pending_function');
  await page.getByTestId('function-code-editor').fill('say pending draft\n');
  await page.getByTestId('function-save-btn').click();
  await page.getByTestId('nav-assets').click();
  await page.getByTestId('workbench-tab-elements').click();
  await expect(page.getByTestId('function-code-editor')).not.toBeEditable();
  await page.evaluate(() => (window as any).finishFunctionSave());
  await expect(page.getByTestId('function-code-editor')).toBeEditable();
  await expect(page.getByTestId('function-code-editor')).toHaveValue('say pending draft\n');
  await expect.poll(() => dirtyCount(page)).toBe(0);
});

test('restored function drafts keep their original revision and survive a rejected stale save', async ({ page }) => {
  await installGuard(page);
  await page.goto('/');
  await createFunction(page, 'stale_function');
  await page.getByTestId('function-code-editor').fill('say keep the stale draft\n');
  await page.getByTestId('nav-assets').click();
  await page.evaluate(async () => {
    const { coreBridge } = await import('/src/bridge/index.ts');
    coreBridge.getState().workbench!.workspace.revision++;
    (coreBridge as any).notifyState();
  });
  await page.getByTestId('nav-elements').click();
  await page.getByTestId('function-save-btn').click();
  await expect(page.getByTestId('revision-conflict-dialog')).toBeVisible();
  await page.keyboard.press('Escape');
  await expect(page.getByTestId('function-code-editor')).toHaveValue('say keep the stale draft\n');
  await expect.poll(() => dirtyCount(page)).toBe(1);
  page.once('dialog', dialog => dialog.accept());
  await page.getByTestId('function-reload').click();
  await expect(page.getByTestId('function-code-editor')).not.toHaveValue('say keep the stale draft\n');
  await expect.poll(() => dirtyCount(page)).toBe(0);
});

test('keyboard command selection scrolls into view in both directions', async ({ page }) => {
  await page.goto('/');
  await page.getByTestId('workbench-command-trigger').click();
  for (let i = 0; i < 12; i++) await page.keyboard.press('ArrowDown');
  const visible = () => page.locator('#workbench-command-results').evaluate(list => {
    const a = list.getBoundingClientRect(), b = list.querySelector('[aria-selected="true"]')!.getBoundingClientRect();
    return b.top >= a.top - 1 && b.bottom <= a.bottom + 1;
  });
  await expect.poll(visible).toBe(true);
  for (let i = 0; i < 12; i++) await page.keyboard.press('ArrowUp');
  await expect.poll(visible).toBe(true);
});

test('a clean function refreshes its baseline after an unrelated workspace revision', async ({ page }) => {
  await installGuard(page);
  await page.goto('/');
  await createFunction(page, 'clean_function');
  await page.getByTestId('nav-assets').click();
  await page.evaluate(async () => {
    const { coreBridge } = await import('/src/bridge/index.ts');
    coreBridge.getState().workbench!.workspace.revision++;
    (coreBridge as any).notifyState();
  });
  await page.getByTestId('nav-elements').click();
  await expect(page.getByTestId('function-code-editor')).toBeEditable();
  await page.getByTestId('function-code-editor').fill('say new edit after refresh\n');
  await page.getByTestId('function-save-btn').click();
  await expect(page.getByTestId('revision-conflict-dialog')).toHaveCount(0);
  await expect.poll(() => dirtyCount(page)).toBe(0);
  await expect(page.getByTestId('function-code-editor')).toHaveValue('say new edit after refresh\n');
});

test('asset source actions use the Core capability instead of guessing from the filename', async ({ page }) => {
  await page.goto('/');
  await page.getByTestId('nav-assets').click();
  await page.getByTestId(`asset-card-asset:${'4'.repeat(64)}`).click();
  await expect(page.getByRole('button', { name: '查看文件源码', exact: true })).toBeVisible();
  await page.evaluate(async () => {
    const { coreBridge } = await import('/src/bridge/index.ts');
    const original = coreBridge.sendQuery.bind(coreBridge);
    coreBridge.sendQuery = async (query: any) => {
      const result = await original(query);
      if (query.operation === 'list_assets') for (const asset of (result.data as any).assets) asset.sourceAvailable = false;
      return result;
    };
    window.dispatchEvent(new Event('focus'));
  });
  await expect(page.getByRole('button', { name: '查看文件源码', exact: true })).toHaveCount(0);
});
