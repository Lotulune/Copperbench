import { expect, test, type Page } from '@playwright/test';

/** Controlled native transport fixture; these tests do not write a real workspace or launch Minecraft. */
async function installFirstCreationFixture(page: Page, options: { missingElement?: boolean; delayedRead?: boolean; rejectRead?: boolean;
  withoutBinding?: boolean; existingElement?: boolean; buildState?: 'running' | 'succeeded' | 'failed'; withoutInputIdentity?: boolean; delayCancellation?: boolean; generatorId?: string } = {}) {
  await page.addInitScript((options) => {
    const fixtureWindow = window as unknown as Record<string, any>;
    const workspaceId = '11111111-1111-4111-8111-111111111112';
    const workspaceFile = 'D:/MockWorkspace/first_creation.mcreator';
    const buildId = '66666666-6666-4666-8666-666666666666';
    const inputSha = 'a'.repeat(64);
    fixtureWindow.__CURRENT_INPUT_SHA__ = inputSha;
    fixtureWindow.__INPUT_OBSERVATIONS__ = 0;
    const listeners = new Set<(raw: string) => void>();
    let eventSequence = 900;
    const publish = (bridge: any, task: any) => listeners.forEach(listener => listener(JSON.stringify({
      messageType: 'event', schemaVersion: '1.0', eventId: crypto.randomUUID(), workspaceId,
      revision: bridge.getState().workbench.workspace.revision, sequence: ++eventSequence, occurredAt: new Date().toISOString(),
      event: 'task_completed', causedByRequestId: null, payload: { task }
    })));
    const buildTask = (state: string) => ({ id: buildId, kind: 'build', state, cancellable: state === 'running', progress: state === 'running' ? 0.5 : 1,
      stage: { key: state === 'failed' ? 'task.failed' : state === 'running' ? 'task.build.building' : 'task.build.completed', fallback: `Fixture build ${state}` },
      startedAt: '2026-10-15T00:00:00Z', ...(state === 'running' ? {} : { completedAt: '2026-10-15T00:01:00Z' }),
      diagnostics: { error: state === 'failed' ? 1 : 0, warning: 0, info: 0 },
      ...(!options.withoutInputIdentity ? { sourceSnapshot: { sha256: inputSha, fileCount: 1, bytes: 10,
        manifestPath: `D:/MockWorkspace/.copperbench/task-inputs/${buildId}.json` } } : {}) });
    let fixturePromise: Promise<any> | undefined;
    const fixture = () => fixturePromise ??= import('/src/mock/mockBridge.ts').then(async ({ MockCoreBridge }) => {
      const bridge = new MockCoreBridge(); bridge.loadScenario('empty-workspace');
      if (options.generatorId) bridge.getState().workbench!.workspace.generator.id = options.generatorId;
      bridge.onEvent((event: unknown) => listeners.forEach(listener => listener(JSON.stringify(event))));
      if (options.existingElement) {
        const result = await bridge.sendCommand({ messageType: 'command', schemaVersion: '1.0', requestId: crypto.randomUUID(), workspaceId,
          operation: 'create_mod_element', expectedRevision: bridge.getState().workbench.workspace.revision,
          payload: { elementType: 'block', name: 'trail_block', initialValues: {}, clientMutationId: crypto.randomUUID() } });
        if (result.status !== 'committed' || result.data?.element?.type !== 'block') throw new Error('The existing-element fixture did not create a block summary.');
        fixtureWindow.__EXISTING_ELEMENT_ID__ = result.data?.element?.id ?? result.data?.elementId;
      }
      if (options.buildState) {
        bridge.getState().tasks[buildId] = buildTask(options.buildState);
        bridge.getState().taskLogs[buildId] = [{ sequence: 1, timestamp: '2026-10-15T00:00:30Z',
          level: options.buildState === 'failed' ? 'error' : 'info',
          text: options.buildState === 'failed' ? 'Original fixture failed build log' : 'Original fixture build log' }];
      }
      return bridge;
    });
    fixtureWindow.__FIRST_CREATION_COMMANDS__ = [];
    fixtureWindow.__FIRST_CREATION_READS__ = 0;
    fixtureWindow.__FIRST_CREATION_SAVES__ = [];
    fixtureWindow.__COPPERBENCH_WORKSPACE_OPEN_HOST__ = { available: true, buildFolderAvailable: true, verifiedExportFolderAvailable: true,
      open: async (file: string) => { fixtureWindow.__OPENED_WORKSPACE__ = file; }, openBuildFolder: async () => {},
      openVerifiedExportFolder: async (taskId: string) => { fixtureWindow.__OPENED_VERIFIED_TASK__ = taskId; } };
    let releaseRead!: () => void;
    const readGate = options.delayedRead ? new Promise<void>(resolve => { releaseRead = resolve; }) : Promise.resolve();
    fixtureWindow.__RELEASE_FIRST_CREATION_READ__ = () => releaseRead?.();
    const seed = { workspaceFile, modId: 'first_creation', showOnOpen: true, clients: [],
      ...(options.buildState ? { build: { id: buildId, inputRevision: 0 } } : {}),
      ...(options.missingElement ? { elementId: '99999999-9999-4999-8999-999999999999' } : {}) };
    let saved: typeof seed | null = options.withoutBinding ? null : JSON.parse(sessionStorage.getItem('fixture-first-creation') ?? JSON.stringify(seed));
    fixtureWindow.__COPPERBENCH_FIRST_CREATION_HOST__ = { workspaceFile, workspaceId,
      read: async () => { fixtureWindow.__FIRST_CREATION_READS__++; await readGate;
        if (options.rejectRead) throw new Error('Damaged stored reference fixture');
        if (saved?.build) saved.build.inputRevision = (await fixture()).getState().workbench.workspace.revision;
        return saved; },
      save: async (binding: typeof seed) => { saved = JSON.parse(JSON.stringify(binding)); fixtureWindow.__FIRST_CREATION_SAVES__.push(saved); sessionStorage.setItem('fixture-first-creation', JSON.stringify(saved)); },
      observeInputs: async (ids: string[]) => {
        fixtureWindow.__INPUT_OBSERVATIONS__++;
        const bridge = await fixture();
        return { workspaceId, observedAt: new Date().toISOString(), currentSha256: fixtureWindow.__CURRENT_INPUT_SHA__,
          tasks: Object.fromEntries(ids.map(id => { const task = bridge.getState().tasks[id], sha = task?.sourceSnapshot?.sha256;
            return [id, { status: !sha || task.state !== 'succeeded' ? 'unknown'
              : sha === fixtureWindow.__CURRENT_INPUT_SHA__ ? 'current' : 'changed', ...(sha ? { sourceSha256: sha } : {}) }]; })) };
      } };
    let resolveCancellation!: () => void;
    fixtureWindow.__RESOLVE_CANCEL_REQUEST__ = () => resolveCancellation?.();
    fixtureWindow.copperbenchHost = { workspaceId,
      onEvent: (listener: (raw: string) => void) => { listeners.add(listener); return () => listeners.delete(listener); },
      invoke: async (raw: string) => {
        const request = JSON.parse(raw), bridge = await fixture();
        if (request.messageType === 'handshake') return JSON.stringify(await bridge.negotiateHandshake(request));
        if (request.messageType === 'command') fixtureWindow.__FIRST_CREATION_COMMANDS__.push(request);
        if (options.delayCancellation && request.operation === 'cancel_task') {
          await new Promise<void>(resolve => { resolveCancellation = resolve; });
          return JSON.stringify({ messageType: 'command_result', schemaVersion: '1.0', requestId: request.requestId,
            workspaceId, operation: 'cancel_task', status: 'accepted', newRevision: bridge.getState().workbench.workspace.revision,
            recoveryPointId: null, task: bridge.getState().tasks[buildId], data: null, diagnostics: [], conflict: null, denial: null });
        }
        return JSON.stringify(await (request.messageType === 'command' ? bridge.sendCommand(request) : bridge.sendQuery(request)));
      } };
    fixtureWindow.__SET_BUILD_TASK_STATE__ = async (state: string) => {
      const bridge = await fixture(), task = buildTask(state); bridge.getState().tasks[buildId] = task; publish(bridge, task);
    };
    fixtureWindow.__FIXTURE_REVISION__ = async () => (await fixture()).getState().workbench.workspace.revision;
    fixtureWindow.__ADD_EXACT_EXPORT_RECEIPT__ = async (environment: Record<string, unknown> | null = { loader: 'fabric', minecraftVersion: '1.20.1', loaderVersion: '0.18' }) => {
      const bridge = await fixture();
      const id = '88888888-8888-4888-8888-888888888888';
      const task = { id, kind: 'export', state: 'succeeded', cancellable: false, progress: 1,
        stage: { key: 'task.export.completed', fallback: 'Verified artifact exported' },
        startedAt: '2026-10-15T00:00:00Z', completedAt: '2026-10-15T00:01:00Z', diagnostics: { error: 0, warning: 0, info: 0 },
        verifiedExport: { schemaVersion: '1.0', taskId: buildId, scope: 'Recorded acceptance cases only',
          artifactPath: 'verified-mod.jar', reportPath: 'gametest-results.xml', reportSha256: 'b'.repeat(64), environment: environment ?? {},
          exportDirectory: `D:/MockWorkspace/.copperbench/verified-exports/${id}`, artifactSha256: 'a'.repeat(64), status: 'passed_historical_input' } };
      bridge.getState().tasks[id] = task;
      publish(bridge, task);
      return id;
    };
  }, options);
}

