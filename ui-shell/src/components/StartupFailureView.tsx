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
        style={{ width: '460px' }}
      >
        <div className="modal-header" style={{ background: 'var(--badge-red-bg)' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px', color: 'var(--badge-red)', fontWeight: 700 }}>
            <AlertTriangle size={18} aria-hidden="true" />
            <span id="startup-failure-title">桌面核心未连接</span>
          </div>
        </div>
        <div className="modal-body" style={{ gap: '12px' }}>
          <p style={{ fontSize: '13px', lineHeight: 1.6 }}>
            无法建立 UI-Core 原生桥接。为防止误把演示数据当作真实工作区，发行构建不会回退到 Mock 数据。
          </p>
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
          <p style={{ color: 'var(--text-sub)', fontSize: '11px' }}>
            请从 Copperbench 桌面程序启动，或检查 JCEF 宿主初始化日志后重启应用。
          </p>
        </div>
      </div>
    </div>
  );
};
