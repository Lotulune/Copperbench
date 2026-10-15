import assert from 'node:assert/strict';
import test from 'node:test';
import { randomUUID } from 'node:crypto';
import { loadSsrTestModules } from './helpers/vite-ssr-loader.mjs';

// Load the production TypeScript and its real imports through the existing Vite
// toolchain. There is no copied bridge implementation or browser server to test.
const [{ JcefCoreBridge, taskLogWatermark }] = await loadSsrTestModules(['/src/bridge/JcefCoreBridge.ts']);

const workspaceId = '11111111-1111-4111-8111-111111111111';
const taskId = '44444444-4444-4444-8444-444444444444';
const otherTaskId = '55555555-5555-4555-8555-555555555555';
const revision = 10;

test('preview and copy-upgrade requests work on desktop origins without crypto.randomUUID', async (t) => {
  const cryptoDescriptor = Object.getOwnPropertyDescriptor(globalThis, 'crypto');
  Object.defineProperty(globalThis, 'crypto', { value: {}, configurable: true });
  t.after(() => {
    if (cryptoDescriptor) Object.defineProperty(globalThis, 'crypto', cryptoDescriptor);
    else Reflect.deleteProperty(globalThis, 'crypto');
  });
  const calls = [];
  const bridge = new JcefCoreBridge({ workspaceId, onEvent() { return () => {}; }, invoke: async raw => {
    const request = JSON.parse(raw); calls.push(request);
    return JSON.stringify({ contract: request.messageType === 'preview' ? 'preview_targets' : 'version_copy_upgrade',
      schemaVersion: '1', selectedVersion: request.request.supportedVersions[0], status: 'succeeded',
      requestId: request.requestId, workspaceId, diagnostics: [] });
  } });
  t.after(() => bridge.dispose());
  await bridge.sendPreview(workspaceId, { supportedVersions: ['1'], operation: 'catalog' });
  await bridge.previewVersionCopyUpgrade(workspaceId, 'fabric-26.3');
  await bridge.versionCopyUpgradeV2(workspaceId, { operation: 'preview', targetGeneratorId: 'fabric-26.3', outputName: 'copy' });
  assert.deepEqual(calls.map(request => [request.messageType, request.request.supportedVersions]), [
    ['preview', ['1']], ['version_copy_upgrade', ['1']], ['version_copy_upgrade', ['2']]
  ]);
  for (const request of calls) {
    assert.equal(request.workspaceId, workspaceId);
    assert.match(request.requestId, /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/);
  }
  assert.equal(new Set(calls.map(request => request.requestId)).size, calls.length);
});

test('editor schema 1.1 is explicit and a legacy response cannot populate the rich editor', async () => {
  let downgrade = false;
  const calls = [];
  const bridge = new JcefCoreBridge({ workspaceId, onEvent() { return () => {}; }, invoke: async raw => {
    const request = JSON.parse(raw); calls.push(request);
    return JSON.stringify({ messageType: 'query_result', schemaVersion: downgrade ? '1.0' : request.schemaVersion,
      requestId: request.requestId, workspaceId, operation: request.operation, status: 'succeeded', revision,
      data: { element: { id: 'element' }, configuration: {}, sourceManagement: {} }, diagnostics: [] });
  } });
  const query = { messageType: 'query', schemaVersion: '1.1', requestId: randomUUID(), workspaceId,
    operation: 'get_mod_element_editor', payload: { elementId: 'element' } };
  assert.equal((await bridge.sendQuery(query)).schemaVersion, '1.1');
  assert.equal(calls[0].schemaVersion, '1.1');
  downgrade = true;
  await assert.rejects(bridge.sendQuery({ ...query, requestId: randomUUID() }), /requested editor schema/);
});

test('acceptance diagnostics bind the nested Core workspace and never mutate the UI projection', async (t) => {
  const calls = [];
  let mismatch = '';
  const summary = { schemaVersion: '1', taskId, state: 'insufficient', required: 6, minimumTests: 6,
    uniqueExecuted: 5, shortfall: 1, duplicates: [], missingCases: [] };
  const bridge = new JcefCoreBridge({ workspaceId, onEvent() { return () => {}; }, invoke: async raw => {
    const request = JSON.parse(raw); calls.push(request);
    return JSON.stringify({ contract: 'required_cases', schemaVersion: '1', selectedVersion: '1', status: 'succeeded',
      requestId: mismatch === 'request' ? otherTaskId : request.requestId,
      core: { workspaceId: mismatch === 'workspace' ? otherTaskId : workspaceId },
      coverageDiagnostics: { ...summary, taskId: mismatch === 'task' ? otherTaskId : taskId } });
  } });
  t.after(() => bridge.dispose());
  const before = structuredClone(bridge.getState());
  assert.deepEqual(await bridge.readAcceptanceDiagnostics(workspaceId, taskId), summary);
  assert.deepEqual(bridge.getState(), before);
  assert.deepEqual(calls[0].request, { supportedVersions: ['1'], diagnosticVersions: ['1'], operation: 'get', payload: { taskId } });
  for (mismatch of ['request', 'workspace', 'task'])
    await assert.rejects(() => bridge.readAcceptanceDiagnostics(workspaceId, taskId));
});

