export interface NativeWorkspaceOpenHost {
  readonly available: boolean;
  open(workspaceFile: string): Promise<void>;
}

declare global {
  interface Window {
    __COPPERBENCH_WORKSPACE_OPEN_HOST__?: NativeWorkspaceOpenHost;
  }
}

export interface WorkspaceOpenBridge {
  readonly available: boolean;
  open(workspaceFile: string): Promise<void>;
}

class JcefWorkspaceOpenBridge implements WorkspaceOpenBridge {
  public readonly available: boolean;

  public constructor(private readonly host: NativeWorkspaceOpenHost) {
    this.available = host.available;
  }

  public open(workspaceFile: string): Promise<void> {
    return this.host.open(workspaceFile);
  }
}

class UnavailableWorkspaceOpenBridge implements WorkspaceOpenBridge {
  public readonly available = false;

  public open(_workspaceFile: string): Promise<void> {
    return Promise.reject(new Error('Opening a workspace in a new window is only available in the desktop host'));
  }
}

const nativeHost = typeof window === 'undefined'
  ? undefined
  : window.__COPPERBENCH_WORKSPACE_OPEN_HOST__;

export const workspaceOpenBridge: WorkspaceOpenBridge = nativeHost
  ? new JcefWorkspaceOpenBridge(nativeHost)
  : new UnavailableWorkspaceOpenBridge();
