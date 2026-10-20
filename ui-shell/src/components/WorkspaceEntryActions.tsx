import { useRef, useState } from 'react';
import { FolderOpen, X } from 'lucide-react';
import { workspaceOpenBridge } from '../bridge/workspaceOpenBridge';
import { useDialogA11y } from '../hooks/useDialogA11y';
import { renderUiMessage, uiMessage, uiText, type UiMessage } from '../i18n';
import { useFirstCreation } from '../onboarding/FirstCreationContext';

export function OpenWorkspaceDialog() {
  const { workspaceOpen, closeWorkspaceOpen } = useFirstCreation();
  const [file, setFile] = useState('');
  const [busy, setBusy] = useState(false);
  const [choosing, setChoosing] = useState(false);
  const [requested, setRequested] = useState(false);
  const [message, setMessage] = useState<UiMessage | null>(null);
  const submitting = useRef(false);
  const picking = useRef(false);
  const dialogRef = useDialogA11y(workspaceOpen, closeWorkspaceOpen);
  if (!workspaceOpen) return null;
  return <div className="modal-overlay"><div ref={dialogRef} className="modal-card first-creation-guide open-workspace-dialog" role="dialog" aria-modal="true" aria-labelledby="open-workspace-title" data-testid="open-workspace-dialog">
    <header className="modal-header"><h2 id="open-workspace-title">{uiText('打开已有工作区', 'Open existing workspace')}</h2>
      <button type="button" onClick={closeWorkspaceOpen} aria-label={uiText('关闭打开工作区窗口', 'Close open-workspace dialog')}><X size={18} aria-hidden="true" /></button></header>
    <form className="modal-body first-creation-content" onSubmit={event => {
      event.preventDefault();
      if (submitting.current || picking.current || !workspaceOpenBridge.available || requested || !file.trim().endsWith('.mcreator')) return;
      submitting.current = true; setBusy(true); setMessage(null);
      void workspaceOpenBridge.open(file.trim()).then(() => { setRequested(true); setMessage(uiMessage('已请求在新窗口打开所选工作区。', 'Opening the selected workspace in a new window was requested.')); })
        .catch(error => setMessage(error instanceof Error ? error.message : uiMessage('无法打开此工作区，请检查文件路径。', 'Could not open this workspace. Check the file path.')))
        .finally(() => { submitting.current = false; setBusy(false); });
    }}>
      <label htmlFor="open-workspace-file">{uiText('工作区文件', 'Workspace file')}</label>
      <input id="open-workspace-file" data-testid="open-workspace-file" value={file} disabled={busy || choosing} onChange={event => { setFile(event.target.value); setRequested(false); setMessage(null); }} aria-describedby="open-workspace-help" />
      <button type="button" className="btn-secondary" data-testid="open-workspace-browse" disabled={!workspaceOpenBridge.workspacePickerAvailable || busy || choosing} aria-busy={choosing}
        onClick={() => {
          if (submitting.current || picking.current) return;
          picking.current = true; setChoosing(true); setMessage(null);
          void workspaceOpenBridge.chooseWorkspace().then(result => {
            if (result.cancelled) return;
            if (!result.workspaceFile) throw new Error(uiText('未获取到工作区文件，请重新选择或输入路径。', 'No workspace file was returned. Choose again or enter its path.'));
            setFile(result.workspaceFile); setRequested(false);
          }).catch(error => { setRequested(false); setMessage(error instanceof Error ? error.message : uiMessage('无法选择工作区文件，请手动输入路径。', 'Could not choose a workspace file. Enter its path manually.')); })
            .finally(() => { picking.current = false; setChoosing(false); });
        }}><FolderOpen size={14} aria-hidden="true" />{uiText('浏览文件…', 'Browse files…')}</button>
      <p id="open-workspace-help">{workspaceOpenBridge.workspacePickerAvailable
        ? uiText('选择原作品的 .mcreator 文件，也可以粘贴完整路径。', 'Choose the original creation’s .mcreator file, or paste its full path.')
        : uiText('输入原作品 .mcreator 文件的完整路径。', 'Enter the full path to the original creation’s .mcreator file.')}</p>
      {message && <p role={requested ? 'status' : 'alert'}>{renderUiMessage(message)}</p>}
      <button type="submit" className="btn-primary" disabled={!workspaceOpenBridge.available || busy || choosing || requested || !file.trim().endsWith('.mcreator')} aria-busy={busy}><FolderOpen size={14} aria-hidden="true" />{uiText('打开工作区', 'Open workspace')}</button>
    </form>
  </div></div>;
}
