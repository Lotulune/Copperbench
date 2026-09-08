import fs from 'node:fs';
import path from 'node:path';

const root = process.cwd();
const manifestPath = path.join(root, 'sdk', 'evals', 'manifest.json');
const manifest = JSON.parse(fs.readFileSync(manifestPath, 'utf8'));
const typescriptSdk = fs.readFileSync(path.join(root, 'sdk', 'typescript', 'copperbench.ts'), 'utf8');
const pythonSdk = fs.readFileSync(path.join(root, 'sdk', 'python', 'copperbench.py'), 'utf8');
const typescriptQuickstart = fs.readFileSync(path.join(root, 'examples', 'ai', 'quickstart.ts'), 'utf8');
const pythonQuickstart = fs.readFileSync(path.join(root, 'examples', 'ai', 'quickstart.py'), 'utf8');
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
  'getWorkspaceHealth', 'createRegistryEntry', 'listWorkspaceRegistries', 'renameRegistryEntry', 'previewRegistryRename',
  'planWorkspaceChanges', 'planProcedureRefactor', 'previewWorkspacePlan', 'applyWorkspacePlan',
  'listAssets', 'previewAssetMove', 'moveAsset',
  'buildWorkspace', 'runDatagen', 'previewDatagenOutput', 'publishDatagenOutput', 'getTask', 'cancelTask',
  'createRecoveryPoint', 'restoreRecoveryPoint'
];
const missingTypeScriptMethods = requiredMethods.filter((method) => !typescriptSdk.includes(`${method}(`));
if (missingTypeScriptMethods.length) throw new Error(`TypeScript SDK is missing methods: ${missingTypeScriptMethods.join(', ')}`);
const requiredPythonMethods = [
  'get_workspace', 'get_workspace_health', 'list_mod_elements', 'create_mod_element', 'update_procedure',
  'create_registry_entry', 'list_workspace_registries', 'rename_registry_entry', 'preview_registry_rename',
  'plan_workspace_changes', 'plan_procedure_refactor', 'preview_workspace_plan', 'apply_workspace_plan',
  'list_assets', 'preview_asset_move', 'move_asset',
  'build_workspace', 'run_datagen', 'preview_datagen_output', 'publish_datagen_output', 'get_task', 'cancel_task',
  'create_recovery_point', 'restore_recovery_point'
];
const missingPythonMethods = requiredPythonMethods.filter((method) => !pythonSdk.includes(`def ${method}(`));
if (missingPythonMethods.length) throw new Error(`Python SDK is missing methods: ${missingPythonMethods.join(', ')}`);
if (!typescriptSdk.includes('maxTransportRetries') || !pythonSdk.includes('max_transport_retries')) {
  throw new Error('SDKs must implement bounded transport retry configuration');
}
if (!typescriptSdk.includes('readWorkspaceConnection(') || !typescriptSdk.includes('fromWorkspace(')) {
  throw new Error('TypeScript SDK must discover the desktop MCP endpoint from workspace connection metadata');
}
if (!pythonSdk.includes('def read_workspace_connection(') || !pythonSdk.includes('def from_workspace(')) {
  throw new Error('Python SDK must discover the desktop MCP endpoint from workspace connection metadata');
}
for (const [name, quickstart] of [['TypeScript', typescriptQuickstart], ['Python', pythonQuickstart]]) {
  if (!quickstart.includes('COPPERBENCH_WORKSPACE')) {
    throw new Error(`${name} quickstart must accept a workspace path for desktop MCP discovery`);
  }
  if (quickstart.includes('127.0.0.1:8787')) {
    throw new Error(`${name} quickstart must not assume the legacy fixed MCP port 8787`);
  }
}
for (const liveFile of ['scripts/run-ai-live-evals.py', 'scripts/verify-ai-live-evals.ps1']) {
  if (!fs.existsSync(path.join(root, liveFile))) throw new Error(`AI live eval runner is missing: ${liveFile}`);
}
console.log(`AI eval manifest passed: ${manifest.cases.length} cases, ${covered.size} coverage targets.`);