const handshake = (supportedSchemaVersions = ['1.0']) => ({
  messageType: 'handshake', requestId: randomUUID(), supportedSchemaVersions,
  client: { id: 'production-bridge-test', version: '1.0' }
});
const query = (operation, payload = {}) => ({
  messageType: 'query', schemaVersion: '1.0', requestId: randomUUID(), workspaceId, operation, payload
});
const task = (state = 'running', id = taskId) => ({
  id, kind: 'run_datagen', state, cancellable: state === 'running',
  progress: state === 'running' ? 0.5 : 1,
  stage: { key: `task.run_datagen.${state}`, fallback: state, args: {} },
  startedAt: '2026-10-04T10:00:00Z', completedAt: state === 'running' ? null : '2026-10-04T10:00:01Z',
  diagnostics: { error: state === 'failed' ? 1 : 0, warning: 0, info: 0 }
});
const log = (sequence) => ({
  sequence, timestamp: '2026-10-04T10:00:00Z', level: 'info', text: `Task log ${sequence}`
});
const failure = (id = taskId) => ({
  code: 'TASK_BUILD_FAILED', severity: 'error', blocking: true,
  message: { key: 'task.build.failed', fallback: 'Build failed', args: {} },
  actions: [{ id: 'logs', kind: 'open_logs', target: id, payload: { taskId: id } }]
});
const projection = (summary = task(), logs = [], diagnostics = []) => ({ task: summary, logs, diagnostics });
const deferred = () => {
  let resolve;
  let reject;
  const promise = new Promise((res, rej) => { resolve = res; reject = rej; });
  return { promise, resolve, reject };
};
const flush = () => new Promise((resolve) => setImmediate(resolve));

test('incremental task reads retain a missing log interval until it has been filled', () => {
  assert.equal(taskLogWatermark([log(1), log(3)]), 1);
  assert.equal(taskLogWatermark([log(3)]), 0);
  assert.equal(taskLogWatermark([log(3), log(2), log(1), log(2)]), 3);
});

async function fixture(t, { initialize = true } = {}) {
  let eventSink;
  let sequence = 0;
  let readTask = () => projection();
  let history = { currentRevision: revision, currentRecoveryPointId: null, recoveryPoints: [] };
  const invocations = [];
  const host = {
    workspaceId,
    invoke: async (raw) => {
      const envelope = JSON.parse(raw);
      invocations.push(envelope);
      if (envelope.messageType === 'handshake') {
        const compatible = envelope.supportedSchemaVersions.includes('1.0');
        return JSON.stringify({
          messageType: 'handshake_result', requestId: envelope.requestId,
          status: compatible ? 'compatible' : 'incompatible', selectedSchemaVersion: compatible ? '1.0' : null,
          coreSchemaVersions: ['1.0'], diagnostics: []
        });
      }
      if (envelope.messageType === 'command') {
        return JSON.stringify({
          messageType: 'command_result', schemaVersion: '1.0', requestId: envelope.requestId,
          workspaceId, operation: envelope.operation, status: 'committed', newRevision: revision,
          task: task(), diagnostics: []
        });
      }
      let data;
      switch (envelope.operation) {
        case 'get_workbench':
          data = {
            workspace: { id: workspaceId, name: 'Copper Trails', revision, dirty: false },
            recentElements: [], recentTasks: [], activeTasks: [],
            elementCounts: { total: 0, valid: 0, invalid: 0, draft: 0, unsupported: 0 }
          };
          break;
        case 'list_mod_elements': data = { items: [], nextCursor: null }; break;
        case 'get_history': data = history; break;
        case 'get_task': data = await readTask(envelope); break;
        default: throw new Error(`Unexpected operation: ${envelope.operation}`);
      }
      return JSON.stringify({
        messageType: 'query_result', schemaVersion: '1.0', requestId: envelope.requestId,
        workspaceId, operation: envelope.operation, status: 'succeeded', revision, data, diagnostics: []
      });
    },
    onEvent: (listener) => { eventSink = listener; return () => { eventSink = null; }; }
  };
  const bridge = new JcefCoreBridge(host);
  t.after(() => bridge.dispose());
  if (initialize) await bridge.negotiateHandshake(handshake());
  return {
    bridge, host, invocations,
    readTask: (reader) => { readTask = reader; },
    history: (value) => { history = value; },
    emit: (event, payload) => eventSink?.(JSON.stringify({
      messageType: 'event', schemaVersion: '1.0', eventId: randomUUID(), workspaceId,
      revision, sequence: ++sequence, occurredAt: '2026-10-04T10:00:00Z', event, payload
    })),
    taskQueries: () => invocations.filter((envelope) => envelope.operation === 'get_task')
  };
}