test('workspace overview has no startup cards or permanent first-creation option', async ({ page }) => {
  await page.goto('/');
  for (const id of ['hub-open-workspace', 'hub-connect-agent', 'hub-first-creation', 'nav-first-creation', 'first-creation-overlay']) {
    await expect(page.getByTestId(id)).toHaveCount(0);
  }
  await page.getByTestId('compact-run-menu').click();
  await expect(page.getByTestId('compact-export-btn')).toBeDisabled();
});

test('ordinary native workspaces do not offer a separate first-creation project', async ({ page }) => {
  await installFirstCreationFixture(page, { withoutBinding: true });
  await page.goto('/');
  await expect(page.getByTestId('nav-hub')).toBeVisible();
  await expect(page.getByTestId('first-creation-overlay')).toHaveCount(0);
  await expect(page.getByTestId('first-creation-begin')).toHaveCount(0);
  await expect(page.getByTestId('workspace-entry-actions')).toHaveCount(0);
  expect(await page.evaluate(() => (window as any).__FIRST_CREATION_COMMANDS__.length)).toBe(0);
});

test('delayed native reference reads never save empty defaults over the existing file', async ({ page }) => {
  await installFirstCreationFixture(page, { delayedRead: true, missingElement: true });
  await page.goto('/');
  await expect.poll(() => page.evaluate(() => (window as any).__FIRST_CREATION_READS__)).toBeGreaterThan(0);
  expect(await page.evaluate(() => (window as any).__FIRST_CREATION_SAVES__.length)).toBe(0);
  await page.evaluate(() => (window as any).__RELEASE_FIRST_CREATION_READ__());
  await expect(page.getByTestId('first-creation-step-element')).toContainText('原元素已删除');
  await expect(page.getByTestId('first-creation-create-element')).toHaveCount(0);
  expect(await page.evaluate(() => (window as any).__FIRST_CREATION_COMMANDS__.length)).toBe(0);
  await expect.poll(() => page.evaluate(() => (window as any).__FIRST_CREATION_SAVES__.at(-1)?.elementId)).toBe('99999999-9999-4999-8999-999999999999');
});

