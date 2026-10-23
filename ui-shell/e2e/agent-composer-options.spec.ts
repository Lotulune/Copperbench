import { test, expect, type Page } from '@playwright/test';

test.use({ storageState: { cookies: [], origins: [] } });
const pixel = 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a7ioAAAAASUVORK5CYII=';

async function fixture(page: Page, options: { dark?: boolean; english?: boolean; failure?: boolean; nativeSelection?: boolean;
  uploadFailure?: 'begin' | 'chunk' | 'commit' | 'backend' | 'cleanup' | 'select' } = {}) {
  await page.addInitScript(({ options, pixel }) => {
    localStorage.setItem('copperbench.workbench.mode', 'simple');
    localStorage.setItem('copperbench.ui.locale', options.english ? 'en' : 'zh');
    localStorage.setItem('copperbench.theme', options.dark ? 'dark' : 'light');
    const calls: any[] = [], uploads = new Map<string, any>(), sessions: any[] = [], transcripts = new Map<string, any[]>();
    let reject = false, target = '', deleteFails = false, retryFails = false, cleanupFails = false;
    const snapshot: any = { state: 'idle', sequence: 1, conversationId: null, provider: null, sessions, messages: [], approvals: [],
      providers: [{ id: 'codex', label: 'Codex CLI', installed: true, executable: '/fixture/codex', setupUrl: 'https://example.com', loginAvailable: true,
        modelCatalog: options.failure ? { state: 'failed', models: [], error: 'Catalog unavailable' } : { state: 'ready', models: [{ id: 'fixture-model', label: 'Fixture model' }], defaultModel: 'fixture-model' } }] };
    (window as any).composerFixture = { calls, uploads, snapshot, reject: (value: boolean) => { reject = value; }, target: () => target,
      deleteFailure: (value: boolean) => { deleteFails = value; }, retryFailure: (value: boolean) => { retryFails = value; },
      cleanupFailure: (value: boolean) => { cleanupFails = value; },
      seedHistory: () => {
        const session = { id: crypto.randomUUID(), provider: 'codex', model: 'fixture-model', title: 'Earlier conversation', updatedAt: Date.now() };
        sessions.push(session); transcripts.set(session.id, [{ id: crypto.randomUUID(), role: 'user', text: 'Earlier request', at: Date.now() }]);
        snapshot.sequence++; return session.id;
      },
      nativeDrop: () => { const item = { id: crypto.randomUUID(), name: 'native-drop.txt', size: 10, mediaType: 'application/octet-stream' }; uploads.set(item.id, item);
        window.dispatchEvent(new CustomEvent('copperbench:agent-attachments', { detail: { ok: true, target, attachments: [item] } })); },
      unsupported: () => { snapshot.state = 'failed'; snapshot.error = 'Unsupported effort'; snapshot.messages.at(-1).delivery = 'failed'; snapshot.sequence++; } };
    window.__COPPERBENCH_AGENT_HOST__ = { nativeFileSelection: options.nativeSelection, async invoke(request: any) {
      calls.push(request);
      if (request.operation === 'attachmentBegin' && options.uploadFailure === 'begin') return { ok: true };
      if (request.operation === 'attachmentChunk' && options.uploadFailure === 'chunk') return { ok: true, offset: 0 };
      if (request.operation === 'attachmentCommit' && options.uploadFailure === 'commit') return { ok: true };
      if (request.operation === 'attachmentChunk' && options.uploadFailure === 'backend'
        || request.operation === 'attachmentDiscard' && options.uploadFailure === 'cleanup'
        || request.operation === 'attachmentSelect' && options.uploadFailure === 'select') return { ok: false, error: '附件操作失败：fixture.txt',
          errorText: { key: 'attachment.fixture', zh: '附件操作失败：{name}', en: 'Attachment operation failed: {name}', args: { name: 'fixture.txt' } } };
      if (request.operation === 'attachmentTarget') { target = request.target; return { ok: true }; }
      if (request.operation === 'attachmentSelect') { const item = { id: crypto.randomUUID(), name: 'native-selected.txt', size: 10, mediaType: 'application/octet-stream' };
        uploads.set(item.id, item); return { ok: true, attachments: [item] }; }
      if (request.operation === 'attachmentBegin') { const id = crypto.randomUUID(); uploads.set(id, { id, name: request.name, size: request.size, offset: 0,
        mediaType: request.name.endsWith('.png') ? 'image/png' : 'application/octet-stream' }); return { ok: true, id, offset: 0 }; }
      if (request.operation === 'attachmentChunk') { const item = uploads.get(request.id); item.offset += atob(request.data).length; return { ok: true, id: item.id, offset: item.offset }; }
      if (request.operation === 'attachmentCommit') { const { offset, ...attachment } = uploads.get(request.id); return { ok: true, attachment }; }
      if (request.operation === 'attachmentDiscard') return { ok: true };
      if (request.operation === 'attachmentPreview') return { ok: true, dataUrl: `data:image/png;base64,${pixel}` };
      if (request.operation === 'new') {
        const session = { id: crypto.randomUUID(), provider: request.provider, model: request.model ?? null, effort: request.effort, permission: request.permission, title: 'Conversation', updatedAt: Date.now() };
        sessions.push(session); Object.assign(snapshot, { conversationId: session.id, provider: session.provider, model: session.model, effort: session.effort, permission: session.permission, messages: [] });
        transcripts.set(session.id, snapshot.messages);
      }
      if (request.operation === 'send') {
        if (!snapshot.conversationId) {
          const session = { id: crypto.randomUUID(), provider: request.provider, model: request.model, effort: request.effort, permission: request.permission, title: 'First message', updatedAt: Date.now() };
          sessions.push(session); Object.assign(snapshot, { conversationId: session.id, provider: session.provider, model: session.model, effort: session.effort, permission: session.permission });
          transcripts.set(session.id, snapshot.messages);
        }
        if (reject) { snapshot.state = 'starting'; snapshot.messages.push({ id: crypto.randomUUID(), role: 'user', text: request.text, at: Date.now(), delivery: 'pending' }); }
        else {
          snapshot.state = 'idle'; snapshot.effort = request.effort; snapshot.permission = request.permission;
          snapshot.messages.push({ id: crypto.randomUUID(), role: 'user', text: request.text, at: Date.now(), delivery: 'sent', attachments: request.attachments.map((id: string) => {
            const { offset, ...item } = uploads.get(id); return item;
          }) });
        }
      }
      if (request.operation === 'stop') {
        snapshot.state = 'idle'; snapshot.messages.at(-1).delivery = 'failed';
      }
      if (request.operation === 'select') {
        const session = sessions.find(item => item.id === request.conversationId);
        Object.assign(snapshot, { conversationId: session.id, provider: session.provider, model: session.model, state: 'idle', messages: transcripts.get(session.id) });
      }
      if (request.operation === 'deleteConversations') {
        delete snapshot.historyResult;
        if (deleteFails) snapshot.error = 'History write failed';
        else {
          for (let i = sessions.length - 1; i >= 0; i--) if (request.conversationIds.includes(sessions[i].id)) sessions.splice(i, 1);
          if (request.conversationIds.includes(snapshot.conversationId)) Object.assign(snapshot, { conversationId: null, provider: null, model: null, messages: [] });
          if (cleanupFails) snapshot.error = 'Conversation deleted; attachment cleanup failed'; else delete snapshot.error;
          snapshot.historyResult = { operation: request.operation };
        }
      }
      if (request.operation === 'retryHistory') {
        delete snapshot.historyResult;
        if (retryFails) snapshot.error = 'Disk is full';
        else { delete snapshot.error; snapshot.storageBlocked = false; snapshot.historyResult = { operation: request.operation }; }
      }
      return { ...snapshot, sequence: ++snapshot.sequence };
    } };
  }, { options, pixel });
  await page.goto('/'); await expect(page.getByTestId('simple-prompt')).toBeVisible();
}

