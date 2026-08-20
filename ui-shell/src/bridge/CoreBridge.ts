import {
  UUID,
  Revision,
  Command,
  CommandResult,
  Query,
  QueryResult,
  CoreEvent,
  WorkbenchProjection,
  ModElementSummary,
  ModElementEditorProjection,
  TaskSummary,
  TaskLogEntry,
  Diagnostic,
  PermissionProfile,
  ScenarioExpectedUi,
  RecoveryPoint,
  HistoryComparison,
  OperationApproval,
  VersionTracksProjection,
  PublishBatch
} from '../types/contract';

/**
 * UI-shell client-side projection state. Both the U1 mock bridge and the
 * U2 JCEF bridge maintain this store by applying command results and events;
 * components only ever read it through the WorkbenchContext.
 */
export interface BridgeState {
  currentScenarioId: string;
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
  expectedUi: ScenarioExpectedUi | null;
  workbench: WorkbenchProjection | null;
  elements: ModElementSummary[];
  elementEditors: Record<UUID, ModElementEditorProjection>;
  tasks: Record<UUID, TaskSummary>;
  taskLogs: Record<UUID, TaskLogEntry[]>;
  diagnostics: Diagnostic[];
  recoveryPoints: RecoveryPoint[];
  currentRecoveryPointId: string | null;
  historyComparison: HistoryComparison | null;
  operationApprovals: OperationApproval[];
  versionTracks: VersionTracksProjection | null;
  publishBatches: PublishBatch[];
  recoveryState: {
    reasonCode: string;
    lastCommittedRevision: Revision;
    uncommittedRequestIds: UUID[];
  } | null;
  schemaIncompatible: boolean;
}

export interface HandshakeClient {
  id: string;
  version: string;
}

/** Aligned with ui-core/schemas/v1.0/handshake.schema.json */
export interface HandshakeRequest {
  messageType: 'handshake';
  requestId: UUID;
  supportedSchemaVersions: string[];
  client: HandshakeClient;
}

/** Aligned with ui-core/schemas/v1.0/handshake-result.schema.json */
export interface HandshakeResult {
  messageType: 'handshake_result';
  requestId: UUID;
  status: 'compatible' | 'incompatible';
  selectedSchemaVersion: string | null;
  coreSchemaVersions: string[];
  diagnostics: Diagnostic[];
}

/**
 * Versioned UI-Core bridge contract (PRD §8.2).
 *
 * The product shell depends only on this interface. U1 binds the in-memory
 * MockCoreBridge; U2 binds the JCEF bridge implementation without touching
 * components or the WorkbenchContext.
 */
export interface CoreBridge {
  getState(): BridgeState;
  onEvent(listener: (event: CoreEvent) => void): () => void;
  onStateChange(listener: (state: BridgeState) => void): () => void;
  sendCommand<T = unknown>(command: Command<T>): Promise<CommandResult>;
  sendQuery<T = unknown>(query: Query): Promise<QueryResult<T>>;
  /** Startup schema negotiation; must run before any command or query. */
  negotiateHandshake(request: HandshakeRequest): Promise<HandshakeResult>;

  /* ---- U1 mock affordances, not part of the U2 production contract ---- */
  /** Switch the mock fixture scenario (testing tray only). */
  loadScenario?(scenarioId: string): void;
  /** Simulate a permission-profile change (AI control page only). */
  elevatePermission?(profile: PermissionProfile): void;
  /** Simulate bridge recovery reconciliation. */
  reconcileRecovery?(): void;
}

/** Schema versions this UI build supports; sent during every handshake. */
export const UI_SUPPORTED_SCHEMA_VERSIONS = ['1.0'];

export const UI_CLIENT_IDENTITY: HandshakeClient = {
  id: 'product_shell',
  version: '0.1.0'
};
