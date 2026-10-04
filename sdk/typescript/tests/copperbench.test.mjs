import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import test from 'node:test';

// Compile and execute the shipped SDK in memory. Only the fetch transport seam
// is replaced; callTool, retries, JSON/SSE parsing and errors remain production code.
const require = createRequire(new URL('../../../ui-shell/package.json', import.meta.url));
const ts = require('typescript');
const source = readFileSync(new URL('../copperbench.ts', import.meta.url), 'utf8');
const compiled = ts.transpileModule(source, {
  fileName: 'copperbench.ts',
  compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 },
  reportDiagnostics: true
});
assert.equal(compiled.diagnostics?.length ?? 0, 0, 'The production SDK must transpile');
const { CopperbenchClient, CopperbenchError } = await import(
  `data:text/javascript;base64,${Buffer.from(compiled.outputText).toString('base64')}`
);
const fixtures = JSON.parse(readFileSync(new URL('../../tests/mcp-client-reliability.json', import.meta.url), 'utf8'));
const failures = ['timeout', 'connection_reset', 'body_reset', 'incomplete_read', 'http_502', 'http_503', 'http_504'];

function client(fetch, options = {}) {
  return new CopperbenchClient({ endpoint: 'http://127.0.0.1:61999/mcp', token: 'fixture-token',
    workspaceId: 'workspace', retryBackoffMs: 0, ...options, fetch });
}

function toolResult(payload, metadata = {}) {
  return { content: [{ type: 'text', text: JSON.stringify(payload) }], ...metadata };
}

function reply(request, result, { sse = false, headers = {} } = {}) {
  const body = JSON.stringify({ jsonrpc: '2.0', id: JSON.parse(request.body).id, result });
  return new Response(sse ? `event: message\r\ndata:${body}\r\n\r\n` : body, { headers });
}

function fail(kind) {
  if (kind.startsWith('http_')) return new Response('gateway failure', { status: Number(kind.slice(5)) });
  if (kind === 'body_reset' || kind === 'incomplete_read') {
    return new Response(new ReadableStream({ start(controller) { controller.error(new TypeError('response body interrupted')); } }));
  }
  if (kind === 'timeout') throw new DOMException('response lost after request reached server', 'TimeoutError');
  throw new TypeError('connection reset after request reached server');
}

async function assertCode(code, action, label) {
  let captured;
  await assert.rejects(action, (error) => {
    assert.ok(error instanceof CopperbenchError, label);
    assert.equal(error.code, code, label);
    captured = error;
    return true;
  });
  return captured;
}

test('audited reads retry transient failures without changing the request', async () => {
  for (const tool of fixtures.readTools) {
    for (const failure of failures) {
      const sent = [];
      const sdk = client(async (_, request) => {
        sent.push(JSON.parse(request.body));
        if (sent.length === 1) return fail(failure);
        return reply(request, toolResult({ status: 'succeeded', data: { revision: 7 } }));
      });
      const result = await sdk.callTool(tool.name, tool.arguments);
      assert.equal(result.status, 'succeeded', `${tool.name}: ${failure}`);
      assert.equal(sent.length, 2, `${tool.name}: ${failure}`);
      assert.deepEqual(sent[0], sent[1]);
      assert.deepEqual(sent[0].params.arguments, tool.arguments);
    }
  }
});

test('mutations and unreviewed tools are sent once even with a retry option', async () => {
  for (const tool of fixtures.singleAttemptTools) {
    for (const failure of failures) {
      const sent = [];
      const sdk = client(async (_, request) => {
        sent.push(JSON.parse(request.body));
        if (sent.length === 1) return fail(failure);
        return reply(request, toolResult({ status: 'accepted', task: { id: 'duplicate-task' } }));
      }, { maxTransportRetries: 99 });
      await assertCode(failure.startsWith('http_') ? `HTTP_${failure.slice(5)}` : 'MCP_TRANSPORT_FAILED',
        () => sdk.callTool(tool.name, tool.arguments), `${tool.name}: ${failure}`);
      assert.equal(sent.length, 1, 'An uncertain write must not start a second operation');
      assert.deepEqual(sent[0].params.arguments, tool.arguments);
    }
  }
});

test('retry limit and disabling retries', async () => {
  for (const [configured, attempts] of [[99, 3], [0, 1], [-1, 1]]) {
    for (const failure of ['timeout', 'http_502']) {
      let calls = 0;
      const sdk = client(async () => { calls++; return fail(failure); }, { maxTransportRetries: configured });
      await assertCode(failure === 'http_502' ? 'HTTP_502' : 'MCP_TRANSPORT_FAILED', () => sdk.getWorkspace());
      assert.equal(calls, attempts);
    }
  }
});

