import assert from 'node:assert/strict';
import test from 'node:test';
import { readFile } from 'node:fs/promises';
import { createValidator, validateAll } from '../scripts/validate.mjs';

test('Procedure trigger catalogs carry typed dependencies and remain optional for older hosts', async () => {
  const { ajv } = await createValidator();
  const validate = ajv.getSchema('urn:ui-core:1.0:query-result');
  const result = { messageType: 'query_result', schemaVersion: '1.0', requestId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaa31',
    workspaceId: 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', operation: 'get_procedure_editor',
    status: 'succeeded', revision: 1, diagnostics: [], data: {} };
  assert.equal(validate(result), true, JSON.stringify(validate.errors));
  result.data.triggerCatalog = [{ id: 'player_ticks', label: { key: 'trigger.player_ticks', fallback: 'Player tick' },
    dependencies: [{ name: 'x', type: 'number' }] }];
  assert.equal(validate(result), true, JSON.stringify(validate.errors));
  result.data.triggerCatalog[0].dependencies[0].type = 3;
  assert.equal(validate(result), false);
  result.data.triggerCatalog[0].dependencies[0].type = 'number';
  result.data.triggerCatalog[0].id = '';
  assert.equal(validate(result), false);
});

async function schema(name) {
  return JSON.parse(await readFile(new URL(`../schemas/v1.0/${name}.schema.json`, import.meta.url), 'utf8'));
}

test('creation discovery distinguishes availability and validates read-only paging', async () => {
  const { ajv } = await createValidator();
  const query = ajv.getSchema('urn:ui-core:1.0:query');
  assert.equal(query(operationRequest('query', 'get_mod_element_field_contract', { elementType: 'item' })), true);
  assert.equal(query(operationRequest('query', 'get_mod_element_field_contract', { elementType: 'item', createProbe: true })), false);
  assert.equal(query(operationRequest('query', 'get_field_reference_options', { elementType: 'recipe', mappingSource: 'blocksitems', limit: 200 })), true);
  assert.equal(query(operationRequest('query', 'get_field_reference_options', { elementType: 'recipe', mappingSource: 'blocksitems', limit: 201 })), false);
  const validate = ajv.getSchema('urn:ui-core:1.0:element-field-contract');
  const data = { elementType: 'item', generatorId: 'fabric-1.21.1', contractVersion: '1',
    availability: 'not_exposed', complete: false, reasonCode: 'FIELD_CONTRACT_UNAVAILABLE', fields: [], alternatives: [] };
  assert.equal(validate(data), true, JSON.stringify(validate.errors));
  data.availability = 'unsupported';
  assert.equal(validate(data), true, JSON.stringify(validate.errors));
  data.complete = true;
  assert.equal(validate(data), false);
  data.availability = 'available';
  assert.equal(validate(data), false, 'available requires usable fields, restrictions and an example');
  data.fields = [{ path: '/stackSize', compatibilityPath: '/fields/stackSize', inputSchema: { type: 'integer' }, requiredOnCreate: false, generatorSupport: 'declared' }];
  data.minimalExample = { elementType: 'item', name: 'Item', initialValues: {} };
  data.generatorRestrictions = { source: 'active_generator_definition', includedFields: null, excludedFields: [], scope: 'template coverage' };
  data.createNameSchema = { type: 'string', pattern: '^[a-z][a-z0-9_]{0,63}$' };
  assert.equal(validate(data), true, JSON.stringify(validate.errors));
});

