import { test, expect, type Page } from '@playwright/test';
import { readFileSync } from 'node:fs';

const legacyHistory = JSON.parse(readFileSync(new URL('../../src/test/resources/dev/copperbench/agent/history-0.1.15.json', import.meta.url), 'utf8'));

test.use({ storageState: { cookies: [], origins: [] } });

async function fixture(page: Page, options: { dark?: boolean; failed?: boolean; english?: boolean; legacy?: boolean } = {}) {
  await page.addInitScript(({ options, legacyHistory }) => {
    localStorage.setItem('copperbench.workbench.mode', 'simple');
    localStorage.setItem('copperbench.ui.locale', options.english ? 'en' : 'zh');
    localStorage.setItem('copperbench.theme', options.dark ? 'dark' : 'light');
    const calls: any[] = [];
    let failed = options.failed, rejectNew = false;
    const snapshot: any = { state: 'idle', sequence: 1, conversationId: null, provider: null, sessions: [], messages: [], approvals: [],
      providers: [{ id: options.legacy ? 'codex' : 'claude', label: options.legacy ? 'Codex CLI' : 'Claude Code', installed: true, executable: '/fixture/cli', version: 'fixture', setupUrl: 'https://example.com', loginAvailable: true }] };
    if (options.legacy) {
      const saved = sessionStorage.getItem('legacy-model-fixture');
      const history = saved ? JSON.parse(saved) : legacyHistory;
      snapshot.sessions = history.sessions;
      const session = snapshot.sessions.find((item: any) => item.id === history.selectedConversationId);
      Object.assign(snapshot, { conversationId: session.id, provider: session.provider, model: session.model ?? null,
        usesSessionModel: !session.model && Boolean(session.remoteId), messages: session.messages });
      localStorage.setItem('copperbench.agent.models', JSON.stringify({ codex: 'saved-default' }));
    }
    (window as any).modelsFixture = { calls, fail: (value: boolean) => { failed = value; }, reject: (value: boolean) => { rejectNew = value; },
      update: (patch: object) => Object.assign(snapshot, patch) };
    window.__COPPERBENCH_AGENT_HOST__ = { async invoke(request: any) {
      if (request.operation === 'attachmentTarget') return { ok: true };
      calls.push(request);
      if (request.operation === 'models') snapshot.providers[0].modelCatalog = failed
        ? { state: 'failed', models: [], requiresSelection: true, source: 'service', error: 'Model service unavailable' }
        : { state: 'ready', requiresSelection: true, source: 'service', defaultModel: options.legacy ? 'catalog-default' : undefined,
          models: [{ id: 'deepseek-v4.1-flash', label: 'DeepSeek Flash' }, { id: 'z-ai/glm-5.3', label: 'GLM 5.3' }] };
      if (request.operation === 'new') {
        if (rejectNew) { snapshot.error = 'Could not save conversation'; return { ...snapshot, sequence: ++snapshot.sequence }; }
        snapshot.conversationId = `conversation-${snapshot.sessions.length}`;
        snapshot.provider = request.provider; snapshot.model = request.model ?? null; snapshot.usesSessionModel = false;
        snapshot.messages = []; delete snapshot.error;
        snapshot.sessions.push({ id: snapshot.conversationId, provider: request.provider, model: snapshot.model, title: `Model chat ${snapshot.sessions.length}`, updatedAt: Date.now(), messages: snapshot.messages });
      }
      if (request.operation === 'select') {
        const session = snapshot.sessions.find((session: any) => session.id === request.conversationId);
        snapshot.conversationId = session.id; snapshot.provider = session.provider; snapshot.model = session.model;
        snapshot.usesSessionModel = !session.model && Boolean(session.remoteId); snapshot.messages = session.messages;
      }
      if (request.operation === 'send') { snapshot.messages.push({ id: `user-${calls.length}`, role: 'user', text: request.text, at: Date.now(), delivery: 'sent' }); snapshot.state = options.legacy ? 'idle' : 'running'; }
      if (options.legacy) sessionStorage.setItem('legacy-model-fixture', JSON.stringify({ selectedConversationId: snapshot.conversationId, sessions: snapshot.sessions }));
      return { ...snapshot, sequence: ++snapshot.sequence };
    } };
  }, { options, legacyHistory });
  await page.goto('/');
  await expect(page.getByTestId('simple-provider')).toHaveAttribute('data-provider-value', options.legacy ? 'codex' : 'claude');
  await expect.poll(() => page.evaluate(() => (window as any).modelsFixture.calls.filter((request: any) => request.operation === 'models').length)).toBe(1);
}

