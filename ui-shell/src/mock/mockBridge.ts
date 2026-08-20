import {
  UUID,
  Command,
  CommandResult,
  Query,
  QueryResult,
  CoreEvent,
  WorkbenchProjection,
  ModElementListProjection,
  ModElementEditorProjection,
  ModElementSummary,
  TaskSummary,
  PermissionProfile,
  ScenarioDefinition,
  CreateModElementPayload,
  UpdateModElementPayload,
  DeleteModElementPayload,
  CancelTaskPayload,
  RecoveryPoint,
  HistoryProjection,
  HistoryComparison,
  OperationApprovalListProjection,
  CreateRecoveryPointPayload,
  RestoreRecoveryPointPayload,
  ResolveOperationApprovalPayload,
  ExecuteLoaderMigrationPayload,
  ImportUpstreamWorkspacePayload,
  CreatePublishBatchPayload,
  PrepareResourcePackClientPayload,
  PreviewLoaderMigrationPayload,
  PreviewUpstreamImportPayload,
  VersionTracksProjection,
  LoaderMigrationPreview,
  UpstreamImportPreview,
  MigrationReport,
  PublishBatch,
  PublishBatchListProjection,
  PublishBatchResultPayload,
  ClientLoadPreparation,
  InstalledPluginInventory,
  ElementCoverage,
  UpstreamToolCatalogProjection
} from '../types/contract';
import {
  BridgeState,
  CoreBridge,
  HandshakeRequest,
  HandshakeResult
} from '../bridge/CoreBridge';
import { SCENARIOS } from './scenarios';
import versionTracksData from '../../../ui-core/fixtures/v1.0/tracks/version-tracks.json';
import releaseNotesData from '../../../ui-core/fixtures/v1.0/release/release-notes.json';

export type { BridgeState };

type EventListener = (event: CoreEvent) => void;
type StateListener = (state: BridgeState) => void;

