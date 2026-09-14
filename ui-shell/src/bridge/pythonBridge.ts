export interface PythonContextState {
  workspaceId: string;
  activeElementId: string | null;
  selectionVersion: number;
  view: string;
}

export interface PythonStatus {
  state: 'stopped' | 'starting' | 'running' | 'ready' | 'failed';
  pythonVersion: string;
  requestId: string;
  lastResult?: 'idle' | 'running' | 'succeeded' | 'failed' | 'incomplete' | 'cancelled';
  lastError?: string;
  lastSequence: number;
  truncated: boolean;
  errorLine: number | null;
  context: PythonContextState;
  completions: string[];
  operators: { idname: string; label: string }[];
  output: { sequence: number; channel: string; text: string }[];
}

declare global {
  interface Window {
    __COPPERBENCH_PYTHON_HOST__?: { invoke(payload: Record<string, unknown>): Promise<unknown> };
  }
}

export const pythonBridge = {
  get available() { return typeof window !== 'undefined' && Boolean(window.__COPPERBENCH_PYTHON_HOST__); },
  async invoke<T>(payload: Record<string, unknown>): Promise<T> {
    const host = window.__COPPERBENCH_PYTHON_HOST__;
    if (!host) throw new Error('Python 工作台需要支持此功能的桌面版本');
    return await host.invoke(payload) as T;
  }
};