async function expectErrorInBothLocales(page: Page, error: string, translated: string) {
  const surface = page.getByTestId('creation-shell');
  // Error chips and batch errors both remain in the same mounted creation surface.
  await expect(surface).toContainText(error);
  await page.getByTestId('ui-language-select').selectOption('zh');
  await expect(surface).toContainText(translated);
  await expect(surface).not.toContainText(error);
  await page.getByTestId('ui-language-select').selectOption('en');
  await expect(surface).toContainText(error);
  await expect(page.getByTestId('simple-prompt')).toHaveValue('Keep the attachment draft');
}

for (const kind of ['empty', 'oversized', 'count', 'total'] as const) test(`attachment ${kind} validation follows language changes`, async ({ page }) => {
  await fixture(page, { english: true });
  await page.getByTestId('simple-prompt').fill('Keep the attachment draft');
  const file = (size: number, index = 0) => ({ name: `notes-${index}.txt`, mimeType: 'text/plain', buffer: Buffer.alloc(size, 65) });
  await page.getByTestId('agent-file-input').setInputFiles(kind === 'empty' ? file(0) : kind === 'oversized' ? file(10 * 1024 * 1024 + 1)
    : kind === 'count' ? Array.from({ length: 9 }, (_, i) => file(1, i)) : Array.from({ length: 3 }, (_, i) => file(7 * 1024 * 1024, i)));
  const copy = kind === 'count' ? ['Attach up to 8 files per message.', '每条消息最多 8 个附件。']
    : kind === 'total' ? ['Attachments must total 20 MiB or less.', '附件总大小不能超过 20 MiB。']
    : ['Each file must be non-empty and no larger than 10 MiB.', '附件须非空且不超过 10 MiB。'];
  await expectErrorInBothLocales(page, copy[0], copy[1]);
});

