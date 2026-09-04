/**
 * UI-Core v1.0 TypeScript Contract Definitions
 * Strictly aligned with ui-core/schemas/v1.0 JSON Schema specification
 */

export type UUID = string;
export type Revision = number;
export type Timestamp = string;

export interface LocalizedText {
  key: string;
  fallback: string;
  args?: Record<string, string | number | boolean | null>;
}

export type ActionHintKind =
  | 'retry'
  | 'refresh'
  | 'open_field'
  | 'open_logs'
  | 'request_permission'
  | 'dismiss';

export interface ActionHint {
  id: string;
  label: LocalizedText;
  kind: ActionHintKind;
  target?: string | null;
}

export interface Diagnostic {
  code: string;
  severity: 'info' | 'warning' | 'error';
  message: LocalizedText;
  path: string | null;
  elementId?: UUID | null;
  recoverable: boolean;
  actions: ActionHint[];
}

export interface DiagnosticCounts {
  error: number;
  warning: number;
  info: number;
}

export interface GeneratorTarget {
  id: string;
  loader: 'fabric' | 'neoforge' | 'resource_pack';
  minecraftVersion: string;
  displayName: string;
  state: 'ready' | 'partial' | 'missing' | 'outdated' | 'incompatible';
}

export interface WorkspaceLock {
  state: 'write_available' | 'read_only' | 'locked_elsewhere';
  holder: string | null;
}

export interface WorkspaceSummary {
  id: UUID;
  name: string;
  kind: 'mod' | 'resource_pack';
  revision: Revision;
  dirty: boolean;
  generator: GeneratorTarget;
  lock: WorkspaceLock;
  compatibility: {
    mode: 'native' | 'upstream' | 'partial';
    unknownDataPreserved: boolean;
  };
}

export type PermissionProfile = 'read_only' | 'workspace' | 'full_access';

export interface PermissionProjection {
  profile: PermissionProfile;
  canRequestElevation: boolean;
  protectedOperationsAlwaysConfirm: boolean;
}

export interface ConnectionProjection {
  core: 'connected' | 'reconnecting' | 'disconnected';
  network: 'online' | 'offline' | 'limited';
  bridge: 'ready' | 'degraded' | 'recovery_required';
}

export interface CapabilityDecision {
  id: string;
  availability: 'available' | 'partial' | 'unavailable';
  reasonCode: string | null;
  message: LocalizedText | null;
  affectedPaths: string[];
}

export type ModElementType =
  | 'block' | 'item' | 'recipe' | 'procedure' | 'function' | 'loottable' | 'achievement'
  | 'armor' | 'armortrim' | 'tool' | 'itemextension' | 'attribute' | 'bannerpattern'
  | 'command' | 'damagetype' | 'enchantment' | 'gamerule' | 'keybind' | 'painting' | 'particle'
  | 'potion' | 'potioneffect' | 'tab' | 'villagerprofession' | 'villagertrade'
  | 'biome' | 'dimension' | 'feature' | 'fluid' | 'plant' | 'structure' | 'livingentity'
  | 'specialentity' | 'projectile' | 'gui' | 'overlay' | 'code';

export const ALL_MOD_ELEMENT_TYPES: ModElementType[] = [
  'block', 'item', 'recipe', 'procedure', 'function', 'loottable', 'achievement', 'armor', 'armortrim', 'tool',
  'itemextension', 'attribute', 'bannerpattern', 'command', 'damagetype', 'enchantment', 'gamerule', 'keybind',
  'painting', 'particle', 'potion', 'potioneffect', 'tab', 'villagerprofession', 'villagertrade', 'biome', 'dimension',
  'feature', 'fluid', 'plant', 'structure', 'livingentity', 'specialentity', 'projectile', 'gui', 'overlay', 'code'
];

export interface ModElementSummary {
  id: UUID;
  type: ModElementType | string;
  name: string;
  displayName: string;
  state: 'valid' | 'invalid' | 'draft' | 'unsupported';
  ownership: 'generated' | 'manual' | 'mixed';
  updatedAt: Timestamp;
  firstParty?: boolean;
  diagnostics: DiagnosticCounts;
}

