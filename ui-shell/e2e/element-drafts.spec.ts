import { test, expect, type Page } from '@playwright/test';

async function openLamp(page: Page) {
  await page.getByTestId('nav-elements').click();
  await page.locator('.element-card').filter({ hasText: 'Copper Lamp' }).click();
  await expect(page.getByTestId('field-displayName')).toBeVisible();
}

async function leaveAndReturn(page: Page) {
  await page.getByTestId('nav-assets').click();
  await page.getByTestId('nav-elements').click();
}

async function installEditorFixture(page: Page, deferSave = false) {
  await page.addInitScript(({ deferSave }) => {
    const modulePath = '/src/mock/mockBridge.ts';
    const ready = import(modulePath).then(({ MockCoreBridge }) => new MockCoreBridge());
    const listeners = new Set<(raw: string) => void>();
    let releaseSave: (() => void) | undefined;
    window.addEventListener('draft-fixture-release-save', () => releaseSave?.());
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
        if (deferSave && request.operation === 'update_mod_element') {
          await new Promise<void>(resolve => { releaseSave = resolve; });
        }
        const result = request.messageType === 'handshake' ? await core.negotiateHandshake(request)
          : request.messageType === 'command' ? await core.sendCommand(request) : await core.sendQuery(request);
        if (request.operation === 'get_mod_element_editor' && result.data) {
          const fields = result.data.sections[0].fields;
          if (!fields.some(field => field.path === '/draftJson')) fields.push(
            { path: '/draftJson', label: { key: 'audit.json', fallback: 'Draft JSON' }, control: 'json',
              required: false, readOnly: false, value: {}, options: [], diagnostics: [] },
            { path: '/draftReferences', label: { key: 'audit.references', fallback: 'Draft references' }, control: 'element_reference_list',
              required: false, readOnly: false, value: [], options: [], diagnostics: [] }
          );
        }
        return JSON.stringify(result);
      }
    };
  }, { deferSave });
}

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
  await expect(page.getByTestId('inspector-external-change')).toBeVisible();
  await leaveAndReturn(page);
  await expect(page.getByTestId('field-displayName')).toHaveValue('Newer unsaved draft');
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
