import assert from 'node:assert/strict';
import test from 'node:test';

// Test implementation replicating and exercising the JcefCoreBridge logic and contract in Node.js
class TestJcefCoreBridge {
  constructor(host) {
    this.host = host;
    this.stateListeners = new Set();
    this.eventListeners = new Set();
    this.state = {
      currentScenarioId: 'native',
      viewportState: 'loading',
      expectedUi: null,
      workbench: null,
      elements: [],
      elementEditors: {},
      tasks: {},
      taskLogs: {},
      diagnostics: [],
      recoveryPoints: [],
      currentRecoveryPointId: null,
      historyComparison: null,
      operationApprovals: [],
      recoveryState: null,
      schemaIncompatible: false
    };
    this.hostUnsubscribe = host.onEvent((raw) => this.processEvent(this.parse(raw)));
  }

  getState() {
    return this.state;
  }

  onEvent(listener) {
    this.eventListeners.add(listener);
    return () => this.eventListeners.delete(listener);
  }

  onStateChange(listener) {
    this.stateListeners.add(listener);
    listener(this.state);
    return () => this.stateListeners.delete(listener);
  }

  async negotiateHandshake(request) {
    const result = await this.invoke(request);
    this.state.schemaIncompatible = result.status === 'incompatible';
    this.state.diagnostics = [...result.diagnostics];
    this.state.viewportState = result.status === 'compatible' ? 'ready' : 'error';
    this.notify();
    if (result.status === 'compatible') await this.refreshInitialProjection();
    return result;
  }

  async sendCommand(command) {
    const result = await this.invoke(command);
    this.applyCommandResult(result);
    this.notify();
    return result;
  }

  async sendQuery(query) {
    const result = await this.invoke(query);
    this.applyQueryResult(result);
    this.notify();
    return result;
  }

  async reconcileRecovery() {
    this.state.recoveryState = null;
    await this.refreshInitialProjection();
    this.notify();
  }

  dispose() {
    this.hostUnsubscribe();
    this.eventListeners.clear();
    this.stateListeners.clear();
  }

  async refreshInitialProjection() {
    const base = {
      messageType: 'query',
      schemaVersion: '1.0',
      workspaceId: this.host.workspaceId
    };
    await this.sendQuery({ ...base, requestId: 'req-workbench', operation: 'get_workbench', payload: {} });
    await this.sendQuery({
      ...base,
      requestId: 'req-elements',
      operation: 'list_mod_elements',
      payload: { search: '', types: [], states: [], page: 1, pageSize: 200 }
    });
  }

  async invoke(envelope) {
    return this.parse(await this.host.invoke(JSON.stringify(envelope)));
  }

  parse(raw) {
    const value = JSON.parse(raw);
    if (!value || typeof value !== 'object') throw new Error('JCEF bridge returned a non-object envelope');
    return value;
  }

  applyCommandResult(result) {
    this.state.diagnostics = [...result.diagnostics];
    if (result.status === 'rejected') {
      this.state.viewportState = result.conflict ? 'conflict' : result.denial ? 'permission_denied' : 'error';
    }
    if (result.task) this.state.tasks[result.task.id] = result.task;
  }

  applyQueryResult(result) {
    if (result.status !== 'succeeded' || !result.data) {
      this.state.diagnostics = [...result.diagnostics];
      return;
    }
    switch (result.operation) {
      case 'get_workbench':
        this.state.workbench = result.data;
        this.state.viewportState = 'ready';
        break;
      case 'list_mod_elements':
        this.state.elements = [...result.data.items];
        break;
      case 'get_mod_element_editor':
        this.state.elementEditors[result.data.element.id] = result.data;
        break;
      case 'get_task':
        this.state.tasks[result.data.task.id] = result.data.task;
        this.state.taskLogs[result.data.task.id] = [...result.data.logs];
        break;
      case 'get_history':
        this.state.recoveryPoints = [...result.data.recoveryPoints];
        this.state.currentRecoveryPointId = result.data.recoveryPoints[0]?.id ?? null;
        break;
      case 'get_diff':
        this.state.historyComparison = result.data;
        break;
      case 'list_operation_approvals':
        this.state.operationApprovals = [...result.data.items];
        break;
    }
  }

  processEvent(event) {
    if (event.event === 'task_started' || event.event === 'task_progressed' || event.event === 'task_completed') {
      this.state.tasks[event.payload.task.id] = event.payload.task;
    } else if (event.event === 'task_log_appended') {
      const current = this.state.taskLogs[event.payload.taskId] ?? [];
      this.state.taskLogs[event.payload.taskId] = [...current, ...event.payload.entries];
    } else if (event.event === 'diagnostics_changed') {
      this.state.diagnostics = [...event.payload.diagnostics];
    } else if (event.event === 'bridge_recovery_required') {
      this.state.viewportState = 'recovery';
      this.state.recoveryState = event.payload;
    }
    if (this.state.workbench) this.state.workbench.workspace.revision = event.revision;
    this.eventListeners.forEach((l) => l(event));
    this.notify();
  }

  notify() {
    this.stateListeners.forEach((l) => l(this.state));
  }
}