export interface TaskSummary {
  id: UUID;
  kind: 'validate' | 'generate' | 'build' | 'export' | 'run_client' | 'run_server' | 'run_datagen' | 'run_gametest' | 'import';
  state: 'queued' | 'running' | 'succeeded' | 'failed' | 'cancelled';
  cancellable: boolean;
  progress: number | null;
  stage: LocalizedText;
  startedAt: Timestamp;
  completedAt?: Timestamp | null;
  diagnostics: DiagnosticCounts;
}

export interface ElementCounts {
  total: number;
  valid: number;
  invalid: number;
  draft: number;
  unsupported: number;
}

export interface WorkbenchProjection {
  workspace: WorkspaceSummary;
  permission: PermissionProjection;
  connection: ConnectionProjection;
  elementCounts: ElementCounts;
  activeTasks: TaskSummary[];
  capabilities: CapabilityDecision[];
  recentElements: ModElementSummary[];
}

export interface ModElementListProjection {
  items: ModElementSummary[];
  page: number;
  pageSize: number;
  total: number;
  nextCursor?: string | null;
  availableTypes: ModElementType[];
}

export interface ProcedureNode {
  id: UUID;
  type: string;
  kind: 'statement' | 'value';
  x: number;
  y: number;
  fields: Record<string, string | number | boolean>;
  inputs: Record<string, UUID>;
  next: UUID | null;
  unknown: boolean;
  rawPayload?: string;
}

export interface ProcedureDependency {
  id: UUID;
  kind: 'variable' | 'procedure' | 'context' | string;
  name: string;
  dataType: string;
  target: string;
}

export interface ProcedureIr {
  schemaVersion: '1.0';
  trigger: string;
  nodes: ProcedureNode[];
  dependencies: ProcedureDependency[];
  unknownRoot?: Record<string, unknown>;
}

export interface ProcedureNodeCatalogItem {
  type: string;
  category: string;
  label: LocalizedText;
  output: string;
  availability: 'available' | 'unavailable';
  reasonCode?: string | null;
}

export interface ProcedureEditorProjection {
  element: ModElementSummary;
  baseRevision: Revision;
  readOnly: boolean;
  ir: ProcedureIr;
  nodeCatalog: ProcedureNodeCatalogItem[];
  sourcePreview: string;
  sourceOwnership: 'generated' | 'manual' | 'mixed';
  references: WorkspaceReferenceProjection;
  diagnostics?: Diagnostic[];
}

export type ProcedureEdit = Record<string, unknown> & { operation: string };

export interface WorkspaceReferenceProjection {
  revision: Revision;
  nodes: Array<Record<string, unknown>>;
  edges: Array<Record<string, unknown>>;
  diagnostics: Diagnostic[];
  stats: { indexedElements: number; edgeCount: number; incremental: boolean };
}

export interface RegistryEntry {
  id: UUID;
  kind: 'variable' | 'tag' | 'language_key';
  name?: string;
  key?: string;
  dataType?: string;
  scope?: string;
  namespace?: string;
  category?: string;
  value?: unknown;
  members?: string[];
  translations?: Record<string, string>;
  support?: { state: string; reasonCode: string };
}

export interface WorkspaceRegistriesProjection {
  registries?: {
    variables: RegistryEntry[];
    tags: RegistryEntry[];
    languageKeys: RegistryEntry[];
  };
  variables?: RegistryEntry[];
  tags?: RegistryEntry[];
  languageKeys?: RegistryEntry[];
  registry?: 'variables' | 'tags' | 'languageKeys';
  items?: Array<Partial<RegistryEntry>>;
  total?: number;
  pageSize?: number;
  nextCursor?: string | null;
  languageStats: {
    keyCount: number;
    languageCount: number;
    missingTranslationCount: number;
    duplicateKeyCount: number;
  };
  stableIds: boolean;
  referenceAwareRename: boolean;
}

export interface RegistryRenamePreview {
  entryId: UUID;
  registry: 'variables' | 'tags' | 'languageKeys';
  oldName: string;
  newName: string;
  references: WorkspaceReferenceProjection;
  impactedElementCount: number;
  canApply: boolean;
}

