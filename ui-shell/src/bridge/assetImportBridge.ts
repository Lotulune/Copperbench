export const ASSET_IMPORT_BRIDGE_SCHEMA_VERSION = '1.0' as const;
export const ASSET_IMPORT_DROP_EVENT = 'copperbench:asset-drop' as const;

export interface AssetImportSelectionGrant {
  readonly id: string;
  readonly fileName: string;
  readonly size: number;
  readonly expiresAt: string;
  readonly cancelled?: boolean;
}

interface NativeAssetImportHost {
  readonly schemaVersion: typeof ASSET_IMPORT_BRIDGE_SCHEMA_VERSION;
  selectSource(): Promise<AssetImportSelectionGrant>;
  selectSources(): Promise<{ cancelled: boolean; grants: AssetImportSelectionGrant[] }>;
}

declare global {
  interface Window {
    __COPPERBENCH_ASSET_IMPORT_HOST__?: NativeAssetImportHost;
  }
}

export interface AssetImportBridge {
  readonly available: boolean;
  selectSource(): Promise<AssetImportSelectionGrant>;
  selectSources(): Promise<{ cancelled: boolean; grants: AssetImportSelectionGrant[] }>;
  subscribeDroppedSources(listener: (grants: AssetImportSelectionGrant[]) => void): () => void;
}

function subscribeDroppedSources(listener: (grants: AssetImportSelectionGrant[]) => void): () => void {
  if (typeof window === 'undefined') return () => undefined;
  const handler = (event: Event) => {
    const detail = (event as CustomEvent<{ grants?: unknown }>).detail;
    if (!detail || !Array.isArray(detail.grants)) return;
    const grants = detail.grants.filter((grant): grant is AssetImportSelectionGrant => {
      if (!grant || typeof grant !== 'object') return false;
      const candidate = grant as Partial<AssetImportSelectionGrant>;
      return typeof candidate.id === 'string'
        && typeof candidate.fileName === 'string'
        && typeof candidate.size === 'number'
        && typeof candidate.expiresAt === 'string';
    });
    if (grants.length > 0) listener(grants);
  };
  window.addEventListener(ASSET_IMPORT_DROP_EVENT, handler);
  return () => window.removeEventListener(ASSET_IMPORT_DROP_EVENT, handler);
}

class NativeBridge implements AssetImportBridge {
  public readonly available = true;
  public constructor(private readonly host: NativeAssetImportHost) {}
  public selectSource(): Promise<AssetImportSelectionGrant> { return this.host.selectSource(); }
  public selectSources(): Promise<{ cancelled: boolean; grants: AssetImportSelectionGrant[] }> {
    return this.host.selectSources();
  }
  public subscribeDroppedSources(listener: (grants: AssetImportSelectionGrant[]) => void): () => void {
    return subscribeDroppedSources(listener);
  }
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
  public async selectSources(): Promise<{ cancelled: boolean; grants: AssetImportSelectionGrant[] }> {
    const expiresAt = new Date(Date.now() + 10 * 60 * 1000).toISOString();
    return {
      cancelled: false,
      grants: [
        { id: 'mock-asset-batch-source-1', fileName: 'batch_texture.png', size: 2048, expiresAt, cancelled: false },
        { id: 'mock-asset-batch-source-2', fileName: 'batch_icon.png', size: 4096, expiresAt, cancelled: false }
      ]
    };
  }
  public subscribeDroppedSources(listener: (grants: AssetImportSelectionGrant[]) => void): () => void {
    return subscribeDroppedSources(listener);
  }
}

const host = typeof window === 'undefined' ? undefined : window.__COPPERBENCH_ASSET_IMPORT_HOST__;
export const assetImportBridge: AssetImportBridge = host?.schemaVersion === ASSET_IMPORT_BRIDGE_SCHEMA_VERSION
  ? new NativeBridge(host)
  : new PreviewBridge();
