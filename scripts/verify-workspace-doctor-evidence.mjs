import { readFile, readdir } from 'node:fs/promises';
import { createValidator } from '../ui-core/scripts/validate.mjs';

const directory = new URL('../build/reports/workspace-doctor/', import.meta.url);
const { ajv } = await createValidator();
const validate = ajv.getSchema('urn:ui-core:1.0:workspace-doctor');
const reports = (await readdir(directory)).filter(name => name.endsWith('.json'));
if (!reports.length) throw new Error('No actual doctor observations were produced');
for (const name of reports) {
  const report = JSON.parse(await readFile(new URL(name, directory), 'utf8'));
  if (!validate(report)) throw new Error(name+': '+JSON.stringify(validate.errors));
}
console.log('Validated '+reports.length+' actual doctor observations against the shared schema.');