test('production bridge negotiates and loads workbench, elements, and history', async (t) => {
  const f = await fixture(t);
  assert.equal(f.bridge.getState().viewportState, 'ready');
  assert.equal(f.bridge.getState().workbench.workspace.name, 'Copper Trails');
  assert.deepEqual(f.invocations.map((request) => request.operation ?? request.messageType),
    ['handshake', 'get_workbench', 'list_mod_elements', 'get_history']);
});

for (const [operation, command] of [['run', 'run_gametest'], ['export', 'export_workspace']]) {
  test(`nested preview acceptance ${operation} updates tasks and polls completion`, async (t) => {
    const f = await fixture(t);
    const invoke = f.host.invoke;
    const started = { ...task(), kind: operation === 'run' ? 'run_gametest' : 'export' };
    const completed = { ...task('succeeded'), kind: started.kind };
    f.host.invoke = async raw => {
      const request = JSON.parse(raw);
      if (request.messageType !== 'preview') return invoke(raw);
      return JSON.stringify({ contract: 'preview_targets', schemaVersion: '1', selectedVersion: '1',
        requestId: request.requestId, workspaceId, status: 'accepted', diagnostics: [],
        acceptance: { contract: 'required_cases', schemaVersion: '1', selectedVersion: '1', requestId: request.requestId,
          status: 'accepted', core: { messageType: 'command_result', schemaVersion: '1.0', requestId: request.requestId,
            workspaceId, operation: command, status: 'accepted', newRevision: revision, task: started, diagnostics: [] } } });
    };
    f.readTask(() => projection(completed, [log(1)]));
    await f.bridge.sendPreview(workspaceId, { supportedVersions: ['1'], operation: 'acceptance', generatorId: 'fabric-26.3',
      payload: { supportedVersions: ['1'], operation, expectedRevision: revision, payload: {} } });
    assert.deepEqual(f.bridge.getState().tasks[taskId], started);
    await new Promise(resolve => setTimeout(resolve, 600));
    assert.deepEqual(f.bridge.getState().tasks[taskId], completed);
    assert.equal(f.bridge.getState().taskLogs[taskId][0].sequence, 1);
    assert.equal(f.taskQueries().length, 1);
  });
}

test('nested preview acceptance rejects mismatched receipts without changing state', async (t) => {
  const f = await fixture(t);
  let mismatch = '';
  f.host.invoke = async raw => {
    const request = JSON.parse(raw);
    return JSON.stringify({ contract: 'preview_targets', schemaVersion: '1', selectedVersion: '1',
      requestId: request.requestId, workspaceId, status: 'accepted', diagnostics: [],
      acceptance: { contract: 'required_cases', schemaVersion: '1', selectedVersion: mismatch === 'version' ? '2' : '1',
        requestId: mismatch === 'request' ? otherTaskId : request.requestId,
        status: 'accepted', core: { messageType: 'command_result', schemaVersion: '1.0', requestId: request.requestId,
          workspaceId: mismatch === 'workspace' ? otherTaskId : workspaceId,
          operation: mismatch === 'operation' ? 'run_client' : 'run_gametest', status: 'accepted',
          newRevision: mismatch === 'revision' ? -1 : revision, task: task(), diagnostics: [] } } });
  };
  const before = structuredClone(f.bridge.getState());
  for (mismatch of ['version', 'request', 'workspace', 'operation', 'revision']) {
    await assert.rejects(f.bridge.sendPreview(workspaceId, { supportedVersions: ['1'], operation: 'acceptance', generatorId: 'fabric-26.3',
      payload: { supportedVersions: ['1'], operation: 'run', expectedRevision: revision, payload: {} } }));
    assert.deepEqual(f.bridge.getState(), before);
  }
});