export interface FieldOption {
  value: string | number | boolean;
  label: LocalizedText;
  disabled: boolean;
  reason?: LocalizedText | null;
}

export interface EditorField {
  path: string;
  label: LocalizedText;
  help?: LocalizedText | null;
  control:
    | 'text'
    | 'textarea'
    | 'json'
    | 'number'
    | 'toggle'
    | 'select'
    | 'resource_reference'
    | 'procedure_reference'
    | 'element_reference'
    | 'element_reference_list';
  required: boolean;
  readOnly: boolean;
  value: unknown;
  options: FieldOption[];
  constraints?: {
    min?: number;
    max?: number;
    step?: number;
    minLength?: number;
    maxLength?: number;
    pattern?: string;
  };
  condition?: {
    operator: 'any_truthy';
    paths: string[];
  };
  diagnostics: Diagnostic[];
}

export interface EditorSection {
  id: string;
  title: LocalizedText;
  fields: EditorField[];
}

export interface ModElementEditorProjection {
  element: ModElementSummary;
  sections: EditorSection[];
  capabilities: CapabilityDecision[];
}

export interface ModElementChangePreview {
  elementId: UUID;
  baseRevision: Revision;
  canApply: boolean;
  changedPaths: string[];
  candidateValues: Record<string, unknown>;
  diagnostics: Diagnostic[];
  semanticSummary?: {
    changedFieldCount: number;
    changedFields: Array<{
      path: string;
      field: string;
      sectionId: string;
    }>;
    sections: string[];
  };
  generationImpact?: {
    scope: 'element' | string;
    requiresRegeneration: boolean;
    generatorId: string;
    loader: string;
    minecraftVersion: string;
    affectedDomains: string[];
  };
}

export interface TaskLogEntry {
  sequence: number;
  timestamp: Timestamp;
  level: 'info' | 'warning' | 'error';
  text: string;
}

export interface TaskProjection {
  task: TaskSummary;
  logs: TaskLogEntry[];
  diagnostics: Diagnostic[];
}

export type RecoveryPointActor = 'ui' | 'mcp' | 'headless' | 'legacy_ui' | 'system';

export interface RecoveryPoint {
  id: string;
  label: string;
  actor: RecoveryPointActor;
  taskId: string;
  createdAt: Timestamp;
}

export interface HistoryProjection {
  currentRevision: Revision;
  recoveryPoints: RecoveryPoint[];
  total?: number;
  pageSize?: number;
  nextCursor?: string | null;
}

export interface WorkspaceChange {
  type: 'add' | 'modify' | 'delete' | 'rename' | 'copy';
  path: string;
}

export interface HistoryComparison {
  fromRecoveryPointId: string;
  toRecoveryPointId: string;
  baseRevision: Revision;
  changes: WorkspaceChange[];
}

export type ProtectedOperation =
  | 'delete_workspace'
  | 'overwrite_workspace'
  | 'export_credentials'
  | 'external_publish'
  | 'enable_java_plugin'
  | 'update_java_plugin'
  | 'relax_mcp_binding';

export interface OperationApproval {
  id: UUID;
  operation: ProtectedOperation;
  title: LocalizedText;
  requestedBy: RecoveryPointActor;
  requestedAt: Timestamp;
  risk: 'high' | 'critical';
  affectedPaths: string[];
  canApprove: boolean;
  policyCode: string;
}

export interface OperationApprovalListProjection {
  items: OperationApproval[];
}

/* =========================================================================
 * Command Payloads
 * ========================================================================= */

export interface CreateWorkspacePayload {
  clientMutationId: UUID;
  generatorId: string;
  modName: string;
  modId: string;
  packageName?: string;
  workspaceFolderPath: string;
  version?: string;
  userApproved: boolean;
}

export interface CreateModElementPayload {
  clientMutationId: UUID;
  elementType: ModElementType;
  name: string;
  initialValues: Record<string, unknown>;
}

export interface FieldChange {
  path: string;
  value: unknown;
}

export interface UpdateModElementPayload {
  clientMutationId: UUID;
  elementId: UUID;
  changes: FieldChange[];
}