function generateUUID(): UUID {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

export class MockCoreBridge implements CoreBridge {
  private state: BridgeState = {
    currentScenarioId: 'ready',
    viewportState: 'ready',
    expectedUi: null,
    workbench: null,
    elements: [],
    elementEditors: {},
    tasks: {},
    taskLogs: {},
    diagnostics: [],
    recoveryPoints: [],
    currentRecoveryPointId: null,
    historyComparison: null,
    operationApprovals: [],
    versionTracks: versionTracksData as unknown as VersionTracksProjection,
    publishBatches: [
      {
        id: 'batch-default-rp',
        name: 'default_resource_pack_v1',
        sourceDirectory: 'src/main/resources',
        outputPath: 'build/distributions/copperbench-assets-1.0.0.zip',
        sha256: '9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08',
        assetCount: 18,
        createdAt: '2026-08-19T08:00:00.000Z',
        assets: [
          'assets/mod/models/item/ruby.json',
          'assets/mod/textures/item/ruby.png'
        ]
      }
    ],
    recoveryState: null,
    schemaIncompatible: false
  };

  private initial(): BridgeState {
    return {
      currentScenarioId: 'ready',
      viewportState: 'ready',
      expectedUi: null,
      workbench: null,
      elements: [],
      elementEditors: {},
      tasks: {},
      taskLogs: {},
      diagnostics: [],
      recoveryPoints: [],
      currentRecoveryPointId: null,
      historyComparison: null,
      operationApprovals: [],
      versionTracks: versionTracksData as unknown as VersionTracksProjection,
      publishBatches: [
        {
          id: 'batch-default-rp',
          name: 'default_resource_pack_v1',
          sourceDirectory: 'src/main/resources',
          outputPath: 'build/distributions/copperbench-assets-1.0.0.zip',
          sha256: '9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08',
          assetCount: 18,
          createdAt: '2026-08-19T08:00:00.000Z',
          assets: [
            'assets/mod/models/item/ruby.json',
            'assets/mod/textures/item/ruby.png'
          ]
        }
      ],
      recoveryState: null,
      schemaIncompatible: false
    };
  }

  private eventListeners = new Set<EventListener>();
  private stateListeners = new Set<StateListener>();
  private timelineTimers: ReturnType<typeof setTimeout>[] = [];
  private sequenceCounter = 100;

  constructor() {
    this.loadScenario('ready');
  }

  public getState(): BridgeState {
    return this.state;
  }

  public onEvent(listener: EventListener): () => void {
    this.eventListeners.add(listener);
    return () => this.eventListeners.delete(listener);
  }

  public onStateChange(listener: StateListener): () => void {
    this.stateListeners.add(listener);
    listener(this.state);
    return () => this.stateListeners.delete(listener);
  }

  private notifyEvent(event: CoreEvent) {
    this.eventListeners.forEach((l) => l(event));
  }

  private notifyState() {
    this.stateListeners.forEach((l) => l(this.state));
  }

  public clearTimers() {
    this.timelineTimers.forEach((t) => clearTimeout(t));
    this.timelineTimers = [];
  }

  public loadScenario(scenarioId: string) {
    this.clearTimers();
    const scenario = SCENARIOS[scenarioId] || SCENARIOS['ready'];

    // Reset to a clean projection before replaying; scenario state must not
    // leak across switches (tasks, editors and diagnostics included).
    this.state = this.initial();
    this.sequenceCounter = 100;

    // If extends, load base scenario first
    if (scenario.extendsScenarioId && SCENARIOS[scenario.extendsScenarioId]) {
      this.applyScenarioMessages(SCENARIOS[scenario.extendsScenarioId], false);
    }

    this.applyScenarioMessages(scenario, true);
    this.state.expectedUi = scenario.expectedUi ?? null;
    this.notifyState();
  }

  private applyScenarioMessages(scenario: ScenarioDefinition, isTarget: boolean) {
    if (isTarget) {
      this.state.currentScenarioId = scenario.scenarioId;
      this.state.viewportState = scenario.viewportState;
      this.state.recoveryState = null;
    }

    for (const msg of scenario.initialMessages) {
      this.processMessage(msg);
    }

    if (isTarget && scenario.timeline) {
      for (const entry of scenario.timeline) {
        const timer = setTimeout(() => {
          this.processMessage(entry.message);
          this.notifyState();
        }, entry.afterMs);
        this.timelineTimers.push(timer);
      }
    }
  }

  private processMessage(msg: unknown) {
    if (!msg || typeof msg !== 'object') return;
    const envelope = msg as { messageType: string };

    if (envelope.messageType === 'query') {
      // An in-flight query is not a result; the projection must stay empty
      // until a query_result arrives (loading-workbench scenario).
      return;
    }

    if (envelope.messageType === 'query_result') {
      const qr = msg as QueryResult;
      if (qr.status !== 'succeeded' || !qr.data) return;
      if (qr.operation === 'get_workbench') {
        this.state.workbench = qr.data as WorkbenchProjection;
        if (this.state.workbench.recentElements) {
          // Merge recent elements if elements list is not yet populated
          if (this.state.elements.length === 0) {
            this.state.elements = [...this.state.workbench.recentElements];
          }
        }
      } else if (qr.operation === 'list_mod_elements') {
        const list = qr.data as ModElementListProjection;
        this.state.elements = list.items;
        this.updateWorkbenchCounts();
      } else if (qr.operation === 'get_mod_element_editor') {
        const editor = qr.data as ModElementEditorProjection;
        this.state.elementEditors[editor.element.id] = editor;
      } else if (qr.operation === 'get_history') {
        const history = qr.data as HistoryProjection;
        this.state.recoveryPoints = [...history.recoveryPoints];
        this.state.currentRecoveryPointId = history.recoveryPoints[0]?.id ?? null;
      } else if (qr.operation === 'get_diff') {
        this.state.historyComparison = qr.data as HistoryComparison;
      } else if (qr.operation === 'list_operation_approvals') {
        const approvals = qr.data as OperationApprovalListProjection;
        this.state.operationApprovals = [...approvals.items];
      }
    } else if (envelope.messageType === 'command_result') {
      const cr = msg as CommandResult;
      if (cr.status === 'rejected') {
        if (cr.conflict) {
          this.state.viewportState = 'conflict';
        } else if (cr.denial) {
          this.state.viewportState = 'permission_denied';
          // The denial fact states the session's current profile; keep the
          // projection consistent instead of letting the UI re-derive it.
          if (this.state.workbench) {
            this.state.workbench.permission.profile = cr.denial.currentProfile;
          }
        } else {
          this.state.viewportState = 'error';
        }
      }
      if (cr.diagnostics && cr.diagnostics.length > 0) {
        this.state.diagnostics = [...cr.diagnostics];
      }
    } else if (envelope.messageType === 'handshake_result') {
      const hr = msg as HandshakeResult;
      if (hr.status === 'incompatible') {
        this.state.schemaIncompatible = true;
        if (hr.diagnostics.length > 0) {
          this.state.diagnostics = [...hr.diagnostics];
        }
      } else {
        this.state.schemaIncompatible = false;
      }
    } else if (envelope.messageType === 'event') {
      const ev = msg as CoreEvent;
      this.applyEvent(ev);
      this.notifyEvent(ev);
    }
  }

  /**
   * Startup schema negotiation. The mock core speaks 1.0; the
   * schema-incompatible fixture models a core whose versions cannot match
   * the UI's, which keeps the flag and diagnostic set by the fixture.
   */
  public async negotiateHandshake(request: HandshakeRequest): Promise<HandshakeResult> {
    if (this.state.schemaIncompatible || !request.supportedSchemaVersions.includes('1.0')) {
      return {
        messageType: 'handshake_result',
        requestId: request.requestId,
        status: 'incompatible',
        selectedSchemaVersion: null,
        coreSchemaVersions: ['1.0'],
        diagnostics:
          this.state.diagnostics.length > 0
            ? this.state.diagnostics
            : [
                {
                  code: 'UI_CORE_SCHEMA_INCOMPATIBLE',
                  severity: 'error',
                  message: {
                    key: 'diagnostic.ui_core_schema_incompatible',
                    fallback: 'The UI and Java Core do not support a common schema version.',
                    args: {
                      ui: request.supportedSchemaVersions.join(', '),
                      core: '1.0'
                    }
                  },
                  path: null,
                  elementId: null,
                  recoverable: false,
                  actions: []
                }
              ]
      };
    }
    return {
      messageType: 'handshake_result',
      requestId: request.requestId,
      status: 'compatible',
      selectedSchemaVersion: '1.0',
      coreSchemaVersions: ['1.0'],
      diagnostics: []
    };
  }

  private applyEvent(ev: CoreEvent) {
    if (this.state.workbench && ev.revision) {
      this.state.workbench.workspace.revision = ev.revision;
    }

    switch (ev.event) {
      case 'workspace_revision_advanced': {
        if (this.state.workbench) {
          this.state.workbench.workspace.revision = ev.revision;
        }
        break;
      }
      case 'mod_element_created': {
        const elem = ev.payload.element;
        const exists = this.state.elements.some((e) => e.id === elem.id);
        if (!exists) {
          this.state.elements.unshift(elem);
          this.updateWorkbenchCounts();
        }
        break;
      }
      case 'mod_element_updated': {
        const elem = ev.payload.element;
        this.state.elements = this.state.elements.map((e) =>
          e.id === elem.id ? elem : e
        );
        this.updateWorkbenchCounts();
        break;
      }
      case 'mod_element_deleted': {
        this.state.elements = this.state.elements.filter(
          (e) => e.id !== ev.payload.elementId
        );
        delete this.state.elementEditors[ev.payload.elementId];
        this.updateWorkbenchCounts();
        break;
      }
      case 'diagnostics_changed': {
        this.state.diagnostics = ev.payload.diagnostics;
        break;
      }
      case 'task_started':
      case 'task_progressed':
      case 'task_completed': {
        const task = ev.payload.task;
        this.state.tasks[task.id] = task;
        if (this.state.workbench) {
          this.state.workbench.activeTasks = Object.values(this.state.tasks).filter(
            (t) => t.state === 'running' || t.state === 'queued'
          );
        }
        break;
      }
      case 'task_log_appended': {
        const { taskId, entries } = ev.payload;
        if (!this.state.taskLogs[taskId]) {
          this.state.taskLogs[taskId] = [];
        }
        this.state.taskLogs[taskId].push(...entries);
        break;
      }
      case 'connectivity_changed': {
        if (this.state.workbench) {
          this.state.workbench.connection = ev.payload;
        }
        if (ev.payload.network === 'offline') {
          this.state.viewportState = 'offline';
        }
        break;
      }
      case 'capabilities_changed': {
        if (this.state.workbench) {
          this.state.workbench.capabilities = ev.payload.capabilities;
        }
        break;
      }
      case 'bridge_recovery_required': {
        this.state.viewportState = 'recovery';
        this.state.recoveryState = ev.payload;
        break;
      }
    }
  }

  private updateWorkbenchCounts() {
    if (!this.state.workbench) return;
    const total = this.state.elements.length;
    const valid = this.state.elements.filter((e) => e.state === 'valid').length;
    const draft = this.state.elements.filter((e) => e.state === 'draft').length;
    const invalid = this.state.elements.filter((e) => e.state === 'invalid').length;
    const unsupported = this.state.elements.filter((e) => e.state === 'unsupported').length;

    this.state.workbench.elementCounts = { total, valid, draft, invalid, unsupported };
    this.state.workbench.recentElements = this.state.elements.slice(0, 5);
  }

  /* =========================================================================
   * Interactive Dispatchers (Command / Query)
   * ========================================================================= */

  public async sendCommand<T>(command: Command<T>): Promise<CommandResult> {
    const currentRevision = this.state.workbench?.workspace.revision ?? 42;
    const workspaceId = this.state.workbench?.workspace.id ?? '11111111-1111-4111-8111-111111111111';

    // 1. Permission Check
    if (this.state.workbench?.permission.profile === 'read_only') {
      const result: CommandResult = {
        messageType: 'command_result',
        schemaVersion: '1.0',
        requestId: command.requestId,
        workspaceId,
        operation: command.operation,
        status: 'rejected',
        newRevision: currentRevision,
        recoveryPointId: null,
        task: null,
        data: null,
        conflict: null,
        denial: {
          currentProfile: 'read_only',
          requiredProfile: 'workspace',
          approvalRequired: false,
          protectedOperation: false
        },
        diagnostics: [
          {
            code: 'PERMISSION_PROFILE_DENIED',
            severity: 'error',
            message: {
              key: 'diagnostic.permission_profile_denied',
              fallback: 'Workspace permission is required to perform this action.'
            },
            path: null,
            recoverable: true,
            actions: [
              {
                id: 'request_workspace_permission',
                label: { key: 'action.request_permission', fallback: 'Request Elevation' },
                kind: 'request_permission',
                target: 'workspace'
              }
            ]
          }
        ]
      };
      this.state.viewportState = 'permission_denied';
      this.notifyState();
      return result;
    }

    // 2. Revision Check
    if (command.expectedRevision !== currentRevision) {
      const result: CommandResult = {
        messageType: 'command_result',
        schemaVersion: '1.0',
        requestId: command.requestId,
        workspaceId,
        operation: command.operation,
        status: 'rejected',
        newRevision: currentRevision,
        recoveryPointId: null,
        task: null,
        data: null,
        conflict: {
          expectedRevision: command.expectedRevision,
          actualRevision: currentRevision,
          changedPaths: []
        },
        denial: null,
        diagnostics: [
          {
            code: 'WORKSPACE_REVISION_CONFLICT',
            severity: 'error',
            message: {
              key: 'diagnostic.workspace_revision_conflict',
              fallback: `Workspace changed by another writer (Expected: ${command.expectedRevision}, Current: ${currentRevision}).`
            },
            path: null,
            recoverable: true,
            actions: [
              {
                id: 'refresh_element',
                label: { key: 'action.refresh', fallback: 'Review Latest Changes' },
                kind: 'refresh'
              }
            ]
          }
        ]
      };
      this.state.viewportState = 'conflict';
      this.notifyState();
      return result;
    }

    // 3. Operation Execution
    const newRevision = currentRevision + 1;

    switch (command.operation) {
      case 'create_mod_element': {
        const payload = command.payload as unknown as CreateModElementPayload;
        const newId = generateUUID();
        const newElement: ModElementSummary = {
          id: newId,
          type: payload.elementType,
          name: payload.name,
          displayName: payload.name
            .split('_')
            .map((s) => s.charAt(0).toUpperCase() + s.slice(1))
            .join(' '),
          state: 'draft',
          ownership: 'generated',
          updatedAt: new Date().toISOString(),
          diagnostics: { error: 0, warning: 0, info: 0 }
        };

        const result: CommandResult = {
          messageType: 'command_result',
          schemaVersion: '1.0',
          requestId: command.requestId,
          workspaceId,
          operation: 'create_mod_element',
          status: 'committed',
          newRevision,
          recoveryPointId: `rec-${generateUUID().slice(0, 8)}`,
          task: null,
          data: { element: newElement },
          conflict: null,
          denial: null,
          diagnostics: []
        };

        // Advance Revision and add element
        if (this.state.workbench) {
          this.state.workbench.workspace.revision = newRevision;
        }
        this.state.elements.unshift(newElement);
        this.updateWorkbenchCounts();

        const createdEvent: CoreEvent = {
          messageType: 'event',
          schemaVersion: '1.0',
          eventId: generateUUID(),
          workspaceId,
          revision: newRevision,
          sequence: ++this.sequenceCounter,
          occurredAt: new Date().toISOString(),
          event: 'mod_element_created',
          causedByRequestId: command.requestId,
          payload: { element: newElement }
        };
        this.notifyEvent(createdEvent);
        this.notifyState();
        return result;
      }

      case 'update_mod_element': {
        const payload = command.payload as unknown as UpdateModElementPayload;
        // Check for simulated validation error
        const hardnessChange = payload.changes.find((c) => c.path === '/fields/hardness');
        if (
          hardnessChange &&
          typeof hardnessChange.value === 'number' &&
          (hardnessChange.value < 0 || hardnessChange.value > 100)
        ) {
          const result: CommandResult = {
            messageType: 'command_result',
            schemaVersion: '1.0',
            requestId: command.requestId,
            workspaceId,
            operation: 'update_mod_element',
            status: 'rejected',
            newRevision: currentRevision,
            recoveryPointId: null,
            task: null,
            data: null,
            conflict: null,
            denial: null,
            diagnostics: [
              {
                code: 'FIELD_VALUE_OUT_OF_RANGE',
                severity: 'error',
                message: {
                  key: 'diagnostic.field_value_out_of_range',
                  fallback: 'Hardness must be between 0 and 100.',
                  args: { min: 0, max: 100 }
                },
                path: '/fields/hardness',
                elementId: payload.elementId,
                recoverable: true,
                actions: [
                  {
                    id: 'open_invalid_field',
                    label: { key: 'action.open_field', fallback: 'Locate Invalid Field' },
                    kind: 'open_field',
                    target: '/fields/hardness'
                  }
                ]
              }
            ]
          };
          this.state.diagnostics = [...result.diagnostics];
          this.notifyState();
          return result;
        }

        // Apply update
        let updatedElem: ModElementSummary | null = null;
        this.state.elements = this.state.elements.map((e) => {
          if (e.id === payload.elementId) {
            updatedElem = {
              ...e,
              state: 'valid',
              updatedAt: new Date().toISOString()
            };
            return updatedElem;
          }
          return e;
        });

        if (this.state.workbench) {
          this.state.workbench.workspace.revision = newRevision;
        }

        const result: CommandResult = {
          messageType: 'command_result',
          schemaVersion: '1.0',
          requestId: command.requestId,
          workspaceId,
          operation: 'update_mod_element',
          status: 'committed',
          newRevision,
          recoveryPointId: `rec-${generateUUID().slice(0, 8)}`,
          task: null,
          data: updatedElem ? { element: updatedElem } : null,
          conflict: null,
          denial: null,
          diagnostics: []
        };

        if (updatedElem) {
          const updatedEvent: CoreEvent = {
            messageType: 'event',
            schemaVersion: '1.0',
            eventId: generateUUID(),
            workspaceId,
            revision: newRevision,
            sequence: ++this.sequenceCounter,
            occurredAt: new Date().toISOString(),
            event: 'mod_element_updated',
            causedByRequestId: command.requestId,
            payload: { element: updatedElem }
          };
          this.notifyEvent(updatedEvent);
        }
        this.notifyState();
        return result;
      }

      case 'delete_mod_element': {
        const payload = command.payload as unknown as DeleteModElementPayload;
        const target = this.state.elements.find((e) => e.id === payload.elementId);
        this.state.elements = this.state.elements.filter((e) => e.id !== payload.elementId);
        if (this.state.workbench) {
          this.state.workbench.workspace.revision = newRevision;
        }
        this.updateWorkbenchCounts();

        const result: CommandResult = {
          messageType: 'command_result',
          schemaVersion: '1.0',
          requestId: command.requestId,
          workspaceId,
          operation: 'delete_mod_element',
          status: 'committed',
          newRevision,
          recoveryPointId: `rec-${generateUUID().slice(0, 8)}`,
          task: null,
          data: { elementId: payload.elementId },
          conflict: null,
          denial: null,
          diagnostics: []
        };

        const deletedEvent: CoreEvent = {
          messageType: 'event',
          schemaVersion: '1.0',
          eventId: generateUUID(),
          workspaceId,
          revision: newRevision,
          sequence: ++this.sequenceCounter,
          occurredAt: new Date().toISOString(),
          event: 'mod_element_deleted',
          causedByRequestId: command.requestId,
          payload: {
            elementId: payload.elementId,
            name: target?.name ?? 'element'
          }
        };
        this.notifyEvent(deletedEvent);
        this.notifyState();
        return result;
      }

      case 'build_workspace':
      case 'generate_workspace':
      case 'export_workspace':
      case 'validate_workspace': {
		const taskId = generateUUID();
		const kind = command.operation === 'build_workspace' ? 'build' : command.operation === 'generate_workspace' ? 'generate' : command.operation === 'export_workspace' ? 'export' : 'validate';
        const task: TaskSummary = {
          id: taskId,
          kind,
          state: 'running',
          cancellable: true,
          progress: 0.05,
          stage: { key: 'task.starting', fallback: `Starting ${kind} task...` },
          startedAt: new Date().toISOString(),
          diagnostics: { error: 0, warning: 0, info: 0 }
        };

        this.state.tasks[taskId] = task;
        this.state.taskLogs[taskId] = [
          {
            sequence: 1,
            timestamp: new Date().toISOString(),
            level: 'info',
            text: `Initiating ${kind} workflow for Fabric 1.21.1...`
          }
        ];
        if (this.state.workbench) {
          this.state.workbench.activeTasks = [task];
        }

        const result: CommandResult = {
          messageType: 'command_result',
          schemaVersion: '1.0',
          requestId: command.requestId,
          workspaceId,
          operation: command.operation,
          status: 'accepted',
          newRevision: currentRevision,
          recoveryPointId: null,
          task,
          data: null,
          conflict: null,
          denial: null,
          diagnostics: []
        };

        // Emit task_started event
        const startEv: CoreEvent = {
          messageType: 'event',
          schemaVersion: '1.0',
          eventId: generateUUID(),
          workspaceId,
          revision: currentRevision,
          sequence: ++this.sequenceCounter,
          occurredAt: new Date().toISOString(),
          event: 'task_started',
          causedByRequestId: command.requestId,
          payload: { task }
        };
        this.notifyEvent(startEv);
        this.notifyState();

        // Simulate progression
        const t1 = setTimeout(() => {
          task.progress = 0.5;
          task.stage = { key: 'task.compiling', fallback: 'Compiling Java sources & mixins...' };
          this.state.taskLogs[taskId].push({
            sequence: 2,
            timestamp: new Date().toISOString(),
            level: 'info',
            text: 'Compiling generated Java classes with JDK 25...'
          });
          const progEv: CoreEvent = {
            messageType: 'event',
            schemaVersion: '1.0',
            eventId: generateUUID(),
            workspaceId,
            revision: currentRevision,
            sequence: ++this.sequenceCounter,
            occurredAt: new Date().toISOString(),
            event: 'task_progressed',
            causedByRequestId: command.requestId,
            payload: { task: { ...task } }
          };
          this.notifyEvent(progEv);
          this.notifyState();
        }, 800);

        const t2 = setTimeout(() => {
          task.progress = 1.0;
          task.state = 'succeeded';
          task.cancellable = false;
          task.stage = { key: 'task.completed', fallback: `${kind.toUpperCase()} completed successfully.` };
          task.completedAt = new Date().toISOString();
          this.state.taskLogs[taskId].push({
            sequence: 3,
            timestamp: new Date().toISOString(),
            level: 'info',
            text: `Task ${kind} finished with 0 errors. Artifact ready.`
          });
          const doneEv: CoreEvent = {
            messageType: 'event',
            schemaVersion: '1.0',
            eventId: generateUUID(),
            workspaceId,
            revision: currentRevision,
            sequence: ++this.sequenceCounter,
            occurredAt: new Date().toISOString(),
            event: 'task_completed',
            causedByRequestId: command.requestId,
            payload: { task: { ...task } }
          };
          this.notifyEvent(doneEv);
          this.notifyState();
        }, 1600);

        this.timelineTimers.push(t1, t2);
        return result;
      }

      case 'cancel_task': {
        const payload = command.payload as unknown as CancelTaskPayload;
        const target = this.state.tasks[payload.taskId];
        if (target) {
          target.state = 'cancelled';
          target.cancellable = false;
          target.stage = { key: 'task.cancelled', fallback: 'Task cancelled by user.' };
          target.completedAt = new Date().toISOString();
          if (this.state.taskLogs[payload.taskId]) {
            this.state.taskLogs[payload.taskId].push({
              sequence: this.state.taskLogs[payload.taskId].length + 1,
              timestamp: new Date().toISOString(),
              level: 'warning',
              text: 'Process cancelled. Cleaning up Gradle runner locks...'
            });
          }
        }
        const result: CommandResult = {
          messageType: 'command_result',
          schemaVersion: '1.0',
          requestId: command.requestId,
          workspaceId,
          operation: 'cancel_task',
          status: 'completed',
          newRevision: currentRevision,
          recoveryPointId: null,
          task: target ?? null,
          data: null,
          conflict: null,
          denial: null,
          diagnostics: []
        };
        if (target) {
          const cancelEv: CoreEvent = {
            messageType: 'event',
            schemaVersion: '1.0',
            eventId: generateUUID(),
            workspaceId,
            revision: currentRevision,
            sequence: ++this.sequenceCounter,
            occurredAt: new Date().toISOString(),
            event: 'task_completed',
            causedByRequestId: command.requestId,
            payload: { task: target }
          };
          this.notifyEvent(cancelEv);
        }
        this.notifyState();
        return result;
      }

      case 'create_recovery_point': {
        const payload = command.payload as unknown as CreateRecoveryPointPayload;
        const recoveryPoint: RecoveryPoint = {
          id: generateUUID().replaceAll('-', '').slice(0, 16),
          label: payload.label,
          actor: 'ui',
          taskId: '',
          createdAt: new Date().toISOString()
        };
        this.state.recoveryPoints = [recoveryPoint, ...this.state.recoveryPoints];
        this.state.currentRecoveryPointId = recoveryPoint.id;
        const result: CommandResult = {
          messageType: 'command_result',
          schemaVersion: '1.0',
          requestId: command.requestId,
          workspaceId,
          operation: 'create_recovery_point',
          status: 'committed',
          newRevision: currentRevision,
          recoveryPointId: recoveryPoint.id,
          task: null,
          data: { recoveryPoint },
          conflict: null,
          denial: null,
          diagnostics: []
        };
        this.notifyState();
        return result;
      }

      case 'restore_recovery_point': {
        const payload = command.payload as unknown as RestoreRecoveryPointPayload;
        const changedPaths = this.state.historyComparison?.changes.map((change) => change.path) ?? [];
        this.state.currentRecoveryPointId = payload.recoveryPointId;
        if (this.state.workbench) {
          this.state.workbench.workspace.revision = newRevision;
          this.state.workbench.workspace.dirty = false;
        }
        const result: CommandResult = {
          messageType: 'command_result',
          schemaVersion: '1.0',
          requestId: command.requestId,
          workspaceId,
          operation: 'restore_recovery_point',
          status: 'committed',
          newRevision,
          recoveryPointId: payload.recoveryPointId,
          task: null,
          data: { recoveryPointId: payload.recoveryPointId, changedPaths },
          conflict: null,
          denial: null,
          diagnostics: []
        };
        this.notifyState();
        return result;
      }

      case 'resolve_operation_approval': {
        const payload = command.payload as unknown as ResolveOperationApprovalPayload;
        this.state.operationApprovals = this.state.operationApprovals.filter(
          (approval) => approval.id !== payload.approvalId
        );
        const result: CommandResult = {
          messageType: 'command_result',
          schemaVersion: '1.0',
          requestId: command.requestId,
          workspaceId,
          operation: 'resolve_operation_approval',
          status: 'completed',
          newRevision: currentRevision,
          recoveryPointId: null,
          task: null,
          data: { approvalId: payload.approvalId, decision: payload.decision },
          conflict: null,
          denial: null,
          diagnostics: []
        };
        this.notifyState();
        return result;
      }

      case 'execute_loader_migration': {
        const payload = command.payload as unknown as ExecuteLoaderMigrationPayload;
        if (!payload.userApproved) {
          const result: CommandResult = {
            messageType: 'command_result',
            schemaVersion: '1.0',
            requestId: command.requestId,
            workspaceId,
            operation: 'execute_loader_migration',
            status: 'rejected',
            newRevision: currentRevision,
            recoveryPointId: null,
            task: null,
            data: null,
            conflict: null,
            denial: null,
            diagnostics: [
              {
                code: 'MIGRATION_USER_CONFIRMATION_REQUIRED',
                severity: 'error',
                message: {
                  key: 'diagnostic.migration_confirmation_required',
                  fallback: '用户必须确认迁移差异后方可执行。'
                },
                path: null,
                recoverable: true,
                actions: []
              }
            ]
          };
          this.state.diagnostics = [...result.diagnostics];
          this.notifyState();
          return result;
        }

        const isSupported = payload.targetGeneratorId === 'neoforge-1.21.1';
        if (!isSupported) {
          const incompleteReport: MigrationReport = {
            kind: 'loader',
            sourceGeneratorId: this.state.workbench?.workspace.generator.id ?? 'fabric-1.21.1',
            targetGeneratorId: payload.targetGeneratorId,
            sourceHash: 'sha256-mock-hash-4421',
            targetDirectory: null,
            sourceUnchanged: true,
            complete: false,
            items: [
              {
                path: '/generator',
                name: 'generator',
                type: 'generator',
                disposition: 'blocked',
                reasonCode: payload.targetGeneratorId.includes('26.')
                  ? 'TRACK_GENERATE_READY'
                  : 'VERSION_TRACK_GENERATOR_MISSING',
                nextStep: 'Choose a first-party Fabric/NeoForge pair on the same Minecraft version.'
              }
            ],
            blockedCount: 1,
            lostCount: 0,
            manualCount: 0
          };
          const result: CommandResult = {
            messageType: 'command_result',
            schemaVersion: '1.0',
            requestId: command.requestId,
            workspaceId,
            operation: 'execute_loader_migration',
            status: 'rejected',
            newRevision: currentRevision,
            recoveryPointId: null,
            task: null,
            data: incompleteReport,
            conflict: null,
            denial: null,
            diagnostics: [
              {
                code: 'MIGRATION_INCOMPLETE',
                severity: 'error',
                message: {
                  key: 'diagnostic.migration_incomplete',
                  fallback: 'The copy was created or previewed but is not a complete supported migration.'
                },
                path: null,
                recoverable: false,
                actions: []
              }
            ]
          };
          this.state.diagnostics = [...result.diagnostics];
          this.notifyState();
          return result;
        }

        const targetDirectory = `workspaces/${payload.outputName || 'workspace_neoforge_1_21_1'}`;
        const copyResult: MigrationReport = {
          kind: 'loader',
          sourceGeneratorId: this.state.workbench?.workspace.generator.id ?? 'fabric-1.21.1',
          targetGeneratorId: payload.targetGeneratorId,
          sourceHash: 'sha256-mock-hash-4421',
          targetDirectory,
          sourceUnchanged: true,
          complete: true,
          items: [
            {
              path: '/generator',
              name: 'generator',
              type: 'generator',
              disposition: 'supported',
              reasonCode: 'GENERATOR_SWITCH',
              nextStep: 'The target copy uses neoforge-1.21.1 as its single active generator.'
            },
            {
              path: '/elements/ruby_ore',
              name: 'ruby_ore',
              type: 'block',
              disposition: 'supported',
              reasonCode: 'COMMON_FIELDS_COPIED',
              nextStep: 'Common vertical-slice fields copy without conversion.'
            },
            {
              path: '/elements/ruby_gem',
              name: 'ruby_gem',
              type: 'item',
              disposition: 'supported',
              reasonCode: 'COMMON_FIELDS_COPIED',
              nextStep: 'Common vertical-slice fields copy without conversion.'
            },
            {
              path: '/elements/ruby_block_recipe',
              name: 'ruby_block_recipe',
              type: 'recipe',
              disposition: 'supported',
              reasonCode: 'COMMON_FIELDS_COPIED',
              nextStep: 'Common vertical-slice fields copy without conversion.'
            },
            {
              path: '/elements/ruby_fire_handler',
              name: 'ruby_fire_handler',
              type: 'procedure',
              disposition: 'substitute',
              reasonCode: 'LOADER_EVENT_SUBSTITUTED',
              nextStep: 'Fabric event listener replaced with equivalent NeoForge subscriber.'
            },
            {
              path: '/elements/ruby_aura_particle',
              name: 'ruby_aura_particle',
              type: 'procedure',
              disposition: 'manual',
              reasonCode: 'LOADER_EXCLUSIVE_FIELDS_PRESERVED',
              nextStep: 'Loader-exclusive fields were copied unchanged and need review in the target generator.'
            }
          ],
          blockedCount: 0,
          lostCount: 0,
          manualCount: 1
        };

        const result: CommandResult = {
          messageType: 'command_result',
          schemaVersion: '1.0',
          requestId: command.requestId,
          workspaceId,
          operation: 'execute_loader_migration',
          status: 'committed',
          newRevision,
          recoveryPointId: `rec-${generateUUID().slice(0, 8)}`,
          task: null,
          data: copyResult,
          conflict: null,
          denial: null,
          diagnostics: []
        };

        if (this.state.workbench) {
          this.state.workbench.workspace.revision = newRevision;
        }

        const ev: CoreEvent = {
          messageType: 'event',
          schemaVersion: '1.0',
          eventId: generateUUID(),
          workspaceId,
          revision: newRevision,
          sequence: ++this.sequenceCounter,
          occurredAt: new Date().toISOString(),
          event: 'loader_migration_executed',
          causedByRequestId: command.requestId,
          payload: copyResult
        };
        this.notifyEvent(ev);
        this.notifyState();
        return result;
      }

      case 'import_upstream_workspace': {
        const payload = command.payload as unknown as ImportUpstreamWorkspacePayload;
        const profile = this.state.workbench?.permission.profile ?? 'workspace';
        if (profile !== 'full_access') {
          const result: CommandResult = {
            messageType: 'command_result',
            schemaVersion: '1.0',
            requestId: command.requestId,
            workspaceId,
            operation: 'import_upstream_workspace',
            status: 'rejected',
            newRevision: currentRevision,
            recoveryPointId: null,
            task: null,
            data: null,
            conflict: null,
            denial: {
              currentProfile: profile,
              requiredProfile: 'full_access',
              approvalRequired: false,
              protectedOperation: true
            },
            diagnostics: [
              {
                code: 'PERMISSION_DENIED',
                severity: 'error',
                message: {
                  key: 'diagnostic.permission_denied',
                  fallback: '迁入上游工作区需要桌面 Full Access 权限。'
                },
                path: null,
                recoverable: false,
                actions: []
              }
            ]
          };
          this.state.viewportState = 'permission_denied';
          this.notifyState();
          return result;
        }

        if (!payload.userApproved) {
          const result: CommandResult = {
            messageType: 'command_result',
            schemaVersion: '1.0',
            requestId: command.requestId,
            workspaceId,
            operation: 'import_upstream_workspace',
            status: 'rejected',
            newRevision: currentRevision,
            recoveryPointId: null,
            task: null,
            data: null,
            conflict: null,
            denial: {
              currentProfile: 'full_access',
              requiredProfile: 'full_access',
              approvalRequired: true,
              protectedOperation: true
            },
            diagnostics: [
              {
                code: 'USER_APPROVAL_REQUIRED',
                severity: 'error',
                message: {
                  key: 'diagnostic.user_approval_required',
                  fallback: 'Importing an upstream workspace copies it and requires confirmation.'
                },
                path: null,
                recoverable: true,
                actions: []
              }
            ]
          };
          this.notifyState();
          return result;
        }

        const targetDirectory = `workspaces/${payload.outputName || 'workspace_imported_copy'}`;
        const copyResult: MigrationReport = {
          kind: 'upstream_import',
          sourceGeneratorId: 'mcreator-2024.2-fabric',
          targetGeneratorId: 'fabric-1.21.1',
          sourceHash: 'sha256-upstream-hash-7711',
          targetDirectory,
          sourceUnchanged: true,
          complete: true,
          items: [
            {
              path: '/workspace.mcreator',
              name: 'workspace',
              type: 'workspace',
              disposition: 'supported',
              reasonCode: 'UPSTREAM_WORKSPACE_COPIED',
              nextStep: 'The upstream workspace is copied; the source directory is left untouched.'
            },
            {
              path: '/workspaceSettings/currentGenerator',
              name: 'generator',
              type: 'generator',
              disposition: 'supported',
              reasonCode: 'GENERATOR_PRESERVED',
              nextStep: 'The original generator identifier is preserved in the copy.'
            },
            {
              path: '/elements/copper_sword',
              name: 'copper_sword',
              type: 'item',
              disposition: 'supported',
              reasonCode: 'ELEMENT_COPIED',
              nextStep: 'Known vertical-slice element copied with its original definition.'
            },
            {
              path: '/elements/copper_block',
              name: 'copper_block',
              type: 'block',
              disposition: 'supported',
              reasonCode: 'ELEMENT_COPIED',
              nextStep: 'Known vertical-slice element copied with its original definition.'
            },
            {
              path: '/elements/copper_pickaxe_recipe',
              name: 'copper_pickaxe_recipe',
              type: 'recipe',
              disposition: 'supported',
              reasonCode: 'ELEMENT_COPIED',
              nextStep: 'Known vertical-slice element copied with its original definition.'
            },
            {
              path: '/elements/copper_strike_proc',
              name: 'copper_strike_proc',
              type: 'procedure',
              disposition: 'substitute',
              reasonCode: 'ELEMENT_COPIED',
              nextStep: 'Known vertical-slice element copied with its original definition.'
            }
          ],
          blockedCount: 0,
          lostCount: 0,
          manualCount: 0
        };

        const result: CommandResult = {
          messageType: 'command_result',
          schemaVersion: '1.0',
          requestId: command.requestId,
          workspaceId,
          operation: 'import_upstream_workspace',
          status: 'committed',
          newRevision,
          recoveryPointId: `rec-${generateUUID().slice(0, 8)}`,
          task: null,
          data: copyResult,
          conflict: null,
          denial: null,
          diagnostics: []
        };

        if (this.state.workbench) {
          this.state.workbench.workspace.revision = newRevision;
        }

        const ev: CoreEvent = {
          messageType: 'event',
          schemaVersion: '1.0',
          eventId: generateUUID(),
          workspaceId,
          revision: newRevision,
          sequence: ++this.sequenceCounter,
          occurredAt: new Date().toISOString(),
          event: 'upstream_workspace_imported',
          causedByRequestId: command.requestId,
          payload: copyResult
        };
        this.notifyEvent(ev);
        this.notifyState();
        return result;
      }

      case 'create_publish_batch': {
        const payload = command.payload as unknown as CreatePublishBatchPayload;
        const newBatch: PublishBatch = {
          id: generateUUID(),
          name: payload.name,
          sourceDirectory: payload.sourceDirectory,
          outputPath: payload.output,
          sha256: '9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08',
          assetCount: 18,
          createdAt: new Date().toISOString(),
          assets: [
            'assets/mod/models/item/ruby.json',
            'assets/mod/textures/item/ruby.png'
          ]
        };
        this.state.publishBatches = [newBatch, ...this.state.publishBatches];

        const resultData: PublishBatchResultPayload = {
          complete: true,
          batch: newBatch
        };

        const result: CommandResult = {
          messageType: 'command_result',
          schemaVersion: '1.0',
          requestId: command.requestId,
          workspaceId,
          operation: 'create_publish_batch',
          status: 'committed',
          newRevision,
          recoveryPointId: null,
          task: null,
          data: resultData,
          conflict: null,
          denial: null,
          diagnostics: []
        };

        const ev: CoreEvent = {
          messageType: 'event',
          schemaVersion: '1.0',
          eventId: generateUUID(),
          workspaceId,
          revision: newRevision,
          sequence: ++this.sequenceCounter,
          occurredAt: new Date().toISOString(),
          event: 'publish_batch_created',
          causedByRequestId: command.requestId,
          payload: resultData
        };
        this.notifyEvent(ev);
        this.notifyState();
        return result;
      }

      case 'prepare_resource_pack_client': {
        const payload = command.payload as unknown as PrepareResourcePackClientPayload;
        const preparation: ClientLoadPreparation = {
          zipRelativePath: `run/resourcepacks/${payload.zipFileName}`,
          sha256: '9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08',
          packFormat: 48,
          optionsRelativePath: 'run/options.txt',
          readyForClient: true,
          clientLaunched: false,
          complete: true
        };

        const result: CommandResult = {
          messageType: 'command_result',
          schemaVersion: '1.0',
          requestId: command.requestId,
          workspaceId,
          operation: 'prepare_resource_pack_client',
          status: 'committed',
          newRevision: currentRevision,
          recoveryPointId: null,
          task: null,
          data: preparation,
          conflict: null,
          denial: null,
          diagnostics: []
        };

        const ev: CoreEvent = {
          messageType: 'event',
          schemaVersion: '1.0',
          eventId: generateUUID(),
          workspaceId,
          revision: currentRevision,
          sequence: ++this.sequenceCounter,
          occurredAt: new Date().toISOString(),
          event: 'resource_pack_client_prepared',
          causedByRequestId: command.requestId,
          payload: preparation
        };
        this.notifyEvent(ev);
        this.notifyState();
        return result;
      }

      default:
        throw new Error(`Unsupported operation: ${command.operation}`);
    }
  }

  public async sendQuery<T>(query: Query): Promise<QueryResult<T>> {
    const revision = this.state.workbench?.workspace.revision ?? 42;
    const workspaceId = this.state.workbench?.workspace.id ?? '11111111-1111-4111-8111-111111111111';

    let data: unknown = null;
    switch (query.operation) {
      case 'get_workbench':
        data = this.state.workbench;
        break;
      case 'list_mod_elements':
        data = {
          items: this.state.elements,
          page: 1,
          pageSize: 50,
          total: this.state.elements.length,
          availableTypes: ['block', 'item', 'recipe', 'procedure']
        };
        break;
      case 'get_version_tracks':
        data = {
          ...(versionTracksData as unknown as VersionTracksProjection),
          currentWorkspace: {
            generator: {
              id: this.state.workbench?.workspace.generator.id ?? 'fabric-1.21.1',
              loader: this.state.workbench?.workspace.generator.loader ?? 'fabric',
              minecraftVersion: this.state.workbench?.workspace.generator.minecraftVersion ?? '1.21.1',
              displayName: this.state.workbench?.workspace.generator.displayName ?? 'Fabric 1.21.1',
              state: this.state.workbench?.workspace.generator.state ?? 'ready'
            },
            status: 'supported',
            reasonCode: 'TRACK_SUPPORTED',
            generatable: true
          }
        } satisfies VersionTracksProjection;
        break;
      case 'get_release_notes':
        data = releaseNotesData;
        break;
      case 'preview_loader_migration': {
        const targetGeneratorId = (query.payload as PreviewLoaderMigrationPayload)?.targetGeneratorId ?? 'neoforge-1.21.1';
        const sourceGeneratorId = this.state.workbench?.workspace.generator.id ?? 'fabric-1.21.1';

        if (targetGeneratorId === 'neoforge-1.21.1') {
          data = {
            kind: 'loader',
            sourceGeneratorId,
            targetGeneratorId,
            sourceHash: 'sha256-mock-hash-4421',
            targetDirectory: null,
            sourceUnchanged: true,
            complete: true,
            items: [
              {
                path: '/generator',
                name: 'generator',
                type: 'generator',
                disposition: 'supported',
                reasonCode: 'GENERATOR_SWITCH',
                nextStep: 'The target copy uses neoforge-1.21.1 as its single active generator.'
              },
              {
                path: '/elements/ruby_ore',
                name: 'ruby_ore',
                type: 'block',
                disposition: 'supported',
                reasonCode: 'COMMON_FIELDS_COPIED',
                nextStep: 'Common vertical-slice fields copy without conversion.'
              },
              {
                path: '/elements/ruby_gem',
                name: 'ruby_gem',
                type: 'item',
                disposition: 'supported',
                reasonCode: 'COMMON_FIELDS_COPIED',
                nextStep: 'Common vertical-slice fields copy without conversion.'
              },
              {
                path: '/elements/ruby_block_recipe',
                name: 'ruby_block_recipe',
                type: 'recipe',
                disposition: 'supported',
                reasonCode: 'COMMON_FIELDS_COPIED',
                nextStep: 'Common vertical-slice fields copy without conversion.'
              },
              {
                path: '/elements/ruby_fire_handler',
                name: 'ruby_fire_handler',
                type: 'procedure',
                disposition: 'substitute',
                reasonCode: 'LOADER_EVENT_SUBSTITUTED',
                nextStep: 'Fabric event listener replaced with equivalent NeoForge subscriber.'
              },
              {
                path: '/elements/ruby_aura_particle',
                name: 'ruby_aura_particle',
                type: 'procedure',
                disposition: 'manual',
                reasonCode: 'LOADER_EXCLUSIVE_FIELDS_PRESERVED',
                nextStep: 'Loader-exclusive fields were copied unchanged and need review in the target generator.'
              }
            ],
            blockedCount: 0,
            lostCount: 0,
            manualCount: 1
          } satisfies LoaderMigrationPreview;
        } else if (targetGeneratorId.includes('26.')) {
          data = {
            kind: 'loader',
            sourceGeneratorId,
            targetGeneratorId,
            sourceHash: 'sha256-mock-hash-4421',
            targetDirectory: null,
            sourceUnchanged: true,
            complete: false,
            items: [
              {
                path: '/generator',
                name: 'generator',
                type: 'generator',
                disposition: 'blocked',
                reasonCode: 'TRACK_GENERATE_READY',
                nextStep: 'Choose a first-party Fabric/NeoForge pair on the same Minecraft version.'
              },
              {
                path: '/elements/ruby_ore',
                name: 'ruby_ore',
                type: 'block',
                disposition: 'supported',
                reasonCode: 'COMMON_FIELDS_COPIED',
                nextStep: 'Common vertical-slice fields copy without conversion.'
              },
              {
                path: '/elements/ruby_block_recipe',
                name: 'ruby_block_recipe',
                type: 'recipe',
                disposition: 'substitute',
                reasonCode: 'RECIPE_SCHEMA_UPGRADE',
                nextStep: 'Recipe converted to 26.1 format.'
              },
              {
                path: '/elements/legacy_fluid_registry',
                name: 'legacy_fluid_registry',
                type: 'procedure',
                disposition: 'lost',
                reasonCode: 'API_DEPRECATED_UNAVAILABLE',
                nextStep: '1.21.1 fluid API removed in 26.1; procedure must be rewritten.'
              },
              {
                path: '/elements/render_mixin',
                name: 'render_mixin',
                type: 'procedure',
                disposition: 'blocked',
                reasonCode: 'TRACK_GENERATE_READY',
                nextStep: 'Target generator build evidence is not yet claimed.'
              },
              {
                path: '/elements/ruby_aura_particle',
                name: 'ruby_aura_particle',
                type: 'procedure',
                disposition: 'manual',
                reasonCode: 'RENDER_PIPELINE_CHANGED',
                nextStep: 'Particle registration requires manual review in 26.1.'
              }
            ],
            blockedCount: 2,
            lostCount: 1,
            manualCount: 1
          } satisfies LoaderMigrationPreview;
        } else {
          data = {
            kind: 'loader',
            sourceGeneratorId,
            targetGeneratorId,
            sourceHash: 'sha256-mock-hash-4421',
            targetDirectory: null,
            sourceUnchanged: true,
            complete: false,
            items: [
              {
                path: '/generator',
                name: 'generator',
                type: 'generator',
                disposition: 'blocked',
                reasonCode: 'VERSION_TRACK_GENERATOR_MISSING',
                nextStep: 'Choose a first-party Fabric/NeoForge pair on the same Minecraft version.'
              }
            ],
            blockedCount: 1,
            lostCount: 0,
            manualCount: 0
          } satisfies LoaderMigrationPreview;
        }
        break;
      }
      case 'preview_upstream_import': {
        const _sourceWorkspacePath = (query.payload as PreviewUpstreamImportPayload)?.sourceWorkspacePath ?? 'fixtures/upstream/sample_workspace';
        void _sourceWorkspacePath;
        data = {
          kind: 'upstream_import',
          sourceGeneratorId: 'mcreator-2024.2-fabric',
          targetGeneratorId: 'fabric-1.21.1',
          sourceHash: 'sha256-upstream-hash-7711',
          targetDirectory: null,
          sourceUnchanged: true,
          complete: true,
          items: [
            {
              path: '/workspace.mcreator',
              name: 'workspace',
              type: 'workspace',
              disposition: 'supported',
              reasonCode: 'UPSTREAM_WORKSPACE_COPIED',
              nextStep: 'The upstream workspace is copied; the source directory is left untouched.'
            },
            {
              path: '/workspaceSettings/currentGenerator',
              name: 'generator',
              type: 'generator',
              disposition: 'supported',
              reasonCode: 'GENERATOR_PRESERVED',
              nextStep: 'The original generator identifier is preserved in the copy.'
            },
            {
              path: '/elements/copper_sword',
              name: 'copper_sword',
              type: 'item',
              disposition: 'supported',
              reasonCode: 'ELEMENT_COPIED',
              nextStep: 'Known vertical-slice element copied with its original definition.'
            },
            {
              path: '/elements/copper_block',
              name: 'copper_block',
              type: 'block',
              disposition: 'supported',
              reasonCode: 'ELEMENT_COPIED',
              nextStep: 'Known vertical-slice element copied with its original definition.'
            },
            {
              path: '/elements/copper_pickaxe_recipe',
              name: 'copper_pickaxe_recipe',
              type: 'recipe',
              disposition: 'supported',
              reasonCode: 'ELEMENT_COPIED',
              nextStep: 'Known vertical-slice element copied with its original definition.'
            },
            {
              path: '/elements/copper_strike_proc',
              name: 'copper_strike_proc',
              type: 'procedure',
              disposition: 'substitute',
              reasonCode: 'ELEMENT_COPIED',
              nextStep: 'Known vertical-slice element copied with its original definition.'
            }
          ],
          blockedCount: 0,
          lostCount: 0,
          manualCount: 0
        } satisfies UpstreamImportPreview;
        break;
      }
      case 'list_publish_batches':
        data = { items: this.state.publishBatches } satisfies PublishBatchListProjection;
        break;
      case 'get_element_coverage':
        data = (releaseNotesData as { elementCoverage: ElementCoverage }).elementCoverage;
        break;
      case 'get_upstream_tools':
        data = (releaseNotesData as { upstreamTools: UpstreamToolCatalogProjection }).upstreamTools;
        break;
      case 'list_installed_plugins':
        data = {
          loadsJava: false,
          scannedRoots: ['plugins'],
          plugins: [
            {
              pluginId: 'generator-1.21.1',
              path: 'plugins/generator-1.21.1',
              firstParty: true,
              level: 'A',
              route: 'RESOURCE_PIPELINE',
              containsJavaCode: false,
              versionSupported: true,
              displayName: 'Generator 1.21.1',
              version: '1.0'
            }
          ]
        } satisfies InstalledPluginInventory;
        break;
      case 'get_mod_element_editor': {
        const elemId = (query.payload as { elementId?: UUID })?.elementId;
        if (elemId && this.state.elementEditors[elemId]) {
          data = this.state.elementEditors[elemId];
        } else {
          // Generate and cache a default editor projection. The shape mirrors
          // the EditorField contract (control kinds, constraints, readOnly
          // loader extensions) so the UI renders purely schema-driven.
          const elem = this.state.elements.find((e) => e.id === elemId) || this.state.elements[0];
          const editor: ModElementEditorProjection = {
            element: elem,
            sections: [
              {
                id: 'block.general',
                title: { key: 'editor.general', fallback: 'General Attributes (通用属性)' },
                fields: [
                  {
                    path: '/fields/name',
                    label: { key: 'field.name', fallback: 'Identifier Name (内部标识符)' },
                    control: 'text',
                    required: true,
                    readOnly: false,
                    value: elem?.name ?? 'custom_block',
                    options: [],
                    diagnostics: []
                  },
                  {
                    path: '/fields/displayName',
                    label: { key: 'field.displayName', fallback: 'Display Name (显示名称)' },
                    control: 'text',
                    required: true,
                    readOnly: false,
                    value: elem?.displayName ?? 'Custom Block',
                    options: [],
                    diagnostics: []
                  }
                ]
              },
              {
                id: 'block.behavior',
                title: { key: 'editor.behavior', fallback: 'Physical & Mining (物理挖掘特性)' },
                fields: [
                  {
                    path: '/fields/hardness',
                    label: { key: 'field.hardness', fallback: 'Hardness (硬度)' },
                    control: 'number',
                    required: true,
                    readOnly: false,
                    value: 2.0,
                    options: [],
                    constraints: { min: 0, max: 100, step: 0.5 },
                    diagnostics: []
                  },
                  {
                    path: '/fields/material',
                    label: { key: 'field.material', fallback: 'Material (材质)' },
                    control: 'select',
                    required: true,
                    readOnly: false,
                    value: 'stone',
                    options: [
                      { value: 'wood', label: { key: 'material.wood', fallback: 'Wood (木材)' }, disabled: false },
                      { value: 'stone', label: { key: 'material.stone', fallback: 'Stone (石材)' }, disabled: false },
                      { value: 'metal', label: { key: 'material.metal', fallback: 'Metal (金属)' }, disabled: false }
                    ],
                    diagnostics: []
                  },
                  {
                    path: '/fields/flammable',
                    label: { key: 'field.flammable', fallback: 'Flammable (可燃)' },
                    control: 'toggle',
                    required: false,
                    readOnly: false,
                    value: false,
                    options: [],
                    diagnostics: []
                  },
                  {
                    path: '/loaderExtensions/neoforge/fireSpreadSpeed',
                    label: { key: 'field.fire_spread_speed', fallback: 'Fire Spread Speed (火焰蔓延速度)' },
                    help: {
                      key: 'field.loader_specific_preserved',
                      fallback: 'This NeoForge field is preserved but unavailable while Fabric is active.'
                    },
                    control: 'number',
                    required: false,
                    readOnly: true,
                    value: 5,
                    options: [],
                    constraints: { min: 0, max: 100, step: 1 },
                    diagnostics: []
                  }
                ]
              }
            ],
            capabilities: [
              {
                id: 'block.fire_spread_speed',
                availability: 'unavailable',
                reasonCode: 'ACTIVE_LOADER_UNSUPPORTED_FIELD',
                message: {
                  key: 'capability.active_loader_unsupported_field',
                  fallback: 'This NeoForge field is preserved but not editable while Fabric is active.',
                  args: { loader: 'fabric' }
                },
                affectedPaths: ['/loaderExtensions/neoforge/fireSpreadSpeed']
              }
            ]
          };
          if (elem) {
            this.state.elementEditors[elem.id] = editor;
          }
          data = editor;
        }
        break;
      }
      case 'get_task': {
        const taskId = (query.payload as { taskId?: UUID })?.taskId;
        const task = taskId ? this.state.tasks[taskId] : Object.values(this.state.tasks)[0];
        data = {
          task,
          logs: taskId ? this.state.taskLogs[taskId] || [] : [],
          diagnostics: []
        };
        break;
      }
      case 'get_history':
        data = {
          currentRevision: revision,
          recoveryPoints: this.state.recoveryPoints
        };
        break;
      case 'get_diff':
        data = this.state.historyComparison;
        break;
      case 'list_operation_approvals':
        data = { items: this.state.operationApprovals };
        break;
    }

    return {
      messageType: 'query_result',
      schemaVersion: '1.0',
      requestId: query.requestId,
      workspaceId,
      operation: query.operation,
      status: 'succeeded',
      revision,
      data: data as T,
      diagnostics: []
    };
  }

  public elevatePermission(profile: PermissionProfile) {
    if (this.state.workbench) {
      this.state.workbench.permission.profile = profile;
      // A fresh workbench projection after elevation no longer carries the
      // permission-denied diagnostics.
      this.state.diagnostics = this.state.diagnostics.filter(
        (d) => d.code !== 'PERMISSION_PROFILE_DENIED'
      );
      if (this.state.viewportState === 'permission_denied') {
        this.state.viewportState = 'ready';
      }
      this.notifyState();
    }
  }

  public reconcileRecovery() {
    this.state.recoveryState = null;
    this.state.viewportState = 'ready';
    this.notifyState();
  }

  public simulateConflict() {
    this.state.viewportState = 'conflict';
    this.notifyState();
  }
}

