import { expect, test, type Page } from '@playwright/test';
import { openToolView } from './navigation';

// Exercises the real UI with a controlled desktop host, not an external MCP connection.
const installPairingHost = async (page: Page, mode: 'normal' | 'pending' | 'reject' | 'legacy' | 'reveal-pending' | 'state-unavailable' | 'refresh-pending' = 'normal') => {
  await page.addInitScript((mode) => {
    const state = {
      status: 'listening', url: 'http://127.0.0.1:43123/mcp',
      workspaceId: '11111111-1111-4111-8111-111111111111',
      permissionProfile: 'read_only', expiresAt: '2026-10-20T15:00:00Z', tokenAvailable: true,
      failure: null as string | null
    };
    let generation = 0;
    const stateRefreshes: Array<() => void> = [];
    const hostWindow = window as unknown as {
      __COPPERBENCH_MCP_HOST__: unknown;
      __PAIRING_CALLS__: number;
      __PAIRING_PROFILE_CALLS__: number;
      __PAIRING_REVEAL_CALLS__: number;
      __PAIRING_CLIPBOARD__: string;
      __FINISH_PAIRING__?: () => void;
      __FINISH_REVEAL__?: () => void;
      __FINISH_STATE_REFRESH__?: () => void;
    };
    hostWindow.__PAIRING_CALLS__ = 0;
    hostWindow.__PAIRING_PROFILE_CALLS__ = 0;
    hostWindow.__PAIRING_REVEAL_CALLS__ = 0;
    hostWindow.__PAIRING_CLIPBOARD__ = '';
    hostWindow.__COPPERBENCH_MCP_HOST__ = {
      available: true,
      getState: async () => {
        // All refreshes keep failing until reset actually succeeds, including
        // the refresh emitted when the bridge starts changing the runtime.
        if (mode === 'state-unavailable' && generation === 0) throw new Error('MCP state temporarily unavailable');
        if (mode === 'refresh-pending' && generation > 0) await new Promise<void>(resolve => {
          stateRefreshes.push(resolve);
          hostWindow.__FINISH_STATE_REFRESH__ = () => stateRefreshes.splice(0).forEach(finish => finish());
        });
        return { ...state };
      },
      setPermissionProfile: async (profile: string) => {
        hostWindow.__PAIRING_PROFILE_CALLS__ += 1;
        state.permissionProfile = profile;
        return { ...state };
      },
      revealTokenOnce: async () => {
        hostWindow.__PAIRING_REVEAL_CALLS__ += 1;
        if (!state.tokenAvailable) throw new Error('Already revealed');
        state.tokenAvailable = false;
        if (mode === 'reveal-pending') await new Promise<void>(resolve => { hostWindow.__FINISH_REVEAL__ = resolve; });
        return { token: `pairing-fixture-${generation}` };
      },
      copyText: async (text: string) => { hostWindow.__PAIRING_CLIPBOARD__ = text; },
      ...(mode === 'legacy' ? {} : {
        resetPairing: async () => {
          hostWindow.__PAIRING_CALLS__ += 1;
          if (mode === 'reject' && hostWindow.__PAIRING_CALLS__ === 1) throw new Error('Pairing reset unavailable');
          if (mode === 'pending') await new Promise<void>(resolve => { hostWindow.__FINISH_PAIRING__ = resolve; });
          generation += 1;
          state.tokenAvailable = true;
          return { ...state };
        }
      })
    };
  }, mode);
};

const openPairing = async (page: Page) => {
  await page.goto('/');
  await openToolView(page, 'ai');
  return page.getByRole('button', { name: '重新配对', exact: true });
};

test('configuration stays disabled until it contains a one-time credential', async ({ page }) => {
  await installPairingHost(page);
  await openPairing(page);
  const copy = page.getByRole('button', { name: '复制配置', exact: true });
  await expect(copy).toBeDisabled();
  await expect(page.getByText('先显示一次令牌，再复制完整配置。', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: '复制 URL', exact: true }).click();
  expect(await page.evaluate(() => (window as unknown as { __PAIRING_CLIPBOARD__: string }).__PAIRING_CLIPBOARD__))
    .toBe('http://127.0.0.1:43123/mcp');
  await page.getByRole('button', { name: '显示一次令牌', exact: true }).click();
  await expect(copy).toBeEnabled();
  await copy.click();
  expect(await page.evaluate(() => (window as unknown as { __PAIRING_CLIPBOARD__: string }).__PAIRING_CLIPBOARD__))
    .toContain('Authorization: Bearer pairing-fixture-0');
});

