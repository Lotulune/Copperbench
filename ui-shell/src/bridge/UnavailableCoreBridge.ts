import type {
  Command,
  CommandResult,
  CoreEvent,
  Query,
  QueryResult
} from '../types/contract';
import type { BridgeState, CoreBridge, HandshakeRequest, HandshakeResult } from './CoreBridge';

const unavailableState: BridgeState = {
  currentScenarioId: 'native',
  viewportState: 'error',
  expectedUi: null,
  workbench: null,
  elements: [],
  elementEditors: {},
  tasks: {},
  taskLogs: {},
  diagnostics: [
    {
      code: 'UI_CORE_HOST_UNAVAILABLE',
      severity: 'error',
      message: {
        key: 'diagnostic.ui_core_host_unavailable',
        fallback: 'The desktop UI-Core host was not injected. Mock data is disabled in production.'
      },
      path: null,
      recoverable: false,
      actions: []
    }
  ],
  recoveryPoints: [],
  currentRecoveryPointId: null,
  historyComparison: null,
  operationApprovals: [],
  versionTracks: null,
  publishBatches: [],
  recoveryState: null,
  schemaIncompatible: false
};

/** Fail-closed bridge used only when a production shell starts without its native host. */
export class UnavailableCoreBridge implements CoreBridge {
  public getState(): BridgeState {
    return unavailableState;
  }

  public onEvent(_listener: (event: CoreEvent) => void): () => void {
    return () => undefined;
  }

  public onStateChange(listener: (state: BridgeState) => void): () => void {
    listener(unavailableState);
    return () => undefined;
  }

  public async sendCommand<T>(_command: Command<T>): Promise<CommandResult> {
    throw this.unavailableError();
  }

  public async sendQuery<T>(_query: Query): Promise<QueryResult<T>> {
    throw this.unavailableError();
  }

  public async negotiateHandshake(_request: HandshakeRequest): Promise<HandshakeResult> {
    throw this.unavailableError();
  }

  private unavailableError(): Error {
    return new Error('UI_CORE_HOST_UNAVAILABLE: production shell has no native JCEF host');
  }
}
