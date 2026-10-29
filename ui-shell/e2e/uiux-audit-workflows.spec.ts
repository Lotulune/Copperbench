import { expect, test, type Page } from '@playwright/test';

const taskA = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';
const taskB = 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb';
async function seedTasks(page: Page) {
  await page.evaluate(async ({ taskA, taskB }) => {
    const path = '/src/bridge/index.ts'; const { coreBridge } = await import(path);
    const state = coreBridge.getState();
    const task = (id: string, status: string, date: string) => ({ id, kind: 'build', state: status, cancellable: false,
      progress: null, stage: { key: 'fixture.stage', fallback: id === taskA ? 'Failure A' : 'Task B' }, startedAt: date, completedAt: date,
      diagnostics: { error: status === 'failed' ? 1 : 0, warning: 0, info: 0 } });
    // Deliberately insert the older failure first; insertion order is not recency.
    state.tasks = { [taskB]: task(taskB, 'failed', '2026-10-15T03:00:00Z'), [taskA]: task(taskA, 'failed', '2026-10-16T03:00:00Z') };
    state.taskLogs = Object.fromEntries([taskA, taskB].map(id => [id, Array.from({ length: 160 }, (_, index) => ({
      sequence: index + 1, timestamp: '2026-10-16T03:04:05Z', level: index % 13 === 0 ? 'error' : 'info', text: `${id === taskA ? 'A' : 'B'} line ${index + 1}`
    }))]));
    state.taskDiagnostics = { [taskA]: [{ code: 'AUDIT_A', severity: 'error', message: { key: 'fixture.a', fallback: 'Diagnostic for A' }, path: null, elementId: null, recoverable: false, actions: [] }] };
    coreBridge.notifyState();
  }, { taskA, taskB });
}

test('failure navigation selects the newest failed task, including its diagnostics and logs', async ({ page }) => {
  await page.goto('/'); await seedTasks(page);
  await page.getByTestId('recent-tasks-button').click();
  await page.getByRole('combobox', { name: '最近任务' }).selectOption(taskB);
  await page.getByTestId('task-drawer-close').click();
  await page.getByTestId('open-failed-task-logs-btn').click();
  await expect(page.getByTestId('task-drawer')).toHaveAttribute('data-task-id', taskA);
  await expect(page.getByTestId('task-diagnostics')).toContainText('Diagnostic for A');
  await expect(page.getByTestId('task-log-stream')).toContainText('A line 1');
  await expect(page.getByTestId('task-log-stream')).not.toContainText('B line');
  await page.evaluate(async id => {
    const path = '/src/bridge/index.ts'; const { coreBridge } = await import(path);
    delete coreBridge.getState().tasks[id]; coreBridge.notifyState();
  }, taskA);
  await expect(page.getByTestId('task-status-unavailable')).toContainText(taskA);
  await expect(page.getByTestId('task-log-stream')).toHaveCount(0);
  await page.getByRole('combobox', { name: '最近任务' }).selectOption(taskB);
  await expect(page.getByTestId('task-log-stream')).toContainText('B line');
});

test('appending logs preserves paused reading, counts new lines, and keeps task positions separate', async ({ page }) => {
  await page.goto('/'); await seedTasks(page);
  await page.getByTestId('open-failed-task-logs-btn').click();
  const stream = page.getByTestId('task-log-stream');
  await stream.evaluate(element => { element.scrollTop = 150; element.dispatchEvent(new Event('scroll')); });
  await expect(page.getByTestId('task-log-latest')).toBeVisible();
  const position = await stream.evaluate(element => element.scrollTop);
  await page.evaluate(async id => {
    const path = '/src/bridge/index.ts'; const { coreBridge } = await import(path);
    coreBridge.getState().taskLogs[id].push({ sequence: 161, timestamp: '2026-10-16T03:04:06Z', level: 'error', text: 'A newest diagnostic' });
    coreBridge.notifyState();
  }, taskA);
  await expect(page.getByTestId('task-log-latest')).toContainText('+1');
  expect(await stream.evaluate(element => element.scrollTop)).toBe(position);
  await page.getByRole('combobox', { name: '最近任务' }).selectOption(taskB);
  await stream.evaluate(element => { element.scrollTop = 300; element.dispatchEvent(new Event('scroll')); });
  await page.getByRole('combobox', { name: '最近任务' }).selectOption(taskA);
  await expect.poll(() => stream.evaluate(element => element.scrollTop)).toBe(position);
  await page.getByTestId('task-log-latest').click();
  await expect(page.getByTestId('task-log-pause')).toBeVisible();
  await expect.poll(() => stream.evaluate(element => Math.abs(element.scrollHeight - element.clientHeight - element.scrollTop))).toBeLessThan(2);
  await page.getByTestId('task-log-search').fill('newest');
  await page.getByRole('combobox', { name: '日志级别' }).selectOption('error');
  await expect(stream.locator('.task-log-line')).toHaveCount(1);
  await page.evaluate(() => {
    Object.defineProperty(navigator, 'clipboard', { configurable: true, value: { writeText: async (value: string) => { (window as any).auditClipboard = value; } } });
  });
  await page.getByTestId('task-log-copy').click();
  expect(await page.evaluate(() => (window as any).auditClipboard)).toBe('2026-10-16T03:04:06Z [ERROR] A newest diagnostic');
});