for (const uploadFailure of ['begin', 'chunk', 'commit', 'backend', 'cleanup', 'select'] as const)
  test(`attachment ${uploadFailure} failure follows language changes without losing drafts`, async ({ page }, info) => {
    await fixture(page, { english: true, uploadFailure, nativeSelection: uploadFailure === 'select' });
    await page.getByTestId('simple-prompt').fill('Keep the attachment draft');
    if (uploadFailure === 'select') await page.getByRole('button', { name: 'Add files and images', exact: true }).click();
    else await page.getByTestId('agent-file-input').setInputFiles({ name: 'fixture.txt', mimeType: 'text/plain', buffer: Buffer.from('fixture') });
    if (uploadFailure === 'cleanup') {
      await expect(page.locator('.creation-composer [data-state="ready"]')).toHaveCount(1);
      await page.getByRole('button', { name: 'Remove fixture.txt', exact: true }).click();
    }
    const copy = uploadFailure === 'begin' ? ['The attachment upload did not start.', '附件上传未能开始。']
      : uploadFailure === 'chunk' ? ['The attachment chunk was not acknowledged.', '附件分块未被确认。']
      : uploadFailure === 'commit' ? ['The attachment was not saved completely.', '附件未保存完整。']
      : ['Attachment operation failed: fixture.txt', '附件操作失败：fixture.txt'];
    await expectErrorInBothLocales(page, copy[0], copy[1]);
    if (uploadFailure === 'backend') await page.screenshot({ path: info.outputPath('attachment-error-english.png') });
    expect(await page.evaluate(() => (window as any).composerFixture.calls.filter((call: any) => call.operation === 'send'))).toHaveLength(0);
  });

async function hasDrafts(page: Page) {
  return page.evaluate(async () => {
    const url = performance.getEntriesByType('resource').map(entry => entry.name)
      .find(value => new URL(value).pathname === '/src/hooks/unsavedDraftGuard.ts') ?? '/src/hooks/unsavedDraftGuard.ts';
    return (await import(/* @vite-ignore */ url)).hasUnsavedDrafts();
  });
}

test('stopping before model admission keeps the draft when another history is opened', async ({ page }) => {
  await fixture(page);
  const earlier = await page.evaluate(() => (window as any).composerFixture.seedHistory());
  await page.getByTestId('agent-file-input').setInputFiles({ name: 'keep.txt', mimeType: 'text/plain', buffer: Buffer.from('keep') });
  await expect(page.locator('.creation-composer [data-state="ready"]')).toHaveCount(1);
  await page.getByTestId('simple-prompt').fill('Keep the stopped request');
  await page.evaluate(() => (window as any).composerFixture.reject(true));
  await page.getByTestId('simple-send').click();
  await expect(page.getByTestId('simple-stop')).toBeVisible();
  const stopped = await page.evaluate(() => (window as any).composerFixture.snapshot.conversationId);
  await page.getByTestId('simple-stop').click();
  await expect(page.getByTestId('simple-send')).toBeEnabled();
  await page.getByTestId(`simple-conversation-${earlier}`).click();
  await expect(page.getByRole('log')).toContainText('Earlier request');
  await page.getByTestId(`simple-conversation-${stopped}`).click();
  await expect(page.getByTestId('simple-prompt')).toHaveValue('Keep the stopped request');
  await expect(page.locator('.creation-composer [data-state="ready"]')).toHaveCount(1);
  expect(await page.evaluate(() => (window as any).composerFixture.calls.filter((item: any) => item.operation === 'send'))).toHaveLength(1);
});

