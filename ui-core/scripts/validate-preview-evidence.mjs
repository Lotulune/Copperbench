import { readFile } from 'node:fs/promises';
import { createValidator } from './validate.mjs';
const { ajv } = await createValidator();
for (const file of ['required-cases-v1', 'preview-targets-v1'])
  ajv.addSchema(JSON.parse(await readFile(new URL(`../schemas/extensions/${file}.schema.json`, import.meta.url), 'utf8')));
const validate = ajv.getSchema('urn:copperbench:preview-targets:1#/$defs/response');
const checks = [];
if (process.argv.length < 3) throw new Error('Provide Native/MCP preview receipt arrays.');
for (const file of process.argv.slice(2)) {
  const responses = JSON.parse(await readFile(file, 'utf8'));
  for (const [index, response] of responses.entries()) {
    if (!validate(response)) throw new Error(`${file}[${index}]: ${JSON.stringify(validate.errors)}`);
    checks.push({ file, index, status: 'passed' });
  }
}
console.log(JSON.stringify({ status: 'passed', checks }, null, 2));
