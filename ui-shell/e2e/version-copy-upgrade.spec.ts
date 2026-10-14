import { test, expect, type Page } from '@playwright/test';
import { readFileSync } from 'node:fs';

const responses = Object.fromEntries(['fabric', 'neoforge'].map(loader => [loader,
  JSON.parse(readFileSync(new URL(`../../ui-core/fixtures/extensions/version-copy-upgrade/${loader}-preview.json`, import.meta.url), 'utf8'))
]));
const executables = Object.fromEntries(['fabric', 'neoforge'].map(loader => [loader,
  JSON.parse(readFileSync(new URL(`../../ui-core/fixtures/extensions/version-copy-upgrade/${loader}-executable-preview.json`, import.meta.url), 'utf8'))
]));

async function fixture(page: Page, loader: string, v2 = false) {
  await page.addInitScript(({ response, loader }) => {
    const corePromise = import('/src/mock/mockBridge.ts').then(({ MockCoreBridge }) => {
      const core = new MockCoreBridge(); core.loadScenario('ready');
      const state = core.getState();
      state.workbench!.workspace.id = response.workspaceId;
      state.workbench!.workspace.revision = response.revision;
      state.workbench!.workspace.generator.id = `${loader}-26.2`;
      state.workbench!.workspace.generator.loader = loader;
      state.workbench!.workspace.generator.minecraftVersion = '26.2';
      state.workbench!.workspace.generator.displayName = `${loader === 'fabric' ? 'Fabric' : 'NeoForge'} 26.2`;
      state.workbench!.workspace.name = 'Upgrade Source';
      return core;
    });
    (window as any).upgradeRequests = [];
    window.copperbenchHost = {
      workspaceId: response.workspaceId, onEvent: () => () => {},
      async invoke(raw) {
        const request = JSON.parse(raw), core = await corePromise;
        (window as any).upgradeRequests.push(request);
        if (request.messageType === 'version_copy_upgrade' && request.request.operation === 'execute') {
          return JSON.stringify({ contract: 'version_copy_upgrade', schemaVersion: '2', selectedVersion: '2', requestId: request.requestId,
            workspaceId: request.workspaceId, status: (window as any).failCopy ? 'failed' : 'succeeded',
            diagnostics: (window as any).failCopy ? [{ code: 'UPGRADE_TARGET_FAILED', message: 'The target is incomplete. Continue using the source.' }] : [],
            execution: { status: (window as any).failCopy ? 'failed' : 'prepared', conversionComplete: !(window as any).failCopy,
              targetDirectory: response.plan.targetDirectory, targetWorkspaceFile: `${response.plan.targetDirectory}/source.mcreator`,
              sourceUnchanged: true, worldsCopied: false, acceptance: 'not_run' } });
        }
        if (request.messageType === 'version_copy_upgrade') return JSON.stringify((window as any).rejectUpgradePreview
          ? { contract: 'version_copy_upgrade', schemaVersion: response.schemaVersion, selectedVersion: response.selectedVersion, requestId: request.requestId,
              workspaceId: request.workspaceId, status: 'rejected', diagnostics: [{ code: 'UPGRADE_SOURCE_UNAVAILABLE', message: 'The source changed. Request a new plan.' }] }
          : { ...structuredClone(response), requestId: request.requestId, workspaceId: request.workspaceId });
        return JSON.stringify(request.messageType === 'handshake' ? await core.negotiateHandshake(request)
          : request.messageType === 'preview' ? { ...await core.sendPreview(request.workspaceId, request.request), requestId: request.requestId }
          : request.messageType === 'command' ? await core.sendCommand(request) : await core.sendQuery(request));
      }
    };
  }, { response: v2 ? executables[loader] : responses[loader], loader });
  await page.goto('/');
  if (!(await page.getByTestId('nav-tracks').isVisible())) await page.getByTestId('nav-tools-toggle').click();
  await page.getByTestId('nav-tracks').click();
  await page.getByTestId('tab-version-upgrade').click();
}

for (const loader of ['fabric', 'neoforge']) for (const language of ['zh', 'en'])
test(`read-only conversion plan exposes original fields and manual work (${loader}, ${language})`, async ({ page }, info) => {
  await fixture(page, loader);
  await page.getByTestId('ui-language-select').selectOption(language);
  const panel = page.getByTestId('version-copy-upgrade');
  await expect(panel.getByLabel(language === 'zh' ? '目标生成器' : 'Target generator')).toHaveValue(`${loader}-26.3`);
  await panel.getByTestId('preview-version-upgrade').click();
  await expect(panel.getByTestId('upgrade-copy-policy')).toBeVisible();
  await page.screenshot({ path: info.outputPath(`upgrade-${loader}-${language}.png`) });
  const field = panel.locator('article').filter({ has: page.locator('code.version-upgrade-path', { hasText: '/unknownUpgradeField' }) });
  await field.getByText(language === 'zh' ? '原值与处理依据' : 'Original value and rule evidence').click();
  await expect(field).toContainText('retain-exactly');
  await expect(field).toHaveAttribute('data-disposition', 'manual');
  await field.screenshot({ path: info.outputPath(`original-field-${language}.png`) });
  const requests = await page.evaluate(() => (window as any).upgradeRequests);
  expect(requests.filter((r: any) => r.messageType === 'version_copy_upgrade').map((r: any) => r.request)).toEqual([
    { supportedVersions: ['1'], operation: 'preview', targetGeneratorId: `${loader}-26.3` }
  ]);
  expect(requests.filter((r: any) => r.messageType === 'command' || r.request?.operation === 'command')).toHaveLength(0);
  expect(await panel.evaluate(el => el.scrollWidth <= el.clientWidth)).toBe(true);
});

