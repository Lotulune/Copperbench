import { test, expect, type Page } from '@playwright/test';

declare global {
  interface Window {
    draftFixture: {
      requests: { operation?: string; payload?: { changes?: { path: string; value: unknown }[] } }[];
      refreshPending: boolean;
      refreshCompleted: boolean;
    };
  }
}

async function openLamp(page: Page) {
  await page.getByTestId('nav-elements').click();
  await page.locator('.element-card').filter({ hasText: 'Copper Lamp' }).click();
  await expect(page.getByTestId('field-displayName')).toBeVisible();
}

async function leaveAndReturn(page: Page) {
  await page.getByTestId('nav-assets').click();
  await page.getByTestId('nav-elements').click();
}

async function installEditorFixture(page: Page, deferSave = false, deferRefresh = false) {
  await page.addInitScript(({ deferSave, deferRefresh }) => {
    const modulePath = '/src/mock/mockBridge.ts';
    const ready = import(modulePath).then(({ MockCoreBridge }) => new MockCoreBridge());
    const listeners = new Set<(raw: string) => void>();
    let releaseSave: (() => void) | undefined;
    let releaseRefresh: (() => void) | undefined;
    let refreshAfterSave = false;
    window.draftFixture = { requests: [], refreshPending: false, refreshCompleted: false };
    const saveRequests: Array<{ workspaceId: string; elementId: string; expectedRevision: number; changes: Array<{ path: string; value: unknown }> }> = [];
    const saveResults: Array<{ status: string; newRevision: number }> = [];
    window.addEventListener('draft-fixture-release-save', () => releaseSave?.());
    window.addEventListener('draft-fixture-release-refresh', () => releaseRefresh?.());
    window.addEventListener('draft-fixture-external-update', async () => {
      const core = await ready;
      const state = core.getState();
      const element = { ...state.elements.find(item => item.name === 'copper_lamp')!, updatedAt: '2026-10-06T12:00:00Z' };
      state.elements = state.elements.map(item => item.id === element.id ? element : item);
      const projection = state.elementEditors[element.id];
      projection.element = element;
      projection.sections.flatMap(section => section.fields).find(field => field.path.endsWith('/displayName'))!.value = 'External Lamp';
      state.workbench.workspace.revision += 1;
      listeners.forEach(listener => listener(JSON.stringify({
        messageType: 'event', schemaVersion: '1.0', eventId: 'draft-external-update',
        workspaceId: state.workbench.workspace.id, revision: state.workbench.workspace.revision,
        sequence: 101, occurredAt: element.updatedAt, event: 'mod_element_updated', payload: { element }
      })));
    });
    window.copperbenchHost = {
      workspaceId: '11111111-1111-4111-8111-111111111111',
      onEvent(listener) { listeners.add(listener); return () => listeners.delete(listener); },
      async invoke(raw) {
        const core = await ready;
        const request = JSON.parse(raw);
        window.draftFixture.requests.push(request);
        if (request.operation === 'update_mod_element') {
          saveRequests.push({ workspaceId: request.workspaceId, elementId: request.payload.elementId,
            expectedRevision: request.expectedRevision, changes: request.payload.changes });
          sessionStorage.setItem('draft-fixture-save-requests', JSON.stringify(saveRequests));
        }
        if (deferSave && request.operation === 'update_mod_element') {
          await new Promise<void>(resolve => { releaseSave = resolve; });
        }
        const result = request.messageType === 'handshake' ? await core.negotiateHandshake(request)
          : request.messageType === 'command' ? await core.sendCommand(request) : await core.sendQuery(request);
        if (request.operation === 'update_mod_element' && result.status === 'committed') refreshAfterSave = true;
        if (request.operation === 'update_mod_element') {
          saveResults.push({ status: result.status, newRevision: result.newRevision });
          sessionStorage.setItem('draft-fixture-save-results', JSON.stringify(saveResults));
        }
        if (request.operation === 'get_mod_element_editor' && result.data) {
          const fields = result.data.sections[0].fields;
          if (!fields.some(field => field.path === '/draftJson')) fields.push(
            { path: '/draftJson', label: { key: 'audit.json', fallback: 'Draft JSON' }, control: 'json',
              required: false, readOnly: false, value: {}, options: [], diagnostics: [] },
            { path: '/draftReferences', label: { key: 'audit.references', fallback: 'Draft references' }, control: 'element_reference_list',
              required: false, readOnly: false, value: [], options: [], diagnostics: [] },
            { path: '/draftRows', label: { key: 'audit.rows', fallback: 'Draft rows' }, control: 'structured_list',
              required: false, readOnly: false, value: [{ amount: 1, note: 'Preserved', metadata: { keep: true } }], options: [], diagnostics: [],
              itemFields: [
                { path: '/amount', label: { key: 'audit.amount', fallback: 'Amount' }, control: 'number',
                  required: false, readOnly: false, value: 1, options: [], diagnostics: [], constraints: { step: 0.1 } },
                { path: '/note', label: { key: 'audit.note', fallback: 'Note' }, control: 'text',
                  required: false, readOnly: false, value: '', options: [], diagnostics: [],
                  condition: { operator: 'any_truthy', paths: ['/amount'] } }
              ] }
          );
          if (deferRefresh && refreshAfterSave) {
            refreshAfterSave = false;
            window.draftFixture.refreshPending = true;
            await new Promise<void>(resolve => { releaseRefresh = resolve; });
            window.draftFixture.refreshCompleted = true;
          }
        }
        return JSON.stringify(result);
      }
    };
  }, { deferSave, deferRefresh });
}

