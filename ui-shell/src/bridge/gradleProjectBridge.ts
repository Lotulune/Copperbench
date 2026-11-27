import { uiText } from '../i18n';

/** The adopted project's desktop choices (ADR-0020 decisions 7 and 8), as the native host reports them. */
export interface GradleProjectStatus {
  projectRoot: string;
  gitIgnore: {
    /** Whether the project folder is inside a Git repository. */
    repository: boolean;
    /** Whether the project's own .gitignore already lists .copperbench/. */
    ignored: boolean;
    /** The user's answer; null until they choose. */
    decision: 'added' | 'declined' | null;
  };
  gradleUserHome: {
    mode: 'product' | 'environment';
    product: string;
    environment: string;
  };
}

export type GradleProjectRequest =
  | { operation: 'status' }
  | { operation: 'git_ignore'; add: boolean }
  | { operation: 'gradle_user_home'; mode: 'product' | 'environment' };

export interface NativeGradleProjectHost {
  readonly available: boolean;
  request(payload: GradleProjectRequest): Promise<GradleProjectStatus>;
}

declare global {
  interface Window {
    __COPPERBENCH_GRADLE_PROJECT_HOST__?: NativeGradleProjectHost;
  }
}

/** The host installs itself when each page load starts, so it is read at call time rather than at import. */
function host(): NativeGradleProjectHost | undefined {
  const candidate = typeof window === 'undefined' ? undefined : window.__COPPERBENCH_GRADLE_PROJECT_HOST__;
  return candidate?.available && typeof candidate.request === 'function' ? candidate : undefined;
}

function request(payload: GradleProjectRequest): Promise<GradleProjectStatus> {
  const native = host();
  return native ? native.request(payload)
    : Promise.reject(new Error(uiText('工程设置仅可在桌面应用中修改', 'Project settings can only be changed in the desktop app')));
}

export const gradleProjectBridge = {
  get available(): boolean { return host() !== undefined; },
  status: () => request({ operation: 'status' }),
  decideGitIgnore: (add: boolean) => request({ operation: 'git_ignore', add }),
  setGradleUserHome: (mode: 'product' | 'environment') => request({ operation: 'gradle_user_home', mode })
};