export interface DeleteModElementPayload {
  clientMutationId: UUID;
  elementId: UUID;
}

export interface WorkspaceTaskPayload {
  clientMutationId: UUID;
  scope: 'workspace' | 'selection';
  elementIds?: UUID[];
}

export interface CancelTaskPayload {
  clientMutationId: UUID;
  taskId: UUID;
}

export interface DatagenFilePreview {
  path: string;
  status: 'add' | 'modify' | 'unchanged';
  size: number;
  sha256: string;
}

export interface DatagenPreview {
  taskId: UUID;
  sourceRevision: Revision;
  currentRevision: Revision;
  manifestHash: string;
  files: DatagenFilePreview[];
  changeCount: number;
  stale: boolean;
  published: boolean;
  canPublish: boolean;
  changedPaths?: string[];
}

export interface PublishDatagenPayload {
  clientMutationId: UUID;
  taskId: UUID;
  manifestHash: string;
}

export interface CreateRecoveryPointPayload {
  clientMutationId: UUID;
  label: string;
}

export interface RestoreRecoveryPointPayload {
  clientMutationId: UUID;
  recoveryPointId: string;
  userApproved: true;
}

export interface ResolveOperationApprovalPayload {
  clientMutationId: UUID;
  approvalId: UUID;
  decision: 'approve' | 'deny';
}

export interface ExecuteLoaderMigrationPayload {
  clientMutationId: UUID;
  targetGeneratorId: string;
  outputName: string;
  userApproved: boolean;
}

export interface ImportUpstreamWorkspacePayload {
  clientMutationId: UUID;
  sourceWorkspacePath: string;
  outputName: string;
  userApproved: boolean;
}

export interface CreatePublishBatchPayload {
  clientMutationId: UUID;
  name: string;
  sourceDirectory: string;
  output: string;
}

export interface PrepareResourcePackClientPayload {
  clientMutationId: UUID;
  sourceDirectory: string;
  zipFileName: string;
}

export type WorkspacePlanOperation =
  | 'create_mod_element'
  | 'update_mod_element'
  | 'delete_mod_element'
  | 'update_procedure'
  | 'create_registry_entry'
  | 'update_registry_entry'
  | 'delete_registry_entry'
  | 'rename_registry_entry';

export interface WorkspacePlanStep {
  operation: WorkspacePlanOperation;
  payload: Record<string, unknown>;
  plannedId?: UUID;
}

export interface WorkspacePlanPermission {
  currentProfile: PermissionProfile;
  requiredProfile: 'workspace';
  allowed: boolean;
}

export interface WorkspacePlan {
  schemaVersion: '1.0';
  workspaceId: UUID;
  baseRevision: Revision;
  idempotencyKey: string;
  operations: WorkspacePlanStep[];
  operationCount: number;
  targetDigest: string;
  semanticDiff: Record<string, unknown>[];
  changedPaths: string[];
  permission: WorkspacePlanPermission;
  planId: string;
  planToken: string;
  currentRevision?: Revision;
  alreadyApplied?: boolean;
  wouldApply?: boolean;
}

export interface WorkspacePlanRequestPayload {
  expectedRevision: Revision;
  idempotencyKey: string;
  operations: Array<Omit<WorkspacePlanStep, 'plannedId'>>;
}

export interface WorkspacePlanEnvelopePayload {
  plan: WorkspacePlan;
}

export interface ApplyWorkspacePlanPayload extends WorkspacePlanEnvelopePayload {
  clientMutationId: UUID;
}

export type CommandOperation =
  | 'create_workspace'
  | 'create_mod_element'
  | 'update_mod_element'
  | 'delete_mod_element'
  | 'update_procedure'
  | 'create_registry_entry'
  | 'update_registry_entry'
  | 'delete_registry_entry'
  | 'rename_registry_entry'
  | 'apply_workspace_plan'
  | 'validate_workspace'
  | 'generate_workspace'
  | 'build_workspace'
  | 'export_workspace'
  | 'run_client'
  | 'run_server'
  | 'run_datagen'
	| 'publish_datagen_output'
  | 'run_gametest'
  | 'cancel_task'
  | 'create_recovery_point'
  | 'restore_recovery_point'
  | 'resolve_operation_approval'
  | 'execute_loader_migration'
  | 'import_upstream_workspace'
  | 'create_publish_batch'
  | 'prepare_resource_pack_client';