test('incompatible schema stops initial projection queries', async (t) => {
  const f = await fixture(t, { initialize: false });
  const result = await f.bridge.negotiateHandshake(handshake(['0.1']));
  assert.equal(result.status, 'incompatible');
  assert.equal(f.bridge.getState().schemaIncompatible, true);
  assert.equal(f.bridge.getState().viewportState, 'error');
  assert.equal(f.invocations.length, 1);
});

test('history refresh clears a stale recovery point and notifies consumers', async (t) => {
  const f = await fixture(t);
  f.bridge.getState().currentRecoveryPointId = 'stale-current';
  const observed = [];
  f.bridge.onStateChange((state) => observed.push(state.currentRecoveryPointId));
  f.history({ currentRevision: revision, currentRecoveryPointId: null, recoveryPoints: [{ id: 'baseline' }] });
  await f.bridge.sendQuery(query('get_history'));
  assert.equal(f.bridge.getState().recoveryPoints[0].id, 'baseline');
  assert.equal(observed.at(-1), null);
});

test('transport rejections remain Promise errors', async (t) => {
  const f = await fixture(t);
  f.readTask(() => { throw new Error('Disconnected test host'); });
  await assert.rejects(f.bridge.sendQuery(query('get_task', { taskId })), /Disconnected test host/);
});

test('public task query cannot undo pushed completion and still fills missing logs and returns source', async (t) => {
  const f = await fixture(t);
  f.emit('task_started', { task: task() });
  const pending = deferred();
  f.readTask(() => pending.promise);
  const read = f.bridge.sendQuery(query('get_task', { taskId, sourcePath: 'src/Example.java' }));
  f.emit('task_log_appended', { taskId, entries: [log(2)] });
  f.emit('task_completed', { task: task('succeeded') });
  const source = { path: 'src/Example.java', language: 'java', content: 'class Example {}', size: 16, line: 1 };
  pending.resolve({ ...projection(task(), [log(2), log(1)]), source });
  const result = await read;
  const state = f.bridge.getState();
  assert.equal(state.tasks[taskId].state, 'succeeded');
  assert.equal(state.tasks[taskId].cancellable, false);
  assert.deepEqual(state.workbench.activeTasks, []);
  assert.deepEqual(state.taskLogs[taskId].map((entry) => entry.sequence), [1, 2]);
  assert.deepEqual(result.data.source, source);
  assert.equal(result.data.task.state, 'running'); // The request's raw result is intentionally preserved.
});

const compilerFailure = () => ({
  ...failure(), code: 'JAVA_COMPILE_ERROR', path: '/src/main/java/com/example/acceptance/WorkflowNote.java',
  message: { key: 'diagnostic.java_compile_error', fallback: '{message}', args: { message: 'Line 4: 非法的表达式开始' } }
});
const sourceFailure = () => ({
  code: 'TASK_SOURCE_NOT_FOUND', severity: 'error', recoverable: true, path: '/sourcePath', actions: [],
  message: { key: 'diagnostic.task_source_not_found', fallback: 'The requested task source preview is unavailable.',
    args: { detail: 'Raw preview failure 中文' } }
});
const capturedSource = () => {
  const content = 'package com.example.acceptance;\n\npublic final class WorkflowNote {\n    public static final String NOTE = ;\n}\n';
  return { path: '/src/main/java/com/example/acceptance/WorkflowNote.java', language: 'java',
    content, size: Buffer.byteLength(content, 'utf8'), line: 4 };
};
async function observeCompilerFailure(f) {
  f.readTask(() => projection(task('failed'), [log(1)], [compilerFailure()]));
  await f.bridge.sendQuery(query('get_task', { taskId }));
  f.emit('diagnostics_changed', { counts: { error: 1, warning: 0, info: 0 }, diagnostics: [compilerFailure()] });
}

test('a rejected source subquery preserves terminal task, logs, compiler diagnostics and raw receipt', async (t) => {
  const f = await fixture(t);
  await observeCompilerFailure(f);
  const before = structuredClone(f.bridge.getState());
  const invoke = f.host.invoke;
  f.host.invoke = async raw => {
    const result = JSON.parse(await invoke(raw));
    if (JSON.parse(raw).payload?.sourcePath) {
      result.status = 'rejected'; result.data = null; result.diagnostics = [sourceFailure()];
    }
    return JSON.stringify(result);
  };
  const result = await f.bridge.sendQuery(query('get_task', { taskId, sourcePath: capturedSource().path }));
  assert.equal(result.status, 'rejected');
  assert.deepEqual(result.diagnostics, [sourceFailure()]);
  assert.deepEqual(f.bridge.getState(), before);
  assert.equal(f.bridge.getState().taskReadErrors?.[taskId], undefined);
});

