import React from 'react';
import { AlertOctagon, RefreshCw, Copy, Trash2, X } from 'lucide-react';
import { useWorkbench } from '../context/WorkbenchContext';
import { useDialogA11y } from '../hooks/useDialogA11y';
import { t } from '../i18n';

export const RevisionConflictModal: React.FC = () => {
  const { isConflictModalOpen, setIsConflictModalOpen, loadScenario, state } = useWorkbench();

  const dialogRef = useDialogA11y(isConflictModalOpen, () => setIsConflictModalOpen(false));

  const conflictDiagnostic =
    state.diagnostics.find((d) => d.code === 'WORKSPACE_REVISION_CONFLICT') ?? null;

  if (!isConflictModalOpen) return null;

  const handleRefresh = () => {
    setIsConflictModalOpen(false);
    loadScenario('ready');
  };

  const handleCreateCopy = () => {
    setIsConflictModalOpen(false);
    alert(t({
      key: 'notice.conflict_draft_cloned',
      fallback: 'Local changes have been cloned into a local draft element copy.'
    }));
  };

  const handleDiscard = () => {
    setIsConflictModalOpen(false);
    loadScenario('ready');
  };

  return (
    <div className="modal-overlay" data-testid="revision-conflict-dialog">
      <div
        ref={dialogRef}
        role="dialog"
        aria-modal="true"
        aria-label="版本并发写入冲突"
        className="modal-card animate-fade-in"
        style={{ width: '500px' }}
      >
        <div className="modal-header" style={{ background: 'var(--badge-red-bg)' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px', color: 'var(--badge-red)', fontWeight: 700 }}>
            <AlertOctagon size={18} />
            <span>版本并发写入冲突</span>
          </div>
          <button onClick={() => setIsConflictModalOpen(false)} style={{ color: 'var(--text-muted)' }}>
            <X size={16} />
          </button>
        </div>

        <div className="modal-body">
          <div style={{ fontSize: '13px', color: 'var(--text-main)', lineHeight: '1.6' }}>
            {conflictDiagnostic
              ? t(conflictDiagnostic.message)
              : '此编辑器打开后工作区已被修改。为避免静默覆盖或数据丢失，你的更改未提交。'}
          </div>

          {conflictDiagnostic?.path && (
            <div
              style={{
                background: 'var(--bg-panel)',
                border: '1px solid var(--border-subtle)',
                borderRadius: 'var(--radius-md)',
                padding: '12px',
                display: 'flex',
                flexDirection: 'column',
                gap: '6px',
                fontSize: '11px',
                fontFamily: 'var(--font-mono)'
              }}
            >
              <div><strong>变更路径：</strong>{conflictDiagnostic.path}</div>
              <div><strong>写入者：</strong>MCP 自动化会话</div>
            </div>
          )}
        </div>

        <div className="modal-footer" style={{ justifyContent: 'space-between' }}>
          <button className="btn-secondary" onClick={handleDiscard}>
            <Trash2 size={13} />
            <span>放弃修改</span>
          </button>

          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <button className="btn-secondary" onClick={handleCreateCopy}>
              <Copy size={13} />
              <span>克隆为草稿</span>
            </button>
            <button
              className="btn-primary"
              onClick={handleRefresh}
              data-testid="conflict-refresh-btn"
            >
              <RefreshCw size={13} />
              <span>查看最新</span>
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};
