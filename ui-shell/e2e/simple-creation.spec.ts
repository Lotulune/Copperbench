import { test, expect, type Page } from '@playwright/test';
import type { AgentApproval } from '../src/bridge/agentBridge';

test.use({ storageState: { cookies: [], origins: [] } });

async function installAgentFixture(page: Page, options: { installed?: boolean; english?: boolean; theme?: string } = {}) {
  await page.addInitScript(options => {
    localStorage.setItem('copperbench.workbench.mode', 'simple');
    if (options.english) localStorage.setItem('copperbench.ui.locale', 'en');
    if (options.theme) localStorage.setItem('copperbench.theme', options.theme);
    const providers = ['codex', 'claude', 'grok', 'antigravity', 'deepseek'].map((id, index) => ({
      id, label: ['Codex CLI', 'Claude Code', 'Grok Build', 'Antigravity CLI', 'DeepSeek Harness'][index],
      installed: options.installed !== false, executable: options.installed === false ? null : `/fixture/${id}`,
      modelCatalog: { state: 'ready', models: [{ id: 'fixture-model', label: 'Fixture model' }], defaultModel: 'fixture-model' },
      setupUrl: 'https://example.invalid/fixture', loginAvailable: options.installed !== false && id !== 'deepseek'
    }));
    type Message = { id: string; role: string; text: string; at: number };
    type Session = { id: string; provider: string; model: string; title: string; updatedAt: number; messages: Message[] };
    let sessions: Session[] = JSON.parse(localStorage.getItem('agent-test-sessions') ?? '[]');
    let selected = sessions[0]?.id ?? '';
    let state = 'idle', sequence = 0;
    let approvals: AgentApproval[] = [];
    let error: string | undefined;
    const save = () => localStorage.setItem('agent-test-sessions', JSON.stringify(sessions));
    const record = (operation: string) => {
      const calls: string[] = JSON.parse(localStorage.getItem('agent-test-calls') ?? '[]');
      calls.push(operation); localStorage.setItem('agent-test-calls', JSON.stringify(calls));
    };
    const current = () => sessions.find(session => session.id === selected);
    const snapshot = () => ({ state, sequence: ++sequence, conversationId: selected || null, provider: current()?.provider ?? null, model: current()?.model ?? null,
      sessions: sessions.map(({messages: _, ...session}) => session), messages: current()?.messages ?? [], providers, approvals, error });
    const add = (role: string, text: string) => {
      current()?.messages.push({ id: crypto.randomUUID(), role, text, at: Date.now() }); save();
    };
    window.__COPPERBENCH_AGENT_HOST__ = { async invoke(payload) {
      const operation = String(payload.operation);
      if (operation === 'attachmentTarget') return { ok: true };
      record(operation);
      if (operation === 'new') {
        selected = crypto.randomUUID(); sessions.push({id:selected, provider:String(payload.provider), model:String(payload.model ?? 'fixture-model'), title:'新对话', updatedAt:Date.now(), messages:[]}); save();
      } else if (operation === 'select') selected = String(payload.conversationId);
      else if (operation === 'send') {
        if (!selected) { selected = crypto.randomUUID(); sessions.push({id:selected, provider:String(payload.provider), model:String(payload.model ?? 'fixture-model'), title:'林间铜灯', updatedAt:Date.now(), messages:[]}); }
        add('user', String(payload.text)); state = 'running'; error = undefined;
        if (String(payload.text).includes('approval')) {
          state = 'waiting_approval'; approvals = [{id:'approval-1', title:'运行构建', detail:'Build the current workspace', choices:[{id:'once',label:'允许一次'},{id:'deny',label:'拒绝'}]}];
        } else if (String(payload.text).includes('multi-question')) {
          state = 'waiting_approval'; approvals = [{id:'question-multi',kind:'question',multiSelect:true,allowCustom:true,title:'选择效果',detail:'希望铜灯具备哪些效果？',
            choices:[{id:'glow',label:'发光',description:'照亮周围环境'},{id:'flight',label:'飞行',description:'悬浮在玩家身旁'}]}];
        } else if (String(payload.text).includes('custom-question')) {
          state = 'waiting_approval'; approvals = [{id:'question-custom',kind:'question',multiSelect:false,allowCustom:true,title:'作品名称',detail:'选择名称或填写自己的名字。',choices:[{id:'lamp',label:'铜灯'}]}];
        } else if (String(payload.text).includes('question')) {
          state = 'waiting_approval'; approvals = [{id:'question-1',kind:'question',multiSelect:false,allowCustom:true,title:'铜灯应该是什么颜色？',detail:'Choose the light color',choices:[]}];
        } else if (String(payload.text).includes('permission')) {
          state = 'failed'; error = 'Antigravity 的构建命令未获授权。请检查 CLI 权限设置后重试。';
        } else add('assistant', '已收到你的创作需求，正在准备作品。');
      } else if (operation === 'stop') { state = 'idle'; approvals = []; add('system','已停止，保留已有修改。'); }
      else if (operation === 'approve') {
        localStorage.setItem('agent-test-answer', JSON.stringify(payload));
        approvals = []; state = 'idle';
        add('assistant', `已收到：${'choice' in payload ? payload.choice : payload.answer ?? payload.choices?.join(',')}`);
      }
      return snapshot();
    } };
    window.__COPPERBENCH_WORKSPACE_OPEN_HOST__ = {
      available: true, workspacePickerAvailable: true, workspaceFolderPickerAvailable: true,
      async chooseWorkspace() { return {cancelled:false, workspaceFile:'D:/Creations/lantern/lantern.mcreator'}; },
      async chooseWorkspaceFolder() { return {cancelled:false,path:'D:/MCreatorWorkspaces/lantern'}; },
      async open(file: string) { localStorage.setItem('agent-test-opened-work',file); }
    };
  }, options);
}

