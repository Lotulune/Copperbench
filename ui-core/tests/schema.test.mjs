import assert from 'node:assert/strict';
import test from 'node:test';
import { readFile } from 'node:fs/promises';
import { createValidator, validateAll } from '../scripts/validate.mjs';

async function schema(name) {
  return JSON.parse(await readFile(new URL(`../schemas/v1.0/${name}.schema.json`, import.meta.url), 'utf8'));
}

test('all UI-Core schemas compile and all mock scenarios validate', async () => {
  const result = await validateAll();
  assert.ok(result.schemaCount >= 9);
  assert.ok(result.fixtureCount >= 13);
  assert.deepEqual(result.failures, []);
});

test('command and query result operation sets match their request envelopes', async () => {
  const command = await schema('command');
  const commandResult = await schema('command-result');
  const query = await schema('query');
  const queryResult = await schema('query-result');

  assert.deepEqual(commandResult.properties.operation.enum, command.properties.operation.enum);
  assert.deepEqual(queryResult.properties.operation.enum, query.properties.operation.enum);
});

test('list_mod_elements accepts the unified cursor query contract and rejects unknown fields', async () => {
  const { ajv } = await createValidator();
  const validate = ajv.getSchema('urn:ui-core:1.0:query');
  assert.ok(validate, 'query schema should be registered');
  const query = {
    messageType: 'query',
    schemaVersion: '1.0',
    requestId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaa31',
    workspaceId: 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
    operation: 'list_mod_elements',
    payload: {
      cursor: 'opaque-cursor',
      limit: 137,
      sort: '-updatedAt',
      filter: {
        search: 'ore',
        types: ['livingentity'],
        states: ['valid'],
        firstParty: true,
      },
      fields: ['id', 'name', 'updatedAt'],
    },
  };
  assert.equal(validate(query), true, JSON.stringify(validate.errors));
  query.payload.unexpected = true;
  assert.equal(validate(query), false);
});

test('workspace registry, recovery point, and publish batch lists accept cursor contracts', async () => {
  const { ajv } = await createValidator();
  const validate = ajv.getSchema('urn:ui-core:1.0:query');
  const base = {
    messageType: 'query',
    schemaVersion: '1.0',
    workspaceId: 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
  };

  const registry = {
    ...base,
    requestId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaa32',
    operation: 'list_workspace_registries',
    payload: {
      registry: 'variables', limit: 50, sort: '-name', filter: { search: 'score' }, fields: ['id', 'name'],
    },
  };
  assert.equal(validate(registry), true, JSON.stringify(validate.errors));

  const recovery = {
    ...base,
    requestId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaa33',
    operation: 'get_history',
    payload: {
      limit: 50, sort: '-createdAt', filter: { actor: 'mcp' }, fields: ['id', 'label', 'createdAt'],
    },
  };
  assert.equal(validate(recovery), true, JSON.stringify(validate.errors));

  const batches = {
    ...base,
    requestId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaa34',
    operation: 'list_publish_batches',
    payload: {
      limit: 50, sort: '-assetCount', filter: { search: 'release' }, fields: ['id', 'name', 'assetCount'],
    },
  };
  assert.equal(validate(batches), true, JSON.stringify(validate.errors));

  registry.payload = { limit: 50 };
  assert.equal(validate(registry), false, 'registry cursor mode must identify one registry');
});

test('get_task requires an incremental log cursor and rejects malformed cursors', async () => {
  const { ajv } = await createValidator();
  const validate = ajv.getSchema('urn:ui-core:1.0:query');
  const query = {
    messageType: 'query',
    schemaVersion: '1.0',
    requestId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaa35',
    workspaceId: 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
    operation: 'get_task',
    payload: {
      taskId: 'cccccccc-cccc-4ccc-8ccc-cccccccccccc',
      afterLogSequence: 12,
    },
  };
  assert.equal(validate(query), true, JSON.stringify(validate.errors));
  delete query.payload.afterLogSequence;
  assert.equal(validate(query), false, 'get_task must carry an incremental log cursor');
  query.payload.afterLogSequence = -1;
  assert.equal(validate(query), false, 'afterLogSequence must be non-negative');
});