test('a source transport error remains a Promise error without becoming a task-status failure', async (t) => {
  const f = await fixture(t);
  await observeCompilerFailure(f);
  const before = structuredClone(f.bridge.getState());
  f.readTask(() => { throw new Error('Source connection lost 中文'); });
  await assert.rejects(f.bridge.sendQuery(query('get_task', { taskId, sourcePath: capturedSource().path })),
    /Source connection lost 中文/);
  assert.deepEqual(f.bridge.getState(), before);
});

test('a source response for another task still fails identity validation without replacing the failed task', async (t) => {
  const f = await fixture(t);
  await observeCompilerFailure(f);
  const before = structuredClone(f.bridge.getState());
  f.readTask(() => ({ ...projection(task('failed', otherTaskId), [], [compilerFailure()]), source: capturedSource() }));
  await assert.rejects(f.bridge.sendQuery(query('get_task', { taskId, sourcePath: capturedSource().path })),
    /does not match the requested task/);
  assert.deepEqual(f.bridge.getState(), before);
});

for (const mode of ['transport', 'rejected']) {
  test(`a successful source read cannot hide an in-flight ${mode} status failure or clear its recovery entry`, async (t) => {
    const f = await fixture(t);
    await observeCompilerFailure(f);
    const pending = deferred();
    const invoke = f.host.invoke;
    let failStatusRead = true;
    f.readTask(() => ({ ...projection(task('failed'), [log(1)], [compilerFailure()]), source: capturedSource() }));
    f.host.invoke = async raw => {
      const request = JSON.parse(raw);
      if (request.operation === 'get_task' && !('sourcePath' in request.payload) && failStatusRead) {
        await pending.promise;
        if (mode === 'transport') throw new Error('Original task status disconnected');
        const result = JSON.parse(await invoke(raw));
        return JSON.stringify({ ...result, status: 'failed', data: null,
          diagnostics: [{ ...failure(), code: 'TASK_STATUS_UNAVAILABLE' }] });
      }
      return invoke(raw);
    };
    const read = f.bridge.sendQuery(query('get_task', { taskId }));
    const observed = mode === 'transport' ? assert.rejects(read, /Original task status disconnected/) : read;
    const source = await f.bridge.sendQuery(query('get_task', { taskId, sourcePath: capturedSource().path }));
    assert.deepEqual(source.data.source, capturedSource());
    pending.resolve();
    await observed;
    const error = f.bridge.getState().taskReadErrors[taskId];
    assert.match(error, mode === 'transport' ? /Original task status disconnected/ : /TASK_STATUS_UNAVAILABLE/);
    await f.bridge.sendQuery(query('get_task', { taskId, sourcePath: capturedSource().path }));
    assert.equal(f.bridge.getState().taskReadErrors[taskId], error);
    assert.equal(f.bridge.getState().tasks[taskId].state, 'failed');
    assert.deepEqual(f.bridge.getState().taskDiagnostics[taskId], [compilerFailure()]);
    failStatusRead = false;
    await f.bridge.sendQuery(query('get_task', { taskId }));
    assert.equal(f.bridge.getState().taskReadErrors?.[taskId], undefined);
  });
}

test('old task query cannot clear separately pushed failure details', async (t) => {
  const f = await fixture(t);
  f.emit('task_started', { task: task() });
  const pending = deferred();
  f.readTask(() => pending.promise);
  const read = f.bridge.sendQuery(query('get_task', { taskId }));
  f.emit('diagnostics_changed', { counts: { error: 1, warning: 0, info: 0 }, diagnostics: [failure()] });
  f.emit('task_completed', { task: task('failed') });
  pending.resolve(projection(task(), [], []));
  await read;
  assert.equal(f.bridge.getState().tasks[taskId].state, 'failed');
  assert.deepEqual(f.bridge.getState().taskDiagnostics[taskId], [failure()]);
  f.readTask(() => projection(task('failed'), [log(1)], [failure()]));
  await f.bridge.sendQuery(query('get_task', { taskId }));
  assert.equal(f.bridge.getState().taskLogs[taskId].length, 1);
  assert.deepEqual(f.bridge.getState().taskDiagnostics[taskId], [failure()]);
});