test('a lost one-time credential can be replaced without changing permissions', async ({ page }) => {
  await installPairingHost(page);
  await openPairing(page);
  await page.getByRole('button', { name: '显示一次令牌', exact: true }).click();
  await expect(page.getByText('pairing-fixture-0', { exact: true })).toBeVisible();
  await page.getByTestId('nav-history').click();
  await openToolView(page, 'ai');
  await expect(page.getByText('pairing-fixture-0', { exact: true })).toHaveCount(0);
  await expect(page.getByRole('button', { name: '显示一次令牌', exact: true })).toBeDisabled();
  await expect(page.getByRole('button', { name: '复制配置', exact: true })).toBeDisabled();
  await expect(page.getByRole('button', { name: '重新配对', exact: true })).toHaveCount(1);
  await page.getByRole('button', { name: '重新配对', exact: true }).click();
  const dialog = page.getByRole('dialog', { name: '重新配对 MCP' });
  await expect(dialog).toContainText('旧令牌和 MCP 连接将失效');
  await dialog.getByRole('button', { name: '确认重新配对', exact: true }).click();
  await expect(dialog).toHaveCount(0);
  await expect(page.getByRole('group', { name: '权限档位' }).getByRole('button', { name: '只读' }))
    .toHaveAttribute('aria-pressed', 'true');
  await page.getByRole('button', { name: '显示一次令牌', exact: true }).click();
  await expect(page.getByText('pairing-fixture-1', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: '复制配置', exact: true }).click();
  expect(await page.evaluate(() => (window as unknown as { __PAIRING_CLIPBOARD__: string }).__PAIRING_CLIPBOARD__))
    .toContain('Authorization: Bearer pairing-fixture-1');
});

test('cancelling pairing reset preserves the current token and focus', async ({ page }) => {
  await installPairingHost(page);
  const reset = await openPairing(page);
  await page.getByRole('button', { name: '显示一次令牌', exact: true }).click();
  await reset.click();
  await page.keyboard.press('Shift+Tab');
  expect(await page.evaluate(() => !!document.activeElement?.closest('[data-testid="mcp-pairing-dialog"]'))).toBe(true);
  await page.keyboard.press('Escape');
  await expect(page.getByTestId('mcp-pairing-dialog')).toHaveCount(0);
  await expect(reset).toBeFocused();
  await expect(page.getByText('pairing-fixture-0', { exact: true })).toBeVisible();
  expect(await page.evaluate(() => (window as unknown as { __PAIRING_CALLS__: number }).__PAIRING_CALLS__)).toBe(0);
});

test('pending pairing reset blocks duplicate requests and old-token copying', async ({ page }) => {
  await installPairingHost(page, 'pending');
  const reset = await openPairing(page);
  await page.getByRole('button', { name: '显示一次令牌', exact: true }).click();
  await reset.click();
  const confirm = page.getByRole('button', { name: '确认重新配对', exact: true });
  await confirm.click();
  await expect(confirm).toBeDisabled();
  await expect(page.getByRole('button', { name: '复制配置', exact: true })).toBeDisabled();
  await expect(page.getByText('pairing-fixture-0', { exact: true })).toHaveCount(0);
  const blockedReveal = await page.evaluate(async () => {
    const { mcpRuntimeBridge } = await import('/src/bridge/mcpRuntimeBridge.ts');
    return mcpRuntimeBridge.revealTokenOnce().then(() => false, () => true);
  });
  expect(blockedReveal).toBe(true);
  expect(await page.evaluate(() => (window as unknown as { __PAIRING_REVEAL_CALLS__: number }).__PAIRING_REVEAL_CALLS__)).toBe(1);
  await page.keyboard.press('Enter');
  expect(await page.evaluate(() => (window as unknown as { __PAIRING_CALLS__: number }).__PAIRING_CALLS__)).toBe(1);
  await page.evaluate(() => (window as unknown as { __FINISH_PAIRING__: () => void }).__FINISH_PAIRING__());
  await expect(page.getByTestId('mcp-pairing-dialog')).toHaveCount(0);
  await expect(page.getByRole('button', { name: '显示一次令牌', exact: true })).toBeEnabled();
});