test('legacy history continues in place without adopting defaults and survives reopening', async ({ page }, info) => {
  await fixture(page, { legacy: true, english: true });
  const conversation = legacyHistory.selectedConversationId;
  await expect(page.getByRole('log')).toContainText('Created the original blue block');
  await expect(page.getByTestId('simple-model')).toHaveText('Keep session model');
  const prompt = page.getByTestId('simple-prompt');
  await prompt.fill('Continue the original block');
  await page.getByTestId('simple-model').click();
  await expect(page.getByRole('dialog', { name: 'Choose a model' })).toContainText('Continue with the original CLI session model');
  await page.keyboard.press('Escape');
  await expect(prompt).toHaveValue('Continue the original block');
  await expect(page.getByTestId('simple-send')).toBeEnabled();
  await page.screenshot({ path: info.outputPath('legacy-continue.png') });
  await page.getByTestId('simple-send').click();
  const calls = await page.evaluate(() => (window as any).modelsFixture.calls);
  expect(calls.filter((request: any) => request.operation === 'new')).toHaveLength(0);
  const sent = calls.find((request: any) => request.operation === 'send');
  expect(sent).toMatchObject({ conversationId: conversation, provider: 'codex', text: 'Continue the original block' });
  expect(sent).not.toHaveProperty('model');
  await expect(prompt).toHaveValue('');
  await page.reload();
  await expect(page.getByTestId('simple-model')).toHaveText('Keep session model');
  await expect(page.getByRole('log')).toContainText('Continue the original block');
  await expect(page.getByRole('log')).toContainText('Created the original blue block');
  await prompt.fill('Draft for the chosen model');
  await page.getByTestId('simple-model').click();
  await page.locator('[data-model-id="z-ai/glm-5.3"]').click();
  await expect(prompt).toHaveValue('Draft for the chosen model');
  expect(await page.evaluate(() => (window as any).modelsFixture.calls.filter((request: any) => request.operation === 'new')))
    .toMatchObject([{ model: 'z-ai/glm-5.3', provider: 'codex' }]);
  await page.getByTestId(`simple-conversation-${conversation}`).click();
  await expect(page.getByRole('log')).toContainText('Created the original blue block');
  await expect(page.getByTestId('simple-model')).toHaveText('Keep session model');
});

test('a model-less new conversation still requires a selection', async ({ page }) => {
  await fixture(page);
  await page.getByTestId('simple-new-chat').click();
  await expect(page.getByTestId('simple-conversation-conversation-0')).toBeVisible();
  await page.getByTestId('simple-prompt').fill('Choose a model first');
  await expect(page.getByTestId('simple-send')).toBeDisabled();
  await expect(page.getByTestId('simple-model')).toHaveText('选择或输入模型');
});

test('configured service models are selectable, preserve drafts and reach the send request', async ({ page }) => {
  await fixture(page);
  const prompt = page.getByTestId('simple-prompt');
  await prompt.fill('保留这份方块创意');
  await expect(page.getByTestId('simple-send')).toBeDisabled();
  await page.getByTestId('simple-model').click();
  await expect(page.getByRole('dialog', { name: '选择模型' })).toContainText('当前服务的模型列表');
  await expect(page.getByRole('button', { name: '使用 CLI 默认模型' })).toHaveCount(0);
  const search = page.getByRole('combobox', { name: '搜索或输入模型 ID' });
  await search.fill('deepseek'); await search.press('Enter');
  await expect(page.getByTestId('simple-model')).toHaveAttribute('data-model-value', 'deepseek-v4.1-flash');
  await expect(prompt).toHaveValue('保留这份方块创意');
  await page.getByTestId('simple-model').click();
  await page.locator('[data-model-id="z-ai/glm-5.3"]').click();
  await expect(prompt).toHaveValue('保留这份方块创意');
  await expect(page.getByTestId('simple-model')).toHaveAttribute('data-model-value', 'z-ai/glm-5.3');
  await page.getByTestId('simple-send').click();
  expect(await page.evaluate(() => (window as any).modelsFixture.calls.findLast((request: any) => request.operation === 'send')))
    .toMatchObject({ provider: 'claude', model: 'z-ai/glm-5.3', text: '保留这份方块创意' });
  await expect(page.getByTestId('simple-model')).toBeDisabled();
  await expect(prompt).toHaveValue('');
  await page.evaluate(() => (window as any).modelsFixture.update({ state: 'idle' }));
  await page.getByTestId('simple-conversation-conversation-0').click();
  await expect(page.getByTestId('simple-model')).toHaveAttribute('data-model-value', 'deepseek-v4.1-flash');
});