test('confirmed deletion clears only deleted conversation drafts after persistence succeeds', async ({ page }, testInfo) => {
  await fixture(page, { english: true });
  for (const name of ['first', 'second']) {
    await page.getByTestId('simple-new-chat').click();
    await page.getByTestId('simple-prompt').fill(`${name} unsent draft`);
    await page.getByTestId('agent-file-input').setInputFiles({ name: `${name}.txt`, mimeType: 'text/plain', buffer: Buffer.from(name) });
    await expect(page.locator('.creation-composer [data-state="ready"]')).toHaveCount(1);
  }
  await page.getByTestId('simple-manage-conversations').click();
  const dialog = page.getByTestId('agent-history-dialog');
  for (const checkbox of await dialog.getByRole('checkbox').all()) await checkbox.check();
  await dialog.getByRole('button', { name: 'Delete selected (2)', exact: true }).click();
  await expect(page.getByTestId('confirm-dialog')).toContainText('unsent text and attachments');
  await page.getByRole('button', { name: 'Cancel', exact: true }).click();
  expect(await hasDrafts(page)).toBe(true);
  await page.evaluate(() => (window as any).composerFixture.deleteFailure(true));
  await dialog.getByRole('button', { name: 'Delete selected (2)', exact: true }).click();
  await page.getByRole('button', { name: 'Delete selected', exact: true }).click();
  await expect(dialog).toContainText('History write failed');
  expect(await hasDrafts(page)).toBe(true);
  expect(await page.evaluate(() => (window as any).composerFixture.calls.filter((item: any) => item.operation === 'attachmentDiscard'))).toHaveLength(0);
  await page.evaluate(() => { (window as any).composerFixture.deleteFailure(false); (window as any).composerFixture.cleanupFailure(true); });
  for (const checkbox of await dialog.getByRole('checkbox').all()) await checkbox.check();
  await dialog.getByRole('button', { name: 'Delete selected (2)', exact: true }).click();
  await expect(page.getByTestId('confirm-dialog')).toBeVisible();
  await page.screenshot({ path: testInfo.outputPath('delete-drafts-confirmation.png'), animations: 'disabled' });
  await page.getByRole('button', { name: 'Delete selected', exact: true }).click();
  await expect(dialog).toContainText('Conversation deleted; attachment cleanup failed');
  await dialog.getByRole('button', { name: 'Done', exact: true }).click();
  await expect(page.getByTestId('simple-prompt')).toHaveValue('');
  await expect(page.locator('.creation-composer [data-testid="agent-attachment"]')).toHaveCount(0);
  await expect.poll(() => hasDrafts(page)).toBe(false);
  expect(await page.evaluate(() => (window as any).composerFixture.calls.filter((item: any) => item.operation === 'attachmentDiscard'))).toHaveLength(2);
});

test('retrying history reports failure and recovery inside the management dialog', async ({ page }) => {
  await fixture(page, { english: true });
  await page.evaluate(() => { const fixture = (window as any).composerFixture; fixture.snapshot.storageBlocked = true; fixture.snapshot.sequence++; fixture.retryFailure(true); });
  await page.getByTestId('simple-manage-conversations').click();
  const dialog = page.getByTestId('agent-history-dialog');
  await dialog.getByRole('button', { name: 'Retry saving history', exact: true }).click();
  await expect(dialog).toContainText('Disk is full');
  await page.evaluate(() => (window as any).composerFixture.retryFailure(false));
  await dialog.getByRole('button', { name: 'Retry saving history', exact: true }).click();
  await expect(dialog).toContainText('History saved. You can continue the conversation.');
  await expect(dialog.getByRole('button', { name: 'Retry saving history', exact: true })).toHaveCount(0);
});

test('manual input works without a catalog and effort stays immediately to the model right', async ({ page }) => {
  await fixture(page, { failure: true, english: true });
  await page.getByTestId('simple-prompt').fill('Keep this draft');
  await page.getByTestId('simple-model').click();
  await page.getByRole('button', { name: 'Enter a model ID manually', exact: true }).click();
  await page.getByRole('textbox', { name: 'Model ID to use', exact: true }).fill('deepseek-ai/custom');
  await page.getByRole('button', { name: 'Use model', exact: true }).click();
  await expect(page.getByTestId('simple-prompt')).toHaveValue('Keep this draft');
  await expect(page.getByTestId('simple-model')).toHaveAttribute('data-model-value', 'deepseek-ai/custom');
  await page.getByTestId('simple-effort').click();
  await expect(page.getByRole('listbox', { name: 'Reasoning effort' }).getByRole('option')).toHaveText(['low', 'medium', 'high', 'xhigh', 'max']);
  const model = await page.getByTestId('simple-model').boundingBox(), effort = await page.getByTestId('simple-effort').boundingBox();
  expect(effort!.x).toBeGreaterThanOrEqual(model!.x + model!.width);
  await expect(page.locator('#creation-effort-hint')).toContainText('Not all models support every level');
  await page.getByRole('option', { name: 'max', exact: true }).click(); await page.getByTestId('simple-send').click();
  expect(await page.evaluate(() => (window as any).composerFixture.calls.findLast((call: any) => call.operation === 'send')))
    .toMatchObject({ model: 'deepseek-ai/custom', effort: 'max', permission: 'default' });
});