test('skipping the one-time guide persists across reload without discarding references', async ({ page }) => {
  await installFirstCreationFixture(page, { missingElement: true, buildState: 'succeeded' });
  await page.goto('/');
  await expect(page.getByTestId('first-creation-overlay')).toBeVisible();
  await page.getByTestId('first-creation-close').click();
  await expect(page.getByTestId('first-creation-overlay')).toHaveCount(0);
  await expect(page.getByTestId('continue-first-creation')).toHaveCount(0);
  await expect.poll(() => page.evaluate(() => (window as any).__FIRST_CREATION_SAVES__.at(-1)?.showOnOpen)).toBe(false);
  const saved = await page.evaluate(() => (window as any).__FIRST_CREATION_SAVES__.at(-1));
  expect(saved.elementId).toBe('99999999-9999-4999-8999-999999999999');
  expect(saved.build.id).toBe('66666666-6666-4666-8666-666666666666');
  await page.reload();
  await expect(page.getByTestId('nav-hub')).toBeVisible();
  await expect.poll(() => page.evaluate(() => (window as any).__FIRST_CREATION_READS__)).toBeGreaterThan(0);
  await expect(page.getByTestId('first-creation-overlay')).toHaveCount(0);
  expect(await page.evaluate(() => (window as any).__FIRST_CREATION_COMMANDS__.length)).toBe(0);
});