test('generation preflight has no mutation payload and cannot claim ready with unknown input or conflicts', async () => {
  const { ajv } = await createValidator();
  const query = ajv.getSchema('urn:ui-core:1.0:query');
  assert.equal(query(operationRequest('query', 'preview_generation', {})), true, JSON.stringify(query.errors));
  for (const payload of [{ takeOwnership: true }, { force: true }, { expectedRevision: 7 }])
    assert.equal(query(operationRequest('query', 'preview_generation', payload)), false);
  const validate = ajv.getSchema('urn:ui-core:1.0:query-result');
  const data = { contractVersion: '1', scope: 'generation_source_safety', revision: 7,
    generator: { id: 'fabric-1.21.1' }, status: 'ready', reasonCode: null,
    inputFingerprint: 'a'.repeat(64), fingerprintScope: 'workspace_inputs', managedPaths: ['src/main/java/Entry.java'],
    managedPathCount: 1, managedPathsTruncated: false, conflicts: [], conflictCount: 0, conflictsTruncated: false,
    dependenciesRequired: true, executionRechecksInputs: true, nextSteps: ['inspect_source', 'keep_native_workflow', 'review_migration'] };
  const result = { messageType: 'query_result', schemaVersion: '1.0', requestId: operationProbeId,
    workspaceId: operationProbeWorkspace, operation: 'preview_generation', status: 'succeeded', revision: 7, data, diagnostics: [] };
  assert.equal(validate(result), true, JSON.stringify(validate.errors));
  data.inputFingerprint = null;
  assert.equal(validate(result), false);
  data.status = 'unknown'; data.reasonCode = 'GENERATION_PREFLIGHT_UNAVAILABLE';
  assert.equal(validate(result), true, JSON.stringify(validate.errors));
  data.status = 'conflicted'; data.reasonCode = null;
  data.conflictCount = 1; data.conflicts = [{ relativePath: 'src/main/java/Entry.java', reasonCode: 'SOURCE_CHANGED',
    expectedOwnership: 'generated', observedOwnership: 'unknown' }];
  assert.equal(validate(result), true, JSON.stringify(validate.errors));
  data.conflicts[0].relativePath = '../outside.java';
  assert.equal(validate(result), false);
  data.conflicts[0].relativePath = null;
  assert.equal(validate(result), true, JSON.stringify(validate.errors));
  data.conflicts = Array.from({ length: 101 }, () => data.conflicts[0]);
  assert.equal(validate(result), false, 'Conflict lists must remain bounded');
});

test('wire operations cover Java and UI callers, with matching request and response categories', async () => {
  const [command, query, java, typescript, { ajv }] = await Promise.all([
    schema('command'), schema('query'),
    readFile(new URL('../../src/main/java/dev/copperbench/core/contract/UiCore.java', import.meta.url), 'utf8'),
    readFile(new URL('../../ui-shell/src/types/contract.ts', import.meta.url), 'utf8'),
    createValidator()
  ]);
  const operationEnum = java.match(/public enum Operation\s*\{([\s\S]*?)\n\s*\}/)?.[1];
  assert.ok(operationEnum, 'Java wire operation enum must remain discoverable');
  const coreOperations = [...operationEnum.matchAll(/@SerializedName\("([a-z_]+)"\)/g)].map(match => match[1]);
  assert.ok(coreOperations.length > 0, 'Java wire operation enum must not be empty');
  const schemas = { Command: command, Query: query };
  const allOperations = [...command.properties.operation.enum, ...query.properties.operation.enum];
  assert.equal(new Set(allOperations).size, allOperations.length, 'an operation belongs to exactly one request category');
  assert.deepEqual([...allOperations].sort(), [...coreOperations].sort(), 'schemas must describe the operations exposed by Core');
  for (const [kind, document] of Object.entries(schemas)) {
    const union = typescript.match(new RegExp(`export type ${kind}Operation\\s*=([\\s\\S]*?);`))?.[1];
    assert.ok(union, `${kind} UI operation union must remain discoverable`);
    const uiOperations = [...union.matchAll(/'([a-z_]+)'/g)].map(match => match[1]);
    assert.ok(uiOperations.length > 0, `${kind} UI operation union must not be empty`);
    const supported = new Set(document.properties.operation.enum);
    assert.deepEqual(uiOperations.filter(operation => !supported.has(operation)), [], `${kind} operations used by UI must be supported`);
    const validateResponseOperation = ajv.compile({ $ref: `urn:ui-core:1.0:${kind.toLowerCase()}-result#/properties/operation` });
    for (const operation of [...allOperations, 'unknown_operation']) {
      assert.equal(validateResponseOperation(operation), supported.has(operation), `${kind} response category for ${operation}`);
    }
  }
});

const operationProbeId = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaa31';
const operationProbeWorkspace = 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb';

function operationRequest(messageType, operation, payload) {
  return { messageType, schemaVersion: '1.0', requestId: operationProbeId,
    workspaceId: operationProbeWorkspace, operation, payload,
    ...(messageType === 'command' ? { expectedRevision: 7 } : {}) };
}