test('nonretryable HTTP statuses are sent once', async () => {
  for (const status of [400, 401, 403, 404, 409, 500]) {
    let calls = 0;
    const sdk = client(async () => { calls++; return fail(`http_${status}`); });
    await assertCode(`HTTP_${status}`, () => sdk.getWorkspace());
    assert.equal(calls, 1);
  }
});

test('initialization is not replayed after an uncertain response', async () => {
  for (const failure of ['timeout', 'http_502']) {
    let calls = 0;
    const sdk = client(async () => { calls++; return fail(failure); });
    await assertCode(failure === 'http_502' ? 'HTTP_502' : 'MCP_TRANSPORT_FAILED', () => sdk.initialize());
    assert.equal(calls, 1);
  }
});

test('interrupted HTTP error body preserves status and retry policy', async () => {
  for (const [status, tool, succeeds, attempts] of [
    [401, 'get_workspace', false, 1], [403, 'get_workspace', false, 1],
    [502, 'get_workspace', true, 2], [502, 'build_workspace', false, 1]
  ]) {
    let calls = 0;
    const sdk = client(async (_, request) => {
      calls++;
      if (calls === 1) {
        return new Response(new ReadableStream({ start(controller) { controller.error(new TypeError('error body interrupted')); } }), { status });
      }
      return reply(request, toolResult({ status: 'succeeded' }));
    });
    if (succeeds) assert.equal((await sdk.callTool(tool, {})).status, 'succeeded');
    else await assertCode(`HTTP_${status}`, () => sdk.callTool(tool, {}));
    assert.equal(calls, attempts);
  }
});

test('tool error codes and details survive without retries', async () => {
  for (const scenario of fixtures.toolErrors) {
    let calls = 0;
    const result = toolResult(scenario.payload, 'isError' in scenario ? { isError: scenario.isError } : {});
    const sdk = client(async (_, request) => { calls++; return reply(request, result); });
    const error = await assertCode(scenario.code, () => sdk.getWorkspace(), scenario.label);
    assert.deepEqual(error.details, scenario.payload);
    assert.equal(calls, 1);
  }
});

test('malformed tool results fail closed without retries', async () => {
  for (const result of fixtures.invalidToolResults) {
    let calls = 0;
    const sdk = client(async (_, request) => { calls++; return reply(request, result); });
    await assertCode('MCP_TOOL_RESULT_INVALID', () => sdk.getWorkspace(), JSON.stringify(result));
    assert.equal(calls, 1);
  }
});

test('malformed RPC envelopes fail closed without retries', async () => {
  const bodies = [...fixtures.invalidEnvelopes.map((value) => JSON.stringify(value)),
    '', '[]', 'null', '{', 'data: [1]\n\n', 'data: invalid\n\n'];
  for (const body of bodies) {
    let calls = 0;
    const sdk = client(async () => { calls++; return new Response(body); });
    await assertCode('MCP_RESPONSE_INVALID', () => sdk.getWorkspace(), body);
    assert.equal(calls, 1);
  }
});

test('JSON-RPC error codes are preserved without retries', async () => {
  for (const code of [-32602, 'MCP_AUTHORIZATION_REJECTED']) {
    let calls = 0;
    const error = { code, message: 'request rejected' };
    const sdk = client(async () => { calls++; return new Response(JSON.stringify({ jsonrpc: '2.0', id: 1, error })); });
    const captured = await assertCode(String(code), () => sdk.getWorkspace());
    assert.deepEqual(captured.details, error);
    assert.equal(calls, 1);
  }
});

test('successful payloads are unchanged in JSON and SSE', async () => {
  for (const payload of fixtures.successfulPayloads) {
    for (const sse of [false, true]) {
      const sdk = client(async (_, request) => reply(request, toolResult(payload, { isError: false }), { sse }));
      assert.deepEqual(await sdk.getWorkspace(), payload);
    }
  }
});

test('session and incremental log cursor survive a read retry', async () => {
  const requests = [];
  const sdk = client(async (_, request) => {
    requests.push(request);
    const sent = JSON.parse(request.body);
    if (sent.method === 'initialize') {
      return reply(request, { protocolVersion: '2025-11-25', capabilities: {} }, { headers: { 'mcp-session-id': 'session-1' } });
    }
    if (sent.method === 'notifications/initialized') return new Response(null, { status: 202 });
    if (requests.length === 3) throw new DOMException('first task poll timed out', 'TimeoutError');
    return reply(request, toolResult({ status: 'succeeded', data: { logs: [{ sequence: 38 }] } }), { sse: true });
  });
  await sdk.initialize();
  const result = await sdk.getTask('task-1', 37);
  assert.equal(requests.length, 4);
  assert.equal(requests.at(-1).headers['mcp-session-id'], 'session-1');
  assert.equal(requests.at(-2).body, requests.at(-1).body);
  assert.deepEqual(JSON.parse(requests.at(-1).body).params.arguments, { taskId: 'task-1', afterLogSequence: 37 });
  assert.equal(result.data.logs[0].sequence, 38);
});