test('task-specific diagnostics alone protect a newer diagnostic projection', async (t) => {
  const f = await fixture(t);
  f.emit('task_started', { task: task() });
  const pending = deferred();
  f.readTask(() => pending.promise);
  const read = f.bridge.sendQuery(query('get_task', { taskId }));
  f.emit('diagnostics_changed', { counts: { error: 1, warning: 0, info: 0 }, diagnostics: [failure()] });
  pending.resolve(projection(task(), [log(1)], []));
  await read;
  assert.deepEqual(f.bridge.getState().taskDiagnostics[taskId], [failure()]);
  assert.equal(f.bridge.getState().taskLogs[taskId].length, 1);
});

test('another task changing does not invalidate this task query', async (t) => {
  const f = await fixture(t);
  f.emit('task_started', { task: task() });
  f.emit('task_started', { task: task('running', otherTaskId) });
  const pending = deferred();
  f.readTask(() => pending.promise);
  const read = f.bridge.sendQuery(query('get_task', { taskId }));
  f.emit('task_progressed', { task: { ...task('running', otherTaskId), progress: 0.8 } });
  f.emit('diagnostics_changed', { counts: { error: 1, warning: 0, info: 0 }, diagnostics: [failure(otherTaskId)] });
  pending.resolve(projection(task('succeeded')));
  await read;
  assert.equal(f.bridge.getState().tasks[taskId].state, 'succeeded');
  assert.deepEqual(f.bridge.getState().workbench.activeTasks.map((item) => item.id), [otherTaskId]);
});

test('same-task log events do not starve a query that reports completion', async (t) => {
  const f = await fixture(t);
  f.emit('task_started', { task: task() });
  const pending = deferred();
  f.readTask(() => pending.promise);
  const read = f.bridge.sendQuery(query('get_task', { taskId }));
  f.emit('task_log_appended', { taskId, entries: [log(2), log(3)] });
  pending.resolve(projection(task('succeeded'), [log(1), log(2)]));
  await read;
  assert.equal(f.bridge.getState().tasks[taskId].state, 'succeeded');
  assert.deepEqual(f.bridge.getState().taskLogs[taskId].map((entry) => entry.sequence), [1, 2, 3]);
});

test('an older query cannot replace a newer accepted query even without task events', async (t) => {
  const f = await fixture(t);
  const older = deferred();
  const newer = deferred();
  let reads = 0;
  f.readTask(() => ++reads === 1 ? older.promise : newer.promise);
  const first = f.bridge.sendQuery(query('get_task', { taskId }));
  const second = f.bridge.sendQuery(query('get_task', { taskId }));
  newer.resolve(projection(task('failed'), [log(2)], [failure()]));
  await second;
  older.resolve(projection(task(), [log(1)]));
  await first;
  assert.equal(f.bridge.getState().tasks[taskId].state, 'failed');
  assert.deepEqual(f.bridge.getState().taskDiagnostics[taskId], [failure()]);
  assert.deepEqual(f.bridge.getState().taskLogs[taskId].map((entry) => entry.sequence), [1, 2]);
});

test('disposing during a query prevents late state changes and notifications', async (t) => {
  const f = await fixture(t);
  const pending = deferred();
  f.readTask(() => pending.promise);
  let notifications = 0;
  f.bridge.onStateChange(() => { notifications += 1; });
  const read = f.bridge.sendQuery(query('get_task', { taskId }));
  const before = structuredClone(f.bridge.getState());
  f.bridge.dispose();
  pending.resolve(projection(task('succeeded'), [log(1)]));
  assert.equal((await read).data.task.state, 'succeeded');
  assert.deepEqual(f.bridge.getState(), before);
  assert.equal(notifications, 1);
});

async function startPolledTask(t, f) {
  t.mock.timers.enable({ apis: ['setTimeout'] });
  await f.bridge.sendCommand({
    messageType: 'command', schemaVersion: '1.0', requestId: randomUUID(), workspaceId,
    operation: 'run_datagen', expectedRevision: revision, payload: {}
  });
  f.emit('task_started', { task: task() });
  t.mock.timers.tick(500);
  await flush();
}

