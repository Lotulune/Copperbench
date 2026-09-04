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
  NewWorkspaceGeneratorCatalog,
  CreateWorkspacePayload,
  CommandResultData,
  LoaderMigrationPreview,
  UpstreamImportPreview,
  MigrationReport,
  PublishBatch,
  PublishBatchListProjection,
  PublishBatchResultPayload,
  ClientLoadPreparation,
  InstalledPluginInventory,
  ElementCoverage,
  UpstreamToolCatalogProjection,
  Diagnostic,
  ProcedureEditorProjection,
  ProcedureIr,
  ProcedureNode,
  RegistryEntry,
  WorkspaceReferenceProjection,
  WorkspaceRegistriesProjection,
  DatagenPreview,
  FieldChange
} from '../types/contract';
import {
  BridgeState,
  CoreBridge,
  HandshakeRequest,
  HandshakeResult
} from '../bridge/CoreBridge';
import { SCENARIOS } from './scenarios';
import { ASSET_FIXTURES } from './assetFixtures';
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

function mockAssetProjection() {
  return {
    schemaVersion: '1.0' as const,
    assets: ASSET_FIXTURES.map((asset) => ({
      id: asset.id,
      relativePath: asset.path,
      category: asset.category.toUpperCase() as 'MODEL' | 'TEXTURE' | 'ANIMATION' | 'LANGUAGE' | 'SOUND' | 'RESOURCE_PACK',
      size: asset.sizeBytes,
      sha256: '0000000000000000000000000000000000000000000000000000000000000000',
      mediaType: asset.format === 'PNG' ? 'image/png' : asset.format === 'OGG' ? 'audio/ogg' : 'application/octet-stream',
      updatedAt: asset.updatedAt
    })),
    references: [],
    diagnostics: []
  };
}

