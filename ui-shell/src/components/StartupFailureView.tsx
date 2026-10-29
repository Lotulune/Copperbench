import { tr } from '../i18n/locale';
import React from 'react';
import { AlertTriangle } from 'lucide-react';
import { useWorkbench } from '../context/WorkbenchContext';
import { useDialogA11y } from '../hooks/useDialogA11y';
import { t } from '../i18n';

export const StartupFailureView: React.FC = () => {
  const { state } = useWorkbench();
  const diagnostic = state.diagnostics.find((item) => item.severity === 'error');
  const isOpen = state.viewportState === 'error' && !state.schemaIncompatible && !state.workbench;
  const dialogRef = useDialogA11y(isOpen, null);

  if (!isOpen) return null;

  return (
    <div className="modal-overlay" data-testid="startup-failure" style={{ zIndex: 2900 }}>
      <div
        className="modal-card animate-fade-in"
        ref={dialogRef}
        role="alertdialog"
        aria-modal="true"
        aria-labelledby="startup-failure-title"
        aria-describedby="startup-failure-description"
        style={{ width: '460px' }}
      >
        <div className="modal-header" style={{ background: 'var(--badge-red-bg)' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px', color: 'var(--badge-red)', fontWeight: 700 }}>
            <AlertTriangle size={18} aria-hidden="true" />
            <span id="startup-failure-title">{tr("暂时无法打开工作区")}</span>
          </div>
        </div>
        <div className="modal-body" style={{ gap: '12px' }}>
          <p id="startup-failure-description" style={{ fontSize: '13px', lineHeight: 1.6 }}>
            {tr("界面未能连接到 Copperbench。请重新加载；若问题持续，请关闭并重新启动应用。")}</p>
          <details>
            <summary>{tr("查看错误详情")}</summary>
          <div
            style={{
              padding: '10px 12px',
              border: '1px solid var(--border-subtle)',
              borderRadius: 'var(--radius-md)',
              background: 'var(--bg-panel)',
              color: 'var(--badge-red)',
              fontFamily: 'var(--font-mono)',
              fontSize: '11px',
              userSelect: 'text'
            }}
          >
            {diagnostic ? `${diagnostic.code}: ${t(diagnostic.message)}` : 'UI_CORE_STARTUP_FAILED'}
          </div>
          </details>
        </div>
        <div className="modal-footer">
          <button className="btn-primary" onClick={() => window.location.reload()}>{tr("重新加载")}</button>
        </div>
      </div>
    </div>
  );
};