test('workspace source edits require a disk hash, revision and bounded text while reads remain separate', async () => {
  const { ajv } = await createValidator();
  const command = ajv.getSchema('urn:ui-core:1.0:command');
  const query = ajv.getSchema('urn:ui-core:1.0:query');
  const update = operationRequest('command', 'update_workspace_file', {
    clientMutationId: operationProbeId, relativePath: 'src/main/java/example/Entry.java', content: '', expectedSha256: 'a'.repeat(64)
  });
  assert.equal(command(update), true, JSON.stringify(command.errors));
  delete update.expectedRevision;
  assert.equal(command(update), false);
  update.expectedRevision = 7;
  delete update.payload.expectedSha256;
  assert.equal(command(update), false);
  update.payload.expectedSha256 = 'stale';
  assert.equal(command(update), false);
  update.payload.expectedSha256 = 'b'.repeat(64);
  update.payload.content = 'x'.repeat(1048577);
  assert.equal(command(update), false);
  update.payload.content = 'class Entry {}';
  update.payload.force = true;
  assert.equal(command(update), false, 'there is no force-overwrite escape hatch');
  assert.equal(query(operationRequest('query', 'read_workspace_file', { relativePath: 'src/main/java/example/Entry.java' })), true);
  assert.equal(query(operationRequest('query', 'read_workspace_file', {})), false);
  assert.equal(query(operationRequest('query', 'list_workspace_files', { limit: 200, offset: 0, search: 'Entry' })), true);
  for (const payload of [{ limit: 201 }, { offset: -1 }, { offset: 1.5 }])
    assert.equal(query(operationRequest('query', 'list_workspace_files', payload)), false);
  assert.equal(query(operationRequest('query', 'get_workspace_source_index', {})), true);
  assert.equal(query(operationRequest('query', 'get_workspace_source_index', { root: 'C:/' })), false);
});

test('source projections retain ownership, evidence locations and conflict-compatible response shapes', async () => {
  const { ajv } = await createValidator();
  const query = ajv.getSchema('urn:ui-core:1.0:query-result');
  const command = ajv.getSchema('urn:ui-core:1.0:command-result');
  const file = { relativePath: 'src/main/java/Entry.java', name: 'Entry.java', language: 'java', size: 14,
    ownership: 'manual', editable: true, reasonCode: null };
  const response = { messageType: 'query_result', schemaVersion: '1.0', requestId: operationProbeId,
    workspaceId: operationProbeWorkspace, operation: 'read_workspace_file', status: 'succeeded', revision: 7,
    data: { ...file, content: 'class Entry {}', sha256: 'a'.repeat(64) }, diagnostics: [] };
  assert.equal(query(response), true, JSON.stringify(query.errors));
  delete response.data.sha256;
  assert.equal(query(response), false, 'read cannot drop the concurrency fingerprint');
  response.operation = 'list_workspace_files';
  response.data = { files: [file], total: 1, nextOffset: null, truncated: false, maxFileBytes: 1048576 };
  assert.equal(query(response), true, JSON.stringify(query.errors));
  delete file.editable;
  assert.equal(query(response), false, 'file ownership must include the concrete editability decision');
  response.operation = 'get_workspace_source_index';
  response.data = { entries: [{ id: 'evidence-id', kind: 'registration', relativePath: 'src/main/java/Entry.java',
    line: 4, symbol: 'ITEM · Registry.register', evidence: 'Registry.register(...)' }], scannedFiles: 1, truncated: false };
  assert.equal(query(response), true, JSON.stringify(query.errors));
  response.data.entries[0].line = 0;
  assert.equal(query(response), false);
  response.status = 'rejected'; response.data = null;
  assert.equal(query(response), true, 'failures do not pretend to have a successful projection');
  const saved = { ...response, messageType: 'command_result', operation: 'update_workspace_file', status: 'committed',
    newRevision: 8, recoveryPointId: null, task: null, conflict: null, denial: null,
    data: { relativePath: 'src/main/java/Entry.java', sha256: 'b'.repeat(64), size: 0, changed: true } };
  delete saved.revision;
  assert.equal(command(saved), true, JSON.stringify(command.errors));
  delete saved.data.changed;
  assert.equal(command(saved), false);
});

