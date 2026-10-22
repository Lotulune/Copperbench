import { test, expect, type Page } from '@playwright/test';
import { readFileSync } from 'node:fs';

test.use({ storageState: { cookies: [], origins: [] } });

async function fixture(page: Page, options: { english?: boolean; dark?: boolean; single?: boolean; snapshot?: unknown } = {}) {
  await page.addInitScript(options => {
    localStorage.setItem('copperbench.workbench.mode', 'simple');
    localStorage.setItem('copperbench.ui.locale', options.english ? 'en' : 'zh');
    localStorage.setItem('copperbench.theme', options.dark ? 'dark' : 'light');
    const base = options.snapshot as Record<string, any> | undefined;
    let snapshot: Record<string, any> = base ?? { state: 'idle', sequence: 1, conversationId: null, provider: null, sessions: [], messages: [], approvals: [],
      history: { count: 0, maxCount: 100, bytes: 100, maxBytes: 16777216, level: 'ok' },
      providers: ['codex','claude','grok','antigravity','deepseek'].map(id => ({ id, label: id, installed: !options.single || id === 'claude',
        executable: options.single && id !== 'claude' ? null : `/fixture/${id}`, version: '1.0 fixture', setupUrl: 'https://example.com', loginAvailable: id !== 'deepseek', checkAvailable: id !== 'antigravity' })) };
    const calls: unknown[] = [];
    (window as any).agentFixture = { calls, update: (patch: object) => { snapshot = { ...snapshot, ...patch, sequence: snapshot.sequence + 1 }; } };
    window.__COPPERBENCH_AGENT_HOST__ = { async invoke(payload: any) {
      if (payload.operation === 'attachmentTarget') return { ok: true };
      calls.push(payload);
      if (payload.operation === 'new') {
        snapshot.conversationId = `conversation-${snapshot.sessions.length}`; snapshot.provider = payload.provider;
        snapshot.sessions.push({ id: snapshot.conversationId, provider: payload.provider, title: 'New creation', updatedAt: Date.now() }); snapshot.messages = [];
      }
      if (payload.operation === 'checkConnection') {
        snapshot.providers = snapshot.providers.map((provider: any) => provider.id === payload.provider ? { ...provider, connection: { state: 'verified', scope: 'protocol', at: Date.now() } } : provider);
      }
      if (payload.operation === 'openLink') snapshot.linkOpened = true;
      if (payload.operation === 'exportConversation') snapshot.historyResult = { operation: payload.operation, path: '/fixture/export.json' };
      if (payload.operation === 'deleteConversation') {
        snapshot.sessions = snapshot.sessions.filter((session: any) => session.id !== payload.conversationId);
        snapshot.history = { ...snapshot.history, count: snapshot.sessions.length, level: 'ok' };
        snapshot.historyResult = { operation: payload.operation };
        snapshot.storageBlocked = false; delete snapshot.error;
      }
      return { ...snapshot, sequence: ++snapshot.sequence };
    } };
  }, options);
  await page.goto('/');
}

test('installed recommendation, setup diagnostics and limited connection check preserve the draft', async ({ page }) => {
  await fixture(page, { english: true, single: true });
  await expect(page.getByTestId('simple-provider')).toHaveAttribute('data-provider-value', 'claude');
  await page.getByTestId('simple-prompt').fill('Keep this creation idea');
  await page.getByTestId('simple-agent-setup').click();
  const dialog = page.getByTestId('agent-setup-dialog');
  await expect(dialog.getByTestId('setup-provider-codex')).toContainText('Not installed');
  await dialog.getByTestId('agent-check-connection').click();
  await expect(dialog).toContainText('CLI connected · model unverified');
  await expect(dialog).toContainText('without requesting a model');
  await expect(dialog.getByTestId('setup-provider-codex')).toBeInViewport();
  expect((await dialog.boundingBox())!.width).toBeGreaterThan(800);
  await page.screenshot({ path: '../output/simple-mode-onboarding/setup-light.png' });
  await dialog.getByTestId('setup-provider-antigravity').click();
  await expect(dialog.getByTestId('agent-check-connection')).toHaveCount(0);
  await expect(dialog).toContainText('no standalone handshake');
  await dialog.getByTestId('setup-provider-deepseek').click();
  await expect(dialog).toContainText('@deepseek-ai/dsh');
  await dialog.getByRole('button', { name: 'Done', exact: true }).click();
  await expect(page.getByTestId('simple-prompt')).toHaveValue('Keep this creation idea');
  expect(await page.evaluate(() => (window as any).agentFixture.calls.some((call: any) => call.operation === 'send'))).toBe(false);
});