test('queued and cancelled tasks have distinct non-failure semantics', async ({ page }) => {
  await page.goto('/'); await seedTasks(page); await page.getByTestId('open-failed-task-logs-btn').click();
  for (const [status, tone] of [['queued', 'blue'], ['running', 'copper'], ['succeeded', 'green'], ['failed', 'red'], ['cancelled', 'neutral']]) {
    await page.evaluate(async ({ id, status }) => {
      const path = '/src/bridge/index.ts'; const { coreBridge } = await import(path);
      coreBridge.getState().tasks[id].state = status; coreBridge.notifyState();
    }, { id: taskA, status });
    await expect(page.getByTestId('task-current-state')).toHaveClass(`badge badge-${tone}`);
    await expect(page.getByTestId('task-current-state')).not.toBeEmpty();
  }
});

test('diagnostic navigation expands health and focuses the requested error', async ({ page }) => {
  await page.goto('/');
  await page.evaluate(async () => {
    const path = '/src/bridge/index.ts'; const { coreBridge } = await import(path);
    const send = coreBridge.sendQuery.bind(coreBridge);
    coreBridge.sendQuery = async (query: any) => {
      const result = await send(query);
      if (query.operation === 'get_workspace_health') result.data.diagnostics = { total: 1, error: 1, warning: 0, info: 0, collectionState: 'complete',
        items: [{ code: 'AUDIT_HEALTH', severity: 'error', message: { key: 'fixture.health', fallback: 'Fix the audit fixture reference' }, actions: [{ id: 'fixture-open', kind: 'open_view', target: 'elements', label: { key: 'fixture.action', fallback: 'Inspect element' } }] }] };
      return result;
    };
  });
  await page.getByTestId('nav-assets').click();
  await expect(page.getByTestId('diagnostics-badge')).toContainText('1 错误');
  await page.getByTestId('diagnostics-badge').click();
  await expect(page.getByTestId('workspace-health-panel').locator('details')).toHaveAttribute('open', '');
  await expect(page.getByText('Fix the audit fixture reference')).toBeVisible();
  await expect(page.getByTestId('workspace-health-panel').locator('[data-diagnostic-severity="error"]')).toBeFocused();
  await expect(page.getByRole('button', { name: 'Inspect element' })).toBeVisible();
});

test('unknown connection and unknown task progress do not claim ready or zero percent', async ({ page }) => {
  await page.goto('/'); await expect(page.getByTestId('core-status')).toHaveAttribute('data-healthy', 'true');
  await seedTasks(page);
  await page.evaluate(async id => {
    const path = '/src/bridge/index.ts'; const { coreBridge } = await import(path);
    coreBridge.getState().workbench.activeTasks = [{ ...coreBridge.getState().tasks[id], state: 'running', progress: null }]; coreBridge.notifyState();
  }, taskA);
  await expect(page.getByTestId('running-task-pill')).not.toContainText('0%');
  await expect(page.locator('.overview-task progress')).not.toHaveAttribute('value');
  await page.evaluate(async () => {
    const path = '/src/bridge/index.ts'; const { coreBridge } = await import(path);
    const state = coreBridge.getState(); state.workbench = null; state.viewportState = 'loading'; coreBridge.notifyState();
  });
  await expect(page.getByTestId('core-status')).toHaveAttribute('data-healthy', 'false');
  await expect(page.getByTestId('core-status')).toContainText('连接中');
  await expect(page.getByTestId('offline-status')).toContainText('未知');
  await expect(page.getByTestId('bridge-status')).toHaveAttribute('data-healthy', 'false');
});