test('asset preview and apply contracts preserve grant, plan-token and batch boundaries', async () => {
  const { ajv } = await createValidator();
  const command = ajv.getSchema('urn:ui-core:1.0:command');
  const query = ajv.getSchema('urn:ui-core:1.0:query');
  const destination = 'src/main/resources/assets/example/textures/block/copper.png';
  for (const operation of ['import_asset', 'import_asset_batch', 'move_asset']) {
    const request = operationRequest('command', operation, {
      clientMutationId: operationProbeId, taskAuthorizationId: operationProbeId, planToken: 'reviewed-plan'
    });
    assert.equal(command(request), true, `${operation}: ${JSON.stringify(command.errors)}`);
    if (operation !== 'move_asset') {
      request.payload.confirmReplace = false;
      assert.equal(command(request), true, JSON.stringify(command.errors));
      request.payload.confirmReplace = 'false';
      assert.equal(command(request), false, 'replacement confirmation must be a boolean');
      delete request.payload.confirmReplace;
    }
    delete request.payload.planToken;
    assert.equal(command(request), false, 'apply must reference a reviewed plan');
    request.payload.planToken = '';
    assert.equal(command(request), false, 'plan token must not be empty');
    request.payload.planToken = 'reviewed-plan';
    request.payload.sourcePath = 'C:/unreviewed.png';
    assert.equal(command(request), false, 'apply cannot replace the reviewed source with a file path');
  }
  const item = { sourceGrantId: 'selected-source-grant', targetRelativePath: destination };
  const request = operationRequest('query', 'preview_asset_import', { ...item });
  assert.equal(query(request), true, JSON.stringify(query.errors));
  delete request.payload.sourceGrantId;
  request.payload.sourcePath = 'C:/ungranted.png';
  assert.equal(query(request), false, 'preview requires a source grant, not an arbitrary file');
  request.operation = 'preview_asset_move';
  request.payload = { sourceAssetId: 'asset:models/block/copper.json', targetRelativePath: destination };
  assert.equal(query(request), true, JSON.stringify(query.errors));
  delete request.payload.sourceAssetId;
  assert.equal(query(request), false);
  request.operation = 'preview_asset_import_batch';
  request.payload = { items: Array.from({ length: 64 }, () => ({ ...item })) };
  assert.equal(query(request), true, JSON.stringify(query.errors));
  request.payload.items.push({ ...item });
  assert.equal(query(request), false, 'Core supports at most 64 items');
  request.payload.items = [];
  assert.equal(query(request), false, 'a batch must contain an item');
});

test('Procedure refactor contracts require the selected refactor inputs and a revision-bound plan', async () => {
  const { ajv } = await createValidator();
  const validate = ajv.getSchema('urn:ui-core:1.0:query');
  const variants = [
    { kind: 'extract_node', elementId: operationProbeId, nodeId: operationProbeId, newProcedureName: 'shared_logic' },
    { kind: 'replace_call_target', sourceProcedureId: operationProbeId, targetProcedureId: operationProbeId },
    { kind: 'replace_resource_target', sourceResource: 'example:copper', targetResource: 'example:iron' }
  ];
  for (const variant of variants) {
    const payload = { ...variant, expectedRevision: 7, idempotencyKey: 'refactor-request' };
    const request = operationRequest('query', 'plan_procedure_refactor', payload);
    assert.equal(validate(request), true, JSON.stringify(validate.errors));
    for (const field of Object.keys(payload)) {
      request.payload = { ...payload };
      delete request.payload[field];
      assert.equal(validate(request), false, `${variant.kind} requires ${field}`);
    }
    request.payload = { ...payload, expectedRevision: -1 };
    assert.equal(validate(request), false);
    request.payload = { ...payload, idempotencyKey: '' };
    assert.equal(validate(request), false);
  }
});

