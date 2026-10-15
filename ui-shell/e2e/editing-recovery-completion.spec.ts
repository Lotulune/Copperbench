import { test, expect, type Page } from '@playwright/test';

async function installWrites(page: Page, mode: 'lost' | 'late' | 'rejected' = 'lost') {
  await page.addInitScript(({ mode }) => {
    const modulePath = '/src/mock/mockBridge.ts';
    const ready = import(modulePath);
    const cores = new Map<string, any>();
    const firstWorkspace = '11111111-1111-4111-8111-111111111111';
    const fixture = { mode, writes: [] as any[], receipts: [] as any[], readFailure: false, lost: false,
      release: () => {}, switchWorkspace: async (_id: string) => {},
      pauseRead: false, readPaused: false, readOnlyCode: false, readOnlyAll: false, releaseRead: () => {}, advanceRevision: async () => {} };
    async function coreFor(id: string) {
      if (!cores.has(id)) {
        const { MockCoreBridge } = await ready;
        const core = new MockCoreBridge();
        // Mock scenarios share templates. Detach this Core's entire projection
        // before changing identity so another Core cannot be renamed by setup.
        Object.assign(core.getState(), structuredClone(core.getState()));
        core.getState().workbench.workspace.id = id;
        cores.set(id, core);
      }
      return cores.get(id);
    }
    Object.assign(window, { recoveryFixture: fixture });
    window.copperbenchHost = {
      workspaceId: firstWorkspace,
      onEvent() { return () => {}; },
      async invoke(raw) {
        const request = JSON.parse(raw);
        const id = request.workspaceId ?? window.copperbenchHost!.workspaceId;
        const core = await coreFor(id);
        const isWrite = ['update_mod_element', 'update_procedure'].includes(request.operation);
        if (isWrite) {
          fixture.writes.push(structuredClone(request));
          if (fixture.mode === 'late' && fixture.writes.length === 1)
            await new Promise<void>(resolve => { fixture.release = resolve; });
          if (fixture.mode === 'rejected') return JSON.stringify({
            messageType: 'command_result', schemaVersion: '1.0', requestId: request.requestId,
            workspaceId: id, operation: request.operation, status: 'rejected',
            newRevision: core.getState().workbench.workspace.revision, task: null, data: null,
            recoveryPointId: null, conflict: null, denial: null,
            diagnostics: [{ code: 'FIXTURE_FIELD_REJECTED', severity: 'error',
              message: { key: 'fixture.rejected', fallback: 'Correct this field.', args: { original: 'reason 中文' } },
              path: '/code', elementId: request.payload.elementId, actions: [], recoverable: true }]
          });
        }
        const result = request.messageType === 'handshake' ? await core.negotiateHandshake(request)
          : request.messageType === 'command' ? await core.sendCommand(request) : await core.sendQuery(request);
        if (request.workspaceId) result.workspaceId = request.workspaceId;
        if (isWrite) {
          fixture.receipts.push(structuredClone(result));
          if (fixture.mode === 'lost' && !fixture.lost) {
            fixture.lost = true;
            if (result.status !== 'committed') throw new Error('Fixture did not commit the original write.');
            throw new Error('Transport disconnected after the Core commit.');
          }
        }
        if (fixture.readFailure && ['get_mod_element_editor', 'get_procedure_editor'].includes(request.operation)) {
          result.status = 'rejected'; result.data = null;
          result.diagnostics = [{ code: 'CONFIRMATION_READ_FAILED', severity: 'error',
            message: { key: 'fixture.read_failed', fallback: 'The editor could not be read.' },
            path: '/fixture/read', elementId: request.payload.elementId, actions: [], recoverable: true }];
        }
        if (request.operation === 'get_mod_element_editor' && result.data) {
          // Native Core projects the editor and its element summary from the
          // same workspace state. Mock field writes leave its cached summary old.
          const element = core.getState().elements.find((item: any) => item.id === request.payload.elementId);
          if (element) result.data = { ...result.data, element: structuredClone(element) };
        }
        if (fixture.readOnlyCode && request.operation === 'get_mod_element_editor' && result.data) {
          for (const field of result.data.sections.flatMap((section: any) => section.fields))
            if (field.path === '/code' || field.path === '/fields/code') field.readOnly = true;
        }
        if (fixture.readOnlyAll && request.operation === 'get_mod_element_editor' && result.data) {
          for (const field of result.data.sections.flatMap((section: any) => section.fields)) field.readOnly = true;
          result.data.configuration = { status: 'drift', sourceFingerprint: 'fixture-definition-change',
            differences: [{ path: '/displayName', declared: 'Declared name', effective: result.data.element.displayName }],
            planOperations: {} };
        }
        if (fixture.pauseRead && request.operation === 'get_mod_element_editor') {
          const captured = structuredClone(result);
          fixture.readPaused = true;
          await new Promise<void>(resolve => { fixture.releaseRead = resolve; });
          return JSON.stringify(captured);
        }
        return JSON.stringify(result);
      }
    };
    fixture.advanceRevision = async () => {
      const id = window.copperbenchHost!.workspaceId, core = await coreFor(id);
      core.getState().workbench.workspace.revision++;
      const bridgePath = '/src/bridge/index.ts';
      const { coreBridge } = await import(bridgePath);
      await coreBridge.sendQuery({ messageType: 'query', schemaVersion: '1.0', requestId: crypto.randomUUID(),
        workspaceId: id, operation: 'get_workbench', payload: {} });
    };
    fixture.switchWorkspace = async (id: string) => {
      if (!cores.has(id)) {
        const original = await coreFor(window.copperbenchHost!.workspaceId);
        const next = await coreFor(id);
        Object.assign(next.getState(), structuredClone(original.getState()));
        next.getState().workbench.workspace.id = id;
      }
      (window.copperbenchHost as any).workspaceId = id;
      const bridgePath = '/src/bridge/index.ts';
      const { coreBridge } = await import(bridgePath);
      const result = await coreBridge.sendQuery({ messageType: 'query', schemaVersion: '1.0', requestId: crypto.randomUUID(),
        workspaceId: id, operation: 'get_workbench', payload: {} });
      if (result.status !== 'succeeded' || (result.data as any)?.workspace.id !== id
        || coreBridge.getState().workbench?.workspace.id !== id)
        throw new Error('The fixture did not switch to the requested workspace identity.');
    };
  }, { mode });
}
async function create(page: Page, type: string, name: string) {
  await page.getByTestId('nav-elements').click();
  await page.getByTestId('create-element-btn').click();
  await page.getByTestId('create-element-type-' + type).click();
  await page.getByTestId('create-element-name-input').fill(name);
  await page.getByTestId('create-element-submit-btn').click();
}
async function returnToEditor(page: Page) {
  await page.getByTestId('nav-assets').click();
  await page.getByTestId('nav-elements').click();
}
async function english(page: Page) {
  await page.evaluate(async () => { const path = '/src/i18n/locale.ts'; (await import(path)).setUiLocale('en'); });
}
async function writes(page: Page) {
  return page.evaluate(() => (window as any).recoveryFixture.writes);
}

