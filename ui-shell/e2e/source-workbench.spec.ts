import { test, expect, type Page } from '@playwright/test';

const javaPath = 'src/main/java/example/ExampleMod.java';
const propertiesPath = 'gradle.properties';

async function openSource(page: Page) {
  await page.goto('/');
  await page.getByTestId('nav-source').click();
  await expect(page.getByTestId('source-workbench')).toBeVisible();
}
async function openFile(page: Page, path: string) {
  await page.locator('[data-source-path]').filter({ has: page.locator('span') }).locator(`xpath=self::*[@data-source-path="${path}"]`).click();
  await expect(page.getByTestId('source-editor')).not.toHaveAttribute('readonly', '');
}

test('source tabs retain drafts across views and Ctrl+S saves through Core with revision and hash', async ({ page }) => {
  await page.addInitScript(() => Object.defineProperty(crypto, 'randomUUID', { value: undefined }));
  await openSource(page);
  await openFile(page, javaPath);
  const original = await page.getByTestId('source-editor').inputValue();
  const draft = original.replace('registerItems();', 'registerItems(); // edited');
  await page.getByTestId('source-editor').fill(draft);
  await expect(page.getByRole('tab', { name: /ExampleMod.java/ })).toContainText('●');
  await openFile(page, propertiesPath);
  await page.getByTestId('source-editor').fill('mod_version=2.0.0\nmod_id=example\n');
  await page.getByTestId('nav-assets').click();
  await page.getByTestId('nav-source').click();
  await expect(page.getByTestId('source-editor')).toHaveValue('mod_version=2.0.0\nmod_id=example\n');
  await page.getByRole('tab', { name: /ExampleMod.java/ }).click();
  await expect(page.getByTestId('source-editor')).toHaveValue(draft);
  await page.getByTestId('source-editor').press('Control+s');
  await expect(page.getByTestId('source-save')).toBeDisabled();
  await expect(page.getByRole('tab', { name: /ExampleMod.java/ })).not.toContainText('●');
  await page.getByTestId('source-reload').click();
  await expect(page.getByTestId('source-editor')).toHaveValue(draft);
  const saved = await page.evaluate(async path => {
    const modulePath = '/src/bridge/sourceBridge.ts';
    const { sourceBridge } = await import(modulePath);
    return sourceBridge.read('11111111-1111-4111-8111-111111111111', path);
  }, javaPath);
  expect(saved.data.content).toBe(draft);
  expect(saved.data.sha256).toMatch(/^[0-9a-f]{64}$/);
  expect(saved.revision).toBeGreaterThan(42);
  await page.getByRole('tab', { name: /gradle.properties/ }).click();
  await expect(page.getByTestId('source-editor')).toHaveValue('mod_version=2.0.0\nmod_id=example\n');
});

test('Core-generated ownership stays read only and file search does not discard drafts', async ({ page }) => {
  await openSource(page);
  await openFile(page, javaPath);
  await page.getByTestId('source-editor').fill('local draft');
  await page.getByRole('textbox', { name: '搜索文件路径' }).fill('Generated');
  await expect(page.locator('[data-source-path]')).toHaveCount(1);
  await page.locator('[data-source-path]').click();
  await expect(page.getByTestId('source-editor')).toHaveAttribute('readonly', '');
  await expect(page.getByTestId('source-workbench')).toContainText('生成文件 · 只读');
  await expect(page.getByTestId('source-save')).toBeDisabled();
  await page.getByRole('tab', { name: /ExampleMod.java/ }).click();
  await expect(page.getByTestId('source-editor')).toHaveValue('local draft');
});

