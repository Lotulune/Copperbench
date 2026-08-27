import type {
  Command,
  CommandResult,
  CoreEvent,
  HistoryComparison,
  HistoryProjection,
  ModElementEditorProjection,
  ModElementListProjection,
  ModElementSummary,
  OperationApprovalListProjection,
  Query,
  QueryResult,
  TaskProjection,
  WorkbenchProjection
} from '../types/contract';
import type { BridgeState, CoreBridge, HandshakeRequest, HandshakeResult } from './CoreBridge';

export interface JcefHostTransport {
  readonly workspaceId: string;
  invoke(envelopeJson: string): Promise<string>;
  onEvent(listener: (eventJson: string) => void): () => void;
}

function safeRandomUUID(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

/** Creates a client-side host transport using window.cefQuery and the global event emitter. */
export function createBrowserJcefTransport(
  workspaceId: string,
  queryPrefix = 'copperbench:bridge:'
): JcefHostTransport {
  const eventListeners = new Set<(eventJson: string) => void>();

  if (typeof window !== 'undefined') {
    const prevEmitter = window.__COPPERBENCH_EMIT_EVENT__;
    window.__COPPERBENCH_EMIT_EVENT__ = (raw: string) => {
      if (typeof prevEmitter === 'function') {
        try {
          prevEmitter(raw);
        } catch (e) {
          console.error('[Copperbench Bridge] Previous emitter error:', e);
        }
      }
      eventListeners.forEach((listener) => {
        try {
          listener(raw);
        } catch (err) {
          console.error('[Copperbench Bridge] Event listener error:', err);
        }
      });
    };
  }

  return {
    workspaceId,
    invoke(envelopeJson: string): Promise<string> {
      return new Promise<string>((resolve, reject) => {
        if (typeof window === 'undefined' || typeof window.cefQuery !== 'function') {
          reject(new Error('JCEF native query transport (window.cefQuery) is not available'));
          return;
        }

        window.cefQuery({
          request: queryPrefix + envelopeJson,
          persistent: false,
          onSuccess: (response: string) => {
            resolve(response);
          },
          onFailure: (errorCode: number, errorMessage: string) => {
            reject(new Error(`Native bridge query failed [${errorCode}]: ${errorMessage}`));
          }
        });
      });
    },
    onEvent(listener: (eventJson: string) => void): () => void {
      eventListeners.add(listener);
      return () => {
        eventListeners.delete(listener);
      };
    }
  };
}

type EventListener = (event: CoreEvent) => void;
type StateListener = (state: BridgeState) => void;

const initialState = (): BridgeState => ({
  currentScenarioId: 'native',
  viewportState: 'loading',
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
  versionTracks: null,
  publishBatches: [],
  recoveryState: null,
  schemaIncompatible: false
});

/** Production UI-Core adapter. The JCEF host only transports versioned JSON. */
export class JcefCoreBridge implements CoreBridge {
  private readonly eventListeners = new Set<EventListener>();
  private readonly stateListeners = new Set<StateListener>();
  private readonly hostUnsubscribe: () => void;
  private readonly taskPollers = new Map<string, ReturnType<typeof setTimeout>>();
  private lastEventSequence = 0;
  private projectionRefresh: Promise<void> | null = null;
  private projectionRefreshRequested = false;
  private disposed = false;
  private state = initialState();

  public constructor(private readonly host: JcefHostTransport) {
    this.hostUnsubscribe = host.onEvent((raw) => this.processEvent(this.parse<CoreEvent>(raw)));
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

  public async negotiateHandshake(request: HandshakeRequest): Promise<HandshakeResult> {
    const result = await this.invoke<HandshakeResult>(request);
    this.state.schemaIncompatible = result.status === 'incompatible';
    this.state.diagnostics = [...result.diagnostics];
    this.state.viewportState = result.status === 'compatible' ? 'ready' : 'error';
    this.notify();
    if (result.status === 'compatible') await this.refreshInitialProjection();
    return result;
  }

  public async sendCommand<T>(command: Command<T>): Promise<CommandResult> {
    const result = await this.invoke<CommandResult>(command);
    this.applyCommandResult(result);
    this.notify();
    if (result.task?.id && result.task.state === 'running') this.pollTask(result.task.id);
    if (
      result.status === 'committed' &&
      ['create_mod_element', 'update_mod_element', 'delete_mod_element', 'restore_recovery_point'].includes(
        result.operation
      )
    ) {
      await this.refreshInitialProjection().catch((error) => {
        console.warn('[Copperbench Bridge] Projection refresh after command failed:', error);
      });
    }
    return result;
  }

  public async sendQuery<T>(query: Query): Promise<QueryResult<T>> {
    const result = await this.invoke<QueryResult<T>>(query);
    this.applyQueryResult(result as QueryResult);
    this.notify();
    return result;
  }

  public async reconcileRecovery(): Promise<void> {
    this.state.recoveryState = null;
    await this.refreshInitialProjection();
    this.notify();
  }

  public dispose(): void {
	if (this.disposed) return;
	this.disposed = true;
    this.hostUnsubscribe();
    this.taskPollers.forEach((timer) => clearTimeout(timer));
    this.taskPollers.clear();
    this.eventListeners.clear();
    this.stateListeners.clear();
  }

  private refreshInitialProjection(): Promise<void> {
    if (this.disposed) return Promise.resolve();
    this.projectionRefreshRequested = true;
    if (!this.projectionRefresh) {
      this.projectionRefresh = this.drainProjectionRefreshes().finally(() => {
        this.projectionRefresh = null;
      });
    }
    return this.projectionRefresh;
  }

  private async drainProjectionRefreshes(): Promise<void> {
    while (this.projectionRefreshRequested && !this.disposed) {
      this.projectionRefreshRequested = false;
      const baselineSequence = this.lastEventSequence;
      const consistent = await this.refreshProjectionGeneration(baselineSequence);
      if (!consistent && !this.disposed) this.projectionRefreshRequested = true;
    }
  }

  private async refreshProjectionGeneration(baselineSequence: number): Promise<boolean> {
    const base = {
      messageType: 'query' as const,
      schemaVersion: '1.0' as const,
      workspaceId: this.host.workspaceId
    };
    const workbench = await this.invoke<QueryResult<WorkbenchProjection>>({
      ...base,
      requestId: safeRandomUUID(),
      operation: 'get_workbench',
      payload: {}
    });
    if (!this.isProjectionGenerationCurrent(baselineSequence)) return false;
    this.applyQueryResult(workbench);
    this.notify();
    if (workbench.status !== 'succeeded' || !workbench.data) return true;

    const projectionRevision = workbench.revision;
    if (!(await this.refreshElements(base, baselineSequence, projectionRevision))) return false;
    try {
      const history = await this.invoke<QueryResult<HistoryProjection>>({
        ...base,
        requestId: safeRandomUUID(),
        operation: 'get_history',
        payload: {}
      });
      if (!this.isProjectionGenerationCurrent(baselineSequence)) return false;
      if (history.status === 'succeeded' && history.data && history.revision !== projectionRevision) return false;
      this.applyQueryResult(history);
      this.notify();
    } catch (error) {
      console.warn('[Copperbench Bridge] History projection is unavailable:', error);
    }
    return this.isProjectionGenerationCurrent(baselineSequence);
  }

  private async refreshElements(base: {
    messageType: 'query';
    schemaVersion: '1.0';
    workspaceId: string;
  }, baselineSequence: number, projectionRevision: number): Promise<boolean> {
    const pageSize = 200;
    const items: ModElementSummary[] = [];
    let cursor: string | undefined;
    do {
      const query: Query = {
        ...base,
        requestId: safeRandomUUID(),
        operation: 'list_mod_elements',
        payload: { search: '', types: [], states: [], limit: pageSize, ...(cursor ? { cursor } : {}) }
      };
      const result = await this.invoke<QueryResult<ModElementListProjection>>(query);
      if (!this.isProjectionGenerationCurrent(baselineSequence)) return false;
      if (result.status !== 'succeeded' || !result.data) {
        this.state.diagnostics = [...result.diagnostics];
        this.notify();
        return true;
      }
      if (result.revision !== projectionRevision) return false;
      if (this.isStaleRevision(result.revision)) return true;
      items.push(...result.data.items);
      cursor = result.data.nextCursor ?? undefined;
    } while (cursor);
    if (!this.isProjectionGenerationCurrent(baselineSequence)) return false;
    this.state.elements = items;
    this.synchronizeElementProjection();
    this.notify();
    return true;
  }

  private async invoke<T>(envelope: unknown): Promise<T> {
    const request = envelope as Record<string, unknown>;
    const response = this.parse<Record<string, unknown>>(await this.host.invoke(JSON.stringify(envelope)));
    const expectedMessageType =
      request.messageType === 'handshake'
        ? 'handshake_result'
        : request.messageType === 'command'
          ? 'command_result'
          : request.messageType === 'query'
            ? 'query_result'
            : null;
    if (expectedMessageType && response.messageType !== expectedMessageType) {
      throw new Error(`JCEF bridge returned ${String(response.messageType)} for ${String(request.messageType)}`);
    }
    if (response.requestId !== request.requestId) {
      throw new Error('JCEF bridge response requestId does not match the request');
    }
    if (request.workspaceId && response.workspaceId !== request.workspaceId) {
      throw new Error('JCEF bridge response workspaceId does not match the request');
    }
    if (request.operation && response.operation !== request.operation) {
      throw new Error('JCEF bridge response operation does not match the request');
    }
    return response as T;
  }

  private parse<T>(raw: string): T {
    const value: unknown = JSON.parse(raw);
    if (!value || typeof value !== 'object') throw new Error('JCEF bridge returned a non-object envelope');
    return value as T;
  }

  private applyCommandResult(result: CommandResult): void {
    this.state.diagnostics = [...result.diagnostics];
    if (result.status === 'rejected') {
      this.state.viewportState = result.conflict ? 'conflict' : result.denial ? 'permission_denied' : 'error';
    }
    if (result.task) this.state.tasks[result.task.id] = result.task;
    if (this.state.workbench) {
      this.state.workbench.workspace.revision = Math.max(
        this.state.workbench.workspace.revision,
        result.newRevision
      );
    }
  }

  private applyQueryResult(result: QueryResult): void {
    if (result.status !== 'succeeded' || !result.data) {
      this.state.diagnostics = [...result.diagnostics];
      return;
    }
    if (this.isStaleRevision(result.revision)) return;
    switch (result.operation) {
      case 'get_workbench':
        this.state.workbench = result.data as WorkbenchProjection;
        this.state.viewportState = 'ready';
        break;
      case 'list_mod_elements':
        this.state.elements = [...(result.data as ModElementListProjection).items];
        break;
      case 'get_mod_element_editor': {
        const editor = result.data as ModElementEditorProjection;
        this.state.elementEditors[editor.element.id] = editor;
        break;
      }
      case 'get_task': {
        const projection = result.data as TaskProjection;
        this.state.tasks[projection.task.id] = projection.task;
        const existing = this.state.taskLogs[projection.task.id] ?? [];
        const bySequence = new Map(existing.map((entry) => [entry.sequence, entry]));
        projection.logs.forEach((entry) => bySequence.set(entry.sequence, entry));
        this.state.taskLogs[projection.task.id] = [...bySequence.values()].sort((left, right) => left.sequence - right.sequence);
        break;
      }
      case 'get_history': {
        const history = result.data as HistoryProjection;
        this.state.recoveryPoints = [...history.recoveryPoints];
        this.state.currentRecoveryPointId = history.recoveryPoints[0]?.id ?? null;
        break;
      }
      case 'get_diff':
        this.state.historyComparison = result.data as HistoryComparison;
        break;
      case 'list_operation_approvals':
        this.state.operationApprovals = [...(result.data as OperationApprovalListProjection).items];
        break;
    }
  }

  private processEvent(event: CoreEvent): void {
    if (
      event.messageType !== 'event' ||
      event.workspaceId !== this.host.workspaceId ||
      !Number.isSafeInteger(event.sequence) ||
      event.sequence <= this.lastEventSequence
    ) {
      return;
    }
    const hasSequenceGap = event.sequence > this.lastEventSequence + 1;
    this.lastEventSequence = event.sequence;

    switch (event.event) {
      case 'mod_element_created':
      case 'mod_element_updated':
      case 'procedure_updated':
        this.upsertElement(event.payload.element);
        break;
      case 'registry_updated':
		case 'datagen_published':
        break;
      case 'mod_element_deleted':
        this.state.elements = this.state.elements.filter((element) => element.id !== event.payload.elementId);
        delete this.state.elementEditors[event.payload.elementId];
        this.synchronizeElementProjection();
        break;
      case 'task_started':
      case 'task_progressed':
      case 'task_completed':
        this.state.tasks[event.payload.task.id] = event.payload.task;
        this.synchronizeActiveTask(event.payload.task);
        break;
      case 'task_log_appended': {
        const current = this.state.taskLogs[event.payload.taskId] ?? [];
        const bySequence = new Map(current.map((entry) => [entry.sequence, entry]));
        event.payload.entries.forEach((entry) => bySequence.set(entry.sequence, entry));
        this.state.taskLogs[event.payload.taskId] = [...bySequence.values()].sort(
          (left, right) => left.sequence - right.sequence
        );
        break;
      }
      case 'diagnostics_changed':
        this.state.diagnostics = [...event.payload.diagnostics];
        break;
      case 'connectivity_changed':
        if (this.state.workbench) this.state.workbench.connection = event.payload;
        break;
      case 'capabilities_changed':
        if (this.state.workbench) this.state.workbench.capabilities = [...event.payload.capabilities];
        break;
      case 'recovery_point_created':
        this.state.recoveryPoints = [
          event.payload.recoveryPoint,
          ...this.state.recoveryPoints.filter((point) => point.id !== event.payload.recoveryPoint.id)
        ];
        this.state.currentRecoveryPointId = event.payload.recoveryPoint.id;
        break;
      case 'bridge_recovery_required':
        this.state.viewportState = 'recovery';
        this.state.recoveryState = event.payload;
        break;
      case 'workspace_restored':
      case 'workspace_created':
      case 'loader_migration_executed':
      case 'upstream_workspace_imported':
        void this.refreshInitialProjection().catch((error) => {
          console.warn('[Copperbench Bridge] Projection refresh after event failed:', error);
        });
        break;
    }
    if (this.state.workbench) {
      this.state.workbench.workspace.revision = Math.max(
        this.state.workbench.workspace.revision,
        event.revision
      );
    }
    this.eventListeners.forEach((listener) => listener(event));
    this.notify();
    if (hasSequenceGap) {
      // Apply the newest known event first, then reconcile the missing interval.
      // Refresh responses are sequence-generation guarded, so a delayed snapshot
      // cannot overwrite an event that arrived after the refresh began.
      void this.refreshInitialProjection().catch((error) => {
        console.warn('[Copperbench Bridge] Event gap reconciliation failed:', error);
      });
    }
  }

  private upsertElement(element: ModElementSummary): void {
    const index = this.state.elements.findIndex((candidate) => candidate.id === element.id);
    if (index < 0) {
      this.state.elements = [element, ...this.state.elements];
    } else {
      this.state.elements = this.state.elements.map((candidate) =>
        candidate.id === element.id ? element : candidate
      );
    }
    this.synchronizeElementProjection();
  }

  private synchronizeElementProjection(): void {
    if (!this.state.workbench) return;
    const elements = this.state.elements;
    this.state.workbench.elementCounts = {
      total: elements.length,
      valid: elements.filter((element) => element.state === 'valid').length,
      invalid: elements.filter((element) => element.state === 'invalid').length,
      draft: elements.filter((element) => element.state === 'draft').length,
      unsupported: elements.filter((element) => element.state === 'unsupported').length
    };
    this.state.workbench.recentElements = [...elements]
      .sort((left, right) => right.updatedAt.localeCompare(left.updatedAt))
      .slice(0, 5);
  }

  private synchronizeActiveTask(task: WorkbenchProjection['activeTasks'][number]): void {
    if (!this.state.workbench) return;
    const activeTasks = this.state.workbench.activeTasks.filter((candidate) => candidate.id !== task.id);
    if (task.state === 'queued' || task.state === 'running') activeTasks.unshift(task);
    this.state.workbench.activeTasks = activeTasks;
  }

  private isStaleRevision(revision: number): boolean {
    return Boolean(this.state.workbench && revision < this.state.workbench.workspace.revision);
  }

  private isProjectionGenerationCurrent(baselineSequence: number): boolean {
    return !this.disposed && this.lastEventSequence === baselineSequence;
  }

  private pollTask(taskId: string): void {
    if (this.disposed || this.taskPollers.has(taskId)) return;
    const refresh = async () => {
      this.taskPollers.delete(taskId);
      if (this.disposed) return;
      try {
        const baselineSequence = this.lastEventSequence;
        const afterLogSequence = (this.state.taskLogs[taskId] ?? []).reduce(
          (maximum, entry) => Math.max(maximum, entry.sequence),
          0
        );
        const result = await this.invoke<QueryResult<TaskProjection>>({
          messageType: 'query',
          schemaVersion: '1.0',
          requestId: safeRandomUUID(),
          workspaceId: this.host.workspaceId,
          operation: 'get_task',
          payload: { taskId, afterLogSequence }
        });
        if (this.disposed) return;
        if (this.lastEventSequence !== baselineSequence) {
          const currentTask = this.state.tasks[taskId];
          if (currentTask?.state === 'queued' || currentTask?.state === 'running') {
            this.taskPollers.set(taskId, setTimeout(refresh, 500));
          }
          return;
        }
        this.applyQueryResult(result);
        this.notify();
        const task = (result.data as TaskProjection | null)?.task;
        if (!this.disposed && (task?.state === 'queued' || task?.state === 'running')) {
          this.taskPollers.set(taskId, setTimeout(refresh, 500));
        }
      } catch (error) {
        console.warn('[Copperbench Bridge] Task refresh failed:', error);
        if (!this.disposed) this.taskPollers.set(taskId, setTimeout(refresh, 1000));
      }
    };
    // Leave the originating command observable to native hosts before polling task state.
    this.taskPollers.set(taskId, setTimeout(refresh, 500));
  }

  private notify(): void {
    this.stateListeners.forEach((listener) => listener(this.state));
  }
}