test('failed native reads preserve data and never issue a preference save or product write', async ({ page }) => {
  await installFirstCreationFixture(page, { rejectRead: true });
  await page.goto('/');
  await expect.poll(() => page.evaluate(() => (window as any).__FIRST_CREATION_READS__)).toBeGreaterThan(0);
  await expect(page.getByTestId('first-creation-overlay')).toHaveCount(0);
  expect(await page.evaluate(() => (window as any).__FIRST_CREATION_SAVES__.length)).toBe(0);
  expect(await page.evaluate(() => (window as any).__FIRST_CREATION_COMMANDS__.length)).toBe(0);
});

test('created element references survive guide close, navigation and language changes without creating again', async ({ page }) => {
  await installFirstCreationFixture(page);
  await page.goto('/');
  await page.getByTestId('first-creation-create-element').click();
  await expect(page.getByTestId('first-creation-overlay')).toHaveCount(0);
  await expect.poll(() => page.evaluate(() => (window as any).__FIRST_CREATION_SAVES__.at(-1)?.elementId)).toBeTruthy();
  const originalId = await page.evaluate(() => (window as any).__FIRST_CREATION_SAVES__.at(-1)?.elementId);
  await page.getByTestId('ui-language-select').selectOption('en');
  await page.getByTestId('nav-hub').click();
  await page.getByTestId('continue-first-creation').click();
  await expect(page.getByTestId('first-creation-step-element')).toContainText('Block created');
  await expect(page.getByTestId('first-creation-create-element')).toHaveCount(0);
  await page.keyboard.press('Escape');
  await expect(page.getByTestId('continue-first-creation')).toHaveCount(0);
  const references = await page.evaluate(() => (window as any).__FIRST_CREATION_SAVES__.at(-1));
  expect(references.elementId).toBe(originalId);
  expect(await page.evaluate(() => (window as any).__FIRST_CREATION_COMMANDS__.filter((request: any) => request.operation === 'create_mod_element').length)).toBe(1);
});

test('exact export folders are opened by the recorded task UUID while historical scope stays visible', async ({ page }) => {
  await installFirstCreationFixture(page);
  await page.goto('/');
  await page.getByTestId('first-creation-skip').click();
  const taskId = await page.evaluate(() => (window as any).__ADD_EXACT_EXPORT_RECEIPT__());
  await page.getByTestId('compact-run-menu').click();
  await page.getByTestId('compact-export-btn').click();
  await expect(page.getByTestId('delivery-verified-export')).toContainText('历史已验收产物导出回执');
  await page.getByTestId('delivery-open-verified-folder').click();
  expect(await page.evaluate(() => (window as any).__OPENED_VERIFIED_TASK__)).toBe(taskId);
  expect(await page.evaluate(() => (window as any).__FIRST_CREATION_COMMANDS__.length)).toBe(0);
});