test('pending question input and reading position survive language changes and streaming updates', async ({ page }) => {
  await fixture(page, { english: true });
  await page.evaluate(() => (window as any).agentFixture.update({ state: 'waiting_approval', approvals: [{ id: 'question', kind: 'question',
    title: 'Effects', detail: 'Choose effects', multiSelect: true, allowCustom: true, choices: [{ id: 'glow', label: 'Glow' }, { id: 'fly', label: 'Fly' }] }],
    messages: [{ id: 'long', role: 'assistant', text: Array.from({ length: 60 }, (_, i) => `Paragraph ${i}\n\n`).join(''), at: Date.now() }] }));
  const approval = page.getByTestId('simple-agent-approval');
  await approval.getByRole('checkbox', { name: 'Glow', exact: true }).check();
  await page.getByTestId('simple-approval-answer').fill('Keep custom input');
  await page.evaluate(async () => { const locale = await import('/src/i18n/locale.ts'); locale.setUiLocale('zh'); });
  await expect(approval.getByRole('checkbox', { name: 'Glow', exact: true })).toBeChecked();
  await expect(page.getByTestId('simple-approval-answer')).toHaveValue('Keep custom input');
  await page.locator('.creation-messages').evaluate(element => { element.scrollTop = 0; element.dispatchEvent(new Event('scroll')); });
  await page.evaluate(() => (window as any).agentFixture.update({ messages: [{ id: 'long', role: 'assistant', text: Array.from({ length: 70 }, (_, i) => `Paragraph ${i}\n\n`).join(''), at: Date.now() }] }));
  await expect(page.locator('.agent-markdown')).toContainText('Paragraph 69');
  expect(await page.locator('.creation-messages').evaluate(element => element.scrollTop)).toBeLessThan(50);
});

