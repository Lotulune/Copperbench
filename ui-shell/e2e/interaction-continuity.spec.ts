import { expect, test, type Page } from '@playwright/test';
import type { TaskLogEntry, TaskSummary } from '../src/types/contract';

const TASK_IDS = {
  failed: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
  succeeded: 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
  running: 'cccccccc-cccc-4ccc-8ccc-cccccccccccc'
};

function fixtureTask(id: string, state: TaskSummary['state']): TaskSummary {
  return {
    id, kind: 'build', state, cancellable: state === 'running',
    progress: state === 'running' ? 0.5 : 1,
    stage: { key: 'fixture.interaction.stage', fallback: `Fixture build ${state}` },
    startedAt: '2026-10-19T20:00:00Z',
    ...(state === 'running' ? {} : { completedAt: '2026-10-19T20:01:00Z' }),
    diagnostics: { error: state === 'failed' ? 1 : 0, warning: 0, info: 0 }
  };
}

function fixtureLog(sequence: number, text: string): TaskLogEntry {
  return { sequence, timestamp: '2026-10-19T20:00:30Z', level: 'info', text };
}

/** Controlled preview state: this exercises shell interactions, not native builds or JCEF. */
async function installTasks(page: Page, options: { running?: boolean; longLogs?: boolean } = {}) {
  const tasks = [fixtureTask(TASK_IDS.failed, 'failed'), fixtureTask(TASK_IDS.succeeded, 'succeeded')];
  if (options.running) tasks.push(fixtureTask(TASK_IDS.running, 'running'));
  const logs = {
    [TASK_IDS.failed]: [{ ...fixtureLog(1, 'Fixture failed task A log'), level: 'error' as const }],
    [TASK_IDS.succeeded]: [fixtureLog(1, 'Fixture succeeded task B log')],
    [TASK_IDS.running]: options.longLogs
      ? Array.from({ length: 120 }, (_, index) => fixtureLog(index + 1, `Fixture running log ${index + 1}`))
      : [fixtureLog(1, 'Fixture running task C log')]
  };
  await page.evaluate(async ({ tasks, logs }) => {
    const modulePath = '/src/bridge/index.ts';
    const { coreBridge } = await import(modulePath);
    const state = coreBridge.getState();
    state.tasks = Object.fromEntries(tasks.map(task => [task.id, task]));
    state.taskLogs = logs;
    state.taskDiagnostics = {};
    state.workbench.activeTasks = tasks.filter(task => task.state === 'running');
    coreBridge.notifyState();
  }, { tasks, logs });
  await expect(page.getByTestId('task-failure')).toBeVisible();
}

async function appendLogs(page: Page, entries: TaskLogEntry[]) {
  await page.evaluate(async ({ taskId, entries }) => {
    const modulePath = '/src/bridge/index.ts';
    const { coreBridge } = await import(modulePath);
    const state = coreBridge.getState();
    // A fresh array also models a read returning the same log sequences.
    state.taskLogs[taskId] = [...state.taskLogs[taskId], ...entries];
    coreBridge.notifyState();
  }, { taskId: TASK_IDS.running, entries });
}

test.beforeEach(async ({ page }) => {
  await page.addInitScript(() => localStorage.setItem('copperbench.theme', 'light'));
  await page.goto('/');
  await expect(page.getByTestId('workbench-main')).toBeVisible();
});

test('the failure banner opens the failed task after a different task was selected', async ({ page }) => {
  await installTasks(page);
  const drawer = page.getByTestId('task-drawer');
  const selector = drawer.getByRole('combobox');
  await page.getByTestId('recent-tasks-button').click();
  await selector.selectOption(TASK_IDS.succeeded);
  await expect(page.getByTestId('task-log-stream')).toContainText('Fixture succeeded task B log');
  await page.getByTestId('task-drawer-close').click();

  await page.getByTestId('open-failed-task-logs-btn').click();
  await expect(selector).toHaveValue(TASK_IDS.failed);
  await expect(page.getByTestId('task-log-stream')).toContainText('Fixture failed task A log');
  await expect(page.getByTestId('task-log-stream')).not.toContainText('Fixture succeeded task B log');
});

