import type {
  Command,
  CommandResult,
  CoreEvent,
  HistoryComparison,
  HistoryProjection,
  ModElementEditorProjection,
  ModElementListProjection,
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

  private async refreshInitialProjection(): Promise<void> {
    const base = {
      messageType: 'query' as const,
      schemaVersion: '1.0' as const,
      workspaceId: this.host.workspaceId
    };
    await this.sendQuery({ ...base, requestId: safeRandomUUID(), operation: 'get_workbench', payload: {} });
    await this.sendQuery({
      ...base,
      requestId: safeRandomUUID(),
      operation: 'list_mod_elements',
      payload: { search: '', types: [], states: [], page: 1, pageSize: 200 }
    });
  }

  private async invoke<T>(envelope: unknown): Promise<T> {
    return this.parse<T>(await this.host.invoke(JSON.stringify(envelope)));
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
  }

  private applyQueryResult(result: QueryResult): void {
    if (result.status !== 'succeeded' || !result.data) {
      this.state.diagnostics = [...result.diagnostics];
      return;
    }
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
        this.state.taskLogs[projection.task.id] = [...projection.logs];
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
    if (event.event === 'task_started' || event.event === 'task_progressed' || event.event === 'task_completed') {
      this.state.tasks[event.payload.task.id] = event.payload.task;
    } else if (event.event === 'task_log_appended') {
      const current = this.state.taskLogs[event.payload.taskId] ?? [];
      this.state.taskLogs[event.payload.taskId] = [...current, ...event.payload.entries];
    } else if (event.event === 'diagnostics_changed') {
      this.state.diagnostics = [...event.payload.diagnostics];
    } else if (event.event === 'bridge_recovery_required') {
      this.state.viewportState = 'recovery';
      this.state.recoveryState = event.payload;
    }
    if (this.state.workbench) this.state.workbench.workspace.revision = event.revision;
    this.eventListeners.forEach((listener) => listener(event));
    this.notify();
  }

  private pollTask(taskId: string): void {
    if (this.disposed || this.taskPollers.has(taskId)) return;
    const refresh = async () => {
      this.taskPollers.delete(taskId);
      if (this.disposed) return;
      try {
        const result = await this.sendQuery<TaskProjection>({
          messageType: 'query',
          schemaVersion: '1.0',
          requestId: safeRandomUUID(),
          workspaceId: this.host.workspaceId,
          operation: 'get_task',
          payload: { taskId }
        });
        const task = (result.data as TaskProjection | null)?.task;
        if (!this.disposed && task?.state === 'running') {
          this.taskPollers.set(taskId, setTimeout(refresh, 500));
        }
      } catch (error) {
        console.warn('[Copperbench Bridge] Task refresh failed:', error);
        if (!this.disposed) this.taskPollers.set(taskId, setTimeout(refresh, 1000));
      }
    };
    this.taskPollers.set(taskId, setTimeout(refresh, 150));
  }

  private notify(): void {
    this.stateListeners.forEach((listener) => listener(this.state));
  }
}