async function installDraftCountHost(page: Page) {
  await page.addInitScript(() => {
    (window as any).recoveryDraftCounts = [];
    window.__COPPERBENCH_WINDOW_HOST__ = { systemFrame: true, unsavedChangesSchemaVersion: '1.0',
      async reportUnsavedChanges(count) { (window as any).recoveryDraftCounts.push(count); }, async invoke() {} };
  });
}

async function openOrdinaryEditor(page: Page) {
  await page.getByTestId('nav-elements').click();
  await page.locator('.element-card').filter({ hasText: 'Copper Lamp' }).click();
  await expect(page.getByTestId('field-displayName')).toBeVisible();
}

async function draftCount(page: Page) {
  return page.evaluate(() => (window as any).recoveryDraftCounts.at(-1) ?? 0);
}

test('ordinary editor keeps an unknown save after reverting to its old baseline until an explicit confirmation and save', async ({ page }) => {
  await installWrites(page);
  await installDraftCountHost(page);
  await page.goto('/');
  await openOrdinaryEditor(page);
  const input = page.getByTestId('field-displayName');
  const original = await input.inputValue();
  await input.fill('Submitted A');
  await page.getByTestId('inspector-save-btn').click();
  await expect(page.getByTestId('inspector-confirm-save-result')).toBeVisible();
  await input.fill(original);
  await expect.poll(() => draftCount(page)).toBeGreaterThan(0);
  await returnToEditor(page);
  await expect(input).toHaveValue(original);
  await expect(page.getByTestId('inspector-confirm-save-result')).toBeVisible();
  await expect(page.getByTestId('inspector-save-btn')).toBeDisabled();
  await english(page);
  expect((await writes(page)).length).toBe(1);
  await page.getByTestId('inspector-confirm-save-result').click();
  await expect(page.getByTestId('inspector-confirm-save-result')).toHaveCount(0);
  await expect(input).toHaveValue(original);
  await expect(page.getByTestId('inspector-save-btn')).toBeEnabled();
  expect((await writes(page)).length).toBe(1);
  await page.getByTestId('inspector-save-btn').click();
  await expect(page.getByTestId('inspector-save-btn')).toBeDisabled();
  await expect.poll(() => draftCount(page)).toBe(0);
  await returnToEditor(page);
  await expect(input).toHaveValue(original);
  expect((await writes(page)).length).toBe(2);
});

