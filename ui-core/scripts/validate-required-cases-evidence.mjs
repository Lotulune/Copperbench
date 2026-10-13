import { readFile } from 'node:fs/promises';
import path from 'node:path';
import { createValidator } from './validate.mjs';

const root = process.argv[2];
if (!root) throw new Error('Provide the completed Native evidence directory.');
const { ajv } = await createValidator();
ajv.addSchema(JSON.parse(await readFile(new URL('../schemas/extensions/required-cases-v1.schema.json', import.meta.url), 'utf8')));
const extension = ajv.getSchema('urn:copperbench:required-cases:1#/$defs/response');
const legacy = ajv.getSchema('urn:ui-core:1.0:query-result');
const checks = [];
for (const name of ['before-reconnect', 'after-reconnect', 'missing-evidence']) {
  const value = JSON.parse(await readFile(path.join(root, name + '.json'), 'utf8'));
  if (!extension(value)) throw new Error(name + ': ' + JSON.stringify(extension.errors));
  checks.push({ name, schema: 'required_cases/1', status: 'passed' });
}
for (const name of ['required-pass', 'required-export', 'required-missing', 'missing-export-rejected']) {
  const value = JSON.parse(await readFile(path.join(root, name + '.json'), 'utf8'));
  if (!legacy(value)) throw new Error(name + ': ' + JSON.stringify(legacy.errors));
  checks.push({ name, schema: 'UI-Core/1.0/query-result', status: 'passed' });
}
for (const name of ['unknown-version', 'changed-digest']) {
  const value = JSON.parse(await readFile(path.join(root, name + '.json'), 'utf8'));
  if (!extension(value.details)) throw new Error(name + ': ' + JSON.stringify(extension.errors));
  checks.push({ name, schema: 'required_cases/1 error', status: 'passed' });
}
console.log(JSON.stringify({ status: 'passed', checks }, null, 2));
