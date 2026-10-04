import { test, expect } from '@playwright/test';

test('element search trims pasted whitespace and distinguishes no results from an empty workspace', async ({ page }) => {
  await page.goto('/');
  await page.getByTestId('nav-elements').click();
  const search = page.getByTestId('elements-search-input');
  await search.fill('   ');
  await expect(page.locator('.element-card')).toHaveCount(2);
  await expect(page.locator('.elements-empty')).not.toBeVisible();
  await search.fill('  COPPER_LAMP  ');
  await expect(page.locator('.element-card')).toHaveCount(1);
  await expect(page.locator('.element-card')).toContainText('Copper Lamp');
  await search.fill('no_matching_element');
  await expect(page.locator('.elements-empty')).toContainText('没有匹配');
  await page.locator('.elements-empty').getByRole('button', { name: '清除筛选' }).click();
  await expect(page.locator('.element-card')).toHaveCount(2);
});

for (const theme of ['dark', 'light'] as const) {
  test(`scenario control keeps readable text in the ${theme} theme`, async ({ page }) => {
    await page.emulateMedia({ colorScheme: theme, reducedMotion: 'reduce' });
    await page.goto('/');
    const ratio = await page.getByTestId('scenario-switcher-trigger').evaluate(button => {
      const style = getComputedStyle(button);
      const luminance = (value: string) => value.match(/[\d.]+/g)!.slice(0, 3)
        .map(Number).map(value => {
          const channel = value / 255;
          return channel <= 0.04045 ? channel / 12.92 : ((channel + 0.055) / 1.055) ** 2.4;
        }).reduce((sum, value, index) => sum + value * [0.2126, 0.7152, 0.0722][index], 0);
      const foreground = luminance(style.color), background = luminance(style.backgroundColor);
      return (Math.max(foreground, background) + 0.05) / (Math.min(foreground, background) + 0.05);
    });
    expect(ratio).toBeGreaterThanOrEqual(4.5);
  });
}

test('compact layout retains a disconnected Core status', async ({ page }) => {
  await page.setViewportSize({ width: 500, height: 800 });
  await page.addInitScript(() => {
    const modulePath = '/src/mock/mockBridge.ts';
    const ready = import(modulePath).then(({ MockCoreBridge }) => new MockCoreBridge());
    window.copperbenchHost = {
      workspaceId: '11111111-1111-4111-8111-111111111111', onEvent() { return () => {}; },
      async invoke(raw) {
        const core = await ready, request = JSON.parse(raw);
        const result = request.messageType === 'handshake' ? await core.negotiateHandshake(request)
          : request.messageType === 'command' ? await core.sendCommand(request) : await core.sendQuery(request);
        if (request.operation === 'get_workbench') result.data.connection.core = 'disconnected';
        return JSON.stringify(result);
      }
    };
  });
  await page.goto('/');
  await expect(page.getByTestId('core-status')).toBeVisible();
  await expect(page.getByTestId('core-status')).toContainText('已断开');
});

test('compact inspector moves focus into visible controls and restores its invoker', async ({ page }) => {
  await page.setViewportSize({ width: 500, height: 800 });
  await page.goto('/');
  await page.getByTestId('nav-elements').click();
  const card = page.locator('.element-card').first();
  await card.focus();
  await page.keyboard.press('Enter');
  await expect(page.getByTestId('inspector-close-btn')).toBeFocused();
  await expect(page.getByTestId('elements-search-input')).not.toBeVisible();
  await page.keyboard.press('Tab');
  await expect.poll(() => page.evaluate(() => !!document.activeElement?.closest('.element-inspector'))).toBe(true);
  await page.getByTestId('inspector-close-btn').click();
  await expect(card).toBeFocused();
  await expect(page.getByTestId('elements-search-input')).toBeVisible();
});

test('compact run menu exposes actions, dismisses with Escape, and opens task feedback', async ({ page }) => {
  await page.setViewportSize({ width: 500, height: 800 });
  await page.goto('/');
  const trigger = page.getByTestId('compact-run-menu');
  await trigger.click();
  await expect(page.locator('.compact-run-popover')).toBeVisible();
  await page.keyboard.press('Escape');
  await expect(page.locator('.compact-run-popover')).not.toBeVisible();
  await expect(trigger).toBeFocused();
  await page.keyboard.press('Enter');
  await page.locator('.compact-run-popover').getByRole('button', { name: '在暂存区运行数据生成', exact: true }).click();
  await expect(page.locator('.compact-run-popover')).not.toBeVisible();
  await expect(page.getByTestId('task-drawer')).toBeVisible();
});
