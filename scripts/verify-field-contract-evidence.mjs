import assert from 'node:assert/strict';
import { readFile, readdir } from 'node:fs/promises';
import { createRequire } from 'node:module';
import { createValidator } from '../ui-core/scripts/validate.mjs';

const require = createRequire(new URL('../ui-core/package.json', import.meta.url));
const Ajv = require('ajv/dist/2020.js');
const { ajv } = await createValidator();
const envelope = ajv.getSchema('urn:ui-core:1.0:query-result');
// Input schemas carry explicit annotations for generator/reference/context rules.
const inputs = new Ajv({ strict: false, allErrors: true, allowUnionTypes: true });
const directory = new URL('../build/reports/m1-discovery/', import.meta.url);
const files = (await readdir(directory)).filter(name => name.endsWith('.json')).sort();
assert.equal(files.length, 16, 'Eight tracks times two contracts must have actual adapter observations');
let values = 0;
for (const file of files) {
  const result = JSON.parse(await readFile(new URL(file, directory), 'utf8'));
  assert.ok(envelope(result), file + ': ' + JSON.stringify(envelope.errors));
  const contract = result.data;
  const validateName = inputs.compile(contract.createNameSchema);
  assert.ok(validateName(contract.minimalExample.name), file + ': example identity must obey the creation validator');
  const fields = new Map(contract.fields.map(field => [field.path.slice(1), field]));
  for (const [name, value] of Object.entries(contract.minimalExample.initialValues)) {
    const validate = inputs.compile(fields.get(name).inputSchema);
    assert.ok(validate(value), file + '/' + name + ': ' + JSON.stringify(validate.errors));
    values++;
  }
  for (const field of contract.fields) {
    if (!Object.hasOwn(field, 'default')) continue;
    const validate = inputs.compile(field.inputSchema);
    assert.ok(validate(field.default), file + field.path + ' default: ' + JSON.stringify(validate.errors));
    values++;
  }
}
console.log(JSON.stringify({ status: 'passed', adapterEnvelopes: files.length, exampleAndDefaultValues: values }));