test('failed discovery can refresh or use a manual ID and a rejected selection keeps the draft', async ({ page }) => {
  await fixture(page, { failed: true, english: true });
  await page.getByTestId('simple-prompt').fill('Keep this draft');
  await page.getByTestId('simple-model').click();
  await expect(page.getByRole('dialog', { name: 'Choose a model' })).toContainText('Model service unavailable');
  const search = page.getByRole('combobox', { name: 'Search or enter a model ID' });
  await search.fill('--unsafe');
  await expect(page.getByRole('button', { name: /Use this model ID/ })).toHaveCount(0);
  await search.fill('provider/custom-model');
  await page.evaluate(() => (window as any).modelsFixture.reject(true));
  await page.getByRole('button', { name: /Use this model ID/ }).focus(); await page.keyboard.press('Enter');
  await expect(page.getByTestId('simple-agent-error')).toContainText('Could not save conversation');
  await expect(page.getByTestId('simple-prompt')).toHaveValue('Keep this draft');
  await page.evaluate(() => { (window as any).modelsFixture.reject(false); (window as any).modelsFixture.fail(false); });
  await page.getByTestId('simple-model').click();
  await page.getByRole('button', { name: 'Refresh model list' }).focus(); await page.keyboard.press('Enter');
  await expect(page.getByTestId('model-option')).toHaveCount(2);
  expect(await page.evaluate(() => (window as any).modelsFixture.calls.filter((request: any) => request.operation === 'new').length)).toBe(1);
  await search.fill('provider/custom-model'); await search.press('Enter');
  await expect(page.getByTestId('simple-model')).toHaveAttribute('data-model-value', 'provider/custom-model');
  await expect(page.getByTestId('simple-prompt')).toHaveValue('Keep this draft');
});

for (const dark of [false, true]) test(`composer has one focus boundary and the model menu fits at narrow sizes (${dark ? 'dark' : 'light'})`, async ({ page }, testInfo) => {
  await page.setViewportSize({ width: 500, height: 600 });
  await fixture(page, { dark });
  const prompt = page.getByTestId('simple-prompt');
  await prompt.fill('一个统一的输入区域');
  const inner = await prompt.evaluate(element => { const style = getComputedStyle(element); return [style.boxShadow, style.outlineStyle, style.borderBottomWidth]; });
  expect(inner).toEqual(['none', 'none', '0px']);
  expect(await page.locator('.creation-composer').evaluate(element => getComputedStyle(element).boxShadow)).not.toBe('none');
  await page.screenshot({ path: testInfo.outputPath('composer-focus.png') });
  await page.getByTestId('simple-model').click();
  const menu = page.getByRole('dialog', { name: '选择模型' });
  await expect(menu).toBeInViewport();
  const box = await menu.boundingBox();
  expect(box!.y).toBeGreaterThanOrEqual(0); expect(box!.y + box!.height).toBeLessThanOrEqual(600);
  expect(box!.x).toBeGreaterThanOrEqual(0); expect(box!.x + box!.width).toBeLessThanOrEqual(500);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.screenshot({ path: testInfo.outputPath('model-menu.png') });
  await page.keyboard.press('Escape'); await expect(page.getByTestId('simple-model')).toBeFocused();
});