test('first visit defaults to simple; returning preferences and an explicit choice are retained', async ({ page }) => {
  await page.goto('/');
  await expect(page.getByTestId('mode-simple')).toHaveAttribute('aria-pressed','true');
  await expect(page.getByTestId('simple-agent-unavailable')).toBeVisible();
  await expect(page.getByTestId('simple-send')).toBeDisabled();
  await page.getByTestId('workbench-mode-control').hover();
  await page.getByTestId('mode-professional').click();
  await page.reload();
  await expect(page.getByTestId('mode-professional')).toHaveAttribute('aria-pressed','true');
  await page.evaluate(() => { localStorage.removeItem('copperbench.workbench.mode'); localStorage.setItem('copperbench.theme','light'); });
  await page.reload();
  await expect(page.getByTestId('mode-professional')).toHaveAttribute('aria-pressed','true');
});

async function unsavedDrafts(page: Page, excluded: string[] = []) {
  return page.evaluate(async excludedOwners => {
    const modulePath = performance.getEntriesByType('resource').map(entry => entry.name)
      .find(url => new URL(url).pathname === '/src/hooks/unsavedDraftGuard.ts') ?? '/src/hooks/unsavedDraftGuard.ts';
    return (await import(modulePath)).hasUnsavedDrafts(excludedOwners);
  }, excluded);
}

test('new creation retains an unfinished draft and resets a completed form for the next project', async ({ page }) => {
  await installAgentFixture(page);
  await page.goto('/');
  await page.getByTestId('simple-new-workspace').click();
  await page.getByTestId('new-workspace-mod-name-input').fill('First Review Work');
  await page.getByTestId('simple-back').click();
  await page.getByTestId('simple-new-workspace').click();
  await expect(page.getByTestId('new-workspace-mod-name-input')).toHaveValue('First Review Work');
  await page.getByTestId('confirm-create-workspace-checkbox').check();
  await page.getByTestId('create-workspace-submit-btn').click();
  await expect(page.getByTestId('workspace-created-banner')).toBeVisible();
  const firstId = await page.getByTestId('new-workspace-mod-id-input').inputValue();
  await page.getByTestId('simple-back').click();
  await page.getByTestId('simple-new-workspace').click();
  await expect(page.getByTestId('workspace-created-banner')).toHaveCount(0);
  await expect(page.getByTestId('new-workspace-mod-name-input')).toHaveValue('');
  await expect(page.getByTestId('confirm-create-workspace-checkbox')).not.toBeChecked();
  await page.getByTestId('new-workspace-mod-name-input').fill('Second Review Work');
  await expect(page.getByTestId('new-workspace-mod-id-input')).not.toHaveValue(firstId);
  await page.getByTestId('confirm-create-workspace-checkbox').check();
  await page.getByTestId('create-workspace-submit-btn').click();
  await expect(page.getByTestId('workspace-created-banner')).toBeVisible();
  await page.getByTestId('create-another-workspace').click();
  await expect(page.getByTestId('workspace-created-banner')).toHaveCount(0);
  await expect(page.getByTestId('new-workspace-mod-name-input')).toHaveValue('');
});