for (const field of [
  { name: 'text', input: 'field-displayName', submitted: 'Submitted A', retained: 'Retained B' },
  { name: 'invalid numeric', input: 'field-hardness', submitted: '7', retained: '-' }
]) {
  test('ordinary editor preserves its ' + field.name + ' draft through a read-only configuration drift until explicit discard', async ({ page }) => {
    await installWrites(page);
    await installDraftCountHost(page);
    await page.goto('/');
    await openOrdinaryEditor(page);
    const input = page.getByTestId(field.input);
    await input.fill(field.submitted);
    await page.getByTestId('inspector-save-btn').click();
    await expect(page.getByTestId('inspector-confirm-save-result')).toBeVisible();
    await input.fill(field.retained);
    await page.evaluate(() => { (window as any).recoveryFixture.readOnlyAll = true; });
    await page.getByTestId('inspector-confirm-save-result').click();
    await expect(page.getByTestId('inspector-confirm-save-result')).toHaveCount(0);
    await expect(input).toHaveValue(field.retained);
    await expect(input).toBeDisabled();
    await expect(page.getByTestId('inspector-save-btn')).toBeDisabled();
    await expect.poll(() => draftCount(page)).toBeGreaterThan(0);
    await expect(page.getByTestId('inspector-external-change')).toContainText('草稿包含当前不可提交的字段');
    await returnToEditor(page);
    await expect(input).toHaveValue(field.retained);
    await expect(input).toBeDisabled();
    await english(page);
    await expect(page.getByTestId('inspector-external-change')).toContainText('cannot currently be submitted');
    expect((await writes(page)).length).toBe(1);
    await page.getByTestId('inspector-reload-latest').click();
    await expect(input).toHaveValue(field.submitted);
    await expect.poll(() => draftCount(page)).toBe(0);
    await expect(page.getByTestId('inspector-save-btn')).toBeDisabled();
    expect((await writes(page)).length).toBe(1);
  });
}

