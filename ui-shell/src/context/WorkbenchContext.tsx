import React, { createContext, useContext, useEffect, useState, useMemo, useCallback } from 'react';
import {
  UUID,
  ModElementType,
  FieldChange,
  PermissionProfile,
  CommandResult,
  ModElementSummary,
  ModElementEditorProjection,
  ModElementChangePreview,
  Diagnostic,
  ActionHint,
  VersionTracksProjection,
  LoaderMigrationPreview,
  UpstreamImportPreview,
  PublishBatchListProjection,
  InstalledPluginInventory,
  UpstreamToolCatalogProjection,
  NewWorkspaceGeneratorCatalog,
  AssetProjection,
  AssetImportPreview, AssetImportBatchPreview, AssetMovePreview,
  ProcedureEditorProjection,
  ProcedureChangePreview,
  ProcedureEdit,
  WorkspaceRegistriesProjection,
  RegistryEntry,
  RegistryRenamePreview,
  WorkspaceReferenceProjection,
  WorkspacePlan,
  WorkspacePlanStep,
  ProcedureRefactorRequest,
  DatagenPreview,
  TaskProjection,
  TaskSourcePreview,
  HistoryProjection,
  HistoryComparison,
  RecoveryRestorePreview,
  WorkspaceHealthProjection
} from '../types/contract';
import type { TaskAuthorization, TaskAuthorizationRequest, CommandOperation } from '../types/contract';
import {
  coreBridge,
  BridgeState,
  UI_SUPPORTED_SCHEMA_VERSIONS,
  UI_CLIENT_IDENTITY
} from '../bridge';
import { windowBridge } from '../bridge/windowBridge';
import { diagnosticsBridge } from '../bridge/diagnosticsBridge';
import { t } from '../i18n';

export type NavView = 'hub' | 'elements' | 'data' | 'assets' | 'history' | 'ai' | 'plugins' | 'tracks' | 'new-workspace' | 'help';

export interface ProcedureFocusRequest {
  elementId: UUID;
  nodeId: string;
  port: string | null;
  requestId: UUID;
}

interface WorkbenchContextType {
  state: BridgeState;
  theme: 'dark' | 'light';
  toggleTheme: () => void;
  activeView: NavView;
  setActiveView: (view: NavView) => void;
  selectedElementId: UUID | null;
  selectedElement: ModElementSummary | null;
  setSelectedElementId: (id: UUID | null) => void;
  assetFocusId: string | null;
  setAssetFocusId: (id: string | null) => void;
  procedureFocusRequest: ProcedureFocusRequest | null;
  clearProcedureFocusRequest: () => void;
  isTaskDrawerOpen: boolean;
  setIsTaskDrawerOpen: (open: boolean) => void;
  activeTaskId: UUID | null;
  setActiveTaskId: (id: UUID | null) => void;
  isMaximized: boolean;
  toggleMaximize: () => void;
  systemFrameFallback: boolean;
  toggleSystemFrameFallback: () => void;
  isCreateModalOpen: boolean;
  setIsCreateModalOpen: (open: boolean) => void;
  isConflictModalOpen: boolean;
  setIsConflictModalOpen: (open: boolean) => void;
  announcement: string | null;

  // Bridge Actions
  loadScenario: (scenarioId: string) => void;
  getModElementEditor: (elementId: UUID) => Promise<ModElementEditorProjection | null>;
  previewModElementChange: (elementId: UUID, changes: FieldChange[]) => Promise<ModElementChangePreview | null>;
  getProcedureEditor: (elementId: UUID) => Promise<ProcedureEditorProjection | null>;
  previewProcedureChange: (elementId: UUID, edits: ProcedureEdit[]) => Promise<ProcedureChangePreview | null>;
  updateProcedure: (elementId: UUID, edits: ProcedureEdit[]) => Promise<CommandResult>;
  listWorkspaceRegistries: () => Promise<WorkspaceRegistriesProjection | null>;
  getWorkspaceReferences: (target?: string) => Promise<WorkspaceReferenceProjection | null>;
  createRegistryEntry: (registry: 'variables' | 'tags' | 'languageKeys', entry: Partial<RegistryEntry>) => Promise<CommandResult>;
  updateRegistryEntry: (entryId: UUID, changes: FieldChange[]) => Promise<CommandResult>;
  previewRegistryRename: (entryId: UUID, newName: string) => Promise<RegistryRenamePreview | null>;
  planProcedureRefactor: (request: ProcedureRefactorRequest) => Promise<WorkspacePlan | null>;
  planWorkspaceChanges: (operations: Array<Omit<WorkspacePlanStep, 'plannedId'>>, requireRecoveryPoint?: boolean,
    expectedRevision?: number) => Promise<WorkspacePlan | null>;
  applyWorkspacePlan: (plan: WorkspacePlan) => Promise<CommandResult>;
  renameRegistryEntry: (entryId: UUID, newName: string) => Promise<CommandResult>;
  deleteRegistryEntry: (entryId: UUID) => Promise<CommandResult>;
  createModElement: (type: ModElementType, name: string) => Promise<CommandResult>;
  updateModElement: (elementId: UUID, changes: FieldChange[]) => Promise<CommandResult>;
  deleteModElement: (elementId: UUID) => Promise<CommandResult>;
  generateWorkspace: () => Promise<CommandResult>;
  buildWorkspace: () => Promise<CommandResult>;
  runClient: () => Promise<CommandResult>;
  runServer: (userApproved: boolean) => Promise<CommandResult>;
  runDatagen: () => Promise<CommandResult>;
  previewDatagenOutput: (taskId: UUID) => Promise<DatagenPreview | null>;
  previewTaskSource: (taskId: UUID, sourcePath: string) => Promise<TaskSourcePreview | null>;
  publishDatagenOutput: (taskId: UUID, manifestHash: string) => Promise<CommandResult>;
  runGameTest: () => Promise<CommandResult>;
  prepareGameTests: () => Promise<CommandResult>;
  listTaskAuthorizations: () => Promise<TaskAuthorization[]>;
  getWorkspaceRoot: () => Promise<string>;
  createTaskAuthorization: (request: TaskAuthorizationRequest) => Promise<CommandResult>;
  revokeTaskAuthorization: (authorizationId: UUID) => Promise<CommandResult>;
  cancelTask: (taskId: UUID) => Promise<CommandResult>;
  createRecoveryPoint: (label: string) => Promise<CommandResult>;
  refreshHistory: () => Promise<HistoryProjection | null>;
  compareRecoveryPoints: (fromRecoveryPointId: string, toRecoveryPointId: string) => Promise<HistoryComparison | null>;
  previewRecoveryRestore: (recoveryPointId: string) => Promise<RecoveryRestorePreview | null>;
  restoreRecoveryPoint: (recoveryPointId: string) => Promise<CommandResult>;
  resolveOperationApproval: (approvalId: UUID, decision: 'approve' | 'deny') => Promise<CommandResult>;
  getWorkspaceHealth: () => Promise<WorkspaceHealthProjection | null>;
  getVersionTracks: () => Promise<VersionTracksProjection | null>;
  previewLoaderMigration: (targetGeneratorId: string) => Promise<LoaderMigrationPreview | null>;
  executeLoaderMigration: (targetGeneratorId: string, outputName: string, userApproved: boolean) => Promise<CommandResult>;
  previewUpstreamImport: (sourceWorkspacePath: string) => Promise<UpstreamImportPreview | null>;
  importUpstreamWorkspace: (sourceWorkspacePath: string, outputName: string, userApproved: boolean) => Promise<CommandResult>;
  listPublishBatches: () => Promise<PublishBatchListProjection | null>;
  listInstalledPlugins: () => Promise<InstalledPluginInventory | null>;
  getUpstreamTools: () => Promise<UpstreamToolCatalogProjection | null>;
  createPublishBatch: (name: string, sourceDirectory: string, output: string) => Promise<CommandResult>;
  prepareResourcePackClient: (sourceDirectory: string, zipFileName: string) => Promise<CommandResult>;
  listAssets: () => Promise<AssetProjection | null>;
  previewAssetImport: (sourceGrantId: string, targetRelativePath: string) => Promise<AssetImportPreview | null>;
  importAsset: (planToken: string, confirmReplace: boolean) => Promise<CommandResult>;
  previewAssetImportBatch: (items: { sourceGrantId: string; targetRelativePath: string }[]) => Promise<AssetImportBatchPreview | null>;
  importAssetBatch: (planToken: string, confirmReplace: boolean) => Promise<CommandResult>;
  previewAssetMove: (sourceAssetId: string, targetRelativePath: string) => Promise<AssetMovePreview | null>;
  moveAsset: (planToken: string) => Promise<CommandResult>;
  listNewWorkspaceGenerators: () => Promise<NewWorkspaceGeneratorCatalog | null>;
  createWorkspace: (form: {
    generatorId: string;
    modName: string;
    modId: string;
    packageName?: string;
    workspaceFolderPath: string;
    version?: string;
    userApproved: boolean;
  }) => Promise<CommandResult>;
  elevatePermission: (profile: PermissionProfile) => void;
  reconcileRecovery: () => void;
  runDiagnosticAction: (action: ActionHint, diagnostic: Diagnostic) => void;
}