test('the overview progress row opens its own running task', async ({ page }) => {
  await installTasks(page, { running: true });
  const selector = page.getByTestId('task-drawer').getByRole('combobox');
  await page.getByTestId('open-failed-task-logs-btn').click();
  await selector.selectOption(TASK_IDS.succeeded);
  await page.getByTestId('task-drawer-close').click();

  await page.locator(`.overview-task[data-task-id="${TASK_IDS.running}"]`)
    .getByRole('button', { name: '查看日志', exact: true }).click();
  await expect(selector).toHaveValue(TASK_IDS.running);
  await expect(page.getByTestId('task-log-stream')).toContainText('Fixture running task C log');
});

test('the running-task pill switches another open task and still toggles its own console', async ({ page }) => {
  await installTasks(page, { running: true });
  const drawer = page.getByTestId('task-drawer');
  const selector = drawer.getByRole('combobox');
  const pill = page.getByTestId('running-task-pill');
  await page.getByTestId('open-failed-task-logs-btn').click();
  await selector.selectOption(TASK_IDS.succeeded);

  await pill.click();
  await expect(drawer).toBeVisible();
  await expect(selector).toHaveValue(TASK_IDS.running);
  await expect(page.getByTestId('task-log-stream')).toContainText('Fixture running task C log');
  await pill.click();
  await expect(drawer).toBeHidden();
  await pill.click();
  await expect(drawer).toBeVisible();
  await expect(selector).toHaveValue(TASK_IDS.running);
});

test('initial reverse Tab stays in the dialog, with boundary cycling and focus restoration', async ({ page }) => {
  const trigger = page.getByTestId('empty-primary-action');
  await trigger.click();
  const dialog = page.getByTestId('create-element-modal').getByRole('dialog');
  const firstControl = dialog.getByRole('button', { name: '关闭创建元素对话框', exact: true });
  const lastControl = page.getByTestId('create-element-submit-btn');
  await expect(lastControl).toBeEnabled();
  await expect(dialog).toBeFocused();

  // The first key is deliberately Shift+Tab, without a preceding forward Tab.
  await page.keyboard.press('Shift+Tab');
  await expect(lastControl).toBeFocused();
  await page.keyboard.press('Tab');
  await expect(firstControl).toBeFocused();
  await page.keyboard.press('Shift+Tab');
  await expect(lastControl).toBeFocused();
  await page.keyboard.press('Escape');
  await expect(dialog).toBeHidden();
  await expect(trigger).toBeFocused();
});

test('a revision conflict owns keyboard navigation without closing the underlying creation draft', async ({ page }) => {
  const trigger = page.getByTestId('empty-primary-action');
  await trigger.click();
  await page.getByTestId('create-element-name-input').fill('conflicting_draft');
  // An intervening writer makes this command stale; use the real rejection path.
  await page.evaluate(async () => {
    const modulePath = '/src/bridge/index.ts';
    const { coreBridge } = await import(modulePath);
    const original = coreBridge.sendCommand.bind(coreBridge);
    coreBridge.sendCommand = (command: { operation: string; expectedRevision: number }) => original(
      command.operation === 'create_mod_element' ? { ...command, expectedRevision: command.expectedRevision - 1 } : command);
  });
  const submit = page.getByTestId('create-element-submit-btn');
  await submit.click();
  const conflict = page.getByTestId('revision-conflict-dialog');
  await expect(conflict).toBeVisible();
  await expect(page.getByTestId('create-element-modal')).toBeVisible();
  await page.keyboard.press('Tab');
  await expect(conflict.getByRole('button', { name: '关闭', exact: true })).toBeFocused();
  await page.keyboard.press('Tab');
  await expect(conflict.getByRole('button', { name: '继续编辑', exact: true })).toBeFocused();
  await page.keyboard.press('Tab');
  await expect(page.getByTestId('conflict-refresh-btn')).toBeFocused();
  await page.keyboard.press('Tab');
  await expect(conflict.getByRole('button', { name: '关闭', exact: true })).toBeFocused();
  await page.keyboard.press('Escape');
  await expect(conflict).toBeHidden();
  await expect(page.getByTestId('create-element-name-input')).toHaveValue('conflicting_draft');
  expect(await page.evaluate(() => !!document.activeElement?.closest('[data-testid="create-element-modal"]'))).toBe(true);
  await page.keyboard.press('Escape');
  await expect(page.getByTestId('create-element-modal')).toBeHidden();
  await expect(trigger).toBeFocused();
});