async function lastSavedChanges(page: Page) {
  return page.evaluate(() => window.draftFixture.requests.filter(request => request.operation === 'update_mod_element').at(-1)?.payload?.changes);
}

test('a delayed post-save refresh preserves edits made after the command committed', async ({ page }) => {
  await installEditorFixture(page, false, true);
  await page.goto('/');
  await openLamp(page);
  const name = page.getByTestId('field-displayName');
  const save = page.getByTestId('inspector-save-btn');
  await name.fill('First saved name');
  await save.click();
  await expect.poll(() => page.evaluate(() => window.draftFixture.refreshPending)).toBe(true);
  await expect(name).toBeEditable();
  await name.fill('Second unsaved name');
  await page.evaluate(() => window.dispatchEvent(new Event('draft-fixture-release-refresh')));
  await expect.poll(() => page.evaluate(() => window.draftFixture.refreshCompleted)).toBe(true);
  await page.evaluate(() => new Promise<void>(resolve => requestAnimationFrame(() => requestAnimationFrame(() => resolve()))));
  await expect(name).toHaveValue('Second unsaved name');
  await expect(save).toBeEnabled();
  await leaveAndReturn(page);
  await expect(name).toHaveValue('Second unsaved name');
  await expect(page.getByTestId('inspector-external-change')).not.toBeVisible();
  await expect(save).toBeEnabled();
});

test('negative and decimal drafts survive typing and navigation and are submitted as numbers', async ({ page }) => {
  await installEditorFixture(page);
  await page.goto('/');
  await page.getByTestId('nav-elements').click();
  await page.getByTestId('create-element-btn').click();
  await page.getByTestId('create-element-type-biome').click();
  await page.getByTestId('create-element-name-input').fill('cold_grove');
  await page.getByTestId('create-element-submit-btn').click();
  const temperature = page.getByTestId('field-temperature');
  const save = page.getByTestId('inspector-save-btn');
  await expect(temperature).toHaveAttribute('min', '-1');
  await temperature.fill('');
  await temperature.pressSequentially('-', { delay: 40 });
  await expect(temperature).toHaveAttribute('aria-invalid', 'true');
  await expect(save).toBeDisabled();
  await temperature.pressSequentially('1', { delay: 40 });
  await expect(temperature).toHaveValue('-1');
  await save.click();
  await expect(save).toBeDisabled();
  expect(await lastSavedChanges(page)).toContainEqual({ path: '/temperature', value: -1 });

  await temperature.fill('');
  await leaveAndReturn(page);
  await expect(temperature).toHaveValue('');
  await expect(temperature).toHaveAttribute('aria-invalid', 'true');
  await expect(save).toBeDisabled();
  await temperature.pressSequentially('-0.25', { delay: 40 });
  await expect(temperature).toHaveValue('-0.25');
  await save.click();
  await expect(save).toBeDisabled();
  expect(await lastSavedChanges(page)).toContainEqual({ path: '/temperature', value: -0.25 });
});