test('ordinary editor rejects a confirmation that crosses a revision and only saves against a later stable confirmation', async ({ page }) => {
  await installWrites(page);
  await installDraftCountHost(page);
  await page.goto('/');
  await openOrdinaryEditor(page);
  const input = page.getByTestId('field-displayName');
  await input.fill('Submitted A');
  await page.getByTestId('inspector-save-btn').click();
  await expect(page.getByTestId('inspector-confirm-save-result')).toBeVisible();
  await input.fill('Retained B');
  await page.evaluate(() => { (window as any).recoveryFixture.pauseRead = true; });
  await page.getByTestId('inspector-confirm-save-result').click();
  await expect.poll(() => page.evaluate(() => (window as any).recoveryFixture.readPaused)).toBe(true);
  await page.evaluate(async () => { await (window as any).recoveryFixture.advanceRevision(); });
  await page.evaluate(() => (window as any).recoveryFixture.releaseRead());
  await expect(page.getByTestId('validation-alert')).toContainText('无法读取最新版本');
  await expect(page.getByTestId('inspector-confirm-save-result')).toBeVisible();
  await expect(page.getByTestId('inspector-save-btn')).toBeDisabled();
  await expect(input).toHaveValue('Retained B');
  await expect.poll(() => draftCount(page)).toBeGreaterThan(0);
  expect((await writes(page)).length).toBe(1);
  await page.evaluate(() => { (window as any).recoveryFixture.pauseRead = false; });
  await page.getByTestId('inspector-confirm-save-result').click();
  await expect(page.getByTestId('inspector-confirm-save-result')).toHaveCount(0);
  await expect(input).toHaveValue('Retained B');
  await page.getByTestId('inspector-save-btn').click();
  await expect(page.getByTestId('inspector-save-btn')).toBeDisabled();
  await expect.poll(() => draftCount(page)).toBe(0);
  const actual = await page.evaluate(() => ({ writes: (window as any).recoveryFixture.writes,
    receipts: (window as any).recoveryFixture.receipts }));
  expect(actual.writes).toHaveLength(2);
  expect(actual.writes[1].expectedRevision).toBe(actual.receipts[0].newRevision + 1);
  expect(actual.writes[1].payload.changes).toEqual([{ path: actual.writes[0].payload.changes[0].path, value: 'Retained B' }]);
});

const editors = [
  { type: 'function', editor: 'function', input: 'function-code-editor', save: 'function-save-btn', a: 'say captured A', b: 'say newer B', dismiss: true },
  { type: 'loottable', editor: 'loot', input: 'pool-name-input', save: 'loottable-save-btn', a: 'captured_a', b: 'newer_b', dismiss: true },
  { type: 'achievement', editor: 'advancement', input: 'advancement-desc-input', save: 'advancement-save-btn', a: 'Captured A', b: 'Newer B', dismiss: true },
  { type: 'gui', editor: 'gui', input: 'gui-width', save: 'gui-save-btn', a: '190', b: '210', dismiss: false },
  { type: 'overlay', editor: 'overlay', input: 'overlay-grid-sx', save: 'overlay-save-btn', a: '20', b: '30', dismiss: false }
] as const;

for (const config of editors) {
  test(config.editor + ': a committed but lost save remains recoverable after feedback, input and navigation', async ({ page }) => {
    await installWrites(page);
    await page.goto('/');
    await create(page, config.type, 'unknown_' + config.editor);
    const input = page.getByTestId(config.input);
    await input.fill(config.a);
    await page.getByTestId(config.save).click();
    await expect(page.getByTestId(config.editor + '-save-unknown')).toBeVisible();
    if (config.dismiss) {
      await page.getByRole('button', { name: '关闭提示', exact: true }).click();
      await expect(page.getByRole('button', { name: '关闭提示', exact: true })).toHaveCount(0);
    }
    await input.fill(config.b);
    await returnToEditor(page);
    await expect(input).toHaveValue(config.b);
    await english(page);
    await expect(page.getByTestId(config.editor + '-save-unknown')).toContainText('save result is unknown');
    const read = page.getByTestId(config.editor + '-read-latest');
    await read.focus();
    await expect(read).toBeFocused();
    await page.evaluate(() => { (window as any).recoveryFixture.readFailure = true; });
    await page.keyboard.press('Enter');
    await expect(page.getByText('Could not read the latest version. Your draft and original diagnostics are preserved.', { exact: true })).toBeVisible();
    await expect(read).toBeEnabled();
    await expect(page.getByTestId(config.editor + '-save-unknown')).toBeVisible();
    await expect(input).toHaveValue(config.b);
    expect((await writes(page)).length).toBe(1);
    await page.evaluate(() => { (window as any).recoveryFixture.readFailure = false; });
    await read.click();
    await expect(page.getByTestId(config.editor + '-save-unknown')).toHaveCount(0);
    await expect(input).toHaveValue(config.b);
    await expect(page.getByTestId(config.save)).toBeEnabled();
    expect((await writes(page)).length).toBe(1);
  });
}

