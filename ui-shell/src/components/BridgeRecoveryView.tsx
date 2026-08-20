import React from 'react';
import { RotateCcw, AlertTriangle } from 'lucide-react';
import { useWorkbench } from '../context/WorkbenchContext';
import { useDialogA11y } from '../hooks/useDialogA11y';

export const BridgeRecoveryView: React.FC = () => {
  const { state, reconcileRecovery } = useWorkbench();
  const recovery = state.recoveryState;

  // Blocking recovery view: Escape must not dismiss it; the explicit
  // reconcile action is the only way out.
  const dialogRef = useDialogA11y(state.viewportState === 'recovery', null);

  if (state.viewportState !== 'recovery' || !recovery) return null;

  return (
    <div
      className="modal-overlay"
      data-testid="recovery-view"
      style={{ zIndex: 2000 }}
    >
      <div
        ref={dialogRef}
        role="dialog"
        aria-modal="true"
        aria-label="渲染桥接异常恢复"
        className="modal-card animate-fade-in"
        style={{ width: '480px' }}
      >
        <div className="modal-header" style={{ background: 'var(--badge-amber-bg)' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px', color: 'var(--badge-amber)', fontWeight: 700 }}>
            <AlertTriangle size={18} />
            <span>渲染桥接异常恢复</span>
          </div>
        </div>

        <div className="modal-body">
          <div style={{ fontSize: '13px', color: 'var(--text-main)', lineHeight: '1.6' }}>
            桌面渲染桥接发生异常重启（{recovery.reasonCode}）。
            工作区正在按最后一次提交的快照安全恢复，未提交的操作不会被视为已保存。
          </div>

          <div
            style={{
              background: 'var(--bg-panel)',
              border: '1px solid var(--border-subtle)',
              borderRadius: 'var(--radius-md)',
              padding: '12px',
              display: 'flex',
              flexDirection: 'column',
              gap: '6px',
              fontSize: '11px'
            }}
          >
            <div><strong>最后提交修订：</strong> {recovery.lastCommittedRevision}</div>
            <div><strong>已丢弃的未提交请求：</strong> {recovery.uncommittedRequestIds.length}</div>
            <div style={{ color: 'var(--text-sub)' }}>
              已提交的数据没有丢失。崩溃前尚未提交的输入已被安全丢弃。
            </div>
          </div>
        </div>

        <div className="modal-footer">
          <button
            className="btn-primary"
            onClick={reconcileRecovery}
            data-testid="recovery-reconcile-btn"
          >
            <RotateCcw size={14} />
            <span>恢复并继续工作</span>
          </button>
        </div>
      </div>
    </div>
  );
};
