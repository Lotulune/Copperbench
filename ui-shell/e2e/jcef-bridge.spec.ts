import { test, expect } from '@playwright/test';

test.describe('JCEF Bridge & Host Transport Integration', () => {
  test('default browser environment uses MockCoreBridge and retains interactive scenario testing', async ({ page }) => {
    await page.goto('/');
    await page.waitForSelector('[data-testid="app-shell"]');

    // Without native injection, mock bridge should be active and scenario switcher should function
    await expect(page.locator('[data-testid="scenario-switcher-trigger"]')).toBeVisible();
    await page.click('[data-testid="scenario-switcher-trigger"]');
    await page.click('[data-testid="scenario-btn-ready"]');
    await expect(page.getByText('Copper Trails').first()).toBeVisible();
  });

  test('routes the isolated legacy plugin window through its scoped native host', async ({ page }) => {
    await page.addInitScript(() => {
      window.__COPPERBENCH_LEGACY_PLUGIN_HOST__ = {
        available: true,
        invoke: async (action) => {
          window.sessionStorage.setItem('lastLegacyPluginAction', action);
        }
      };
    });

    await page.goto('/');
    await page.waitForSelector('[data-testid="app-shell"]');
    await page.click('[data-testid="nav-plugins"]');

    const openButton = page.locator('[data-testid="open-legacy-plugin-window"]');
    await expect(openButton).toBeEnabled();
    await openButton.click();
    await expect.poll(() => page.evaluate(() => window.sessionStorage.getItem('lastLegacyPluginAction')))
      .toBe('open');
  });

  test('detects injected window.copperbenchHost and routes handshake and initial projections', async ({ page }) => {
    await page.addInitScript(() => {
      const workspaceId = '11111111-1111-4111-8111-111111111111';
      const eventListeners = new Set<(raw: string) => void>();

      window.__COPPERBENCH_EMIT_EVENT__ = (raw: string) => {
        eventListeners.forEach((l) => l(raw));
      };

      window.__COPPERBENCH_WINDOW_HOST__ = {
        systemFrame: true,
        invoke: async (action) => {
          window.sessionStorage.setItem('lastWindowAction', action);
        }
      };

      window.copperbenchHost = {
        workspaceId,
        invoke: async (rawJson: string) => {
          const envelope = JSON.parse(rawJson);
          if (envelope.operation) {
            window.sessionStorage.setItem('lastCoreOperation', envelope.operation);
            if (envelope.messageType === 'command') {
              window.sessionStorage.setItem('lastCoreCommand', envelope.operation);
            }
          }

          if (envelope.messageType === 'handshake') {
            return JSON.stringify({
              messageType: 'handshake_result',
              requestId: envelope.requestId,
              status: 'compatible',
              selectedSchemaVersion: '1.0',
              coreSchemaVersions: ['1.0'],
              diagnostics: []
            });
          }

          if (envelope.messageType === 'query' && envelope.operation === 'get_workbench') {
            return JSON.stringify({
              messageType: 'query_result',
              schemaVersion: '1.0',
              requestId: envelope.requestId,
              workspaceId,
              operation: 'get_workbench',
              status: 'succeeded',
              revision: 42,
              data: {
                workspace: {
                  id: workspaceId,
                  name: 'Native JCEF Workspace',
                  kind: 'mod',
                  revision: 42,
                  dirty: false,
                  generator: {
                    id: 'fabric-1.21.1',
                    loader: 'fabric',
                    minecraftVersion: '1.21.1',
                    displayName: 'Fabric 1.21.1',
                    state: 'ready'
                  },
                  lock: { state: 'write_available', holder: null },
                  compatibility: { mode: 'native', unknownDataPreserved: true }
                },
                permission: {
                  profile: 'workspace',
                  canRequestElevation: true,
                  protectedOperationsAlwaysConfirm: true
                },
                connection: { core: 'connected', network: 'online', bridge: 'ready' },
                elementCounts: { total: 1, valid: 1, invalid: 0, draft: 0, unsupported: 0 },
                activeTasks: [],
                capabilities: [
                  {
                    id: 'mod_elements.create',
                    availability: 'available',
                    reasonCode: null,
                    message: null,
                    affectedPaths: []
                  }
                ],
                recentElements: [
                  {
                    id: '22222222-2222-4222-8222-222222222221',
                    type: 'item',
                    name: 'native_compass',
                    displayName: 'Native Compass',
                    state: 'valid',
                    ownership: 'generated',
                    updatedAt: '2026-08-17T05:00:00Z',
                    diagnostics: { error: 0, warning: 0, info: 0 }
                  }
                ]
              },
              diagnostics: []
            });
          }

          if (envelope.messageType === 'query' && envelope.operation === 'list_mod_elements') {
            return JSON.stringify({
              messageType: 'query_result',
              schemaVersion: '1.0',
              requestId: envelope.requestId,
              workspaceId,
              operation: 'list_mod_elements',
              status: 'succeeded',
              revision: 42,
              data: {
                items: [
                  {
                    id: '22222222-2222-4222-8222-222222222221',
                    type: 'item',
                    name: 'native_compass',
                    displayName: 'Native Compass',
                    state: 'valid',
                    ownership: 'generated',
                    updatedAt: '2026-08-17T05:00:00Z',
                    diagnostics: { error: 0, warning: 0, info: 0 }
                  }
                ],
                total: 1,
                page: 1,
                pageSize: 200,
                availableTypes: ['item', 'block', 'recipe', 'procedure']
              },
              diagnostics: []
            });
          }

          if (envelope.messageType === 'query' && envelope.operation === 'get_task') {
            return JSON.stringify({
              messageType: 'query_result',
              schemaVersion: '1.0',
              requestId: envelope.requestId,
              workspaceId,
              operation: 'get_task',
              status: 'succeeded',
              revision: 42,
              data: {
                task: {
                  id: envelope.payload.taskId,
                  kind: 'run_client',
                  state: 'succeeded',
                  cancellable: false,
                  progress: 1,
                  stage: { key: 'task.run_client.completed', fallback: 'Minecraft client started', args: {} },
                  startedAt: '2026-08-17T05:00:00Z',
                  completedAt: '2026-08-17T05:00:01Z',
                  diagnostics: { error: 0, warning: 0, info: 0 }
                },
                logs: [
                  {
                    sequence: 1,
                    timestamp: '2026-08-17T05:00:01Z',
                    level: 'info',
                    text: 'Minecraft client reached the readiness marker.'
                  }
                ],
                diagnostics: []
              },
              diagnostics: []
            });
          }

          if (envelope.messageType === 'command' && envelope.operation === 'run_client') {
            return JSON.stringify({
              messageType: 'command_result',
              schemaVersion: '1.0',
              requestId: envelope.requestId,
              workspaceId,
              operation: envelope.operation,
              status: 'committed',
              newRevision: 42,
              task: {
                id: '44444444-4444-4444-8444-444444444444',
                kind: 'run_client',
                state: 'running',
                cancellable: true,
                progress: 0.1,
                stage: { key: 'task.run_client.starting', fallback: 'Starting Minecraft client', args: {} },
                startedAt: '2026-08-17T05:00:00Z',
                completedAt: null,
                diagnostics: { error: 0, warning: 0, info: 0 }
              },
              diagnostics: []
            });
          }

          return JSON.stringify({
            messageType: 'command_result',
            schemaVersion: '1.0',
            requestId: envelope.requestId,
            workspaceId,
            operation: envelope.operation,
            status: 'committed',
            newRevision: 43,
            diagnostics: []
          });
        },
        onEvent: (listener: (raw: string) => void) => {
          eventListeners.add(listener);
          return () => {
            eventListeners.delete(listener);
          };
        }
      };
    });

    await page.goto('/');
    await page.waitForSelector('[data-testid="app-shell"]');

    // Should display the native workspace name and generator received from native host
    await expect(page.getByText('Native JCEF Workspace').first()).toBeVisible();
    await expect(page.getByText('Fabric 1.21.1').first()).toBeVisible();
    await expect(page.getByText('Native Compass').first()).toBeVisible();
    await expect(page.locator('[data-testid="scenario-switcher-trigger"]')).toHaveCount(0);
    await expect(page.locator('[data-testid="window-minimize-btn"]')).toHaveCount(0);
    await expect(page.locator('[data-testid="system-fallback-toggle-btn"]')).toBeDisabled();

    await page.click('[data-testid="titlebar-run-btn"]');
    await expect.poll(() => page.evaluate(() => window.sessionStorage.getItem('lastCoreCommand')))
      .toBe('run_client');
    await expect(page.locator('[data-testid="task-drawer"]')).toContainText('SUCCEEDED');
    await expect(page.locator('[data-testid="task-log-stream"]'))
      .toContainText('Minecraft client reached the readiness marker.');
  });

  test('routes native domain events to update diagnostics and workbench in real-time', async ({ page }) => {
    await page.addInitScript(() => {
      const workspaceId = '11111111-1111-4111-8111-111111111111';
      const eventListeners = new Set<(raw: string) => void>();

      window.__COPPERBENCH_EMIT_EVENT__ = (raw: string) => {
        eventListeners.forEach((l) => l(raw));
      };

      window.copperbenchHost = {
        workspaceId,
        invoke: async (rawJson: string) => {
          const envelope = JSON.parse(rawJson);
          if (envelope.messageType === 'handshake') {
            return JSON.stringify({
              messageType: 'handshake_result',
              requestId: envelope.requestId,
              status: 'compatible',
              selectedSchemaVersion: '1.0',
              coreSchemaVersions: ['1.0'],
              diagnostics: []
            });
          }
          if (envelope.messageType === 'query' && envelope.operation === 'get_workbench') {
            return JSON.stringify({
              messageType: 'query_result',
              schemaVersion: '1.0',
              requestId: envelope.requestId,
              workspaceId,
              operation: 'get_workbench',
              status: 'succeeded',
              revision: 1,
              data: {
                workspace: {
                  id: workspaceId,
                  name: 'Event Test Workspace',
                  kind: 'mod',
                  revision: 1,
                  dirty: false,
                  generator: {
                    id: 'fabric-1.21.1',
                    loader: 'fabric',
                    minecraftVersion: '1.21.1',
                    displayName: 'Fabric 1.21.1',
                    state: 'ready'
                  },
                  lock: { state: 'write_available', holder: null },
                  compatibility: { mode: 'native', unknownDataPreserved: true }
                },
                permission: {
                  profile: 'workspace',
                  canRequestElevation: true,
                  protectedOperationsAlwaysConfirm: true
                },
                connection: { core: 'connected', network: 'online', bridge: 'ready' },
                elementCounts: { total: 0, valid: 0, invalid: 0, draft: 0, unsupported: 0 },
                activeTasks: [],
                capabilities: [],
                recentElements: []
              },
              diagnostics: []
            });
          }
          if (envelope.messageType === 'query' && envelope.operation === 'list_mod_elements') {
            return JSON.stringify({
              messageType: 'query_result',
              schemaVersion: '1.0',
              requestId: envelope.requestId,
              workspaceId,
              operation: 'list_mod_elements',
              status: 'succeeded',
              revision: 1,
              data: { items: [], total: 0, page: 1, pageSize: 200, availableTypes: [] },
              diagnostics: []
            });
          }
          return JSON.stringify({ messageType: 'query_result', schemaVersion: '1.0', requestId: envelope.requestId, status: 'succeeded', diagnostics: [] });
        },
        onEvent: (listener: (raw: string) => void) => {
          eventListeners.add(listener);
          return () => { eventListeners.delete(listener); };
        }
      };
    });

    await page.goto('/');
    await page.waitForSelector('[data-testid="app-shell"]');
    await expect(page.getByText('Event Test Workspace').first()).toBeVisible();

    // Emit event from simulated host
    await page.evaluate(() => {
      const eventJson = JSON.stringify({
        messageType: 'event',
        schemaVersion: '1.0',
        sequence: 2001,
        revision: 99,
        workspaceId: '11111111-1111-4111-8111-111111111111',
        event: 'diagnostics_changed',
        payload: {
          diagnostics: [{
            code: 'NATIVE_HOST_EVENT_ALERT',
            severity: 'warning',
            message: { key: 'alert.native_event', fallback: 'Native event delivered safely' },
            path: null,
            recoverable: true,
            actions: []
          }]
        }
      });
      window.__COPPERBENCH_EMIT_EVENT__?.(eventJson);
    });

    // Check that status footer reflects the update
    await expect(page.locator('[data-testid="status-footer"]')).toBeVisible();
    await expect(page.getByText('Native event delivered safely').first()).toBeVisible();

    await page.evaluate(() => {
      const eventJson = JSON.stringify({
        messageType: 'event',
        schemaVersion: '1.0',
        sequence: 2002,
        revision: 100,
        workspaceId: '11111111-1111-4111-8111-111111111111',
        event: 'mod_element_created',
        payload: {
          element: {
            id: '22222222-2222-4222-8222-222222222299',
            type: 'block',
            name: 'event_lantern',
            displayName: 'Event Lantern',
            state: 'valid',
            ownership: 'generated',
            updatedAt: '2026-08-24T03:00:00Z',
            diagnostics: { error: 0, warning: 0, info: 0 }
          }
        }
      });
      window.__COPPERBENCH_EMIT_EVENT__?.(eventJson);
    });

    await expect(page.getByText('Event Lantern').first()).toBeVisible();
    await expect(page.locator('[data-testid="nav-elements"]')).toContainText('1');
  });

  test('does not let a delayed same-revision gap refresh overwrite a newer task event', async ({ page }) => {
    await page.addInitScript(() => {
      const workspaceId = '11111111-1111-4111-8111-111111111111';
      const taskId = '44444444-4444-4444-8444-444444444444';
      const eventListeners = new Set<(raw: string) => void>();
      let workbenchCalls = 0;
      let releaseGapRefresh: (() => void) | null = null;
      let taskState: 'running' | 'succeeded' = 'running';

      const task = () => ({
        id: taskId,
        kind: 'run_client',
        state: taskState,
        cancellable: taskState === 'running',
        progress: taskState === 'running' ? 0.6 : 1,
        stage: {
          key: taskState === 'running' ? 'task.run_client.running' : 'task.run_client.completed',
          fallback: taskState === 'running' ? 'Running client' : 'Client completed',
          args: {}
        },
        startedAt: '2026-08-27T10:00:00Z',
        completedAt: taskState === 'succeeded' ? '2026-08-27T10:00:01Z' : null,
        diagnostics: { error: 0, warning: 0, info: 0 }
      });

      const workbenchResult = (requestId: string, activeTask: ReturnType<typeof task> | null) => JSON.stringify({
        messageType: 'query_result',
        schemaVersion: '1.0',
        requestId,
        workspaceId,
        operation: 'get_workbench',
        status: 'succeeded',
        revision: 1,
        data: {
          workspace: {
            id: workspaceId,
            name: 'Gap Refresh Workspace',
            kind: 'mod',
            revision: 1,
            dirty: false,
            generator: {
              id: 'fabric-1.21.1', loader: 'fabric', minecraftVersion: '1.21.1',
              displayName: 'Fabric 1.21.1', state: 'ready'
            },
            lock: { state: 'write_available', holder: null },
            compatibility: { mode: 'native', unknownDataPreserved: true }
          },
          permission: { profile: 'workspace', canRequestElevation: true, protectedOperationsAlwaysConfirm: true },
          connection: { core: 'connected', network: 'online', bridge: 'ready' },
          elementCounts: { total: 0, valid: 0, invalid: 0, draft: 0, unsupported: 0 },
          activeTasks: activeTask ? [activeTask] : [],
          capabilities: [],
          recentElements: []
        },
        diagnostics: []
      });

      window.__COPPERBENCH_EMIT_EVENT__ = (raw: string) => eventListeners.forEach((listener) => listener(raw));
      window.copperbenchHost = {
        workspaceId,
        invoke: async (rawJson: string) => {
          const envelope = JSON.parse(rawJson);
          if (envelope.messageType === 'handshake') {
            return JSON.stringify({
              messageType: 'handshake_result', requestId: envelope.requestId, status: 'compatible',
              selectedSchemaVersion: '1.0', coreSchemaVersions: ['1.0'], diagnostics: []
            });
          }
          if (envelope.operation === 'get_workbench') {
            workbenchCalls += 1;
            window.sessionStorage.setItem('gapRefreshWorkbenchCalls', String(workbenchCalls));
            if (workbenchCalls === 2) {
              const stale = workbenchResult(envelope.requestId, task());
              window.sessionStorage.setItem('gapRefreshPending', 'true');
              return await new Promise<string>((resolve) => {
                releaseGapRefresh = () => resolve(stale);
              });
            }
            return workbenchResult(envelope.requestId, taskState === 'running' ? task() : null);
          }
          if (envelope.operation === 'list_mod_elements') {
            return JSON.stringify({
              messageType: 'query_result', schemaVersion: '1.0', requestId: envelope.requestId,
              workspaceId, operation: 'list_mod_elements', status: 'succeeded', revision: 1,
              data: { items: [], total: 0, page: 1, pageSize: 200, availableTypes: [] }, diagnostics: []
            });
          }
          if (envelope.operation === 'get_history') {
            return JSON.stringify({
              messageType: 'query_result', schemaVersion: '1.0', requestId: envelope.requestId,
              workspaceId, operation: 'get_history', status: 'succeeded', revision: 1,
              data: { recoveryPoints: [] }, diagnostics: []
            });
          }
          throw new Error(`Unexpected operation: ${String(envelope.operation)}`);
        },
        onEvent: (listener: (raw: string) => void) => {
          eventListeners.add(listener);
          return () => eventListeners.delete(listener);
        }
      };

      const testWindow = window as typeof window & {
        __COPPERBENCH_TEST_START_GAP__: () => void;
        __COPPERBENCH_TEST_COMPLETE_TASK__: () => void;
        __COPPERBENCH_TEST_RELEASE_GAP_REFRESH__: () => void;
      };
      const emitTask = (sequence: number, event: 'task_started' | 'task_progressed' | 'task_completed') => {
        window.__COPPERBENCH_EMIT_EVENT__?.(JSON.stringify({
          messageType: 'event', schemaVersion: '1.0', sequence, revision: 1, workspaceId,
          event, payload: { task: task() }
        }));
      };
      testWindow.__COPPERBENCH_TEST_START_GAP__ = () => {
        emitTask(1, 'task_started');
        emitTask(3, 'task_progressed');
      };
      testWindow.__COPPERBENCH_TEST_COMPLETE_TASK__ = () => {
        taskState = 'succeeded';
        emitTask(4, 'task_completed');
      };
      testWindow.__COPPERBENCH_TEST_RELEASE_GAP_REFRESH__ = () => releaseGapRefresh?.();
    });

    await page.goto('/');
    await page.waitForSelector('[data-testid="app-shell"]');
    await expect(page.getByText('Gap Refresh Workspace').first()).toBeVisible();

    await page.evaluate(() => {
      (window as typeof window & { __COPPERBENCH_TEST_START_GAP__: () => void }).__COPPERBENCH_TEST_START_GAP__();
    });
    await expect(page.locator('[data-task-id="44444444-4444-4444-8444-444444444444"]')).toHaveCount(1);
    await expect.poll(() => page.evaluate(() => window.sessionStorage.getItem('gapRefreshPending'))).toBe('true');

    await page.evaluate(() => {
      (window as typeof window & { __COPPERBENCH_TEST_COMPLETE_TASK__: () => void }).__COPPERBENCH_TEST_COMPLETE_TASK__();
    });
    await expect(page.locator('[data-task-id="44444444-4444-4444-8444-444444444444"]')).toHaveCount(0);

    await page.evaluate(() => {
      (window as typeof window & { __COPPERBENCH_TEST_RELEASE_GAP_REFRESH__: () => void })
        .__COPPERBENCH_TEST_RELEASE_GAP_REFRESH__();
    });
    await expect.poll(() => page.evaluate(() => window.sessionStorage.getItem('gapRefreshWorkbenchCalls'))).toBe('3');
    await expect(page.locator('[data-task-id="44444444-4444-4444-8444-444444444444"]')).toHaveCount(0);
  });

  test('deduplicates task logs and gives pushed completion precedence over a stale poll', async ({ page }) => {
    await page.goto('/');
    const outcome = await page.evaluate(async () => {
      const { JcefCoreBridge } = await import('/src/bridge/JcefCoreBridge.ts');
      const workspaceId = '11111111-1111-4111-8111-111111111111';
      const taskId = '44444444-4444-4444-8444-444444444444';
      let eventListener: ((raw: string) => void) | null = null;
      let pollCalls = 0;
      let releaseStalePoll: (() => void) | null = null;

      const runningTask = {
        id: taskId,
        kind: 'run_client',
        state: 'running',
        cancellable: true,
        progress: 0.5,
        stage: { key: 'task.run_client.running', fallback: 'Running client', args: {} },
        startedAt: '2026-08-27T10:00:00Z',
        completedAt: null,
        diagnostics: { error: 0, warning: 0, info: 0 }
      };
      const completedTask = {
        ...runningTask,
        state: 'succeeded',
        cancellable: false,
        progress: 1,
        stage: { key: 'task.run_client.completed', fallback: 'Client completed', args: {} },
        completedAt: '2026-08-27T10:00:01Z'
      };
      const logEntry = {
        sequence: 7,
        timestamp: '2026-08-27T10:00:00Z',
        level: 'info',
        text: 'shared poll and push log entry'
      };
      const queryResult = (envelope: Record<string, unknown>, operation: string, data: unknown) => JSON.stringify({
        messageType: 'query_result', schemaVersion: '1.0', requestId: envelope.requestId,
        workspaceId, operation, status: 'succeeded', revision: 1, data, diagnostics: []
      });
      const taskPollResult = (envelope: Record<string, unknown>) => queryResult(
        envelope,
        'get_task',
        { task: runningTask, logs: [logEntry], diagnostics: [] }
      );

      const host = {
        workspaceId,
        invoke: async (rawJson: string): Promise<string> => {
          const envelope = JSON.parse(rawJson) as Record<string, unknown>;
          if (envelope.messageType === 'handshake') {
            return JSON.stringify({
              messageType: 'handshake_result', requestId: envelope.requestId, status: 'compatible',
              selectedSchemaVersion: '1.0', coreSchemaVersions: ['1.0'], diagnostics: []
            });
          }
          if (envelope.operation === 'get_workbench') {
            return queryResult(envelope, 'get_workbench', {
              workspace: {
                id: workspaceId, name: 'Task Poll Workspace', kind: 'mod', revision: 1, dirty: false,
                generator: {
                  id: 'fabric-1.21.1', loader: 'fabric', minecraftVersion: '1.21.1',
                  displayName: 'Fabric 1.21.1', state: 'ready'
                },
                lock: { state: 'write_available', holder: null },
                compatibility: { mode: 'native', unknownDataPreserved: true }
              },
              permission: { profile: 'workspace', canRequestElevation: true, protectedOperationsAlwaysConfirm: true },
              connection: { core: 'connected', network: 'online', bridge: 'ready' },
              elementCounts: { total: 0, valid: 0, invalid: 0, draft: 0, unsupported: 0 },
              activeTasks: [], capabilities: [], recentElements: []
            });
          }
          if (envelope.operation === 'list_mod_elements') {
            return queryResult(envelope, 'list_mod_elements', {
              items: [], total: 0, page: 1, pageSize: 200, availableTypes: []
            });
          }
          if (envelope.operation === 'get_history') {
            return queryResult(envelope, 'get_history', { recoveryPoints: [] });
          }
          if (envelope.operation === 'run_client') {
            return JSON.stringify({
              messageType: 'command_result', schemaVersion: '1.0', requestId: envelope.requestId,
              workspaceId, operation: 'run_client', status: 'committed', newRevision: 1,
              task: runningTask, diagnostics: []
            });
          }
          if (envelope.operation === 'get_task') {
            pollCalls += 1;
            if (pollCalls === 1) return taskPollResult(envelope);
            if (pollCalls === 2) {
              const stale = taskPollResult(envelope);
              return await new Promise<string>((resolve) => {
                releaseStalePoll = () => resolve(stale);
              });
            }
            return taskPollResult(envelope);
          }
          throw new Error(`Unexpected operation: ${String(envelope.operation)}`);
        },
        onEvent: (listener: (raw: string) => void) => {
          eventListener = listener;
          return () => { eventListener = null; };
        }
      };
      const bridge = new JcefCoreBridge(host);
      await bridge.negotiateHandshake({
        messageType: 'handshake',
        requestId: '00000000-0000-4000-8000-000000000001',
        supportedSchemaVersions: ['1.0'],
        client: { id: 'jcef-race-test', version: '1.0' }
      });
      await bridge.sendCommand({
        messageType: 'command', schemaVersion: '1.0',
        requestId: '00000000-0000-4000-8000-000000000002', workspaceId,
        operation: 'run_client', expectedRevision: 1, payload: {}
      });

      const waitFor = async (predicate: () => boolean) => {
        const deadline = Date.now() + 3000;
        while (!predicate()) {
          if (Date.now() >= deadline) throw new Error('Timed out waiting for task poll state');
          await new Promise((resolve) => setTimeout(resolve, 10));
        }
      };
      await waitFor(() => pollCalls >= 1 && (bridge.getState().taskLogs[taskId]?.length ?? 0) === 1);

      eventListener?.(JSON.stringify({
        messageType: 'event', schemaVersion: '1.0', eventId: '00000000-0000-4000-8000-000000000003',
        workspaceId, revision: 1, sequence: 1, occurredAt: '2026-08-27T10:00:00Z',
        event: 'task_log_appended', causedByRequestId: null, payload: { taskId, entries: [logEntry] }
      }));
      const logCountAfterDuplicatePush = bridge.getState().taskLogs[taskId]?.length ?? 0;

      await waitFor(() => pollCalls >= 2 && releaseStalePoll !== null);
      eventListener?.(JSON.stringify({
        messageType: 'event', schemaVersion: '1.0', eventId: '00000000-0000-4000-8000-000000000004',
        workspaceId, revision: 1, sequence: 2, occurredAt: '2026-08-27T10:00:01Z',
        event: 'task_completed', causedByRequestId: null, payload: { task: completedTask }
      }));
      releaseStalePoll?.();
      await new Promise((resolve) => setTimeout(resolve, 50));

      const finalTaskState = bridge.getState().tasks[taskId]?.state ?? null;
      bridge.dispose();
      return { logCountAfterDuplicatePush, finalTaskState, pollCalls };
    });

    expect(outcome.logCountAfterDuplicatePush).toBe(1);
    expect(outcome.finalTaskState).toBe('succeeded');
    expect(outcome.pollCalls).toBe(2);
  });

  test('detects window.cefQuery and __COPPERBENCH_WORKSPACE_ID__ for query routing and error handling', async ({ page }) => {
    await page.addInitScript(() => {
      const workspaceId = '33333333-3333-4333-8333-333333333333';
      window.__COPPERBENCH_WORKSPACE_ID__ = workspaceId;
      window.__COPPERBENCH_QUERY_PREFIX__ = 'copperbench:bridge:';

      window.cefQuery = (options: {
        request: string;
        persistent?: boolean;
        onSuccess: (response: string) => void;
        onFailure: (errorCode: number, errorMessage: string) => void;
      }) => {
        setTimeout(() => {
          const prefix = 'copperbench:bridge:';
          if (!options.request.startsWith(prefix)) {
            options.onFailure(404, 'Unknown query prefix');
            return;
          }

          const envelope = JSON.parse(options.request.substring(prefix.length));
          if (envelope.messageType === 'handshake') {
            options.onSuccess(
              JSON.stringify({
                messageType: 'handshake_result',
                requestId: envelope.requestId,
                status: 'compatible',
                selectedSchemaVersion: '1.0',
                coreSchemaVersions: ['1.0'],
                diagnostics: []
              })
            );
            return;
          }

          if (envelope.messageType === 'query' && envelope.operation === 'get_workbench') {
            options.onSuccess(
              JSON.stringify({
                messageType: 'query_result',
                schemaVersion: '1.0',
                requestId: envelope.requestId,
                workspaceId,
                operation: 'get_workbench',
                status: 'succeeded',
                revision: 5,
                data: {
                  workspace: {
                    id: workspaceId,
                    name: 'CEF Query Workspace',
                    kind: 'mod',
                    revision: 5,
                    dirty: false,
                    generator: {
                      id: 'fabric-1.21.1',
                      loader: 'fabric',
                      minecraftVersion: '1.21.1',
                      displayName: 'Fabric 1.21.1',
                      state: 'ready'
                    },
                    lock: { state: 'write_available', holder: null },
                    compatibility: { mode: 'native', unknownDataPreserved: true }
                  },
                  permission: {
                    profile: 'workspace',
                    canRequestElevation: true,
                    protectedOperationsAlwaysConfirm: true
                  },
                  connection: { core: 'connected', network: 'online', bridge: 'ready' },
                  elementCounts: { total: 0, valid: 0, invalid: 0, draft: 0, unsupported: 0 },
                  activeTasks: [],
                  capabilities: [],
                  recentElements: []
                },
                diagnostics: []
              })
            );
            return;
          }

          if (envelope.messageType === 'query' && envelope.operation === 'list_mod_elements') {
            options.onSuccess(
              JSON.stringify({
                messageType: 'query_result',
                schemaVersion: '1.0',
                requestId: envelope.requestId,
                workspaceId,
                operation: 'list_mod_elements',
                status: 'succeeded',
                revision: 5,
                data: { items: [], total: 0, page: 1, pageSize: 200, availableTypes: [] },
                diagnostics: []
              })
            );
            return;
          }

          options.onSuccess(
            JSON.stringify({
              messageType: 'query_result',
              schemaVersion: '1.0',
              requestId: envelope.requestId,
              status: 'succeeded',
              diagnostics: []
            })
          );
        }, 0);
      };
    });

    await page.goto('/');
    await page.waitForSelector('[data-testid="app-shell"]');
    await expect(page.getByText('CEF Query Workspace').first()).toBeVisible();
  });
});
