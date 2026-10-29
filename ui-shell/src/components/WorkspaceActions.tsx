import React, { useEffect, useRef, useState } from 'react';
import { Cog, DatabaseZap, Ellipsis, FileArchive, FolderOpen, Hammer, Play, Server, TestTube2 } from 'lucide-react';
import { useWorkbench } from '../context/WorkbenchContext';
import { workspaceOpenBridge } from '../bridge/workspaceOpenBridge';
import { uiText, uiMessage } from '../i18n';
import { confirmAction, showNotice } from '../feedback/feedback';
import { isNativeHostPresent } from '../bridge';
import { useFirstCreation } from '../onboarding/FirstCreationContext';

export const WorkspaceActions: React.FC = () => {
  const { state, generateWorkspace, buildWorkspace, runClient, runServer, runDatagen, runGameTest } = useWorkbench();
  const { openDelivery } = useFirstCreation();
  const [openingFolder, setOpeningFolder] = useState(false);
  const menu = useRef<HTMLDetailsElement>(null);
  useEffect(() => {
    const dismiss = (event: PointerEvent) => {
      if (event.target instanceof Node && menu.current && !menu.current.contains(event.target)) menu.current.open = false;
    };
    const escape = (event: KeyboardEvent) => {
      if (event.key !== 'Escape' || !menu.current?.open) return;
      menu.current.open = false;
      menu.current.querySelector('summary')?.focus();
    };
    document.addEventListener('pointerdown', dismiss);
    document.addEventListener('keydown', escape);
    return () => { document.removeEventListener('pointerdown', dismiss); document.removeEventListener('keydown', escape); };
  }, []);
  const openFolder = async () => {
    if (openingFolder) return;
    setOpeningFolder(true);
    try { await workspaceOpenBridge.openBuildFolder(); }
    catch (error) {
      showNotice({ tone: 'error', message: (error as { code?: number })?.code === 404
        ? uiMessage('尚无 JAR 文件，请先构建工作区。', 'No JAR files yet. Build the workspace first.')
        : uiMessage('无法打开文件夹，请检查系统文件管理器。', 'Could not open the folder. Check the system file manager.') });
    } finally { setOpeningFolder(false); }
  };
  return <div className="workspace-actions" aria-label={uiText('构建与运行', 'Build and run')}>
    <button type="button" className="workspace-action" data-testid="titlebar-build-btn"
      aria-label={uiText('构建工作区', 'Build workspace')} onClick={() => void buildWorkspace()}>
      <Hammer size={14} aria-hidden="true" /><span>{uiText('构建', 'Build')}</span>
    </button>
    <button type="button" className="workspace-action" data-testid="titlebar-run-btn"
      aria-label={uiText('运行测试客户端', 'Run test client')} onClick={() => void runClient()}>
      <Play size={14} aria-hidden="true" /><span>{uiText('运行', 'Run')}</span>
    </button>
    <details ref={menu} className="workspace-actions-menu">
      <summary data-testid="compact-run-menu" aria-label={uiText('更多运行选项', 'More run options')} title={uiText('更多运行选项', 'More run options')}>
        <Ellipsis size={17} aria-hidden="true" />
      </summary>
      <div className="workspace-actions-popover" onClick={() => { if (menu.current) menu.current.open = false; }}>
        <button type="button" data-testid="titlebar-generate-btn" aria-label={uiText('生成工作区源码', 'Generate workspace sources')} onClick={() => void generateWorkspace()}>
          <Cog size={15} aria-hidden="true" />{uiText('生成源码', 'Generate sources')}
        </button>
        <button type="button" data-testid="compact-export-btn" disabled={!state.workbench || !isNativeHostPresent()}
          title={!isNativeHostPresent() ? uiText('在桌面应用中导出本机工作区', 'Export a local workspace in the desktop app') : undefined}
          onClick={() => { menu.current?.querySelector('summary')?.focus(); openDelivery(); }}><FileArchive size={15} aria-hidden="true" />{uiText('交付与导出', 'Delivery & export')}</button>
        <button type="button" data-testid="compact-open-jar-folder-btn" aria-label={uiText('打开 JAR 文件夹', 'Open JAR folder')}
          disabled={!state.workbench || !workspaceOpenBridge.buildFolderAvailable || openingFolder} aria-busy={openingFolder}
          title={workspaceOpenBridge.buildFolderAvailable ? uiText('打开 JAR 文件夹', 'Open JAR folder') : uiText('需要桌面版本', 'Requires the desktop app')}
          onClick={() => void openFolder()}><FolderOpen size={15} aria-hidden="true" />{uiText('JAR 文件夹', 'JAR folder')}</button>
        <button type="button" aria-label={uiText('运行隔离专用服务端', 'Run isolated dedicated server')} onClick={async () => {
          if (await confirmAction({ title: uiMessage('运行专用服务端', 'Run dedicated server'),
            message: uiMessage('服务端将在隔离测试目录启动。继续即接受 Minecraft EULA：https://aka.ms/MinecraftEULA', 'The server will start in an isolated test directory. Continuing accepts the Minecraft EULA: https://aka.ms/MinecraftEULA'),
            confirmLabel: uiMessage('接受并启动', 'Accept and start') })) void runServer(true);
        }}><Server size={15} aria-hidden="true" />{uiText('专用服务端', 'Dedicated server')}</button>
        <button type="button" aria-label={uiText('在暂存区运行数据生成', 'Run staged data generation')} onClick={() => void runDatagen()}>
          <DatabaseZap size={15} aria-hidden="true" />{uiText('数据生成', 'Data generation')}</button>
        <button type="button" aria-label={uiText('运行已有 GameTest', 'Run existing GameTests')} onClick={() => void runGameTest()}>
          <TestTube2 size={15} aria-hidden="true" />{uiText('GameTest', 'GameTest')}</button>
      </div>
    </details>
  </div>;
};