for (const loader of ['fabric', 'neoforge']) for (const language of ['zh', 'en']) test(`a reviewed version 2 copy needs explicit confirmation (${loader}, ${language})`, async ({ page }, info) => {
  const executable = executables[loader];
  await fixture(page, loader, true); await page.getByTestId('ui-language-select').selectOption(language);
  await page.getByTestId('upgrade-output-name').fill(executable.plan.outputName);
  await page.getByTestId('preview-executable-upgrade').click();
  await expect(page.getByTestId('execute-version-copy')).toBeDisabled();
  await expect(page.getByTestId('upgrade-execute-controls')).toContainText(executable.plan.targetDirectory);
  await page.getByTestId('confirm-version-copy').check();
  await expect(page.getByTestId('execute-version-copy')).toBeEnabled();
  await page.getByTestId('execute-version-copy').scrollIntoViewIfNeeded();
  await page.screenshot({ path: info.outputPath(`reviewed-copy-${loader}-${language}.png`) });
  await page.getByTestId('execute-version-copy').click();
  await expect(page.getByTestId('upgrade-execution-result')).toContainText(language === 'zh' ? '副本已创建' : 'Copy created');
  await expect(page.getByTestId('execute-version-copy')).toHaveCount(0);
  const requests = await page.evaluate(() => (window as any).upgradeRequests);
  const executed = requests.filter((r: any) => r.messageType === 'version_copy_upgrade' && r.request.operation === 'execute');
  expect(executed).toHaveLength(1);
  expect(executed[0].request).toEqual({ operation: 'execute', supportedVersions: ['2'], targetGeneratorId: `${loader}-26.3`,
    outputName: executable.plan.outputName, planId: executable.plan.planId, expectedRevision: executable.plan.sourceRevision, userApproved: true });
  expect(requests.some((r: any) => r.messageType === 'preview' && r.request.operation === 'opt_in' && r.request.workspaceFolderPath === executable.plan.targetDirectory)).toBe(true);
  expect(await page.getByTestId('version-copy-upgrade').evaluate(el => el.scrollWidth <= el.clientWidth)).toBe(true);
});

for (const loader of ['fabric', 'neoforge']) test(`changing the destination discards the plan and confirmation (${loader})`, async ({ page }) => {
  await fixture(page, loader, true);
  await page.getByTestId('preview-executable-upgrade').click(); await page.getByTestId('confirm-version-copy').check();
  await page.getByTestId('upgrade-output-name').fill('different-copy');
  await expect(page.getByTestId('execute-version-copy')).toHaveCount(0);
  await expect(page.getByTestId('upgrade-plan-entries')).toHaveCount(0);
});

for (const loader of ['fabric', 'neoforge']) test(`failed copy shows recovery and cannot be retried from the old plan (${loader})`, async ({ page }) => {
  await fixture(page, loader, true); await page.getByTestId('ui-language-select').selectOption('en');
  await page.evaluate(() => { (window as any).failCopy = true; });
  await page.getByTestId('preview-executable-upgrade').click(); await page.getByTestId('confirm-version-copy').check();
  await page.getByTestId('execute-version-copy').click();
  await expect(page.getByTestId('upgrade-execution-result')).toContainText('Copy incomplete');
  await expect(page.getByRole('alert')).toContainText('Continue using the source');
  await expect(page.getByTestId('execute-version-copy')).toHaveCount(0);
  expect(await page.evaluate(() => (window as any).upgradeRequests.filter((r: any) => r.messageType === 'version_copy_upgrade' && r.request.operation === 'execute').length)).toBe(1);
});

test('a failed refresh removes the old plan and does not retry', async ({ page }) => {
  await fixture(page, 'fabric'); await page.getByTestId('ui-language-select').selectOption('en');
  const panel = page.getByTestId('version-copy-upgrade');
  await panel.getByTestId('preview-version-upgrade').click(); await expect(panel.getByTestId('upgrade-copy-policy')).toBeVisible();
  await page.evaluate(() => { (window as any).rejectUpgradePreview = true; });
  await panel.getByTestId('preview-version-upgrade').click();
  await expect(panel.getByRole('alert')).toHaveText('The source changed. Request a new plan.');
  await expect(panel.getByTestId('upgrade-plan-entries')).toHaveCount(0);
  const count = await page.evaluate(() => (window as any).upgradeRequests.filter((r: any) => r.messageType === 'version_copy_upgrade').length);
  expect(count).toBe(2);
});
