import { tr } from '../i18n/locale';
export const WINDOW_CHROME_SCHEMA_VERSION = '1.0' as const;
export const PREFERENCES_SCHEMA_VERSION = '1.0' as const;
export const PREFERENCES_SAVED_EVENT = 'copperbench:preferences-saved';

export type AppPreferenceValue = boolean | number | string;
export interface AppPreferenceEntry {
  key: string;
  section: 'ui' | 'ide' | 'gradle';
  type: 'boolean' | 'integer' | 'choice';
  value: AppPreferenceValue;
  min?: number;
  max?: number;
  options?: string[];
}
export interface AppPreferencesSnapshot {
  schemaVersion: typeof PREFERENCES_SCHEMA_VERSION;
  revision: string;
  entries: AppPreferenceEntry[];
}
export interface AppPreferencesPatch {
  revision: string;
  changes: Record<string, AppPreferenceValue>;
}

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
  readonly unsavedChangesSchemaVersion?: '1.0';
  reportUnsavedChanges?(count: number): Promise<void>;
  readonly systemFrame: boolean;
  readonly maximized?: boolean;
  readonly preferencesAvailable?: boolean;
  readonly preferencesSchemaVersion?: typeof PREFERENCES_SCHEMA_VERSION;
  getPreferences?(): Promise<AppPreferencesSnapshot>;
  savePreferences?(patch: AppPreferencesPatch): Promise<AppPreferencesSnapshot>;
  readonly chromeRegionSchemaVersion?: typeof WINDOW_CHROME_SCHEMA_VERSION;
  invoke(action: 'minimize' | 'toggle_maximize' | 'close' | 'open_preferences'): Promise<void>;
  reportChromeRegions?(snapshot: WindowChromeSnapshot): Promise<void>;
  pointerGesture?(gesture: WindowPointerGesture): void;
}

export interface WindowPointerGesture {
  phase: 'begin' | 'update' | 'end' | 'cancel';
  x: number;
  y: number;
  screenX: number;
  screenY: number;
}

declare global {
  interface Window {
    __COPPERBENCH_WINDOW_HOST__?: NativeWindowHost;
  }
}

export interface WindowBridge {
  readonly canGuardUnsavedChanges: boolean;
  reportUnsavedChanges(count: number): Promise<void>;
  readonly canOpenPreferences: boolean;
  readonly canManagePreferences: boolean;
  getPreferences(): Promise<AppPreferencesSnapshot>;
  savePreferences(patch: AppPreferencesPatch): Promise<AppPreferencesSnapshot>;
  openPreferences(): Promise<void>;
  readonly systemFrame: boolean;
  readonly canToggleFrame: boolean;
  readonly supportsChromeRegions: boolean;
  minimize(): void;
  toggleMaximize(): void;
  close(): void;
  reportChromeRegions(snapshot: WindowChromeSnapshot): void;
  pointerGesture(gesture: WindowPointerGesture): void;
}

class JcefWindowBridge implements WindowBridge {
  public readonly canGuardUnsavedChanges: boolean;
  public readonly canOpenPreferences: boolean;
  public readonly canManagePreferences: boolean;
  public readonly systemFrame: boolean;
  public readonly canToggleFrame = false;
  public readonly supportsChromeRegions: boolean;

  public constructor(private readonly host: NativeWindowHost) {
    this.canGuardUnsavedChanges = host.unsavedChangesSchemaVersion === '1.0' && typeof host.reportUnsavedChanges === 'function';
    this.canOpenPreferences = host.preferencesAvailable === true;
    this.canManagePreferences = host.preferencesSchemaVersion === PREFERENCES_SCHEMA_VERSION
      && typeof host.getPreferences === 'function' && typeof host.savePreferences === 'function';
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

  public reportUnsavedChanges(count: number): Promise<void> {
    if (!Number.isSafeInteger(count) || count < 0 || count > 1_000_000) return Promise.reject(new Error('Invalid unsaved source count'));
    return this.canGuardUnsavedChanges && this.host.reportUnsavedChanges
      ? this.host.reportUnsavedChanges(count) : Promise.reject(new Error('Native unsaved-source guard is unavailable'));
  }

  public openPreferences(): Promise<void> {
    return this.canOpenPreferences
      ? this.host.invoke('open_preferences')
      : Promise.reject(new Error(tr("当前桌面版本不支持打开设置，请更新程序。")));
  }

  public getPreferences(): Promise<AppPreferencesSnapshot> {
    return this.canManagePreferences && this.host.getPreferences
      ? this.host.getPreferences() : Promise.reject(new Error('Preferences are unavailable in this host'));
  }

  public async savePreferences(patch: AppPreferencesPatch): Promise<AppPreferencesSnapshot> {
    if (!this.canManagePreferences || !this.host.savePreferences) throw new Error('Preferences are unavailable in this host');
    const snapshot = await this.host.savePreferences(patch);
    window.dispatchEvent(new CustomEvent(PREFERENCES_SAVED_EVENT, { detail: snapshot }));
    return snapshot;
  }

  public reportChromeRegions(snapshot: WindowChromeSnapshot): void {
    if (!this.supportsChromeRegions || !this.host.reportChromeRegions) return;
    void this.host.reportChromeRegions(snapshot).catch((error: unknown) => {
      console.warn('[Copperbench Window Bridge] Native chrome-region report failed:', error);
    });
  }

  public pointerGesture(gesture: WindowPointerGesture): void {
    this.host.pointerGesture?.(gesture);
  }

  private invoke(action: 'minimize' | 'toggle_maximize' | 'close'): void {
    void this.host.invoke(action).catch((error: unknown) => {
      console.warn('[Copperbench Window Bridge] Native action failed:', error);
    });
  }
}

class MockWindowBridge implements WindowBridge {
  public readonly canGuardUnsavedChanges = false;
  public reportUnsavedChanges(_count: number): Promise<void> { return Promise.resolve(); }
  public readonly canOpenPreferences = false;
  public readonly canManagePreferences = false;

  public getPreferences(): Promise<AppPreferencesSnapshot> {
    return Promise.reject(new Error('Preferences require a desktop host'));
  }

  public savePreferences(_patch: AppPreferencesPatch): Promise<AppPreferencesSnapshot> {
    return Promise.reject(new Error('Preferences require a desktop host'));
  }

  public openPreferences(): Promise<void> {
    return Promise.reject(new Error(tr("请在桌面应用中打开设置。")));
  }
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

  public reportChromeRegions(_snapshot: WindowChromeSnapshot): void {
    // Browser previews do not own a native non-client area.
  }

  public pointerGesture(_gesture: WindowPointerGesture): void {}
}

const nativeWindowHost = typeof window === 'undefined' ? undefined : window.__COPPERBENCH_WINDOW_HOST__;

export const windowBridge: WindowBridge = nativeWindowHost
  ? new JcefWindowBridge(nativeWindowHost)
  : new MockWindowBridge();