for (const config of editors.filter(config => config.editor !== 'function' && config.editor !== 'advancement')) {
  test(config.editor + ': a late successful A receipt preserves editable B in the actual component', async ({ page }) => {
    await installWrites(page, 'late');
    await page.goto('/');
    await create(page, config.type, 'late_' + config.editor);
    const input = page.getByTestId(config.input);
    await input.fill(config.a);
    await page.getByTestId(config.save).click();
    await expect.poll(async () => (await writes(page)).length).toBe(1);
    await expect(input).toBeEditable();
    await input.fill(config.b);
    await page.evaluate(() => (window as any).recoveryFixture.release());
    await expect(page.getByTestId(config.save)).toBeEnabled();
    await expect(input).toHaveValue(config.b);
    await returnToEditor(page);
    await expect(input).toHaveValue(config.b);
    expect((await writes(page)).length).toBe(1);
    await page.getByTestId(config.save).click();
    await expect(page.getByTestId(config.save)).toBeDisabled();
    const requests = await writes(page);
    const receipts = await page.evaluate(() => (window as any).recoveryFixture.receipts);
    expect(requests).toHaveLength(2);
    expect(requests[1].expectedRevision).toBe(receipts[0].newRevision);
    expect(requests[1].workspaceId).toBe(requests[0].workspaceId);
    expect(requests[1].payload.elementId).toBe(requests[0].payload.elementId);
  });
}

test('Advancement blocks input until its delayed save receipt, then preserves and commits newer B', async ({ page }) => {
  await installWrites(page, 'late');
  await page.goto('/');
  await create(page, 'achievement', 'late_advancement');
  const input = page.getByTestId('advancement-desc-input');
  const save = page.getByTestId('advancement-save-btn');
  await input.fill('Captured A');
  await save.click();
  await expect.poll(async () => (await writes(page)).length).toBe(1);
  await expect(input).toBeDisabled();
  await expect(input).toHaveValue('Captured A');
  await expect(save).toBeDisabled();
  await page.evaluate(() => (window as any).recoveryFixture.release());
  await expect(input).toBeEditable();
  await expect(save).toBeDisabled();
  await input.fill('Newer B');
  await expect(save).toBeEnabled();
  await returnToEditor(page);
  await expect(input).toHaveValue('Newer B');
  expect((await writes(page)).length).toBe(1);
  await save.click();
  await expect(save).toBeDisabled();
  const requests = await writes(page);
  const receipts = await page.evaluate(() => (window as any).recoveryFixture.receipts);
  expect(requests).toHaveLength(2);
  const path = requests[0].payload.changes.find((change: any) => change.value === 'Captured A')?.path;
  expect(path).toBeTruthy();
  expect(requests[1].payload.changes).toEqual([{ path, value: 'Newer B' }]);
  expect(requests[1].expectedRevision).toBe(receipts[0].newRevision);
  expect(requests[1].workspaceId).toBe(requests[0].workspaceId);
  expect(requests[1].payload.elementId).toBe(requests[0].payload.elementId);
});

async function addNumber(page: Page) {
  await page.getByLabel('筛选过程节点分类').selectOption('value');
  await page.getByLabel('搜索过程节点').fill('数值');
  await page.getByRole('button', { name: /^数值 值/ }).click();
}
async function numberCount(page: Page, count: number) {
  await page.getByLabel('搜索当前过程图').fill('数值');
  await expect(page.getByTestId('procedure-graph-navigation')).toContainText(count + ' 个匹配');
}
for (const mode of ['lost', 'late'] as const) {
  test('Procedure: captured A and newer Blockly B survive a ' + mode + ' receipt and confirmation', async ({ page }) => {
    await installWrites(page, mode);
    await page.goto('/');
    await create(page, 'procedure', 'procedure_' + mode);
    await addNumber(page);
    await expect(page.locator('.procedure-save')).toBeEnabled();
    await page.locator('.procedure-save').click();
    await expect.poll(async () => (await writes(page)).length).toBe(1);
    if (mode === 'lost') await expect(page.getByTestId('procedure-save-unknown')).toBeVisible();
    await addNumber(page);
    await numberCount(page, 2);
    if (mode === 'late') await page.evaluate(() => (window as any).recoveryFixture.release());
    await returnToEditor(page);
    await numberCount(page, 2);
    if (mode === 'lost') {
      await english(page);
      await expect(page.getByTestId('procedure-save-unknown')).toContainText('save result is unknown');
      await page.getByTestId('procedure-read-latest').focus();
      await page.keyboard.press('Enter');
      await expect(page.getByTestId('procedure-save-unknown')).toHaveCount(0);
    }
    await expect(page.locator('.procedure-save')).toBeEnabled();
    expect((await writes(page)).length).toBe(1);
    await page.locator('.procedure-save').click();
    await expect(page.locator('.procedure-save')).toBeDisabled();
    await returnToEditor(page);
    // Search uses the current display language after confirmation.
    await page.getByLabel(mode === 'lost' ? 'Search this procedure graph' : '搜索当前过程图').fill(mode === 'lost' ? 'Number' : '数值');
    await expect(page.getByTestId('procedure-graph-navigation')).toContainText('2');
    expect((await writes(page)).length).toBe(2);
  });
}

