import { useRef, useState } from 'react';
import { ArrowRight, Bot, Flag, FolderOpen, X } from 'lucide-react';
import { isNativeHostPresent } from '../bridge';
import { mcpRuntimeBridge } from '../bridge/mcpRuntimeBridge';
import { workspaceOpenBridge } from '../bridge/workspaceOpenBridge';
import { useWorkbench } from '../context/WorkbenchContext';
import { useDialogA11y } from '../hooks/useDialogA11y';
import { renderUiMessage, uiMessage, uiText, type UiMessage } from '../i18n';
import { useFirstCreation } from '../onboarding/FirstCreationContext';

export function WorkspaceEntryActions() {
  const { setActiveView } = useWorkbench();
  const { openGuide, currentBinding, openWorkspaceOpen } = useFirstCreation();
  return <section className="workspace-entry-actions" aria-label={uiText('开始创作', 'Start creating')} data-testid="workspace-entry-actions">
    <button type="button" data-testid="hub-first-creation" onClick={() => openGuide()}><Flag size={18} aria-hidden="true" />
      <span><strong>{currentBinding ? uiText('继续第一件作品', 'Continue first creation') : uiText('第一件作品', 'First creation')}</strong><small>{uiText('从真实方块到游戏检查与交付', 'From a real block to game checks and delivery')}</small></span><ArrowRight size={14} aria-hidden="true" /></button>
    <button type="button" data-testid="hub-open-workspace" disabled={!workspaceOpenBridge.available} title={!workspaceOpenBridge.available ? uiText('在桌面应用中打开本机工作区', 'Open a local workspace in the desktop app') : undefined} onClick={openWorkspaceOpen}><FolderOpen size={18} aria-hidden="true" />
      <span><strong>{uiText('打开已有工作区', 'Open existing workspace')}</strong><small>{uiText('从原工程继续编辑', 'Keep editing the original project')}</small></span><ArrowRight size={14} aria-hidden="true" /></button>
    <button type="button" data-testid="hub-connect-agent" disabled={!mcpRuntimeBridge.available} title={!mcpRuntimeBridge.available ? uiText('在桌面应用的 AI 与 MCP 中连接', 'Connect through AI & MCP in the desktop app') : undefined} onClick={() => setActiveView('ai')}><Bot size={18} aria-hidden="true" />
      <span><strong>{uiText('连接外部 Agent', 'Connect external Agent')}</strong><small>{uiText('打开现有 AI 与 MCP 入口', 'Open the existing AI & MCP controls')}</small></span><ArrowRight size={14} aria-hidden="true" /></button>
    {!isNativeHostPresent() && <p role="status">{uiText('本机工作区与 Agent 连接请使用桌面应用。', 'Use the desktop app for local workspaces and Agent connections.')}</p>}
  </section>;
}

export function OpenWorkspaceDialog() {
  const { workspaceOpen, closeWorkspaceOpen } = useFirstCreation();
  const [file, setFile] = useState('');
  const [busy, setBusy] = useState(false);
  const [requested, setRequested] = useState(false);
  const [message, setMessage] = useState<UiMessage | null>(null);
  const submitting = useRef(false);
  const dialogRef = useDialogA11y(workspaceOpen, closeWorkspaceOpen);
  if (!workspaceOpen) return null;
  return <div className="modal-overlay"><div ref={dialogRef} className="modal-card first-creation-guide open-workspace-dialog" role="dialog" aria-modal="true" aria-labelledby="open-workspace-title" data-testid="open-workspace-dialog">
    <header className="modal-header"><h2 id="open-workspace-title">{uiText('打开已有工作区', 'Open existing workspace')}</h2>
      <button type="button" onClick={closeWorkspaceOpen} aria-label={uiText('关闭打开工作区窗口', 'Close open-workspace dialog')}><X size={18} aria-hidden="true" /></button></header>
    <form className="modal-body first-creation-content" onSubmit={event => {
      event.preventDefault();
      if (submitting.current || !workspaceOpenBridge.available || requested || !file.trim().endsWith('.mcreator')) return;
      submitting.current = true; setBusy(true); setMessage(null);
      void workspaceOpenBridge.open(file.trim()).then(() => { setRequested(true); setMessage(uiMessage('已请求在新窗口打开所选工作区。', 'Opening the selected workspace in a new window was requested.')); })
        .catch(error => setMessage(error instanceof Error ? error.message : uiMessage('无法打开此工作区，请检查文件路径。', 'Could not open this workspace. Check the file path.')))
        .finally(() => { submitting.current = false; setBusy(false); });
    }}>
      <label htmlFor="open-workspace-file">{uiText('工作区文件的完整路径', 'Full path to the workspace file')}</label>
      <input id="open-workspace-file" data-testid="open-workspace-file" value={file} onChange={event => { setFile(event.target.value); setRequested(false); setMessage(null); }} aria-describedby="open-workspace-help" />
      <p id="open-workspace-help">{uiText('选择原工程的 .mcreator 文件，宿主沿现有流程在新窗口打开。', 'Select the original project’s .mcreator file to open it through the existing desktop flow in a new window.')}</p>
      {message && <p role={requested ? 'status' : 'alert'}>{renderUiMessage(message)}</p>}
      <button type="submit" className="btn-primary" disabled={!workspaceOpenBridge.available || busy || requested || !file.trim().endsWith('.mcreator')} aria-busy={busy}><FolderOpen size={14} aria-hidden="true" />{uiText('打开工作区', 'Open workspace')}</button>
    </form>
  </div></div>;
}
