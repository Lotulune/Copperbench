import { test, expect, type Page } from '@playwright/test';

test.use({ storageState: { cookies: [], origins: [] } });

async function fixture(page: Page, options: { dark?: boolean; failed?: boolean; english?: boolean } = {}) {
  await page.addInitScript(options => {
    localStorage.setItem('copperbench.workbench.mode', 'simple');
    localStorage.setItem('copperbench.ui.locale', options.english ? 'en' : 'zh');
    localStorage.setItem('copperbench.theme', options.dark ? 'dark' : 'light');
    const calls: any[] = [];
    let failed = options.failed, rejectNew = false;
    const snapshot: any = { state: 'idle', sequence: 1, conversationId: null, provider: null, sessions: [], messages: [], approvals: [],
      providers: [{ id: 'claude', label: 'Claude Code', installed: true, executable: '/fixture/claude', version: 'fixture', setupUrl: 'https://example.com', loginAvailable: true }] };
    (window as any).modelsFixture = { calls, fail: (value: boolean) => { failed = value; }, reject: (value: boolean) => { rejectNew = value; },
      update: (patch: object) => Object.assign(snapshot, patch) };
    window.__COPPERBENCH_AGENT_HOST__ = { async invoke(request: any) {
      if (request.operation === 'attachmentTarget') return { ok: true };
      calls.push(request);
      if (request.operation === 'models') snapshot.providers[0].modelCatalog = failed
        ? { state: 'failed', models: [], requiresSelection: true, source: 'service', error: 'Model service unavailable' }
        : { state: 'ready', requiresSelection: true, source: 'service', models: [{ id: 'deepseek-v4.1-flash', label: 'DeepSeek Flash' }, { id: 'z-ai/glm-5.3', label: 'GLM 5.3' }] };
      if (request.operation === 'new') {
        if (rejectNew) { snapshot.error = 'Could not save conversation'; return { ...snapshot, sequence: ++snapshot.sequence }; }
        snapshot.conversationId = `conversation-${snapshot.sessions.length}`;
        snapshot.provider = request.provider; snapshot.model = request.model ?? null;
        snapshot.messages = []; delete snapshot.error;
        snapshot.sessions.push({ id: snapshot.conversationId, provider: request.provider, model: snapshot.model, title: `Model chat ${snapshot.sessions.length}`, updatedAt: Date.now() });
      }
      if (request.operation === 'select') {
        const session = snapshot.sessions.find((session: any) => session.id === request.conversationId);
        snapshot.conversationId = session.id; snapshot.provider = session.provider; snapshot.model = session.model;
      }
      if (request.operation === 'send') { snapshot.messages.push({ id: `user-${calls.length}`, role: 'user', text: request.text, at: Date.now() }); snapshot.state = 'running'; }
      return { ...snapshot, sequence: ++snapshot.sequence };
    } };
  }, options);
  await page.goto('/');
  await expect(page.getByTestId('simple-provider')).toHaveAttribute('data-provider-value', 'claude');
  await expect.poll(() => page.evaluate(() => (window as any).modelsFixture.calls.filter((request: any) => request.operation === 'models').length)).toBe(1);
}

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
