import { MockCoreBridge } from '../mock/mockBridge';
import type { CoreBridge } from './CoreBridge';
import {
  JcefCoreBridge,
  type JcefHostTransport,
  createBrowserJcefTransport
} from './JcefCoreBridge';

declare global {
  interface Window {
    copperbenchHost?: JcefHostTransport;
    __COPPERBENCH_HOST__?: JcefHostTransport;
    __COPPERBENCH_WORKSPACE_ID__?: string;
    __COPPERBENCH_QUERY_PREFIX__?: string;
    __COPPERBENCH_EMIT_EVENT__?: (eventJson: string) => void;
    cefQuery?: (options: {
      request: string;
      persistent?: boolean;
      onSuccess: (response: string) => void;
      onFailure: (errorCode: number, errorMessage: string) => void;
    }) => void;
  }
}

/**
 * Checks for a typed native JCEF host transport injected into the browser window.
 * Returns null in standard browser development and Playwright environments.
 */
export function getNativeHostTransport(): JcefHostTransport | null {
  if (typeof window === 'undefined') {
    return null;
  }
  if (isJcefHostTransport(window.copperbenchHost)) {
    return window.copperbenchHost;
  }
  if (isJcefHostTransport(window.__COPPERBENCH_HOST__)) {
    return window.__COPPERBENCH_HOST__;
  }
  if (typeof window.cefQuery === 'function' && window.__COPPERBENCH_WORKSPACE_ID__) {
    fallbackTransport ??= createBrowserJcefTransport(
        window.__COPPERBENCH_WORKSPACE_ID__,
        window.__COPPERBENCH_QUERY_PREFIX__ ?? 'copperbench:bridge:'
      );
    return fallbackTransport;
  }
  return null;
}

let fallbackTransport: JcefHostTransport | null = null;

function isJcefHostTransport(value: unknown): value is JcefHostTransport {
  if (!value || typeof value !== 'object') return false;
  const candidate = value as Partial<JcefHostTransport>;
  return (
    typeof candidate.workspaceId === 'string' &&
    candidate.workspaceId.length > 0 &&
    typeof candidate.invoke === 'function' &&
    typeof candidate.onEvent === 'function'
  );
}

/**
 * Returns true if a native JCEF host environment is detected.
 */
export function isNativeHostPresent(): boolean {
  if (typeof window === 'undefined') return false;
  return (
    isJcefHostTransport(window.copperbenchHost) ||
    isJcefHostTransport(window.__COPPERBENCH_HOST__) ||
    (typeof window.cefQuery === 'function' && Boolean(window.__COPPERBENCH_WORKSPACE_ID__))
  );
}

/**
 * Creates the active bridge instance: selects JcefCoreBridge if native host is present,
 * or MockCoreBridge for browser development and Playwright e2e tests.
 */
export function createCoreBridge(explicitHost?: JcefHostTransport): CoreBridge {
  const host = explicitHost ?? getNativeHostTransport();
  if (host) {
    return new JcefCoreBridge(host);
  }
  return new MockCoreBridge();
}

/**
 * The single bridge binding point for the product shell.
 */
export const coreBridge: CoreBridge = createCoreBridge();

export * from './CoreBridge';
export * from './JcefCoreBridge';
