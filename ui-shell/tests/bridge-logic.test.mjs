import assert from 'node:assert/strict';
import test from 'node:test';
import { randomUUID } from 'node:crypto';
import { fileURLToPath } from 'node:url';
import { createServer } from 'vite';

// Load the production TypeScript and its real imports through the existing Vite
// toolchain. There is no copied bridge implementation or browser server to test.
const loader = await createServer({
  configFile: false,
  root: fileURLToPath(new URL('../', import.meta.url)),
  server: { middlewareMode: true, hmr: false, watch: null },
  appType: 'custom',
  optimizeDeps: { noDiscovery: true }
});
let JcefCoreBridge;
try {
  ({ JcefCoreBridge } = await loader.ssrLoadModule('/src/bridge/JcefCoreBridge.ts'));
} finally {
  await loader.close();
}

const workspaceId = '11111111-1111-4111-8111-111111111111';
const taskId = '44444444-4444-4444-8444-444444444444';
const otherTaskId = '55555555-5555-4555-8555-555555555555';
const revision = 10;
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