export interface Command<T = unknown> {
  messageType: 'command';
  schemaVersion: '1.0';
  requestId: UUID;
  workspaceId: UUID;
  expectedRevision: Revision;
  operation: CommandOperation;
  payload: T;
}

/* =========================================================================
 * Command Result
 * ========================================================================= */

export type CommandResultStatus =
  | 'committed'
  | 'accepted'
  | 'completed'
  | 'rejected'
  | 'failed'
  | 'cancelled';

export interface RevisionConflict {
  expectedRevision: Revision;
  actualRevision: Revision;
  changedPaths: string[];
}

export interface PermissionDenial {
  currentProfile: PermissionProfile;
  requiredProfile: PermissionProfile;
  approvalRequired: boolean;
  protectedOperation: boolean;
}

export interface CommandResultData {
  element?: ModElementSummary;
  elementId?: UUID;
  entry?: RegistryEntry;
  entryId?: UUID;
  oldName?: string;
  changedElementIds?: UUID[];
  references?: WorkspaceReferenceProjection;
  recoveryPoint?: RecoveryPoint;
  recoveryPointId?: string;
  changedPaths?: string[];
  approvalId?: UUID;
  decision?: 'approve' | 'deny';
  complete?: boolean;
  targetDirectory?: string | null;
  sourceHash?: string;
  sourceUnchanged?: boolean;
  kind?: string;
  sourceGeneratorId?: string;
  targetGeneratorId?: string;
  items?: MigrationItem[];
  blockedCount?: number;
  lostCount?: number;
  manualCount?: number;
  batch?: PublishBatch;
  zipRelativePath?: string;
  packFormat?: number;
  optionsRelativePath?: string;
  readyForClient?: boolean;
  clientLaunched?: boolean;
  workspaceFile?: string;
  generatorId?: string;
  modId?: string;
  taskId?: UUID;
  sourceRevision?: Revision;
  currentRevision?: Revision;
  manifestHash?: string;
  files?: DatagenFilePreview[];
  changeCount?: number;
  stale?: boolean;
  published?: boolean;
  canPublish?: boolean;
  planId?: string;
  idempotencyKey?: string;
  operationCount?: number;
  targetDigest?: string;
  semanticDiff?: Record<string, unknown>[];
  idempotentReplay?: boolean;
}

export interface CommandResult {
  messageType: 'command_result';
  schemaVersion: '1.0';
  requestId: UUID;
  workspaceId: UUID;
  operation: CommandOperation;
  status: CommandResultStatus;
  newRevision: Revision;
  recoveryPointId?: UUID | null;
  task?: TaskSummary | null;
  data?: CommandResultData | null;
  diagnostics: Diagnostic[];
  conflict?: RevisionConflict | null;
  denial?: PermissionDenial | null;
}

/* =========================================================================
 * Query & Query Result
 * ========================================================================= */

export type QueryOperation =
  | 'get_workbench'
  | 'list_new_workspace_generators'
  | 'list_assets'
  | 'list_mod_elements'
  | 'get_mod_element_editor'
  | 'preview_mod_element_change'
  | 'get_procedure_editor'
  | 'preview_procedure_change'
  | 'get_workspace_references'
  | 'list_workspace_registries'
  | 'preview_registry_rename'
	| 'plan_workspace_changes'
	| 'preview_workspace_plan'
	| 'preview_datagen_output'
  | 'get_task'
  | 'get_history'
  | 'get_diff'
  | 'list_operation_approvals'
  | 'get_version_tracks'
  | 'get_release_notes'
  | 'preview_loader_migration'
  | 'preview_upstream_import'
  | 'list_publish_batches'
  | 'list_installed_plugins'
  | 'get_element_coverage'
  | 'get_upstream_tools';

export interface ElementCoverage {
  schemaVersion: '1.0';
  firstPartySlice: string[];
  unsupportedInNewUi: string[];
  bedrockAddonNotApplicable: string[];
  appliesToGenerators: string;
  notes: string;
}