function initialMockRegistries(): NonNullable<WorkspaceRegistriesProjection['registries']> {
  return {
    variables: [
      {
        id: '7a4be662-5208-4cc7-8984-c08ae63a447a',
        kind: 'variable',
        name: 'player_energy',
        dataType: 'number',
        scope: 'player_persistent',
        support: { state: 'supported', reasonCode: 'REGISTRY_ENTRY_SUPPORTED' }
      }
    ],
    tags: [
      {
        id: '27489572-df19-4962-b19f-e764a355817d',
        kind: 'tag',
        name: 'forgeable_ores',
        namespace: 'copperbench',
        category: 'items',
        members: ['minecraft:iron_ore'],
        support: { state: 'supported', reasonCode: 'REGISTRY_ENTRY_SUPPORTED' }
      }
    ],
    languageKeys: [
      {
        id: '36e344a2-84c4-4d22-a41d-124c273f16f5',
        kind: 'language_key',
        key: 'item.copperbench.ruby',
        translations: { 'zh_cn': '红宝石', 'en_us': 'Ruby' },
        support: { state: 'supported', reasonCode: 'REGISTRY_ENTRY_SUPPORTED' }
      }
    ]
  };
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
  private publishedDatagenTasks = new Set<UUID>();
  private timelineTimers: ReturnType<typeof setTimeout>[] = [];
  private sequenceCounter = 100;
  private procedureIrs = new Map<UUID, ProcedureIr>();
  private mockRegistries = initialMockRegistries();

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
    this.procedureIrs.clear();
    this.mockRegistries = initialMockRegistries();

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
      case 'procedure_updated': {
        const elem = ev.payload.element;
        this.state.elements = this.state.elements.map((e) =>
          e.id === elem.id ? elem : e
        );
        this.updateWorkbenchCounts();
        break;
      }
      case 'registry_updated':
        break;
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

  private getMockProcedure(elementId: UUID): ProcedureIr {
    const existing = this.procedureIrs.get(elementId);
    if (existing) return existing;
    const created: ProcedureIr = {
      schemaVersion: '1.0',
      trigger: 'no_ext_trigger',
      nodes: [
        {
          id: generateUUID(),
          type: 'event_trigger',
          kind: 'statement',
          x: 48,
          y: 48,
          fields: { trigger: 'no_ext_trigger' },
          inputs: {},
          next: null,
          unknown: false
        }
      ],
      dependencies: []
    };
    this.procedureIrs.set(elementId, created);
    return created;
  }

  private applyMockProcedureEdits(elementId: UUID, edits: Array<Record<string, unknown>>): ProcedureIr {
    const current = this.getMockProcedure(elementId);
    let trigger = current.trigger;
    let nodes = current.nodes.map((node) => ({
      ...node,
      fields: { ...node.fields },
      inputs: { ...node.inputs }
    }));
    for (const edit of edits) {
      const operation = String(edit.operation ?? '');
      if (operation === 'add_node' && edit.node) {
        nodes.push(edit.node as ProcedureNode);
      } else if (operation === 'update_node') {
        nodes = nodes.map((node) => node.id === edit.nodeId ? {
          ...node,
          fields: edit.fields ? { ...node.fields, ...(edit.fields as ProcedureNode['fields']) } : node.fields,
          x: typeof edit.x === 'number' ? edit.x : node.x,
          y: typeof edit.y === 'number' ? edit.y : node.y
        } : node);
      } else if (operation === 'move_node') {
        nodes = nodes.map((node) => node.id === edit.nodeId ? {
          ...node,
          x: Number(edit.x),
          y: Number(edit.y)
        } : node);
      } else if (operation === 'delete_node') {
        nodes = nodes
          .filter((node) => node.id !== edit.nodeId)
          .map((node) => ({
            ...node,
            inputs: Object.fromEntries(Object.entries(node.inputs).filter(([, id]) => id !== edit.nodeId)),
            next: node.next === edit.nodeId ? null : node.next
          }));
      } else if (operation === 'connect') {
        nodes = nodes.map((node) => {
          if (node.id !== edit.sourceNodeId) return node;
          const port = String(edit.port);
          if (port === 'next') return { ...node, next: String(edit.targetNodeId) };
          return { ...node, inputs: { ...node.inputs, [port]: String(edit.targetNodeId) } };
        });
      } else if (operation === 'disconnect') {
        nodes = nodes.map((node) => {
          if (node.id !== edit.sourceNodeId) return node;
          const port = String(edit.port);
          if (port === 'next') return { ...node, next: null };
          const inputs = { ...node.inputs };
          delete inputs[port];
          return { ...node, inputs };
        });
      } else if (operation === 'set_trigger') {
        trigger = String(edit.trigger);
      }
    }
    const result: ProcedureIr = { ...current, trigger, nodes };
    this.procedureIrs.set(elementId, result);
    return result;
  }

  private mockReferences(target = ''): WorkspaceReferenceProjection {
    const nodes = [
      ...this.state.elements.map((element) => ({
        id: element.id,
        kind: 'element',
        type: element.type,
        name: element.name,
        displayName: element.displayName
      })),
      ...Object.entries(this.mockRegistries ?? {}).flatMap(([type, entries]) =>
        entries.map((entry) => ({
          id: entry.id,
          kind: 'registry',
          type,
          name: entry.key ?? entry.name ?? '',
          displayName: entry.key ?? entry.name ?? ''
        })))
    ];
    const edges: Array<Record<string, unknown>> = [];
    return {
      revision: this.state.workbench?.workspace.revision ?? 42,
      nodes,
      edges: target ? edges.filter((edge) => edge.target === target || edge.targetId === target) : edges,
      diagnostics: [],
      stats: { indexedElements: this.state.elements.length, edgeCount: edges.length, incremental: true }
    };
  }

  private mockProcedureProjection(elementId: UUID): ProcedureEditorProjection | null {
    const element = this.state.elements.find((candidate) => candidate.id === elementId);
    if (!element) return null;
    const ir = this.getMockProcedure(elementId);
    return {
      element,
      baseRevision: this.state.workbench?.workspace.revision ?? 42,
      readOnly: this.state.workbench?.permission.profile === 'read_only',
      ir,
      nodeCatalog: [
        ['controls_if', 'control', '条件', 'statement'],
        ['controls_repeat_ext', 'control', '重复循环', 'statement'],
        ['controls_while', 'control', '条件循环', 'statement'],
        ['math_number', 'value', '数值', 'number'],
        ['math_binary_ops', 'value', '数值运算', 'number'],
        ['text', 'value', '文本', 'string'],
        ['logic_boolean', 'value', '布尔值', 'logic'],
        ['logic_binary_ops', 'value', '布尔运算', 'logic'],
        ['variables_get_number', 'variable', '读取变量', 'number'],
        ['variables_set_number', 'variable', '设置变量', 'statement'],
        ['entity_from_deps', 'context', '上下文实体', 'entity'],
        ['coord_x', 'context', '上下文 X', 'number'],
        ['coord_y', 'context', '上下文 Y', 'number'],
        ['coord_z', 'context', '上下文 Z', 'number'],
        ['mcitem_all', 'context', '物品引用', 'itemstack'],
        ['call_procedure', 'procedure', '调用 Procedure', 'statement'],
        ['return_number', 'procedure', '返回数值', 'statement']
      ].map(([type, category, label, output]) => ({
        type,
        category,
        label: { key: `procedure.node.${type}`, fallback: label },
        output,
        availability: 'available' as const,
        reasonCode: null
      })),
      sourcePreview: `// Read-only Procedure IR preview\ntrigger ${ir.trigger}\n${ir.nodes.map((node) => `${node.type} ${node.id}`).join('\n')}`,
      sourceOwnership: 'generated',
      references: this.mockReferences(elementId)
    };
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
        const hardnessChange = payload.changes.find((c) => c.path === '/hardness' || c.path === '/fields/hardness');
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
                path: hardnessChange.path,
                elementId: payload.elementId,
                recoverable: true,
                actions: [
                  {
                    id: 'open_invalid_field',
                    label: { key: 'action.open_field', fallback: 'Locate Invalid Field' },
                    kind: 'open_field',
                    target: hardnessChange.path
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

        if (this.state.elementEditors[payload.elementId]) {
          const editor = this.state.elementEditors[payload.elementId];
          const allFields = editor.sections.flatMap((s) => s.fields);
          for (const change of payload.changes || []) {
            const field = allFields.find(
              (f) =>
                f.path === change.path ||
                f.path === `/fields${change.path}` ||
                change.path === `/fields${f.path}` ||
                f.path.replace('/fields', '') === change.path ||
                (f.path === '/title' && (change.path === '/fields/achievementName' || change.path === '/fields/title')) ||
                (f.path === '/description' && (change.path === '/fields/achievementDescription' || change.path === '/fields/description')) ||
                (f.path === '/icon' && (change.path === '/fields/achievementIcon' || change.path === '/fields/icon')) ||
                (f.path === '/frame' && (change.path === '/fields/achievementType' || change.path === '/fields/frame'))
            );
            if (field) {
              field.value = change.value;
            }
          }
        }

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

      case 'update_procedure': {
        const payload = command.payload as unknown as {
          elementId: UUID;
          edits: Array<Record<string, unknown>>;
        };
        this.applyMockProcedureEdits(payload.elementId, payload.edits);
        const current = this.state.elements.find((element) => element.id === payload.elementId);
        if (!current) throw new Error(`Procedure element not found: ${payload.elementId}`);
        const updated: ModElementSummary = {
          ...current,
          state: 'valid',
          updatedAt: new Date().toISOString()
        };
        this.state.elements = this.state.elements.map((element) =>
          element.id === updated.id ? updated : element
        );
        if (this.state.workbench) this.state.workbench.workspace.revision = newRevision;
        this.updateWorkbenchCounts();
        const result: CommandResult = {
          messageType: 'command_result', schemaVersion: '1.0', requestId: command.requestId,
          workspaceId, operation: 'update_procedure', status: 'committed', newRevision,
          recoveryPointId: `rec-${generateUUID().slice(0, 8)}`, task: null,
          data: { element: updated }, conflict: null, denial: null, diagnostics: []
        };
        const event: CoreEvent = {
          messageType: 'event', schemaVersion: '1.0', eventId: generateUUID(), workspaceId,
          revision: newRevision, sequence: ++this.sequenceCounter, occurredAt: new Date().toISOString(),
          event: 'procedure_updated', causedByRequestId: command.requestId, payload: { element: updated }
        };
        this.notifyEvent(event);
        this.notifyState();
        return result;
      }

      case 'create_registry_entry':
      case 'update_registry_entry':
      case 'rename_registry_entry':
      case 'delete_registry_entry': {
        const payload = command.payload as unknown as {
          registry?: keyof NonNullable<WorkspaceRegistriesProjection['registries']>;
          entry?: Partial<RegistryEntry>;
          entryId?: UUID;
          newName?: string;
          changes?: FieldChange[];
        };
        let data: CommandResult['data'] = null;
        if (command.operation === 'create_registry_entry') {
          const registry = payload.registry;
          if (!registry || !payload.entry) throw new Error('Registry and entry are required.');
          const kind = registry === 'variables' ? 'variable' : registry === 'tags' ? 'tag' : 'language_key';
          const entry = {
            ...payload.entry,
            id: generateUUID(),
            kind,
            support: { state: 'supported', reasonCode: 'REGISTRY_ENTRY_SUPPORTED' }
          } as RegistryEntry;
          this.mockRegistries[registry].push(entry);
          data = { entry };
        } else {
          const location = Object.entries(this.mockRegistries).find(([, entries]) =>
            entries.some((entry) => entry.id === payload.entryId)
          );
          if (!location) throw new Error(`Registry entry not found: ${payload.entryId}`);
          const [registry, entries] = location as [keyof typeof this.mockRegistries, RegistryEntry[]];
          const entry = entries.find((candidate) => candidate.id === payload.entryId)!;
          if (command.operation === 'update_registry_entry') {
            for (const change of payload.changes ?? []) {
              if (change.path === '/translations' && change.value && typeof change.value === 'object') {
                entry.translations = { ...(change.value as Record<string, string>) };
              }
            }
            data = { entry };
          } else if (command.operation === 'rename_registry_entry') {
            const oldName = entry.key ?? entry.name ?? '';
            if (registry === 'languageKeys') entry.key = payload.newName ?? entry.key;
            else entry.name = payload.newName ?? entry.name;
            data = { entry, oldName, changedElementIds: [] };
          } else {
            this.mockRegistries[registry] = entries.filter((candidate) => candidate.id !== payload.entryId);
            data = { entryId: payload.entryId, references: this.mockReferences(payload.entryId) };
          }
        }
        if (this.state.workbench) this.state.workbench.workspace.revision = newRevision;
        const result: CommandResult = {
          messageType: 'command_result', schemaVersion: '1.0', requestId: command.requestId,
          workspaceId, operation: command.operation, status: 'committed', newRevision,
          recoveryPointId: command.operation === 'create_registry_entry' ? null : `rec-${generateUUID().slice(0, 8)}`,
          task: null, data, conflict: null, denial: null, diagnostics: []
        };
        const event: CoreEvent = {
          messageType: 'event', schemaVersion: '1.0', eventId: generateUUID(), workspaceId,
          revision: newRevision, sequence: ++this.sequenceCounter, occurredAt: new Date().toISOString(),
          event: 'registry_updated', causedByRequestId: command.requestId,
          payload: (data ?? {}) as Record<string, unknown>
        };
        this.notifyEvent(event);
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
	  case 'validate_workspace':
	  case 'run_client':
	  case 'run_server':
	  case 'run_datagen':
      case 'run_gametest': {
		const taskId = generateUUID();
		const kind: TaskSummary['kind'] = command.operation === 'build_workspace' ? 'build'
          : command.operation === 'generate_workspace' ? 'generate'
          : command.operation === 'export_workspace' ? 'export'
          : command.operation === 'run_client' ? 'run_client'
          : command.operation === 'run_server' ? 'run_server'
          : command.operation === 'run_datagen' ? 'run_datagen'
          : command.operation === 'run_gametest' ? 'run_gametest'
          : 'validate';
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

      case 'publish_datagen_output': {
        const payload = command.payload as unknown as { taskId: UUID; manifestHash: string };
        const task = this.state.tasks[payload.taskId];
        const manifestHash = 'a'.repeat(64);
        if (!task || task.kind !== 'run_datagen' || task.state !== 'succeeded'
          || payload.manifestHash !== manifestHash || this.publishedDatagenTasks.has(payload.taskId)) {
          return {
            messageType: 'command_result', schemaVersion: '1.0', requestId: command.requestId, workspaceId,
            operation: 'publish_datagen_output', status: 'rejected', newRevision: currentRevision,
            recoveryPointId: null, task: null, data: null, conflict: null, denial: null,
            diagnostics: [{
              code: 'DATAGEN_PUBLISH_FAILED', severity: 'error',
              message: { key: 'diagnostic.datagen_publish_failed', fallback: 'Staged datagen output could not be published.' },
              path: '/taskId', recoverable: true, actions: []
            }]
          };
        }
        this.publishedDatagenTasks.add(payload.taskId);
        if (this.state.workbench) this.state.workbench.workspace.revision = newRevision;
        const preview: DatagenPreview = {
          taskId: payload.taskId,
          sourceRevision: currentRevision,
          currentRevision: newRevision,
          manifestHash,
          files: [{
            path: 'src/generated/resources/data/copper_trails/loot_tables/blocks/copper_marker.json',
            status: 'add', size: 284, sha256: 'b'.repeat(64)
          }],
          changeCount: 1,
          stale: false,
          published: true,
          canPublish: false,
          changedPaths: ['src/generated/resources/data/copper_trails/loot_tables/blocks/copper_marker.json']
        };
        const result: CommandResult = {
          messageType: 'command_result', schemaVersion: '1.0', requestId: command.requestId, workspaceId,
          operation: 'publish_datagen_output', status: 'committed', newRevision,
          recoveryPointId: `rec-${generateUUID().slice(0, 8)}`, task: null, data: preview,
          conflict: null, denial: null, diagnostics: []
        };
        this.notifyEvent({
          messageType: 'event', schemaVersion: '1.0', eventId: generateUUID(), workspaceId,
          revision: newRevision, sequence: ++this.sequenceCounter, occurredAt: new Date().toISOString(),
          event: 'datagen_published', causedByRequestId: command.requestId, payload: preview
        });
        this.notifyState();
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

      case 'create_workspace': {
        const payload = command.payload as unknown as CreateWorkspacePayload;
        const diagnostics: Diagnostic[] = [];
        if (!payload.userApproved) {
          diagnostics.push({
            code: 'USER_APPROVAL_REQUIRED',
            severity: 'error',
            message: {
              key: 'diagnostic.user_approval_required',
              fallback: '创建工作区会写入新文件夹，需要用户确认。'
            },
            path: null,
            recoverable: true,
            actions: []
          });
        } else {
          const MOD_ID = /^[a-z][a-z0-9_]{1,31}$/;
          const MOD_NAME = /^\S.{0,63}$/;
          const PACKAGE_NAME = /^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$/;
          if (!MOD_NAME.test(payload.modName)) {
            diagnostics.push({
              code: 'MOD_NAME_INVALID',
              severity: 'error',
              message: { key: 'diagnostic.mod_name_invalid', fallback: '模组名称无效。' },
              path: '/modName',
              recoverable: true,
              actions: []
            });
          }
          if (!MOD_ID.test(payload.modId)) {
            diagnostics.push({
              code: 'MOD_ID_INVALID',
              severity: 'error',
              message: { key: 'diagnostic.mod_id_invalid', fallback: '模组 ID 必须为 2-32 位小写字母、数字或下划线。' },
              path: '/modId',
              recoverable: true,
              actions: []
            });
          }
          const packageName = payload.packageName || `net.mcreator.${payload.modId.replaceAll(/[^a-z0-9_]/g, '')}`;
          if (!PACKAGE_NAME.test(packageName)) {
            diagnostics.push({
              code: 'PACKAGE_NAME_INVALID',
              severity: 'error',
              message: { key: 'diagnostic.package_name_invalid', fallback: 'Java 包名无效。' },
              path: '/packageName',
              recoverable: true,
              actions: []
            });
          }
          if (!payload.workspaceFolderPath || payload.workspaceFolderPath.trim().length === 0) {
            diagnostics.push({
              code: 'WORKSPACE_FOLDER_REQUIRED',
              severity: 'error',
              message: { key: 'diagnostic.workspace_folder_required', fallback: '必须提供工作区文件夹路径。' },
              path: '/workspaceFolderPath',
              recoverable: true,
              actions: []
            });
          }
        }
        if (diagnostics.length > 0) {
          const result: CommandResult = {
            messageType: 'command_result',
            schemaVersion: '1.0',
            requestId: command.requestId,
            workspaceId,
            operation: 'create_workspace',
            status: 'rejected',
            newRevision: currentRevision,
            recoveryPointId: null,
            task: null,
            data: null,
            conflict: null,
            denial: null,
            diagnostics
          };
          this.state.diagnostics = [...diagnostics];
          this.notifyState();
          return result;
        }

        const workspaceFile = `${payload.workspaceFolderPath.replace(/[/\\]+$/, '')}/${payload.modId}.mcreator`
          .replace(/\\/g, '/');
        const created: CommandResultData = {
          workspaceFile,
          generatorId: payload.generatorId,
          modId: payload.modId
        };
        const ev: CoreEvent = {
          messageType: 'event',
          schemaVersion: '1.0',
          eventId: generateUUID(),
          workspaceId,
          revision: currentRevision,
          sequence: ++this.sequenceCounter,
          occurredAt: new Date().toISOString(),
          event: 'workspace_created',
          causedByRequestId: command.requestId,
          payload: { workspaceFile, generatorId: payload.generatorId, modId: payload.modId }
        };
        this.notifyEvent(ev);
        const result: CommandResult = {
          messageType: 'command_result',
          schemaVersion: '1.0',
          requestId: command.requestId,
          workspaceId,
          operation: 'create_workspace',
          status: 'committed',
          newRevision: currentRevision,
          recoveryPointId: null,
          task: null,
          data: created,
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
              nextStep: 'Common Java element fields copy without conversion.'
            },
            {
              path: '/elements/ruby_gem',
              name: 'ruby_gem',
              type: 'item',
              disposition: 'supported',
              reasonCode: 'COMMON_FIELDS_COPIED',
              nextStep: 'Common Java element fields copy without conversion.'
            },
            {
              path: '/elements/ruby_block_recipe',
              name: 'ruby_block_recipe',
              type: 'recipe',
              disposition: 'supported',
              reasonCode: 'COMMON_FIELDS_COPIED',
              nextStep: 'Common Java element fields copy without conversion.'
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
              nextStep: 'Known Java element copied with its original definition.'
            },
            {
              path: '/elements/copper_block',
              name: 'copper_block',
              type: 'block',
              disposition: 'supported',
              reasonCode: 'ELEMENT_COPIED',
              nextStep: 'Known Java element copied with its original definition.'
            },
            {
              path: '/elements/copper_pickaxe_recipe',
              name: 'copper_pickaxe_recipe',
              type: 'recipe',
              disposition: 'supported',
              reasonCode: 'ELEMENT_COPIED',
              nextStep: 'Known Java element copied with its original definition.'
            },
            {
              path: '/elements/copper_strike_proc',
              name: 'copper_strike_proc',
              type: 'procedure',
              disposition: 'substitute',
              reasonCode: 'ELEMENT_COPIED',
              nextStep: 'Known Java element copied with its original definition.'
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
          nextCursor: null,
          availableTypes: ['block', 'item', 'recipe', 'procedure', 'function', 'loottable', 'achievement', 'armor', 'armortrim', 'tool', 'itemextension', 'attribute', 'bannerpattern', 'command', 'damagetype', 'enchantment', 'gamerule', 'keybind', 'painting', 'particle', 'potion', 'potioneffect', 'tab', 'villagerprofession', 'villagertrade', 'biome', 'dimension', 'feature', 'fluid', 'plant', 'structure', 'livingentity', 'specialentity', 'projectile', 'gui', 'overlay', 'code']
        };
        break;
      case 'get_procedure_editor': {
        const elementId = (query.payload as { elementId?: UUID })?.elementId;
        data = elementId ? this.mockProcedureProjection(elementId) : null;
        break;
      }
      case 'preview_procedure_change': {
        const payload = query.payload as { elementId?: UUID; edits?: Array<Record<string, unknown>> };
        if (!payload.elementId) {
          data = null;
          break;
        }
        const original = this.getMockProcedure(payload.elementId);
        const candidate = this.applyMockProcedureEdits(payload.elementId, payload.edits ?? []);
        this.procedureIrs.set(payload.elementId, original);
        data = {
          elementId: payload.elementId,
          baseRevision: revision,
          canSaveDraft: true,
          canGenerate: true,
          candidateIr: candidate,
          sourcePreview: `// Read-only Procedure IR preview\ntrigger ${candidate.trigger}`,
          diagnostics: [],
          changedPaths: [`/elements/${payload.elementId}/procedureIr`, `/elements/${payload.elementId}/procedurexml`]
        };
        break;
      }
      case 'get_workspace_references': {
        const target = (query.payload as { target?: string })?.target ?? '';
        data = this.mockReferences(target);
        break;
      }
      case 'list_workspace_registries': {
        const languages = new Set<string>();
        this.mockRegistries.languageKeys.forEach((entry) =>
          Object.keys(entry.translations ?? {}).forEach((language) => languages.add(language))
        );
        let missingTranslationCount = 0;
        this.mockRegistries.languageKeys.forEach((entry) =>
          languages.forEach((language) => {
            if (!String(entry.translations?.[language] ?? '').trim()) missingTranslationCount++;
          })
        );
        data = {
          registries: {
            variables: [...this.mockRegistries.variables],
            tags: [...this.mockRegistries.tags],
            languageKeys: [...this.mockRegistries.languageKeys]
          },
          variables: [...this.mockRegistries.variables],
          tags: [...this.mockRegistries.tags],
          languageKeys: [...this.mockRegistries.languageKeys],
          languageStats: {
            keyCount: this.mockRegistries.languageKeys.length,
            languageCount: languages.size,
            missingTranslationCount,
            duplicateKeyCount: 0
          },
          stableIds: true,
          referenceAwareRename: true
        } satisfies WorkspaceRegistriesProjection;
        break;
      }
      case 'preview_registry_rename': {
        const payload = query.payload as { entryId?: UUID; newName?: string };
        const location = Object.entries(this.mockRegistries).find(([, entries]) =>
          entries.some((entry) => entry.id === payload.entryId)
        );
        const entry = location?.[1].find((candidate) => candidate.id === payload.entryId);
        data = entry ? {
          entryId: entry.id,
          registry: location?.[0],
          oldName: entry.key ?? entry.name,
          newName: payload.newName,
          references: this.mockReferences(entry.id),
          impactedElementCount: 0,
          canApply: true
        } : null;
        break;
      }
      case 'list_assets':
        data = mockAssetProjection();
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
      case 'list_new_workspace_generators':
        data = {
          schemaVersion: '1.0',
          generators: [
            { generatorId: 'fabric-26.2', loader: 'fabric', minecraftVersion: '26.2', trackId: 'latest_stable', displayName: 'Fabric 26.2', dynamic: true, available: true, workspaceGeneratorName: 'fabric-26.2' },
            { generatorId: 'neoforge-26.2', loader: 'neoforge', minecraftVersion: '26.2', trackId: 'latest_stable', displayName: 'NeoForge 26.2', dynamic: true, available: true, workspaceGeneratorName: 'neoforge-26.2' },
            { generatorId: 'fabric-26.1.2', loader: 'fabric', minecraftVersion: '26.1.2', trackId: 'previous_stable', displayName: 'Fabric 26.1.2', dynamic: true, available: true, workspaceGeneratorName: 'fabric-26.1.2' },
            { generatorId: 'neoforge-26.1.2', loader: 'neoforge', minecraftVersion: '26.1.2', trackId: 'previous_stable', displayName: 'NeoForge 26.1.2', dynamic: true, available: true, workspaceGeneratorName: 'neoforge-26.1.2' },
            { generatorId: 'fabric-1.21.1', loader: 'fabric', minecraftVersion: '1.21.1', trackId: 'minecraft_1_21_1', displayName: 'Fabric 1.21.1', dynamic: false, available: true, workspaceGeneratorName: 'fabric-1.21.1' },
            { generatorId: 'neoforge-1.21.1', loader: 'neoforge', minecraftVersion: '1.21.1', trackId: 'minecraft_1_21_1', displayName: 'NeoForge 1.21.1', dynamic: false, available: true, workspaceGeneratorName: 'neoforge-1.21.1' },
            { generatorId: 'fabric-1.20.1', loader: 'fabric', minecraftVersion: '1.20.1', trackId: 'minecraft_1_20_1', displayName: 'Fabric 1.20.1', dynamic: false, available: true, workspaceGeneratorName: 'fabric-1.20.1' },
            { generatorId: 'neoforge-1.20.1', loader: 'neoforge', minecraftVersion: '1.20.1', trackId: 'minecraft_1_20_1', displayName: 'NeoForge 1.20.1', dynamic: false, available: true, workspaceGeneratorName: 'neoforge-1.20.1' },
            { generatorId: 'resourcepack-1.21.1', loader: 'resource_pack', minecraftVersion: '1.21.1', trackId: 'resource_pack', displayName: 'Resource Pack 1.21.1', dynamic: false, available: true, workspaceGeneratorName: 'resourcepack-1.21.1' }
          ],
          suggestedWorkspaceFoldersRoot: 'C:\\Users\\example\\MCreatorWorkspaces'
        } satisfies NewWorkspaceGeneratorCatalog;
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
              nextStep: 'Common Java element fields copy without conversion.'
              },
              {
                path: '/elements/ruby_gem',
                name: 'ruby_gem',
                type: 'item',
                disposition: 'supported',
                reasonCode: 'COMMON_FIELDS_COPIED',
              nextStep: 'Common Java element fields copy without conversion.'
              },
              {
                path: '/elements/ruby_block_recipe',
                name: 'ruby_block_recipe',
                type: 'recipe',
                disposition: 'supported',
                reasonCode: 'COMMON_FIELDS_COPIED',
              nextStep: 'Common Java element fields copy without conversion.'
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
              nextStep: 'Common Java element fields copy without conversion.'
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
              nextStep: 'Known Java element copied with its original definition.'
            },
            {
              path: '/elements/copper_block',
              name: 'copper_block',
              type: 'block',
              disposition: 'supported',
              reasonCode: 'ELEMENT_COPIED',
              nextStep: 'Known Java element copied with its original definition.'
            },
            {
              path: '/elements/copper_pickaxe_recipe',
              name: 'copper_pickaxe_recipe',
              type: 'recipe',
              disposition: 'supported',
              reasonCode: 'ELEMENT_COPIED',
              nextStep: 'Known Java element copied with its original definition.'
            },
            {
              path: '/elements/copper_strike_proc',
              name: 'copper_strike_proc',
              type: 'procedure',
              disposition: 'substitute',
              reasonCode: 'ELEMENT_COPIED',
              nextStep: 'Known Java element copied with its original definition.'
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
          let editor: ModElementEditorProjection;

          if (elem?.type === 'function') {
            editor = {
              element: elem,
              sections: [
                {
                  id: 'function.general',
                  title: { key: 'editor.general', fallback: 'Function Attributes' },
                  fields: [
                    {
                      path: '/name',
                      label: { key: 'field.name', fallback: 'Identifier Name' },
                      control: 'text',
                      required: true,
                      readOnly: false,
                      value: elem.name,
                      options: [],
                      diagnostics: []
                    },
                    {
                      path: '/displayName',
                      label: { key: 'field.displayName', fallback: 'Display Name' },
                      control: 'text',
                      required: true,
                      readOnly: false,
                      value: elem.displayName,
                      options: [],
                      diagnostics: []
                    },
                    {
                      path: '/namespace',
                      label: { key: 'field.name', fallback: 'Namespace' },
                      control: 'text',
                      required: true,
                      readOnly: false,
                      value: 'copperbench',
                      options: [],
                      diagnostics: []
                    },
                    {
                      path: '/code',
                      label: { key: 'field.name', fallback: 'Commands' },
                      control: 'textarea',
                      required: false,
                      readOnly: false,
                      value: '# 函数: 初始化事件\ntellraw @a {"text":"[Copperbench] 函数已触发","color":"aqua"}\nparticle minecraft:totem_of_undying ~ ~1 ~ 0.5 0.5 0.5 0.2 30\n',
                      options: [],
                      diagnostics: []
                    },
                    {
                      path: '/tags',
                      label: { key: 'field.name', fallback: 'Tags' },
                      control: 'text',
                      required: false,
                      readOnly: false,
                      value: ['minecraft:load'],
                      options: [],
                      diagnostics: []
                    }
                  ]
                }
              ],
              capabilities: []
            };
          } else if (elem?.type === 'loottable') {
            editor = {
              element: elem,
              sections: [
                {
                  id: 'loottable.general',
                  title: { key: 'editor.general', fallback: 'Loot Table Attributes' },
                  fields: [
                    {
                      path: '/name',
                      label: { key: 'field.name', fallback: 'Identifier Name' },
                      control: 'text',
                      required: true,
                      readOnly: false,
                      value: elem.name,
                      options: [],
                      diagnostics: []
                    },
                    {
                      path: '/displayName',
                      label: { key: 'field.displayName', fallback: 'Display Name' },
                      control: 'text',
                      required: true,
                      readOnly: false,
                      value: elem.displayName,
                      options: [],
                      diagnostics: []
                    },
                    {
                      path: '/type',
                      label: { key: 'field.material', fallback: 'Type' },
                      control: 'select',
                      required: true,
                      readOnly: false,
                      value: 'Block',
                      options: [
                        { value: 'Block', label: { key: 'field.option', fallback: 'Block' }, disabled: false },
                        { value: 'Entity', label: { key: 'field.option', fallback: 'Entity' }, disabled: false },
                        { value: 'Chest', label: { key: 'field.option', fallback: 'Chest' }, disabled: false },
                        { value: 'Generic', label: { key: 'field.option', fallback: 'Generic' }, disabled: false }
                      ],
                      diagnostics: []
                    },
                    {
                      path: '/pools',
                      label: { key: 'field.name', fallback: 'Pools' },
                      control: 'json',
                      required: false,
                      readOnly: false,
                      value: [
                        {
                          id: 'pool_1',
                          name: '主掉落池',
                          minrolls: 1,
                          minRolls: 1,
                          maxrolls: 1,
                          maxRolls: 1,
                          hasbonusrolls: false,
                          hasBonusRolls: false,
                          minbonusrolls: 0,
                          minBonusRolls: 0,
                          maxbonusrolls: 0,
                          maxBonusRolls: 0,
                          conditions: [{ type: 'minecraft:survives_explosion' }],
                          entries: [
                            {
                              id: 'entry_1',
                              type: 'item',
                              item: 'minecraft:copper_ingot',
                              weight: 1,
                              minCount: 1,
                              maxCount: 2,
                              minEnchantmentLevel: 0,
                              maxEnchantmentLevel: 0,
                              affectedByFortune: true,
                              explosionDecay: true,
                              silkTouchMode: 0
                            }
                          ]
                        }
                      ],
                      options: [],
                      diagnostics: []
                    }
                  ]
                }
              ],
              capabilities: []
            };
          } else if (elem?.type === 'achievement') {
            editor = {
              element: elem,
              sections: [
                {
                  id: 'achievement.general',
                  title: { key: 'editor.general', fallback: 'Advancement Settings' },
                  fields: [
                    {
                      path: '/name',
                      label: { key: 'field.name', fallback: 'Identifier Name' },
                      control: 'text',
                      required: true,
                      readOnly: false,
                      value: elem.name,
                      options: [],
                      diagnostics: []
                    },
                    {
                      path: '/displayName',
                      label: { key: 'field.displayName', fallback: 'Display Name' },
                      control: 'text',
                      required: true,
                      readOnly: false,
                      value: elem.displayName,
                      options: [],
                      diagnostics: []
                    },
                    {
                      path: '/title',
                      label: { key: 'field.displayName', fallback: 'Title' },
                      control: 'text',
                      required: true,
                      readOnly: false,
                      value: elem.displayName,
                      options: [],
                      diagnostics: []
                    },
                    {
                      path: '/description',
                      label: { key: 'field.displayName', fallback: 'Description' },
                      control: 'textarea',
                      required: false,
                      readOnly: false,
                      value: '探索未知领域并制作你的第一个铜制工具。',
                      options: [],
                      diagnostics: []
                    },
                    {
                      path: '/icon',
                      label: { key: 'field.displayName', fallback: 'Icon' },
                      control: 'resource_reference',
                      required: true,
                      readOnly: false,
                      value: 'minecraft:diamond',
                      options: [],
                      diagnostics: []
                    },
                    {
                      path: '/frame',
                      label: { key: 'field.material', fallback: 'Frame' },
                      control: 'select',
                      required: true,
                      readOnly: false,
                      value: 'task',
                      options: [
                        { value: 'task', label: { key: 'field.option', fallback: 'Task' }, disabled: false },
                        { value: 'goal', label: { key: 'field.option', fallback: 'Goal' }, disabled: false },
                        { value: 'challenge', label: { key: 'field.option', fallback: 'Challenge' }, disabled: false }
                      ],
                      diagnostics: []
                    },
                    {
                      path: '/parent',
                      label: { key: 'field.displayName', fallback: 'Parent' },
                      control: 'procedure_reference',
                      required: false,
                      readOnly: false,
                      value: 'root',
                      options: [],
                      diagnostics: []
                    },
                    {
                      path: '/showPopup',
                      label: { key: 'field.flammable', fallback: 'Show Popup' },
                      control: 'toggle',
                      required: false,
                      readOnly: false,
                      value: true,
                      options: [],
                      diagnostics: []
                    },
                    {
                      path: '/announceToChat',
                      label: { key: 'field.flammable', fallback: 'Announce Chat' },
                      control: 'toggle',
                      required: false,
                      readOnly: false,
                      value: true,
                      options: [],
                      diagnostics: []
                    },
                    {
                      path: '/hideIfNotCompleted',
                      label: { key: 'field.flammable', fallback: 'Hide' },
                      control: 'toggle',
                      required: false,
                      readOnly: false,
                      value: false,
                      options: [],
                      diagnostics: []
                    },
                    {
                      path: '/disableDisplay',
                      label: { key: 'field.flammable', fallback: 'Disable Display' },
                      control: 'toggle',
                      required: false,
                      readOnly: false,
                      value: false,
                      options: [],
                      diagnostics: []
                    },
                    {
                      path: '/rewardXP',
                      label: { key: 'field.hardness', fallback: 'Reward XP' },
                      control: 'number',
                      required: false,
                      readOnly: false,
                      value: 50,
                      options: [],
                      constraints: { min: 0, max: 64000, step: 10 },
                      diagnostics: []
                    },
                    {
                      path: '/rewardLoot',
                      label: { key: 'field.name', fallback: 'Reward Loot' },
                      control: 'json',
                      required: false,
                      readOnly: false,
                      value: [],
                      options: [],
                      diagnostics: []
                    },
                    {
                      path: '/rewardRecipes',
                      label: { key: 'field.name', fallback: 'Reward Recipes' },
                      control: 'json',
                      required: false,
                      readOnly: false,
                      value: [],
                      options: [],
                      diagnostics: []
                    },
                    {
                      path: '/rewardFunction',
                      label: { key: 'field.name', fallback: 'Reward Function' },
                      control: 'procedure_reference',
                      required: false,
                      readOnly: false,
                      value: '',
                      options: [],
                      diagnostics: []
                    },
                    {
                      path: '/criteria',
                      label: { key: 'field.name', fallback: 'Criteria' },
                      control: 'json',
                      required: false,
                      readOnly: false,
                      value: [
                        {
                          id: 'crit_1',
                          name: 'has_copper_item',
                          trigger: 'minecraft:inventory_changed',
                          item: 'minecraft:copper_ingot'
                        }
                      ],
                      options: [],
                      diagnostics: []
                    }
                  ]
                }
              ],
              capabilities: []
            };
          } else {
            editor = {
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
          };
          if (elem) {
            this.state.elementEditors[elem.id] = editor;
          }
          data = editor;
        }
        break;
      }
      case 'preview_mod_element_change': {
        const payload = query.payload as {
          elementId?: UUID;
          changes?: Array<{ path: string; value: unknown }>;
        };
        const changes = payload.changes ?? [];
        const elem = this.state.elements.find((candidate) => candidate.id === payload.elementId);
        const sectionForPath = (path: string): string => {
          const field = path.split('/').filter(Boolean).pop()?.toLowerCase() ?? '';
          if (elem?.type === 'livingentity') {
            if (field.includes('texture') || field.includes('sound')) return 'resources';
            if (['health', 'attackstrength', 'movementspeed', 'armorbasevalue'].includes(field)) return 'attributes';
            if (field.startsWith('on') || field.startsWith('when') || field.endsWith('condition')) return 'events';
            if (field.includes('spawn')) return 'spawning';
          }
          return elem && ['livingentity', 'biome', 'dimension', 'gui'].includes(String(elem.type)) ? 'advanced' : 'general';
        };
        const sections = [...new Set(changes.map((change) => sectionForPath(change.path)))];
        const affectedDomains = elem?.type === 'livingentity'
          ? [...new Set(sections.map((section) =>
              section === 'resources' ? 'client_resources'
                : section === 'attributes' ? 'entity_definition'
                : section === 'events' || section === 'spawning' ? 'entity_behavior'
                : 'element_source'
            ))]
          : ['element_source'];
        data = {
          elementId: payload.elementId ?? '',
          baseRevision: revision,
          canApply: true,
          changedPaths: changes.map((change) => `/elements/${payload.elementId}${change.path}`),
          candidateValues: Object.fromEntries(changes.map((change) => [change.path, change.value])),
          diagnostics: [],
          semanticSummary: {
            changedFieldCount: changes.length,
            changedFields: changes.map((change) => ({
              path: change.path,
              field: change.path.split('/').filter(Boolean).pop() ?? 'field',
              sectionId: sectionForPath(change.path)
            })),
            sections
          },
          generationImpact: {
            scope: 'element',
            requiresRegeneration: true,
            generatorId: this.state.workbench?.workspace.generator.id ?? 'fabric-1.21.1',
            loader: this.state.workbench?.workspace.generator.loader ?? 'fabric',
            minecraftVersion: this.state.workbench?.workspace.generator.minecraftVersion ?? '1.21.1',
            affectedDomains
          }
        };
        break;
      }
      case 'get_task': {
        const taskId = (query.payload as { taskId?: UUID })?.taskId;
        const afterLogSequence = (query.payload as { afterLogSequence?: number })?.afterLogSequence ?? 0;
        const task = taskId ? this.state.tasks[taskId] : Object.values(this.state.tasks)[0];
        const logs = taskId ? this.state.taskLogs[taskId] || [] : [];
        data = {
          task,
          logs: logs.filter((entry) => entry.sequence > afterLogSequence),
          diagnostics: []
        };
        break;
      }
      case 'preview_datagen_output': {
        const taskId = (query.payload as { taskId?: UUID })?.taskId ?? '';
        const task = this.state.tasks[taskId];
        const published = this.publishedDatagenTasks.has(taskId);
        data = task && task.kind === 'run_datagen' && task.state === 'succeeded' ? {
          taskId,
          sourceRevision: revision,
          currentRevision: revision,
          manifestHash: 'a'.repeat(64),
          files: [{
            path: 'src/generated/resources/data/copper_trails/loot_tables/blocks/copper_marker.json',
            status: published ? 'unchanged' : 'add',
            size: 284,
            sha256: 'b'.repeat(64)
          }],
          changeCount: published ? 0 : 1,
          stale: false,
          published,
          canPublish: !published
        } satisfies DatagenPreview : null;
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
