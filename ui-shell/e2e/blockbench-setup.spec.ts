import { test, expect, type Page } from '@playwright/test';

async function installNativeWorkspace(page: Page) {
  await page.addInitScript(() => {
    const modulePath = '/src/mock/mockBridge.ts';
    const ready = import(modulePath).then(({ MockCoreBridge }) => new MockCoreBridge());
    (window as any).blockbenchProbes = 0;
    window.copperbenchHost = {
      workspaceId: '11111111-1111-4111-8111-111111111111', onEvent() { return () => {}; },
      async invoke(raw) {
        const request = JSON.parse(raw), core = await ready;
        const result = request.messageType === 'handshake' ? await core.negotiateHandshake(request) : await core.sendQuery(request);
        if (request.operation === 'get_blockbench_environment') {
          if (request.payload.probeMcp) (window as any).blockbenchProbes += 1;
          result.data = {
            editor: { state: 'ready', available: true, version: '5.1.6' },
            mcp: { state: 'not_checked' }, inspectionState: 'completed', onboardingDismissed: false
          };
        }
        if (request.operation === 'list_assets') result.data.assets[0].relativePath = 'assets/coppertrails/models/item/staff.json';
        return JSON.stringify(result);
      }
    };
  });
}

test('onboarding opens usable settings without navigating away from the workspace', async ({ page }, testInfo) => {
  await installNativeWorkspace(page);
  await page.goto('/');
  const trigger = page.getByTestId('blockbench-onboarding').getByRole('button', { name: '设置 Blockbench', exact: true });
  await trigger.click();
  const dialog = page.getByRole('dialog', { name: 'Blockbench 设置' });
  await expect(dialog.getByRole('button', { name: '选择安装位置' })).toBeVisible();
  await expect(dialog).toContainText('编辑器：已检测到编辑器（5.1.6）');
  await expect(dialog.getByLabel('Blockbench MCP 本机地址')).toBeVisible();
  await expect(page.locator('.app-content-canvas')).toHaveAttribute('data-active-view', 'hub');
  expect(await page.evaluate(() => (window as any).blockbenchProbes)).toBe(0);
  expect(await dialog.evaluate(element => element.scrollWidth <= element.clientWidth)).toBe(true);
  await page.screenshot({ path: testInfo.outputPath('blockbench-settings-dialog.png') });
  await page.keyboard.press('Escape');
  await expect(dialog).not.toBeVisible();
  await expect(trigger).toBeFocused();
  await trigger.click();
  await expect(dialog.getByRole('button', { name: '重新检测安装' })).toBeVisible();
});

test('English setup translates detection details and retains results across locale changes', async ({ page }) => {
  await installNativeWorkspace(page);
  await page.addInitScript(() => { window.__COPPERBENCH_UI_LOCALE__ = 'en'; });
  await page.goto('/');
  await page.getByTestId('nav-assets').click();
  await page.getByRole('button', { name: 'Blockbench setup', exact: true }).click();
  const dialog = page.getByRole('dialog', { name: 'Blockbench setup' });
  await expect(dialog.getByRole('status').last()).toContainText('Editor detection complete; MCP connection not tested.');
  await dialog.getByText('Detection and runtime details', { exact: true }).click();
  await expect(dialog).toContainText('Development build or unconfirmed source');
  expect(await dialog.innerText()).not.toMatch(/[\u3400-\u9fff]/);
  await page.evaluate(() => window.dispatchEvent(new CustomEvent('copperbench:locale-changed', { detail: 'zh' })));
  const chineseDialog = page.getByRole('dialog', { name: 'Blockbench 设置' });
  await expect(chineseDialog).toContainText('编辑器检测完成；尚未测试 MCP 连接。');
  await expect(chineseDialog.getByLabel('Blockbench MCP 本机地址')).toHaveValue('http://127.0.0.1:3000/bb-mcp');
  expect(await page.evaluate(() => (window as any).blockbenchProbes)).toBe(0);
});

test('JSON model launch shows pending and failure feedback beside the action and allows retry', async ({ page }, testInfo) => {
  await installNativeWorkspace(page);
  await page.addInitScript(() => {
    (window as any).__COPPERBENCH_BLOCKBENCH_HOST__ = {
      schemaVersion: '1.0',
      openAsset: (assetId: string) => {
        (window as any).openedModelId = assetId;
        return new Promise((_resolve, reject) => { (window as any).rejectBlockbenchOpen = reject; });
      }
    };
  });
  await page.goto('/');
  await page.getByTestId('nav-assets').click();
  await expect(page.getByTestId('asset-details')).toContainText('staff.json');
  const open = page.getByTestId('asset-open-blockbench');
  await open.click();
  await expect(open).toBeDisabled();
  await expect(open).toHaveAttribute('aria-busy', 'true');
  const notice = page.getByTestId('asset-blockbench-notice');
  await expect(notice).toContainText('正在准备模型并启动 Blockbench');
  await expect(notice).toBeInViewport();
  await page.getByRole('button', { name: 'Blockbench 设置', exact: true }).click();
  await expect(page.getByRole('dialog', { name: 'Blockbench 设置' })).toBeVisible();
  await page.keyboard.press('Escape');
  await page.evaluate(() => (window as any).rejectBlockbenchOpen(new Error('BLOCKBENCH_RECOVERY_FAILED')));
  await expect(notice).toContainText('BLOCKBENCH_RECOVERY_FAILED');
  await expect(open).toBeEnabled();
  await expect(notice).toBeInViewport();
  expect(await page.evaluate(() => (window as any).openedModelId)).toBe('asset:' + '1'.repeat(64));
  await page.screenshot({ path: testInfo.outputPath('blockbench-launch-feedback.png') });
  await open.click();
  await expect(notice).toContainText('正在准备模型并启动 Blockbench');
});