export interface UpstreamTool {
  id: string;
  upstream: string;
  surface: 'new_ui' | 'legacy_window' | 'unsupported' | 'not_applicable';
  notes: string;
}

export interface UpstreamToolCatalogProjection {
  schemaVersion: '1.0';
  notes: string;
  tools: UpstreamTool[];
}

export interface InstalledPlugin {
  pluginId: string;
  path: string;
  firstParty: boolean;
  level: 'A' | 'B' | 'C' | 'X';
  route: string;
  containsJavaCode: boolean;
  versionSupported: boolean;
  displayName?: string;
  version?: string;
  sha256?: string;
  limitations?: string[];
}

export interface InstalledPluginInventory {
  plugins: InstalledPlugin[];
  scannedRoots: string[];
  loadsJava: boolean;
}

export interface PreviewLoaderMigrationPayload {
  targetGeneratorId: string;
}

export interface PreviewUpstreamImportPayload {
  sourceWorkspacePath: string;
}

/* =========================================================================
 * U3 Domain Projections (Tracks, Migration, Batches)
 * ========================================================================= */

export type TrackStatus = 'supported' | 'preview' | 'unavailable' | 'coincides';
export type TrackId =
  | 'latest_stable'
  | 'previous_stable'
  | 'minecraft_1_21_1'
  | 'minecraft_1_20_1'
  | 'resource_pack';
export type LoaderType = 'fabric' | 'neoforge' | 'resource_pack';

export interface TrackLoader {
  loader: LoaderType;
  generatorId: string;
  minecraftVersion: string;
  status: TrackStatus;
  pluginId: string | null;
  reasonCode: string;
  notes: string;
}

export interface VersionTrack {
  id: TrackId;
  minecraftVersion: string;
  displayName: string;
  dynamic: boolean;
  loaders: TrackLoader[];
}

export interface CurrentWorkspaceTrackOverlay {
  generator: GeneratorTarget;
  status: TrackStatus;
  reasonCode: string;
  generatable: boolean;
}

export interface VersionTracksProjection {
  schemaVersion: '1.0';
  latestMinecraftVersion: string;
  previousMinecraftVersion: string;
  tracks: VersionTrack[];
  currentWorkspace?: CurrentWorkspaceTrackOverlay;
}

export interface NewWorkspaceGenerator {
  generatorId: string;
  loader: LoaderType;
  minecraftVersion: string;
  trackId: TrackId;
  displayName: string;
  dynamic: boolean;
  available: boolean;
  workspaceGeneratorName: string;
}

export interface NewWorkspaceGeneratorCatalog {
  schemaVersion: '1.0';
  generators: NewWorkspaceGenerator[];
  suggestedWorkspaceFoldersRoot: string;
}

export type AssetProjectionCategory =
  | 'MODEL'
  | 'TEXTURE'
  | 'ANIMATION'
  | 'LANGUAGE'
  | 'SOUND'
  | 'RESOURCE_PACK'
  | 'BLOCKSTATE'
  | 'OTHER';

export interface AssetProjectionAsset {
  id: string;
  relativePath: string;
  category: AssetProjectionCategory;
  size: number;
  sha256: string;
  mediaType: string;
  updatedAt?: string;
}

export interface AssetProjectionReference {
  sourceAssetId: string;
  sourcePath: string;
  targetPath: string;
  targetAssetId: string;
  kind: 'RESOURCE_ID' | 'JSON_STRING';
}

export interface AssetProjectionDiagnostic {
  code: 'INVALID_ASSET_DOCUMENT' | 'REFERENCE_PATH_ESCAPE' | 'MISSING_ASSET_REFERENCE';
  severity: 'INFO' | 'WARNING' | 'ERROR';
  sourcePath: string;
  targetPath: string | null;
  message: string;
}

export interface AssetProjection {
  schemaVersion: '1.0';
  assets: AssetProjectionAsset[];
  references: AssetProjectionReference[];
  diagnostics: AssetProjectionDiagnostic[];
}

export type MigrationDisposition = 'supported' | 'substitute' | 'lost' | 'blocked' | 'manual';