test('workspace plans validate ordered plan, preview, and apply envelopes', async () => {
  const { ajv } = await createValidator();
  const validateQuery = ajv.getSchema('urn:ui-core:1.0:query');
  const validateCommand = ajv.getSchema('urn:ui-core:1.0:command');
  const workspaceId = 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb';
  const planRequest = {
    messageType: 'query',
    schemaVersion: '1.0',
    requestId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaa36',
    workspaceId,
    operation: 'plan_workspace_changes',
    payload: {
      expectedRevision: 9,
      idempotencyKey: 'ai-plan-001',
      operations: [{
        operation: 'create_mod_element',
        payload: { elementType: 'item', name: 'planned_item', initialValues: {} },
      }],
    },
  };
  assert.equal(validateQuery(planRequest), true, JSON.stringify(validateQuery.errors));

  const plan = {
    schemaVersion: '1.0',
    workspaceId,
    baseRevision: 9,
    idempotencyKey: 'ai-plan-001',
    operations: [{
      operation: 'create_mod_element',
      payload: { elementType: 'item', name: 'planned_item', initialValues: {} },
      plannedId: 'cccccccc-cccc-4ccc-8ccc-cccccccccccc',
    }],
    operationCount: 1,
    targetDigest: 'a'.repeat(64),
    semanticDiff: [{ kind: 'element_created', elementId: 'cccccccc-cccc-4ccc-8ccc-cccccccccccc' }],
    changedPaths: ['/elements/cccccccc-cccc-4ccc-8ccc-cccccccccccc'],
    permission: { currentProfile: 'workspace', requiredProfile: 'workspace', allowed: true },
    planId: 'b'.repeat(64),
    planToken: 'c'.repeat(64),
  };
  const preview = {
    ...planRequest,
    requestId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaa37',
    operation: 'preview_workspace_plan',
    payload: { plan },
  };
  assert.equal(validateQuery(preview), true, JSON.stringify(validateQuery.errors));

  const apply = {
    messageType: 'command',
    schemaVersion: '1.0',
    requestId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaa38',
    workspaceId,
    expectedRevision: 9,
    operation: 'apply_workspace_plan',
    payload: {
      clientMutationId: 'dddddddd-dddd-4ddd-8ddd-dddddddddddd',
      plan,
    },
  };
  assert.equal(validateCommand(apply), true, JSON.stringify(validateCommand.errors));
  apply.payload.unexpected = true;
  assert.equal(validateCommand(apply), false, 'workspace plan apply should reject unknown payload fields');
});

test('list_mod_elements result accepts imported read-only upstream element types', async () => {
  const { ajv } = await createValidator();
  const validate = ajv.getSchema('urn:ui-core:1.0:query-result');
  const result = {
    messageType: 'query_result',
    schemaVersion: '1.0',
    requestId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaa35',
    workspaceId: 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
    operation: 'list_mod_elements',
    status: 'succeeded',
    revision: 9,
    data: {
      items: [{
        id: 'cccccccc-cccc-4ccc-8ccc-cccccccccccc',
        type: 'livingentity',
        name: 'copper_golem',
        displayName: 'Copper Golem',
        state: 'valid',
        ownership: 'generated',
        updatedAt: '2026-08-26T12:00:00Z',
        firstParty: false,
        diagnostics: { error: 0, warning: 0, info: 0 },
      }],
      page: 1,
      pageSize: 50,
      total: 1,
      nextCursor: null,
      availableTypes: ['block', 'item', 'recipe', 'procedure', 'function', 'loottable', 'achievement'],
    },
    diagnostics: [],
  };
  assert.equal(validate(result), true, JSON.stringify(validate.errors));
});

test('canonical events include the workspace lifecycle emitted by Java Core', async () => {
  const event = await schema('event');
  assert.ok(event.properties.event.enum.includes('workspace_created'));
});

test('version track matrix fixture validates against the canonical tracks schema', async () => {
  const { ajv } = await createValidator();
  const validate = ajv.getSchema('urn:ui-core:1.0:tracks');
  assert.ok(validate, 'tracks schema should be registered');
  const fixture = JSON.parse(await (await import('node:fs/promises')).readFile(
    new URL('../fixtures/v1.0/tracks/version-tracks.json', import.meta.url), 'utf8'));
  assert.equal(validate(fixture), true, JSON.stringify(validate.errors));
});

test('release notes fixture validates against the canonical release schema', async () => {
  const { ajv } = await createValidator();
  const validate = ajv.getSchema('urn:ui-core:1.0:release');
  assert.ok(validate, 'release schema should be registered');
  const fixture = JSON.parse(await (await import('node:fs/promises')).readFile(
    new URL('../fixtures/v1.0/release/release-notes.json', import.meta.url), 'utf8'));
  assert.equal(validate(fixture), true, JSON.stringify(validate.errors));
});

test('asset reference graph fixture validates against the canonical asset projection schema', async () => {
  const { ajv } = await createValidator();
  const validate = ajv.getSchema('urn:ui-core:1.0:asset');
  assert.ok(validate, 'asset schema should be registered');
  const fixture = JSON.parse(await (await import('node:fs/promises')).readFile(
    new URL('../fixtures/v1.0/assets/asset-reference-graph.json', import.meta.url), 'utf8'));
  assert.equal(validate(fixture), true, JSON.stringify(validate.errors));
});

test('legacy v0.1 schemas and fixtures remain valid after the v1 freeze', async () => {
  const result = await validateAll('0.1');
  assert.deepEqual(result.failures, []);
});

