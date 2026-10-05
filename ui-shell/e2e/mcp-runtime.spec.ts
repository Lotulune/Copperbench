import { test, expect, type Page } from '@playwright/test';

const installSelectableHost = async (page: Page, mode: 'normal' | 'reject' | 'stop' | 'pending' = 'normal') => {
  await page.addInitScript((mode) => {
    const state = {
      status: 'listening', url: 'http://127.0.0.1:43123/mcp',
      workspaceId: '11111111-1111-4111-8111-111111111111',
      permissionProfile: 'workspace', expiresAt: '2026-10-05T15:00:00Z', tokenAvailable: true,
      failure: null as string | null
    };
    let calls = 0;
    const hostWindow = window as unknown as {
      __COPPERBENCH_MCP_HOST__: unknown;
      __MCP_CALLS__: number;
      __COMPLETE_MCP_CHANGE__?: () => void;
      __COPPERBENCH_TEST_CLIPBOARD__?: string;
    };
    hostWindow.__MCP_CALLS__ = 0;
    hostWindow.__COPPERBENCH_MCP_HOST__ = {
      available: true,
      getState: async () => ({ ...state }),
      setPermissionProfile: async (profile: string) => {
        hostWindow.__MCP_CALLS__ = ++calls;
        if (mode === 'reject' && calls === 1) throw new Error('Permission change unavailable');
        if (mode === 'stop' && calls === 1) {
          Object.assign(state, { status: 'not_started', url: null, tokenAvailable: false, failure: 'Could not save MCP settings' });
          return { ...state };
        }
        if (mode === 'pending') await new Promise<void>(resolve => { hostWindow.__COMPLETE_MCP_CHANGE__ = resolve; });
        Object.assign(state, { status: 'listening', url: 'http://127.0.0.1:43123/mcp', permissionProfile: profile,
          tokenAvailable: true, failure: null });
        return { ...state };
      },
      revealTokenOnce: async () => {
        state.tokenAvailable = false;
        return { token: `test-token-${state.permissionProfile}` };
      },
      copyText: async (text: string) => { hostWindow.__COPPERBENCH_TEST_CLIPBOARD__ = text; }
    };
  }, mode);
};