test('removed content has an explicit draft discard that preserves drafts for remaining content', async ({ page }) => {
  await installAgentFixture(page);
  await page.goto('/');
  const lamp = page.locator('[data-testid^="simple-element-"]').filter({hasText:'Copper Lamp'});
  const compass = page.locator('[data-testid^="simple-element-"]').filter({hasText:'Trail Compass'});
  await lamp.click();
  await page.getByRole('tab',{name:'设置',exact:true}).click();
  await page.locator('[data-testid^="simple-parameter-"]').first().fill('Unapplied lamp');
  await compass.click();
  await page.locator('[data-testid^="simple-parameter-"]').first().fill('Keep this compass draft');
  await page.getByTestId('simple-prompt').fill('Create another item');
  const result = await page.evaluate(async () => {
    const bridgePath = performance.getEntriesByType('resource').map(entry => entry.name)
      .find(url => new URL(url).pathname === '/src/bridge/index.ts') ?? '/src/bridge/index.ts';
    const { coreBridge } = await import(bridgePath), state = coreBridge.getState();
    const element = state.elements.find(item => item.name === 'copper_lamp');
    return coreBridge.sendCommand({messageType:'command',schemaVersion:'1.0',requestId:crypto.randomUUID(),workspaceId:state.workbench.workspace.id,
      expectedRevision:state.workbench.workspace.revision,operation:'delete_mod_element',payload:{clientMutationId:crypto.randomUUID(),elementId:element.id}});
  });
  expect(result.status).toBe('committed');
  await expect(lamp).toHaveCount(0);
  await expect(page.getByTestId('simple-orphan-drafts')).toContainText('Copper Lamp');
  await page.screenshot({path:'../output/simple-creation-fixes/orphan-draft-recovery.png',fullPage:true});
  await page.getByRole('button',{name:'放弃已移除内容的输入',exact:true}).click();
  await expect(page.getByTestId('simple-orphan-drafts')).toHaveCount(0);
  await expect(page.locator('[data-testid^="simple-parameter-"]').first()).toHaveValue('Keep this compass draft');
  await expect(page.getByTestId('simple-send')).toBeDisabled();
  await page.getByRole('button',{name:'放弃输入并重读',exact:true}).click();
  await expect(page.getByTestId('simple-send')).toBeEnabled();
  await expect(page.getByTestId('simple-playtest')).toBeEnabled();
});

test('chat drafts reach the close guard and move into a newly selected backend before being sent', async ({ page }) => {
  await installAgentFixture(page);
  await page.goto('/');
  await expect(page.getByTestId('simple-prompt')).toBeVisible();
  expect(await unsavedDrafts(page)).toBe(false);
  await page.getByTestId('simple-prompt').fill('保留这段尚未发送的创作想法');
  await expect.poll(() => unsavedDrafts(page)).toBe(true);
  expect(await unsavedDrafts(page,['simple-chat'])).toBe(false);
  await page.getByTestId('workbench-mode-control').hover();
  await page.getByTestId('mode-professional').click();
  await page.getByTestId('workbench-mode-control').hover();
  await page.getByTestId('mode-simple').click();
  await page.getByTestId('simple-provider').click();
  await page.getByTestId('provider-option-claude').click();
  await expect(page.getByTestId('simple-prompt')).toHaveValue('保留这段尚未发送的创作想法');
  await page.getByTestId('simple-export').click();
  await expect(page.getByTestId('delivery-dialog')).toBeVisible();
  await expect(page.getByTestId('delivery-unsaved')).toHaveCount(0);
  await page.getByRole('button',{name:'关闭交付窗口',exact:true}).click();
  await page.getByTestId('simple-send').click();
  await expect(page.getByTestId('simple-prompt')).toHaveValue('');
  await expect.poll(() => unsavedDrafts(page)).toBe(false);
  await page.getByTestId('simple-stop').click();
});

