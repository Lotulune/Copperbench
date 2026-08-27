import fs from 'node:fs';
import path from 'node:path';

const root = process.cwd();
const manifestPath = path.join(root, 'sdk', 'evals', 'manifest.json');
const manifest = JSON.parse(fs.readFileSync(manifestPath, 'utf8'));
const typescriptSdk = fs.readFileSync(path.join(root, 'sdk', 'typescript', 'copperbench.ts'), 'utf8');
const pythonSdk = fs.readFileSync(path.join(root, 'sdk', 'python', 'copperbench.py'), 'utf8');
if (manifest.schemaVersion !== '1.0' || !Array.isArray(manifest.cases)) {
  throw new Error('AI eval manifest must use schemaVersion 1.0 and contain cases');
}
if (manifest.cases.length < manifest.minimumCases || manifest.cases.length < 10) {
  throw new Error(`AI eval suite has ${manifest.cases.length} cases; at least 10 are required`);
}
const ids = new Set();
const covered = new Set();
for (const item of manifest.cases) {
  if (!item.id || ids.has(item.id)) throw new Error(`Duplicate or missing eval id: ${item.id}`);
  if (!item.operation || !item.expected || !Array.isArray(item.covers) || item.covers.length === 0) {
    throw new Error(`Eval ${item.id} is missing operation, expected result, or coverage`);
  }
  ids.add(item.id);
  item.covers.forEach((value) => covered.add(value));
}
const requiredCoverage = [
  'create elements', 'Procedure modification', 'rename references', 'build repair',
  'revision conflicts', 'unauthorized access rejection', 'datagen cancellation',
  'datagen preview and publish', 'recovery point restore', 'task event reconnect'
];
const missing = requiredCoverage.filter((value) => !covered.has(value));
if (missing.length) throw new Error(`AI eval suite is missing coverage: ${missing.join(', ')}`);
const requiredMethods = [
  'getWorkspace', 'listModElements', 'createModElement', 'updateProcedure',
  'createRegistryEntry', 'listWorkspaceRegistries', 'renameRegistryEntry',
  'planWorkspaceChanges', 'previewWorkspacePlan', 'applyWorkspacePlan',
  'buildWorkspace', 'runDatagen', 'previewDatagenOutput', 'publishDatagenOutput', 'getTask', 'cancelTask',
  'createRecoveryPoint', 'restoreRecoveryPoint'
];
const missingTypeScriptMethods = requiredMethods.filter((method) => !typescriptSdk.includes(`${method}(`));
if (missingTypeScriptMethods.length) throw new Error(`TypeScript SDK is missing methods: ${missingTypeScriptMethods.join(', ')}`);
const requiredPythonMethods = [
  'get_workspace', 'list_mod_elements', 'create_mod_element', 'update_procedure',
  'create_registry_entry', 'list_workspace_registries', 'rename_registry_entry',
  'plan_workspace_changes', 'preview_workspace_plan', 'apply_workspace_plan',
  'build_workspace', 'run_datagen', 'preview_datagen_output', 'publish_datagen_output', 'get_task', 'cancel_task',
  'create_recovery_point', 'restore_recovery_point'
];
const missingPythonMethods = requiredPythonMethods.filter((method) => !pythonSdk.includes(`def ${method}(`));
if (missingPythonMethods.length) throw new Error(`Python SDK is missing methods: ${missingPythonMethods.join(', ')}`);
if (!typescriptSdk.includes('maxTransportRetries') || !pythonSdk.includes('max_transport_retries')) {
  throw new Error('SDKs must implement bounded transport retry configuration');
}
for (const liveFile of ['scripts/run-ai-live-evals.py', 'scripts/verify-ai-live-evals.ps1']) {
  if (!fs.existsSync(path.join(root, liveFile))) throw new Error(`AI live eval runner is missing: ${liveFile}`);
}
console.log(`AI eval manifest passed: ${manifest.cases.length} cases, ${covered.size} coverage targets.`);
