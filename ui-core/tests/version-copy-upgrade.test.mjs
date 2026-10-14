import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { createValidator } from '../scripts/validate.mjs';

async function validators() {
  const { ajv } = await createValidator();
  ajv.addSchema(JSON.parse(await readFile(new URL('../schemas/extensions/version-copy-upgrade-v1.schema.json', import.meta.url), 'utf8')));
  ajv.addSchema(JSON.parse(await readFile(new URL('../schemas/extensions/version-copy-upgrade-v2.schema.json', import.meta.url), 'utf8')));
  return ajv;
}

test('copy upgrade requests require negotiation and cannot smuggle write parameters', async () => {
  const ajv = await validators(); const validate = ajv.getSchema('urn:copperbench:version-copy-upgrade:1#/$defs/request');
  assert.ok(validate({ supportedVersions: ['1'], operation: 'preview', targetGeneratorId: 'fabric-26.3' }));
  assert.ok(validate({ supportedVersions: ['1'], operation: 'negotiate' }));
  for (const request of [
    { operation: 'preview', targetGeneratorId: 'fabric-26.3' },
    { supportedVersions: ['1'], operation: 'execute' },
    { supportedVersions: ['1'], operation: 'preview', targetGeneratorId: 'fabric-26.3', targetDirectory: '../copy' },
    { supportedVersions: ['1'], operation: 'preview', targetGeneratorId: 'fabric-26.3', userApproved: true }
  ]) assert.equal(validate(request), false, JSON.stringify(request));
});

test('version 2 execution requires the bound plan, revision, exact target and new directory name', async () => {
  const ajv = await validators(); const validate = ajv.getSchema('urn:copperbench:version-copy-upgrade:2#/$defs/request');
  const request = { supportedVersions: ['2'], operation: 'execute', targetGeneratorId: 'fabric-26.3', outputName: 'upgraded-copy',
    planId: 'a'.repeat(64), expectedRevision: 1, taskAuthorizationId: 'grant' };
  assert.ok(validate(request), JSON.stringify(validate.errors));
  for (const key of ['supportedVersions', 'planId', 'expectedRevision', 'outputName']) {
    const missing = structuredClone(request); delete missing[key]; assert.equal(validate(missing), false, key);
  }
  for (const changed of [{ outputName: '../outside' }, { expectedRevision: 1.5 }, { targetDirectory: '/unbound' }, { supportedVersions: ['1'] }])
    assert.equal(validate({ ...request, ...changed }), false);
});

for (const loader of ['fabric', 'neoforge']) test(`actual version 2 ${loader} plan is independently negotiated and cannot be mistaken for a v1 plan`, async () => {
  const ajv = await validators();
  const response = JSON.parse(await readFile(new URL(`../fixtures/extensions/version-copy-upgrade/${loader}-executable-preview.json`, import.meta.url), 'utf8'));
  const validate = ajv.getSchema('urn:copperbench:version-copy-upgrade:2#/$defs/response');
  assert.ok(validate(response), JSON.stringify(validate.errors));
  assert.equal(response.plan.targetGeneratorId, `${loader}-26.3`);
  assert.equal(response.plan.ruleVersion, `${loader}-26.2-to-26.3-function-v1`);
  assert.ok(response.plan.executionAvailable); assert.equal(response.plan.counts.manual, 0);
  assert.equal(ajv.getSchema('urn:copperbench:version-copy-upgrade:1#/$defs/response')(response), false);
  const altered = structuredClone(response); delete altered.plan.targetPluginFingerprint; assert.equal(validate(altered), false);
});

test('actual Core plans bind source identity and keep unavailable conversions explicit', async () => {
  const ajv = await validators(); const validate = ajv.getSchema('urn:copperbench:version-copy-upgrade:1#/$defs/response');
  for (const loader of ['fabric', 'neoforge']) {
    const response = JSON.parse(await readFile(new URL(`../fixtures/extensions/version-copy-upgrade/${loader}-preview.json`, import.meta.url), 'utf8'));
    assert.ok(validate(response), JSON.stringify(validate.errors));
    const plan = response.plan;
    assert.equal(plan.sourceGeneratorId, `${loader}-26.2`); assert.equal(plan.targetGeneratorId, `${loader}-26.3`);
    for (const kind of ['convertible', 'manual', 'unsupported']) assert.equal(plan.counts[kind], plan.entries.filter(e => e.disposition === kind).length);
    const wrong = structuredClone(response); wrong.plan.executionAvailable = true; assert.equal(validate(wrong), false);
    const missing = structuredClone(response); delete missing.plan.sourceFingerprint; assert.equal(validate(missing), false);
    const guessed = structuredClone(response); guessed.plan.entries[0].disposition = 'passed'; assert.equal(validate(guessed), false);
  }
});