test('file selection, pasted images, browser drops and native drops stay in the draft until sending', async ({ page }) => {
  await fixture(page);
  await page.getByTestId('agent-file-input').setInputFiles({ name: 'notes.md', mimeType: 'text/markdown', buffer: Buffer.from('a'.repeat(300000)) });
  await expect(page.locator('.creation-composer [data-state="ready"]')).toHaveCount(1);
  await page.getByTestId('simple-prompt').evaluate((element, png) => {
    const bytes = Uint8Array.from(atob(png), c => c.charCodeAt(0)); const clipboard = new DataTransfer();
    clipboard.items.add(new File([bytes], 'clipboard.png', { type: 'image/png' }));
    element.dispatchEvent(new ClipboardEvent('paste', { bubbles: true, cancelable: true, clipboardData: clipboard }));
  }, pixel);
  await page.locator('.creation-composer').evaluate(element => {
    const transfer = new DataTransfer(); transfer.items.add(new File(['drop'], 'dropped.txt', { type: 'text/plain' }));
    element.dispatchEvent(new DragEvent('drop', { bubbles: true, cancelable: true, dataTransfer: transfer }));
  });
  await expect(page.locator('.creation-composer [data-state="ready"]')).toHaveCount(3);
  await page.evaluate(() => (window as any).composerFixture.nativeDrop());
  await expect(page.locator('.creation-composer [data-state="ready"]')).toHaveCount(4);
  await expect(page.locator('.creation-composer img[alt="clipboard.png"]')).toBeVisible();
  expect(await page.evaluate(() => (window as any).composerFixture.calls.some((call: any) => call.operation === 'send'))).toBe(false);
  await page.getByRole('button', { name: '移除 dropped.txt', exact: true }).click();
  await page.getByTestId('simple-model').click();
  await page.getByRole('combobox', { name: '搜索或输入模型 ID' }).fill('new-model');
  await page.getByRole('combobox', { name: '搜索或输入模型 ID' }).press('Enter');
  await expect(page.locator('.creation-composer [data-state="ready"]')).toHaveCount(3);
  await page.getByTestId('simple-send').click();
  await expect(page.locator('.creation-composer [data-testid="agent-attachment"]')).toHaveCount(0);
  await expect(page.getByRole('log').getByTestId('agent-attachment')).toHaveCount(3);
  const send = await page.evaluate(() => (window as any).composerFixture.calls.findLast((call: any) => call.operation === 'send'));
  expect(send.attachments).toHaveLength(3); expect(send.text).toBe('');
});

test('automatic approval requires confirmation and rejected preflight retains draft and attachment', async ({ page }) => {
  await fixture(page);
  await page.getByTestId('simple-permission').click();
  await page.getByRole('option', { name: '自动批准', exact: true }).click();
  await page.getByRole('button', { name: '取消', exact: true }).click();
  await expect(page.getByTestId('simple-permission')).toHaveAttribute('data-option-value', 'default');
  await expect(page.getByTestId('simple-permission')).toBeFocused();
  await page.getByTestId('simple-permission').click();
  await page.getByRole('option', { name: '自动批准', exact: true }).click();
  await page.getByRole('button', { name: '启用自动批准', exact: true }).click();
  await expect(page.getByTestId('simple-permission')).toHaveAttribute('data-option-value', 'auto-approve');
  await page.getByTestId('simple-permission').click();
  await expect(page.getByRole('option', { name: '自动批准', exact: true })).toHaveAttribute('aria-selected', 'true');
  await page.getByTestId('simple-permission').press('Escape');
  await page.getByTestId('agent-file-input').setInputFiles({ name: 'keep.txt', mimeType: 'text/plain', buffer: Buffer.from('keep') });
  await expect(page.locator('.creation-composer [data-state="ready"]')).toHaveCount(1);
  await page.getByTestId('simple-prompt').fill('Do not lose this draft');
  await page.evaluate(() => (window as any).composerFixture.reject(true));
  await page.getByTestId('simple-send').click();
  await expect(page.getByTestId('simple-effort')).toBeDisabled();
  await expect(page.getByTestId('simple-permission')).toBeDisabled();
  expect(await page.evaluate(() => (window as any).composerFixture.calls.findLast((call: any) => call.operation === 'send')))
    .toMatchObject({ permission: 'auto-approve' });
  await page.evaluate(() => (window as any).composerFixture.unsupported());
  await expect(page.getByTestId('simple-agent-error')).toContainText('Unsupported effort');
  await expect(page.getByTestId('simple-prompt')).toHaveValue('Do not lose this draft');
  await expect(page.locator('.creation-composer [data-state="ready"]')).toHaveCount(1);
});