test('handshake rejects an incompatible version without an untyped fallback', async () => {
  const { ajv } = await createValidator();
  const validate = ajv.getSchema('urn:ui-core:1.0:handshake-result');
  const valid = validate({
    messageType: 'handshake_result',
    requestId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaa22',
    status: 'incompatible',
    selectedSchemaVersion: '1.0',
    coreSchemaVersions: ['1.0'],
    diagnostics: [],
  });
  assert.equal(valid, false);
});

test('a mutating command without expectedRevision is rejected', async () => {
  const { ajv } = await createValidator();
  const validate = ajv.getSchema('urn:ui-core:1.0:command');
  const valid = validate({
    messageType: 'command',
    schemaVersion: '1.0',
    requestId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaa12',
    workspaceId: '11111111-1111-4111-8111-111111111111',
    operation: 'delete_mod_element',
    payload: {
      clientMutationId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaa13',
      elementId: '22222222-2222-4222-8222-222222222221',
    },
  });
  assert.equal(valid, false);
});

test('an event with the wrong payload shape is rejected', async () => {
  const { ajv } = await createValidator();
  const validate = ajv.getSchema('urn:ui-core:1.0:event');
  const valid = validate({
    messageType: 'event',
    schemaVersion: '1.0',
    eventId: 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbba1',
    workspaceId: '11111111-1111-4111-8111-111111111111',
    revision: 42,
    sequence: 999,
    occurredAt: '2026-08-16T07:00:00Z',
    event: 'task_progressed',
    causedByRequestId: null,
    payload: { core: 'connected', network: 'online', bridge: 'ready' },
  });
  assert.equal(valid, false);
});

test('history lifecycle events emitted by Java Core are accepted', async () => {
  const { ajv } = await createValidator();
  const validate = ajv.getSchema('urn:ui-core:1.0:event');
  const valid = validate({
    messageType: 'event',
    schemaVersion: '1.0',
    eventId: 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbba2',
    workspaceId: '11111111-1111-4111-8111-111111111111',
    revision: 42,
    sequence: 1000,
    occurredAt: '2026-08-17T02:10:00Z',
    event: 'recovery_point_created',
    causedByRequestId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaa31',
    payload: {
      recoveryPoint: {
        id: '7d7c3e34c657acc1',
        label: 'Before MCP batch edit',
        actor: 'mcp',
        taskId: '',
        createdAt: '2026-08-17T02:10:00Z',
      },
    },
  });
  assert.equal(valid, true, JSON.stringify(validate.errors));
});

test('unknown envelope properties are rejected', async () => {
  const { ajv } = await createValidator();
  const validate = ajv.getSchema('urn:ui-core:1.0:query');
  const valid = validate({
    messageType: 'query',
    schemaVersion: '1.0',
    requestId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaa14',
    workspaceId: '11111111-1111-4111-8111-111111111111',
    operation: 'get_workbench',
    payload: {},
    arbitraryPath: 'C:\\Users\\example',
  });
  assert.equal(valid, false);
});

test('history projections use versioned query envelopes', async () => {
  const { ajv } = await createValidator();
  const validate = ajv.getSchema('urn:ui-core:1.0:query-result');
  const valid = validate({
    messageType: 'query_result',
    schemaVersion: '1.0',
    requestId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaa31',
    workspaceId: '11111111-1111-4111-8111-111111111111',
    operation: 'get_history',
    status: 'succeeded',
    revision: 42,
    data: {
      currentRevision: 42,
      recoveryPoints: [{
        id: '7d7c3e34c657acc1',
        label: 'Before MCP batch edit',
        actor: 'mcp',
        taskId: 'task-184',
        createdAt: '2026-08-17T02:10:00Z',
      }],
    },
    diagnostics: [],
  });
  assert.equal(valid, true, JSON.stringify(validate.errors));
});

test('restore requires an explicit confirmation fact', async () => {
  const { ajv } = await createValidator();
  const validate = ajv.getSchema('urn:ui-core:1.0:command');
  const valid = validate({
    messageType: 'command',
    schemaVersion: '1.0',
    requestId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaa32',
    workspaceId: '11111111-1111-4111-8111-111111111111',
    expectedRevision: 42,
    operation: 'restore_recovery_point',
    payload: {
      clientMutationId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaa33',
      recoveryPointId: '7d7c3e34c657acc1',
    },
  });
  assert.equal(valid, false);
});

test('protected operation decisions are explicit and bounded', async () => {
  const { ajv } = await createValidator();
  const validate = ajv.getSchema('urn:ui-core:1.0:command');
  const valid = validate({
    messageType: 'command',
    schemaVersion: '1.0',
    requestId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaa34',
    workspaceId: '11111111-1111-4111-8111-111111111111',
    expectedRevision: 42,
    operation: 'resolve_operation_approval',
    payload: {
      clientMutationId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaa35',
      approvalId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaa36',
      decision: 'allow_forever',
    },
  });
  assert.equal(valid, false);
});
