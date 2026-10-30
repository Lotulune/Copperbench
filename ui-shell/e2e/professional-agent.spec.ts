import { expect, test, type Page } from '@playwright/test';

async function fixture(page: Page, options: { installed?: boolean; english?: boolean; theme?: string; assistantReply?: string; holdRun?: boolean; approval?: boolean; settingsSupport?: boolean; catalogFailure?: boolean } = {}) {
  await page.addInitScript(options => {
    localStorage.setItem('copperbench.workbench.mode', 'professional');
    if (options.english) localStorage.setItem('copperbench.ui.locale', 'en');
    if (options.theme) localStorage.setItem('copperbench.theme', options.theme);
    const saved = JSON.parse(localStorage.getItem('professional-agent-fixture') ?? '{}');
    const messages = saved.messages ?? [], runs = saved.runs ?? [];
    let state = saved.state ?? 'idle', sequence = 0, conversationId = saved.conversationId ?? null;
    let selectedRun = saved.selectedRun ?? null;
    let settings = saved.settings ?? { model: '', effort: '', permissionProfile: 'workspace' };
    let settingsExplicit = saved.settingsExplicit ?? false;
    let modelCatalogs = {};
    let activeVerification: { runId: string; cancelling: boolean } | undefined;
    let exportedRun: { runId: string; path: string } | undefined;
    let approvals: { id: string; title: string; detail: string; choices: { id: string; label: string }[] }[] = [];
    const providers = [{ id: 'codex', label: 'Codex CLI', installed: options.installed !== false,
      executable: options.installed === false ? null : 'C:/Tools/codex.exe', setupUrl: 'https://developers.openai.com/codex/cli/', loginAvailable: true,
      version: 'codex-cli 0.162.1', capabilities: { structuredEvents: true, interactivePermission: true, sessionResume: true, cancellation: 'both', usageReporting: false, permissionProfile: 'workspace-write / on-request', runSettings: options.settingsSupport !== false } }];
    const calls: unknown[] = JSON.parse(localStorage.getItem('professional-agent-calls') ?? '[]');
    const snapshot = () => ({ state, sequence: ++sequence, conversationId, provider: conversationId ? 'codex' : null,
      sessions: conversationId ? [{ id: conversationId, provider: 'codex', title: 'Repair the project', updatedAt: 1 }] : [],
      messages, approvals, providers, runs, ...(selectedRun ? { run: selectedRun } : {}), project: { id: 'canonical-project', canonicalRoot: 'D:/Creations/铜灯 project', executionTargetId: 'local-native:Windows' },
      verificationAvailable: true, enabled: true, activeVerification, exportedRun, settings, settingsExplicit, modelCatalogs,
      model: settings.model || null, usesSessionModel: Boolean(conversationId && !settings.model), effort: settings.effort || undefined,
      permission: settings.permissionProfile === 'workspace' ? 'default' : settings.permissionProfile });
    const save = () => localStorage.setItem('professional-agent-fixture', JSON.stringify({ state, conversationId, messages, runs, selectedRun, settings, settingsExplicit }));
    const file = { id: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', name: 'notes.txt', size: 12, mediaType: 'application/octet-stream' as const };
    window.__COPPERBENCH_AGENT_HOST__ = { nativeFileSelection: true, async invoke(request) {
      if (request.operation === 'attachmentTarget') return { ok: true, target: request.target };
      if (request.operation === 'attachmentSelect') return { ok: true, attachments: [file] };
      if (request.operation === 'attachmentDiscard') return { ok: true };
      calls.push(request); localStorage.setItem('professional-agent-calls', JSON.stringify(calls));
      if (request.operation === 'send') {
        if (request.settings) { settings = request.settings; settingsExplicit = true; }
        conversationId ??= 'fixture-conversation';
        messages.push({ id: crypto.randomUUID(), role: 'user', text: request.text, at: Date.now(), ...(request.attachments?.length ? { attachments: [file] } : {}) });
        selectedRun = { runId: crypto.randomUUID(), conversationId, state: options.holdRun || options.approval || request.text.includes('wait') ? 'running' : 'completed', agentId: 'codex',
          createdAt: Date.now(), adapterVersion: 'copperbench-1', cliVersion: 'codex-cli 0.162.1', canonicalRoot: 'D:/Creations/铜灯 project', executionTargetId: 'local-native:Windows',
          execution: { cleanup: 'confirmed', exitCode: 0, endReason: 'provider_completed' }, context: { task: request.text, attachments: request.context ?? [], permissionProfile: 'workspace-write / on-request' },
          agentStatement: 'The requested file was updated.', changes: { coverage: 'complete', attribution: 'mixed_or_unknown', scope: 'Fixture source files', files: [{ path: 'src/Example.java', kind: 'modified', before: { text: '// existing user edit\nreturn 1;' }, after: { text: '// existing user edit\nreturn 2;' } }] },
          verification: { status: 'not_run' } };
        runs.unshift(selectedRun); state = selectedRun.state === 'running' ? 'running' : 'idle';
        messages.push({ id: crypto.randomUUID(), role: 'assistant', text: options.assistantReply ?? 'The requested file was updated.', at: Date.now() });
        if (options.approval) {
          state = 'waiting_approval';
          approvals = [{ id: 'approval-build', title: '允许运行构建？', detail: '将在当前工作区执行 build_workspace。', choices: [{ id: 'allow', label: '允许这次' }, { id: 'deny', label: '拒绝' }] }];
        }
      } else if (request.operation === 'new') {
        conversationId = crypto.randomUUID(); messages.splice(0); runs.splice(0); selectedRun = null; state = 'idle'; settingsExplicit = false;
        settings = { model: request.model ?? '', effort: request.effort ?? '', permissionProfile: request.permission === 'default' || !request.permission ? 'workspace' : request.permission };
      } else if (request.operation === 'settingsCatalog') {
        modelCatalogs = { codex: options.catalogFailure ? { state: 'error', models: [], permissionProfiles: [], error: 'CLI catalog unavailable' }
          : { state: 'ready', models: [{ id: 'gpt-6.1-sol', label: 'GPT-6.1 Sol', efforts: ['low', 'medium', 'high', 'xhigh'], defaultEffort: 'medium' },
              { id: 'gpt-6-sol', label: 'GPT-6 Sol', efforts: ['low', 'high'], defaultEffort: 'low' }], permissionProfiles: ['workspace', 'read-only', 'full-access'] } };
      } else if (request.operation === 'stop') {
        state = 'stopping'; selectedRun.state = 'cancelling'; approvals = [];
        setTimeout(() => { state = 'idle'; selectedRun.state = 'cancelled'; save(); }, 800);
      } else if (request.operation === 'approve') {
        approvals = []; state = 'idle'; selectedRun.state = 'completed';
      } else if (request.operation === 'selectRun') selectedRun = runs.find((run: { runId: string }) => run.runId === request.runId);
      else if (request.operation === 'verify') {
        selectedRun.verification = { status: 'running', executionTaskId: 'core-verification-task', command: 'build_workspace', directory: selectedRun.canonicalRoot };
        activeVerification = { runId: selectedRun.runId, cancelling: false };
      } else if (request.operation === 'stopVerification') {
        selectedRun.verification.status = 'cancelled'; activeVerification = undefined;
      } else if (request.operation === 'exportRun') {
        exportedRun = { runId: request.runId, path: 'D:/Creations/铜灯 project/.copperbench/agent-chat/exports/record.json' };
      } else if (request.operation === 'refreshRun') {
        selectedRun.verification = { status: 'failed', command: 'build_workspace', evidence: { exitCode: 1, logs: ['Compilation failed'] } }; activeVerification = undefined;
      }
      save(); return snapshot();
    } };
  }, options);
}
async function calls(page: Page, operation: string) {
  return page.evaluate(operation => JSON.parse(localStorage.getItem('professional-agent-calls') ?? '[]').filter((call: { operation: string }) => call.operation === operation), operation);
}

test('professional mode opens an optional panel and retains its draft across closing and mode switching', async ({ page }) => {
  await fixture(page); await page.goto('/'); await page.getByTestId('professional-agent-toggle').click();
  const prompt = page.getByTestId('professional-agent-prompt');
  await expect(prompt).toBeFocused(); await prompt.fill('Preserve this unsent draft');
  await prompt.press('Escape'); await expect(page.getByTestId('professional-agent-panel')).toBeHidden();
  await expect(page.getByTestId('professional-agent-toggle')).toBeFocused();
  await page.getByTestId('workbench-mode-control').hover(); await page.getByTestId('mode-simple').click(); await page.getByTestId('workbench-mode-control').hover(); await page.getByTestId('mode-professional').click();
  await page.getByTestId('professional-agent-toggle').click(); await expect(prompt).toHaveValue('Preserve this unsent draft');
  expect(await calls(page, 'send')).toHaveLength(0);
});

test('one session survives hiding, mode switching and reload; stopping remains observable', async ({ page }) => {
  await fixture(page); await page.goto('/'); await page.getByTestId('professional-agent-toggle').click();
  await page.getByTestId('professional-agent-prompt').fill('wait for me'); await page.getByTestId('professional-agent-send').click();
  await expect(page.getByTestId('professional-agent-stop')).toBeVisible();
  await page.getByTestId('professional-agent-prompt').press('Escape');
  await expect(page.getByTestId('agent-activity')).toBeVisible();
  await page.getByTestId('workbench-mode-control').hover(); await page.getByTestId('mode-simple').click(); await expect(page.getByTestId('simple-stop')).toBeVisible();
  await page.getByTestId('workbench-mode-control').hover(); await page.getByTestId('mode-professional').click(); await page.reload();
  await page.getByTestId('professional-agent-toggle').click();
  expect(await calls(page, 'send')).toHaveLength(1);
  await page.getByTestId('professional-agent-stop').click(); await expect(page.getByTestId('professional-agent-stop')).toBeDisabled();
  await expect(page.getByTestId('professional-agent-stop')).toHaveCount(0);
  expect(await calls(page, 'stop')).toHaveLength(1);
});

test('completion, fixed before/after changes and verification failure have independent labels', async ({ page }) => {
  await fixture(page); await page.goto('/'); await page.getByTestId('professional-agent-toggle').click();
  await page.getByTestId('professional-agent-prompt').fill('Repair the project'); await page.getByTestId('professional-agent-send').click();
  const panel = page.getByTestId('professional-agent-panel');
  await panel.getByRole('tab', { name: '变化与验证' }).click();
  await expect(page.getByTestId('agent-verification-status')).toHaveText('尚未验证');
  await panel.locator('summary').filter({ hasText: 'src/Example.java' }).click();
  await expect(panel.locator('pre').filter({ hasText: '// existing user edit\nreturn 1;' })).toBeVisible();
  await panel.getByRole('button', { name: '运行所选验证' }).click(); await expect(page.getByTestId('professional-agent-stop')).toHaveText('停止验证');
  await page.getByTestId('professional-agent-stop').click(); await expect(page.getByTestId('agent-verification-status')).toHaveText('验证已取消');
  await expect(panel.getByRole('heading', { name: 'Agent 已结束', exact: true })).toBeVisible();
  await page.evaluate(() => window.__COPPERBENCH_AGENT_HOST__!.invoke({ operation: 'refreshRun' }));
  await expect(page.getByTestId('agent-verification-status')).toHaveText('验证未通过');
  await panel.getByRole('button', { name: '导出本次脱敏记录' }).click();
  await expect(panel.getByText('已保存：', { exact: false })).toContainText('exports/record.json');
  expect(await calls(page, 'send')).toHaveLength(1);
});

test('missing CLI has an installation path and leaves professional editors usable', async ({ page }) => {
  await fixture(page, { installed: false }); await page.goto('/'); await page.getByTestId('professional-agent-toggle').click();
  await expect(page.getByRole('button', { name: '官方安装指引' })).toBeVisible(); await expect(page.getByTestId('professional-agent-send')).toBeDisabled();
  await page.getByRole('button', { name: '收起 Agent 面板' }).click(); await page.getByTestId('nav-elements').click();
  await expect(page.getByTestId('nav-elements')).toHaveAttribute('aria-current', 'page');
});

test('English, light theme and a narrow viewport retain usable controls without overflow', async ({ page }) => {
  await fixture(page, { english: true, theme: 'light' }); await page.setViewportSize({ width: 900, height: 700 }); await page.goto('/');
  await page.getByTestId('professional-agent-toggle').click(); const panel = page.getByTestId('professional-agent-panel');
  await expect(panel.getByRole('heading', { name: 'Project Agent' })).toBeVisible();
  await page.getByTestId('professional-agent-prompt').fill('Review this file'); await page.getByTestId('professional-agent-send').click();
  await panel.getByRole('tab', { name: 'Changes & verification' }).click();
  await expect(page.getByTestId('agent-verification-status')).toHaveText('Not yet verified');
  expect(await panel.evaluate(element => element.scrollWidth <= element.clientWidth)).toBe(true);
  await page.screenshot({ path: '../output/professional-agent/ui-english-900.png', fullPage: true });
});

test('the desktop result panel uses the existing workbench layout', async ({ page }) => {
  await fixture(page); await page.setViewportSize({ width: 1366, height: 768 }); await page.goto('/');
  await page.getByTestId('professional-agent-toggle').click(); await page.getByTestId('professional-agent-prompt').fill('Repair the project');
  await page.getByTestId('professional-agent-send').click(); const panel = page.getByTestId('professional-agent-panel');
  await panel.getByRole('tab', { name: '变化与验证' }).click();
  await panel.locator('summary').filter({ hasText: 'src/Example.java' }).click();
  await page.screenshot({ path: '../output/professional-agent/ui-results-1366.png', fullPage: true });
});

test('a failed task attaches a reviewable log excerpt without sending it automatically', async ({ page }) => {
  await fixture(page); await page.goto('/');
  await page.getByTestId('scenario-switcher-trigger').click(); await page.getByTestId('scenario-btn-compile-diagnostic').click();
  await page.getByTestId('open-failed-task-logs-btn').click(); await page.getByTestId('task-analyze-agent').click();
  await expect(page.getByTestId('professional-agent-panel')).toBeVisible();
  await expect(page.getByTestId('professional-agent-prompt')).toHaveValue('请分析这次任务的结果并修复问题。');
  expect(await calls(page, 'send')).toHaveLength(0);
  await page.getByTestId('professional-agent-send').click();
  const submitted = await calls(page, 'send'); expect(submitted[0].context[0].kind).toBe('task-log');
  expect(submitted[0].context[0].text).toContain('JAVA_COMPILE_ERROR');
});

test('the source entry previews the selected file and allows removing the attachment', async ({ page }) => {
  await fixture(page); await page.goto('/'); await page.getByTestId('nav-source').click();
  await page.locator('[data-source-path="src/main/java/example/ExampleMod.java"]').click();
  await page.getByTestId('source-analyze-agent').click(); const panel = page.getByTestId('professional-agent-panel');
  await expect(page.getByTestId('professional-agent-prompt')).toHaveValue('请分析这个文件并说明需要修改的地方。');
  expect(await calls(page, 'send')).toHaveLength(0);
  await panel.locator('.professional-agent-attachment summary').click();
  await expect(panel.getByRole('textbox', { name: '附带内容', exact: true })).toContainText('ExampleMod');
  await panel.getByRole('button', { name: '移除附件' }).click();
  await expect(panel.locator('.professional-agent-attachment')).toHaveCount(0);
});

test('the professional selector gives its icon, label and chevron a shared center and visible spacing', async ({ page }) => {
  await fixture(page, { theme: 'light' }); await page.setViewportSize({ width: 1366, height: 768 }); await page.goto('/');
  await page.getByTestId('professional-agent-toggle').click();
  const trigger = page.getByTestId('professional-provider');
  const geometry = await trigger.evaluate(button => {
    const box = button.getBoundingClientRect();
    const icon = button.querySelector('img')!.getBoundingClientRect(), label = button.querySelector('span')!.getBoundingClientRect();
    const chevron = button.querySelector('svg')!.getBoundingClientRect();
    return { height: box.height, iconGap: label.left - icon.right, chevronGap: chevron.left - label.right,
      iconOffset: Math.abs(icon.top + icon.height / 2 - label.top - label.height / 2),
      chevronOffset: Math.abs(chevron.top + chevron.height / 2 - label.top - label.height / 2) };
  });
  expect(geometry.height).toBeGreaterThanOrEqual(36);
  expect(geometry.iconGap).toBeGreaterThanOrEqual(8); expect(geometry.chevronGap).toBeGreaterThanOrEqual(8);
  expect(geometry.iconOffset).toBeLessThanOrEqual(1); expect(geometry.chevronOffset).toBeLessThanOrEqual(1);
  await expect(page.getByTestId('professional-agent-prompt')).toBeInViewport();
  await page.screenshot({ path: '../output/professional-agent-ui-polish/conversation-empty-1366.png', fullPage: true, animations: 'disabled' });
  await trigger.press('ArrowDown'); await expect(page.getByRole('listbox', { name: '选择创作助手' })).toBeVisible();
  await page.screenshot({ path: '../output/professional-agent-ui-polish/provider-menu-1366.png', fullPage: true, animations: 'disabled' });
  await trigger.press('Escape'); await expect(page.getByRole('listbox')).toHaveCount(0);
  await expect(page.getByTestId('professional-agent-panel')).toBeVisible(); await expect(trigger).toBeFocused();
});

test('conversation, composer and follow-up sending stay available when inspecting changes', async ({ page }) => {
  await fixture(page, { theme: 'light', assistantReply: '铜灯的亮度已调整为 12，配方和纹理保持不变。\n\n你可以在“变化与验证”中查看修改，再运行构建检查。' });
  await page.setViewportSize({ width: 1366, height: 768 }); await page.goto('/'); await page.getByTestId('professional-agent-toggle').click();
  const prompt = page.getByTestId('professional-agent-prompt'), panel = page.getByTestId('professional-agent-panel');
  await prompt.fill('把铜灯的亮度改成 12，保留原来的配方和纹理。'); await prompt.press('Control+Enter');
  await expect(panel.getByRole('log')).toContainText('铜灯的亮度已调整为 12');
  await prompt.fill('请再检查一下：手持铜灯时是否也会发光？');
  await expect(prompt).toBeInViewport(); await expect(page.getByTestId('professional-agent-send')).toBeEnabled();
  await page.screenshot({ path: '../output/professional-agent-ui-polish/conversation-light-1366.png', fullPage: true, animations: 'disabled' });
  await panel.getByRole('tab', { name: '变化与验证' }).click();
  await expect(prompt).toBeVisible(); await expect(prompt).toBeInViewport();
  await expect(prompt).toHaveValue('请再检查一下：手持铜灯时是否也会发光？');
  await page.screenshot({ path: '../output/professional-agent-ui-polish/results-with-composer-1366.png', fullPage: true, animations: 'disabled' });
  await prompt.press('Control+Enter');
  await expect(panel.getByRole('tab', { name: '对话', exact: true })).toHaveAttribute('aria-selected', 'true');
  expect(await calls(page, 'send')).toHaveLength(2);
});

test('the compact dark conversation keeps the running stop action and input reachable', async ({ page }) => {
  await fixture(page, { theme: 'dark', english: true, holdRun: true, assistantReply: 'I am checking the Copper Lamp settings and its references before changing the light level.' });
  await page.setViewportSize({ width: 900, height: 700 }); await page.goto('/'); await page.getByTestId('professional-agent-toggle').click();
  await page.getByTestId('professional-agent-prompt').fill('Set the Copper Lamp light level to 12 and keep its recipe unchanged.');
  await page.getByTestId('professional-agent-send').click();
  await expect(page.getByTestId('professional-agent-stop')).toBeInViewport(); await expect(page.getByTestId('professional-agent-prompt')).toBeInViewport();
  const panel = page.getByTestId('professional-agent-panel');
  expect(await panel.evaluate(element => element.scrollWidth <= element.clientWidth)).toBe(true);
  await page.screenshot({ path: '../output/professional-agent-ui-polish/conversation-running-dark-900.png', fullPage: true, animations: 'disabled' });
  await page.getByTestId('professional-agent-stop').click(); await expect(page.getByTestId('professional-agent-send')).toBeVisible();
});

test('professional approval choices have normal button spacing and still return the chosen decision', async ({ page }) => {
  await fixture(page, { theme: 'light', approval: true, assistantReply: '配置已经检查完毕。接下来需要运行构建，确认当前修改可以编译。' });
  await page.setViewportSize({ width: 1366, height: 768 }); await page.goto('/'); await page.getByTestId('professional-agent-toggle').click();
  await page.getByTestId('professional-agent-prompt').fill('检查铜灯的修改，并运行构建。'); await page.getByTestId('professional-agent-send').click();
  const approval = page.getByTestId('professional-agent-approval'); await expect(approval).toBeVisible();
  const allow = approval.getByRole('button', { name: '允许这次' }), deny = approval.getByRole('button', { name: '拒绝' });
  await allow.scrollIntoViewIfNeeded(); const left = (await allow.boundingBox())!, right = (await deny.boundingBox())!;
  expect(left.height).toBeGreaterThanOrEqual(32); expect(right.x - left.x - left.width).toBeGreaterThanOrEqual(8);
  await expect(page.getByTestId('professional-agent-prompt')).toBeInViewport();
  await page.screenshot({ path: '../output/professional-agent-ui-polish/approval-1366.png', fullPage: true, animations: 'disabled' });
  const panel = page.getByTestId('professional-agent-panel');
  await panel.getByRole('tab', { name: '变化与验证' }).click(); await expect(approval).toBeVisible(); await expect(deny).toBeInViewport();
  await panel.getByRole('button', { name: '收起 Agent 面板' }).click();
  await expect(page.getByTestId('professional-agent-toggle')).toHaveText('Agent · 等待你的处理');
  await page.getByTestId('professional-agent-toggle').click();
  await deny.click(); await expect(approval).toHaveCount(0);
  const responses = await calls(page, 'approve'); expect(responses).toHaveLength(1); expect(responses[0].choice).toBe('deny');
});

test('model, effort and permissions are sent together and restored for the conversation', async ({ page }) => {
  await fixture(page, { theme: 'light' }); await page.setViewportSize({ width: 1366, height: 768 }); await page.goto('/');
  await page.getByTestId('professional-agent-toggle').click(); await page.getByTestId('agent-settings-toggle').click();
  const settings = page.getByRole('dialog', { name: 'Agent 运行设置' });
  await expect(page.getByTestId('agent-effort')).toBeDisabled();
  await page.getByTestId('agent-model').selectOption('gpt-6.1-sol'); await page.getByTestId('agent-effort').selectOption('high');
  await page.getByTestId('agent-permissions').selectOption('read-only');
  await expect(page.getByTestId('professional-agent-prompt')).toBeInViewport();
  await page.screenshot({ path: '../output/professional-agent-settings/settings-light-1366.png', fullPage: true, animations: 'disabled' });
  await settings.getByRole('button', { name: '完成', exact: true }).click();
  await page.getByTestId('professional-agent-prompt').fill('Analyze the current project without changing files.');
  await page.getByTestId('professional-agent-send').click();
  expect((await calls(page, 'send'))[0].settings).toEqual({ model: 'gpt-6.1-sol', effort: 'high', permissionProfile: 'read-only' });
  await page.reload(); await page.getByTestId('professional-agent-toggle').click(); await page.getByTestId('agent-settings-toggle').click();
  await expect(page.getByTestId('agent-model')).toHaveValue('gpt-6.1-sol'); await expect(page.getByTestId('agent-effort')).toHaveValue('high');
  await expect(page.getByTestId('agent-permissions')).toHaveValue('read-only');
});

test('changing model clears incompatible effort and active turns keep their settings locked', async ({ page }) => {
  await fixture(page, { english: true, theme: 'dark', holdRun: true }); await page.setViewportSize({ width: 900, height: 700 }); await page.goto('/');
  await page.getByTestId('professional-agent-toggle').click(); await page.getByTestId('agent-settings-toggle').click();
  await page.getByTestId('agent-model').selectOption('gpt-6.1-sol'); await page.getByTestId('agent-effort').selectOption('medium');
  await page.getByTestId('agent-model').selectOption('gpt-6-sol'); await expect(page.getByTestId('agent-effort')).toHaveValue('');
  await expect(page.getByTestId('agent-effort').getByRole('option', { name: 'Medium', exact: true })).toHaveCount(0);
  await page.getByTestId('agent-effort').selectOption('high'); await page.getByTestId('agent-permissions').selectOption('full-access');
  await expect(page.getByText('Allows reading and writing outside the workspace;', { exact: false })).toBeVisible();
  await page.screenshot({ path: '../output/professional-agent-settings/settings-dark-900.png', fullPage: true, animations: 'disabled' });
  await page.getByRole('button', { name: 'Done', exact: true }).click();
  await page.getByTestId('professional-agent-prompt').fill('Check this project.'); await page.getByTestId('professional-agent-send').click();
  await page.getByTestId('agent-settings-toggle').click();
  await expect(page.getByTestId('agent-model')).toBeDisabled(); await expect(page.getByTestId('agent-effort')).toBeDisabled(); await expect(page.getByTestId('agent-permissions')).toBeDisabled();
  await page.getByRole('dialog', { name: 'Agent run settings' }).press('Escape');
  await expect(page.getByTestId('professional-agent-panel')).toBeVisible(); await expect(page.getByTestId('agent-settings-toggle')).toBeFocused();
  await page.getByTestId('professional-agent-stop').click(); await expect(page.getByTestId('professional-agent-stop')).toHaveCount(0);
});

test('unsupported hosts and failed catalogs retain honest defaults without fabricated choices', async ({ page }) => {
  await fixture(page, { settingsSupport: false }); await page.goto('/'); await page.getByTestId('professional-agent-toggle').click();
  await page.getByTestId('agent-settings-toggle').click(); await expect(page.getByTestId('agent-model')).toHaveCount(0);
  await expect(page.getByText('使用上方的模型和运行选项。可接受的思考强度由当前 CLI 和模型决定。')).toBeVisible();
  expect(await calls(page, 'settingsCatalog')).toHaveLength(0);
});

test('a catalog failure is actionable and does not prevent sending with the existing defaults', async ({ page }) => {
  await fixture(page, { catalogFailure: true }); await page.goto('/'); await page.getByTestId('professional-agent-toggle').click();
  await page.getByTestId('agent-settings-toggle').click(); await expect(page.getByRole('alert')).toHaveText('CLI catalog unavailable');
  await expect(page.getByTestId('agent-model').getByRole('option')).toHaveCount(1); await expect(page.getByTestId('agent-effort')).toBeDisabled();
  await expect(page.getByRole('button', { name: '重新读取模型' })).toBeEnabled();
  await page.getByRole('button', { name: '完成', exact: true }).click(); await page.getByTestId('professional-agent-prompt').fill('Analyze with current defaults.');
  await page.getByTestId('professional-agent-send').click();
  expect((await calls(page, 'send'))[0].settings).toBeUndefined();
});

test('changing an existing conversation model carries its unsent draft and native attachments into the new conversation', async ({ page }) => {
  await fixture(page); await page.goto('/'); await page.getByTestId('professional-agent-toggle').click();
  const prompt = page.getByTestId('professional-agent-prompt');
  await prompt.fill('Start with the CLI default model.'); await page.getByTestId('professional-agent-send').click();
  await expect(prompt).toHaveValue('');
  const previous = await page.getByRole('combobox', { name: 'Agent 会话历史' }).inputValue();
  await prompt.fill('Use the selected model to review these notes.');
  await page.getByRole('button', { name: '添加文件和图片' }).click();
  await expect(page.getByTestId('agent-attachment')).toHaveAttribute('data-state', 'ready');
  await page.getByTestId('agent-settings-toggle').click(); await page.getByTestId('agent-model').selectOption('gpt-6.1-sol');
  await expect.poll(async () => (await calls(page, 'new')).length).toBe(1);
  await expect(page.getByRole('combobox', { name: 'Agent 会话历史' })).not.toHaveValue(previous);
  await expect(prompt).toHaveValue('Use the selected model to review these notes.');
  await expect(page.getByTestId('agent-attachment')).toContainText('notes.txt');
  await page.getByTestId('professional-agent-send').click();
  const sent = (await calls(page, 'send'))[1];
  expect(sent.conversationId).not.toBe(previous);
  expect(sent.settings).toEqual({ model: 'gpt-6.1-sol', effort: '', permissionProfile: 'workspace' });
  expect(sent.attachments).toEqual(['aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa']);
  await expect(prompt).toHaveValue('');
  await expect(page.getByRole('log', { name: 'Agent 对话记录' }).getByTestId('agent-attachment')).toHaveAttribute('data-state', 'sent');
  await expect(page.getByRole('button', { name: '移除 notes.txt' })).toHaveCount(0);
});