test('streaming logs preserve the reading position and resume following only on request', async ({ page }, testInfo) => {
  await installTasks(page, { running: true, longLogs: true });
  await page.getByTestId('open-failed-task-logs-btn').click();
  const selector = page.getByTestId('task-drawer').getByRole('combobox');
  await selector.selectOption(TASK_IDS.running);
  const log = page.getByTestId('task-log-stream');
  const scrollTop = () => log.evaluate(element => element.scrollTop);
  const distanceFromLatest = () => log.evaluate(element => element.scrollHeight - element.clientHeight - element.scrollTop);
  await expect.poll(distanceFromLatest).toBeLessThanOrEqual(2);

  await log.hover();
  await page.mouse.wheel(0, -1200);
  await expect.poll(distanceFromLatest).toBeGreaterThan(1000);
  const readingTop = await scrollTop();
  await appendLogs(page, [fixtureLog(121, 'New streamed line 121')]);
  await expect(log).toContainText('New streamed line 121');
  await expect.poll(scrollTop).toBeCloseTo(readingTop, 0);

  const newLogs = page.getByTestId('task-new-log-count');
  const jump = page.getByRole('button', { name: '回到最新', exact: true });
  await expect(newLogs).toHaveText('1 条新日志');
  await expect(jump).toBeVisible();
  const contrast = await newLogs.evaluate(element => {
    const luminance = (rgb: string) => rgb.match(/[\d.]+/g)!.slice(0, 3)
      .map(value => Number(value) / 255)
      .map(value => value <= 0.04045 ? value / 12.92 : ((value + 0.055) / 1.055) ** 2.4)
      .reduce((sum, value, index) => sum + value * [0.2126, 0.7152, 0.0722][index], 0);
    const values = [luminance(getComputedStyle(element).color),
      luminance(getComputedStyle(element.parentElement!).backgroundColor)].sort((a, b) => b - a);
    return (values[0] + 0.05) / (values[1] + 0.05);
  });
  expect(contrast).toBeGreaterThanOrEqual(4.5);
  await testInfo.attach('paused-log-contrast', { body: JSON.stringify({ contrast }), contentType: 'application/json' });
  await page.screenshot({ path: testInfo.outputPath('paused-log-light.png') });
  await appendLogs(page, []);
  await expect(newLogs).toHaveText('1 条新日志');
  await expect.poll(scrollTop).toBeCloseTo(readingTop, 0);
  await appendLogs(page, [fixtureLog(122, 'New streamed line 122')]);
  await expect(newLogs).toHaveText('2 条新日志');

  await jump.click();
  await expect(jump).toBeHidden();
  await expect.poll(distanceFromLatest).toBeLessThanOrEqual(2);
  await appendLogs(page, [fixtureLog(123, 'New followed line 123')]);
  await expect(log).toContainText('New followed line 123');
  await expect.poll(distanceFromLatest).toBeLessThanOrEqual(2);

  // Paused state and pending counts belong to one task, not the next selection.
  await log.hover();
  await page.mouse.wheel(0, -1200);
  await expect(jump).toBeVisible();
  await appendLogs(page, [fixtureLog(124, 'New streamed line 124')]);
  await expect(newLogs).toHaveText('1 条新日志');
  await selector.selectOption(TASK_IDS.succeeded);
  await expect(log).toContainText('Fixture succeeded task B log');
  await expect(jump).toBeHidden();
  await selector.selectOption(TASK_IDS.running);
  await expect.poll(distanceFromLatest).toBeLessThanOrEqual(2);
  await expect(jump).toBeHidden();
});