test('turn menus support keyboard selection and dismiss without changing the draft', async ({ page }) => {
  await fixture(page, { english: true });
  const effort = page.getByTestId('simple-effort'), permission = page.getByTestId('simple-permission');
  const menu = page.getByRole('listbox', { name: 'Reasoning effort' });
  await page.getByTestId('simple-prompt').fill('Keep this draft');
  await expect(effort).toHaveRole('combobox');
  await expect(effort).toHaveAccessibleDescription(/Choose an effort supported by your model/);
  await effort.focus(); await effort.press('ArrowUp');
  await expect(menu.getByRole('option', { name: 'medium', exact: true })).toHaveAttribute('data-highlighted', 'true');
  await effort.press('ArrowDown');
  await expect(menu.getByRole('option', { name: 'high', exact: true })).toHaveAttribute('data-highlighted', 'true');
  await effort.press('Home');
  await expect(menu.getByRole('option', { name: 'low', exact: true })).toHaveAttribute('data-highlighted', 'true');
  await effort.press('End');
  await expect(menu.getByRole('option', { name: 'max', exact: true })).toHaveAttribute('data-highlighted', 'true');
  await effort.press('Escape');
  await expect(menu).toHaveCount(0); await expect(effort).toBeFocused();
  await expect(effort).toHaveAttribute('data-option-value', 'medium');
  await effort.press('Space'); await effort.press('h'); await effort.press('Enter');
  await expect(effort).toHaveAttribute('data-option-value', 'high');
  await expect(menu).toHaveCount(0); await expect(effort).toBeFocused();
  await effort.click(); await effort.press('Home'); await effort.press('Space');
  await expect(effort).toHaveAttribute('data-option-value', 'low');
  await effort.click(); await effort.press('Tab');
  await expect(menu).toHaveCount(0); await expect(permission).toBeFocused();
  await effort.click(); await permission.click();
  await expect(menu).toHaveCount(0);
  await expect(page.getByRole('listbox', { name: 'Execution permissions' })).toBeVisible();
  await permission.press('Escape'); await expect(permission).toBeFocused();
  await effort.click(); await page.getByTestId('simple-model').click();
  await expect(menu).toHaveCount(0);
  await expect(page.getByRole('dialog', { name: 'Choose a model' })).toBeVisible();
  await page.keyboard.press('Escape'); await effort.click();
  // The upward menu covers the textarea center; click its exposed leading edge.
  await page.getByTestId('simple-prompt').click({ position: { x: 16, y: 16 } }); await expect(menu).toHaveCount(0);
  await effort.click();
  await page.evaluate(() => { const snapshot = (window as any).composerFixture.snapshot; snapshot.state = 'running'; snapshot.sequence++; });
  await expect(effort).toBeDisabled(); await expect(permission).toBeDisabled(); await expect(menu).toHaveCount(0);
  await expect(page.getByTestId('simple-prompt')).toHaveValue('Keep this draft');
  expect(await page.evaluate(() => (window as any).composerFixture.calls.filter((call: any) => call.operation === 'send'))).toHaveLength(0);
});

