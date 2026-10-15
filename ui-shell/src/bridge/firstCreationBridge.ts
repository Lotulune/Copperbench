import type { FirstCreationBinding } from '../onboarding/firstCreationModel';

export interface NativeFirstCreationHost {
  readonly workspaceFile: string;
  readonly workspaceId: string;
  read(): Promise<unknown>;
  save(binding: FirstCreationBinding): Promise<void>;
  /** Explicit read-only input observation; older hosts leave freshness unknown. */
  observeInputs?(taskIds: string[]): Promise<unknown>;
}

declare global {
  interface Window { __COPPERBENCH_FIRST_CREATION_HOST__?: NativeFirstCreationHost }
}

export function getFirstCreationPreferencesHost(): NativeFirstCreationHost | null {
  const host = typeof window === 'undefined' ? undefined : window.__COPPERBENCH_FIRST_CREATION_HOST__;
  return host && typeof host.workspaceFile === 'string' && typeof host.workspaceId === 'string'
    && typeof host.read === 'function' && typeof host.save === 'function' ? host : null;
}