test('successful pairing waits for refresh before closing and restoring keyboard focus', async ({ page }) => {
  await installPairingHost(page, 'refresh-pending');
  const reset = await openPairing(page);
  await reset.click();
  const dialog = page.getByTestId('mcp-pairing-dialog');
  const confirm = dialog.getByRole('button', { name: '确认重新配对', exact: true });
  await page.keyboard.press('Shift+Tab');
  await expect(confirm).toBeFocused();
  await page.keyboard.press('Enter');
  await expect.poll(() => page.evaluate(() => typeof (window as unknown as { __FINISH_STATE_REFRESH__?: unknown }).__FINISH_STATE_REFRESH__)).toBe('function');
  await expect(dialog).toBeVisible();
  await expect(confirm).toBeDisabled();
  await page.evaluate(() => (window as unknown as { __FINISH_STATE_REFRESH__: () => void }).__FINISH_STATE_REFRESH__());
  await expect(dialog).toHaveCount(0);
  await expect(reset).toBeEnabled();
  await expect(reset).toBeFocused();
  await expect(page.getByRole('button', { name: '显示一次令牌', exact: true })).toBeEnabled();
});

test('search above pairing owns Tab and Escape, then returns focus to the remaining dialog', async ({ page }) => {
  await installPairingHost(page);
  const reset = await openPairing(page);
  await reset.click();
  const pairing = page.getByTestId('mcp-pairing-dialog');
  await page.getByTestId('workbench-command-trigger').click();
  const search = page.getByTestId('workbench-command-palette');
  await expect(search.getByRole('combobox')).toBeFocused();
  await page.keyboard.press('Tab');
  await expect(search.getByRole('button', { name: '关闭搜索', exact: true })).toBeFocused();
  await page.keyboard.press('Tab');
  await expect(search.getByRole('option').first()).toBeFocused();
  await page.keyboard.press('Escape');
  await expect(search).toHaveCount(0);
  await expect(pairing).toBeVisible();
  await expect(pairing).toBeFocused();
  await page.keyboard.press('Shift+Tab');
  await expect(pairing.getByRole('button', { name: '确认重新配对', exact: true })).toBeFocused();
  await page.keyboard.press('Escape');
  await expect(pairing).toHaveCount(0);
  await expect(reset).toBeFocused();
});

test('a rejected pairing reset is explained inside the dialog and can be retried', async ({ page }) => {
  await installPairingHost(page, 'reject');
  const reset = await openPairing(page);
  await reset.click();
  const dialog = page.getByTestId('mcp-pairing-dialog');
  await dialog.getByRole('button', { name: '确认重新配对', exact: true }).click();
  await expect(dialog.getByRole('alert')).toHaveText('Pairing reset unavailable');
  await expect(dialog.getByRole('button', { name: '确认重新配对', exact: true })).toBeEnabled();
  await dialog.getByRole('button', { name: '确认重新配对', exact: true }).click();
  await expect(dialog).toHaveCount(0);
  expect(await page.evaluate(() => (window as unknown as { __PAIRING_CALLS__: number }).__PAIRING_CALLS__)).toBe(2);
});