test('playtest asks before using saved content when another editor has unsaved changes', async ({ page }) => {
  await installAgentFixture(page);
  await page.goto('/');
  await page.getByTestId('workbench-mode-control').hover();
  await page.getByTestId('mode-professional').click();
  await page.getByTestId('nav-elements').click();
  await page.locator('.element-card').filter({hasText:'Copper Lamp'}).click();
  await page.getByTestId('field-displayName').fill('Unapplied professional edit');
  await page.getByTestId('workbench-mode-control').hover();
  await page.getByTestId('mode-simple').click();
  expect(await unsavedDrafts(page)).toBe(true);
  await page.evaluate(async () => {
    const bridgePath = performance.getEntriesByType('resource').map(entry => entry.name)
      .find(url => new URL(url).pathname === '/src/bridge/index.ts') ?? '/src/bridge/index.ts';
    const { coreBridge } = await import(bridgePath), original = coreBridge.sendCommand.bind(coreBridge);
    coreBridge.sendCommand = async request => {
      if (request.operation === 'run_client') sessionStorage.setItem('simple-playtest-calls',String(Number(sessionStorage.getItem('simple-playtest-calls') ?? '0') + 1));
      return original(request);
    };
  });
  await page.getByTestId('simple-playtest').click();
  await expect(page.getByTestId('confirm-dialog')).toContainText('已保存的内容');
  await page.getByTestId('confirm-dialog').getByRole('button',{name:'取消',exact:true}).click();
  expect(await page.evaluate(() => sessionStorage.getItem('simple-playtest-calls'))).toBeNull();
  await page.getByTestId('simple-playtest').click();
  await page.getByTestId('confirm-dialog').getByRole('button',{name:'试玩已保存内容',exact:true}).click();
  await expect.poll(() => page.evaluate(() => sessionStorage.getItem('simple-playtest-calls'))).toBe('1');
  expect(await unsavedDrafts(page)).toBe(true);
});

test('clarifying questions submit multiple choices and custom answers explicitly', async ({ page }) => {
  await installAgentFixture(page,{theme:'light'});
  await page.goto('/');
  await page.getByTestId('simple-prompt').fill('multi-question');
  await page.getByTestId('simple-send').click();
  const approval = page.getByTestId('simple-agent-approval');
  await approval.getByRole('checkbox',{name:/发光/}).check();
  await approval.getByRole('checkbox',{name:/飞行/}).check();
  expect(await page.evaluate(() => localStorage.getItem('agent-test-answer'))).toBeNull();
  await approval.getByTestId('simple-approval-answer').fill('附加冰冻效果');
  await approval.screenshot({path:'../output/simple-creation-fixes/question-light.png',animations:'disabled'});
  await page.getByTestId('theme-toggle-btn').click();
  await expect(page.locator('html')).toHaveAttribute('data-theme','dark');
  await page.setViewportSize({width:500,height:600});
  await approval.getByRole('button',{name:'提交回答',exact:true}).scrollIntoViewIfNeeded();
  await expect(approval.getByRole('button',{name:'提交回答',exact:true})).toBeInViewport();
  await page.screenshot({path:'../output/simple-creation-fixes/question-dark-compact.png',fullPage:true,animations:'disabled',style:'.scenario-switcher { visibility: hidden !important; }'});
  await approval.getByRole('button',{name:'提交回答',exact:true}).click();
  expect(await page.evaluate(() => JSON.parse(localStorage.getItem('agent-test-answer')!))).toMatchObject({choices:['glow','flight'],answer:'附加冰冻效果'});
  await page.getByTestId('simple-prompt').fill('custom-question');
  await page.getByTestId('simple-send').click();
  await approval.getByRole('radio',{name:'铜灯',exact:true}).check();
  await approval.getByTestId('simple-approval-answer').fill('月影灯');
  await expect(approval.getByRole('radio',{name:'铜灯',exact:true})).not.toBeChecked();
  await approval.getByRole('button',{name:'提交回答',exact:true}).click();
  expect(await page.evaluate(() => JSON.parse(localStorage.getItem('agent-test-answer')!))).toMatchObject({choices:[],answer:'月影灯'});
  await expect(page.getByRole('log')).toContainText('已收到：月影灯');
});

