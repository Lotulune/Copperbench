import { test, expect } from '@playwright/test';

test('client completion refreshes partial resource diagnostics without a revision or focus change', async ({ page }) => {
  await page.addInitScript(() => {
    const listeners = new Set<(raw: string) => void>();
    const modulePath = '/src/mock/mockBridge.ts';
    const ready = import(modulePath).then(({ MockCoreBridge }) => new MockCoreBridge());
    let completed = false;
    window.addEventListener('complete-client-resources', async () => {
      const core = await ready;
      completed = true;
      const state = core.getState();
      listeners.forEach(listener => listener(JSON.stringify({
        messageType: 'event', schemaVersion: '1.0', eventId: crypto.randomUUID(),
        workspaceId: state.workbench.workspace.id, revision: state.workbench.workspace.revision,
        sequence: 1, event: 'task_completed', timestamp: new Date().toISOString(),
        payload: { task: { id: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', kind: 'run_client',
          state: 'succeeded', cancellable: false, progress: 1,
          startedAt: '2026-09-24T09:59:07Z', completedAt: '2026-09-24T10:24:14Z',
          stage: { key: 'task.run_client.completed', fallback: 'Client completed' },
          diagnostics: { error: 0, warning: 0, info: 0 } } }
      })));
    });
    window.copperbenchHost = {
      workspaceId: '11111111-1111-4111-8111-111111111111',
      onEvent(listener) { listeners.add(listener); return () => listeners.delete(listener); },
      async invoke(raw) {
        const core = await ready, request = JSON.parse(raw);
        const result = request.messageType === 'handshake' ? await core.negotiateHandshake(request)
          : request.messageType === 'command' ? await core.sendCommand(request) : await core.sendQuery(request);
        if (request.operation === 'get_workspace_health') {
          result.data.diagnostics = { total: completed ? 0 : 233, error: 0, warning: completed ? 0 : 233, info: 0,
            scope: 'workspace_current', collectionState: completed ? 'complete' : 'partial',
            snapshotId: `client-${completed}`, items: [] };
        }
        return JSON.stringify(result);
      }
    };
  });
  await page.goto('/');
  await page.getByTestId('ui-language-select').selectOption('en');
  const badge = page.getByTestId('diagnostics-badge');
  await expect(badge).toContainText('0 errors, 233 warnings');
  await page.waitForTimeout(250);
  await page.evaluate(() => window.dispatchEvent(new Event('complete-client-resources')));
  await expect(badge).toContainText('0 errors, 0 warnings');
});

test('external asset reindex refreshes global counts without a revision change', async ({ page }, testInfo) => {
  await page.addInitScript(() => {
    const modulePath = '/src/mock/mockBridge.ts';
    const ready = import(modulePath).then(({ MockCoreBridge }) => new MockCoreBridge());
    let sourceErrors = 0;
    let indexedErrors = 0;
    let delayed = false;
    window.addEventListener('asset-fixture-change', event => {
      sourceErrors = (event as CustomEvent<number>).detail;
      delayed = true;
    });
    window.copperbenchHost = {
      workspaceId: '11111111-1111-4111-8111-111111111111', onEvent() { return () => {}; },
      async invoke(raw) {
        const core = await ready, request = JSON.parse(raw);
        const result = request.messageType === 'handshake' ? await core.negotiateHandshake(request)
          : request.messageType === 'command' ? await core.sendCommand(request) : await core.sendQuery(request);
        if (request.operation === 'list_assets') {
          if (delayed) {
            delayed = false;
            sessionStorage.setItem('asset-index-waiting', 'true');
            await new Promise<void>(resolve => window.addEventListener('asset-fixture-release', () => resolve(), { once: true }));
          }
          indexedErrors = sourceErrors;
          result.data.health.errorAssets = indexedErrors ? 3 : 0;
          result.data.health.warningAssets = 0;
          result.data.health.missingReferences = indexedErrors;
        }
        if (request.operation === 'get_workspace_health') {
          result.data.diagnostics = { total: indexedErrors, error: indexedErrors, warning: 0, info: 0,
            scope: 'workspace_current', collectionState: 'complete', snapshotId: `assets-${indexedErrors}`, items: [] };
          result.data.assets.summary.missingReferences = indexedErrors;
        }
        return JSON.stringify(result);
      }
    };
  });
  await page.goto('/');
  await page.getByTestId('ui-language-select').selectOption('en');
  const badge = page.getByTestId('diagnostics-badge');
  await expect(badge).toContainText('0 errors, 0 warnings');
  for (const errors of [9, 0]) {
    await page.evaluate(errors => {
      sessionStorage.removeItem('asset-index-waiting');
      window.dispatchEvent(new CustomEvent('asset-fixture-change', { detail: errors }));
    }, errors);
    if (errors) await page.getByTestId('nav-assets').click();
    else await page.evaluate(() => window.dispatchEvent(new Event('focus')));
    await expect.poll(() => page.evaluate(() => sessionStorage.getItem('asset-index-waiting'))).toBe('true');
    await expect(badge).toContainText('Checking diagnostics');
    await page.evaluate(() => window.dispatchEvent(new Event('asset-fixture-release')));
    await expect(badge).toContainText(`${errors} errors, 0 warnings`);
    await expect(page.getByTestId('asset-health-summary')).toContainText(`${errors ? 3 : 0} Assets with errors`);
    for (const label of await page.getByTestId('asset-health-summary').locator('span').all()) {
      expect(await label.evaluate(el => el.scrollWidth <= el.clientWidth + 1)).toBe(true);
    }
    if (errors) await page.getByTestId('asset-health-panel').screenshot({ path: testInfo.outputPath('asset-count-scope.png') });
    await badge.focus();
    await page.keyboard.press('Enter');
    await expect(page.getByTestId('workspace-health-panel')).toBeFocused();
    await expect(page.getByTestId('workspace-health-diagnostics')).toContainText(`${errors} total · ${errors} errors`);
    await expect(badge).toContainText(`${errors} errors, 0 warnings`);
    if (errors) {
      await page.getByTestId('nav-assets').click();
      await expect(badge).toContainText('9 errors, 0 warnings');
    }
  }
});

