import React from 'react';
import { ShieldX, RotateCcw } from 'lucide-react';
import { useWorkbench } from '../context/WorkbenchContext';
import { t } from '../i18n';

export const SchemaIncompatibleView: React.FC = () => {
  const { state, loadScenario } = useWorkbench();
  const incompatDiagnostic = state.diagnostics.find(
    (d) => d.code === 'UI_CORE_SCHEMA_INCOMPATIBLE'
  );

  if (!state.schemaIncompatible) return null;

  return (
    <div
      className="modal-overlay"
      data-testid="schema-incompatible"
      style={{ zIndex: 3000 }}
    >
      <div className="modal-card animate-fade-in" style={{ width: '460px' }}>
        <div className="modal-header" style={{ background: 'var(--badge-red-bg)' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px', color: 'var(--badge-red)', fontWeight: 700 }}>
            <ShieldX size={18} />
            <span>UI-Core 协议不兼容</span>
          </div>
        </div>

        <div className="modal-body">
          <div style={{ fontSize: '13px', color: 'var(--text-main)', lineHeight: '1.6' }}>
            启动时的协议版本协商失败。为避免数据损坏，不会回退到无类型的 JSON 通信。
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
              fontSize: '11px',
              fontFamily: 'var(--font-mono)'
            }}
          >
            <div><strong>UI 协议版本：</strong>{String(incompatDiagnostic?.message.args?.ui ?? '未知')}</div>
            <div><strong>Core 协议版本：</strong>{String(incompatDiagnostic?.message.args?.core ?? '未知')}</div>
            <div style={{ color: 'var(--badge-red)' }}>
              错误：{incompatDiagnostic ? t(incompatDiagnostic.message) : 'UI_CORE_SCHEMA_INCOMPATIBLE'}
            </div>
          </div>
        </div>

        <div className="modal-footer">
          <button
            className="btn-primary"
            onClick={() => loadScenario('ready')}
          >
            <RotateCcw size={14} />
            <span>重置为兼容协议（v1.0）</span>
          </button>
        </div>
      </div>
    </div>
  );
};