test('conversation and editor drafts survive mode changes, approvals, cancellation, and reopening', async ({ page }) => {
  await installAgentFixture(page);
  await page.goto('/');
  const prompt = page.getByTestId('simple-prompt');
  await prompt.fill('铜灯草稿');
  await page.getByTestId('workbench-mode-control').hover();
  await page.getByTestId('mode-professional').click();
  await page.getByTestId('nav-tools-toggle').click();
  await page.getByTestId('nav-python').click();
  await page.getByTestId('python-source').fill('print("retained editor")');
  await page.getByTestId('workbench-mode-control').hover();
  await page.getByTestId('mode-simple').click();
  await expect(prompt).toHaveValue('铜灯草稿');
  await prompt.dispatchEvent('keydown', {key:'Enter',code:'Enter',isComposing:true});
  expect(await page.evaluate(() => JSON.parse(localStorage.getItem('agent-test-calls') ?? '[]').filter((item:string) => item === 'send').length)).toBe(0);
  await page.getByTestId('simple-send').click();
  await expect(page.getByTestId('simple-stop')).toBeEnabled();
  await page.screenshot({path:'../output/simple-creation-validation/ui-running.png',fullPage:true});
  await page.getByTestId('workbench-mode-control').hover();
  await page.getByTestId('mode-professional').click();
  await expect(page.getByTestId('python-source')).toHaveValue('print("retained editor")');
  await page.getByTestId('workbench-mode-control').hover();
  await page.getByTestId('mode-simple').click();
  await page.getByTestId('simple-stop').click();
  await expect(page.getByRole('log')).toContainText('已停止');
  await prompt.fill('approval'); await page.getByTestId('simple-send').click();
  await expect(page.getByTestId('simple-agent-approval')).toBeVisible();
  await page.screenshot({path:'../output/simple-creation-validation/ui-approval.png',fullPage:true});
  await page.getByTestId('simple-agent-approval').getByRole('button',{name:'允许一次',exact:true}).click();
  await expect(page.getByRole('log')).toContainText('已收到：once');
  await prompt.fill('question'); await page.getByTestId('simple-send').click();
  await page.getByTestId('simple-approval-answer').fill('暖黄色');
  await page.getByTestId('simple-agent-approval').getByRole('button',{name:/提交|回答/}).click();
  await expect(page.getByRole('log')).toContainText('已收到：暖黄色');
  const sendCount = await page.evaluate(() => JSON.parse(localStorage.getItem('agent-test-calls') ?? '[]').filter((value:string) => value === 'send').length);
  await page.reload();
  await expect(page.getByRole('log')).toContainText('已收到：暖黄色');
  expect(await page.evaluate(() => JSON.parse(localStorage.getItem('agent-test-calls') ?? '[]').filter((value:string) => value === 'send').length)).toBe(sendCount);
});

test('all five backends keep separate chats and show blocked permission status', async ({ page }) => {
  await installAgentFixture(page);
  await page.goto('/');
  const provider = page.getByTestId('simple-provider');
  for (const id of ['claude','grok','deepseek','antigravity']) {
    await provider.click(); await page.getByTestId(`provider-option-${id}`).click();
  }
  await expect(provider).toHaveAttribute('data-provider-value','antigravity');
  await page.getByTestId('simple-prompt').fill('permission');
  await page.getByTestId('simple-send').click();
  await expect(page.getByTestId('simple-agent-error')).toContainText('未获授权');
  await page.screenshot({path:'../output/simple-creation-validation/ui-permission-blocked.png',fullPage:true});
  await expect(page.getByTestId('simple-agent-approval')).toHaveCount(0);
  await expect(page.locator('[data-testid^="simple-conversation-"]')).toHaveCount(4);
  await page.getByTestId('simple-history').click();
  await expect(page.getByTestId('simple-back')).toBeVisible();
  await page.getByTestId('simple-back').click();
  await expect(provider).toHaveAttribute('data-provider-value','antigravity');
});

