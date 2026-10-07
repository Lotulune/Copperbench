import { LanguageSelector } from './LanguageSelector';
import React, { useEffect, useRef } from 'react';
import {
  Sun,
  Moon,
  Monitor,
  Minus,
  Square,
  Copy,
  X,
  ShieldCheck,
  Search
} from 'lucide-react';
import { useWorkbench } from '../context/WorkbenchContext';
import { OPEN_WORKBENCH_SEARCH_EVENT } from './workbenchNavigation';
import { uiText, useUiLocale } from '../i18n';
import productIcon from '../../../src/main/resources/net/mcreator/ui/res/icon.png';
import { hasUnsavedDrafts } from '../hooks/unsavedDraftGuard';
import {
  WINDOW_CHROME_SCHEMA_VERSION,
  WindowChromeRegion,
  WindowChromeRegionKind,
  WindowPointerGesture,
  windowBridge
} from '../bridge/windowBridge';

const toBounds = (rect: DOMRect): WindowChromeRegion['bounds'] => ({
  x: Math.round(rect.x * 100) / 100,
  y: Math.round(rect.y * 100) / 100,
  width: Math.round(rect.width * 100) / 100,
  height: Math.round(rect.height * 100) / 100
});

export const FramelessTitlebar: React.FC = () => {
  const locale = useUiLocale();
  const titlebarRef = useRef<HTMLElement>(null);
  const reportSequence = useRef(0);
  const {
    theme,
    themePreference,
    toggleTheme,
    isMaximized,
    toggleMaximize,
    systemFrameFallback,
    toggleSystemFrameFallback
  } = useWorkbench();

  const maximizedRef = useRef(isMaximized);
  maximizedRef.current = isMaximized;

  useEffect(() => {
    if (systemFrameFallback || !windowBridge.supportsChromeRegions) return;
    const root = document.documentElement;
    const captureTarget = titlebarRef.current ?? root;
    let captionPress = false;
    let pointerId: number | null = null;
    let latest: PointerEvent | null = null;
    let frame = 0;
    const send = (event: PointerEvent, phase: WindowPointerGesture['phase']) => {
      windowBridge.pointerGesture({ phase, x: event.clientX, y: event.clientY,
        screenX: event.screenX, screenY: event.screenY });
    };
    const resizeCursor = (event: PointerEvent) => {
      if (maximizedRef.current) return '';
      const left = event.clientX < 8, right = event.clientX >= window.innerWidth - 8;
      const top = event.clientY < 8, bottom = event.clientY >= window.innerHeight - 8;
      if ((left && top) || (right && bottom)) return 'nwse-resize';
      if ((left && bottom) || (right && top)) return 'nesw-resize';
      return left || right ? 'ew-resize' : top || bottom ? 'ns-resize' : '';
    };
    const onPointerDown = (event: PointerEvent) => {
      if (event.button !== 0 || !event.isPrimary) return;
      const element = event.target instanceof Element ? event.target : null;
      const edge = resizeCursor(event);
      const caption = !!element?.closest('[data-window-chrome-root]')
        && !element.closest('[data-window-chrome-kind], button, input, a, select, textarea, [role="button"]');
      if (!edge && !caption) return;
      captionPress = caption && !edge;
      event.stopPropagation();
      pointerId = event.pointerId;
      latest = event;
      captureTarget.setPointerCapture(pointerId);
      send(event, 'begin');
    };
    const onPointerMove = (event: PointerEvent) => {
      if (pointerId === null) {
        const cursor = resizeCursor(event);
        if (cursor) root.dataset.windowResizeCursor = cursor;
        else delete root.dataset.windowResizeCursor;
        return;
      }
      if (pointerId !== event.pointerId) return;
      latest = event;
      if (!frame) frame = window.requestAnimationFrame(() => {
        frame = 0;
        if (latest) send(latest, 'update');
      });
    };
    const finish = (event: PointerEvent) => {
      if (event.pointerId !== pointerId) return;
      window.cancelAnimationFrame(frame);
      frame = 0;
      send(event, event.type === 'pointerup' ? 'end' : 'cancel');
      const captured = pointerId;
      pointerId = null;
      latest = null;
      if (captureTarget.hasPointerCapture(captured)) captureTarget.releasePointerCapture(captured);
      delete root.dataset.windowResizeCursor;
    };
    const onDoubleClick = (event: MouseEvent) => {
      const element = event.target instanceof Element ? event.target : null;
      if (captionPress && element?.closest('[data-window-chrome-root]')
        && !element.closest('[data-window-chrome-kind], button, input, a, select, textarea, [role="button"]'))
        toggleMaximize();
    };
    document.addEventListener('pointerdown', onPointerDown, true);
    document.addEventListener('pointermove', onPointerMove, true);
    document.addEventListener('pointerup', finish, true);
    document.addEventListener('pointercancel', finish, true);
    captureTarget.addEventListener('lostpointercapture', finish);
    document.addEventListener('dblclick', onDoubleClick);
    return () => {
      document.removeEventListener('pointerdown', onPointerDown, true);
      document.removeEventListener('pointermove', onPointerMove, true);
      document.removeEventListener('pointerup', finish, true);
      document.removeEventListener('pointercancel', finish, true);
      captureTarget.removeEventListener('lostpointercapture', finish);
      document.removeEventListener('dblclick', onDoubleClick);
      window.cancelAnimationFrame(frame);
      if (latest) send(latest, 'cancel');
      if (pointerId !== null && captureTarget.hasPointerCapture(pointerId)) captureTarget.releasePointerCapture(pointerId);
      delete root.dataset.windowResizeCursor;
    };
  }, [systemFrameFallback, toggleMaximize]);


  useEffect(() => {
    const titlebar = titlebarRef.current;
    if (!titlebar || !windowBridge.supportsChromeRegions) return;

    let animationFrame = 0;
    const reportRegions = () => {
      animationFrame = 0;
      const regions: WindowChromeRegion[] = [];
      if (!systemFrameFallback) {
        regions.push({ id: 'titlebar', kind: 'caption', bounds: toBounds(titlebar.getBoundingClientRect()) });
        titlebar.querySelectorAll<HTMLElement>('[data-window-chrome-kind]').forEach((element) => {
          if (element.getClientRects().length === 0) return;
          regions.push({
            id: element.dataset.windowChromeId ?? element.dataset.testid ?? 'client-control',
            kind: element.dataset.windowChromeKind as WindowChromeRegionKind,
            bounds: toBounds(element.getBoundingClientRect())
          });
        });
      }

      windowBridge.reportChromeRegions({
        schemaVersion: WINDOW_CHROME_SCHEMA_VERSION,
        sequence: ++reportSequence.current,
        coordinateSpace: 'css_viewport',
        devicePixelRatio: window.devicePixelRatio,
        viewport: { width: window.innerWidth, height: window.innerHeight },
        regions
      });
    };
    const scheduleReport = () => {
      window.cancelAnimationFrame(animationFrame);
      animationFrame = window.requestAnimationFrame(reportRegions);
    };
    const resizeObserver = new ResizeObserver(scheduleReport);
    resizeObserver.observe(titlebar);
    titlebar.querySelectorAll<HTMLElement>('[data-window-chrome-kind]').forEach((element) => {
      resizeObserver.observe(element);
    });
    window.addEventListener('resize', scheduleReport);

    // Watch for devicePixelRatio changes across displays / system DPI changes
    let cleanupDpr: (() => void) | null = null;
    const bindDprWatcher = () => {
      if (typeof window.matchMedia !== 'function') return;
      const dpr = window.devicePixelRatio;
      const mediaQuery = window.matchMedia(`(resolution: ${dpr}dppx)`);
      const onDprChange = () => {
        scheduleReport();
        bindDprWatcher();
      };
      if (mediaQuery.addEventListener) {
        mediaQuery.addEventListener('change', onDprChange, { once: true });
        cleanupDpr = () => mediaQuery.removeEventListener('change', onDprChange);
      } else if (mediaQuery.addListener) {
        mediaQuery.addListener(onDprChange);
        cleanupDpr = () => mediaQuery.removeListener(onDprChange);
      }
    };
    bindDprWatcher();

    scheduleReport();

    return () => {
      resizeObserver.disconnect();
      window.removeEventListener('resize', scheduleReport);
      if (cleanupDpr) cleanupDpr();
      window.cancelAnimationFrame(animationFrame);
    };
  }, [systemFrameFallback, locale]);

  return (
    <header
      ref={titlebarRef}
      className="titlebar"
      data-testid="frameless-titlebar"
      data-window-chrome-root
      style={{ WebkitAppRegion: systemFrameFallback ? 'no-drag' : 'drag' } as React.CSSProperties}
    >
      {/* Left: Brand & Workspace Pill */}
      <div className="titlebar-left">
        <div className="titlebar-brand">
          <img src={productIcon} alt="" width={24} height={24} draggable={false} data-testid="product-brand-icon" />
          <span>Copperbench</span>
        </div>

      </div>
      <button type="button" className="workbench-command-trigger" data-testid="workbench-command-trigger"
        data-window-chrome-kind="client" data-window-chrome-id="command-search"
        onClick={() => window.dispatchEvent(new Event(OPEN_WORKBENCH_SEARCH_EVENT))}
        aria-keyshortcuts="Control+K Meta+K" aria-label={uiText('搜索元素与工具', 'Search elements and tools')}>
        <Search size={14} aria-hidden="true" /><span>{uiText('搜索元素与工具', 'Search elements and tools')}</span><kbd>{uiText('Ctrl K', 'Ctrl K')}</kbd>
      </button>

      {/* Right: Tools & Window Controls */}
      <div className="titlebar-tools">
        <LanguageSelector />
        <button
          type="button"
          className="btn-secondary titlebar-tool"
          onClick={toggleTheme}
          title={themePreference === 'system' ? uiText('当前跟随系统；切换到亮色主题', 'System theme; switch to light') : themePreference === 'light' ? uiText('当前亮色主题；切换到暗色主题', 'Light theme; switch to dark') : uiText('当前暗色主题；切换为跟随系统', 'Dark theme; follow system')}
          aria-label={themePreference === 'system' ? uiText('当前跟随系统；切换到亮色主题', 'System theme; switch to light') : themePreference === 'light' ? uiText('当前亮色主题；切换到暗色主题', 'Light theme; switch to dark') : uiText('当前暗色主题；切换为跟随系统', 'Dark theme; follow system')}
          data-testid="theme-toggle-btn"
          data-window-chrome-kind="client"
          data-window-chrome-id="theme"
        >
          {themePreference === 'system' ? <Monitor size={13} aria-hidden="true" /> : theme === 'dark' ? <Sun size={13} aria-hidden="true" /> : <Moon size={13} aria-hidden="true" />}
        </button>

        <button
          type="button"
          className={`btn-secondary titlebar-fallback${systemFrameFallback ? ' is-active' : ''}`}
          onClick={toggleSystemFrameFallback}
          disabled={!windowBridge.canToggleFrame}
          title={windowBridge.canToggleFrame ? uiText('切换系统窗口边框', 'Toggle system window frame') : uiText('当前使用系统窗口框架', 'Using the system window frame')}
          aria-label={uiText("切换系统窗口边框", "Toggle system window frame")}
          data-testid="system-fallback-toggle-btn"
          data-window-chrome-kind="client"
          data-window-chrome-id="system-frame-fallback"
        >
          <ShieldCheck size={13} aria-hidden="true" />
          <span className="titlebar-fallback-label">{systemFrameFallback ? uiText('系统窗口', 'System frame') : uiText('自绘窗口', 'Custom frame')}</span>
        </button>

        {/* Windows Standard Frameless Window Buttons (Hidden when systemFrameFallback is true) */}
        {!systemFrameFallback && (
          <div className="titlebar-window-controls">
            <button
              type="button"
              className="titlebar-window-button"
              onClick={() => windowBridge.minimize()}
              title={uiText('最小化', 'Minimize')}
              aria-label={uiText('最小化', 'Minimize')}
              data-testid="window-minimize-btn"
              data-window-chrome-kind="minimize"
              data-window-chrome-id="minimize"
            >
              <Minus size={13} aria-hidden="true" />
            </button>

            <button
              type="button"
              className="titlebar-window-button"
              onClick={toggleMaximize}
              title={isMaximized ? uiText('恢复', 'Restore') : uiText('最大化', 'Maximize')}
              aria-label={isMaximized ? uiText('恢复', 'Restore') : uiText('最大化', 'Maximize')}
              data-testid="window-maximize-btn"
              data-window-chrome-kind="maximize"
              data-window-chrome-id="maximize"
            >
              {isMaximized ? <Copy size={12} aria-hidden="true" /> : <Square size={12} aria-hidden="true" />}
            </button>

            <button
              type="button"
              className="titlebar-window-button titlebar-close-button"
              onClick={() => {
                if (windowBridge.canGuardUnsavedChanges || !hasUnsavedDrafts() || window.confirm(uiText('有未保存的修改。关闭窗口并放弃这些草稿？', 'There are unsaved changes. Close the window and discard these drafts?'))) windowBridge.close();
              }}
              title={uiText('关闭', 'Close')}
              aria-label={uiText('关闭', 'Close')}
              data-testid="window-close-btn"
              data-window-chrome-kind="client"
              data-window-chrome-id="close"
            >
              <X size={14} aria-hidden="true" />
            </button>
          </div>
        )}
      </div>
    </header>
  );
};