test('ordinary export submits the requested relative path and leaves gameplay confirmation separate', async ({ page }) => {
  await installFirstCreationFixture(page);
  await page.goto('/');
  await page.getByTestId('first-creation-skip').click();
  await page.getByTestId('compact-run-menu').click();
  await page.getByTestId('compact-export-btn').click();
  await expect(page.getByTestId('delivery-output')).toHaveValue(/^build\/libs\/copperbench-delivery-[^/]+\/new_fabric_mod\.jar$/);
  await expect(page.locator('#delivery-output-help')).toContainText('仅可替换 Copperbench 之前导出且内容未变的文件');
  await expect(page.locator('#delivery-output-help')).toContainText('src、elements、gradle、lib、libs、plugins');
  await page.getByTestId('delivery-output').fill('../outside.jar');
  await expect(page.getByTestId('delivery-export')).toBeDisabled();
  await page.getByTestId('delivery-output').fill('build/libs/chosen-delivery/trail_block.jar');
  await page.evaluate(async () => { const path = '/src/i18n/locale.ts'; await (await import(path)).setUiLocale('en'); });
  await expect(page.locator('#delivery-output-help')).toContainText('only if it is an unchanged previous Copperbench export');
  await expect(page.getByTestId('delivery-output')).toHaveValue('build/libs/chosen-delivery/trail_block.jar');
  await page.evaluate(async () => { const path = '/src/i18n/locale.ts'; await (await import(path)).setUiLocale('zh'); });
  await page.getByTestId('delivery-export').click();
  await expect(page.getByTestId('delivery-export-fact')).toContainText('普通导出任务成功');
  await expect(page.getByTestId('delivery-requested-path')).toContainText('D:/MockWorkspace/build/libs/chosen-delivery/trail_block.jar');
  await expect(page.getByTestId('delivery-user-observation')).toContainText('尚无完整用户确认');
  await expect(page.getByTestId('delivery-verified-export')).toHaveCount(0);
  const commands = await page.evaluate(() => (window as any).__FIRST_CREATION_COMMANDS__);
  expect(commands).toHaveLength(1);
  expect(commands[0].operation).toBe('export_workspace');
  expect(commands[0].payload.output).toBe('build/libs/chosen-delivery/trail_block.jar');
  expect(commands[0].payload.verifiedTaskId).toBeUndefined();
  await page.keyboard.press('Escape');
  await expect(page.getByTestId('compact-run-menu')).toBeFocused();
});

for (const generatorId of ['datapack-1.20.1', 'datapack-1.21.1', 'datapack-26.1.x']) {
  test(`${generatorId} ordinary delivery uses ZIP for both initial and renewed paths`, async ({ page }, testInfo) => {
    await installFirstCreationFixture(page, { withoutBinding: true, generatorId });
    await page.goto('/');
    await page.getByTestId('compact-run-menu').click();
    await page.getByTestId('compact-export-btn').click();
    const output = page.getByTestId('delivery-output');
    await expect(output).toHaveValue(/^build\/libs\/copperbench-delivery-[^/]+\/new_fabric_mod\.zip$/);
    const initial = await output.inputValue();
    await page.getByRole('button', { name: '使用新的交付路径', exact: true }).click();
    await expect(output).not.toHaveValue(initial);
    await expect(output).toHaveValue(/\.zip$/);
    const selected = await output.inputValue();
    await page.getByTestId('delivery-export').click();
    await expect(page.getByTestId('delivery-export-fact')).toContainText('普通导出任务成功');
    const commands = await page.evaluate(() => (window as any).__FIRST_CREATION_COMMANDS__);
    expect(commands).toHaveLength(1);
    expect(commands[0].operation).toBe('export_workspace');
    expect(commands[0].payload.output).toBe(selected);
    if (generatorId === 'datapack-1.21.1') await page.screenshot({ path: testInfo.outputPath('datapack-delivery.png') });
  });
}

test('a same-name block is inspected and explicitly adopted without another creation or silent binding', async ({ page }) => {
  await installFirstCreationFixture(page, { existingElement: true });
  await page.goto('/');
  await expect(page.getByTestId('first-creation-step-element')).toContainText('已存在同名元素');
  await expect(page.getByTestId('first-creation-create-element')).toHaveCount(0);
  expect(await page.evaluate(() => (window as any).__FIRST_CREATION_SAVES__.at(-1)?.elementId)).toBeUndefined();
  await page.getByRole('button', { name: '检查已有元素' }).click();
  await expect(page.getByTestId('element-inspector')).toBeVisible();
  await page.getByTestId('continue-first-creation').click();
  await page.getByTestId('first-creation-use-existing').click();
  await expect(page.getByTestId('first-creation-open-element')).toBeVisible();
  await expect.poll(() => page.evaluate(() => (window as any).__FIRST_CREATION_SAVES__.at(-1)?.elementId)).toBe(await page.evaluate(() => (window as any).__EXISTING_ELEMENT_ID__));
  expect(await page.evaluate(() => (window as any).__FIRST_CREATION_COMMANDS__.filter((request: any) => request.operation === 'create_mod_element').length)).toBe(0);
});

