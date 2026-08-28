import React, { useEffect, useRef } from 'react';
import {
  Cog,
  Hammer,
  Play,
  Sun,
  Moon,
  Minus,
  Square,
  Copy,
  X,
  Layers,
  Sparkles,
  ShieldCheck,
  Server,
  DatabaseZap,
  TestTube2
} from 'lucide-react';
import { useWorkbench } from '../context/WorkbenchContext';
import {
  WINDOW_CHROME_SCHEMA_VERSION,
  WindowChromeRegion,
  WindowChromeRegionKind,
  windowBridge
} from '../bridge/windowBridge';

const toBounds = (rect: DOMRect): WindowChromeRegion['bounds'] => ({
  x: Math.round(rect.x * 100) / 100,
  y: Math.round(rect.y * 100) / 100,
  width: Math.round(rect.width * 100) / 100,
  height: Math.round(rect.height * 100) / 100
});

export const FramelessTitlebar: React.FC = () => {
  const titlebarRef = useRef<HTMLElement>(null);
  const reportSequence = useRef(0);
  const {
    state,
    theme,
    toggleTheme,
    isMaximized,
    toggleMaximize,
    systemFrameFallback,
    toggleSystemFrameFallback,
    generateWorkspace,
    buildWorkspace,
    runClient,
    runServer,
    runDatagen,
    runGameTest
  } = useWorkbench();

  const workspace = state.workbench?.workspace;
  const generator = workspace?.generator;

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
  }, [generator?.displayName, systemFrameFallback, workspace?.id, workspace?.name, workspace?.revision]);

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
          <Sparkles size={16} aria-hidden="true" />
          <span>Copperbench</span>
        </div>

        {workspace && (
          <div
            className="titlebar-workspace"
            title={`${workspace.name}，修订 ${workspace.revision}${generator ? `，${generator.displayName}` : ''}`}
            data-testid="titlebar-workspace"
          >
            <Layers size={12} color="var(--text-muted)" aria-hidden="true" />
            <span className="titlebar-workspace-name">{workspace.name}</span>
            <span className="badge badge-copper titlebar-revision">
              修订 {workspace.revision}
            </span>
            {generator && (
              <span className="badge badge-blue titlebar-generator">
                {generator.displayName}
              </span>
            )}
          </div>
        )}
      </div>

      {/* Middle: Fast Action Controls */}
      <div className="titlebar-actions">
        <button
          type="button"
          className="btn-secondary titlebar-action"
          onClick={() => generateWorkspace()}
          title={`生成工作区源码${generator ? `（${generator.displayName}）` : ''}`}
          data-testid="titlebar-generate-btn"
          data-window-chrome-kind="client"
          data-window-chrome-id="generate"
        >
          <Cog size={13} aria-hidden="true" />
          <span className="titlebar-action-label">生成</span>
        </button>

        <button
          type="button"
          className="btn-primary titlebar-action"
          onClick={() => buildWorkspace()}
          title={`构建工作区${generator ? `（${generator.displayName}）` : ''}`}
          data-testid="titlebar-build-btn"
          data-window-chrome-kind="client"
          data-window-chrome-id="build"
        >
          <Hammer size={13} aria-hidden="true" />
          <span className="titlebar-action-label">构建</span>
        </button>

        <button
          type="button"
          className="btn-secondary titlebar-action"
          onClick={() => runClient()}
          title="运行 Minecraft 测试客户端"
          data-testid="titlebar-run-btn"
          data-window-chrome-kind="client"
          data-window-chrome-id="run-client"
        >
          <Play size={13} aria-hidden="true" />
          <span className="titlebar-action-label">测试客户端</span>
        </button>

        <button
          type="button"
          className="btn-secondary titlebar-tool"
          onClick={() => {
            const accepted = window.confirm('仅在隔离测试目录启动专用服务端。确认接受 Minecraft EULA 并继续？');
            if (accepted) void runServer(true);
          }}
          title="运行隔离专用服务端"
          aria-label="运行隔离专用服务端"
          data-window-chrome-kind="client"
          data-window-chrome-id="run-server"
        >
          <Server size={13} aria-hidden="true" />
        </button>
        <button
          type="button"
          className="btn-secondary titlebar-tool"
          onClick={() => void runDatagen()}
          title="在暂存区运行数据生成"
          aria-label="在暂存区运行数据生成"
          data-window-chrome-kind="client"
          data-window-chrome-id="run-datagen"
        >
          <DatabaseZap size={13} aria-hidden="true" />
        </button>
        <button
          type="button"
          className="btn-secondary titlebar-tool"
          onClick={() => void runGameTest()}
          title="运行已有 GameTest"
          aria-label="运行已有 GameTest"
          data-window-chrome-kind="client"
          data-window-chrome-id="run-gametest"
        >
          <TestTube2 size={13} aria-hidden="true" />
        </button>
      </div>

      {/* Right: Tools & Window Controls */}
      <div className="titlebar-tools">
        <button
          type="button"
          className="btn-secondary titlebar-tool"
          onClick={toggleTheme}
          title={theme === 'dark' ? '切换到亮色主题' : '切换到暗色主题'}
          aria-label={theme === 'dark' ? '切换到亮色主题' : '切换到暗色主题'}
          data-testid="theme-toggle-btn"
          data-window-chrome-kind="client"
          data-window-chrome-id="theme"
        >
          {theme === 'dark' ? <Sun size={13} aria-hidden="true" /> : <Moon size={13} aria-hidden="true" />}
        </button>

        <button
          type="button"
          className={`btn-secondary titlebar-fallback${systemFrameFallback ? ' is-active' : ''}`}
          onClick={toggleSystemFrameFallback}
          disabled={!windowBridge.canToggleFrame}
          title={windowBridge.canToggleFrame ? '切换系统窗口框架回退（NFR-UI-06）' : '当前使用系统窗口框架'}
          data-testid="system-fallback-toggle-btn"
          data-window-chrome-kind="client"
          data-window-chrome-id="system-frame-fallback"
        >
          <ShieldCheck size={13} aria-hidden="true" />
          <span className="titlebar-fallback-label">{systemFrameFallback ? '系统窗口' : '自绘窗口'}</span>
        </button>

        {/* Windows Standard Frameless Window Buttons (Hidden when systemFrameFallback is true) */}
        {!systemFrameFallback && (
          <div className="titlebar-window-controls">
            <button
              type="button"
              className="titlebar-window-button"
              onClick={() => windowBridge.minimize()}
              title="最小化"
              aria-label="最小化"
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
              title={isMaximized ? '恢复' : '最大化'}
              aria-label={isMaximized ? '恢复' : '最大化'}
              data-testid="window-maximize-btn"
              data-window-chrome-kind="maximize"
              data-window-chrome-id="maximize"
            >
              {isMaximized ? <Copy size={12} aria-hidden="true" /> : <Square size={12} aria-hidden="true" />}
            </button>

            <button
              type="button"
              className="titlebar-window-button titlebar-close-button"
              onClick={() => windowBridge.close()}
              title="关闭"
              aria-label="关闭"
              data-testid="window-close-btn"
              data-window-chrome-kind="close"
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
