export const ASSET_IMPORT_BRIDGE_SCHEMA_VERSION = '1.0' as const;

export interface AssetImportSelectionGrant {
  readonly id: string;
  readonly fileName: string;
  readonly size: number;
  readonly expiresAt: string;
  readonly cancelled: boolean;
}

interface NativeAssetImportHost {
  readonly schemaVersion: typeof ASSET_IMPORT_BRIDGE_SCHEMA_VERSION;
  selectSource(): Promise<AssetImportSelectionGrant>;
}

declare global {
  interface Window {
    __COPPERBENCH_ASSET_IMPORT_HOST__?: NativeAssetImportHost;
  }
}

export interface AssetImportBridge {
  readonly available: boolean;
  selectSource(): Promise<AssetImportSelectionGrant>;
}

class NativeBridge implements AssetImportBridge {
  public readonly available = true;
  public constructor(private readonly host: NativeAssetImportHost) {}
  public selectSource(): Promise<AssetImportSelectionGrant> { return this.host.selectSource(); }
}

class PreviewBridge implements AssetImportBridge {
  public readonly available = false;
  public async selectSource(): Promise<AssetImportSelectionGrant> {
    return {
      id: 'mock-asset-source-grant',
      fileName: 'imported_texture.png',
      size: 1536,
      expiresAt: new Date(Date.now() + 10 * 60 * 1000).toISOString(),
      cancelled: false
    };
  }
}

const host = typeof window === 'undefined' ? undefined : window.__COPPERBENCH_ASSET_IMPORT_HOST__;
export const assetImportBridge: AssetImportBridge = host?.schemaVersion === ASSET_IMPORT_BRIDGE_SCHEMA_VERSION
  ? new NativeBridge(host)
  : new PreviewBridge();