for (const dark of [false, true]) for (const compact of [false, true])
  test(`turn menus match the model picker and open upward (${dark ? 'dark' : 'light'}, ${compact ? 'compact' : 'desktop'})`, async ({ page }, info) => {
    if (compact) await page.setViewportSize({ width: 500, height: 600 });
    await fixture(page, { dark, english: compact });
    const triggerStyle = (element: HTMLElement | SVGElement) => {
      const style = getComputedStyle(element);
      return [style.fontSize, style.color, style.padding, style.borderRadius, style.minHeight];
    };
    const menuStyle = (element: HTMLElement | SVGElement) => {
      const style = getComputedStyle(element);
      return [style.backgroundColor, style.color, style.borderColor, style.borderRadius, style.boxShadow, style.padding, style.font];
    };
    const model = page.getByTestId('simple-model');
    const expectedTriggerStyle = await model.evaluate(triggerStyle);
    await model.click();
    const modelMenu = page.getByRole('dialog', { name: compact ? 'Choose a model' : '选择模型' });
    const expectedMenuStyle = await modelMenu.evaluate(menuStyle);
    const selectedBackground = await modelMenu.getByRole('option', { selected: true }).evaluate(element => getComputedStyle(element).backgroundColor);
    await page.keyboard.press('Escape');
    for (const [testId, label, selection] of [
      ['simple-effort', compact ? 'Reasoning effort' : '思考强度', 'medium'],
      ['simple-permission', compact ? 'Execution permissions' : '执行权限', compact ? 'Default permissions' : '默认权限']
    ]) {
      const trigger = page.getByTestId(testId);
      await expect(trigger).toHaveRole('combobox');
      expect(await trigger.evaluate(triggerStyle)).toEqual(expectedTriggerStyle);
      await trigger.click();
      const menu = page.getByRole('listbox', { name: label, exact: true });
      await expect(menu).toBeVisible(); await expect(trigger).toHaveAttribute('aria-expanded', 'true');
      const selected = menu.getByRole('option', { name: selection, exact: true });
      await expect(selected).toHaveAttribute('aria-selected', 'true');
      expect(await selected.evaluate(element => getComputedStyle(element).backgroundColor)).toBe(selectedBackground);
      expect(await menu.evaluate(menuStyle)).toEqual(expectedMenuStyle);
      const bounds = (await menu.boundingBox())!, anchor = (await trigger.boundingBox())!, viewport = page.viewportSize()!;
      expect(bounds.y + bounds.height).toBeLessThanOrEqual(anchor.y - 5);
      expect(bounds.y).toBeGreaterThanOrEqual(12); expect(bounds.x).toBeGreaterThanOrEqual(12);
      expect(bounds.x + bounds.width).toBeLessThanOrEqual(viewport.width - 12);
      for (const option of await menu.getByRole('option').all()) await expect(option).toBeInViewport();
      await page.screenshot({ path: info.outputPath(`${testId}-menu.png`), animations: 'disabled' });
      await trigger.press('Escape'); await expect(trigger).toBeFocused();
    }
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  });

test('desktop file button uses the native selector and keeps attachments before send', async ({ page }) => {
  await fixture(page, { nativeSelection: true });
  await page.getByRole('button', { name: '添加文件和图片', exact: true }).click();
  await expect(page.locator('.creation-composer [data-state="ready"]')).toHaveCount(1);
  await expect(page.locator('.creation-composer')).toContainText('native-selected.txt');
  expect(await page.evaluate(() => (window as any).composerFixture.calls.filter((call: any) => ['attachmentBegin', 'send'].includes(call.operation)))).toHaveLength(0);
  await page.getByTestId('simple-send').click();
  await expect(page.getByRole('log')).toContainText('native-selected.txt');
});

for (const dark of [false, true]) test(`compact composer and manual model input remain reachable (${dark ? 'dark' : 'light'})`, async ({ page }, testInfo) => {
  await page.setViewportSize({ width: 500, height: 600 }); await fixture(page, { dark, failure: true });
  await page.getByTestId('agent-file-input').setInputFiles({ name: 'long-attachment-filename-for-small-windows.txt', mimeType: 'text/plain', buffer: Buffer.from('preview') });
  await expect(page.locator('.creation-composer [data-state="ready"]')).toHaveCount(1);
  await page.getByTestId('simple-prompt').fill('依据附件创建新的方块');
  await page.getByTestId('simple-model').click(); await page.getByRole('button', { name: '手动输入模型 ID', exact: true }).click();
  await expect(page.getByRole('textbox', { name: '想使用的模型 ID' })).toBeInViewport();
  await page.getByRole('textbox', { name: '想使用的模型 ID' }).fill('custom-model');
  await page.getByRole('button', { name: '使用模型', exact: true }).click();
  await page.getByTestId('simple-send').scrollIntoViewIfNeeded();
  await expect(page.getByTestId('simple-send')).toBeInViewport();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.screenshot({ path: testInfo.outputPath('composer-options.png') });
});

test('native drops belong to the selected composer across mode changes', async ({ page }) => {
  await fixture(page, { english: true });
  await expect.poll(() => page.evaluate(() => (window as any).composerFixture.target())).not.toBe('');
  const simpleTarget = await page.evaluate(() => (window as any).composerFixture.target());
  await page.evaluate(() => (window as any).composerFixture.nativeDrop());
  await expect(page.locator('.creation-composer [data-state="ready"]')).toHaveCount(1);
  await page.getByTestId('workbench-mode-control').hover(); await page.getByTestId('mode-professional').click();
  await page.getByTestId('professional-agent-toggle').click();
  const professional = page.getByTestId('professional-agent-panel');
  await expect(professional.locator('[data-state="ready"]')).toHaveCount(0);
  await expect.poll(() => page.evaluate(() => (window as any).composerFixture.target())).not.toBe(simpleTarget);
  await expect.poll(() => page.evaluate(() => (window as any).composerFixture.target())).not.toBe('');
  await page.evaluate(() => (window as any).composerFixture.nativeDrop());
  await expect(professional.locator('[data-state="ready"]')).toHaveCount(1);
  await page.getByTestId('workbench-mode-control').hover(); await page.getByTestId('mode-simple').click();
  await expect.poll(() => page.evaluate(() => (window as any).composerFixture.target())).toBe(simpleTarget);
  await page.evaluate(() => (window as any).composerFixture.nativeDrop());
  await expect(page.locator('.creation-composer [data-state="ready"]')).toHaveCount(2);
  await page.getByTestId('workbench-mode-control').hover(); await page.getByTestId('mode-professional').click();
  await expect(professional.locator('[data-state="ready"]')).toHaveCount(1);
});

