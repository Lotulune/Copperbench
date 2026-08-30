import { existsSync, readFileSync, readdirSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const evidenceDirIndex = process.argv.indexOf('--evidence-dir');
if (evidenceDirIndex >= 0 && !process.argv[evidenceDirIndex + 1]) {
  throw new Error('EXTERNAL_TESTER_EVIDENCE_INVALID: --evidence-dir requires a path');
}
const evidenceRoot = evidenceDirIndex >= 0
  ? resolve(root, process.argv[evidenceDirIndex + 1])
  : resolve(root, 'evidence/stage-9/external-testers');
const requireComplete = process.argv.includes('--require-complete');
const fail = (message) => { throw new Error(`EXTERNAL_TESTER_EVIDENCE_INVALID: ${message}`); };
let evidenceSchema;
try {
  evidenceSchema = JSON.parse(readFileSync(resolve(root, 'schemas/external-tester-evidence.schema.json'), 'utf8'));
} catch (error) {
  fail(`cannot read external tester schema: ${error.message}`);
}
const requiredFields = (value, label) => {
  if (!Array.isArray(value) || value.length === 0) fail(`schema ${label} required fields are missing`);
  return value;
};
const recordKeys = requiredFields(evidenceSchema.required, 'record');
const sourceSchema = evidenceSchema.properties?.source;
const environmentSchema = evidenceSchema.properties?.environment;
const taskSchema = evidenceSchema.properties?.tasks;
const issueSchema = evidenceSchema.properties?.issues?.items;
if (!sourceSchema || !environmentSchema || !taskSchema || !issueSchema) fail('schema contract sections are missing');
const sourceKeysAllowed = requiredFields(sourceSchema.required, 'source');
const environmentKeys = requiredFields(environmentSchema.required, 'environment');
const taskNames = requiredFields(taskSchema.required, 'tasks');
const issueKeys = requiredFields(issueSchema.required, 'issue');
const issueAreas = new Set(issueSchema.properties?.area?.enum ?? []);
const priorities = new Set(issueSchema.properties?.severity?.enum ?? []);
const packageTypes = new Set(sourceSchema.properties?.packageType?.enum ?? []);
const schemaVersion = evidenceSchema.properties?.schemaVersion?.const;
const requiredArchitecture = environmentSchema.properties?.architecture?.const;
const developerToolsSchema = environmentSchema.properties?.preinstalledDeveloperTools;
const option = (name) => {
  const index = process.argv.indexOf(name);
  if (index < 0) return null;
  if (!process.argv[index + 1]) fail(`${name} requires a value`);
  return process.argv[index + 1];
};
const expectedCommit = option('--expected-commit');
const expectedInstallerSha256 = option('--expected-installer-sha256');
if (requireComplete && (!expectedCommit || !expectedInstallerSha256)) {
  fail('--require-complete requires --expected-commit and --expected-installer-sha256');
}
if (expectedCommit && !/^[0-9a-f]{40}$/.test(expectedCommit)) fail('--expected-commit must be a full lowercase SHA');
if (expectedInstallerSha256 && !/^[0-9a-f]{64}$/.test(expectedInstallerSha256)) {
  fail('--expected-installer-sha256 must be a lowercase SHA-256');
}
const personalDataPattern = /(?:[a-z]:\\users\\|\/users\/|\/home\/|[\w.+-]+@[\w.-]+\.[a-z]{2,}|bearer\s+\S+|(?:token|password|secret)\s*[:=])/i;
const assertKeys = (value, required, file, label) => {
  if (!value || typeof value !== 'object' || Array.isArray(value)) fail(`${file}: ${label} must be an object`);
  const actual = Object.keys(value).sort();
  const expected = [...required].sort();
  if (actual.length !== expected.length || actual.some((key, index) => key !== expected[index])) {
    fail(`${file}: ${label} fields must be exactly ${expected.join(', ')}`);
  }
};
const stringValues = (value) => {
  if (typeof value === 'string') return [value];
  if (Array.isArray(value)) return value.flatMap(stringValues);
  if (value && typeof value === 'object') return Object.values(value).flatMap(stringValues);
  return [];
};

const files = existsSync(evidenceRoot)
  ? readdirSync(evidenceRoot).filter((file) => file.endsWith('.json')).sort()
  : [];
const records = files.map((file) => {
  try { return JSON.parse(readFileSync(resolve(evidenceRoot, file), 'utf8')); }
  catch (error) { fail(`${file} is not valid JSON: ${error.message}`); }
});

const testerIds = new Set();
const sourceKeys = new Set();
for (let index = 0; index < records.length; index += 1) {
  const record = records[index];
  const file = files[index];
  assertKeys(record, recordKeys, file, 'record');
  assertKeys(record.source, sourceKeysAllowed, file, 'source');
  assertKeys(record.environment, environmentKeys, file, 'environment');
  assertKeys(record.tasks, taskNames, file, 'tasks');
  if (stringValues(record).some((value) => personalDataPattern.test(value))) {
    fail(`${file}: possible personal data or credential found`);
  }
  if (record.schemaVersion !== schemaVersion) fail(`${file}: schemaVersion must be ${schemaVersion}`);
  if (!/^tester-[0-9a-f]{8}$/.test(record.testerId ?? '')) fail(`${file}: testerId is invalid`);
  if (testerIds.has(record.testerId)) fail(`${file}: testerId is duplicated`);
  testerIds.add(record.testerId);
  if (record.nonCoreDeveloper !== true || record.privacyConfirmed !== true || record.result !== 'passed') {
    fail(`${file}: tester, privacy, and result facts must be explicitly passed`);
  }
  if (!/^\d{4}-\d{2}-\d{2}T/.test(record.testedAt ?? '') || Number.isNaN(Date.parse(record.testedAt))) fail(`${file}: testedAt is invalid`);
  if (typeof record.source?.version !== 'string' || !record.source.version.trim()) fail(`${file}: source version is required`);
  if (!/^[0-9a-f]{40}$/.test(record.source?.commit ?? '')) fail(`${file}: source commit must be a full SHA`);
  if (!/^[0-9a-f]{64}$/.test(record.source?.installerSha256 ?? '')) fail(`${file}: installer SHA-256 is invalid`);
  if (!packageTypes.has(record.source?.packageType)) fail(`${file}: packageType is invalid`);
  if (requireComplete && record.source.packageType !== 'exe') {
    fail(`${file}: Public Beta completion requires the tested Windows EXE installer`);
  }
  sourceKeys.add(`${record.source.commit}:${record.source.installerSha256}`);
  if (expectedCommit && record.source.commit !== expectedCommit) fail(`${file}: source commit is not the requested candidate`);
  if (expectedInstallerSha256 && record.source.installerSha256 !== expectedInstallerSha256) {
    fail(`${file}: installer SHA-256 is not the requested candidate`);
  }
  if (record.environment?.architecture !== requiredArchitecture) fail(`${file}: Windows ${requiredArchitecture} is required`);
  if (typeof record.environment?.windowsVersion !== 'string' || !record.environment.windowsVersion.trim()
      || typeof record.environment?.windowsBuild !== 'string' || !record.environment.windowsBuild.trim()
      || !Array.isArray(record.environment?.preinstalledDeveloperTools)) {
    fail(`${file}: complete environment facts are required`);
  }
  if (developerToolsSchema?.items?.type === 'string'
      && record.environment.preinstalledDeveloperTools.some((value) => typeof value !== 'string')) {
    fail(`${file}: preinstalledDeveloperTools entries must be strings`);
  }
  if (developerToolsSchema?.uniqueItems === true
      && new Set(record.environment.preinstalledDeveloperTools).size !== record.environment.preinstalledDeveloperTools.length) {
    fail(`${file}: preinstalledDeveloperTools entries must be unique`);
  }
  for (const task of taskNames) if (record.tasks?.[task] !== 'passed') fail(`${file}: ${task} is not passed`);
  if (!Array.isArray(record.issues)) fail(`${file}: issues must be an array`);
  for (const issue of record.issues) {
    assertKeys(issue, issueKeys, file, 'issue');
    if (!priorities.has(issue.severity) || !issueAreas.has(issue.area)
        || typeof issue.summary !== 'string' || !issue.summary.trim()) {
      fail(`${file}: issue records require valid severity, area, and summary`);
    }
  }
  if (record.issues.some((issue) => issue.severity === 'P0' || issue.severity === 'P1')) {
    fail(`${file}: P0/P1 issue remains open`);
  }
}

if (sourceKeys.size > 1) fail('all records must target the same source commit and installer SHA-256');
const complete = records.length >= 5;
if (requireComplete && !complete) fail(`Public Beta requires 5 records; found ${records.length}`);
console.log(`External tester evidence: ${records.length}/5 valid record(s), complete=${complete}.`);
