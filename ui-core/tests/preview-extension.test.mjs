import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { createValidator } from '../scripts/validate.mjs';

async function validators() {
  const { ajv } = await createValidator();
  for (const file of ['required-cases-v1', 'preview-targets-v1'])
    ajv.addSchema(JSON.parse(await readFile(new URL(`../schemas/extensions/${file}.schema.json`, import.meta.url), 'utf8')));
  return ajv;
}

test('preview is separately negotiated and scoped; old command objects remain strict', async () => {
  const ajv = await validators();
  const validate = ajv.getSchema('urn:copperbench:preview-targets:1#/$defs/request');
  for (const request of [
    { operation: 'catalog', supportedVersions: ['1'] },
    { operation: 'opt_in', generatorId: 'fabric-26.3', expectedRevision: 3, supportedVersions: ['1'] },
    { operation: 'command', generatorId: 'fabric-26.3', expectedRevision: 3, supportedVersions: ['1'], command: 'build_workspace', payload: {} }
  ]) assert.equal(validate(request), true, JSON.stringify(validate.errors));
  for (const request of [
    { operation: 'catalog' },
    { operation: 'catalog', supportedVersions: ['1'], userApproved: true },
    { operation: 'opt_in', supportedVersions: ['1'], expectedRevision: 3 },
    { operation: 'command', generatorId: 'fabric-26.3', supportedVersions: ['1'], command: 'build_workspace' },
    { operation: 'revoke', generatorId: 'fabric-26.3', expectedRevision: 1.5, supportedVersions: ['1'] },
  ]) assert.equal(validate(request), false, JSON.stringify(request));
  const legacy = ajv.getSchema('urn:ui-core:1.0:command');
  assert.equal(legacy({ messageType: 'command', schemaVersion: '1.0', requestId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
    workspaceId: 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', expectedRevision: 0, operation: 'build_workspace',
    payload: { scope: 'workspace', clientMutationId: 'cccccccc-cccc-4ccc-8ccc-cccccccccccc', previewOptIn: true } }), false);
});

test('preview evidence cannot claim ready without reason and scope fields', async () => {
  const ajv = await validators();
  const validate = ajv.getSchema('urn:copperbench:preview-targets:1#/$defs/response');
  const response = { contract: 'preview_targets', schemaVersion: '1', selectedVersion: '1',
    requestId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', workspaceId: 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
    status: 'succeeded', revision: 0, diagnostics: [], targets: [] };
  assert.equal(validate(response), true, JSON.stringify(validate.errors));
  assert.equal(validate({ ...response, implicitlyAuthorized: true }), false);
  response.targets.push({ generatorId: 'fabric-26.3', capabilities: { supportStage: 'supported' } });
  assert.equal(validate(response), false);
});