test('same GUI identity in another workspace does not receive an outstanding save or draft', async ({ page }) => {
  await installWrites(page, 'late');
  await page.goto('/');
  await create(page, 'gui', 'workspace_gui');
  await page.getByTestId('gui-width').fill('190');
  await page.getByTestId('gui-save-btn').click();
  await expect.poll(async () => (await writes(page)).length).toBe(1);
  await page.getByTestId('nav-assets').click();
  await page.evaluate(() => (window as any).recoveryFixture.switchWorkspace('22222222-2222-4222-8222-222222222222'));
  await page.getByTestId('nav-elements').click();
  await expect(page.getByTestId('gui-width')).not.toHaveValue('190');
  await page.getByTestId('gui-width').fill('230');
  await page.evaluate(() => (window as any).recoveryFixture.release());
  await expect.poll(() => page.evaluate(() => (window as any).recoveryFixture.receipts.length)).toBe(1);
  await expect(page.getByTestId('gui-width')).toHaveValue('230');
  await expect(page.getByTestId('gui-save-btn')).toBeEnabled();
  await page.getByTestId('nav-assets').click();
  await page.evaluate(() => (window as any).recoveryFixture.switchWorkspace('11111111-1111-4111-8111-111111111111'));
  await page.getByTestId('nav-elements').click();
  await expect(page.getByTestId('gui-width')).toHaveValue('190');
  await expect(page.getByTestId('gui-save-btn')).toBeDisabled();
  expect((await writes(page)).length).toBe(1);
});

test('an explicit field rejection keeps its original diagnostic after dismissing feedback without becoming unknown', async ({ page }) => {
  await installWrites(page, 'rejected');
  await page.goto('/');
  await create(page, 'function', 'rejected_function');
  await page.getByTestId('function-code-editor').fill('say retained');
  await page.getByTestId('function-save-btn').click();
  await expect(page.getByTestId('function-recovery')).toContainText('Correct this field.');
  await page.getByRole('button', { name: '关闭提示', exact: true }).click();
  await returnToEditor(page);
  await english(page);
  await expect(page.getByTestId('function-save-unknown')).toHaveCount(0);
  const recovery = page.getByTestId('function-recovery');
  await recovery.locator('summary').click();
  await expect(recovery.locator('pre')).toContainText('FIXTURE_FIELD_REJECTED');
  await expect(recovery.locator('pre')).toContainText('reason 中文');
  await expect(page.getByTestId('function-code-editor')).toHaveValue('say retained');
  await expect(page.getByTestId('function-save-btn')).toBeEnabled();
  expect((await writes(page)).length).toBe(1);
});

