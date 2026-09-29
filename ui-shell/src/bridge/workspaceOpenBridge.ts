import { tr } from '../i18n/locale';
export interface NativeWorkspaceOpenHost {
  readonly available: boolean;
  readonly buildFolderAvailable?: boolean;
  openBuildFolder?(): Promise<void>;
  open(workspaceFile: string): Promise<void>;
}

declare global {
  interface Window {
    __COPPERBENCH_WORKSPACE_OPEN_HOST__?: NativeWorkspaceOpenHost;
  }
}

export interface WorkspaceOpenBridge {
  readonly available: boolean;
  readonly buildFolderAvailable: boolean;
  openBuildFolder(): Promise<void>;
  open(workspaceFile: string): Promise<void>;
}

class JcefWorkspaceOpenBridge implements WorkspaceOpenBridge {
  public readonly available: boolean;
  public readonly buildFolderAvailable: boolean;

  public constructor(private readonly host: NativeWorkspaceOpenHost) {
    this.available = host.available;
    this.buildFolderAvailable = host.buildFolderAvailable === true && typeof host.openBuildFolder === 'function';
  }

  public openBuildFolder(): Promise<void> {
    return this.buildFolderAvailable ? this.host.openBuildFolder!() : Promise.reject(new Error('Build folder unavailable'));
  }

  public open(workspaceFile: string): Promise<void> {
    return this.host.open(workspaceFile);
  }
}

class UnavailableWorkspaceOpenBridge implements WorkspaceOpenBridge {
  public readonly available = false;
  public readonly buildFolderAvailable = false;

  public openBuildFolder(): Promise<void> {
    return Promise.reject(new Error('Build folder unavailable'));
  }

  public open(_workspaceFile: string): Promise<void> {
    return Promise.reject(new Error(tr("仅桌面应用支持在新窗口中打开工作区")));
  }
}

const nativeHost = typeof window === 'undefined'
  ? undefined
  : window.__COPPERBENCH_WORKSPACE_OPEN_HOST__;

export const workspaceOpenBridge: WorkspaceOpenBridge = nativeHost
  ? new JcefWorkspaceOpenBridge(nativeHost)
  : new UnavailableWorkspaceOpenBridge();
