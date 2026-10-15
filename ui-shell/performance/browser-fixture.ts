import type { Page } from '@playwright/test';

export interface PerformanceRequest {
  operation: string; status: string; bridgeDurationMs: number;
  projectionCounts?: { nodes?: number; edges?: number; diagnostics?: number; assets?: number; elements?: number };
}
export interface PerformanceWindow {
  requests: PerformanceRequest[];
  timing?: {
    receivedAt: number | null; feedbackAt: number | null; readyAt: number | null;
    requestOffset: number; cancel: () => void;
  };
}
declare global { interface Window { __copperbenchPerformance: PerformanceWindow; } }

export async function installPerformanceFixture(page: Page, fixture: any, locale: 'zh' | 'en') {
  await page.addInitScript(({ fixture, locale }) => {
    window.localStorage.setItem('copperbench.ui.locale', locale);
    window.localStorage.setItem('copperbench.theme', 'light');
    window.__copperbenchPerformance = { requests: [] };
    const modulePath = '/src/mock/mockBridge.ts';
    const ready = import(modulePath).then(({ MockCoreBridge }) => {
      const core = new MockCoreBridge();
      const state = core.getState();
      state.elements = fixture.elements;
      state.elementEditors = {};
      state.workbench.workspace.name = fixture.workspaceName;
      state.workbench.elementCounts = { total: fixture.elements.length, valid: fixture.elements.length,
        invalid: 0, draft: 0, unsupported: 0 };
      state.workbench.recentElements = fixture.elements.slice(0, 5);
      return core;
    });
    window.copperbenchHost = {
      workspaceId: '11111111-1111-4111-8111-111111111111', onEvent() { return () => {}; },
      async invoke(raw) {
        const core = await ready;
        const request = JSON.parse(raw);
        if (request.messageType === 'command') throw new Error('Performance fixtures permit queries and handshake only.');
        const start = performance.now();
        const result = request.messageType === 'handshake' ? await core.negotiateHandshake(request) : await core.sendQuery(request);
        let projectionCounts: PerformanceRequest['projectionCounts'];
        if (request.operation === 'list_mod_elements') {
          result.data = { ...result.data, items: fixture.elements, total: fixture.elements.length,
            page: 1, pageSize: fixture.elements.length, nextCursor: null };
          projectionCounts = { elements: result.data.items.length };
        }
        if (request.operation === 'get_workspace_references') {
          result.data = fixture.references;
          projectionCounts = { nodes: result.data.nodes.length, edges: result.data.edges.length,
            diagnostics: result.data.diagnostics.length };
        }
        if (request.operation === 'list_assets') {
          result.data = fixture.assetProjection;
          projectionCounts = { assets: result.data.assets.length, diagnostics: result.data.diagnostics.length };
        }
        if (request.operation === 'get_workspace_health') {
          const diagnostics = fixture.references.diagnostics;
          result.data = { ...result.data,
            elements: { total: fixture.elements.length, valid: fixture.elements.length, invalid: 0, draft: 0, unsupported: 0 },
            diagnostics: { total: diagnostics.length, error: diagnostics.length, warning: 0, info: 0 },
            references: { edgeCount: fixture.references.edges.length, danglingCount: diagnostics.length, diagnostics },
            assets: { indexed: true, summary: fixture.assetProjection.health, diagnostics: [] } };
        }
        if (request.operation === 'get_asset_preview') {
          const asset = fixture.assetProjection.assets.find((candidate: any) => candidate.id === request.payload?.assetId);
          if (!asset || asset.sha256 !== request.payload?.expectedSha256) throw new Error('Synthetic asset preview identity mismatch.');
          const image = { assetId: asset.id, relativePath: asset.relativePath, sha256: asset.sha256,
            mediaType: asset.mediaType, size: asset.size, base64: fixture.assetContents[asset.id], width: 1, height: 1 };
          result.status = 'succeeded';
          result.data = { ...image, schemaVersion: '1.0', kind: 'image', image, textures: [] };
          result.diagnostics = [];
        }
        const serialized = JSON.stringify(result);
        window.__copperbenchPerformance.requests.push({ operation: request.operation ?? 'handshake', status: result.status ?? 'negotiated',
          bridgeDurationMs: performance.now() - start, ...(projectionCounts ? { projectionCounts } : {}) });
        return serialized;
      },
    };
  }, { fixture, locale });
}

export interface Condition {
  selector: string; value?: string; attribute?: [string, string]; countSelector?: string; count?: number;
  focused?: boolean; textSelector?: string; textIncludes?: string;
}

export async function armTiming(page: Page, inputSelector: string, event: 'click' | 'input', feedback: Condition, ready: Condition) {
  await page.locator(inputSelector).evaluate((element, { event, feedback, ready }) => {
    const state = window.__copperbenchPerformance;
    state.timing?.cancel();
    let frame = 0;
    let settledFrames = 0;
    const timing = { receivedAt: null as number | null, feedbackAt: null as number | null, readyAt: null as number | null,
      requestOffset: state.requests.length, cancel: () => { cancelAnimationFrame(frame); element.removeEventListener(event, receive, true); } };
    const matches = (condition: Condition) => {
      const target = document.querySelector(condition.selector);
      if (!(target instanceof Element) || target.getClientRects().length === 0 || getComputedStyle(target).visibility === 'hidden') return false;
      if ('disabled' in target && target.disabled) return false;
      if (condition.value !== undefined && (!(target instanceof HTMLInputElement) || target.value !== condition.value)) return false;
      if (condition.attribute && target.getAttribute(condition.attribute[0]) !== condition.attribute[1]) return false;
      if (condition.countSelector && document.querySelectorAll(condition.countSelector).length !== condition.count) return false;
      if (condition.textSelector && !document.querySelector(condition.textSelector)?.textContent?.includes(condition.textIncludes!)) return false;
      return !condition.focused || document.activeElement === target;
    };
    function observe() {
      if (timing.receivedAt === null) return;
      if (timing.feedbackAt === null && matches(feedback)) timing.feedbackAt = performance.now();
      settledFrames = matches(ready) ? settledFrames + 1 : 0;
      if (settledFrames >= 2) { timing.readyAt = performance.now(); return; }
      frame = requestAnimationFrame(observe);
    }
    function receive() {
      if (timing.receivedAt !== null) return;
      timing.receivedAt = performance.now(); frame = requestAnimationFrame(observe);
    }
    state.timing = timing;
    element.addEventListener(event, receive, true);
  }, { event, feedback, ready });
}

export async function finishTiming(page: Page) {
  await page.waitForFunction(() => window.__copperbenchPerformance.timing?.readyAt !== null);
  return page.evaluate(() => {
    const state = window.__copperbenchPerformance;
    const timing = state.timing!;
    timing.cancel();
    const memory = (performance as Performance & { memory?: { usedJSHeapSize: number; totalJSHeapSize: number } }).memory;
    return { browserInputToReadyMs: timing.receivedAt === null || timing.readyAt === null ? null : timing.readyAt - timing.receivedAt,
      feedbackMs: timing.receivedAt === null || timing.feedbackAt === null ? null : timing.feedbackAt - timing.receivedAt,
      requests: state.requests.slice(timing.requestOffset),
      memory: memory ? { usedJSHeapBytes: memory.usedJSHeapSize, allocatedJSHeapBytes: memory.totalJSHeapSize } : null };
  });
}
