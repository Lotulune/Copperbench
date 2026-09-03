export const WINDOW_CHROME_SCHEMA_VERSION = '1.0' as const;

export type WindowChromeRegionKind = 'caption' | 'client' | 'minimize' | 'maximize' | 'close';

export interface WindowChromeBounds {
  readonly x: number;
  readonly y: number;
  readonly width: number;
  readonly height: number;
}

export interface WindowChromeRegion {
  readonly id: string;
  readonly kind: WindowChromeRegionKind;
  readonly bounds: WindowChromeBounds;
}

export interface WindowChromeSnapshot {
  readonly schemaVersion: typeof WINDOW_CHROME_SCHEMA_VERSION;
  readonly sequence: number;
  readonly coordinateSpace: 'css_viewport';
  readonly devicePixelRatio: number;
  readonly viewport: {
    readonly width: number;
    readonly height: number;
  };
  readonly regions: readonly WindowChromeRegion[];
}

export interface NativeWindowHost {
  readonly systemFrame: boolean;
  readonly chromeRegionSchemaVersion?: typeof WINDOW_CHROME_SCHEMA_VERSION;
  invoke(action: 'minimize' | 'toggle_maximize' | 'close' | 'begin_drag'): Promise<void>;
  beginDrag?(): Promise<void>;
  reportChromeRegions?(snapshot: WindowChromeSnapshot): Promise<void>;
}

declare global {
  interface Window {
    __COPPERBENCH_WINDOW_HOST__?: NativeWindowHost;
  }
}

export interface WindowBridge {
  readonly systemFrame: boolean;
  readonly canToggleFrame: boolean;
  readonly supportsChromeRegions: boolean;
  minimize(): void;
  toggleMaximize(): void;
  close(): void;
  beginDrag(): void;
  reportChromeRegions(snapshot: WindowChromeSnapshot): void;
}

class JcefWindowBridge implements WindowBridge {
  public readonly systemFrame: boolean;
  public readonly canToggleFrame = false;
  public readonly supportsChromeRegions: boolean;

  public constructor(private readonly host: NativeWindowHost) {
    this.systemFrame = host.systemFrame;
    this.supportsChromeRegions = !host.systemFrame
      && host.chromeRegionSchemaVersion === WINDOW_CHROME_SCHEMA_VERSION
      && typeof host.reportChromeRegions === 'function';
  }

  public minimize(): void {
    this.invoke('minimize');
  }

  public toggleMaximize(): void {
    this.invoke('toggle_maximize');
  }

  public close(): void {
    this.invoke('close');
  }

  public beginDrag(): void {
    if (typeof this.host.beginDrag === 'function') {
      void this.host.beginDrag().catch((error: unknown) => {
        console.warn('[Copperbench Window Bridge] Native begin-drag failed:', error);
      });
      return;
    }
    this.invoke('begin_drag');
  }

  public reportChromeRegions(snapshot: WindowChromeSnapshot): void {
    if (!this.supportsChromeRegions || !this.host.reportChromeRegions) return;
    void this.host.reportChromeRegions(snapshot).catch((error: unknown) => {
      console.warn('[Copperbench Window Bridge] Native chrome-region report failed:', error);
    });
  }

  private invoke(action: 'minimize' | 'toggle_maximize' | 'close' | 'begin_drag'): void {
    void this.host.invoke(action).catch((error: unknown) => {
      console.warn('[Copperbench Window Bridge] Native action failed:', error);
    });
  }
}

class MockWindowBridge implements WindowBridge {
  public readonly systemFrame = false;
  public readonly canToggleFrame = true;
  public readonly supportsChromeRegions = false;
  public lastAction: string | null = null;

  public minimize(): void {
    this.lastAction = 'minimize';
  }

  public toggleMaximize(): void {
    this.lastAction = 'toggle_maximize';
  }

  public close(): void {
    this.lastAction = 'close';
  }

  public beginDrag(): void {
    this.lastAction = 'begin_drag';
  }

  public reportChromeRegions(_snapshot: WindowChromeSnapshot): void {
    // Browser previews do not own a native non-client area.
  }
}

const nativeWindowHost = typeof window === 'undefined' ? undefined : window.__COPPERBENCH_WINDOW_HOST__;

export const windowBridge: WindowBridge = nativeWindowHost
  ? new JcefWindowBridge(nativeWindowHost)
  : new MockWindowBridge();