test('missing CLI offers setup; simple creation generates technical defaults and native pickers fill paths', async ({ page }) => {
  await installAgentFixture(page,{installed:false});
  await page.goto('/');
  await expect(page.getByTestId('simple-provider-setup')).toBeVisible();
  await expect(page.getByTestId('simple-send')).toBeDisabled();
  await page.getByTestId('simple-new-workspace').click();
  await page.getByTestId('new-workspace-mod-name-input').fill('林间铜灯');
  await expect(page.getByTestId('new-workspace-mod-id-input')).toHaveValue(/^[a-z][a-z0-9_]{1,31}$/);
  await expect(page.getByTestId('new-workspace-package-input')).toBeHidden();
  await page.screenshot({path:'../output/simple-creation-validation/ui-new-work.png',fullPage:true});
  await page.getByRole('button',{name:'更多设置',exact:true}).click();
  await expect(page.getByTestId('new-workspace-package-input')).toBeVisible();
  await page.getByTestId('new-workspace-browse-btn').click();
  await expect(page.getByTestId('new-workspace-folder-input')).toHaveValue('D:/MCreatorWorkspaces/lantern');
  await page.getByTestId('simple-back').click();
  await page.getByRole('button',{name:'打开作品',exact:true}).click();
  await page.getByTestId('open-workspace-dialog').getByRole('button',{name:/浏览|选择文件/}).click();
  await expect(page.getByTestId('open-workspace-file')).toHaveValue('D:/Creations/lantern/lantern.mcreator');
  expect(await page.evaluate(() => localStorage.getItem('agent-test-opened-work'))).toBeNull();
});

test('desktop and compact layouts keep actions reachable with both themes', async ({ page }, testInfo) => {
  await installAgentFixture(page,{theme:'light'});
  await page.goto('/');
  await expect(page.getByTestId('simple-prompt')).toBeVisible();
  const expectModeTargets = async () => {
    await page.getByTestId('workbench-mode-control').hover();
    for (const id of ['mode-simple', 'mode-professional']) {
      const button = page.getByTestId(id);
      await expect(button).toBeInViewport({ratio:1});
      const bounds = await button.boundingBox();
      expect(bounds?.width).toBeGreaterThanOrEqual(32);
      expect(bounds?.height).toBeGreaterThanOrEqual(32);
    }
  };
  await page.setViewportSize({width:1600,height:1000});
  await expectModeTargets();
  await page.screenshot({path:'../output/simple-creation-validation/ui-light-1600.png',fullPage:true});
  await page.getByTestId('theme-toggle-btn').click();
  await page.setViewportSize({width:1366,height:768});
  await expectModeTargets();
  await page.screenshot({path:'../output/simple-creation-validation/ui-dark-1366.png',fullPage:true,animations:'disabled'});
  await page.setViewportSize({width:500,height:600});
  await expect(page.getByTestId('simple-prompt')).toBeInViewport();
  await expectModeTargets();
  await expect(page.getByTestId('simple-send')).toBeInViewport();
  const dimensions = await page.evaluate(() => ({width:document.documentElement.clientWidth,scroll:document.documentElement.scrollWidth}));
  expect(dimensions.scroll).toBeLessThanOrEqual(dimensions.width);
  await page.screenshot({path:`../output/simple-creation-validation/ui-compact-500-${testInfo.project.name}.png`,fullPage:true});
  await page.getByTestId('workbench-mode-control').hover();
  await page.getByTestId('mode-professional').click();
  const search = page.getByTestId('workbench-command-trigger');
  await expect(search).toBeInViewport({ratio:1});
  const bounds = await search.boundingBox();
  expect(bounds?.width).toBeGreaterThanOrEqual(32);
  expect(bounds?.height).toBeGreaterThanOrEqual(32);
  await search.click();
  await expect(page.getByRole('dialog')).toBeVisible();
});

