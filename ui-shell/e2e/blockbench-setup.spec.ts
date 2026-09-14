import { test, expect } from '@playwright/test';

// Installed JCEF uses a custom scheme without crypto.randomUUID.
test.beforeEach(async ({ page }) => {
  await page.addInitScript(() => Object.defineProperty(crypto, 'randomUUID', { value: undefined }));
});

test('optional setup is keyboard reachable and never pretends preview can connect', async ({ page }, testInfo) => {
  await page.goto('/');
  await page.getByTestId('nav-assets').click();
  const setup = page.getByTestId('blockbench-setup');
  await expect(setup).not.toHaveAttribute('open', '');
  await setup.locator('summary').focus();
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
  await page.getByTestId('blockbench-setup').locator('summary').click();
  await expect(page.getByRole('button', { name: '复制官方下载地址' })).toBeVisible();
  await page.getByTestId('nav-ai').click();
  await expect(page.getByTestId('blockbench-setup')).toBeVisible();
});