test('source management and local templates accept supported inputs and reject invalid selections', async () => {
  const { ajv } = await createValidator();
  const command = ajv.getSchema('urn:ui-core:1.0:command');
  const query = ajv.getSchema('urn:ui-core:1.0:query');
  const request = operationRequest('command', 'set_mod_element_source_management', {
    clientMutationId: operationProbeId, elementId: operationProbeId, mode: 'manual'
  });
  assert.equal(command(request), true, JSON.stringify(command.errors));
  request.payload.mode = 'generated'; request.payload.userApproved = true;
  assert.equal(command(request), true, JSON.stringify(command.errors));
  request.payload.mode = 'mixed';
  assert.equal(command(request), false);
  request.operation = 'create_local_template';
  request.payload = { clientMutationId: operationProbeId, templateName: 'copper_tools', elementIds: [operationProbeId] };
  assert.equal(command(request), true, JSON.stringify(command.errors));
  request.payload.elementIds = [];
  assert.equal(command(request), false, 'an empty template is rejected by Core');
  request.payload.assetPaths = ['src/main/resources/assets/example/textures/item/copper.png'];
  assert.equal(command(request), true, JSON.stringify(command.errors));
  request.payload.assetPaths = Array(65).fill('texture.png');
  assert.equal(command(request), false);
  request.payload.assetPaths = ['texture.png']; request.payload.templateName = '../outside';
  assert.equal(command(request), false);
  const preview = operationRequest('query', 'preview_local_template_instantiation', {
    templateName: 'copper_tools', expectedRevision: 7, idempotencyKey: 'template-request'
  });
  assert.equal(query(preview), true, JSON.stringify(query.errors));
  delete preview.payload.idempotencyKey;
  assert.equal(query(preview), false);
  const list = operationRequest('query', 'list_local_templates', {});
  assert.equal(query(list), true, JSON.stringify(query.errors));
  list.payload.directory = 'C:/elsewhere';
  assert.equal(query(list), false);
});

test('new operation responses accept success and failure envelopes', async () => {
  const { ajv } = await createValidator();
  for (const [kind, operations] of [
    ['command', ['import_asset', 'import_asset_batch', 'move_asset', 'set_mod_element_source_management', 'create_local_template']],
    ['query', ['preview_asset_import', 'preview_asset_import_batch', 'preview_asset_move', 'plan_procedure_refactor', 'list_local_templates', 'preview_local_template_instantiation']]
  ]) {
    const validate = ajv.getSchema(`urn:ui-core:1.0:${kind}-result`);
    for (const operation of operations) {
      const response = { messageType: `${kind}_result`, schemaVersion: '1.0', requestId: operationProbeId,
        workspaceId: operationProbeWorkspace, operation, diagnostics: [], data: {},
        ...(kind === 'command' ? { status: 'committed', newRevision: 8, recoveryPointId: null, task: null, conflict: null, denial: null }
          : { status: 'succeeded', revision: 7 }) };
      assert.equal(validate(response), true, `${operation}: ${JSON.stringify(validate.errors)}`);
      response.status = 'failed'; response.data = null;
      assert.equal(validate(response), true, `${operation}: ${JSON.stringify(validate.errors)}`);
    }
  }
});