// Installed JCEF uses a custom scheme without crypto.randomUUID.
test.beforeEach(async ({ page }) => {
  await page.addInitScript(() => Object.defineProperty(crypto, 'randomUUID', { value: undefined }));
});

test('optional setup is keyboard reachable and never pretends preview can connect', async ({ page }, testInfo) => {
  await page.goto('/');
  await page.getByTestId('nav-assets').click();
  await page.getByTestId('asset-modeling-disclosure').locator(':scope > summary').focus();
  await page.keyboard.press('Enter');
  const setup = page.getByTestId('blockbench-setup');
  await expect(setup).not.toHaveAttribute('open', '');
  await setup.locator(':scope > summary').focus();
  await page.keyboard.press('Enter');
  await expect(setup.getByText('编辑器：尚未检测')).toBeVisible();
  await setup.getByRole('button', { name: '重新检测安装' }).click();
  await expect(setup.getByText('编辑器：尚未检测到编辑器')).toBeVisible();
  await expect(setup.getByText('AI 建模连接：尚未测试连接')).toBeVisible();
  await setup.getByRole('button', { name: '测试 MCP 连接' }).click();
  await expect(setup.getByText('AI 建模连接：预览模式无法检测本机服务')).toBeVisible();
  await setup.getByLabel('Blockbench MCP 本机地址').fill('http://127.0.0.1:3001/bb-mcp');
  await expect(setup.getByText('AI 建模连接：尚未测试连接')).toBeVisible();
  await expect(setup.getByRole('status')).toContainText('地址已修改');
  await expect(setup).toContainText('社区插件并非 Blockbench 官方 MCP');
  await expect(setup).toContainText('保存候选后预览并回导导出的模型和贴图');
  expect(await setup.evaluate(element => element.scrollWidth <= element.clientWidth)).toBe(true);
  await page.screenshot({ path: testInfo.outputPath('blockbench-setup.png') });
});

test('setup remains available in an empty workspace and AI settings', async ({ page }) => {
  await page.goto('/');
  await page.getByTestId('scenario-switcher-trigger').click();
  await page.getByTestId('scenario-btn-empty-workspace').click();
  await page.getByTestId('nav-assets').click();
  await page.getByTestId('asset-modeling-disclosure').locator(':scope > summary').click();
  await page.getByTestId('blockbench-setup').locator(':scope > summary').click();
  await page.getByTestId('blockbench-setup').getByText('安装步骤', { exact: true }).click();
  await expect(page.getByRole('button', { name: '复制官方下载地址' })).toBeVisible();
  if (!(await page.getByTestId('nav-ai').isVisible())) await page.getByTestId('nav-tools-toggle').click();
  await page.getByTestId('nav-ai').click();
  await page.locator('.ai-integration-settings > summary').click();
  await expect(page.getByTestId('blockbench-setup')).toBeVisible();
});

test('failed connection preserves installed editor and exposes actionable runtime details', async ({ page }) => {
  await page.addInitScript(() => {
    const modulePath = '/src/mock/mockBridge.ts';
    const ready = import(modulePath).then(({ MockCoreBridge }) => new MockCoreBridge());
    window.copperbenchHost = {
      workspaceId: '11111111-1111-4111-8111-111111111111', onEvent() { return () => {}; },
      async invoke(raw) {
        const request = JSON.parse(raw), core = await ready;
        const result = request.messageType === 'handshake' ? await core.negotiateHandshake(request) : await core.sendQuery(request);
        if (request.operation === 'get_blockbench_environment') result.data = {
          editor: { state: 'ready', available: true, version: '5.1.6' },
          mcp: { state: request.payload.probeMcp ? 'initialization_error' : 'not_checked',
            diagnosticCode: request.payload.probeMcp ? 'BLOCKBENCH_MCP_INITIALIZATION_ERROR' : null,
            failurePhase: 'transport_construction' },
          application: { sourceState: 'packaged_binary', javaVersion: '25.0.3', javaVendor: 'JetBrains',
            mcpSdkVersion: '2.0.0', applicationSha256: 'a'.repeat(64) },
          inspectionState: 'completed', onboardingDismissed: true
        };
        return JSON.stringify(result);
      }
    };
  });
  await page.goto('/');
  await page.getByTestId('nav-assets').click();
  await page.getByTestId('asset-modeling-disclosure').locator(':scope > summary').click();
  const setup = page.getByTestId('blockbench-setup');
  await setup.locator(':scope > summary').click();
  await setup.getByRole('button', { name: '测试 MCP 连接' }).click();
  await expect(setup).toContainText('编辑器：已检测到编辑器（5.1.6）');
  await expect(setup).toContainText('连接组件初始化失败');
  await setup.getByText('检测与运行详情', { exact: true }).click();
  await expect(setup).toContainText('BLOCKBENCH_MCP_INITIALIZATION_ERROR');
  await expect(setup).toContainText('Java：25.0.3');
  await expect(setup).toContainText('MCP SDK：2.0.0');
  await expect(setup).toContainText('a'.repeat(64));
  expect(await setup.evaluate(element => element.scrollWidth <= element.clientWidth)).toBe(true);
});
