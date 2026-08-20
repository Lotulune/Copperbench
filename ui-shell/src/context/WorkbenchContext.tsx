import React, { createContext, useContext, useEffect, useState, useMemo, useCallback } from 'react';
import {
  UUID,
  ModElementType,
  FieldChange,
  PermissionProfile,
  CommandResult,
  ModElementSummary,
  ModElementEditorProjection,
  Diagnostic,
  ActionHint,
  VersionTracksProjection,
  LoaderMigrationPreview,
  UpstreamImportPreview,
  PublishBatchListProjection,
  InstalledPluginInventory,
  UpstreamToolCatalogProjection
} from '../types/contract';
import {
  coreBridge,
  BridgeState,
  UI_SUPPORTED_SCHEMA_VERSIONS,
  UI_CLIENT_IDENTITY
} from '../bridge';
import { windowBridge } from '../bridge/windowBridge';
import { t } from '../i18n';

export type NavView = 'hub' | 'elements' | 'assets' | 'history' | 'ai' | 'plugins' | 'tracks' | 'help';

interface WorkbenchContextType {
  state: BridgeState;
  theme: 'dark' | 'light';
  toggleTheme: () => void;
  activeView: NavView;
  setActiveView: (view: NavView) => void;
  selectedElementId: UUID | null;
  selectedElement: ModElementSummary | null;
  setSelectedElementId: (id: UUID | null) => void;
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
  createModElement: (type: ModElementType, name: string) => Promise<CommandResult>;
  updateModElement: (elementId: UUID, changes: FieldChange[]) => Promise<CommandResult>;
  deleteModElement: (elementId: UUID) => Promise<CommandResult>;
  buildWorkspace: () => Promise<CommandResult>;
  runClient: () => Promise<CommandResult>;
  cancelTask: (taskId: UUID) => Promise<CommandResult>;
  createRecoveryPoint: (label: string) => Promise<CommandResult>;
  restoreRecoveryPoint: (recoveryPointId: string) => Promise<CommandResult>;
  resolveOperationApproval: (approvalId: UUID, decision: 'approve' | 'deny') => Promise<CommandResult>;
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
      await coreBridge.negotiateHandshake({
        messageType: 'handshake',
        requestId: generateUUID(),
        supportedSchemaVersions: [...UI_SUPPORTED_SCHEMA_VERSIONS],
        client: { ...UI_CLIENT_IDENTITY }
      });
      setState({ ...coreBridge.getState() });
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
          if (action.target) {
            setActiveTaskId(action.target);
            setIsTaskDrawerOpen(true);
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
    [elevatePermission]
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
      createModElement,
      updateModElement,
      deleteModElement,
      buildWorkspace,
      runClient,
      cancelTask,
      createRecoveryPoint,
      restoreRecoveryPoint,
      resolveOperationApproval,
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
      createModElement,
      updateModElement,
      deleteModElement,
      buildWorkspace,
      runClient,
      cancelTask,
      createRecoveryPoint,
      restoreRecoveryPoint,
      resolveOperationApproval,
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
