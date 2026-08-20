import { readFile, readdir } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import Ajv2020 from 'ajv/dist/2020.js';
import addFormats from 'ajv-formats';

const moduleDirectory = path.dirname(fileURLToPath(import.meta.url));
const packageRoot = path.resolve(moduleDirectory, '..');
async function readJson(filePath) {
  return JSON.parse(await readFile(filePath, 'utf8'));
}

export async function createValidator(version = '1.0') {
	const schemaDirectory = path.join(packageRoot, 'schemas', `v${version}`);
  const ajv = new Ajv2020({ allErrors: true, strict: true, allowUnionTypes: true });
  addFormats(ajv);

  const schemaFiles = (await readdir(schemaDirectory))
    .filter((name) => name.endsWith('.schema.json'))
    .sort();
  for (const name of schemaFiles) {
    ajv.addSchema(await readJson(path.join(schemaDirectory, name)));
  }

  return { ajv, schemaCount: schemaFiles.length };
}

export async function validateAll(version = '1.0') {
	const { ajv, schemaCount } = await createValidator(version);
	const fixtureDirectory = path.join(packageRoot, 'fixtures', `v${version}`, 'scenarios');

	const validateScenario = ajv.getSchema(`urn:ui-core:${version}:scenario`);
  if (!validateScenario) {
    throw new Error('Scenario schema was not registered');
  }

  const fixtureFiles = (await readdir(fixtureDirectory))
    .filter((name) => name.endsWith('.json'))
    .sort();
  const failures = [];
  const scenarios = new Map();
  for (const name of fixtureFiles) {
    const document = await readJson(path.join(fixtureDirectory, name));
    if (!validateScenario(document)) {
      failures.push({ file: name, errors: structuredClone(validateScenario.errors) });
      continue;
    }
    if (scenarios.has(document.scenarioId)) {
      failures.push({ file: name, errors: [{ message: `duplicate scenarioId ${document.scenarioId}` }] });
      continue;
    }
    scenarios.set(document.scenarioId, { file: name, document });
  }

  for (const { file, document } of scenarios.values()) {
    if (document.extendsScenarioId !== null && !scenarios.has(document.extendsScenarioId)) {
      failures.push({ file, errors: [{ message: `unknown extendsScenarioId ${document.extendsScenarioId}` }] });
      continue;
    }
    const visited = new Set([document.scenarioId]);
    let parentId = document.extendsScenarioId;
    while (parentId !== null) {
      if (visited.has(parentId)) {
        failures.push({ file, errors: [{ message: `scenario inheritance cycle through ${parentId}` }] });
        break;
      }
      visited.add(parentId);
      parentId = scenarios.get(parentId).document.extendsScenarioId;
    }
  }

	return {
		version,
    schemaCount,
    fixtureCount: fixtureFiles.length,
    failures,
  };
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const result = await validateAll();
  if (result.failures.length > 0) {
    console.error(JSON.stringify(result, null, 2));
    process.exitCode = 1;
  } else {
    console.log(`Validated ${result.schemaCount} schemas and ${result.fixtureCount} scenarios.`);
  }
}