test('simple composer uses model efforts and Codex permission profiles from the shared session', async ({ page }) => {
  await fixture(page, { english: true });
  await page.evaluate(() => {
    const { snapshot } = (window as any).composerFixture;
    Object.assign(snapshot.providers[0].modelCatalog.models[0], { efforts: ['low', 'high'], defaultEffort: 'high' });
    snapshot.modelCatalogs = { codex: { state: 'ready', models: [{ id: 'fixture-model', label: 'Fixture model', efforts: ['low', 'high'], defaultEffort: 'high' }], permissionProfiles: ['workspace', 'read-only', 'full-access', 'auto-approve'] } };
    snapshot.sequence++;
  });
  await expect(page.getByTestId('simple-effort')).toHaveAttribute('aria-label', 'Reasoning effort: high');
  await page.getByTestId('simple-effort').click();
  await expect(page.getByRole('listbox', { name: 'Reasoning effort' }).getByRole('option')).toHaveCount(2);
  await page.getByRole('option', { name: 'low', exact: true }).click();
  await page.getByTestId('simple-permission').click();
  await page.getByRole('option', { name: 'Full access', exact: true }).click();
  await page.getByTestId('simple-prompt').fill('Keep the chosen model settings');
  await page.getByTestId('simple-send').click();
  await expect.poll(() => page.evaluate(() => (window as any).composerFixture.calls.find((call: any) => call.operation === 'send')))
    .toMatchObject({ model: 'fixture-model', effort: 'low', permission: 'full-access' });
});

test('simple composer stops the shared verification and preserves final result collection', async ({ page }) => {
  await fixture(page, { english: true });
  await page.evaluate(() => {
    const { snapshot } = (window as any).composerFixture;
    snapshot.activeVerification = { runId: 'verification-run', cancelling: false };
    snapshot.sequence++;
  });
  await expect(page.getByTestId('simple-stop')).toHaveAttribute('aria-label', 'Stop verification');
  await page.getByTestId('simple-stop').click();
  await expect.poll(() => page.evaluate(() => (window as any).composerFixture.calls.some((call: any) => call.operation === 'stopVerification' && call.runId === 'verification-run'))).toBe(true);
  expect(await page.evaluate(() => (window as any).composerFixture.calls.some((call: any) => call.operation === 'stop'))).toBe(false);
  await page.evaluate(() => {
    const { snapshot } = (window as any).composerFixture;
    delete snapshot.activeVerification;
    snapshot.state = 'running'; snapshot.activeRunPhase = 'finalizing'; snapshot.sequence++;
  });
  await expect(page.getByTestId('simple-stop')).toHaveAttribute('aria-label', 'Saving results');
  await expect(page.getByTestId('simple-stop')).toBeDisabled();
  await expect(page.getByTestId('creation-shell')).toContainText('Saving results…');
});

test('simple composer respects shared cleanup and execution-disable gates without dropping drafts', async ({ page }) => {
  await fixture(page, { english: true });
  await page.getByTestId('simple-prompt').fill('Preserve this draft until execution is allowed');
  await expect(page.getByTestId('simple-send')).toBeEnabled();
  await page.evaluate(() => { const { snapshot } = (window as any).composerFixture; snapshot.writeBlocked = true; snapshot.sequence++; });
  await expect(page.getByTestId('simple-send')).toBeDisabled();
  await page.getByTestId('simple-prompt').press('Enter');
  expect(await page.evaluate(() => (window as any).composerFixture.calls.some((call: any) => call.operation === 'send'))).toBe(false);
  await page.evaluate(() => { const { snapshot } = (window as any).composerFixture; snapshot.writeBlocked = false; snapshot.enabled = false; snapshot.sequence++; });
  await expect(page.getByTestId('simple-send')).toBeDisabled();
  await page.evaluate(() => { const { snapshot } = (window as any).composerFixture; snapshot.enabled = true; snapshot.sequence++; });
  await expect(page.getByTestId('simple-send')).toBeEnabled();
  await expect(page.getByTestId('simple-prompt')).toHaveValue('Preserve this draft until execution is allowed');
});
