import { test, expect } from '@playwright/test';

// Installed JCEF uses a custom scheme without crypto.randomUUID.
test.beforeEach(async ({ page }) => {
  await page.addInitScript(() => Object.defineProperty(crypto, 'randomUUID', { value: undefined }));
});

test('optional setup is keyboard reachable and never pretends preview can connect', async ({ page }, testInfo) => {
  await page.goto('/');
  await page.getByTestId('nav-assets').click();
  await page.getByTestId('asset-modeling-disclosure').locator(':scope > summary').click();
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
