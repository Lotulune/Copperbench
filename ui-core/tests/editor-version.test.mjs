import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { createValidator } from '../scripts/validate.mjs';

async function validators() {
  const { ajv } = await createValidator();
  ajv.addSchema(JSON.parse(await readFile(new URL('../schemas/v1.1/editor.schema.json', import.meta.url), 'utf8')));
  return ajv;
}

test('editor 1.1 is an explicit operation version and rejects unrelated requests', async () => {
  const ajv = await validators(); const validate = ajv.getSchema('urn:ui-core:1.1:editor#/$defs/request');
  const request = { messageType: 'query', schemaVersion: '1.1', operation: 'get_mod_element_editor',
    requestId: '11111111-1111-4111-8111-111111111111', workspaceId: '22222222-2222-4222-8222-222222222222',
    payload: { elementId: '33333333-3333-4333-8333-333333333333' } };
  assert.ok(validate(request), JSON.stringify(validate.errors));
  for (const changed of [{ schemaVersion: '1.0' }, { operation: 'get_workbench' }, { payload: {} },
    { payload: { ...request.payload, userApproved: true } }]) assert.equal(validate({ ...request, ...changed }), false);
});

for (const loader of ['fabric', 'neoforge']) test(`actual ${loader} editor responses satisfy their selected version without loosening 1.0`, async () => {
  const ajv = await validators(); const legacy = ajv.getSchema('urn:ui-core:1.0:query-result');
  const rich = ajv.getSchema('urn:ui-core:1.1:editor#/$defs/response');
  for (const type of ['item', 'block', 'recipe', 'function', 'code']) {
    const read = async version => JSON.parse(await readFile(new URL(`../fixtures/v1.1/editor/${loader}-26.3/${type}-${version}.json`, import.meta.url), 'utf8'));
    const old = await read('1.0'); const current = await read('1.1');
    assert.ok(legacy(old), `${type}: ${JSON.stringify(legacy.errors)}`);
    assert.ok(rich(current), `${type}: ${JSON.stringify(rich.errors)}`);
    assert.equal(legacy({ ...current, schemaVersion: '1.0' }), false);
    const unknown = structuredClone(current); unknown.data.configuration.unnegotiatedFlag = true;
    assert.equal(rich(unknown), false);
    assert.deepEqual(old.data.element, current.data.element);
  }
});
