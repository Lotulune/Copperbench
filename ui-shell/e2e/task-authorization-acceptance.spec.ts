import { test, expect } from '@playwright/test';
import { CopperbenchClient } from '../../sdk/typescript/copperbench';

test('user reviews bounded authority, cancels, issues and revokes it', async ({ page }, testInfo) => {
  await page.goto('/');
  await page.getByTestId('nav-ai').click();
  const panel = page.getByRole('region', { name: '任务授权', exact: true });
  await expect(panel.getByLabel('授权目录（绝对路径，包含子目录）')).toHaveValue('D:/MockWorkspace');
  await panel.getByLabel('任务名称', { exact: true }).fill('寻路铃验收');
  await panel.getByLabel('有效期', { exact: true }).selectOption('3600');
  await panel.getByRole('checkbox', { name: '运行客户端', exact: true }).uncheck();
  await panel.getByRole('button', { name: '审查并创建授权' }).click();
  const dialog = page.getByRole('dialog', { name: '确认任务授权范围' });
  await expect(dialog).toContainText('D:/MockWorkspace');
  await expect(dialog).toContainText('1 小时');
  await expect(dialog).not.toContainText('运行客户端');
  await page.screenshot({ path: testInfo.outputPath('authority-review-dark.png') });
  expect(await dialog.evaluate(element => element.contains(document.activeElement))).toBe(true);
  await page.keyboard.press('Escape');
  await expect(dialog).not.toBeVisible();
  await expect(panel.getByText('暂无任务授权。')).toBeVisible();
  await page.getByRole('button', { name: '切换到亮色主题', exact: true }).click();
  await panel.getByRole('button', { name: '审查并创建授权' }).click();
  await page.screenshot({ path: testInfo.outputPath('authority-review-light.png') });
  await dialog.getByRole('button', { name: '确认授权', exact: true }).click();
  const grants = panel.getByRole('list', { name: '已签发的任务授权' });
  await expect(grants).toContainText('寻路铃验收');
  await expect(grants.getByLabel('授权 ID')).toHaveText(/[a-f0-9-]{36}/);
  await grants.getByRole('button', { name: '撤销授权' }).click();
  await expect(grants).toContainText('已撤销');
  await expect(grants.getByRole('button', { name: '复制授权 ID' })).toBeDisabled();
  await expect(grants.getByRole('button', { name: '撤销授权' })).toBeDisabled();
});

test('dedicated server authority requires a separate EULA choice', async ({ page }) => {
  await page.goto('/'); await page.getByTestId('nav-ai').click();
  const panel = page.getByRole('region', { name: '任务授权', exact: true });
  await panel.getByRole('checkbox', { name: '运行专用服务器', exact: true }).check();
  const acceptance = panel.getByRole('checkbox', { name: /我已阅读并接受/ });
  await expect(acceptance).not.toBeChecked();
  await panel.getByRole('button', { name: '审查并创建授权' }).click();
  await expect(page.getByRole('dialog', { name: '确认任务授权范围' })).not.toBeVisible();
  await acceptance.check(); await panel.getByRole('button', { name: '审查并创建授权' }).click();
  await expect(page.getByRole('dialog', { name: '确认任务授权范围' })).toContainText('已明确接受');
});

test('task drawer exposes actual counts, cases and artifact identity', async ({ page }) => {
  await page.goto('/'); await page.getByRole('button', { name: '运行已有 GameTest', exact: true }).click();
  const report = page.getByRole('region', { name: 'GameTest 验收报告' });
  await expect(report).toContainText('配置的测试已通过');
  await expect(report.locator('dl')).toContainText('发现3');
  await expect(report.locator('dl')).toContainText('执行2');
  await expect(report.locator('dl')).toContainText('跳过1');
  await expect(report).toContainText('验收执行 1 个');
  await report.getByText('用例与被测内容', { exact: true }).click();
  await expect(report).toContainText('MockFixture.modLoads');
  await expect(report).toContainText('b'.repeat(64));
});

test('TypeScript SDK forwards a task grant only to authorized mutations and keeps query arguments clean', async () => {
  const calls: { name: string; arguments: Record<string, unknown> }[] = [];
  const client = new CopperbenchClient({ endpoint: 'http://127.0.0.1:61999/mcp', token: 'fixture', workspaceId: 'fixture',
    taskAuthorizationId: 'a'.repeat(36), fetch: (async (_input, init) => {
      const request = JSON.parse(String(init?.body)); calls.push(request.params);
      return new Response(JSON.stringify({ jsonrpc: '2.0', id: request.id,
        result: { content: [{ type: 'text', text: JSON.stringify({ status: 'accepted' }) }] } }), { headers: { 'Content-Type': 'application/json' } });
    }) as typeof fetch });
  await client.prepareGameTests(3); await client.runGameTest(3); await client.getTask('task', 8);
  await client.listTaskAuthorizations(); await client.revokeTaskAuthorization('grant', 3);
  expect(calls.map(call => call.name)).toEqual(['prepare_game_tests', 'run_gametest', 'get_task', 'list_task_authorizations', 'revoke_task_authorization']);
  expect(calls[0].arguments.taskAuthorizationId).toBe('a'.repeat(36));
  expect(calls[1].arguments.taskAuthorizationId).toBe('a'.repeat(36));
  expect(calls[2].arguments).toEqual({ taskId: 'task', afterLogSequence: 8 });
  expect(calls[3].arguments).toEqual({});
  expect(calls[4].arguments).toEqual({ authorizationId: 'grant', expectedRevision: 3 });
});