test('polling uses the same completion guard, fills logs, and stops after a pushed terminal state', async (t) => {
  const f = await fixture(t);
  const pending = deferred();
  f.readTask(() => pending.promise);
  await startPolledTask(t, f);
  assert.equal(f.taskQueries().length, 1);
  f.emit('task_completed', { task: task('succeeded') });
  pending.resolve(projection(task(), [log(1)]));
  await flush();
  t.mock.timers.tick(2000);
  await flush();
  assert.equal(f.bridge.getState().tasks[taskId].state, 'succeeded');
  assert.equal(f.bridge.getState().taskLogs[taskId].length, 1);
  assert.equal(f.taskQueries().length, 1);
});

test('a poll can discover failure without events and clear the active-task projection', async (t) => {
  const f = await fixture(t);
  f.readTask(() => projection(task('failed'), [log(1)], [failure()]));
  await startPolledTask(t, f);
  assert.equal(f.bridge.getState().tasks[taskId].state, 'failed');
  assert.deepEqual(f.bridge.getState().taskDiagnostics[taskId], [failure()]);
  assert.deepEqual(f.bridge.getState().workbench.activeTasks, []);
  t.mock.timers.tick(2000);
  await flush();
  assert.equal(f.taskQueries().length, 1);
});

test('polling continues from current progress after discarding an older snapshot', async (t) => {
  const f = await fixture(t);
  const pending = deferred();
  f.readTask(() => pending.promise);
  await startPolledTask(t, f);
  f.emit('task_progressed', { task: { ...task(), progress: 0.8 } });
  pending.resolve(projection(task(), [log(1)]));
  await flush();
  assert.equal(f.bridge.getState().tasks[taskId].progress, 0.8);
  f.readTask(() => projection(task('succeeded')));
  t.mock.timers.tick(500);
  await flush();
  assert.equal(f.taskQueries().length, 2);
  assert.equal(f.taskQueries()[1].payload.afterLogSequence, 1);
  assert.equal(f.bridge.getState().tasks[taskId].state, 'succeeded');
});

test('a transport error retries a running task but not a task completed while the request was pending', async (t) => {
  const f = await fixture(t);
  const first = deferred();
  const second = deferred();
  let reads = 0;
  f.readTask(() => ++reads === 1 ? first.promise : second.promise);
  t.mock.method(console, 'warn', () => {});
  await startPolledTask(t, f);
  first.reject(new Error('Temporary transport failure'));
  await flush();
  t.mock.timers.tick(1000);
  await flush();
  assert.equal(f.taskQueries().length, 2);
  f.emit('task_completed', { task: task('failed') });
  second.reject(new Error('Late transport failure'));
  await flush();
  t.mock.timers.tick(2000);
  await flush();
  assert.equal(f.taskQueries().length, 2);
  assert.equal(f.bridge.getState().tasks[taskId].state, 'failed');
});

test('reconnect hydration queries an existing active task and resumes its incremental logs without a command', async (t) => {
  const f = await fixture(t);
  t.mock.timers.enable({ apis: ['setTimeout'] });
  const invoke = f.host.invoke;
  f.host.invoke = async raw => {
    const response = JSON.parse(await invoke(raw));
    if (response.operation === 'get_workbench') {
      response.data.activeTasks = [task()];
      response.data.recentTasks = [task()];
    }
    return JSON.stringify(response);
  };
  f.bridge.getState().taskLogs[taskId] = [log(1)];
  f.readTask(() => projection(task('succeeded'), [log(2)]));
  await f.bridge.sendQuery(query('get_workbench'));
  t.mock.timers.tick(500);
  await flush();
  assert.equal(f.taskQueries().length, 1);
  assert.equal(f.taskQueries()[0].payload.taskId, taskId);
  assert.equal(f.taskQueries()[0].payload.afterLogSequence, 1);
  assert.deepEqual(f.bridge.getState().taskLogs[taskId].map(entry => entry.sequence), [1, 2]);
  assert.equal(f.bridge.getState().tasks[taskId].state, 'succeeded');
  assert.equal(f.invocations.some(request => request.messageType === 'command'), false);
});

test('a later stale running receipt cannot undo a terminal task or its original diagnostics', async (t) => {
  const f = await fixture(t);
  f.readTask(() => projection(task('failed'), [log(1)], [failure()]));
  await f.bridge.sendQuery(query('get_task', { taskId }));
  f.readTask(() => projection(task(), [log(2)], []));
  const raw = await f.bridge.sendQuery(query('get_task', { taskId, afterLogSequence: 1 }));
  assert.equal(raw.data.task.state, 'running');
  assert.equal(f.bridge.getState().tasks[taskId].state, 'failed');
  assert.deepEqual(f.bridge.getState().taskDiagnostics[taskId], [failure()]);
  assert.deepEqual(f.bridge.getState().taskLogs[taskId].map(entry => entry.sequence), [1, 2]);
});