test('structured numeric drafts preserve unknown data and numeric conditions when serialized', async ({ page }) => {
  await installEditorFixture(page);
  await page.goto('/');
  await openLamp(page);
  const amount = page.getByTestId('field-draftRows-0-amount');
  const note = page.getByTestId('field-draftRows-0-note');
  const save = page.getByTestId('inspector-save-btn');
  await amount.fill('0');
  await expect(note).toBeDisabled();
  await amount.fill('');
  await expect(save).toBeDisabled();
  await leaveAndReturn(page);
  await expect(amount).toHaveValue('');
  await expect(save).toBeDisabled();
  await amount.pressSequentially('-1.5', { delay: 40 });
  await expect(amount).toHaveValue('-1.5');
  await expect(note).toBeEditable();
  await save.click();
  await expect(save).toBeDisabled();
  expect(await lastSavedChanges(page)).toContainEqual({
    path: '/draftRows', value: [{ amount: -1.5, note: 'Preserved', metadata: { keep: true } }]
  });
});

test('unsaved fields survive navigation, switching elements and closing the inspector; applying clears the draft', async ({ page }) => {
  await page.goto('/');
  await openLamp(page);
  const name = page.getByTestId('field-displayName');
  await name.fill('Unapplied Copper Lamp');
  await leaveAndReturn(page);
  await expect(name).toHaveValue('Unapplied Copper Lamp');
  await expect(page.getByTestId('inspector-save-btn')).toBeEnabled();

  await page.locator('.element-card').filter({ hasText: 'Trail Compass' }).click();
  await expect(name).toHaveValue('Trail Compass');
  await name.fill('Unapplied Compass');
  await page.locator('.element-card').filter({ hasText: 'Copper Lamp' }).click();
  await expect(name).toHaveValue('Unapplied Copper Lamp');
  await page.getByTestId('inspector-close-btn').click();
  await page.locator('.element-card').filter({ hasText: 'Copper Lamp' }).click();
  await expect(name).toHaveValue('Unapplied Copper Lamp');

  await page.getByTestId('inspector-save-btn').click();
  await expect(page.getByTestId('inspector-save-btn')).toBeDisabled();
  await leaveAndReturn(page);
  await expect(name).toHaveValue('Unapplied Copper Lamp');
  await expect(page.getByTestId('inspector-external-change')).not.toBeVisible();
  await expect(page.getByTestId('inspector-save-btn')).toBeDisabled();
});

