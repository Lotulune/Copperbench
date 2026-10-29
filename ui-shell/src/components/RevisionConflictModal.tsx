import { tr } from '../i18n/locale';
import React, { useState } from 'react';
import { AlertOctagon, RefreshCw, X } from 'lucide-react';
import { useWorkbench } from '../context/WorkbenchContext';
import { useDialogA11y } from '../hooks/useDialogA11y';
import { t } from '../i18n';

export const RevisionConflictModal: React.FC = () => {
  const { isConflictModalOpen, setIsConflictModalOpen, refreshWorkspace, state } = useWorkbench();
  const [refreshing, setRefreshing] = useState(false);
  const [refreshFailed, setRefreshFailed] = useState(false);

  const dialogRef = useDialogA11y(isConflictModalOpen, () => setIsConflictModalOpen(false));

  const conflictDiagnostic =
    state.diagnostics.find((d) => d.code === 'WORKSPACE_REVISION_CONFLICT') ?? null;

  if (!isConflictModalOpen) return null;

  const handleRefresh = async () => {
    setRefreshing(true);
    setRefreshFailed(false);
    try {
      await refreshWorkspace();
      setIsConflictModalOpen(false);
    } catch { setRefreshFailed(true); }
    finally { setRefreshing(false); }
  };

  return (
    <div className="modal-overlay" data-testid="revision-conflict-dialog">
      <div
        ref={dialogRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby="revision-conflict-title"
        aria-describedby="revision-conflict-description"
        className="modal-card animate-fade-in"
        style={{ width: '500px' }}
      >
        <div className="modal-header" style={{ background: 'var(--badge-red-bg)' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px', color: 'var(--badge-red)', fontWeight: 700 }}>
            <AlertOctagon size={18} />
            <span id="revision-conflict-title">{tr("工作区已有新修改")}</span>
          </div>
          <button aria-label={tr("关闭")} onClick={() => setIsConflictModalOpen(false)} style={{ color: 'var(--text-muted)' }}>
            <X size={16} />
          </button>
        </div>

        <div className="modal-body">
          <div id="revision-conflict-description" style={{ fontSize: '13px', color: 'var(--text-main)', lineHeight: '1.6' }}>
            {conflictDiagnostic
              ? t(conflictDiagnostic.message)
              : tr("此编辑器打开后工作区已被修改。为避免静默覆盖或数据丢失，你的更改未提交。")}
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
              <div><strong>{tr("变更路径：")}</strong>{conflictDiagnostic.path}</div>
            </div>
          )}
          <p>{tr("你的修改尚未保存。可以继续编辑，或读取工作区最新状态；读取不会提交你的草稿。")}</p>
          {refreshFailed && <p role="alert" style={{ color: 'var(--badge-red)' }}>{tr("暂时无法读取最新状态，请重试。草稿仍保留在编辑器中。")}</p>}
        </div>

        <div className="modal-footer" style={{ justifyContent: 'space-between' }}>
          <button className="btn-secondary" onClick={() => setIsConflictModalOpen(false)}>
            <span>{tr("继续编辑")}</span>
          </button>

          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <button
              className="btn-primary"
              onClick={handleRefresh}
              disabled={refreshing}
              data-testid="conflict-refresh-btn"
            >
              <RefreshCw size={13} />
              <span>{refreshing ? tr("正在读取…") : tr("查看最新")}</span>
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};
