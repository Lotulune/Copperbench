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
  getState(): Promise<McpRuntimeState>;
  revealTokenOnce(): Promise<McpTokenResponse>;
  copyText(text: string): Promise<void>;
}

class JcefMcpRuntimeBridge implements McpRuntimeBridge {
  public readonly available: boolean;

  public constructor(private readonly host: NativeMcpRuntimeHost) {
    this.available = host.available;
  }

  public getState(): Promise<McpRuntimeState> {
    return this.host.getState();
  }

  public revealTokenOnce(): Promise<McpTokenResponse> {
    return this.host.revealTokenOnce();
  }

  public copyText(text: string): Promise<void> {
    return this.host.copyText(text);
  }
}

class UnavailableMcpRuntimeBridge implements McpRuntimeBridge {
  public readonly available = false;

  public getState(): Promise<McpRuntimeState> {
    return Promise.resolve({
      status: 'not_started',
      url: null,
      workspaceId: '',
      permissionProfile: 'workspace',
      expiresAt: null,
      tokenAvailable: false,
      failure: '桌面 MCP 宿主不可用'
    });
  }

  public revealTokenOnce(): Promise<McpTokenResponse> {
    return Promise.reject(new Error('MCP 令牌仅可在桌面宿主中获取'));
  }

  public copyText(text: string): Promise<void> {
    if (!navigator.clipboard) return Promise.reject(new Error('浏览器剪贴板不可用'));
    return navigator.clipboard.writeText(text);
  }
}

const nativeHost = typeof window === 'undefined' ? undefined : window.__COPPERBENCH_MCP_HOST__;

export const mcpRuntimeBridge: McpRuntimeBridge = nativeHost
  ? new JcefMcpRuntimeBridge(nativeHost)
  : new UnavailableMcpRuntimeBridge();