test('invalid JSON and unadded reference input survive leaving an editor without enabling save', async ({ page }) => {
  await installEditorFixture(page);
  await page.goto('/');
  await openLamp(page);
  await page.getByTestId('field-draftJson').fill('{ unfinished');
  await page.getByTestId('field-draftReferences').fill('CUSTOM:unfinished_biome');
  await leaveAndReturn(page);
  await expect(page.getByTestId('field-draftJson')).toHaveValue('{ unfinished');
  await expect(page.getByTestId('field-draftReferences')).toHaveValue('CUSTOM:unfinished_biome');
  await expect(page.getByTestId('inspector-save-btn')).toBeDisabled();

  await page.getByTestId('field-draftJson').fill('{}');
  await leaveAndReturn(page);
  await expect(page.getByTestId('field-draftReferences')).toHaveValue('CUSTOM:unfinished_biome');
  await expect(page.getByTestId('inspector-save-btn')).toBeDisabled();

  await page.getByTestId('field-displayName').fill('Saved name with unfinished reference');
  await page.getByTestId('inspector-save-btn').click();
  await expect(page.getByTestId('inspector-save-btn')).toBeDisabled();
  await leaveAndReturn(page);
  await expect(page.getByTestId('field-draftReferences')).toHaveValue('CUSTOM:unfinished_biome');
  await expect(page.getByTestId('inspector-external-change')).not.toBeVisible();
  await expect(page.getByTestId('inspector-save-btn')).toBeDisabled();
});

test('restored drafts retain the old revision and require an explicit discard after an external change', async ({ page }) => {
  await installEditorFixture(page);
  await page.goto('/');
  await openLamp(page);
  await page.getByTestId('field-displayName').fill('Local draft');
  await page.getByTestId('nav-assets').click();
  await page.evaluate(() => window.dispatchEvent(new Event('draft-fixture-external-update')));
  await page.getByTestId('nav-elements').click();
  await expect(page.getByTestId('field-displayName')).toHaveValue('Local draft');
  await expect(page.getByTestId('inspector-external-change')).toBeVisible();
  await expect(page.getByTestId('inspector-save-btn')).toBeDisabled();
  await page.getByTestId('inspector-reload-latest').click();
  await expect(page.getByTestId('field-displayName')).toHaveValue('External Lamp');
  await leaveAndReturn(page);
  await expect(page.getByTestId('field-displayName')).toHaveValue('External Lamp');
  await expect(page.getByTestId('inspector-external-change')).not.toBeVisible();
  await expect(page.getByTestId('inspector-save-btn')).toBeDisabled();
});

test('workspaces with the same element ID keep separate drafts', async ({ page }) => {
  await page.goto('/');
  await openLamp(page);
  await page.getByTestId('field-displayName').fill('First workspace draft');
  await page.evaluate(async () => {
    const scenariosPath = '/src/mock/scenarios.ts';
    const bridgePath = performance.getEntriesByType('resource').map(entry => entry.name)
      .find(url => new URL(url).pathname === '/src/bridge/index.ts') ?? '/src/bridge/index.ts';
    const { SCENARIOS } = await import(scenariosPath);
    const { coreBridge } = await import(bridgePath);
    const scenario = structuredClone(SCENARIOS.ready);
    scenario.scenarioId = 'draft-other-workspace';
    for (const message of scenario.initialMessages) {
      message.workspaceId = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';
      if (message.operation === 'get_workbench') message.data.workspace.id = message.workspaceId;
    }
    SCENARIOS[scenario.scenarioId] = scenario;
    coreBridge.loadScenario(scenario.scenarioId);
  });
  await expect(page.getByTestId('field-displayName')).toHaveValue('Copper Lamp');
  await page.getByTestId('field-displayName').fill('Second workspace draft');
  await page.evaluate(async () => {
    const bridgePath = performance.getEntriesByType('resource').map(entry => entry.name)
      .find(url => new URL(url).pathname === '/src/bridge/index.ts') ?? '/src/bridge/index.ts';
    const { coreBridge } = await import(bridgePath);
    coreBridge.loadScenario('ready');
  });
  await expect(page.getByTestId('field-displayName')).toHaveValue('First workspace draft');
  await expect(page.getByTestId('inspector-save-btn')).toBeEnabled();
});