test('provider icons identify choices and conversations with keyboard access and unclipped menus', async ({ page }) => {
  await installAgentFixture(page,{theme:'light'});
  await page.goto('/');
  const trigger = page.getByTestId('simple-provider');
  await expect(trigger.locator('[data-provider-icon="codex"]')).toBeVisible();
  await trigger.click();
  const menu = page.getByRole('listbox',{name:'选择创作助手'});
  await expect(menu.getByRole('option')).toHaveCount(5);
  for (const id of ['codex','claude','grok','antigravity','deepseek']) {
    const icon = page.getByTestId(`provider-option-${id}`).locator('img');
    await expect(icon).toBeVisible();
    await expect.poll(() => icon.evaluate(image => (image as HTMLImageElement).naturalWidth)).toBeGreaterThan(0);
  }
  await menu.screenshot({path:'../output/deepseek-harness-validation/menu-light.png',animations:'disabled'});
  await trigger.press('ArrowDown');
  await trigger.press('Enter');
  await expect(trigger).toHaveAttribute('data-provider-value','claude');
  await expect(page.locator('.creation-conversation-list [data-provider-icon="claude"]')).toBeVisible();
  await page.getByTestId('simple-prompt').fill('做一盏铜灯');
  await page.getByTestId('simple-send').click();
  await expect(page.locator('[data-message-role="assistant"] [data-provider-icon="claude"]')).toBeVisible();
  await expect(trigger).toBeDisabled();
  await page.getByTestId('simple-stop').click();
  await trigger.click(); await trigger.press('End'); await trigger.press('Enter');
  await expect(trigger).toHaveAttribute('data-provider-value','deepseek');
  await expect(page.locator('.creation-conversation-list [data-provider-icon="deepseek"]')).toBeVisible();
  await expect(page.getByRole('button',{name:'登录',exact:true})).toHaveCount(0);
  await page.getByRole('button',{name:'配置指南',exact:true}).click();
  expect(await page.evaluate(() => JSON.parse(localStorage.getItem('agent-test-calls') ?? '[]'))).toContain('openSetup');
  await page.getByTestId('simple-prompt').fill('给铜灯加个底座');
  await page.getByTestId('simple-send').click();
  await expect(page.locator('[data-message-role="assistant"] [data-provider-icon="deepseek"]')).toBeVisible();
  await page.getByTestId('simple-stop').click();
  await page.getByTestId('theme-toggle-btn').click();
  await expect(page.locator('html')).toHaveAttribute('data-theme','dark');
  await trigger.click();
  await menu.screenshot({path:'../output/deepseek-harness-validation/menu-dark.png',animations:'disabled'});
  await trigger.press('Escape');
  await expect(menu).toHaveCount(0); await expect(trigger).toBeFocused();
  await page.setViewportSize({width:500,height:600});
  await trigger.click();
  for (const id of ['codex','claude','grok','antigravity','deepseek']) await expect(page.getByTestId(`provider-option-${id}`)).toBeInViewport({ratio:1});
  await page.screenshot({path:'../output/deepseek-harness-validation/compact-menu.png',fullPage:true,animations:'disabled'});
  await page.getByTestId('simple-prompt').click();
  await expect(menu).toHaveCount(0);
  await expect(page.getByTestId('simple-prompt')).toBeFocused();
});

test('professional mode omits the large hidden list while repeated switches retain search, selection and drafts', async ({ page }) => {
  await installAgentFixture(page);
  await page.goto('/');
  const lamp = page.locator('[data-testid^="simple-element-"]').filter({ hasText: 'Copper Lamp' });
  await lamp.click();
  await page.getByRole('tab', { name: '设置', exact: true }).click();
  const parameter = page.locator('[data-testid^="simple-parameter-"]').first();
  await parameter.fill('Retained parameter draft');
  await page.getByTestId('simple-prompt').fill('Retained chat draft');
  await page.evaluate(async () => {
    const { coreBridge } = await import('/src/bridge/index.ts');
    const state = coreBridge.getState();
    const original = [...state.elements];
    state.elements = [...original, ...Array.from({ length: 2000 - original.length }, (_, index) => ({
      ...original[0], id: `scale-mode-${index}`, name: `scale_mode_${index}`, displayName: `Scale mode ${index}`
    }))];
    // Fixture-only state publication exercises the same provider subscription as native query notifications.
    (coreBridge as unknown as { notifyState(): void }).notifyState();
  });
  await expect(page.locator('[data-testid^="simple-element-"]')).toHaveCount(2000);
  const search = page.getByRole('searchbox', { name: '查找作品内容', exact: true });
  await search.fill('Copper Lamp');
  await expect(lamp).toHaveAttribute('aria-pressed', 'true');
  const detectCount = await page.evaluate(() => JSON.parse(localStorage.getItem('agent-test-calls') ?? '[]').filter((op: string) => op === 'detect').length);
  for (let attempt = 0; attempt < 3; attempt++) {
    await page.getByTestId('workbench-mode-control').hover();
    await page.getByTestId('mode-professional').click();
    await expect(page.locator('[data-testid^="simple-element-"]')).toHaveCount(0);
    await page.getByTestId('nav-data').click();
    await page.getByTestId('creator-data-view').getByRole('button', { name: '刷新', exact: true }).click();
    await expect(page.locator('[data-testid^="simple-element-"]')).toHaveCount(0);
    expect(await unsavedDrafts(page)).toBe(true);
    await page.getByTestId('workbench-mode-control').hover();
    await page.getByTestId('mode-simple').click();
    await expect(search).toHaveValue('Copper Lamp');
    await expect(lamp).toHaveAttribute('aria-pressed', 'true');
    await expect(parameter).toHaveValue('Retained parameter draft');
    await expect(page.getByTestId('simple-prompt')).toHaveValue('Retained chat draft');
  }
  await search.fill('');
  await expect(page.locator('[data-testid^="simple-element-"]')).toHaveCount(2000);
  expect(await page.evaluate(() => JSON.parse(localStorage.getItem('agent-test-calls') ?? '[]').filter((op: string) => op === 'detect').length)).toBe(detectCount);
});