test('an older desktop host offers recovery guidance without pretending it can reset pairing', async ({ page }) => {
  await installPairingHost(page, 'legacy');
  await page.addInitScript(() => localStorage.setItem('copperbench.ui.locale', 'en'));
  await page.goto('/');
  await openToolView(page, 'ai');
  await page.getByRole('button', { name: 'Reveal token once', exact: true }).click();
  await page.getByTestId('nav-history').click();
  await openToolView(page, 'ai');
  await expect(page.getByRole('button', { name: 'Reset pairing', exact: true })).toBeDisabled();
  await expect(page.getByText('Reopen this workspace to obtain a new token in this desktop version.', { exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Copy configuration', exact: true })).toBeDisabled();
});

test('a token reveal in flight stays locked across navigation before pairing can reset', async ({ page }) => {
  await installPairingHost(page, 'reveal-pending');
  await openPairing(page);
  await page.getByRole('button', { name: '显示一次令牌', exact: true }).click();
  await page.getByTestId('nav-history').click();
  await openToolView(page, 'ai');
  const reset = page.getByRole('button', { name: '重新配对', exact: true });
  await expect(reset).toBeDisabled();
  await expect(page.getByRole('button', { name: '显示一次令牌', exact: true })).toBeDisabled();
  for (const option of await page.getByRole('group', { name: '权限档位' }).getByRole('button').all()) {
    await expect(option).toBeDisabled();
  }
  // A second caller must also be blocked at the bridge, independently of the
  // disabled controls in the remounted view.
  const conflicts = await page.evaluate(async () => {
    const { mcpRuntimeBridge } = await import('/src/bridge/mcpRuntimeBridge.ts');
    const results = await Promise.allSettled([
      mcpRuntimeBridge.revealTokenOnce(),
      mcpRuntimeBridge.resetPairing(),
      mcpRuntimeBridge.setPermissionProfile('workspace')
    ]);
    return results.map(result => result.status);
  });
  expect(conflicts).toEqual(['rejected', 'rejected', 'rejected']);
  expect(await page.evaluate(() => (window as unknown as { __PAIRING_CALLS__: number }).__PAIRING_CALLS__)).toBe(0);
  expect(await page.evaluate(() => (window as unknown as { __PAIRING_PROFILE_CALLS__: number }).__PAIRING_PROFILE_CALLS__)).toBe(0);
  expect(await page.evaluate(() => (window as unknown as { __PAIRING_REVEAL_CALLS__: number }).__PAIRING_REVEAL_CALLS__)).toBe(1);
  await page.evaluate(() => (window as unknown as { __FINISH_REVEAL__: () => void }).__FINISH_REVEAL__());
  await expect(reset).toBeEnabled();
  await expect(page.getByText('pairing-fixture-0', { exact: true })).toHaveCount(0);
  await reset.click();
  await page.getByRole('button', { name: '确认重新配对', exact: true }).click();
  await expect(page.getByTestId('mcp-pairing-dialog')).toHaveCount(0);
  await expect(page.getByRole('button', { name: '显示一次令牌', exact: true })).toBeEnabled();
  expect(await page.evaluate(() => (window as unknown as { __PAIRING_CALLS__: number }).__PAIRING_CALLS__)).toBe(1);
});

test('pairing reset recovers an unavailable state without mistaking the placeholder for the actual profile', async ({ page }) => {
  await installPairingHost(page, 'state-unavailable');
  const reset = await openPairing(page);
  const runtime = page.getByRole('region', { name: '本机 MCP 服务', exact: true });
  const profiles = page.getByRole('group', { name: '权限档位', exact: true });
  await expect(runtime.getByText('MCP state temporarily unavailable', { exact: true })).toBeVisible();
  await expect(profiles.locator('[aria-pressed="true"]')).toHaveCount(0);
  await expect(reset).toBeEnabled();
  await reset.click();
  const dialog = page.getByTestId('mcp-pairing-dialog');
  await dialog.getByRole('button', { name: '确认重新配对', exact: true }).click();

  await expect(dialog).toHaveCount(0);
  await expect(page.getByTestId('permission-status')).toHaveText('已重新配对，请显示新令牌并更新客户端配置。');
  await expect(profiles.getByRole('button', { name: '只读' })).toHaveAttribute('aria-pressed', 'true');
  await expect(runtime.getByText('MCP state temporarily unavailable', { exact: true })).toHaveCount(0);
  await expect(page.getByRole('button', { name: '显示一次令牌', exact: true })).toBeEnabled();
  const calls = await page.evaluate(() => {
    const hostWindow = window as unknown as { __PAIRING_CALLS__: number; __PAIRING_PROFILE_CALLS__: number };
    return { reset: hostWindow.__PAIRING_CALLS__, permission: hostWindow.__PAIRING_PROFILE_CALLS__ };
  });
  expect(calls).toEqual({ reset: 1, permission: 0 });
});