test('a late save result does not clear a newer draft created after navigating back', async ({ page }) => {
  await installEditorFixture(page, true);
  await page.goto('/');
  await openLamp(page);
  await page.getByTestId('field-displayName').fill('Submitted draft');
  await page.getByTestId('inspector-save-btn').click();
  await expect(page.getByTestId('field-displayName')).toBeDisabled();
  await leaveAndReturn(page);
  await page.getByTestId('field-displayName').fill('Newer unsaved draft');
  await page.getByTestId('nav-assets').click();
  await page.evaluate(() => window.dispatchEvent(new Event('draft-fixture-release-save')));
  await page.getByTestId('nav-elements').click();
  await expect(page.getByTestId('field-displayName')).toHaveValue('Newer unsaved draft');
  const save = page.getByTestId('inspector-save-btn');
  await expect(save).toBeEnabled();
  await expect(page.getByTestId('field-displayName')).toBeEditable();
  await expect(page.getByTestId('inspector-external-change')).not.toBeVisible();
  await expect(page.getByTestId('element-inspector').getByRole('status').filter({ hasText: '未保存' })).toBeVisible();
  await leaveAndReturn(page);
  await expect(page.getByTestId('field-displayName')).toHaveValue('Newer unsaved draft');
  await expect(save).toBeEnabled();
  await expect(page.getByTestId('inspector-external-change')).not.toBeVisible();
  const first = await page.evaluate(() => ({
    requests: JSON.parse(sessionStorage.getItem('draft-fixture-save-requests') ?? '[]'),
    results: JSON.parse(sessionStorage.getItem('draft-fixture-save-results') ?? '[]')
  }));
  expect(first.requests).toHaveLength(1);
  expect(first.results).toHaveLength(1);
  expect(first.results[0].status).toBe('committed');
  expect(first.requests[0].changes).toHaveLength(1);
  expect(first.requests[0].changes[0].value).toBe('Submitted draft');

  // An own-save receipt is the new baseline. B must still require a separate
  // user submission with that exact revision, rather than a conflict discard.
  await save.click();
  await expect.poll(() => page.evaluate(() => JSON.parse(sessionStorage.getItem('draft-fixture-save-requests') ?? '[]').length)).toBe(2);
  await page.evaluate(() => window.dispatchEvent(new Event('draft-fixture-release-save')));
  await expect.poll(() => page.evaluate(() => JSON.parse(sessionStorage.getItem('draft-fixture-save-results') ?? '[]').length)).toBe(2);
  const second = await page.evaluate(() => ({
    requests: JSON.parse(sessionStorage.getItem('draft-fixture-save-requests') ?? '[]'),
    results: JSON.parse(sessionStorage.getItem('draft-fixture-save-results') ?? '[]')
  }));
  expect(second.requests[1].expectedRevision).toBe(first.results[0].newRevision);
  expect(second.requests[1].workspaceId).toBe(first.requests[0].workspaceId);
  expect(second.requests[1].elementId).toBe(first.requests[0].elementId);
  expect(second.requests[1].changes).toEqual([{ path: first.requests[0].changes[0].path, value: 'Newer unsaved draft' }]);
  expect(second.results[1].status).toBe('committed');
  await expect(save).toBeDisabled();
  await expect(page.getByTestId('field-displayName')).toHaveValue('Newer unsaved draft');
  await expect(page.getByTestId('element-inspector').getByRole('status').filter({ hasText: '未保存' })).not.toBeVisible();
});

test('numeric intermediate input survives navigation and language changes without an implicit zero', async ({ page }) => {
  await installEditorFixture(page);
  await page.goto('/');
  await openLamp(page);
  const number = page.getByTestId('field-hardness');
  await number.fill('-');
  await expect(page.getByTestId('inspector-save-btn')).toBeDisabled();
  await leaveAndReturn(page);
  await expect(number).toHaveValue('-');
  await page.evaluate(async () => { const modulePath = '/src/i18n/locale.ts'; (await import(modulePath)).setUiLocale('en'); });
  await expect(number).toHaveValue('-');
  await expect(page.getByTestId('validation-alert')).toContainText('Enter a valid number');
  await number.fill('');
  await leaveAndReturn(page);
  await expect(number).toHaveValue('');
  await expect(page.getByTestId('inspector-save-btn')).toBeDisabled();
});