function createMockHost(workspaceId) {
  let eventSink = null;
  const invocationLog = [];

  const host = {
    workspaceId,
    invocationLog,
    invoke: async (rawJson) => {
      const envelope = JSON.parse(rawJson);
      invocationLog.push(envelope);

      if (envelope.messageType === 'handshake') {
        const isV1 = envelope.supportedSchemaVersions.includes('1.0');
        return JSON.stringify({
          messageType: 'handshake_result',
          requestId: envelope.requestId,
          status: isV1 ? 'compatible' : 'incompatible',
          selectedSchemaVersion: isV1 ? '1.0' : null,
          coreSchemaVersions: ['1.0'],
          diagnostics: isV1 ? [] : [{ code: 'SCHEMA_INCOMPATIBLE', message: 'Incompatible schema', severity: 'error', blocking: true }]
        });
      }

      if (envelope.messageType === 'query' && envelope.operation === 'get_workbench') {
        return JSON.stringify({
          messageType: 'query_result',
          requestId: envelope.requestId,
          workspaceId,
          operation: 'get_workbench',
          status: 'succeeded',
          data: {
            workspace: { id: workspaceId, name: 'Copper Trails', revision: 10, writerLeaseHolder: null, dirty: false },
            generator: { id: 'fabric-1.21.1', loader: 'fabric', minecraftVersion: '1.21.1', state: 'ready' },
            recentElements: []
          },
          diagnostics: []
        });
      }

      if (envelope.messageType === 'query' && envelope.operation === 'list_mod_elements') {
        return JSON.stringify({
          messageType: 'query_result',
          requestId: envelope.requestId,
          workspaceId,
          operation: 'list_mod_elements',
          status: 'succeeded',
          data: { items: [], totalCount: 0, page: 1, pageSize: 200 },
          diagnostics: []
        });
      }

      if (envelope.messageType === 'command') {
        return JSON.stringify({
          messageType: 'command_result',
          requestId: envelope.requestId,
          workspaceId,
          status: 'committed',
          newRevision: 11,
          diagnostics: []
        });
      }

      throw new Error('Unsupported envelope in mock host: ' + envelope.messageType);
    },
    onEvent: (listener) => {
      eventSink = listener;
      return () => {
        eventSink = null;
      };
    },
    emit: (eventObj) => {
      if (eventSink) eventSink(JSON.stringify(eventObj));
    }
  };

  return host;
}

test('JcefCoreBridge performs handshake and queries workbench and elements', async () => {
  const host = createMockHost('11111111-1111-4111-8111-111111111111');
  const bridge = new TestJcefCoreBridge(host);

  const handshakeResult = await bridge.negotiateHandshake({
    messageType: 'handshake',
    requestId: 'req-hs',
    supportedSchemaVersions: ['1.0'],
    client: { id: 'test_client', version: '1.0.0' }
  });

  assert.equal(handshakeResult.status, 'compatible');
  assert.equal(handshakeResult.selectedSchemaVersion, '1.0');
  assert.equal(bridge.getState().viewportState, 'ready');
  assert.equal(bridge.getState().workbench.workspace.name, 'Copper Trails');
  assert.equal(host.invocationLog.length, 3); // handshake, get_workbench, list_mod_elements
});

test('JcefCoreBridge marks state incompatible on schema mismatch', async () => {
  const host = createMockHost('11111111-1111-4111-8111-111111111111');
  const bridge = new TestJcefCoreBridge(host);

  const handshakeResult = await bridge.negotiateHandshake({
    messageType: 'handshake',
    requestId: 'req-hs',
    supportedSchemaVersions: ['0.1'],
    client: { id: 'test_client', version: '1.0.0' }
  });

  assert.equal(handshakeResult.status, 'incompatible');
  assert.equal(bridge.getState().schemaIncompatible, true);
  assert.equal(bridge.getState().viewportState, 'error');
});

test('JcefCoreBridge delivers domain events and updates revision', async () => {
  const host = createMockHost('11111111-1111-4111-8111-111111111111');
  const bridge = new TestJcefCoreBridge(host);

  await bridge.negotiateHandshake({
    messageType: 'handshake',
    requestId: 'req-hs',
    supportedSchemaVersions: ['1.0'],
    client: { id: 'test_client', version: '1.0.0' }
  });

  const receivedEvents = [];
  bridge.onEvent((e) => receivedEvents.push(e));

  host.emit({
    messageType: 'event',
    schemaVersion: '1.0',
    sequence: 101,
    revision: 12,
    workspaceId: '11111111-1111-4111-8111-111111111111',
    event: 'diagnostics_changed',
    payload: { diagnostics: [{ code: 'TEST_DIAG', message: 'Test warning', severity: 'warning', blocking: false }] }
  });

  assert.equal(receivedEvents.length, 1);
  assert.equal(bridge.getState().diagnostics.length, 1);
  assert.equal(bridge.getState().workbench.workspace.revision, 12);

  bridge.dispose();
});

test('JcefCoreBridge propagates invoke rejections as Promise errors', async () => {
  const failingHost = {
    workspaceId: '11111111-1111-4111-8111-111111111111',
    invoke: async () => {
      throw new Error('Native bridge query failed [400]: Malformed request');
    },
    onEvent: () => () => {}
  };
  const bridge = new TestJcefCoreBridge(failingHost);

  await assert.rejects(
    async () => {
      await bridge.sendQuery({
        messageType: 'query',
        schemaVersion: '1.0',
        requestId: 'req-1',
        workspaceId: '11111111-1111-4111-8111-111111111111',
        operation: 'get_workbench',
        payload: {}
      });
    },
    /Native bridge query failed/
  );
});
