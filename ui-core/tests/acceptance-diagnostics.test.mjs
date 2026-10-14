import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile, readdir } from 'node:fs/promises';
import { createValidator } from '../scripts/validate.mjs';

test('acceptance diagnostic projection is opt-in and all Core replay states preserve structured counts', async () => {
  const { ajv } = await createValidator();
  ajv.addSchema(JSON.parse(await readFile(new URL('../schemas/extensions/required-cases-v1.schema.json', import.meta.url), 'utf8')));
  const request = ajv.getSchema('urn:copperbench:required-cases:1#/$defs/request');
  const value = { supportedVersions: ['1'], diagnosticVersions: ['1'], operation: 'get', payload: { taskId: '33333333-3333-4333-8333-333333333333' } };
  assert.equal(request(value), true, JSON.stringify(request.errors));
  assert.equal(request({ ...value, diagnosticVersions: [] }), false);
  assert.equal(request({ ...value, operation: 'catalog' }), false);
  const diagnostic = ajv.getSchema('urn:copperbench:required-cases:1#/$defs/coverageDiagnostics');
  const directory = new URL('../fixtures/extensions/acceptance-diagnostics/', import.meta.url);
  const states = new Set();
  for (const name of (await readdir(directory)).filter(name => name.endsWith('.json'))) {
    const data = JSON.parse(await readFile(new URL(name, directory), 'utf8'));
    assert.equal(diagnostic(data), true, `${name}: ${JSON.stringify(diagnostic.errors)}`);
    states.add(data.state);
    assert.equal(diagnostic({ ...data, schemaVersion: '99' }), false);
  }
  assert.equal(states.size, 9);
});
