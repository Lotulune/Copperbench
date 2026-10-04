import { LanguageSelector } from './LanguageSelector';
import React, { useEffect, useRef, useState } from 'react';
import {
  Cog,
  Hammer,
  FolderOpen,
  Play,
  Sun,
  Moon,
  Monitor,
  Minus,
  Square,
  Copy,
  X,
  Layers,
  ShieldCheck,
  Server,
  DatabaseZap,
  TestTube2,
  Ellipsis
} from 'lucide-react';
import { useWorkbench } from '../context/WorkbenchContext';
import { workspaceOpenBridge } from '../bridge/workspaceOpenBridge';
import { uiText, useUiLocale } from '../i18n';
import productIcon from '../../../src/main/resources/net/mcreator/ui/res/icon.png';
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
  const compactActionsRef = useRef<HTMLDetailsElement>(null);
  const [compactActionsOpen, setCompactActionsOpen] = useState(false);

  useEffect(() => {
    const dismiss = (event: PointerEvent) => {
      if (event.target instanceof Node && compactActionsRef.current && !compactActionsRef.current.contains(event.target)) compactActionsRef.current.open = false;
    };
    const escape = (event: KeyboardEvent) => {
      if (event.key !== 'Escape' || !compactActionsRef.current?.open) return;
      compactActionsRef.current.open = false;
      compactActionsRef.current?.querySelector('summary')?.focus();
    };
    const resize = () => { if (compactActionsRef.current) compactActionsRef.current.open = false; };
    document.addEventListener('pointerdown', dismiss);
    document.addEventListener('keydown', escape);
    window.addEventListener('resize', resize);
    return () => {
      document.removeEventListener('pointerdown', dismiss);
      document.removeEventListener('keydown', escape);
      window.removeEventListener('resize', resize);
    };
  }, []);
  const {
    state,
    theme,
    themePreference,
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
  const [openingBuildFolder, setOpeningBuildFolder] = useState(false);
  const openBuildFolder = async () => {
    if (openingBuildFolder) return;
    setOpeningBuildFolder(true);
    try {
      await workspaceOpenBridge.openBuildFolder();
    } catch (error) {
      window.alert((error as { code?: number })?.code === 404
        ? uiText('尚未生成 JAR 输出文件夹，请先构建工作区。', 'The JAR output folder does not exist yet. Build the workspace first.')
        : uiText('无法打开 JAR 文件夹，请检查系统文件管理器是否可用。', 'Could not open the JAR folder. Check that your system file manager is available.'));
    } finally {
      setOpeningBuildFolder(false);
    }
  };

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
  }, [generator?.displayName, systemFrameFallback, workspace?.id, workspace?.name, workspace?.revision, locale, compactActionsOpen]);

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

        {workspace && (
          <div
            className="titlebar-workspace"
            title={`${workspace.name}, ${uiText('修订', 'revision')} ${workspace.revision}${generator ? `, ${generator.displayName}` : ''}`}
            data-testid="titlebar-workspace"
          >
            <Layers size={12} color="var(--text-muted)" aria-hidden="true" />
            <span className="titlebar-workspace-name">{workspace.name}</span>
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
          title={`${uiText('生成工作区源码', 'Generate workspace sources')}${generator ? ` (${generator.displayName})` : ''}`}
          aria-label={uiText("生成工作区源码", "Generate workspace sources")}
          data-testid="titlebar-generate-btn"
          data-window-chrome-kind="client"
          data-window-chrome-id="generate"
        >
          <Cog size={13} aria-hidden="true" />
          <span className="titlebar-action-label">{uiText('生成', 'Generate')}</span>
        </button>

        <button
          type="button"
          className="btn-primary titlebar-action"
          onClick={() => buildWorkspace()}
          title={`${uiText('构建工作区', 'Build workspace')}${generator ? ` (${generator.displayName})` : ''}`}
          aria-label={uiText("构建工作区", "Build workspace")}
          data-testid="titlebar-build-btn"
          data-window-chrome-kind="client"
          data-window-chrome-id="build"
        >
          <Hammer size={13} aria-hidden="true" />
          <span className="titlebar-action-label">{uiText('构建', 'Build')}</span>
        </button>

        <button
          type="button"
          className="btn-secondary titlebar-action"
          onClick={() => void openBuildFolder()}
          disabled={!workspace || !workspaceOpenBridge.buildFolderAvailable || openingBuildFolder}
          title={workspaceOpenBridge.buildFolderAvailable
            ? uiText('打开当前工作区的 JAR 输出文件夹（build/libs）', 'Open the current workspace JAR output folder (build/libs)')
            : uiText('打开 JAR 文件夹需要支持此功能的桌面版本', 'Opening the JAR folder requires a supported desktop version')}
          aria-label={uiText('打开 JAR 文件夹', 'Open JAR folder')}
          aria-busy={openingBuildFolder}
          data-testid="titlebar-open-jar-folder-btn"
          data-window-chrome-kind="client"
          data-window-chrome-id="open-jar-folder"
        >
          <FolderOpen size={13} aria-hidden="true" />
          <span className="titlebar-action-label">{uiText('JAR 文件夹', 'JAR folder')}</span>
        </button>

        <button
          type="button"
          className="btn-secondary titlebar-action"
          onClick={() => runClient()}
          title={uiText('运行 Minecraft 测试客户端', 'Run the Minecraft test client')}
          aria-label={uiText("运行测试客户端", "Run test client")}
          data-testid="titlebar-run-btn"
          data-window-chrome-kind="client"
          data-window-chrome-id="run-client"
        >
          <Play size={13} aria-hidden="true" />
          <span className="titlebar-action-label">{uiText('测试客户端', 'Test client')}</span>
        </button>

        <button
          type="button"
          className="btn-secondary titlebar-tool"
          onClick={() => {
            const accepted = window.confirm(uiText('仅在隔离测试目录启动专用服务端。确认接受 Minecraft EULA 并继续？', 'Start a dedicated server in the isolated test directory. Accept the Minecraft EULA and continue?'));
            if (accepted) void runServer(true);
          }}
          title={uiText('运行隔离专用服务端', 'Run isolated dedicated server')}
          aria-label={uiText('运行隔离专用服务端', 'Run isolated dedicated server')}
          data-window-chrome-kind="client"
          data-window-chrome-id="run-server"
        >
          <Server size={13} aria-hidden="true" />
        </button>
        <button
          type="button"
          className="btn-secondary titlebar-tool"
          onClick={() => void runDatagen()}
          title={uiText('在暂存区运行数据生成', 'Run staged data generation')}
          aria-label={uiText('在暂存区运行数据生成', 'Run staged data generation')}
          data-window-chrome-kind="client"
          data-window-chrome-id="run-datagen"
        >
          <DatabaseZap size={13} aria-hidden="true" />
        </button>
        <button
          type="button"
          className="btn-secondary titlebar-tool"
          onClick={() => void runGameTest()}
          title={uiText('运行已有 GameTest', 'Run existing GameTests')}
          aria-label={uiText('运行已有 GameTest', 'Run existing GameTests')}
          data-window-chrome-kind="client"
          data-window-chrome-id="run-gametest"
        >
          <TestTube2 size={13} aria-hidden="true" />
        </button>
        <details className="compact-run-actions" ref={compactActionsRef}
          onToggle={event => setCompactActionsOpen(event.currentTarget.open)}>
          <summary title={uiText('更多运行选项', 'More run options')} aria-label={uiText('更多运行选项', 'More run options')}
            data-window-chrome-kind="client" data-window-chrome-id="compact-run-menu" data-testid="compact-run-menu">
            <Ellipsis size={17} aria-hidden="true" />
          </summary>
          <div className="compact-run-popover" data-window-chrome-kind="client" data-window-chrome-id="compact-run-popover"
            onClick={() => { if (compactActionsRef.current) compactActionsRef.current.open = false; }}>
              <button
                type="button"
                className="compact-run-action"
                onClick={() => void openBuildFolder()}
                disabled={!workspace || !workspaceOpenBridge.buildFolderAvailable || openingBuildFolder}
                title={workspaceOpenBridge.buildFolderAvailable
                  ? uiText('打开当前工作区的 JAR 输出文件夹（build/libs）', 'Open the current workspace JAR output folder (build/libs)')
                  : uiText('打开 JAR 文件夹需要支持此功能的桌面版本', 'Opening the JAR folder requires a supported desktop version')}
                aria-label={uiText('打开 JAR 文件夹', 'Open JAR folder')}
                aria-busy={openingBuildFolder}
                data-testid="compact-open-jar-folder-btn"
                data-window-chrome-kind="client"
                data-window-chrome-id="compact-open-jar-folder"
              >
                <FolderOpen size={13} aria-hidden="true" />
                <span>{uiText('JAR 文件夹', 'JAR folder')}</span>
              </button>
              <button
                type="button"
                className="compact-run-action"
                onClick={() => {
                  const accepted = window.confirm(uiText('仅在隔离测试目录启动专用服务端。确认接受 Minecraft EULA 并继续？', 'Start a dedicated server in the isolated test directory. Accept the Minecraft EULA and continue?'));
                  if (accepted) void runServer(true);
                }}
                title={uiText('运行隔离专用服务端', 'Run isolated dedicated server')}
                aria-label={uiText('运行隔离专用服务端', 'Run isolated dedicated server')}
                data-window-chrome-kind="client"
                data-window-chrome-id="compact-run-server"
              >
                <Server size={15} aria-hidden="true" /><span>{uiText("专用服务端", "Dedicated server")}</span>
              </button>
              <button
                type="button"
                className="compact-run-action"
                onClick={() => void runDatagen()}
                title={uiText('在暂存区运行数据生成', 'Run staged data generation')}
                aria-label={uiText('在暂存区运行数据生成', 'Run staged data generation')}
                data-window-chrome-kind="client"
                data-window-chrome-id="compact-run-datagen"
              >
                <DatabaseZap size={15} aria-hidden="true" /><span>{uiText("数据生成", "Data generation")}</span>
              </button>
              <button
                type="button"
                className="compact-run-action"
                onClick={() => void runGameTest()}
                title={uiText('运行已有 GameTest', 'Run existing GameTests')}
                aria-label={uiText('运行已有 GameTest', 'Run existing GameTests')}
                data-window-chrome-kind="client"
                data-window-chrome-id="compact-run-gametest"
              >
                <TestTube2 size={15} aria-hidden="true" /><span>{uiText("GameTest", "GameTest")}</span>
              </button>
          </div>
        </details>
      </div>

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
              onClick={() => windowBridge.close()}
              title={uiText('关闭', 'Close')}
              aria-label={uiText('关闭', 'Close')}
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