test('external revision events refresh asset and global health while the window stays focused', async ({ page }) => {
  await page.addInitScript(() => {
    const listeners = new Set<(raw: string) => void>();
    const modulePath = '/src/mock/mockBridge.ts';
    const ready = import(modulePath).then(({ MockCoreBridge }) => {
      const core = new MockCoreBridge();
      return core;
    });
    let errors = 9;
    window.addEventListener('repair-through-sdk', async () => {
      const core = await ready;
      errors = 0;
      const state = core.getState();
      const event = { messageType: 'event', schemaVersion: '1.0', eventId: crypto.randomUUID(),
        workspaceId: state.workbench.workspace.id, revision: state.workbench.workspace.revision + 1,
        sequence: 1, event: 'mod_element_updated', timestamp: new Date().toISOString(),
        payload: { element: { ...state.elements[0], displayName: 'Repaired externally' } } };
      // A contiguous event must not rely on gap reconciliation fetching a fresh workbench object.
      listeners.forEach(listener => listener(JSON.stringify(event)));
    });
    window.copperbenchHost = {
      workspaceId: '11111111-1111-4111-8111-111111111111',
      onEvent(listener) { listeners.add(listener); return () => listeners.delete(listener); },
      async invoke(raw) {
        const core = await ready, request = JSON.parse(raw);
        const result = request.messageType === 'handshake' ? await core.negotiateHandshake(request)
          : request.messageType === 'command' ? await core.sendCommand(request) : await core.sendQuery(request);
        if (request.operation === 'list_assets') {
          result.data.health.errorAssets = errors ? 3 : 0;
          result.data.health.missingReferences = errors;
          result.data.health.warningAssets = 0;
        }
        if (request.operation === 'get_workspace_health') {
          result.data.diagnostics = { total: errors, error: errors, warning: 0, info: 0,
            scope: 'workspace_current', collectionState: 'complete', snapshotId: `external-${errors}`, items: [] };
        }
        return JSON.stringify(result);
      }
    };
  });
  await page.goto('/');
  await page.getByTestId('ui-language-select').selectOption('en');
  await page.getByTestId('nav-assets').click();
  await expect(page.getByTestId('diagnostics-badge')).toContainText('9 errors, 0 warnings');
  await expect(page.getByTestId('asset-health-summary')).toContainText('3 Assets with errors');
  await page.waitForTimeout(250); // Let initial projection queries settle before the external event.
  await page.evaluate(() => window.dispatchEvent(new Event('repair-through-sdk')));
  // No focus event, navigation, reload or click may stand in for the incoming revision.
  await expect(page.getByTestId('asset-health-summary')).toContainText('0 Assets with errors');
  await expect(page.getByTestId('diagnostics-badge')).toContainText('0 errors, 0 warnings');
});
