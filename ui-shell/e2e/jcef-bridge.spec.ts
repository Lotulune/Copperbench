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
    await expect.poll(() => page.evaluate(() => window.sessionStorage.getItem('lastCoreOperation')))
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
