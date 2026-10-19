import { tr } from '../i18n/locale';
import { uiText } from '../i18n';
import type { PermissionProfile } from '../types/contract';

const listeners = new Set<() => void>();
const notifyChanged = () => listeners.forEach(listener => listener());

export const subscribeMcpRuntime = (listener: () => void) => {
  listeners.add(listener);
  return () => { listeners.delete(listener); };
};
export interface McpRuntimeState {
  status: 'listening' | 'not_started';
  url: string | null;
  workspaceId: string;
  permissionProfile: 'read_only' | 'workspace' | 'full_access';
  expiresAt: string | null;
  tokenAvailable: boolean;
  failure: string | null;
}

interface McpTokenResponse {
  token: string;
}

export interface NativeMcpRuntimeHost {
  readonly available: boolean;
  getState(): Promise<McpRuntimeState>;
  setPermissionProfile(profile: PermissionProfile): Promise<McpRuntimeState>;
  resetPairing?(): Promise<McpRuntimeState>;
  revealTokenOnce(): Promise<McpTokenResponse>;
  copyText(text: string): Promise<void>;
}

declare global {
  interface Window {
    __COPPERBENCH_MCP_HOST__?: NativeMcpRuntimeHost;
  }
}

export interface McpRuntimeBridge {
  readonly available: boolean;
  readonly canResetPairing: boolean;
  readonly changing: boolean;
  readonly revealingToken: boolean;
  getState(): Promise<McpRuntimeState>;
  setPermissionProfile(profile: PermissionProfile): Promise<McpRuntimeState>;
  resetPairing(): Promise<McpRuntimeState>;
  revealTokenOnce(): Promise<McpTokenResponse>;
  copyText(text: string): Promise<void>;
}

class JcefMcpRuntimeBridge implements McpRuntimeBridge {
  public readonly available: boolean;
  public readonly canResetPairing: boolean;
  private pendingChange = false;
  private pendingReveal = false;

  public constructor(private readonly host: NativeMcpRuntimeHost) {
    this.available = host.available;
    this.canResetPairing = host.available && typeof host.resetPairing === 'function';
  }

  public get changing(): boolean { return this.pendingChange; }
  public get revealingToken(): boolean { return this.pendingReveal; }

  public getState(): Promise<McpRuntimeState> {
    return this.host.getState();
  }

  public async setPermissionProfile(profile: PermissionProfile): Promise<McpRuntimeState> {
    return this.change(() => this.host.setPermissionProfile(profile));
  }

  public async resetPairing(): Promise<McpRuntimeState> {
    if (!this.canResetPairing) throw new Error(uiText(
      '当前桌面版本不支持重新配对，请重新打开工作区。',
      'Reopen this workspace to obtain a new token in this desktop version.'));
    return this.change(() => this.host.resetPairing!());
  }

  private async change(action: () => Promise<McpRuntimeState>): Promise<McpRuntimeState> {
    this.assertIdle();
    this.pendingChange = true;
    notifyChanged();
    try {
      return await action();
    } finally {
      this.pendingChange = false;
      notifyChanged();
    }
  }

  private assertIdle(): void {
    if (this.pendingChange || this.pendingReveal) throw new Error(uiText(
      'MCP 操作正在进行，请稍候。', 'An MCP operation is in progress. Please wait.'));
  }

  public async revealTokenOnce(): Promise<McpTokenResponse> {
    this.assertIdle();
    this.pendingReveal = true;
    notifyChanged();
    try {
      return await this.host.revealTokenOnce();
    } finally {
      this.pendingReveal = false;
      notifyChanged();
    }
  }

  public copyText(text: string): Promise<void> {
    return this.host.copyText(text);
  }
}

class UnavailableMcpRuntimeBridge implements McpRuntimeBridge {
  public readonly available = false;
  public readonly canResetPairing = false;
  public readonly changing = false;
  public readonly revealingToken = false;

  public getState(): Promise<McpRuntimeState> {
    return Promise.resolve({
      status: 'not_started',
      url: null,
      workspaceId: '',
      permissionProfile: 'workspace',
      expiresAt: null,
      tokenAvailable: false,
      failure: tr("桌面 MCP 宿主不可用")
    });
  }

  public revealTokenOnce(): Promise<McpTokenResponse> {
    return Promise.reject(new Error(tr("MCP 令牌仅可在桌面宿主中获取")));
  }

  public setPermissionProfile(_profile: PermissionProfile): Promise<McpRuntimeState> {
    return Promise.reject(new Error(tr("MCP 权限仅可在桌面宿主中更改")));
  }

  public resetPairing(): Promise<McpRuntimeState> {
    return Promise.reject(new Error(uiText('请在桌面应用中重新配对 MCP。', 'Reset MCP pairing in the desktop app.')));
  }

  public copyText(text: string): Promise<void> {
    if (!navigator.clipboard) return Promise.reject(new Error(tr("浏览器剪贴板不可用")));
    return navigator.clipboard.writeText(text);
  }
}

const nativeHost = typeof window === 'undefined' ? undefined : window.__COPPERBENCH_MCP_HOST__;

export const mcpRuntimeBridge: McpRuntimeBridge = nativeHost
  ? new JcefMcpRuntimeBridge(nativeHost)
  : new UnavailableMcpRuntimeBridge();
