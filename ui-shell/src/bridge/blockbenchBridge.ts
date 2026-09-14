import { tr } from '../i18n/locale';
export const BLOCKBENCH_BRIDGE_SCHEMA_VERSION = '1.0' as const;

export type BlockbenchState = 'unavailable' | 'ready' | 'running' | 'exited' | 'failed';

export interface BlockbenchSnapshot {
  readonly schemaVersion: typeof BLOCKBENCH_BRIDGE_SCHEMA_VERSION;
  readonly state: BlockbenchState;
  readonly assetId: string | null;
  readonly relativePath: string | null;
  readonly processId: number | null;
  readonly exitCode: number | null;
  readonly openedSha256: string | null;
  readonly currentSha256: string | null;
  readonly blockbenchVersion: string | null;
  readonly diagnosticCode: string | null;
  readonly recoveryPointId: string | null;
  readonly workspaceRevision: number | null;
  readonly changeCommitted: boolean;
}

export interface NativeBlockbenchHost {
  readonly schemaVersion: typeof BLOCKBENCH_BRIDGE_SCHEMA_VERSION;
  status(): Promise<BlockbenchSnapshot>;
  selectExecutable?(): Promise<{ cancelled: boolean }>;
  dismissSetup?(): Promise<void>;
  openAsset(assetId: string): Promise<BlockbenchSnapshot>;
}

declare global {
  interface Window {
    __COPPERBENCH_BLOCKBENCH_HOST__?: NativeBlockbenchHost;
  }
}

export interface BlockbenchBridge {
  readonly available: boolean;
  status(): Promise<BlockbenchSnapshot>;
  selectExecutable(): Promise<{ cancelled: boolean }>;
  dismissSetup(): Promise<void>;
  openAsset(assetId: string): Promise<BlockbenchSnapshot>;
}

const unavailable = (): BlockbenchSnapshot => ({
  schemaVersion: BLOCKBENCH_BRIDGE_SCHEMA_VERSION,
  state: 'unavailable',
  assetId: null,
  relativePath: null,
  processId: null,
  exitCode: null,
  openedSha256: null,
  currentSha256: null,
  blockbenchVersion: null,
  diagnosticCode: 'BLOCKBENCH_NOT_CONFIGURED',
  recoveryPointId: null,
  workspaceRevision: null,
  changeCommitted: false
});

class NativeBridge implements BlockbenchBridge {
  public readonly available = true;
  public constructor(private readonly host: NativeBlockbenchHost) {}
  public status(): Promise<BlockbenchSnapshot> { return this.host.status(); }
  public selectExecutable(): Promise<{ cancelled: boolean }> {
    return this.host.selectExecutable?.() ?? Promise.reject(new Error(tr("当前桌面版本不支持安装路径选择。")));
  }
  public dismissSetup(): Promise<void> { return this.host.dismissSetup?.() ?? Promise.reject(new Error(tr("当前桌面版本不支持保存引导偏好。"))); }
  public openAsset(assetId: string): Promise<BlockbenchSnapshot> {
    if (!/^asset:[0-9a-f]{64}$/.test(assetId)) {
      return Promise.reject(new Error('INVALID_ASSET_ID'));
    }
    return this.host.openAsset(assetId);
  }
}

class PreviewBridge implements BlockbenchBridge {
  public readonly available = false;
  public async status(): Promise<BlockbenchSnapshot> { return unavailable(); }
  public selectExecutable(): Promise<{ cancelled: boolean }> { return Promise.reject(new Error(tr("请在桌面产品中选择安装位置。"))); }
  public dismissSetup(): Promise<void> { return Promise.resolve(); }
  public async openAsset(_assetId: string): Promise<BlockbenchSnapshot> { return unavailable(); }
}

const host = typeof window === 'undefined' ? undefined : window.__COPPERBENCH_BLOCKBENCH_HOST__;
export const blockbenchBridge: BlockbenchBridge = host?.schemaVersion === BLOCKBENCH_BRIDGE_SCHEMA_VERSION
  ? new NativeBridge(host)
  : new PreviewBridge();