test('successful old input retains its actual task status and original logs', async ({ page }) => {
  await installFirstCreationFixture(page, { buildState: 'succeeded' });
  await page.goto('/');
  await expect(page.getByTestId('first-creation-step-build')).toContainText('任务成功 · 输入已核对');
  const revision = await page.evaluate(() => (window as any).__FIXTURE_REVISION__());
  await page.evaluate(() => { (window as any).__CURRENT_INPUT_SHA__ = 'b'.repeat(64); });
  await page.getByTestId('first-creation-details').locator('summary').first().click();
  await page.getByTestId('first-creation-refresh-inputs').click();
  await expect(page.getByTestId('first-creation-step-build')).toContainText('任务成功 · 输入随后发生变化');
  expect(await page.evaluate(() => (window as any).__FIXTURE_REVISION__())).toBe(revision);
  await page.getByRole('button', { name: '查看原构建任务' }).click();
  await expect(page.getByTestId('task-current-state')).toHaveText(/Completed|完成|成功/);
  await expect(page.getByTestId('task-log-stream')).toContainText('Original fixture build log');
  await page.getByTestId('task-drawer-close').click();
  await page.getByTestId('continue-first-creation').click();
  await expect(page.getByTestId('first-creation-step-build')).toContainText('任务成功 · 输入随后发生变化');
  expect(await page.evaluate(() => (window as any).__FIRST_CREATION_COMMANDS__.length)).toBe(0);
});

test('a failed build is a separate lifecycle and retains its failed state and original log without retry', async ({ page }) => {
  await installFirstCreationFixture(page, { buildState: 'failed' });
  await page.goto('/');
  await expect(page.getByTestId('first-creation-step-build')).toContainText('任务失败 · 查看日志');
  await page.getByRole('button', { name: '查看原构建任务' }).click();
  await expect(page.getByTestId('task-current-state')).toHaveText(/Failed|失败/);
  await expect(page.getByTestId('task-log-stream')).toContainText('Original fixture failed build log');
  await page.getByTestId('task-drawer-close').click();
  await page.getByTestId('continue-first-creation').click();
  await expect(page.getByTestId('first-creation-step-build')).toContainText('任务失败 · 查看日志');
  expect(await page.evaluate(() => (window as any).__FIRST_CREATION_COMMANDS__.length)).toBe(0);
});

test('an accepted cancellation request stays running until the original task reaches cancelled', async ({ page }) => {
  await installFirstCreationFixture(page, { buildState: 'running', delayCancellation: true });
  await page.goto('/');
  await page.getByRole('button', { name: '查看原构建任务' }).click();
  await expect(page.getByTestId('task-current-state')).toHaveText(/Running|运行中/);
  await page.getByTestId('task-cancel-btn').click();
  await expect(page.getByTestId('task-cancel-status')).toContainText('等待任务返回终态');
  await expect(page.getByTestId('task-current-state')).toHaveText(/Running|运行中/);
  await expect(page.getByTestId('task-cancel-btn')).toBeDisabled();
  await page.evaluate(() => (window as any).__RESOLVE_CANCEL_REQUEST__());
  await expect(page.getByTestId('task-cancel-status')).toContainText('等待任务返回终态');
  await expect(page.getByTestId('task-current-state')).toHaveText(/Running|运行中/);
  await page.evaluate(() => (window as any).__SET_BUILD_TASK_STATE__('cancelled'));
  await expect(page.getByTestId('task-current-state')).toHaveText(/Cancelled|已取消/);
  await expect(page.getByTestId('task-cancel-status')).toHaveCount(0);
  expect(await page.evaluate(() => (window as any).__FIRST_CREATION_COMMANDS__.filter((request: any) => request.operation === 'cancel_task').length)).toBe(1);
});

test('a successful legacy task without an input identity remains unknown', async ({ page }) => {
  await installFirstCreationFixture(page, { buildState: 'succeeded', withoutInputIdentity: true });
  await page.goto('/');
  await expect(page.getByTestId('first-creation-step-build')).toContainText('任务成功 · 输入状态待确认');
  await page.getByTestId('first-creation-nav-behavior').click();
  await expect(page.getByTestId('first-creation-confirm-placed')).toBeDisabled();
  await expect(page.getByTestId('first-creation-confirm-persisted')).toBeDisabled();
});

