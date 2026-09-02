import { test, expect } from '@playwright/test';

test.describe('Desktop MCP runtime state', () => {
  test('browser fallback never claims an MCP client is connected', async ({ page }) => {
    await page.goto('/');
    await page.waitForSelector('[data-testid="app-shell"]');
    await page.click('[data-testid="nav-ai"]');

    await expect(page.getByRole('heading', { name: '本机 MCP 服务' })).toBeVisible();
    await expect(page.getByText('未启动', { exact: true }).first()).toBeVisible();
    await expect(page.getByText('已连接', { exact: true })).toHaveCount(0);
    await expect(page.getByRole('button', { name: '复制 URL' })).toBeDisabled();
    await expect(page.getByRole('button', { name: '显示一次令牌' })).toBeDisabled();
  });

  test('native host state drives endpoint display and one-time token reveal', async ({ page }) => {
    await page.addInitScript(() => {
      let tokenAvailable = true;
      (window as unknown as {
        __COPPERBENCH_MCP_HOST__: {
          available: boolean;
          getState: () => Promise<unknown>;
          revealTokenOnce: () => Promise<{ token: string }>;
          copyText: (text: string) => Promise<void>;
        };
        __COPPERBENCH_TEST_CLIPBOARD__?: string;
      }).__COPPERBENCH_MCP_HOST__ = {
        available: true,
        getState: async () => ({
          status: 'listening',
          url: 'http://127.0.0.1:43123/mcp',
          workspaceId: '11111111-1111-4111-8111-111111111111',
          permissionProfile: 'workspace',
          expiresAt: '2026-09-02T15:00:00Z',
          tokenAvailable,
          failure: null
        }),
        revealTokenOnce: async () => {
          tokenAvailable = false;
          return { token: 'one-time-test-token' };
        },
        copyText: async (text: string) => {
          (window as unknown as { __COPPERBENCH_TEST_CLIPBOARD__?: string }).__COPPERBENCH_TEST_CLIPBOARD__ = text;
        }
      };
    });

    await page.goto('/');
    await page.waitForSelector('[data-testid="app-shell"]');
    await page.click('[data-testid="nav-ai"]');

    await expect(page.getByText('服务已启动', { exact: true }).first()).toBeVisible();
    await expect(page.getByText('http://127.0.0.1:43123/mcp', { exact: true })).toBeVisible();
    await expect(page.getByText('11111111-1111-4111-8111-111111111111', { exact: true })).toBeVisible();
    await expect(page.getByText('已连接', { exact: true })).toHaveCount(0);
    await expect(page.locator('[data-testid="permission-alert"]')).toContainText('MCP: WORKSPACE');

    const reveal = page.getByRole('button', { name: '显示一次令牌' });
    await expect(reveal).toBeEnabled();
    await reveal.click();
    await expect(page.getByText('one-time-test-token', { exact: true })).toBeVisible();
    await expect(reveal).toBeDisabled();

    await page.getByRole('button', { name: '复制配置' }).click();
    await expect(page.getByText('已复制 MCP 配置信息', { exact: true })).toBeVisible();
    const copied = await page.evaluate(() =>
      (window as unknown as { __COPPERBENCH_TEST_CLIPBOARD__?: string }).__COPPERBENCH_TEST_CLIPBOARD__ ?? '');
    expect(copied).toContain('URL: http://127.0.0.1:43123/mcp');
    expect(copied).toContain('Authorization: Bearer one-time-test-token');
    expect(copied).toContain('workspaceId: 11111111-1111-4111-8111-111111111111');
  });
});