const openPermissions = async (page: Page) => {
  await page.goto('/');
  await page.getByTestId('nav-ai').click();
  return page.getByRole('group', { name: '权限档位' });
};

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
    for (const button of await page.getByRole('group', { name: '权限档位' }).getByRole('button').all()) {
      await expect(button).toBeDisabled();
      await expect(button).toHaveAttribute('aria-pressed', 'false');
    }
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
    await expect(page.locator('[data-testid="permission-alert"]')).toContainText('MCP: 工作区读写（Workspace）');

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

  test('selects each profile directly, clears old tokens and synchronizes the footer', async ({ page }, testInfo) => {
    await installSelectableHost(page);
    const options = await openPermissions(page);
    await expect(options.getByRole('button', { name: '完全访问' })).toBeInViewport({ ratio: 1 });
    await page.getByRole('button', { name: '显示一次令牌' }).click();
    await expect(page.getByText('test-token-workspace', { exact: true })).toBeVisible();

    for (const [name, label] of [['只读', 'Read Only'], ['完全访问', 'Full Access'], ['工作区', 'Workspace']]) {
      const choice = options.getByRole('button', { name, exact: false });
      await choice.click();
      await expect(choice).toHaveAttribute('aria-pressed', 'true');
      await expect(page.getByTestId('permission-alert')).toContainText(label);
      await expect(page.getByTestId('permission-status')).toContainText('已切换为');
      await expect(page.getByRole('dialog')).toHaveCount(0);
      await expect(page.getByText('test-token-workspace', { exact: true })).toHaveCount(0);
      await expect(page.getByRole('button', { name: '显示一次令牌' })).toBeEnabled();
    }
    await page.getByRole('button', { name: '显示一次令牌' }).click();
    await expect(page.getByText('test-token-workspace', { exact: true })).toBeVisible();
    await page.getByRole('button', { name: '复制配置' }).click();
    expect(await page.evaluate(() => (window as unknown as { __COPPERBENCH_TEST_CLIPBOARD__: string }).__COPPERBENCH_TEST_CLIPBOARD__))
      .toContain('Authorization: Bearer test-token-workspace');
    await options.scrollIntoViewIfNeeded();
    await page.screenshot({ path: testInfo.outputPath('mcp-permission-selection.png') });
  });

  test('keeps the actual profile selected when the host rejects a change and allows retry', async ({ page }) => {
    await installSelectableHost(page, 'reject');
    const options = await openPermissions(page);
    await options.getByRole('button', { name: '只读' }).click();
    await expect(page.getByTestId('permission-status')).toHaveText('Permission change unavailable');
    await expect(page.getByTestId('permission-status')).toHaveAttribute('role', 'alert');
    await expect(options.getByRole('button', { name: '工作区' })).toHaveAttribute('aria-pressed', 'true');
    await expect(options.getByRole('button', { name: '只读' })).toBeEnabled();
    await options.getByRole('button', { name: '只读' }).click();
    await expect(options.getByRole('button', { name: '只读' })).toHaveAttribute('aria-pressed', 'true');
  });

  test('shows a stopped runtime after publication failure and permits recovery', async ({ page }) => {
    await installSelectableHost(page, 'stop');
    const options = await openPermissions(page);
    await options.getByRole('button', { name: '只读' }).click();
    await expect(page.getByTestId('permission-status')).toHaveText('Could not save MCP settings');
    await expect(page.getByRole('button', { name: '显示一次令牌' })).toBeDisabled();
    await expect(page.getByTestId('permission-alert')).toContainText('未启动');
    await expect(options.locator('[aria-pressed="true"]')).toHaveCount(0);
    await options.getByRole('button', { name: '只读' }).click();
    await expect(options.getByRole('button', { name: '只读' })).toHaveAttribute('aria-pressed', 'true');
  });

  test('keyboard selection waits for the host and prevents duplicate requests', async ({ page }) => {
    await installSelectableHost(page, 'pending');
    const options = await openPermissions(page);
    const readOnly = options.getByRole('button', { name: '只读' });
    await readOnly.focus();
    await page.keyboard.press('Enter');
    await expect(page.getByTestId('permission-status')).toHaveText('正在切换权限…');
    await expect(readOnly).toBeDisabled();
    await expect(options.getByRole('button', { name: '完全访问' })).toBeDisabled();
    await expect(options.getByRole('button', { name: '工作区' })).toHaveAttribute('aria-pressed', 'true');
    await page.keyboard.press('Enter');
    expect(await page.evaluate(() => (window as unknown as { __MCP_CALLS__: number }).__MCP_CALLS__)).toBe(1);
    await page.evaluate(() => (window as unknown as { __COMPLETE_MCP_CHANGE__: () => void }).__COMPLETE_MCP_CHANGE__());
    await expect(readOnly).toHaveAttribute('aria-pressed', 'true');
    await expect(options.getByRole('button', { name: '完全访问' })).toBeEnabled();
  });

  test('English dark theme supports direct selection and readable selected state', async ({ page }, testInfo) => {
    await page.addInitScript(() => {
      localStorage.setItem('copperbench.ui.locale', 'en');
      localStorage.setItem('copperbench.theme', 'dark');
    });
    await page.emulateMedia({ colorScheme: 'dark', reducedMotion: 'reduce' });
    await installSelectableHost(page);
    await page.goto('/');
    await page.getByTestId('nav-ai').click();
    const options = page.getByRole('group', { name: 'Permission profile' });
    await expect(options.getByRole('button', { name: 'Full access' })).toBeInViewport({ ratio: 1 });
    const choice = options.getByRole('button', { name: 'Read only' });
    await choice.click();
    await expect(choice).toHaveAttribute('aria-pressed', 'true');
    await expect(choice).toBeEnabled();
    await expect(page.getByTestId('permission-status')).toContainText('Changed to Read only');
    await expect(page.getByTestId('permission-alert')).toContainText('Read only');
    await choice.click();
    expect(await page.evaluate(() => (window as unknown as { __MCP_CALLS__: number }).__MCP_CALLS__)).toBe(1);
    await page.screenshot({ path: testInfo.outputPath('mcp-permission-english-dark.png') });
  });
});