test('element filters, page, selection and scrolling survive navigation without leaking to another workspace', async ({ page }) => {
  await page.goto('/');
  await page.evaluate(async () => {
    const path = '/src/bridge/index.ts'; const { coreBridge } = await import(path);
    const state = coreBridge.getState(); const template = state.elements.find((element: any) => element.type === 'block');
    state.elements = Array.from({ length: 75 }, (_, index) => ({ ...structuredClone(template), id: crypto.randomUUID(), name: `audit_block_${String(index).padStart(3, '0')}`, displayName: `Audit block ${index}` }));
    coreBridge.notifyState();
  });
  await page.getByTestId('nav-elements').click();
  await page.getByTestId('elements-search-input').fill('audit');
  await page.getByTestId('filter-type-block').click();
  await page.getByTestId('elements-sort-select').selectOption('name_asc');
  await page.getByTestId('elements-next-page-btn').click();
  await expect(page.locator('.elements-pagination')).toContainText('2 / 4');
  await page.locator('.element-card').first().click();
  await page.locator('.elements-results').evaluate(element => { element.scrollTop = 180; element.dispatchEvent(new Event('scroll')); });
  const position = await page.locator('.elements-results').evaluate(element => element.scrollTop);
  await page.getByTestId('nav-assets').click(); await page.getByTestId('nav-elements').click();
  await expect(page.getByTestId('elements-search-input')).toHaveValue('audit');
  await expect(page.getByTestId('filter-type-block')).toHaveAttribute('aria-pressed', 'true');
  await expect(page.getByTestId('elements-sort-select')).toHaveValue('name_asc');
  await expect(page.locator('.elements-pagination')).toContainText('2 / 4');
  await expect.poll(() => page.locator('.elements-results').evaluate(element => element.scrollTop)).toBe(position);
  await expect(page.getByTestId('element-inspector')).toBeVisible();
  await page.evaluate(async () => {
    const path = '/src/bridge/index.ts'; const { coreBridge } = await import(path);
    coreBridge.getState().workbench.workspace.id = 'cccccccc-cccc-4ccc-8ccc-cccccccccccc'; coreBridge.notifyState();
  });
  await expect(page.getByTestId('elements-search-input')).toHaveValue('');
  await expect(page.locator('.elements-pagination')).toContainText('1 / 4');
});

test('delete confirmation uses a safe default and Escape preserves the element', async ({ page }) => {
  await page.goto('/'); await page.getByTestId('nav-elements').click();
  await page.locator('.element-card').filter({ hasText: 'Copper Lamp' }).click();
  await page.getByTestId('inspector-delete-btn').click();
  const confirm = page.getByTestId('confirm-dialog');
  await expect(confirm).toContainText('Copper Lamp');
  await expect(confirm.getByRole('button', { name: '取消', exact: true })).toBeFocused();
  await page.keyboard.press('Escape');
  await expect(confirm).toHaveCount(0);
  await expect(page.locator('.element-card').filter({ hasText: 'Copper Lamp' })).toBeVisible();
  await expect(page.getByTestId('inspector-delete-btn')).toBeFocused();
});

test('timestamps follow the application locale and timezone while preserving raw log time', async ({ page }) => {
  await page.addInitScript(() => localStorage.setItem('copperbench.ui.locale', 'en'));
  await page.goto('/'); await seedTasks(page); await page.getByTestId('open-failed-task-logs-btn').click();
  const date = async (locale: string) => page.evaluate(async locale => {
    const path = '/src/i18n/dateTime.ts'; const formats = await import(path);
    return formats.formatDateTime('2026-10-16T03:00:00Z', locale);
  }, locale);
  await expect(page.getByRole('combobox', { name: 'Recent tasks' })).toContainText(await date('en'));
  await page.getByTestId('ui-language-select').selectOption('zh');
  await expect(page.getByRole('combobox', { name: '最近任务' })).toContainText(await date('zh'));
  await expect(page.locator('.task-log-line time').first()).toHaveAttribute('datetime', '2026-10-16T03:04:05Z');
  await expect(page.locator('.task-log-timezone')).not.toBeEmpty();
});