for (const mapped of [true, false]) {
  test('field diagnosis ' + (mapped ? 'focuses its exact contract control and permits repair' : 'retains an unmapped original path without a wrong focus'), async ({ page }) => {
    await page.addInitScript(({ mapped }) => {
      const path = '/src/mock/mockBridge.ts';
      const ready = import(path).then(({ MockCoreBridge }) => new MockCoreBridge());
      let count = 0;
      (window as any).fieldRequests = [];
      window.copperbenchHost = {
        workspaceId: '11111111-1111-4111-8111-111111111111', onEvent() { return () => {}; },
        async invoke(raw) {
          const request = JSON.parse(raw), core = await ready;
          if (request.operation === 'update_mod_element') {
            (window as any).fieldRequests.push(request);
            if (++count === 1) {
              const target = mapped ? request.payload.changes.find((change: any) => change.path === '/fields/hardness').path : '/unknown/actual/path';
              return JSON.stringify({ messageType: 'command_result', schemaVersion: '1.0',
                requestId: request.requestId, workspaceId: request.workspaceId, operation: request.operation,
                status: 'rejected', newRevision: core.getState().workbench.workspace.revision,
                data: null, task: null, recoveryPointId: null, conflict: null, denial: null,
                diagnostics: [{ code: 'FIXTURE_FIELD_CONTRACT', severity: 'error', recoverable: true,
                  path: '/elements/' + request.payload.elementId + target, elementId: request.payload.elementId,
                  message: { key: 'fixture.field', fallback: 'Correct the specified field.' },
                  actions: [{ id: 'exact_field', kind: 'open_field', target,
                    label: { key: 'fixture.locate', fallback: 'Locate this field' } }] }] });
            }
          }
          const result = request.messageType === 'handshake' ? await core.negotiateHandshake(request)
            : request.messageType === 'command' ? await core.sendCommand(request) : await core.sendQuery(request);
          if (request.operation?.startsWith('preview_mod_element') && result.data) result.data.canApply = true;
          return JSON.stringify(result);
        }
      };
    }, { mapped });
    await page.goto('/');
    await page.getByTestId('nav-elements').click();
    await page.locator('.element-card').filter({ hasText: 'Copper Lamp' }).click();
    await page.getByTestId('field-displayName').fill('Other field retained');
    await page.getByTestId('field-hardness').fill('7');
    await page.getByTestId('inspector-save-btn').click();
    const alert = page.getByTestId('validation-alert');
    await expect(alert).toContainText('Correct the specified field.');
    if (mapped) {
      await page.getByTestId('diag-action-exact_field').focus();
      await page.keyboard.press('Enter');
      await expect(page.getByTestId('field-hardness')).toBeFocused();
    } else {
      await expect(alert).toContainText('/unknown/actual/path');
      await expect(alert.getByRole('button', { name: /定位字段/ })).toHaveCount(0);
    }
    await expect(page.getByTestId('field-displayName')).toHaveValue('Other field retained');
    await page.getByTestId('field-hardness').fill('5');
    await page.getByTestId('inspector-save-btn').click();
    await expect(page.getByTestId('inspector-save-btn')).toBeDisabled();
    const requests = await page.evaluate(() => (window as any).fieldRequests);
    expect(requests).toHaveLength(2);
    const firstPaths = requests[0].payload.changes.map((change: any) => change.path);
    expect(firstPaths).toEqual(['/fields/displayName', '/fields/hardness']);
    expect(requests[1].payload.changes).toEqual([
      { path: firstPaths[0], value: 'Other field retained' }, { path: firstPaths[1], value: 5 }
    ]);
  });
}