test('historical exact-export compatibility comes from the receipt in delivery and the original task', async ({ page }) => {
  await installFirstCreationFixture(page);
  await page.goto('/'); await page.getByTestId('first-creation-skip').click();
  await page.evaluate(() => (window as any).__ADD_EXACT_EXPORT_RECEIPT__({ loader: 'neoforge', minecraftVersion: '1.20.1', loaderVersion: '20.1.0' }));
  await page.getByTestId('compact-run-menu').click(); await page.getByTestId('compact-export-btn').click();
  await expect(page.getByTestId('delivery-compatibility')).toContainText('fabric');
  await expect(page.getByTestId('export-receipt-compatibility')).toContainText('neoforge');
  await expect(page.getByTestId('export-receipt-compatibility')).toContainText('1.20.1');
  await expect(page.getByTestId('export-receipt-compatibility')).not.toContainText('1.21.1');
  await page.getByRole('button', { name: '查看原回执', exact: true }).click();
  await expect(page.getByTestId('task-drawer').getByTestId('export-receipt-compatibility')).toContainText('neoforge');
  await expect(page.getByTestId('task-drawer').getByTestId('export-receipt-compatibility')).toContainText('1.20.1');
});

test('missing historical compatibility remains unknown instead of using the current project', async ({ page }) => {
  await installFirstCreationFixture(page);
  await page.goto('/'); await page.getByTestId('first-creation-skip').click();
  await page.evaluate(() => (window as any).__ADD_EXACT_EXPORT_RECEIPT__(null));
  await page.getByTestId('compact-run-menu').click(); await page.getByTestId('compact-export-btn').click();
  await expect(page.getByTestId('export-receipt-compatibility')).toContainText('回执未记录');
  await expect(page.getByTestId('export-receipt-compatibility')).not.toContainText('fabric');
  await expect(page.getByTestId('export-receipt-compatibility')).not.toContainText('1.21.1');
  await page.getByTestId('ui-language-select').selectOption('en');
  await expect(page.getByTestId('export-receipt-compatibility')).toContainText('Not recorded in receipt');
});

for (const theme of ['light', 'dark']) {
  test(`compact first-creation dialog has one surface and visible controls in ${theme}`, async ({ page }) => {
    await page.setViewportSize({ width: 960, height: 600 });
    await installFirstCreationFixture(page);
    await page.goto('/');
    await page.evaluate(theme => document.documentElement.dataset.theme = theme, theme);
    const dialog = page.getByRole('dialog', { name: '第一件作品', exact: true });
    await expect(dialog).toBeVisible();
    const geometry = await dialog.evaluate(element => {
      const box = element.getBoundingClientRect(), style = getComputedStyle(element);
      const overlay = element.parentElement!;
      return { left: box.left, top: box.top, right: box.right, bottom: box.bottom,
        width: window.innerWidth, height: window.innerHeight, shadow: style.boxShadow,
        dialogs: overlay.querySelectorAll('[role="dialog"]').length };
    });
    expect(geometry.dialogs).toBe(1);
    expect(geometry.left).toBeGreaterThanOrEqual(0);
    expect(geometry.top).toBeGreaterThanOrEqual(0);
    expect(geometry.right).toBeLessThanOrEqual(geometry.width);
    expect(geometry.bottom).toBeLessThanOrEqual(geometry.height);
    expect(geometry.shadow).not.toContain('5px 5px 0px');
    await expect(page.getByTestId('first-creation-close')).toBeInViewport();
    await expect(page.getByTestId('first-creation-skip')).toBeInViewport();
    await expect(page.getByTestId('first-creation-create-element')).toBeInViewport();
    await page.screenshot({ path: `../output/startup-update-fix/first-creation-${theme}.png` });
    await page.emulateMedia({ reducedMotion: 'reduce' });
    expect(await dialog.evaluate(element => getComputedStyle(element).animationName)).toBe('none');
    await page.keyboard.press('Escape');
    await expect(dialog).toHaveCount(0);
  });
}

test('help opens the native updater through the desktop host', async ({ page }) => {
  await page.addInitScript(() => {
    (window as any).__UPDATE_ACTIONS__ = [];
    (window as any).__COPPERBENCH_WINDOW_HOST__ = { systemFrame: false, updatesAvailable: true,
      invoke: async (action: string) => { (window as any).__UPDATE_ACTIONS__.push(action); } };
  });
  await page.goto('/');
  await page.getByTestId('nav-help').click();
  await page.getByTestId('help-check-updates').click();
  expect(await page.evaluate(() => (window as any).__UPDATE_ACTIONS__)).toEqual(['check_updates']);
});