test('workbench hydration cannot replace a task completed by a newer task query', async (t) => {
  const f = await fixture(t);
  const invoke = f.host.invoke;
  const pending = deferred();
  f.host.invoke = async raw => {
    const response = JSON.parse(await invoke(raw));
    if (response.operation === 'get_workbench') {
      response.data.recentTasks = [task()];
      response.data.activeTasks = [task()];
      await pending.promise;
    }
    return JSON.stringify(response);
  };
  const hydration = f.bridge.sendQuery(query('get_workbench'));
  await flush();
  f.readTask(() => projection(task('succeeded'), [log(1)]));
  await f.bridge.sendQuery(query('get_task', { taskId }));
  pending.resolve();
  await hydration;
  assert.equal(f.bridge.getState().tasks[taskId].state, 'succeeded');
  assert.deepEqual(f.bridge.getState().workbench.activeTasks, []);
  assert.equal(f.bridge.getState().workbench.recentTasks[0].state, 'succeeded');
});

test('a command receipt arriving after completion keeps the terminal task and starts no new poll', async (t) => {
  const f = await fixture(t);
  t.mock.timers.enable({ apis: ['setTimeout'] });
  const invoke = f.host.invoke;
  const pending = deferred();
  f.host.invoke = async raw => {
    const response = await invoke(raw);
    if (JSON.parse(raw).messageType === 'command') await pending.promise;
    return response;
  };
  const command = f.bridge.sendCommand({ messageType: 'command', schemaVersion: '1.0', requestId: randomUUID(),
    workspaceId, operation: 'run_datagen', expectedRevision: revision, payload: {} });
  await flush();
  f.emit('task_completed', { task: task('succeeded') });
  pending.resolve();
  await command;
  t.mock.timers.tick(2000);
  await flush();
  assert.equal(f.bridge.getState().tasks[taskId].state, 'succeeded');
  assert.equal(f.taskQueries().length, 0);
});

test('an older read failure cannot replace a newer successful status observation', async (t) => {
  const f = await fixture(t);
  const pending = deferred();
  let reads = 0;
  f.readTask(() => ++reads === 1 ? pending.promise : projection(task('succeeded')));
  const oldRead = f.bridge.sendQuery(query('get_task', { taskId }));
  const rejection = assert.rejects(oldRead, /late disconnect/);
  await f.bridge.sendQuery(query('get_task', { taskId }));
  pending.reject(new Error('late disconnect'));
  await rejection;
  assert.equal(f.bridge.getState().tasks[taskId].state, 'succeeded');
  assert.equal(f.bridge.getState().taskReadErrors?.[taskId], undefined);
});

test('a task response for another task is rejected without changing the selected task projection', async (t) => {
  const f = await fixture(t);
  f.emit('task_started', { task: task() });
  f.readTask(() => projection(task('succeeded', otherTaskId), [log(1)]));
  await assert.rejects(f.bridge.sendQuery(query('get_task', { taskId })), /does not match the requested task/);
  assert.equal(f.bridge.getState().tasks[taskId].state, 'running');
  assert.equal(f.bridge.getState().tasks[otherTaskId], undefined);
  assert.equal(f.bridge.getState().taskLogs[taskId], undefined);
});

test('failed task reads remain unavailable and retry the original task instead of stopping recovery', async (t) => {
  const f = await fixture(t);
  const invoke = f.host.invoke;
  let failed = false;
  f.host.invoke = async raw => {
    const response = JSON.parse(await invoke(raw));
    if (response.operation === 'get_task' && !failed) {
      failed = true; response.status = 'failed'; response.data = null;
      response.diagnostics = [{ ...failure(), code: 'TASK_STATUS_UNAVAILABLE' }];
    }
    return JSON.stringify(response);
  };
  await startPolledTask(t, f);
  assert.equal(f.bridge.getState().tasks[taskId].state, 'running');
  assert.match(f.bridge.getState().taskReadErrors[taskId], /TASK_STATUS_UNAVAILABLE/);
  f.readTask(() => projection(task('succeeded'), [log(1)]));
  t.mock.timers.tick(1000);
  await flush();
  assert.equal(f.taskQueries().length, 2);
  assert.equal(f.taskQueries()[1].payload.taskId, taskId);
  assert.equal(f.bridge.getState().tasks[taskId].state, 'succeeded');
  assert.equal(f.bridge.getState().taskReadErrors?.[taskId], undefined);
});