export interface MigrationItem {
  path: string;
  name: string;
  type: string;
  disposition: MigrationDisposition;
  reasonCode: string;
  nextStep: string;
}

export interface MigrationReport {
  kind: 'loader' | 'upstream_import' | string;
  sourceGeneratorId: string;
  targetGeneratorId: string;
  sourceHash: string;
  targetDirectory: string | null;
  sourceUnchanged: boolean;
  complete: boolean;
  items: MigrationItem[];
  blockedCount: number;
  lostCount: number;
  manualCount: number;
}

export type LoaderMigrationPreview = MigrationReport;
export type UpstreamImportPreview = MigrationReport;

export interface PublishBatch {
  id: UUID;
  name: string;
  sourceDirectory: string;
  outputPath: string;
  sha256: string;
  assetCount: number;
  createdAt: Timestamp;
  assets: string[];
}

export interface PublishBatchListProjection {
  items: PublishBatch[];
  total?: number;
  pageSize?: number;
  nextCursor?: string | null;
}

export interface ClientLoadPreparation {
  zipRelativePath: string;
  sha256: string;
  packFormat: number;
  optionsRelativePath: string;
  readyForClient: boolean;
  clientLaunched: boolean;
  complete: boolean;
}

export type CopyResultPayload = MigrationReport;

export interface PublishBatchResultPayload {
  complete: boolean;
  batch: PublishBatch;
}

export interface Query<T = unknown> {
  messageType: 'query';
  schemaVersion: '1.0';
  requestId: UUID;
  workspaceId: UUID;
  operation: QueryOperation;
  payload?: T;
}

export interface QueryResult<T = unknown> {
  messageType: 'query_result';
  schemaVersion: '1.0';
  requestId: UUID;
  workspaceId: UUID;
  operation: QueryOperation;
  status: 'succeeded' | 'rejected' | 'failed';
  revision: Revision;
  data: T;
  diagnostics: Diagnostic[];
}

/* =========================================================================
 * Events
 * ========================================================================= */

export type EventType =
  | 'workspace_revision_advanced'
  | 'mod_element_created'
  | 'mod_element_updated'
  | 'mod_element_deleted'
  | 'procedure_updated'
  | 'registry_updated'
	| 'datagen_published'
  | 'diagnostics_changed'
  | 'task_started'
  | 'task_progressed'
  | 'task_log_appended'
  | 'task_completed'
  | 'connectivity_changed'
  | 'capabilities_changed'
  | 'bridge_recovery_required'
  | 'recovery_point_created'
  | 'workspace_restored'
  | 'workspace_created'
  | 'workspace_plan_applied'
  | 'loader_migration_executed'
  | 'upstream_workspace_imported'
  | 'publish_batch_created'
  | 'resource_pack_client_prepared';

export interface BaseEvent<E extends EventType, P> {
  messageType: 'event';
  schemaVersion: '1.0';
  eventId: UUID;
  workspaceId: UUID;
  revision: Revision;
  sequence: number;
  occurredAt: Timestamp;
  event: E;
  causedByRequestId: UUID | null;
  payload: P;
}

export type RevisionAdvancedEvent = BaseEvent<
  'workspace_revision_advanced',
  {
    changedPaths: string[];
    actor: 'ui' | 'mcp' | 'headless' | 'legacy_ui' | 'system';
  }
>;

export type WorkspacePlanAppliedEvent = BaseEvent<
  'workspace_plan_applied',
  {
    planId: string;
    idempotencyKey: string;
    operationCount: number;
    targetDigest: string;
    idempotentReplay: false;
    semanticDiff: Record<string, unknown>[];
    changedPaths: string[];
  }
>;

export type ModElementCreatedEvent = BaseEvent<
  'mod_element_created',
  {
    element: ModElementSummary;
  }
>;

export type ModElementUpdatedEvent = BaseEvent<
  'mod_element_updated',
  {
    element: ModElementSummary;
  }
>;

export type ProcedureUpdatedEvent = BaseEvent<
  'procedure_updated',
  {
    element: ModElementSummary;
  }
>;

export type RegistryUpdatedEvent = BaseEvent<
  'registry_updated',
  Record<string, unknown>
