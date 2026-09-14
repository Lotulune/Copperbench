import { test, expect } from '@playwright/test';

test('browser preview never pretends to execute Python', async ({ page }) => {
  await page.goto('/');
  await page.getByTestId('nav-python').click();
  await expect(page.getByTestId('python-workbench')).toContainText('浏览器预览不会执行脚本');
  await expect(page.getByTestId('python-run')).toBeDisabled();
});

test('workbench preserves drafts, executes through the host, completes code and stops a busy worker', async ({ page }) => {
  await page.addInitScript(() => {
    let sequence = 0;
    let selectionVersion = 0;
    let activeElementId: string | null = null;
    let view = 'hub';
    let state = 'stopped';
    let requestId = '';
    let lastResult = 'idle';
    let errorLine: number | null = null;
    let completions: string[] = [];
    const output: { sequence: number; channel: string; text: string }[] = [];
    const log = (text: string, channel = 'stdout') => output.push({ sequence: ++sequence, channel, text });
    const context = () => ({ workspaceId: '11111111-1111-4111-8111-111111111111', activeElementId, selectionVersion, view });
    const snapshot = (after = 0) => ({ state, requestId, lastResult, errorLine, completions, operators: [], context: context(),
      output: output.filter(entry => entry.sequence > after), lastSequence: sequence, pythonVersion: '3.12 test host', truncated: false });
    window.__COPPERBENCH_PYTHON_HOST__ = { async invoke(payload) {
      const operation = payload.operation;
      if (operation === 'get_context') return context();
      if (operation === 'sync_context') {
        if (activeElementId !== payload.elementId) { activeElementId = payload.elementId as string | null; selectionVersion++; }
        view = payload.view as string;
        return context();
      }
      if (operation === 'status') return snapshot(payload.afterSequence as number);
      if (operation === 'stop') { state = 'stopped'; lastResult = 'cancelled'; log('解释器已停止，变量已清空', 'system'); return snapshot(); }
      if (operation === 'save_script') return { cancelled: false, path: 'D:/scripts/saved.py' };
      if (operation === 'open_script') return { cancelled: true };
      if (operation === 'select_python') return { cancelled: false, path: 'D:/Python/python.exe' };
      requestId = crypto.randomUUID();
      state = 'running';
      errorLine = null;
      completions = [];
      if (operation === 'complete') completions = ['cb.context'];
      else {
        lastResult = 'succeeded';
        log(payload.source as string, 'input');
        if ((payload.source as string).includes('while True')) { lastResult = 'running'; return snapshot(); }
        if ((payload.source as string).includes('raise ValueError')) { lastResult = 'failed'; errorLine = 2; log('ValueError: probe', 'stderr'); }
        else log('Python UI probe passed');
      }
      window.setTimeout(() => { state = 'ready'; }, 30);
      return snapshot();
    } };
  });
  await page.goto('/');
  await page.getByTestId('nav-python').click();
  const source = page.getByTestId('python-source');
  await source.fill("print('UI probe')");
  await page.getByTestId('python-run').click();
  await expect(page.getByTestId('python-output')).toContainText('Python UI probe passed');
  await page.getByTestId('nav-elements').click();
  await page.getByTestId('nav-python').click();
  await expect(source).toHaveValue("print('UI probe')");
  await expect(page.getByTestId('python-output')).toContainText('Python UI probe passed');
  const consoleInput = page.getByTestId('python-console-input');
  await expect(page.getByTestId('python-run')).toBeEnabled();
  await expect(page.getByTestId('python-stop')).toBeEnabled(); // Idle callbacks must also be stoppable.
  await consoleInput.fill('cb.con');
  await consoleInput.press('Control+Space');
  await page.getByRole('button', { name: 'cb.context', exact: true }).click();
  await expect(consoleInput).toHaveValue('cb.context');
  await consoleInput.press('Enter');
  await expect(consoleInput).toHaveValue('');
  await consoleInput.press('ArrowUp');
  await expect(consoleInput).toHaveValue('cb.context');
  await expect(page.getByTestId('python-run')).toBeEnabled();
  await source.fill("x = 1\nraise ValueError('probe')");
  await page.getByTestId('python-run').click();
  await page.getByRole('button', { name: '定位第 2 行异常' }).click();
  await expect(page.getByTestId('python-workbench').getByRole('status')).toContainText('脚本失败');
  await expect(source).toBeFocused();
  expect(await source.evaluate(element => (element as HTMLTextAreaElement).selectionStart)).toBe(6);
  await page.getByRole('button', { name: '另存脚本' }).click();
  await expect(page.getByTestId('python-workbench')).toContainText('saved.py');
  await expect(page.getByTestId('python-run')).toBeEnabled();
  await source.fill('while True:\n    pass');
  await page.getByTestId('python-run').click();
  await expect(page.getByTestId('python-stop')).toBeEnabled();
  await page.getByTestId('python-stop').click();
  await expect(page.getByTestId('python-output')).toContainText('解释器已停止');
  await expect(page.getByTestId('python-run')).toBeEnabled();
  await page.screenshot({ path: `../.tmp/python-workbench-${test.info().project.name}.png` });
});
