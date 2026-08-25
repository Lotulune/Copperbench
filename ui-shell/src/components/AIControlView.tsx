import React, { useState } from 'react';
import { Bot, Check, ChevronRight, LockKeyhole, ShieldAlert, ShieldCheck, X } from 'lucide-react';
import { useWorkbench } from '../context/WorkbenchContext';
import { useDialogA11y } from '../hooks/useDialogA11y';
import { t } from '../i18n';
import type { OperationApproval, PermissionProfile } from '../types/contract';

const profiles: { id: PermissionProfile; title: string; desc: string }[] = [
  { id: 'read_only', title: '只读', desc: '查询与快照校验' },
  { id: 'workspace', title: '工作区', desc: '编辑、构建与导出' },
  { id: 'full_access', title: '完全访问', desc: '扩展本机资源访问' }
];

export const AIControlView: React.FC = () => {
  const { state, elevatePermission, resolveOperationApproval } = useWorkbench();
  const profile = state.workbench?.permission?.profile ?? 'workspace';
  const [selected, setSelected] = useState<OperationApproval | null>(null);
  const [status, setStatus] = useState('');
  const dialogRef = useDialogA11y(!!selected, () => setSelected(null));

  const resolve = async (decision: 'approve' | 'deny') => {
    if (!selected) return;
    const result = await resolveOperationApproval(selected.id, decision);
    if (result.status === 'completed') {
      setStatus(decision === 'approve' ? `已批准“${t(selected.title)}”` : `已拒绝“${t(selected.title)}”`);
      setSelected(null);
    }
  };

  return (
    <section className="stage2-view ai-control-view animate-fade-in">
      <header className="stage2-view-header">
        <div className="stage2-view-title">
          <Bot size={20} aria-hidden="true" />
          <div>
            <h2>AI 与 MCP</h2>
            <span>本地连接 · 审计开启</span>
          </div>
        </div>
        <span className="connection-state"><span aria-hidden="true" />已连接</span>
      </header>

      <div className="ai-control-layout">
        <section className="permission-panel" aria-labelledby="permission-heading">
          <div className="stage2-section-heading">
            <div>
              <h3 id="permission-heading">权限档位</h3>
              <p>当前工作区令牌在档位变化后自动吊销。</p>
            </div>
            <ShieldCheck size={18} aria-hidden="true" />
          </div>
          <div className="permission-options">
            {profiles.map((item) => {
              const active = profile === item.id;
              return (
                <button
                  key={item.id}
                  type="button"
                  className={`permission-option${active ? ' is-active' : ''}`}
                  aria-pressed={active}
                  onClick={() => elevatePermission(item.id)}
                >
                  <span>{item.title}</span>
                  <small>{item.desc}</small>
                  {active && <Check size={15} aria-hidden="true" />}
                </button>
              );
            })}
          </div>
        </section>

        <section className="approval-panel" data-testid="approval-queue" aria-labelledby="approval-heading" tabIndex={-1}>
          <div className="stage2-section-heading">
            <div>
              <h3 id="approval-heading">待处理审批</h3>
              <p>{state.operationApprovals.length} 项受保护操作</p>
            </div>
            <span className="approval-count">{state.operationApprovals.length}</span>
          </div>

          <div className="approval-list">
            {state.operationApprovals.map((approval) => (
              <div className="approval-item" data-testid="approval-item" key={approval.id}>
                <div className={`approval-risk risk-${approval.risk}`} aria-hidden="true">
                  {approval.canApprove ? <ShieldAlert size={17} /> : <LockKeyhole size={17} />}
                </div>
                <div className="approval-copy">
                  <strong>{t(approval.title)}</strong>
                  <span>{approval.requestedBy.toUpperCase()} · {approval.affectedPaths[0]}</span>
                  {!approval.canApprove && (
                    <span className="approval-blocked" data-testid="approval-blocked">AI 无权批准，必须在插件中心由用户操作</span>
                  )}
                </div>
                {approval.canApprove ? (
                  <button
                    className="icon-button"
                    type="button"
                    data-testid="review-approval"
                    aria-label={`审查 ${t(approval.title)}`}
                    onClick={() => setSelected(approval)}
                  >
                    <ChevronRight size={16} />
                  </button>
                ) : (
                  <span className="policy-badge">策略阻止</span>
                )}
              </div>
            ))}
            {state.operationApprovals.length === 0 && <div className="stage2-empty">没有待处理审批</div>}
          </div>
        </section>
      </div>

      <div className="stage2-status" data-testid="approval-status" aria-live="polite">
        {status && <><Check size={14} aria-hidden="true" />{status}</>}
      </div>

      {selected && (
        <div className="modal-overlay">
          <div
            className="modal-card stage2-dialog"
            role="dialog"
            aria-modal="true"
            aria-labelledby="approval-dialog-title"
            data-testid="approval-dialog"
            ref={dialogRef}
          >
            <div className="modal-header">
              <div className="dialog-title-with-icon">
                <ShieldAlert size={18} aria-hidden="true" />
                <h3 id="approval-dialog-title">确认受保护操作</h3>
              </div>
              <button className="icon-button" type="button" aria-label="关闭" onClick={() => setSelected(null)}>
                <X size={16} />
              </button>
            </div>
            <div className="modal-body">
              <strong>{t(selected.title)}</strong>
              <dl className="approval-details">
                <div><dt>请求方</dt><dd>{selected.requestedBy.toUpperCase()}</dd></div>
                <div><dt>风险</dt><dd>{selected.risk === 'critical' ? '严重' : '高'}</dd></div>
                <div><dt>策略</dt><dd>{selected.policyCode}</dd></div>
              </dl>
              <div className="dialog-impact-list">
                {selected.affectedPaths.map((path) => <code key={path}>{path}</code>)}
              </div>
            </div>
            <div className="modal-footer approval-actions">
              <button className="btn-danger" type="button" data-testid="deny-approval" onClick={() => void resolve('deny')}>
                <X size={15} aria-hidden="true" />
                拒绝
              </button>
              <button className="btn-primary" type="button" data-testid="approve-operation" onClick={() => void resolve('approve')}>
                <Check size={15} aria-hidden="true" />
                明确批准
              </button>
            </div>
          </div>
        </div>
      )}
    </section>
  );
};