test('external revision and file hash conflict keep the draft until explicit resolution', async ({ page }) => {
  await openSource(page);
  await openFile(page, javaPath);
  await page.getByTestId('source-editor').fill('my local draft\n');
  await page.evaluate(async path => {
    const modulePath = '/src/bridge/sourceBridge.ts';
    const { sourceBridge } = await import(modulePath);
    const id = '11111111-1111-4111-8111-111111111111';
    const current = await sourceBridge.read(id, path);
    await sourceBridge.save(id, path, 'external edit\n', current.revision, current.data.sha256);
  }, javaPath);
  await page.getByTestId('source-save').click();
  await expect(page.getByTestId('source-conflict')).toBeVisible();
  await expect(page.getByTestId('source-editor')).toHaveValue('my local draft\n');
  await expect(page.getByTestId('source-save')).toBeDisabled();
  await page.getByText('查看磁盘上的最新内容', { exact: true }).click();
  await expect(page.getByTestId('source-conflict').locator('pre')).toHaveText('external edit\n');
  await page.getByRole('button', { name: '基于最新版本保留草稿' }).click();
  await page.getByTestId('source-save').click();
  await expect(page.getByTestId('source-save')).toBeDisabled();
  await page.getByTestId('source-reload').click();
  await expect(page.getByTestId('source-editor')).toHaveValue('my local draft\n');
  await expect(page.getByTestId('source-conflict')).not.toBeVisible();
});

test('file hash guards external edits without a workspace revision change', async ({ page }) => {
  await openSource(page);
  await openFile(page, javaPath);
  await page.getByTestId('source-editor').fill('local hash draft\n');
  await page.evaluate(async path => {
    const modulePath = '/src/bridge/index.ts';
    const { coreBridge } = await import(modulePath);
    // A filesystem writer is independent of Core revision bookkeeping.
    const file = coreBridge.sourceFiles.get(path);
    file.content = 'disk edit without revision\n';
  }, javaPath);
  await page.getByTestId('source-save').click();
  await expect(page.getByTestId('source-conflict')).toBeVisible();
  await expect(page.getByTestId('source-editor')).toHaveValue('local hash draft\n');
  await page.getByRole('button', { name: '放弃草稿并加载最新' }).click();
  await expect(page.getByTestId('source-editor')).toHaveValue('disk edit without revision\n');
  await expect(page.getByTestId('source-save')).toBeDisabled();
});

test('entrypoint evidence opens the actual file and line; find and line jump select text', async ({ page }) => {
  await openSource(page);
  await page.getByText('代码入口与引用', { exact: true }).click();
  await page.getByRole('button', { name: /example.ExampleMod.*fabric.mod.json/ }).click();
  await expect(page.getByTestId('source-editor')).toBeFocused();
  const selected = () => page.getByTestId('source-editor').evaluate((element: HTMLTextAreaElement) => element.value.slice(element.selectionStart, element.selectionEnd));
  await expect.poll(selected).toContain('onInitialize');
  await page.getByRole('textbox', { name: '在文件中查找' }).fill('registerItems');
  await page.getByRole('button', { name: '下一处', exact: true }).click();
  expect(await selected()).toBe('registerItems');
  await page.getByRole('spinbutton', { name: '跳转到行' }).fill('4');
  await page.getByRole('button', { name: '跳转', exact: true }).click();
  expect(await selected()).toContain('MOD_ID');
  await page.screenshot({ path: `../output/source-workbench-${test.info().project.name}.png` });
});

test('closing a dirty tab requires an explicit discard', async ({ page }) => {
  await openSource(page);
  await openFile(page, javaPath);
  await page.getByTestId('source-editor').fill('unsaved close draft');
  await page.getByRole('button', { name: `关闭 ${javaPath}`, exact: true }).click();
  await page.getByRole('button', { name: '继续编辑', exact: true }).click();
  await expect(page.getByTestId('source-editor')).toHaveValue('unsaved close draft');
  await page.getByRole('button', { name: `关闭 ${javaPath}`, exact: true }).click();
  await page.getByRole('button', { name: '关闭并放弃草稿' }).click();
  await expect(page.getByRole('tab', { name: /ExampleMod.java/ })).toHaveCount(0);
  await openFile(page, javaPath);
  await expect(page.getByTestId('source-editor')).not.toHaveValue('unsaved close draft');
});

