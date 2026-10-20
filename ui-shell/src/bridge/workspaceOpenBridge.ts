import { tr, uiText } from '../i18n/locale';

export interface WorkspaceFileSelection {
  cancelled: boolean;
  workspaceFile?: string;
}

export interface WorkspaceFolderSelection {
  cancelled: boolean;
  path?: string;
}

export interface NativeWorkspaceOpenHost {
  readonly available: boolean;
  readonly buildFolderAvailable?: boolean;
  readonly verifiedExportFolderAvailable?: boolean;
  readonly workspacePickerAvailable?: boolean;
  readonly workspaceFolderPickerAvailable?: boolean;
  chooseWorkspace?(): Promise<WorkspaceFileSelection>;
  chooseWorkspaceFolder?(initialPath?: string): Promise<WorkspaceFolderSelection>;
  openVerifiedExportFolder?(taskId: string): Promise<void>;
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
  readonly verifiedExportFolderAvailable: boolean;
  readonly workspacePickerAvailable: boolean;
  readonly workspaceFolderPickerAvailable: boolean;
  chooseWorkspace(): Promise<WorkspaceFileSelection>;
  chooseWorkspaceFolder(initialPath?: string): Promise<WorkspaceFolderSelection>;
  openVerifiedExportFolder(taskId: string): Promise<void>;
  openBuildFolder(): Promise<void>;
  open(workspaceFile: string): Promise<void>;
}

class JcefWorkspaceOpenBridge implements WorkspaceOpenBridge {
  public readonly available: boolean;
  public readonly buildFolderAvailable: boolean;
  public readonly verifiedExportFolderAvailable: boolean;
  public readonly workspacePickerAvailable: boolean;
  public readonly workspaceFolderPickerAvailable: boolean;

  public constructor(private readonly host: NativeWorkspaceOpenHost) {
    this.available = host.available;
    this.buildFolderAvailable = host.buildFolderAvailable === true && typeof host.openBuildFolder === 'function';
    this.verifiedExportFolderAvailable = host.verifiedExportFolderAvailable === true && typeof host.openVerifiedExportFolder === 'function';
    this.workspacePickerAvailable = host.workspacePickerAvailable === true && typeof host.chooseWorkspace === 'function';
    this.workspaceFolderPickerAvailable = host.workspaceFolderPickerAvailable === true && typeof host.chooseWorkspaceFolder === 'function';
  }

  public chooseWorkspace(): Promise<WorkspaceFileSelection> {
    return this.workspacePickerAvailable ? this.host.chooseWorkspace!()
      : Promise.reject(new Error(uiText('无法浏览工作区文件，请手动输入路径。', 'The workspace picker is unavailable. Enter the path manually.')));
  }

  public chooseWorkspaceFolder(initialPath?: string): Promise<WorkspaceFolderSelection> {
    return this.workspaceFolderPickerAvailable ? this.host.chooseWorkspaceFolder!(initialPath)
      : Promise.reject(new Error(uiText('无法浏览文件夹，请手动输入路径。', 'The folder picker is unavailable. Enter the path manually.')));
  }

  public openBuildFolder(): Promise<void> {
    return this.buildFolderAvailable ? this.host.openBuildFolder!() : Promise.reject(new Error('Build folder unavailable'));
  }

  public openVerifiedExportFolder(taskId: string): Promise<void> {
    return this.verifiedExportFolderAvailable ? this.host.openVerifiedExportFolder!(taskId) : Promise.reject(new Error('Verified export folder unavailable'));
  }

  public open(workspaceFile: string): Promise<void> {
    return this.host.open(workspaceFile);
  }
}

class UnavailableWorkspaceOpenBridge implements WorkspaceOpenBridge {
  public readonly available = false;
  public readonly buildFolderAvailable = false;
  public readonly verifiedExportFolderAvailable = false;
  public readonly workspacePickerAvailable = false;
  public readonly workspaceFolderPickerAvailable = false;

  public chooseWorkspace(): Promise<WorkspaceFileSelection> {
    return Promise.reject(new Error(uiText('无法浏览工作区文件，请手动输入路径。', 'The workspace picker is unavailable. Enter the path manually.')));
  }

  public chooseWorkspaceFolder(_initialPath?: string): Promise<WorkspaceFolderSelection> {
    return Promise.reject(new Error(uiText('无法浏览文件夹，请手动输入路径。', 'The folder picker is unavailable. Enter the path manually.')));
  }

  public openVerifiedExportFolder(_taskId: string): Promise<void> {
    return Promise.reject(new Error('Verified export folder unavailable'));
  }

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