const WorkbenchContext = createContext<WorkbenchContextType | null>(null);

function generateUUID(): UUID {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

/**
 * Fixture focusTarget values use unquoted attribute selectors such as
 * `[data-field-path=/fields/hardness]`, which are invalid CSS. Re-quote the
 * value before querying so contract data stays copy-pasteable.
 */
function focusByContractSelector(selector: string): void {
  if (selector.startsWith('/')) {
    selector = `[data-field-path=${selector}]`;
  }
  const match = /^\[([A-Za-z0-9_-]+)=(.+)\]$/.exec(selector.trim());
  const query = match ? `[${match[1]}="${match[2]}"]` : selector;
  const el = document.querySelector(query);
  if (el instanceof HTMLElement) {
    if (!el.hasAttribute('tabindex')) {
      el.setAttribute('tabindex', '-1');
    }
    el.focus();
  }
}

export const WorkbenchProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [state, setState] = useState<BridgeState>(coreBridge.getState());
  const [theme, setTheme] = useState<'dark' | 'light'>('dark');
  const [activeView, setActiveView] = useState<NavView>('hub');
  const [selectedElementId, setSelectedElementId] = useState<UUID | null>(null);
  const [assetFocusId, setAssetFocusId] = useState<string | null>(null);
  const [procedureFocusRequest, setProcedureFocusRequest] = useState<ProcedureFocusRequest | null>(null);
  const [isTaskDrawerOpen, setIsTaskDrawerOpen] = useState(false);
  const [activeTaskId, setActiveTaskId] = useState<UUID | null>(null);
  const [isMaximized, setIsMaximized] = useState(false);
  const [systemFrameFallback, setSystemFrameFallback] = useState(windowBridge.systemFrame);
  const [isCreateModalOpen, setIsCreateModalOpen] = useState(false);
  const [isConflictModalOpen, setIsConflictModalOpen] = useState(false);
  const [announcement, setAnnouncement] = useState<string | null>(null);

  useEffect(() => {
    const unsub = coreBridge.onStateChange((newState) => {
      setState({ ...newState });
    });
    return () => unsub();
  }, []);

  // Startup schema negotiation (PRD §8.2): no command or query is trusted
  // before the handshake succeeds; incompatibility renders the structured
  // startup error instead of falling back to untyped JSON.
  useEffect(() => {
    const run = async () => {
      try {
        await coreBridge.negotiateHandshake({
          messageType: 'handshake',
          requestId: generateUUID(),
          supportedSchemaVersions: [...UI_SUPPORTED_SCHEMA_VERSIONS],
          client: { ...UI_CLIENT_IDENTITY }
        });
        setState({ ...coreBridge.getState() });
      } catch (error) {
        const message = error instanceof Error ? error.message : String(error);
        setState((current) => ({
          ...current,
          viewportState: 'error',
          diagnostics: current.diagnostics.some((diagnostic) => diagnostic.code === 'UI_CORE_STARTUP_FAILED')
            ? current.diagnostics
            : [
                ...current.diagnostics,
                {
                  code: 'UI_CORE_STARTUP_FAILED',
                  severity: 'error',
                  message: {
                    key: 'diagnostic.ui_core_startup_failed',
                    fallback: message
                  },
                  path: null,
                  recoverable: false,
                  actions: []
                }
              ]
        }));
      }
    };
    void run();
  }, []);

  // The conflict arbitration dialog follows the projected viewport state; it
  // can still be dismissed manually and will not force itself open again.
  useEffect(() => {
    setIsConflictModalOpen(state.viewportState === 'conflict');
  }, [state.viewportState]);

  useEffect(() => {
    document.documentElement.setAttribute('data-theme', theme);
  }, [theme]);

  const toggleTheme = useCallback(() => {
    setTheme((prev) => (prev === 'dark' ? 'light' : 'dark'));
  }, []);

  const toggleMaximize = useCallback(() => {
    setIsMaximized((prev) => !prev);
    windowBridge.toggleMaximize();
  }, []);

  const toggleSystemFrameFallback = useCallback(() => {
    if (!windowBridge.canToggleFrame) return;
    setSystemFrameFallback((prev) => !prev);
  }, []);

  const loadScenario = useCallback((scenarioId: string) => {
    coreBridge.loadScenario?.(scenarioId);
    setSelectedElementId(null);
    setProcedureFocusRequest(null);
    setIsTaskDrawerOpen(false);
    setActiveTaskId(null);

    // A scenario switch simulates a core restart: re-run the handshake so
    // the schema-incompatible fixture drives the structured startup error.
    void coreBridge.negotiateHandshake({
      messageType: 'handshake',
      requestId: generateUUID(),
      supportedSchemaVersions: [...UI_SUPPORTED_SCHEMA_VERSIONS],
      client: { ...UI_CLIENT_IDENTITY }
    });

    // Apply scenario.expectedUi: announce via live region and move focus to
    // the contract-declared target once the projection has rendered.
    const projected = coreBridge.getState();
    const expected = projected.expectedUi;
    if (expected?.announcement) {
      const match = projected.diagnostics.find((d) => d.code === expected.announcement);
      setAnnouncement(match ? t(match.message) : expected.announcement);
    } else {
      setAnnouncement(null);
    }
    if (expected?.focusTarget) {
      const selector = expected.focusTarget;
      window.setTimeout(() => focusByContractSelector(selector), 60);
    }
  }, []);

  const selectedElement = useMemo(() => {
    if (!selectedElementId) return null;
    return state.elements.find((e) => e.id === selectedElementId) || null;
  }, [selectedElementId, state.elements]);

  const clearProcedureFocusRequest = useCallback(() => setProcedureFocusRequest(null), []);

  const getModElementEditor = useCallback(
    async (elementId: UUID): Promise<ModElementEditorProjection | null> => {
      const res = await coreBridge.sendQuery({
        messageType: 'query',
        schemaVersion: '1.0',
        requestId: generateUUID(),
        workspaceId: state.workbench?.workspace.id ?? '',
        operation: 'get_mod_element_editor',
        payload: { elementId }
      });
      return (res.data as ModElementEditorProjection | null) ?? null;
    },
    [state.workbench]
  );

  const previewAssetImportBatch = useCallback(
    async (items: { sourceGrantId: string; targetRelativePath: string }[]): Promise<AssetImportBatchPreview | null> => {
      const workspaceId = state.workbench?.workspace.id || generateUUID();
      const res = await coreBridge.sendQuery<AssetImportBatchPreview>({
        messageType: 'query',
        schemaVersion: '1.0',
        requestId: generateUUID(),
        workspaceId,
        operation: 'preview_asset_import_batch',
        payload: { items }
      });
      if (res.status !== 'succeeded' || !res.data) {
        const diagnostic = res.diagnostics[0];
        throw new Error(diagnostic ? t(diagnostic.message) : '资产批量导入预览失败。');
      }
      return res.data as AssetImportBatchPreview;
    },
    [state.workbench]
  );

  const importAssetBatch = useCallback(
    async (planToken: string, confirmReplace: boolean): Promise<CommandResult> => {
      const workspaceId = state.workbench?.workspace.id || generateUUID();
      const revision = state.workbench?.workspace.revision ?? 0;
      return coreBridge.sendCommand({
        messageType: 'command',
        schemaVersion: '1.0',
        requestId: generateUUID(),
        workspaceId,
        expectedRevision: revision,
        operation: 'import_asset_batch',
        payload: { clientMutationId: generateUUID(), planToken, confirmReplace }
      });
    },
    [state.workbench]
  );

  const previewAssetMove = useCallback(
    async (sourceAssetId: string, targetRelativePath: string): Promise<AssetMovePreview | null> => {
      const workspaceId = state.workbench?.workspace.id || generateUUID();
      const res = await coreBridge.sendQuery<AssetMovePreview>({
        messageType: 'query',
        schemaVersion: '1.0',
        requestId: generateUUID(),
        workspaceId,
        operation: 'preview_asset_move',
        payload: { sourceAssetId, targetRelativePath }
      });
      if (res.status !== 'succeeded' || !res.data) {
        const diagnostic = res.diagnostics[0];
        throw new Error(diagnostic ? t(diagnostic.message) : '资产移动预览失败。');
      }
      return res.data as AssetMovePreview;
    },
    [state.workbench]
  );

  const moveAsset = useCallback(async (planToken: string): Promise<CommandResult> => {
    const workspaceId = state.workbench?.workspace.id || generateUUID();
    const revision = state.workbench?.workspace.revision ?? 0;
    return coreBridge.sendCommand({
      messageType: 'command',
      schemaVersion: '1.0',
      requestId: generateUUID(),
      workspaceId,
      expectedRevision: revision,
      operation: 'move_asset',
      payload: { clientMutationId: generateUUID(), planToken }
    });
  }, [state.workbench]);

  const getWorkspaceHealth = useCallback(async (): Promise<WorkspaceHealthProjection | null> => {
    if (!state.workbench) return null;
    const res = await coreBridge.sendQuery<WorkspaceHealthProjection>({
      messageType: 'query',
      schemaVersion: '1.0',
      requestId: generateUUID(),
      workspaceId: state.workbench.workspace.id,
      operation: 'get_workspace_health',
      payload: {}
    });
    return res.status === 'succeeded' ? res.data : null;
  }, [state.workbench]);

  const previewModElementChange = useCallback(
    async (elementId: UUID, changes: FieldChange[]): Promise<ModElementChangePreview | null> => {
      if (changes.length === 0) return null;
      const res = await coreBridge.sendQuery<ModElementChangePreview>({
        messageType: 'query',
        schemaVersion: '1.0',
        requestId: generateUUID(),
        workspaceId: state.workbench?.workspace.id ?? '',
        operation: 'preview_mod_element_change',
        payload: { elementId, changes }
      });
      return res.data ?? null;
    },
    [state.workbench]
  );

  const getProcedureEditor = useCallback(async (elementId: UUID): Promise<ProcedureEditorProjection | null> => {
    const res = await coreBridge.sendQuery<ProcedureEditorProjection>({
      messageType: 'query',
      schemaVersion: '1.0',
      requestId: generateUUID(),
      workspaceId: state.workbench?.workspace.id ?? '',
      operation: 'get_procedure_editor',
      payload: { elementId }
    });
    return res.data ? { ...res.data, diagnostics: res.diagnostics } : null;
  }, [state.workbench]);

  const planProcedureRefactor = useCallback(async (request: ProcedureRefactorRequest): Promise<WorkspacePlan | null> => {
    const res = await coreBridge.sendQuery<WorkspacePlan>({
      messageType: 'query', schemaVersion: '1.0', requestId: generateUUID(),
      workspaceId: state.workbench?.workspace.id ?? '', operation: 'plan_procedure_refactor',
      payload: {
        ...request,
        expectedRevision: state.workbench?.workspace.revision ?? 0,
        idempotencyKey: generateUUID()
      }
    });
    return res.data ?? null;
  }, [state.workbench]);

  const previewProcedureChange = useCallback(async (
    elementId: UUID, edits: ProcedureEdit[]
  ): Promise<ProcedureChangePreview | null> => {
    if (edits.length === 0) return null;
    const res = await coreBridge.sendQuery<ProcedureChangePreview>({
      messageType: 'query',
      schemaVersion: '1.0',
      requestId: generateUUID(),
      workspaceId: state.workbench?.workspace.id ?? '',
      operation: 'preview_procedure_change',
      payload: { elementId, edits }
    });
    return res.data ?? null;
  }, [state.workbench]);

  const planWorkspaceChanges = useCallback(async (
    operations: Array<Omit<WorkspacePlanStep, 'plannedId'>>,
    requireRecoveryPoint = false,
    expectedRevision?: number
  ): Promise<WorkspacePlan | null> => {
    const res = await coreBridge.sendQuery<WorkspacePlan>({
      messageType: 'query', schemaVersion: '1.0', requestId: generateUUID(),
      workspaceId: state.workbench?.workspace.id ?? '', operation: 'plan_workspace_changes',
      payload: {
        expectedRevision: expectedRevision ?? state.workbench?.workspace.revision ?? 0,
        idempotencyKey: generateUUID(),
        requireRecoveryPoint,
        operations
      }
    });
    return res.data ?? null;
  }, [state.workbench]);

  const applyWorkspacePlan = useCallback(async (plan: WorkspacePlan): Promise<CommandResult> => coreBridge.sendCommand({
    messageType: 'command', schemaVersion: '1.0', requestId: generateUUID(),
    workspaceId: state.workbench?.workspace.id || plan.workspaceId,
    expectedRevision: plan.baseRevision,
    operation: 'apply_workspace_plan',
    payload: { clientMutationId: generateUUID(), plan }
  }), [state.workbench]);

  const updateProcedure = useCallback(async (elementId: UUID, edits: ProcedureEdit[]): Promise<CommandResult> => {
    const workspaceId = state.workbench?.workspace.id || generateUUID();
    return coreBridge.sendCommand({
      messageType: 'command',
      schemaVersion: '1.0',
      requestId: generateUUID(),
      workspaceId,
      expectedRevision: state.workbench?.workspace.revision ?? 0,
      operation: 'update_procedure',
      payload: { clientMutationId: generateUUID(), elementId, edits }
    });
  }, [state.workbench]);

  const listWorkspaceRegistries = useCallback(async (): Promise<WorkspaceRegistriesProjection | null> => {
    const res = await coreBridge.sendQuery<WorkspaceRegistriesProjection>({
      messageType: 'query', schemaVersion: '1.0', requestId: generateUUID(),
      workspaceId: state.workbench?.workspace.id ?? '', operation: 'list_workspace_registries', payload: {}
    });
    return res.data ?? null;
  }, [state.workbench]);

  const getWorkspaceReferences = useCallback(async (target?: string): Promise<WorkspaceReferenceProjection | null> => {
    const res = await coreBridge.sendQuery<WorkspaceReferenceProjection>({
      messageType: 'query', schemaVersion: '1.0', requestId: generateUUID(),
      workspaceId: state.workbench?.workspace.id ?? '', operation: 'get_workspace_references',
      payload: target ? { target } : {}
    });
    return res.data ?? null;
  }, [state.workbench]);

  const createRegistryEntry = useCallback(async (
    registry: 'variables' | 'tags' | 'languageKeys', entry: Partial<RegistryEntry>
  ): Promise<CommandResult> => coreBridge.sendCommand({
    messageType: 'command', schemaVersion: '1.0', requestId: generateUUID(),
    workspaceId: state.workbench?.workspace.id || generateUUID(),
    expectedRevision: state.workbench?.workspace.revision ?? 0,
    operation: 'create_registry_entry',
    payload: { clientMutationId: generateUUID(), registry, entry }
  }), [state.workbench]);

  const updateRegistryEntry = useCallback(async (entryId: UUID, changes: FieldChange[]): Promise<CommandResult> =>
    coreBridge.sendCommand({
      messageType: 'command', schemaVersion: '1.0', requestId: generateUUID(),
      workspaceId: state.workbench?.workspace.id || generateUUID(),
      expectedRevision: state.workbench?.workspace.revision ?? 0,
      operation: 'update_registry_entry',
      payload: { clientMutationId: generateUUID(), entryId, changes }
    }), [state.workbench]);

  const previewRegistryRename = useCallback(async (entryId: UUID, newName: string): Promise<RegistryRenamePreview | null> => {
    const res = await coreBridge.sendQuery<RegistryRenamePreview>({
      messageType: 'query', schemaVersion: '1.0', requestId: generateUUID(),
      workspaceId: state.workbench?.workspace.id ?? '', operation: 'preview_registry_rename',
      payload: { entryId, newName }
    });
    return res.data ?? null;
  }, [state.workbench]);

  const renameRegistryEntry = useCallback(async (entryId: UUID, newName: string): Promise<CommandResult> =>
    coreBridge.sendCommand({
      messageType: 'command', schemaVersion: '1.0', requestId: generateUUID(),
      workspaceId: state.workbench?.workspace.id || generateUUID(),
      expectedRevision: state.workbench?.workspace.revision ?? 0,
      operation: 'rename_registry_entry',
      payload: { clientMutationId: generateUUID(), entryId, newName }
    }), [state.workbench]);

  const deleteRegistryEntry = useCallback(async (entryId: UUID): Promise<CommandResult> =>
    coreBridge.sendCommand({
      messageType: 'command', schemaVersion: '1.0', requestId: generateUUID(),
      workspaceId: state.workbench?.workspace.id || generateUUID(),
      expectedRevision: state.workbench?.workspace.revision ?? 0,
      operation: 'delete_registry_entry',
      payload: { clientMutationId: generateUUID(), entryId }
    }), [state.workbench]);

  const createModElement = useCallback(
    async (type: ModElementType, name: string): Promise<CommandResult> => {
      const workspaceId = state.workbench?.workspace.id || generateUUID();
      const revision = state.workbench?.workspace.revision ?? 0;
      const res = await coreBridge.sendCommand({
        messageType: 'command',
        schemaVersion: '1.0',
        requestId: generateUUID(),
        workspaceId,
        expectedRevision: revision,
        operation: 'create_mod_element',
        payload: {
          clientMutationId: generateUUID(),
          elementType: type,
          name,
          initialValues: {}
        }
      });
      if (res.data?.element?.id) {
        setSelectedElementId(res.data.element.id);
        setActiveView('elements');
      }
      return res;
    },
    [state.workbench]
  );

  const updateModElement = useCallback(
    async (elementId: UUID, changes: FieldChange[]): Promise<CommandResult> => {
      const workspaceId = state.workbench?.workspace.id || generateUUID();
      const revision = state.workbench?.workspace.revision ?? 0;
      return coreBridge.sendCommand({
        messageType: 'command',
        schemaVersion: '1.0',
        requestId: generateUUID(),
        workspaceId,
        expectedRevision: revision,
        operation: 'update_mod_element',
        payload: {
          clientMutationId: generateUUID(),
          elementId,
          changes
        }
      });
    },
    [state.workbench]
  );

  const deleteModElement = useCallback(
    async (elementId: UUID): Promise<CommandResult> => {
      const workspaceId = state.workbench?.workspace.id || generateUUID();
      const revision = state.workbench?.workspace.revision ?? 0;
      const res = await coreBridge.sendCommand({
        messageType: 'command',
        schemaVersion: '1.0',
        requestId: generateUUID(),
        workspaceId,
        expectedRevision: revision,
        operation: 'delete_mod_element',
        payload: {
          clientMutationId: generateUUID(),
          elementId
        }
      });
      if (selectedElementId === elementId) {
        setSelectedElementId(null);
      }
      return res;
    },
    [state.workbench, selectedElementId]
  );

  const generateWorkspace = useCallback(async (): Promise<CommandResult> => {
    const workspaceId = state.workbench?.workspace.id || generateUUID();
    const revision = state.workbench?.workspace.revision ?? 0;
    const res = await coreBridge.sendCommand({
      messageType: 'command',
      schemaVersion: '1.0',
      requestId: generateUUID(),
      workspaceId,
      expectedRevision: revision,
      operation: 'generate_workspace',
      payload: {
        clientMutationId: generateUUID(),
        scope: 'workspace'
      }
    });
    if (res.task?.id) {
      setActiveTaskId(res.task.id);
      setIsTaskDrawerOpen(true);
    }
    return res;
  }, [state.workbench]);

  const buildWorkspace = useCallback(async (): Promise<CommandResult> => {
    const workspaceId = state.workbench?.workspace.id || generateUUID();
    const revision = state.workbench?.workspace.revision ?? 0;
    const res = await coreBridge.sendCommand({
      messageType: 'command',
      schemaVersion: '1.0',
      requestId: generateUUID(),
      workspaceId,
      expectedRevision: revision,
      operation: 'build_workspace',
      payload: {
        clientMutationId: generateUUID(),
        scope: 'workspace'
      }
    });
    if (res.task?.id) {
      setActiveTaskId(res.task.id);
      setIsTaskDrawerOpen(true);
    }
    return res;
  }, [state.workbench]);

  const runClient = useCallback(async (): Promise<CommandResult> => {
    const workspaceId = state.workbench?.workspace.id || generateUUID();
    const revision = state.workbench?.workspace.revision ?? 0;
    const res = await coreBridge.sendCommand({
      messageType: 'command',
      schemaVersion: '1.0',
      requestId: generateUUID(),
      workspaceId,
      expectedRevision: revision,
      operation: 'run_client',
      payload: {
        clientMutationId: generateUUID(),
        scope: 'workspace'
      }
    });
    if (res.task?.id) {
      setActiveTaskId(res.task.id);
      setIsTaskDrawerOpen(true);
    }
    return res;
  }, [state.workbench]);

  const runWorkspaceTask = useCallback(async (
    operation: 'run_server' | 'run_datagen' | 'run_gametest' | 'prepare_game_tests',
    userApproved?: boolean
  ): Promise<CommandResult> => {
    const workspaceId = state.workbench?.workspace.id || generateUUID();
    const revision = state.workbench?.workspace.revision ?? 0;
    const res = await coreBridge.sendCommand({
      messageType: 'command',
      schemaVersion: '1.0',
      requestId: generateUUID(),
      workspaceId,
      expectedRevision: revision,
      operation,
      payload: {
        clientMutationId: generateUUID(),
        scope: 'workspace',
        ...(operation === 'run_server' ? { userApproved: userApproved === true } : {})
      }
    });
    if (res.task?.id) {
      setActiveTaskId(res.task.id);
      setIsTaskDrawerOpen(true);
    }
    return res;
  }, [state.workbench]);

  const runServer = useCallback((userApproved: boolean) => runWorkspaceTask('run_server', userApproved),
    [runWorkspaceTask]);
  const runDatagen = useCallback(() => runWorkspaceTask('run_datagen'), [runWorkspaceTask]);
  const runGameTest = useCallback(() => runWorkspaceTask('run_gametest'), [runWorkspaceTask]);
  const prepareGameTests = useCallback(() => runWorkspaceTask('prepare_game_tests'), [runWorkspaceTask]);

  const listTaskAuthorizations = useCallback(async (): Promise<TaskAuthorization[]> => {
    const result = await coreBridge.sendQuery<{ authorizations: TaskAuthorization[] }>({
      messageType: 'query', schemaVersion: '1.0', requestId: generateUUID(),
      workspaceId: state.workbench?.workspace.id || generateUUID(), operation: 'list_task_authorizations', payload: {}
    });
    if (result.status !== 'succeeded' || !result.data) throw new Error(t(result.diagnostics[0]?.message ?? '无法读取任务授权'));
    return result.data.authorizations;
  }, [state.workbench?.workspace.id]);

  const getWorkspaceRoot = useCallback(async (): Promise<string> => {
    const result = await coreBridge.sendQuery<{ execution: { workspaceRoot?: string } }>({
      messageType: 'query', schemaVersion: '1.0', requestId: generateUUID(),
      workspaceId: state.workbench?.workspace.id || generateUUID(), operation: 'get_workspace_environment', payload: {}
    });
    return result.data?.execution?.workspaceRoot ?? '';
  }, [state.workbench?.workspace.id]);

  const taskAuthorizationCommand = useCallback((operation: CommandOperation, payload: object): Promise<CommandResult> =>
    coreBridge.sendCommand({ messageType: 'command', schemaVersion: '1.0', requestId: generateUUID(),
      workspaceId: state.workbench?.workspace.id || generateUUID(), expectedRevision: state.workbench?.workspace.revision ?? 0,
      operation, payload: { clientMutationId: generateUUID(), ...payload }
    }), [state.workbench]);
  const createTaskAuthorization = useCallback((request: TaskAuthorizationRequest) =>
    taskAuthorizationCommand('create_task_authorization', { ...request, userApproved: true }), [taskAuthorizationCommand]);
  const revokeTaskAuthorization = useCallback((authorizationId: UUID) =>
    taskAuthorizationCommand('revoke_task_authorization', { authorizationId }), [taskAuthorizationCommand]);

  const previewDatagenOutput = useCallback(async (taskId: UUID): Promise<DatagenPreview | null> => {
    const res = await coreBridge.sendQuery<DatagenPreview>({
      messageType: 'query',
      schemaVersion: '1.0',
      requestId: generateUUID(),
      workspaceId: state.workbench?.workspace.id || generateUUID(),
      operation: 'preview_datagen_output',
      payload: { taskId }
    });
    return res.status === 'succeeded' ? res.data : null;
  }, [state.workbench]);

  const previewTaskSource = useCallback(async (
    taskId: UUID,
    sourcePath: string
  ): Promise<TaskSourcePreview | null> => {
    const afterLogSequence = state.taskLogs[taskId]?.at(-1)?.sequence ?? 0;
    const res = await coreBridge.sendQuery<TaskProjection>({
      messageType: 'query',
      schemaVersion: '1.0',
      requestId: generateUUID(),
      workspaceId: state.workbench?.workspace.id || generateUUID(),
      operation: 'get_task',
      payload: { taskId, afterLogSequence, sourcePath }
    });
    return res.status === 'succeeded' ? res.data?.source ?? null : null;
  }, [state.taskLogs, state.workbench]);

  const publishDatagenOutput = useCallback(async (
    taskId: UUID,
    manifestHash: string
  ): Promise<CommandResult> => coreBridge.sendCommand({
    messageType: 'command',
    schemaVersion: '1.0',
    requestId: generateUUID(),
    workspaceId: state.workbench?.workspace.id || generateUUID(),
    expectedRevision: state.workbench?.workspace.revision ?? 0,
    operation: 'publish_datagen_output',
    payload: {
      clientMutationId: generateUUID(),
      taskId,
      manifestHash
    }
  }), [state.workbench]);

  const cancelTask = useCallback(
    async (taskId: UUID): Promise<CommandResult> => {
      const workspaceId = state.workbench?.workspace.id || generateUUID();
      const revision = state.workbench?.workspace.revision ?? 0;
      return coreBridge.sendCommand({
        messageType: 'command',
        schemaVersion: '1.0',
        requestId: generateUUID(),
        workspaceId,
        expectedRevision: revision,
        operation: 'cancel_task',
        payload: {
          clientMutationId: generateUUID(),
          taskId
        }
      });
    },
    [state.workbench]
  );

  const createRecoveryPoint = useCallback(
    async (label: string): Promise<CommandResult> => {
      const workspaceId = state.workbench?.workspace.id || generateUUID();
      const revision = state.workbench?.workspace.revision ?? 0;
      return coreBridge.sendCommand({
        messageType: 'command',
        schemaVersion: '1.0',
        requestId: generateUUID(),
        workspaceId,
        expectedRevision: revision,
        operation: 'create_recovery_point',
        payload: { clientMutationId: generateUUID(), label }
      });
    },
    [state.workbench]
  );

  const refreshHistory = useCallback(async (): Promise<HistoryProjection | null> => {
    const res = await coreBridge.sendQuery<HistoryProjection>({
      messageType: 'query',
      schemaVersion: '1.0',
      requestId: generateUUID(),
      workspaceId: state.workbench?.workspace.id || generateUUID(),
      operation: 'get_history',
      payload: {}
    });
    return res.status === 'succeeded' ? res.data : null;
  }, [state.workbench]);

  const compareRecoveryPoints = useCallback(
    async (fromRecoveryPointId: string, toRecoveryPointId: string): Promise<HistoryComparison | null> => {
      const res = await coreBridge.sendQuery<HistoryComparison>({
        messageType: 'query',
        schemaVersion: '1.0',
        requestId: generateUUID(),
        workspaceId: state.workbench?.workspace.id || generateUUID(),
        operation: 'get_diff',
        payload: { fromRecoveryPointId, toRecoveryPointId }
      });
      return res.status === 'succeeded' ? res.data : null;
    },
    [state.workbench]
  );

  const previewRecoveryRestore = useCallback(
    async (recoveryPointId: string): Promise<RecoveryRestorePreview | null> => {
      const res = await coreBridge.sendQuery<RecoveryRestorePreview>({
        messageType: 'query',
        schemaVersion: '1.0',
        requestId: generateUUID(),
        workspaceId: state.workbench?.workspace.id || generateUUID(),
        operation: 'preview_recovery_restore',
        payload: { recoveryPointId }
      });
      return res.status === 'succeeded' ? res.data : null;
    },
    [state.workbench]
  );

  const restoreRecoveryPoint = useCallback(
    async (recoveryPointId: string): Promise<CommandResult> => {
      const workspaceId = state.workbench?.workspace.id || generateUUID();
      const revision = state.workbench?.workspace.revision ?? 0;
      return coreBridge.sendCommand({
        messageType: 'command',
        schemaVersion: '1.0',
        requestId: generateUUID(),
        workspaceId,
        expectedRevision: revision,
        operation: 'restore_recovery_point',
        payload: { clientMutationId: generateUUID(), recoveryPointId, userApproved: true as const }
      });
    },
    [state.workbench]
  );

  const resolveOperationApproval = useCallback(
    async (approvalId: UUID, decision: 'approve' | 'deny'): Promise<CommandResult> => {
      const workspaceId = state.workbench?.workspace.id || generateUUID();
      const revision = state.workbench?.workspace.revision ?? 0;
      return coreBridge.sendCommand({
        messageType: 'command',
        schemaVersion: '1.0',
        requestId: generateUUID(),
        workspaceId,
        expectedRevision: revision,
        operation: 'resolve_operation_approval',
        payload: { clientMutationId: generateUUID(), approvalId, decision }
      });
    },
    [state.workbench]
  );

  const getVersionTracks = useCallback(async (): Promise<VersionTracksProjection | null> => {
    const workspaceId = state.workbench?.workspace.id || generateUUID();
    const res = await coreBridge.sendQuery<VersionTracksProjection>({
      messageType: 'query',
      schemaVersion: '1.0',
      requestId: generateUUID(),
      workspaceId,
      operation: 'get_version_tracks',
      payload: {}
    });
    return (res.data as VersionTracksProjection | null) ?? null;
  }, [state.workbench]);

  const previewLoaderMigration = useCallback(
    async (targetGeneratorId: string): Promise<LoaderMigrationPreview | null> => {
      const workspaceId = state.workbench?.workspace.id || generateUUID();
      const res = await coreBridge.sendQuery<LoaderMigrationPreview>({
        messageType: 'query',
        schemaVersion: '1.0',
        requestId: generateUUID(),
        workspaceId,
        operation: 'preview_loader_migration',
        payload: { targetGeneratorId }
      });
      return (res.data as LoaderMigrationPreview | null) ?? null;
    },
    [state.workbench]
  );

  const executeLoaderMigration = useCallback(
    async (targetGeneratorId: string, outputName: string, userApproved: boolean): Promise<CommandResult> => {
      const workspaceId = state.workbench?.workspace.id || generateUUID();
      const revision = state.workbench?.workspace.revision ?? 0;
      return coreBridge.sendCommand({
        messageType: 'command',
        schemaVersion: '1.0',
        requestId: generateUUID(),
        workspaceId,
        expectedRevision: revision,
        operation: 'execute_loader_migration',
        payload: {
          clientMutationId: generateUUID(),
          targetGeneratorId,
          outputName,
          userApproved
        }
      });
    },
    [state.workbench]
  );

  const previewUpstreamImport = useCallback(
    async (sourceWorkspacePath: string): Promise<UpstreamImportPreview | null> => {
      const workspaceId = state.workbench?.workspace.id || generateUUID();
      const res = await coreBridge.sendQuery<UpstreamImportPreview>({
        messageType: 'query',
        schemaVersion: '1.0',
        requestId: generateUUID(),
        workspaceId,
        operation: 'preview_upstream_import',
        payload: { sourceWorkspacePath }
      });
      return (res.data as UpstreamImportPreview | null) ?? null;
    },
    [state.workbench]
  );

  const importUpstreamWorkspace = useCallback(
    async (sourceWorkspacePath: string, outputName: string, userApproved: boolean): Promise<CommandResult> => {
      const workspaceId = state.workbench?.workspace.id || generateUUID();
      const revision = state.workbench?.workspace.revision ?? 0;
      return coreBridge.sendCommand({
        messageType: 'command',
        schemaVersion: '1.0',
        requestId: generateUUID(),
        workspaceId,
        expectedRevision: revision,
        operation: 'import_upstream_workspace',
        payload: {
          clientMutationId: generateUUID(),
          sourceWorkspacePath,
          outputName,
          userApproved
        }
      });
    },
    [state.workbench]
  );

  const getUpstreamTools = useCallback(async (): Promise<UpstreamToolCatalogProjection | null> => {
    const workspaceId = state.workbench?.workspace.id || generateUUID();
    const res = await coreBridge.sendQuery<UpstreamToolCatalogProjection>({
      messageType: 'query',
      schemaVersion: '1.0',
      requestId: generateUUID(),
      workspaceId,
      operation: 'get_upstream_tools',
      payload: {}
    });
    return (res.data as UpstreamToolCatalogProjection | null) ?? null;
  }, [state.workbench]);

  const listInstalledPlugins = useCallback(async (): Promise<InstalledPluginInventory | null> => {
    const workspaceId = state.workbench?.workspace.id || generateUUID();
    const res = await coreBridge.sendQuery<InstalledPluginInventory>({
      messageType: 'query',
      schemaVersion: '1.0',
      requestId: generateUUID(),
      workspaceId,
      operation: 'list_installed_plugins',
      payload: {}
    });
    return (res.data as InstalledPluginInventory | null) ?? null;
  }, [state.workbench]);

  const listPublishBatches = useCallback(async (): Promise<PublishBatchListProjection | null> => {
    const workspaceId = state.workbench?.workspace.id || generateUUID();
    const res = await coreBridge.sendQuery<PublishBatchListProjection>({
      messageType: 'query',
      schemaVersion: '1.0',
      requestId: generateUUID(),
      workspaceId,
      operation: 'list_publish_batches',
      payload: {}
    });
    return (res.data as PublishBatchListProjection | null) ?? null;
  }, [state.workbench]);

  const listAssets = useCallback(async (): Promise<AssetProjection | null> => {
    const workspaceId = state.workbench?.workspace.id || generateUUID();
    const res = await coreBridge.sendQuery<AssetProjection>({
      messageType: 'query',
      schemaVersion: '1.0',
      requestId: generateUUID(),
      workspaceId,
      operation: 'list_assets',
      payload: {}
    });
    return (res.data as AssetProjection | null) ?? null;
  }, [state.workbench]);

  const previewAssetImport = useCallback(
    async (sourceGrantId: string, targetRelativePath: string): Promise<AssetImportPreview | null> => {
      const workspaceId = state.workbench?.workspace.id || generateUUID();
      const res = await coreBridge.sendQuery<AssetImportPreview>({
        messageType: 'query',
        schemaVersion: '1.0',
        requestId: generateUUID(),
        workspaceId,
        operation: 'preview_asset_import',
        payload: { sourceGrantId, targetRelativePath }
      });
      if (res.status !== 'succeeded' || !res.data) {
        const diagnostic = res.diagnostics[0];
        throw new Error(diagnostic ? t(diagnostic.message) : '资产导入预览失败。');
      }
      return res.data as AssetImportPreview;
    },
    [state.workbench]
  );

  const importAsset = useCallback(
    async (planToken: string, confirmReplace: boolean): Promise<CommandResult> => {
      const workspaceId = state.workbench?.workspace.id || generateUUID();
      const revision = state.workbench?.workspace.revision ?? 0;
      return coreBridge.sendCommand({
        messageType: 'command',
        schemaVersion: '1.0',
        requestId: generateUUID(),
        workspaceId,
        expectedRevision: revision,
        operation: 'import_asset',
        payload: {
          clientMutationId: generateUUID(),
          planToken,
          confirmReplace
        }
      });
    },
    [state.workbench]
  );

  const createPublishBatch = useCallback(
    async (name: string, sourceDirectory: string, output: string): Promise<CommandResult> => {
      const workspaceId = state.workbench?.workspace.id || generateUUID();
      const revision = state.workbench?.workspace.revision ?? 0;
      return coreBridge.sendCommand({
        messageType: 'command',
        schemaVersion: '1.0',
        requestId: generateUUID(),
        workspaceId,
        expectedRevision: revision,
        operation: 'create_publish_batch',
        payload: {
          clientMutationId: generateUUID(),
          name,
          sourceDirectory,
          output
        }
      });
    },
    [state.workbench]
  );

  const prepareResourcePackClient = useCallback(
    async (sourceDirectory: string, zipFileName: string): Promise<CommandResult> => {
      const workspaceId = state.workbench?.workspace.id || generateUUID();
      const revision = state.workbench?.workspace.revision ?? 0;
      return coreBridge.sendCommand({
        messageType: 'command',
        schemaVersion: '1.0',
        requestId: generateUUID(),
        workspaceId,
        expectedRevision: revision,
        operation: 'prepare_resource_pack_client',
        payload: {
          clientMutationId: generateUUID(),
          sourceDirectory,
          zipFileName
        }
      });
    },
    [state.workbench]
  );

  const listNewWorkspaceGenerators = useCallback(
    async (): Promise<NewWorkspaceGeneratorCatalog | null> => {
      const workspaceId = state.workbench?.workspace.id || generateUUID();
      const res = await coreBridge.sendQuery<NewWorkspaceGeneratorCatalog>({
        messageType: 'query',
        schemaVersion: '1.0',
        requestId: generateUUID(),
        workspaceId,
      operation: 'list_new_workspace_generators',
      payload: {}
    });
    if (res.status !== 'succeeded' || !res.data) {
      const diagnostic = res.diagnostics[0];
      throw new Error(diagnostic ? t(diagnostic.message) : '生成器目录无法加载。');
    }
    return res.data as NewWorkspaceGeneratorCatalog;
    },
    [state.workbench]
  );

  const createWorkspace = useCallback(
    async (form: {
      generatorId: string;
      modName: string;
      modId: string;
      packageName?: string;
      workspaceFolderPath: string;
      version?: string;
      userApproved: boolean;
    }): Promise<CommandResult> => {
      const workspaceId = state.workbench?.workspace.id || generateUUID();
      const revision = state.workbench?.workspace.revision ?? 0;
      return coreBridge.sendCommand({
        messageType: 'command',
        schemaVersion: '1.0',
        requestId: generateUUID(),
        workspaceId,
        expectedRevision: revision,
        operation: 'create_workspace',
        payload: {
          clientMutationId: generateUUID(),
          ...form
        }
      });
    },
    [state.workbench]
  );

  const elevatePermission = useCallback((profile: PermissionProfile) => {
    coreBridge.elevatePermission?.(profile);
  }, []);

  const reconcileRecovery = useCallback(() => {
    coreBridge.reconcileRecovery?.();
  }, []);

  const runDiagnosticAction = useCallback(
    (action: ActionHint, diagnostic: Diagnostic) => {
      switch (action.kind) {
        case 'request_permission':
          elevatePermission((action.target as PermissionProfile) || 'workspace');
          break;
        case 'open_logs':
          if (action.target && state.tasks[action.target]) {
            setActiveTaskId(action.target);
            setIsTaskDrawerOpen(true);
          } else {
            const failureId = action.target ?? String(diagnostic.message.args?.failureId ?? '');
            void diagnosticsBridge.openLogs(failureId)
              .then(() => setAnnouncement(`已打开应用日志，请搜索错误编号 ${failureId}`))
              .catch(() => {
                if (failureId && navigator.clipboard) void navigator.clipboard.writeText(failureId);
                setAnnouncement(`无法在当前宿主中打开应用日志，错误编号 ${failureId} 已复制`);
              });
          }
          break;
        case 'open_procedure_node':
          if (diagnostic.elementId) {
            const payloadNodeId = typeof action.payload?.nodeId === 'string' ? action.payload.nodeId : null;
            const nodeId = payloadNodeId || action.target;
            if (nodeId) {
              const port = typeof action.payload?.port === 'string' ? action.payload.port : null;
              setProcedureFocusRequest({
                elementId: diagnostic.elementId,
                nodeId,
                port,
                requestId: generateUUID()
              });
              setSelectedElementId(diagnostic.elementId);
              setActiveView('elements');
            }
          }
          break;
        case 'open_asset':
          if (action.target) {
            setAssetFocusId(action.target);
            setActiveView('assets');
          }
          break;
        case 'open_field':
          if (diagnostic.elementId) {
            setSelectedElementId(diagnostic.elementId);
            setActiveView('elements');
          }
          if (action.target) {
            const target = action.target;
            window.setTimeout(() => focusByContractSelector(target), 80);
          }
          break;
        default:
          break;
      }
    },
    [elevatePermission, state.tasks]
  );

  const value = useMemo(
    () => ({
      state,
      theme,
      toggleTheme,
      activeView,
      setActiveView,
      selectedElementId,
      selectedElement,
      setSelectedElementId,
      assetFocusId,
      procedureFocusRequest,
      clearProcedureFocusRequest,
      setAssetFocusId,
      isTaskDrawerOpen,
      setIsTaskDrawerOpen,
      activeTaskId,
      setActiveTaskId,
      isMaximized,
      toggleMaximize,
      systemFrameFallback,
      toggleSystemFrameFallback,
      isCreateModalOpen,
      setIsCreateModalOpen,
      isConflictModalOpen,
      setIsConflictModalOpen,
      announcement,
      loadScenario,
      getModElementEditor,
      previewModElementChange,
      getProcedureEditor,
      previewProcedureChange,
      updateProcedure,
      listWorkspaceRegistries,
      getWorkspaceReferences,
      createRegistryEntry,
      updateRegistryEntry,
      previewRegistryRename,
      planProcedureRefactor,
      planWorkspaceChanges,
      applyWorkspacePlan,
      renameRegistryEntry,
      deleteRegistryEntry,
      createModElement,
      updateModElement,
      deleteModElement,
      generateWorkspace,
      buildWorkspace,
      runClient,
      runServer,
      runDatagen,
      previewDatagenOutput,
      previewTaskSource,
      publishDatagenOutput,
      runGameTest,
      prepareGameTests, listTaskAuthorizations, getWorkspaceRoot, createTaskAuthorization, revokeTaskAuthorization,
      cancelTask,
      createRecoveryPoint,
      refreshHistory,
      compareRecoveryPoints,
      previewRecoveryRestore,
      restoreRecoveryPoint,
      resolveOperationApproval,
      getWorkspaceHealth,
      getVersionTracks,
      previewLoaderMigration,
      executeLoaderMigration,
      previewUpstreamImport,
      importUpstreamWorkspace,
      listPublishBatches,
      listInstalledPlugins,
      getUpstreamTools,
      createPublishBatch,
      prepareResourcePackClient,
      listAssets,
      previewAssetImport,
      importAsset,
      previewAssetImportBatch,
      importAssetBatch,
      previewAssetMove,
      moveAsset,
      listNewWorkspaceGenerators,
      createWorkspace,
      elevatePermission,
      reconcileRecovery,
      runDiagnosticAction
    }),
    [
      state,
      theme,
      toggleTheme,
      activeView,
      selectedElementId,
      selectedElement,
      assetFocusId,
      procedureFocusRequest,
      clearProcedureFocusRequest,
      isTaskDrawerOpen,
      activeTaskId,
      isMaximized,
      toggleMaximize,
      systemFrameFallback,
      toggleSystemFrameFallback,
      isCreateModalOpen,
      isConflictModalOpen,
      announcement,
      loadScenario,
      getModElementEditor,
      previewModElementChange,
      getProcedureEditor,
      previewProcedureChange,
      updateProcedure,
      listWorkspaceRegistries,
      getWorkspaceReferences,
      createRegistryEntry,
      updateRegistryEntry,
      previewRegistryRename,
      planProcedureRefactor,
      planWorkspaceChanges,
      applyWorkspacePlan,
      renameRegistryEntry,
      deleteRegistryEntry,
      createModElement,
      updateModElement,
      deleteModElement,
      generateWorkspace,
      buildWorkspace,
      runClient,
      runServer,
      runDatagen,
      previewDatagenOutput,
      previewTaskSource,
      publishDatagenOutput,
      runGameTest,
      prepareGameTests, listTaskAuthorizations, getWorkspaceRoot, createTaskAuthorization, revokeTaskAuthorization,
      cancelTask,
      createRecoveryPoint,
      refreshHistory,
      compareRecoveryPoints,
      previewRecoveryRestore,
      restoreRecoveryPoint,
      resolveOperationApproval,
      getWorkspaceHealth,
      getVersionTracks,
      previewLoaderMigration,
      executeLoaderMigration,
      previewUpstreamImport,
      importUpstreamWorkspace,
      listPublishBatches,
      listInstalledPlugins,
      getUpstreamTools,
      createPublishBatch,
      prepareResourcePackClient,
      listAssets,
      previewAssetImport,
      importAsset,
      previewAssetImportBatch,
      importAssetBatch,
      previewAssetMove,
      moveAsset,
      listNewWorkspaceGenerators,
      createWorkspace,
      elevatePermission,
      reconcileRecovery,
      runDiagnosticAction
    ]
  );

  return <WorkbenchContext.Provider value={value}>{children}</WorkbenchContext.Provider>;
};

export const useWorkbench = () => {
  const context = useContext(WorkbenchContext);
  if (!context) {
    throw new Error('useWorkbench must be used within WorkbenchProvider');
  }
  return context;
};