test('identical file paths in different workspaces retain separate drafts', async ({ page }) => {
  await openSource(page);
  await openFile(page, javaPath);
  await page.getByTestId('source-editor').fill('first workspace draft');
  await page.evaluate(async () => {
    const scenariosPath = '/src/mock/scenarios.ts';
    const bridgePath = '/src/bridge/index.ts';
    const { SCENARIOS } = await import(scenariosPath);
    const { coreBridge } = await import(bridgePath);
    const scenario = structuredClone(SCENARIOS.ready);
    scenario.scenarioId = 'source-other-workspace';
    for (const message of scenario.initialMessages) {
      message.workspaceId = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';
      if (message.operation === 'get_workbench') message.data.workspace.id = message.workspaceId;
    }
    SCENARIOS[scenario.scenarioId] = scenario;
    coreBridge.loadScenario(scenario.scenarioId);
  });
  await openFile(page, javaPath);
  await expect(page.getByTestId('source-editor')).not.toHaveValue('first workspace draft');
  await page.getByTestId('source-editor').fill('second workspace draft');
  await page.evaluate(async () => {
    const bridgePath = '/src/bridge/index.ts';
    const { coreBridge } = await import(bridgePath);
    coreBridge.loadScenario('ready');
  });
  await expect(page.getByTestId('source-editor')).toHaveValue('first workspace draft');
});

test('pending save completion survives leaving the source view', async ({ page }) => {
  await openSource(page);
  await openFile(page, javaPath);
  await page.getByTestId('source-editor').fill('saved while another view is open\n');
  await page.evaluate(async () => {
    const bridgePath = '/src/bridge/index.ts';
    const { coreBridge } = await import(bridgePath);
    const original = coreBridge.sendCommand.bind(coreBridge);
    coreBridge.sendCommand = async command => {
      if (command.operation === 'update_workspace_file') await new Promise<void>(resolve => window.addEventListener('source-release-save', () => resolve(), { once: true }));
      return original(command);
    };
  });
  await page.getByTestId('source-save').click();
  await expect(page.getByTestId('source-save')).toHaveText('保存中…');
  await page.getByTestId('nav-assets').click();
  await page.evaluate(() => window.dispatchEvent(new Event('source-release-save')));
  await page.getByTestId('nav-source').click();
  await expect(page.getByTestId('source-editor')).toHaveValue('saved while another view is open\n');
  await expect(page.getByTestId('source-save')).toHaveText('保存');
  await expect(page.getByTestId('source-save')).toBeDisabled();
  await page.getByTestId('source-reload').click();
  await expect(page.getByTestId('source-editor')).toHaveValue('saved while another view is open\n');
});

test('source editor applies saved desktop font, line-number and ligature preferences', async ({ page }) => {
  await page.addInitScript(() => {
    let snapshot = { schemaVersion: '1.0' as const, revision: '1', entries: [
      { key: 'ide.fontSize', section: 'ide' as const, type: 'integer' as const, value: 18, min: 8, max: 96 },
      { key: 'ide.lineNumbers', section: 'ide' as const, type: 'boolean' as const, value: false },
      { key: 'ide.useLigatures', section: 'ide' as const, type: 'boolean' as const, value: true }
    ] };
    window.__COPPERBENCH_WINDOW_HOST__ = {
      systemFrame: false, preferencesAvailable: true, preferencesSchemaVersion: '1.0',
      async invoke() {},
      async getPreferences() { return structuredClone(snapshot); },
      async savePreferences(patch) {
        snapshot = { ...snapshot, revision: '2', entries: snapshot.entries.map(entry => ({ ...entry, value: patch.changes[entry.key] ?? entry.value })) };
        return structuredClone(snapshot);
      }
    };
  });
  await openSource(page);
  await openFile(page, javaPath);
  await expect(page.getByTestId('source-editor')).toHaveCSS('font-size', '18px');
  await expect(page.getByTestId('source-editor')).toHaveCSS('font-variant-ligatures', 'normal');
  await expect(page.locator('.source-line-numbers')).toBeHidden();
  await page.getByTestId('source-editor').fill('preference change keeps draft');
  await page.evaluate(async () => {
    const modulePath = '/src/bridge/windowBridge.ts';
    const { windowBridge } = await import(modulePath);
    await windowBridge.savePreferences({ revision: '1', changes: { 'ide.fontSize': 16, 'ide.lineNumbers': true, 'ide.useLigatures': false } });
  });
  await expect(page.getByTestId('source-editor')).toHaveCSS('font-size', '16px');
  await expect(page.getByTestId('source-editor')).toHaveCSS('font-variant-ligatures', 'none');
  await expect(page.locator('.source-line-numbers')).toBeVisible();
  await expect(page.getByTestId('source-editor')).toHaveValue('preference change keeps draft');
});

