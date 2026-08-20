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

export type ModElementType = 'block' | 'item' | 'recipe' | 'procedure';

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
  kind: 'validate' | 'generate' | 'build' | 'export' | 'run_client' | 'import';
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
  availableTypes: ModElementType[];
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
    | 'number'
    | 'toggle'
    | 'select'
    | 'resource_reference'
    | 'procedure_reference';
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

export type CommandOperation =
  | 'create_mod_element'
  | 'update_mod_element'
  | 'delete_mod_element'
  | 'validate_workspace'
  | 'generate_workspace'
  | 'build_workspace'
  | 'export_workspace'
  | 'run_client'
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
  | 'list_mod_elements'
  | 'get_mod_element_editor'
  | 'preview_mod_element_change'
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
export type TrackId = 'latest_stable' | 'previous_stable' | 'minecraft_1_21_1' | 'minecraft_1_20_1';
export type LoaderType = 'fabric' | 'neoforge';

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
  | ModElementDeletedEvent
  | DiagnosticsChangedEvent
  | TaskEvent
  | TaskLogAppendedEvent
  | ConnectivityChangedEvent
  | CapabilitiesChangedEvent
  | BridgeRecoveryRequiredEvent
  | RecoveryPointCreatedEvent
  | WorkspaceRestoredEvent
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