test('version failure is visible and status refresh does not pretend to reconnect', async ({ page }) => {
  await fixture(page, { english: true });
  await page.evaluate(() => { const fixture = (window as any).agentFixture;
    fixture.update({ state: 'failed', error: 'Model request failed', providers: [{ id: 'codex', label: 'Codex CLI', installed: true,
      executable: '/fixture/codex', version: 'unknown', error: 'Version command failed', setupUrl: 'https://example.com', loginAvailable: true }] });
  });
  await expect(page.getByText('Version command failed', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Refresh status', exact: true }).click();
  await expect(page.getByTestId('simple-agent-error')).toContainText('Model request failed');
  expect(await page.evaluate(() => (window as any).agentFixture.calls.at(-1).operation)).toBe('status');
  await page.getByTestId('simple-agent-setup').click();
  await expect(page.getByTestId('agent-setup-dialog')).toContainText('Model request failed');
});

test('compact dark installation panel keeps every provider and action reachable', async ({ page }) => {
  await page.setViewportSize({ width: 500, height: 600 });
  await fixture(page, { dark: true });
  await page.getByTestId('simple-agent-setup').click();
  const dialog = page.getByTestId('agent-setup-dialog');
  for (const provider of ['codex', 'claude', 'grok', 'antigravity', 'deepseek']) {
    await dialog.getByTestId('setup-provider-trigger').click();
    await page.getByTestId(`provider-option-${provider}`).click();
    await expect(dialog.getByTestId('setup-provider-trigger')).toBeInViewport();
    await expect(dialog.getByTestId('setup-provider-trigger')).toHaveAttribute('data-provider-value', provider);
  }
  await expect(dialog.getByRole('button', { name: '使用此助手', exact: true })).toBeInViewport();
  expect(await dialog.evaluate(element => element.scrollWidth <= element.clientWidth)).toBe(true);
  await page.screenshot({ path: '../output/simple-mode-onboarding/setup-dark-compact.png' });
});

test('native service snapshot survives the bridge and language changes without rebuilding approval state', async ({ page }) => {
  const nativeSnapshot = JSON.parse(readFileSync(new URL('./fixtures/agent-runtime.json', import.meta.url), 'utf8'));
  await fixture(page, { english: true, snapshot: nativeSnapshot });
  const approval = page.getByTestId('simple-agent-approval');
  await expect(approval).toContainText('Allow this command?');
  await expect(approval.getByRole('button', { name: 'Allow once', exact: true })).toBeVisible();
  await page.getByTestId('simple-prompt').fill('Retain my draft');
  await page.getByRole('combobox', { name: 'Interface language', exact: true }).selectOption('zh');
  await expect(approval).toContainText('允许运行命令？');
  await expect(page.getByTestId('simple-prompt')).toHaveValue('Retain my draft');
  await page.getByRole('combobox', { name: '界面语言', exact: true }).selectOption('en');
  await expect(approval).toContainText('Allow this command?');
  expect(await page.evaluate(() => (window as any).agentFixture.calls.some((call: any) => call.operation === 'approve'))).toBe(false);
});

test('markdown, exact copying, safe links and unfinished streaming fences remain readable', async ({ page, context }) => {
  await context.grantPermissions(['clipboard-read', 'clipboard-write']);
  await page.setViewportSize({ width: 500, height: 700 });
  await fixture(page, { english: true });
  const text = '# Saved block\n\n- **Hardness**: `3`\n- [Docs](https://example.com/docs)\n\n```java\n  int value = 3;\n  // <tag> & special\n```\n\n<script>window.injected=true</script>\n[unsafe](javascript:alert(1))';
  await page.evaluate(text => (window as any).agentFixture.update({ messages: [{ id: 'm1', role: 'assistant', text, at: Date.now() }] }), text);
  const markdown = page.locator('.agent-markdown');
  await expect(markdown.getByRole('heading', { name: 'Saved block' })).toBeVisible();
  await expect(markdown.locator('li')).toHaveCount(2);
  await expect(markdown.locator('a')).toHaveCount(1);
  await markdown.getByRole('link', { name: 'Docs', exact: true }).click();
  expect(await page.evaluate(() => (window as any).agentFixture.calls.at(-1))).toEqual({ operation: 'openLink', url: 'https://example.com/docs' });
  await expect(markdown.getByRole('alert')).toHaveCount(0);
  await markdown.getByRole('button', { name: 'Copy code', exact: true }).click();
  expect((await page.evaluate(() => navigator.clipboard.readText())).replaceAll('\r\n', '\n')).toBe('  int value = 3;\n  // <tag> & special\n');
  await markdown.getByRole('button', { name: 'Copy message', exact: true }).click();
  expect((await page.evaluate(() => navigator.clipboard.readText())).replaceAll('\r\n', '\n')).toBe(text);
  expect(await page.evaluate(() => (window as any).injected)).toBeUndefined();
  await page.evaluate(() => (window as any).agentFixture.update({ messages: [{ id: 'm1', role: 'assistant', text: '```js\n  still_streaming(', at: Date.now() }] }));
  await expect(markdown.locator('pre')).toContainText('still_streaming(');
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  await page.screenshot({ path: '../output/simple-mode-onboarding/markdown-compact.png' });
});

test('history is manageable at capacity and deleting requires confirmation', async ({ page }) => {
  await fixture(page, { english: true });
  await page.evaluate(() => (window as any).agentFixture.update({ history: { count: 100, maxCount: 100, bytes: 16000000, maxBytes: 16777216, level: 'full' },
    storageBlocked: true, error: 'History full', sessions: [{ id: 'old', provider: 'claude', title: 'Old conversation', updatedAt: Date.now() }] }));
  await expect(page.getByRole('button', { name: 'Manage history and recover' })).toBeVisible();
  await page.getByTestId('simple-manage-conversations').click();
  const dialog = page.getByTestId('agent-history-dialog');
  await expect(dialog).toContainText('100 / 100');
  await dialog.getByRole('button', { name: 'Export Old conversation' }).click();
  await expect(dialog).toContainText('/fixture/export.json');
  await dialog.getByRole('button', { name: 'Delete Old conversation' }).click();
  const confirmation = page.getByRole('dialog').filter({ hasText: 'Delete this conversation?' });
  await confirmation.getByRole('button', { name: 'Cancel', exact: true }).click();
  await expect(dialog).toContainText('Old conversation');
  await dialog.getByRole('button', { name: 'Delete Old conversation' }).click();
  await confirmation.getByRole('button', { name: 'Delete conversation', exact: true }).click();
  await expect(dialog).toContainText('No conversation history yet.');
});

for (const mode of ['simple', 'professional'] as const) test(`${mode} spotlight tour moves, remains within the viewport and respects reduced motion`, async ({ page }) => {
  await page.setViewportSize({ width: 500, height: 700 });
  await fixture(page, { english: true, dark: true });
  if (mode === 'professional') {
    await page.getByTestId('workbench-mode-control').hover();
    await page.getByTestId('mode-professional').click();
    await page.getByTestId('nav-help').click();
    await page.getByTestId('help-start-tour').click();
  } else await page.getByTestId('simple-tour').click();
  const card = page.getByTestId('workbench-tour-card');
  await expect(card).toBeVisible();
  for (let step = 0; step < 6; step++) {
    await expect(card.locator('header')).toContainText(`${step + 1} / 6`);
    await page.waitForTimeout(420);
    await expect(page.locator('.workbench-tour-highlight')).toBeVisible();
    const box = await card.boundingBox();
    expect(box!.x).toBeGreaterThanOrEqual(0); expect(box!.x + box!.width).toBeLessThanOrEqual(501);
    expect(box!.y).toBeGreaterThanOrEqual(0); expect(box!.y + box!.height).toBeLessThanOrEqual(701);
    if (step === 2) await page.screenshot({ path: `../output/simple-mode-onboarding/tour-${mode}-compact.png` });
    if (step < 5) await page.getByTestId('tour-next').click();
  }
  await page.keyboard.press('Escape'); await expect(card).toHaveCount(0);
  await page.emulateMedia({ reducedMotion: 'reduce' });
  if (mode === 'simple') await page.getByTestId('simple-tour').click();
  else { await page.getByTestId('nav-help').click(); await page.getByTestId('help-start-tour').click(); }
  expect(await card.evaluate(element => parseFloat(getComputedStyle(element).transitionDuration))).toBeLessThanOrEqual(.001);
  await page.keyboard.press('Escape'); await expect(card).toHaveCount(0);
});


test('long native command approvals keep the exact command and reachable decisions', async ({ page }, testInfo) => {
  await fixture(page, { english: true });
  const command = 'python inspect_workspace.py\n' + '# exact operation detail\n'.repeat(90);
  const detail = JSON.stringify({ kind: 'command', reason: 'Read the current workspace through its public SDK.', cwd: '/fixture/workspace', command,
    commandActions: [{ command }], proposedExecpolicyAmendment: [command], availableDecisions: ['accept', 'cancel'] });
  await page.evaluate(detail => (window as any).agentFixture.update({ state: 'waiting_approval', approvals: [{ id: 'long-command', kind: 'permission',
    title: 'Allow this command?', detail, choices: [{ id: 'once', label: 'Allow once' }, { id: 'stop', label: 'Stop turn' }] }] }), detail);
  const approval = page.getByTestId('simple-agent-approval');
  await expect(approval).toContainText('Read the current workspace through its public SDK.');
  expect(await approval.getByLabel('Command to run').textContent()).toBe(command);
  await expect(approval.getByRole('button', { name: 'Allow once' })).toBeInViewport();
  await expect(page.getByTestId('simple-prompt')).toBeInViewport();
  expect(await approval.getByLabel('Command to run').evaluate(element => element.scrollHeight > element.clientHeight)).toBe(true);
  await approval.getByText('View full operation', { exact: true }).click();
  expect(await approval.locator('details pre').textContent()).toBe(detail);
  await approval.getByText('View full operation', { exact: true }).click();
  await page.screenshot({ path: testInfo.outputPath('long-command-approval.png') });
  await page.setViewportSize({ width: 500, height: 600 });
  await approval.getByRole('button', { name: 'Allow once' }).scrollIntoViewIfNeeded();
  await expect(approval.getByRole('button', { name: 'Allow once' })).toBeInViewport();
  await page.getByTestId('simple-prompt').scrollIntoViewIfNeeded();
  await expect(page.getByTestId('simple-prompt')).toBeInViewport();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
});