test('element identity distinguishes internal names from generator resource IDs without requiring new fields in old clients', async () => {
  const { ajv } = await createValidator();
  const validate = ajv.compile({ $ref: 'urn:ui-core:1.0:common#/$defs/modElementSummary' });
  const element = { id: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaa31', type: 'block', name: 'contract_probe_v6',
    displayName: 'Contract Probe', state: 'valid', ownership: 'generated', updatedAt: '2026-09-20T00:00:00Z',
    diagnostics: { error: 0, warning: 0, info: 0 } };
  assert.equal(validate(element), true, JSON.stringify(validate.errors));
  element.identity = { internalName: 'contract_probe_v6', registryName: 'contract_probe_v_6',
    namespace: 'structured_forge', resourceId: 'structured_forge:contract_probe_v_6', source: 'generator_definition' };
  assert.equal(validate(element), true, JSON.stringify(validate.errors));
  element.identity.resourceId = 'structured_forge:InvalidName';
  assert.equal(validate(element), false);
  delete element.identity.resourceId;
  delete element.identity.namespace;
  element.ownership = 'manual';
  assert.equal(validate(element), true, JSON.stringify(validate.errors));
  const query = { messageType: 'query', schemaVersion: '1.0', requestId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaa31',
    workspaceId: 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', operation: 'list_mod_elements', payload: { limit: 100, fields: ['id', 'identity'] } };
  assert.equal(ajv.getSchema('urn:ui-core:1.0:query')(query), true);
});

test('Blockbench discovery accepts explicit probing and rejects unexpected execution options', async () => {
  const { ajv } = await createValidator();
  const validate = ajv.getSchema('urn:ui-core:1.0:query');
  const query = {
    messageType: 'query', schemaVersion: '1.0',
    requestId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaa31',
    workspaceId: 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
    operation: 'get_blockbench_environment', payload: {}
  };
  assert.equal(validate(query), true, JSON.stringify(validate.errors));
  query.payload = { probeMcp: true, endpoint: 'http://127.0.0.1:3000/bb-mcp' };
  assert.equal(validate(query), true, JSON.stringify(validate.errors));
  query.payload.probeMcp = 'true';
  assert.equal(validate(query), false);
  query.payload = { launch: true };
  assert.equal(validate(query), false);
});

test('modeling tasks require a single source and a saved file hash for completion', async () => {
  const { ajv } = await createValidator();
  const validate = ajv.getSchema('urn:ui-core:1.0:command');
  const request = { messageType: 'command', schemaVersion: '1.0', requestId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaa31',
    workspaceId: 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', expectedRevision: 0, operation: 'begin_blockbench_task',
    payload: { taskId: 'cccccccc-cccc-4ccc-8ccc-cccccccccccc', targetRelativePath: 'models/test.bbmodel' } };
  assert.equal(validate(request), true, JSON.stringify(validate.errors));
  request.payload.assetId = 'asset:example';
  assert.equal(validate(request), false);
  delete request.payload.targetRelativePath;
  assert.equal(validate(request), true, JSON.stringify(validate.errors));
  request.payload.elementId = 'dddddddd-dddd-4ddd-8ddd-dddddddddddd';
  assert.equal(validate(request), true, JSON.stringify(validate.errors));
  delete request.payload.assetId;
  assert.equal(validate(request), true, JSON.stringify(validate.errors));
  delete request.payload.elementId;
  assert.equal(validate(request), false);
  request.operation = 'finish_blockbench_task';
  delete request.payload.assetId;
  assert.equal(validate(request), false);
  request.payload.savedSha256 = 'a'.repeat(64);
  assert.equal(validate(request), true, JSON.stringify(validate.errors));
  request.payload.launch = true;
  assert.equal(validate(request), false);
});

test('modeling query results describe persisted context and independent binding while accepting legacy tasks', async () => {
  const { ajv } = await createValidator();
  const validate = ajv.getSchema('urn:ui-core:1.0:query-result');
  const task = { taskId: 'cccccccc-cccc-4ccc-8ccc-cccccccccccc', workspaceId: 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
    targetRelativePath: 'models/blockbench/lamp.bbmodel', openedRevision: 0, state: 'editing', editSha256: null };
  const response = { messageType: 'query_result', schemaVersion: '1.0', requestId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaa31',
    workspaceId: task.workspaceId, operation: 'get_blockbench_task', status: 'succeeded', revision: 0, diagnostics: [], data: task };
  assert.equal(validate(response), true, JSON.stringify(validate.errors));
  task.elementContext = { elementId: 'dddddddd-dddd-4ddd-8ddd-dddddddddddd', name: 'lamp', type: 'block',
    namespace: 'example', modelResource: 'example:custom/lamp', textureDirectory: 'src/main/resources/assets/example/textures/block' };
  for (const state of ['unbound', 'manual', 'element_missing']) {
    task.binding = { state };
    assert.equal(validate(response), true, JSON.stringify(validate.errors));
  }
  task.binding = { state: 'bound' };
  assert.equal(validate(response), false);
  task.binding.modelResource = 'example:custom/lamp';
  task.state = 'imported';
  assert.equal(validate(response), true, JSON.stringify(validate.errors));
  task.binding.state = 'succeeded';
  assert.equal(validate(response), false);
  task.binding.state = 'bound';
  task.elementContext.type = 'procedure';
  assert.equal(validate(response), false);
  task.elementContext.type = 'block';
  response.operation = 'list_blockbench_tasks'; response.data = { tasks: [task] };
  assert.equal(validate(response), true, JSON.stringify(validate.errors));
  task.editSha256 = 'invalid';
  assert.equal(validate(response), false);
  response.status = 'failed'; response.data = null;
  assert.equal(validate(response), true, JSON.stringify(validate.errors));
});

test('all UI-Core schemas compile and all mock scenarios validate', async () => {
  const result = await validateAll();
  assert.ok(result.schemaCount >= 9);
  assert.ok(result.fixtureCount >= 13);
  assert.deepEqual(result.failures, []);
});

test('modeling imports require concrete mappings, a preview token, and typed replacement consent', async () => {
  const { ajv } = await createValidator();
  const query = { messageType: 'query', schemaVersion: '1.0', requestId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaa31',
    workspaceId: 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', operation: 'preview_blockbench_import',
    payload: { taskId: 'cccccccc-cccc-4ccc-8ccc-cccccccccccc', outputs: [] } };
  const validateQuery = ajv.getSchema('urn:ui-core:1.0:query');
  assert.equal(validateQuery(query), false);
  query.payload.outputs.push({ sourceRelativePath: 'export/lamp.json', targetRelativePath: 'src/main/resources/assets/test/models/custom/lamp.json' });
  assert.equal(validateQuery(query), true, JSON.stringify(validateQuery.errors));
  query.payload.outputs[0].execute = 'export';
  assert.equal(validateQuery(query), false);
  const command = { ...query, messageType: 'command', expectedRevision: 0, operation: 'import_blockbench_task',
    payload: { taskId: query.payload.taskId } };
  const validateCommand = ajv.getSchema('urn:ui-core:1.0:command');
  assert.equal(validateCommand(command), false);
  command.payload.planToken = 'dddddddd-dddd-4ddd-8ddd-dddddddddddd';
  command.payload.confirmReplace = true;
  assert.equal(validateCommand(command), true, JSON.stringify(validateCommand.errors));
  command.payload.confirmReplace = 'true';
  assert.equal(validateCommand(command), false);
});

test('Stage17 auto mapping, external references and verified exports match the published contract', async () => {
  const { ajv } = await createValidator();
  const query = { messageType: 'query', schemaVersion: '1.0', requestId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaa31',
    workspaceId: 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', operation: 'preview_blockbench_import',
    payload: { taskId: 'cccccccc-cccc-4ccc-8ccc-cccccccccccc', elementId: 'dddddddd-dddd-4ddd-8ddd-dddddddddddd' } };
  const validateQuery = ajv.getSchema('urn:ui-core:1.0:query');
  assert.equal(validateQuery(query), true, JSON.stringify(validateQuery.errors));
  delete query.payload.elementId;
  assert.equal(validateQuery(query), false);
  const command = { ...query, messageType: 'command', operation: 'export_workspace', expectedRevision: 0,
    payload: { clientMutationId: query.requestId, scope: 'workspace', verifiedTaskId: query.payload.taskId, allowHistorical: true } };
  const validateCommand = ajv.getSchema('urn:ui-core:1.0:command');
  assert.equal(validateCommand(command), true, JSON.stringify(validateCommand.errors));
  command.payload.allowHistorical = 'true';
  assert.equal(validateCommand(command), false);
  const asset = await schema('asset');
  const validateReference = ajv.compile(asset.$defs.reference);
  assert.equal(validateReference({ sourceAssetId: `asset:${'a'.repeat(64)}`, sourcePath: 'assets/mod/models/test.json',
    sourcePointer: '/parent', rawValue: 'minecraft:block/cube_all', targetPath: 'assets/minecraft/models/block/cube_all.json',
    targetAssetId: null, kind: 'RESOURCE_ID', resolution: 'vanilla_resolved', resourceSource: 'minecraft-client.jar', resourceVersion: '1.21.1' }), true);
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
  query.payload.sourcePath = '/src/main/java/dev/coppertrails/procedure/AnnounceTrailProcedure.java';
  assert.equal(validate(query), true, JSON.stringify(validate.errors));
  query.payload.sourcePath = '../workspace.mcreator';
  assert.equal(validate(query), false, 'task source previews must stay on generated Java paths');
  delete query.payload.sourcePath;
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

test('task authority schemas accept scoped grants and reject invalid lifetime or untrusted internal flags', async () => {
  const { ajv } = await createValidator();
  const validate = ajv.getSchema('urn:ui-core:1.0:command');
  const command = {
    messageType: 'command', schemaVersion: '1.0', requestId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaa41',
    workspaceId: 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', expectedRevision: 3,
    operation: 'create_task_authorization', payload: {
      clientMutationId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaa42', label: 'Mod trial', root: 'D:/ModProjects/trial',
      capabilities: ['edit', 'build', 'test'], ttlSeconds: 3600, serverEulaAccepted: false, userApproved: true
    }
  };
  assert.equal(validate(command), true, JSON.stringify(validate.errors));
  command.payload.ttlSeconds = 86401;
  assert.equal(validate(command), false);
  command.operation = 'run_server';
  command.payload = { clientMutationId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaa42', scope: 'workspace',
    taskAuthorizationId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaa43' };
  assert.equal(validate(command), true, JSON.stringify(validate.errors));
  command.operation = 'run_gametest'; command.payload.serverEulaAuthorized = true;
  assert.equal(validate(command), false);
});

test('task summaries carry bounded verification and source identity including pending and stale evidence', async () => {
  const { ajv } = await createValidator();
  const validate = ajv.compile({ $ref: 'urn:ui-core:1.0:common#/$defs/taskSummary' });
  const task = {
    id: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaa44', kind: 'run_gametest', state: 'failed', cancellable: false,
    progress: 1, stage: { key: 'task.failed', fallback: 'Failed' }, startedAt: '2026-09-11T03:00:00Z',
    diagnostics: { error: 1, warning: 0, info: 0 },
    sourceSnapshot: { sha256: 'a'.repeat(64), fileCount: 5, bytes: 512, manifestPath: 'D:/trial/source-manifest.json' },
    verification: { schemaVersion: '1.0', status: 'failed', reasonCode: 'GAMETEST_SOURCE_CHANGED', discovered: 1,
      executed: 1, passed: 1, failed: 0, skipped: 0, cases: [{ name: 'behavior', className: 'Acceptance', status: 'passed' }],
      artifactSha256: 'b'.repeat(64), sourceCurrentAtCompletion: false }
  };
  assert.equal(validate(task), true, JSON.stringify(validate.errors));
  task.verification.status = 'pending';
  assert.equal(validate(task), true, JSON.stringify(validate.errors));
  task.verification.executed = -1;
  assert.equal(validate(task), false);
  task.verification.executed = 1; task.verification.artifactSha256 = 'unbound';
  assert.equal(validate(task), false);
});

test('doctor query is read-only and observations cannot imply a network probe or authorization', async () => {
  const { ajv } = await createValidator();
  const query = ajv.getSchema('urn:ui-core:1.0:query');
  assert.equal(query(operationRequest('query', 'get_workspace_doctor', {})), true);
  for (const payload of [{ probeNetwork: true }, { approve: true }, { download: false }]) {
    assert.equal(query(operationRequest('query', 'get_workspace_doctor', payload)), false);
  }
  const validate = ajv.getSchema('urn:ui-core:1.0:workspace-doctor');
  const report = { schemaVersion:'1.0', scope:'local_observation', readOnly:true, networkProbed:false,
    generatorId:'fabric-1.21.1', workspaceRoot:'/tmp/workspace', processWorkingDirectory:'/opt/copperbench',
    application:{}, execution:{}, status:'unknown', capabilities:{ coreSchemaVersion:'1.0',
      doctorSchemaVersion:'1.0', queries:['get_workspace_doctor'], implicitNetworkProbe:false, implicitTaskAuthorization:false },
    findings: ['product_java','workspace_java','workspace_directory','backend','wrapper','cache','network','renderer'].map(id =>
      ({ id, status:'unknown', code:'NOT_OBSERVED', message:'No evidence', nextStep:'Inspect the declared environment', details:{} })) };
  assert.equal(validate(report), true, JSON.stringify(validate.errors));
  for (const value of ['missing','unsupported','blocked','available']) {
    report.findings[0].status = value;
    assert.equal(validate(report), true, JSON.stringify(validate.errors));
  }
  report.networkProbed = true;
  assert.equal(validate(report), false);
  report.networkProbed = false;
  report.findings[0].status = 'assumed';
  assert.equal(validate(report), false);
});
