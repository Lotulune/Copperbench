export interface NativeDiagnosticsHost {
  readonly available: boolean;
  openLogs(failureId: string): Promise<void>;
}

declare global {
  interface Window {
    __COPPERBENCH_DIAGNOSTICS_HOST__?: NativeDiagnosticsHost;
  }
}

export interface DiagnosticsBridge {
  readonly available: boolean;
  openLogs(failureId: string): Promise<void>;
}

class JcefDiagnosticsBridge implements DiagnosticsBridge {
  public readonly available: boolean;

  public constructor(private readonly host: NativeDiagnosticsHost) {
    this.available = host.available;
  }

  public openLogs(failureId: string): Promise<void> {
    return this.host.openLogs(failureId);
  }
}

class UnavailableDiagnosticsBridge implements DiagnosticsBridge {
  public readonly available = false;

  public openLogs(_failureId: string): Promise<void> {
    return Promise.reject(new Error('应用日志仅可在桌面宿主中打开'));
  }
}

const nativeHost = typeof window === 'undefined'
  ? undefined
  : window.__COPPERBENCH_DIAGNOSTICS_HOST__;

export const diagnosticsBridge: DiagnosticsBridge = nativeHost
  ? new JcefDiagnosticsBridge(nativeHost)
  : new UnavailableDiagnosticsBridge();
