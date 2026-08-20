export interface NativeLegacyPluginHost {
  readonly available: boolean;
  invoke(action: 'open'): Promise<void>;
}

declare global {
  interface Window {
    __COPPERBENCH_LEGACY_PLUGIN_HOST__?: NativeLegacyPluginHost;
  }
}

export interface LegacyPluginBridge {
  readonly available: boolean;
  open(): Promise<void>;
}

class JcefLegacyPluginBridge implements LegacyPluginBridge {
  public readonly available: boolean;

  public constructor(private readonly host: NativeLegacyPluginHost) {
    this.available = host.available;
  }

  public open(): Promise<void> {
    return this.host.invoke('open');
  }
}

class UnavailableLegacyPluginBridge implements LegacyPluginBridge {
  public readonly available = false;

  public open(): Promise<void> {
    return Promise.reject(new Error('Legacy plugin window is only available in the desktop host'));
  }
}

const nativeHost = typeof window === 'undefined'
  ? undefined
  : window.__COPPERBENCH_LEGACY_PLUGIN_HOST__;

export const legacyPluginBridge: LegacyPluginBridge = nativeHost
  ? new JcefLegacyPluginBridge(nativeHost)
  : new UnavailableLegacyPluginBridge();