test('switching modes during an unresolved send preserves the pending operation, approval and next draft', async ({ page }) => {
  await installAgentFixture(page);
  await page.goto('/');
  await expect(page.getByTestId('simple-prompt')).toBeVisible();
  await page.evaluate(() => {
    const host = window.__COPPERBENCH_AGENT_HOST__!;
    const original = host.invoke.bind(host);
    let release!: () => void;
    const blocked = new Promise<void>(resolve => { release = resolve; });
    Object.assign(window, { releaseScaleModeSend: release });
    host.invoke = async request => {
      if (request.operation === 'send') { sessionStorage.setItem('scale-mode-send-pending', 'true'); await blocked; }
      return original(request);
    };
  });
  await page.getByTestId('simple-prompt').fill('approval');
  await expect(page.getByTestId('simple-send')).toBeEnabled();
  const detectCount = await page.evaluate(() => JSON.parse(localStorage.getItem('agent-test-calls') ?? '[]').filter((op: string) => op === 'detect').length);
  await page.getByTestId('simple-send').click();
  await expect.poll(() => page.evaluate(() => sessionStorage.getItem('scale-mode-send-pending'))).toBe('true');
  for (let attempt = 0; attempt < 3; attempt++) {
    await page.getByTestId('workbench-mode-control').hover();
    await page.getByTestId('mode-professional').click();
    await expect(page.locator('[data-testid^="simple-element-"]')).toHaveCount(0);
    await page.getByTestId('workbench-mode-control').hover();
    await page.getByTestId('mode-simple').click();
    await expect(page.getByTestId('simple-send')).toBeDisabled();
    await expect(page.getByTestId('simple-prompt')).toHaveValue('approval');
  }
  // Complete the outstanding operation while its controls are hidden.
  await page.getByTestId('workbench-mode-control').hover();
  await page.getByTestId('mode-professional').click();
  await page.evaluate(() => (window as unknown as { releaseScaleModeSend(): void }).releaseScaleModeSend());
  await page.getByTestId('workbench-mode-control').hover();
  await page.getByTestId('mode-simple').click();
  await expect(page.getByTestId('simple-agent-approval')).toBeVisible();
  await expect(page.getByTestId('simple-prompt')).toHaveValue('');
  await page.getByTestId('simple-prompt').fill('Next task draft');
  await page.getByTestId('workbench-mode-control').hover();
  await page.getByTestId('mode-professional').click();
  await page.getByTestId('workbench-mode-control').hover();
  await page.getByTestId('mode-simple').click();
  await expect(page.getByTestId('simple-agent-approval')).toBeVisible();
  await expect(page.getByTestId('simple-prompt')).toHaveValue('Next task draft');
  await page.getByTestId('simple-stop').click();
  await expect(page.getByTestId('simple-agent-approval')).toHaveCount(0);
  await expect(page.getByRole('log')).toContainText('已停止');
  await expect(page.getByTestId('simple-prompt')).toHaveValue('Next task draft');
  const calls: string[] = await page.evaluate(() => JSON.parse(localStorage.getItem('agent-test-calls') ?? '[]'));
  expect(calls.filter(op => op === 'detect')).toHaveLength(detectCount);
  expect(calls.filter(op => op === 'send')).toHaveLength(1);
  expect(calls.filter(op => op === 'stop')).toHaveLength(1);
});
