import { test, expect, type Page } from '@playwright/test';
import { openToolView } from './navigation';

type Locale = 'zh' | 'en';

// These interactions use the development MockCoreBridge. They verify live UI
// translation and retained input, not native authorization or Minecraft behavior.
async function openWorkbench(page: Page, locale: Locale) {
  await page.addInitScript(value => localStorage.setItem('copperbench.ui.locale', value), locale);
  await page.goto('/');
  await expect(page.getByTestId('app-shell')).toBeVisible();
  await expect(page.getByTestId('ui-language-select')).toHaveValue(locale);
}

async function switchLocale(page: Page, locale: Locale) {
  await page.getByTestId('ui-language-select').selectOption(locale);
  await expect(page.locator('html')).toHaveAttribute('lang', locale === 'zh' ? 'zh-CN' : 'en');
}

const capabilityNames = {
  zh: ['创建工作区', '修改内容', '生成与构建', '校验与测试', '运行客户端', '运行专用服务器', '恢复历史版本'],
  en: ['Create workspace', 'Edit content', 'Generate and build', 'Validate and test', 'Run client', 'Run dedicated server', 'Restore history']
};

const historySourceNames = {
  zh: ['全部来源', '手动', '自动操作', '工作区计划', 'Procedure', '资产', 'Datagen', '注册表', 'Blockbench', '还原保护'],
  en: ['All sources', 'Manual', 'Automated operation', 'Workspace plan', 'Procedure', 'Asset', 'Datagen', 'Registry', 'Blockbench', 'Restore protection']
};

const historyActorNames = {
  zh: ['全部操作者', '界面', 'MCP', 'Headless', '旧版界面', '系统'],
  en: ['All actors', 'Interface', 'MCP', 'Headless', 'Legacy interface', 'System']
};

const snippetLabels = {
  zh: ['execute 以所有玩家身份执行', 'title 显示标题', 'give 给予物品', 'particle 生成粒子', 'scoreboard 计分板', 'tag 标签操作', '# 注释说明'],
  en: ['execute — Run as all players', 'title — Display a title', 'give — Give an item', 'particle — Spawn particles', 'scoreboard — Scoreboard', 'tag — Entity tags', '# Comment']
};