test('browser reload guards drafts from inactive workspaces and stops prompting once saved', async ({ page }) => {
  await openSource(page);
  await openFile(page, javaPath);
  await page.getByTestId('source-editor').fill('draft protected on refresh\n');
  await page.evaluate(async () => {
    const scenariosPath = '/src/mock/scenarios.ts';
    const bridgePath = '/src/bridge/index.ts';
    const { SCENARIOS } = await import(scenariosPath);
    const { coreBridge } = await import(bridgePath);
    const scenario = structuredClone(SCENARIOS.ready);
    scenario.scenarioId = 'source-unload-workspace';
    for (const message of scenario.initialMessages) {
      message.workspaceId = 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb';
      if (message.operation === 'get_workbench') message.data.workspace.id = message.workspaceId;
    }
    SCENARIOS[scenario.scenarioId] = scenario;
    coreBridge.loadScenario(scenario.scenarioId);
  });
  await expect(page.getByTestId('source-editor')).toHaveCount(0);
  const prompt = page.waitForEvent('dialog');
  const reload = page.reload({ timeout: 5000 }).catch(() => null);
  const dialog = await prompt;
  expect(dialog.type()).toBe('beforeunload');
  await dialog.dismiss();
  await reload;
  await page.evaluate(async () => {
    const bridgePath = '/src/bridge/index.ts';
    const { coreBridge } = await import(bridgePath);
    coreBridge.loadScenario('ready');
  });
  await expect(page.getByTestId('source-editor')).toHaveValue('draft protected on refresh\n');
  await page.getByTestId('source-save').click();
  await expect(page.getByTestId('source-save')).toBeDisabled();
  expect(await page.evaluate(() => {
    const event = new Event('beforeunload', { cancelable: true });
    window.dispatchEvent(event);
    return event.defaultPrevented;
  })).toBe(false);
});

test('desktop host receives actual dirty counts and owns the close confirmation', async ({ page }) => {
  await page.addInitScript(() => {
    const reports: number[] = [];
    Object.assign(window, { sourceGuardReports: reports, sourceGuardActions: [] });
    window.__COPPERBENCH_WINDOW_HOST__ = {
      systemFrame: false, unsavedChangesSchemaVersion: '1.0',
      async reportUnsavedChanges(count) { reports.push(count); },
      async invoke(action) { (window as unknown as { sourceGuardActions: string[] }).sourceGuardActions.push(action); }
    };
  });
  let browserPrompts = 0;
  page.on('dialog', async dialog => { browserPrompts++; await dialog.dismiss(); });
  await openSource(page);
  await openFile(page, javaPath);
  await page.getByTestId('source-editor').fill('first dirty file');
  await openFile(page, propertiesPath);
  await page.getByTestId('source-editor').fill('second dirty file');
  const reports = () => page.evaluate(() => (window as unknown as { sourceGuardReports: number[] }).sourceGuardReports);
  await expect.poll(reports).toEqual([0, 1, 2]);
  await page.getByTestId('window-close-btn').click();
  expect(await page.evaluate(() => (window as unknown as { sourceGuardActions: string[] }).sourceGuardActions)).toContain('close');
  expect(browserPrompts).toBe(0);
  await page.getByRole('button', { name: `关闭 ${propertiesPath}`, exact: true }).click();
  await page.getByRole('button', { name: '关闭并放弃草稿' }).click();
  await page.getByTestId('source-save').click();
  await expect.poll(reports).toEqual([0, 1, 2, 1, 0]);
});