test('submitting an equivalent numeric spelling clears only the input draft and sends no Core command', async ({ page }) => {
  await installEditorFixture(page);
  await page.goto('/');
  await openLamp(page);
  const number = page.getByTestId('field-hardness');
  const baseline = Number(await number.inputValue());
  await page.evaluate(async () => {
    const modulePath = '/src/bridge/index.ts';
    const { coreBridge } = await import(modulePath);
    const original = coreBridge.sendCommand.bind(coreBridge);
    const observed = window as typeof window & { recoveryCommands: unknown[] };
    observed.recoveryCommands = [];
    coreBridge.sendCommand = async command => { observed.recoveryCommands.push(command); return original(command); };
  });
  const raw = `${baseline}e0`;
  await number.fill(raw);
  await leaveAndReturn(page);
  await expect(number).toHaveValue(raw);
  await expect(page.getByTestId('inspector-save-btn')).toBeEnabled();
  await page.getByTestId('inspector-save-btn').click();
  await expect(number).toHaveValue(String(baseline));
  await expect(page.getByTestId('inspector-save-btn')).toBeDisabled();
  expect(await page.evaluate(() => (window as typeof window & { recoveryCommands: unknown[] }).recoveryCommands.length)).toBe(0);
});

test('a delayed editor refresh after save cannot overwrite input typed in the same inspector', async ({ page }) => {
  await installEditorFixture(page, false, true);
  await page.goto('/');
  await openLamp(page);
  const name = page.getByTestId('field-displayName');
  await name.fill('Saved version A');
  await page.getByTestId('inspector-save-btn').click();
  await expect.poll(() => page.evaluate(() => window.draftFixture.refreshPending)).toBe(true);
  await expect(name).toBeEditable();
  await name.fill('New draft B');
  await page.evaluate(() => window.dispatchEvent(new Event('draft-fixture-release-refresh')));
  await expect.poll(() => page.evaluate(() => window.draftFixture.refreshCompleted)).toBe(true);
  await expect(name).toHaveValue('New draft B');
  await leaveAndReturn(page);
  await expect(name).toHaveValue('New draft B');
  await expect(page.getByTestId('inspector-save-btn')).toBeEnabled();
});

test('unadded reference input remains after a save completes while its inspector is unmounted', async ({ page }) => {
  await installEditorFixture(page, true);
  await page.goto('/');
  await openLamp(page);
  await page.getByTestId('field-displayName').fill('Submitted name');
  await page.getByTestId('field-draftReferences').fill('CUSTOM:still_unadded');
  await page.getByTestId('inspector-save-btn').click();
  await expect(page.getByTestId('field-displayName')).toBeDisabled();
  await page.getByTestId('nav-assets').click();
  await page.evaluate(async () => {
    const bridgePath = performance.getEntriesByType('resource').map(entry => entry.name)
      .find(url => new URL(url).pathname === '/src/bridge/index.ts') ?? '/src/bridge/index.ts';
    const { coreBridge } = await import(bridgePath);
    const beforeRevision = coreBridge.getState().workbench.workspace.revision;
    const completed = new Promise<void>(resolve => {
      const unsubscribe = coreBridge.onStateChange(state => {
        if (state.workbench.workspace.revision > beforeRevision) { unsubscribe(); resolve(); }
      });
    });
    window.dispatchEvent(new Event('draft-fixture-release-save'));
    await completed;
  });
  await page.getByTestId('nav-elements').click();
  await expect(page.getByTestId('field-displayName')).toHaveValue('Submitted name');
  await expect(page.getByTestId('field-draftReferences')).toHaveValue('CUSTOM:still_unadded');
  await expect(page.getByTestId('inspector-external-change')).not.toBeVisible();
  await expect(page.getByTestId('inspector-save-btn')).toBeDisabled();
});