for (const initial of ['zh', 'en'] as const) {
  const target = initial === 'zh' ? 'en' : 'zh';

  test(`${initial} to ${target}: authorization capabilities, review and issued summary follow the locale without changing the scope`, async ({ page }, testInfo) => {
    await openWorkbench(page, initial);
    await openToolView(page, 'ai');
    const panel = page.locator('.task-authority-panel');
    const choices = panel.locator('.task-authority-capabilities label');
    await expect(panel.locator('#task-authority-root')).toHaveValue('D:/MockWorkspace');
    await expect(choices).toHaveText(capabilityNames[initial]);

    const label = 'Release review 发布验收';
    const root = 'D:/MockWorkspace/locale-draft';
    const selected = [true, false, true, true, false, true, true];
    await panel.locator('#task-authority-label').fill(label);
    await panel.locator('#task-authority-root').fill(root);
    await panel.locator('#task-authority-ttl').selectOption('3600');
    for (const [index, checked] of selected.entries()) {
      await choices.nth(index).getByRole('checkbox').setChecked(checked);
    }
    await panel.locator('.task-authority-eula input').check();

    await switchLocale(page, target);
    await expect(choices).toHaveText(capabilityNames[target]);
    await expect(panel.locator('#task-authority-label')).toHaveValue(label);
    await expect(panel.locator('#task-authority-root')).toHaveValue(root);
    await expect(panel.locator('#task-authority-ttl')).toHaveValue('3600');
    for (const [index, checked] of selected.entries()) {
      await expect(choices.nth(index).getByRole('checkbox')).toBeChecked({ checked });
    }
    await expect(panel.locator('.task-authority-eula input')).toBeChecked();
    await expect(panel.locator('.task-authority-list strong')).toHaveCount(0);

    await panel.locator('button[type="submit"]').click();
    const dialog = page.getByRole('dialog');
    const summary = capabilityNames[target].filter((_, index) => selected[index]).join(target === 'zh' ? '、' : ', ');
    await expect(dialog.locator('.approval-details > div').nth(1).locator('dd')).toHaveText(summary);
    await expect(dialog.locator('.modal-body > strong')).toHaveText(label);
    await expect(dialog.locator('code')).toHaveText(root);
    await dialog.locator('.modal-footer .btn-primary').click();
    await expect(dialog).not.toBeVisible();
    const grant = panel.locator('.task-authority-list > li').filter({ has: page.getByText(label, { exact: true }) });
    await expect(grant.locator('small').first()).toHaveText(summary);
    await page.screenshot({ path: testInfo.outputPath(`authorization-${target}.png`), animations: 'disabled' });

    await switchLocale(page, initial);
    await expect(choices).toHaveText(capabilityNames[initial]);
    await expect(grant.locator('small').first()).toHaveText(capabilityNames[initial].filter((_, index) => selected[index]).join(initial === 'zh' ? '、' : ', '));
    await expect(panel.locator('#task-authority-label')).toHaveValue(label);
    await expect(panel.locator('.task-authority-eula input')).toBeChecked();
  });

  test(`${initial} to ${target}: history filters, metadata and diff labels update while the selected recovery point stays intact`, async ({ page }, testInfo) => {
    await openWorkbench(page, initial);
    await page.getByTestId('scenario-switcher-trigger').click();
    await page.getByTestId('scenario-btn-history-ready').click();
    await page.getByTestId('nav-history').click();
    const source = page.getByTestId('history-source-filter');
    const actor = page.getByTestId('history-actor-filter');
    const search = page.getByTestId('history-search');
    await expect(page.getByTestId('history-point')).toHaveCount(3);
    await source.selectOption('manual');
    await actor.selectOption('ui');
    await search.fill('Copper Lamp');
    const selected = page.getByTestId('history-point');
    await expect(selected).toHaveCount(1);
    await expect(selected).toHaveAttribute('aria-pressed', 'true');
    await expect(page.getByTestId('history-change')).toHaveCount(4);
    const originalPaths = await page.getByTestId('history-change').locator('code').allTextContents();

    await switchLocale(page, target);
    await expect(source.locator('option')).toHaveText(historySourceNames[target]);
    await expect(actor.locator('option')).toHaveText(historyActorNames[target]);
    await expect(source).toHaveValue('manual');
    await expect(actor).toHaveValue('ui');
    await expect(search).toHaveValue('Copper Lamp');
    await expect(selected).toHaveCount(1);
    await expect(selected).toHaveAttribute('aria-pressed', 'true');
    await expect(selected).toContainText('Copper Lamp validated');
    await expect(selected.locator('.history-point-meta')).toContainText(target === 'zh' ? '手动 · 界面' : 'Manual · Interface');
    await expect(page.locator('.history-detail-heading p')).toContainText(target === 'zh' ? '界面' : 'Interface');
    await expect(page.getByTestId('history-change').locator('.change-badge')).toHaveText(target === 'zh' ? ['修改', '新增', '修改', '删除'] : ['Modify', 'Add', 'Modify', 'Delete']);
    const codes = page.getByTestId('history-change').locator('code');
    await expect(codes.first()).toHaveText(`${target === 'zh' ? '修改' : 'Modify'} · elements/copper_lamp.mod.json`);
    for (const [index, text] of originalPaths.entries()) {
      await expect(codes.nth(index)).toContainText(text.split(' · ').at(-1)!);
    }
    await page.screenshot({ path: testInfo.outputPath(`history-${target}.png`), animations: 'disabled' });

    await switchLocale(page, initial);
    await expect(source.locator('option')).toHaveText(historySourceNames[initial]);
    await expect(actor.locator('option')).toHaveText(historyActorNames[initial]);
    await expect(search).toHaveValue('Copper Lamp');
    await expect(selected).toHaveAttribute('aria-pressed', 'true');
  });

  test(`${initial} to ${target}: function snippet text and tooltips update while code, namespace and pending tag stay unsaved`, async ({ page }, testInfo) => {
    await openWorkbench(page, initial);
    await page.getByTestId('nav-elements').click();
    await page.getByTestId('create-element-btn').click();
    await page.getByTestId('create-element-type-function').click();
    await page.getByTestId('create-element-name-input').fill('locale_continuity');
    await page.getByTestId('create-element-submit-btn').click();
    await expect(page.getByTestId('function-workbench')).toBeVisible();
    const code = '/say Keep this source 原样保留\n# User-authored comment 用户注释\n';
    await expect(page.getByTestId('function-code-editor')).toBeEditable();
    await page.getByTestId('function-code-editor').fill(code);
    await page.getByTestId('function-namespace-input').fill('locale_draft');
    await page.getByTestId('function-tab-tags').click();
    await page.getByTestId('function-add-tag-input').fill('locale:pending');

    await switchLocale(page, target);
    await expect(page.getByTestId('function-tab-tags')).toHaveAttribute('aria-pressed', 'true');
    await expect(page.getByTestId('function-add-tag-input')).toHaveValue('locale:pending');
    await page.getByTestId('function-tab-editor').click();
    await expect(page.getByTestId('function-code-editor')).toHaveValue(code);
    await expect(page.getByTestId('function-namespace-input')).toHaveValue('locale_draft');
    await expect(page.getByTestId('function-dirty-badge')).toBeVisible();
    await expect(page.locator('[data-testid^="snippet-"]')).toHaveText(snippetLabels[target]);
    await expect(page.getByTestId('snippet-execute')).toHaveAttribute('title', target === 'zh' ? '针对所有在线玩家执行后续命令' : 'Run subsequent commands as all online players');
    await expect(page.getByTestId('snippet-title')).toHaveAttribute('title', target === 'zh' ? '在屏幕中央向玩家显示全屏大标题' : 'Display a large title in the center of the screen');
    await page.screenshot({ path: testInfo.outputPath(`function-${target}.png`), animations: 'disabled' });

    await switchLocale(page, initial);
    await expect(page.locator('[data-testid^="snippet-"]')).toHaveText(snippetLabels[initial]);
    await expect(page.getByTestId('function-code-editor')).toHaveValue(code);
    await expect(page.getByTestId('function-namespace-input')).toHaveValue('locale_draft');
    await page.getByTestId('function-tab-tags').click();
    await expect(page.getByTestId('function-add-tag-input')).toHaveValue('locale:pending');
    await expect(page.getByTestId('function-dirty-badge')).toBeVisible();
  });

  test(`${initial} to ${target}: new-workspace track headings update without resetting the chosen generator or form`, async ({ page }, testInfo) => {
    await openWorkbench(page, initial);
    await page.getByTestId('nav-new-workspace').click();
    await expect(page.getByTestId('generator-option-neoforge-1.21.1')).toBeVisible();
    await page.getByTestId('generator-option-neoforge-1.21.1').click();
    await page.getByLabel(initial === 'zh' ? '显示历史精确版本' : 'Show historical exact versions', { exact: true }).check();
    await expect(page.getByTestId('generator-track-advanced')).toBeVisible();
    const fields = {
      'new-workspace-mod-name-input': 'Locale Draft 语言草稿',
      'new-workspace-mod-id-input': 'locale_draft',
      'new-workspace-package-input': 'org.example.custom_package',
      'new-workspace-folder-input': 'D:/MockWorkspace/locale-draft',
      'new-workspace-version-input': '2.4.0'
    };
    for (const [id, value] of Object.entries(fields)) await page.getByTestId(id).fill(value);
    await page.getByTestId('confirm-create-workspace-checkbox').check();

    await switchLocale(page, target);
    await expect(page.getByTestId('generator-track-resource_pack').locator(':scope > div').first()).toHaveText(target === 'zh' ? '独立资源包 · Minecraft 1.21.1' : 'Standalone resource pack · Minecraft 1.21.1');
    await expect(page.getByTestId('generator-track-advanced').locator(':scope > div').first()).toHaveText(target === 'zh' ? '历史精确版本' : 'Historical exact versions');
    await expect(page.getByLabel(target === 'zh' ? '显示历史精确版本' : 'Show historical exact versions', { exact: true })).toBeChecked();
    await expect(page.getByTestId('selected-generator-info')).toContainText('neoforge-1.21.1');
    for (const [id, value] of Object.entries(fields)) await expect(page.getByTestId(id)).toHaveValue(value);
    await expect(page.getByTestId('confirm-create-workspace-checkbox')).toBeChecked();
    await expect(page.getByTestId('create-workspace-submit-btn')).toBeEnabled();
    await expect(page.getByTestId('workspace-created-banner')).not.toBeVisible();
    await page.screenshot({ path: testInfo.outputPath(`new-workspace-${target}.png`), animations: 'disabled' });

    await switchLocale(page, initial);
    await expect(page.getByTestId('generator-track-resource_pack').locator(':scope > div').first()).toHaveText(initial === 'zh' ? '独立资源包 · Minecraft 1.21.1' : 'Standalone resource pack · Minecraft 1.21.1');
    await expect(page.getByTestId('selected-generator-info')).toContainText('neoforge-1.21.1');
    for (const [id, value] of Object.entries(fields)) await expect(page.getByTestId(id)).toHaveValue(value);
    await expect(page.getByTestId('confirm-create-workspace-checkbox')).toBeChecked();
  });
}