test('CRLF source preserves save bytes while line jump and dirty comparison use LF', async ({ page }) => {
  await openSource(page);
  const original = 'package example;\r\nclass ExampleMod {\r\n    int value = 1;\r\n}\r\n';
  await page.evaluate(async ({ path, original }) => {
    const bridgePath = '/src/bridge/index.ts';
    const { coreBridge } = await import(bridgePath);
    coreBridge.sourceFiles.get(path).content = original;
    const send = coreBridge.sendCommand.bind(coreBridge);
    const requests: string[] = [];
    Object.assign(window, { crlfSaveRequests: requests });
    coreBridge.sendCommand = command => {
      if (command.operation === 'update_workspace_file') requests.push(command.payload.content);
      return send(command);
    };
  }, { path: javaPath, original });
  await openFile(page, javaPath);
  const editor = page.getByTestId('source-editor');
  const normalized = original.replace(/\r\n/g, '\n');
  await expect(editor).toHaveValue(normalized);
  await expect(page.getByTestId('source-save')).toBeDisabled();
  await page.getByRole('spinbutton', { name: '跳转到行' }).fill('3');
  await page.getByRole('button', { name: '跳转', exact: true }).click();
  expect(await editor.evaluate((input: HTMLTextAreaElement) => input.value.slice(input.selectionStart, input.selectionEnd))).toBe('    int value = 1;');
  await editor.fill(normalized + '// temporary\n');
  await editor.fill(normalized);
  await expect(page.getByTestId('source-save')).toBeDisabled();
  await editor.fill(normalized + '// kept\n');
  await page.getByTestId('source-save').click();
  await expect(page.getByTestId('source-save')).toBeDisabled();
  expect(await page.evaluate(() => (window as unknown as { crlfSaveRequests: string[] }).crlfSaveRequests)).toEqual([original + '// kept\r\n']);
  await page.getByTestId('source-reload').click();
  await expect(editor).toHaveValue(normalized + '// kept\n');
  await expect(page.getByTestId('source-save')).toBeDisabled();
  await expect(page.getByRole('tab', { name: /ExampleMod.java/ })).not.toContainText('●');

  // Resolve a genuine hash conflict against a fresh CRLF baseline, retaining
  // normalized editor text but sending the latest file's newline style.
  await editor.fill(normalized + '// local conflict draft\n');
  await page.evaluate(async path => {
    const bridgePath = '/src/bridge/index.ts';
    const { coreBridge } = await import(bridgePath);
    coreBridge.sourceFiles.get(path).content = 'external first line\r\nexternal second line\r\n';
  }, javaPath);
  await page.getByTestId('source-save').click();
  await expect(page.getByTestId('source-conflict')).toBeVisible();
  await page.getByRole('button', { name: '基于最新版本保留草稿' }).click();
  await page.getByTestId('source-save').click();
  await expect(page.getByTestId('source-save')).toBeDisabled();
  expect((await page.evaluate(() => (window as unknown as { crlfSaveRequests: string[] }).crlfSaveRequests)).at(-1)).toBe(original + '// local conflict draft\r\n');
  await page.getByTestId('source-reload').click();
  await expect(editor).toHaveValue(normalized + '// local conflict draft\n');
  await expect(page.getByTestId('source-save')).toBeDisabled();
});

test('320px source view switches files and editor without squeezing the editor or losing drafts', async ({ page }) => {
  await page.setViewportSize({ width: 320, height: 740 });
  await openSource(page);
  await expect(page.locator('.source-explorer')).toBeVisible();
  await openFile(page, javaPath);
  await expect(page.locator('.source-explorer')).toBeHidden();
  await expect(page.getByTestId('source-editor')).toBeVisible();
  expect((await page.getByTestId('source-editor').boundingBox())!.width).toBeGreaterThan(185);
  await page.getByTestId('source-editor').fill('narrow Java draft');
  await page.getByTestId('source-files-toggle').focus();
  await page.keyboard.press('Enter');
  await expect(page.locator('.source-explorer')).toBeVisible();
  await expect(page.getByTestId('source-editor')).toBeHidden();
  await page.getByTestId('source-editor-toggle').click();
  await expect(page.getByTestId('source-editor')).toHaveValue('narrow Java draft');
  await page.getByTestId('source-files-toggle').click();
  await openFile(page, propertiesPath);
  await page.getByTestId('source-editor').fill('narrow config draft');
  await page.getByTestId('source-files-toggle').click();
  await openFile(page, javaPath);
  await expect(page.getByTestId('source-editor')).toHaveValue('narrow Java draft');
  await page.getByRole('tab', { name: /gradle.properties/ }).click();
  await expect(page.getByTestId('source-editor')).toHaveValue('narrow config draft');
  await page.screenshot({ path: '../output/source-workbench-320.png' });
});