test('settings keep unknown status and newer input through a failed confirmation read', async ({ page }) => {
  await page.addInitScript(() => {
    const snapshot = { schemaVersion: '1.0' as const, revision: 'settings-1', entries: [
      { key: 'ide.fontSize', section: 'ide' as const, type: 'integer' as const, value: 13, min: 8, max: 96 }
    ] };
    const fixture = { writes: 0, failRead: false };
    Object.assign(window, { settingsRecovery: fixture });
    window.__COPPERBENCH_WINDOW_HOST__ = { systemFrame: false, preferencesAvailable: true, preferencesSchemaVersion: '1.0',
      getPreferences: async () => { if (fixture.failRead) throw new Error('Preference read unavailable.'); return structuredClone(snapshot); },
      savePreferences: async patch => { fixture.writes++; snapshot.revision = 'settings-2';
        snapshot.entries[0].value = Number(patch.changes['ide.fontSize']); throw new Error('Save response lost after commit.'); },
      invoke: async () => {} };
  });
  await page.goto('/');
  await page.getByTestId('nav-settings').click();
  await page.getByRole('button', { name: '代码编辑器', exact: true }).click();
  const input = page.locator('[id="preference-ide.fontSize"]');
  await input.fill('17');
  await page.getByRole('button', { name: '保存更改', exact: true }).click();
  await expect(page.getByTestId('settings-save-unknown')).toBeVisible();
  await input.fill('19');
  await page.getByTestId('nav-assets').click();
  await page.getByTestId('nav-settings').click();
  await english(page);
  await page.evaluate(() => { (window as any).settingsRecovery.failRead = true; });
  await page.getByTestId('settings-read-latest').focus();
  await page.keyboard.press('Enter');
  await expect(page.getByText('Preference read unavailable.', { exact: true })).toBeVisible();
  await expect(page.getByTestId('settings-read-latest')).toBeEnabled();
  await expect(page.getByTestId('settings-save-unknown')).toContainText('save result is unknown');
  await expect(page.getByTestId('settings-view')).not.toContainText('Your changes have not been saved.');
  await expect(input).toHaveValue('19');
  await expect(page.getByRole('button', { name: 'Save changes', exact: true })).toBeDisabled();
  await page.evaluate(() => { (window as any).settingsRecovery.failRead = false; });
  await page.getByTestId('settings-read-latest').click();
  await expect(page.getByTestId('settings-save-unknown')).toHaveCount(0);
  await expect(input).toHaveValue('19');
  await expect(page.getByRole('button', { name: 'Save changes', exact: true })).toBeEnabled();
  expect(await page.evaluate(() => (window as any).settingsRecovery.writes)).toBe(1);
});


test('a revision change during confirmation cannot accept an older GUI baseline or erase the unknown outcome', async ({ page }) => {
  await installWrites(page);
  await page.goto('/');
  await create(page, 'gui', 'revision_confirmation_gui');
  await page.getByTestId('gui-width').fill('190');
  await page.getByTestId('gui-save-btn').click();
  await expect(page.getByTestId('gui-save-unknown')).toBeVisible();
  await page.getByTestId('gui-width').fill('210');
  await page.evaluate(() => { (window as any).recoveryFixture.pauseRead = true; });
  await page.getByTestId('gui-read-latest').click();
  await expect(page.getByTestId('gui-read-latest')).toBeDisabled();
  await expect.poll(() => page.evaluate(() => (window as any).recoveryFixture.readPaused)).toBe(true);
  await page.evaluate(async () => { await (window as any).recoveryFixture.advanceRevision(); });
  await page.evaluate(() => (window as any).recoveryFixture.releaseRead());
  await expect(page.getByText('无法读取最新版本，草稿和原诊断已保留。', { exact: true })).toBeVisible();
  await expect(page.getByTestId('gui-save-unknown')).toBeVisible();
  await expect(page.getByTestId('gui-width')).toHaveValue('210');
  await expect(page.getByTestId('gui-save-btn')).toBeDisabled();
  expect((await writes(page)).length).toBe(1);
});


test('a Function draft remains protected when confirmation makes its existing code field read only', async ({ page }) => {
  await installWrites(page);
  await page.goto('/');
  await create(page, 'function', 'readonly_confirmation_function');
  await page.getByTestId('function-code-editor').fill('say submitted A');
  await page.getByTestId('function-save-btn').click();
  await expect(page.getByTestId('function-save-unknown')).toBeVisible();
  await page.getByTestId('function-code-editor').fill('say retained B');
  await page.evaluate(() => { (window as any).recoveryFixture.readOnlyCode = true; });
  await page.getByTestId('function-read-latest').click();
  await expect(page.getByTestId('function-save-unknown')).toHaveCount(0);
  await expect(page.getByTestId('function-code-editor')).toHaveValue('say retained B');
  await expect(page.getByTestId('function-code-editor')).toHaveAttribute('readonly', '');
  await expect(page.getByTestId('function-save-btn')).toBeDisabled();
  await expect(page.getByTestId('function-dirty-badge')).toBeVisible();
  await expect(page.getByTestId('function-read-latest')).toBeEnabled();
  const guarded = await page.evaluate(() => {
    const event = new Event('beforeunload', { cancelable: true });
    window.dispatchEvent(event); return event.defaultPrevented;
  });
  expect(guarded).toBe(true);
  expect((await writes(page)).length).toBe(1);
});