>;

export type DatagenPublishedEvent = BaseEvent<
  'datagen_published',
  DatagenPreview
>;

export type ModElementDeletedEvent = BaseEvent<
  'mod_element_deleted',
  {
    elementId: UUID;
    name: string;
  }
>;

export type DiagnosticsChangedEvent = BaseEvent<
  'diagnostics_changed',
  {
    counts: DiagnosticCounts;
    diagnostics: Diagnostic[];
  }
>;

export type TaskEvent = BaseEvent<
  'task_started' | 'task_progressed' | 'task_completed',
  {
    task: TaskSummary;
  }
>;

export type TaskLogAppendedEvent = BaseEvent<
  'task_log_appended',
  {
    taskId: UUID;
    entries: TaskLogEntry[];
  }
>;

export type ConnectivityChangedEvent = BaseEvent<
  'connectivity_changed',
  ConnectionProjection
>;

export type CapabilitiesChangedEvent = BaseEvent<
  'capabilities_changed',
  {
    capabilities: CapabilityDecision[];
  }
>;

export type BridgeRecoveryRequiredEvent = BaseEvent<
  'bridge_recovery_required',
  {
    reasonCode: string;
    lastCommittedRevision: Revision;
    uncommittedRequestIds: UUID[];
  }
>;

export type RecoveryPointCreatedEvent = BaseEvent<
  'recovery_point_created',
  {
    recoveryPoint: RecoveryPoint;
  }
>;

export type WorkspaceRestoredEvent = BaseEvent<
  'workspace_restored',
  {
    recoveryPointId: string;
    actor: RecoveryPointActor;
    changedPaths: string[];
  }
>;

export type WorkspaceCreatedEvent = BaseEvent<
  'workspace_created',
  {
    workspaceFile: string;
    generatorId: string;
    modId: string;
  }
>;

export type LoaderMigrationExecutedEvent = BaseEvent<
  'loader_migration_executed',
  CopyResultPayload
>;

export type UpstreamWorkspaceImportedEvent = BaseEvent<
  'upstream_workspace_imported',
  CopyResultPayload
>;

export type PublishBatchCreatedEvent = BaseEvent<
  'publish_batch_created',
  PublishBatchResultPayload
>;

export type ResourcePackClientPreparedEvent = BaseEvent<
  'resource_pack_client_prepared',
  ClientLoadPreparation
>;

export type CoreEvent =
  | RevisionAdvancedEvent
  | ModElementCreatedEvent
  | ModElementUpdatedEvent
  | ProcedureUpdatedEvent
  | RegistryUpdatedEvent
	| DatagenPublishedEvent
  | ModElementDeletedEvent
  | DiagnosticsChangedEvent
  | TaskEvent
  | TaskLogAppendedEvent
  | ConnectivityChangedEvent
  | CapabilitiesChangedEvent
  | BridgeRecoveryRequiredEvent
  | RecoveryPointCreatedEvent
  | WorkspaceRestoredEvent
  | WorkspaceCreatedEvent
  | WorkspacePlanAppliedEvent
  | LoaderMigrationExecutedEvent
  | UpstreamWorkspaceImportedEvent
  | PublishBatchCreatedEvent
  | ResourcePackClientPreparedEvent;

/* =========================================================================
 * Scenario Schema
 * ========================================================================= */

export interface ScenarioTimelineEntry {
  afterMs: number;
  message: CoreEvent | CommandResult | QueryResult;
}

export interface ScenarioExpectedUi {
  announcement: string | null;
  primaryAction: string | null;
  focusTarget: string | null;
}

export interface ScenarioDefinition {
  schemaVersion: '1.0';
  scenarioId: string;
  extendsScenarioId?: string | null;
  title: LocalizedText;
  description: string;
  viewportState:
    | 'ready'
    | 'empty'
    | 'loading'
    | 'error'
    | 'offline'
    | 'conflict'
    | 'permission_denied'
    | 'degraded'
    | 'recovery';
  initialMessages: (QueryResult | CoreEvent | CommandResult)[];
  timeline?: ScenarioTimelineEntry[];
  expectedUi: ScenarioExpectedUi;
}
