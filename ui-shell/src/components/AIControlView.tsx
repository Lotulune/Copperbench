import { tr } from '../i18n/locale';
import { valueLabel } from '../i18n/labels';
import React, { useMemo, useState } from 'react';
import { Bot, Check, ChevronRight, Clipboard, KeyRound, LockKeyhole, ShieldAlert, ShieldCheck, X } from 'lucide-react';
import { mcpRuntimeBridge } from '../bridge/mcpRuntimeBridge';
import { useWorkbench } from '../context/WorkbenchContext';
import { useDialogA11y } from '../hooks/useDialogA11y';
import { useMcpRuntimeState } from '../hooks/useMcpRuntimeState';
import { t } from '../i18n';
import type { OperationApproval, PermissionProfile } from '../types/contract';
import { TaskAuthorizationPanel } from './TaskAuthorizationPanel';
import { BlockbenchSetupPanel } from './BlockbenchSetupPanel';

const profiles: { id: PermissionProfile; title: string; desc: string }[] = [
  { id: 'read_only', title: tr("只读"), desc: tr("查询与快照校验") },
  { id: 'workspace', title: tr("工作区"), desc: tr("编辑、构建与导出") },
  { id: 'full_access', title: tr("完全访问"), desc: tr("扩展本机资源访问") }
];

export const AIControlView: React.FC = () => {
  const { state, resolveOperationApproval } = useWorkbench();
  const { mcp, refresh: refreshMcp } = useMcpRuntimeState();
  const profile = mcp?.permissionProfile ?? 'workspace';
  const [selected, setSelected] = useState<OperationApproval | null>(null);
  const [status, setStatus] = useState('');
  const [token, setToken] = useState<string | null>(null);
  const dialogRef = useDialogA11y(!!selected, () => setSelected(null));

  const connectionLabel = mcp?.status === 'listening' ? tr("服务已启动") : tr("未启动");
  const configSnippet = useMemo(() => {
    if (!mcp?.url) return '';
    return token
      ? `URL: ${mcp.url}\nAuthorization: Bearer ${token}\nworkspaceId: ${mcp.workspaceId}`
      : `URL: ${mcp.url}\nworkspaceId: ${mcp.workspaceId}`;
  }, [mcp, token]);

  const copyText = async (text: string, message: string) => {
    try {
      await mcpRuntimeBridge.copyText(text);
      setStatus(message);
    } catch {
      setStatus(tr("复制失败，请手动选择文本"));
    }
  };

  const revealToken = async () => {
    try {
      const response = await mcpRuntimeBridge.revealTokenOnce();
      setToken(response.token);
      await refreshMcp();
      setStatus(tr("令牌已显示一次；请勿粘贴到聊天或日志"));
    } catch (error) {
      setStatus(error instanceof Error ? error.message : tr("令牌不可用"));
    }
  };

  const resolve = async (decision: 'approve' | 'deny') => {
    if (!selected) return;
    const result = await resolveOperationApproval(selected.id, decision);
    if (result.status === 'completed') {
      setStatus(decision === 'approve' ? tr("已批准“{0}”", [t(selected.title)]) : tr("已拒绝“{0}”", [t(selected.title)]));
      setSelected(null);
    }
  };

  return (
    <section className="stage2-view ai-control-view animate-fade-in">
      <header className="stage2-view-header">
        <div className="stage2-view-title">
          <Bot size={20} aria-hidden="true" />
          <div>
            <h2>{tr("AI 与 MCP")}</h2>
            <span>{tr("本地连接 · 审计开启")}</span>
          </div>
        </div>
        <span className={`connection-state${mcp?.status === 'listening' ? '' : ' is-offline'}`}>
          <span aria-hidden="true" />{connectionLabel}
        </span>
      </header>

      <BlockbenchSetupPanel />
      <div className="ai-control-layout">
        <section className="permission-panel" aria-labelledby="mcp-runtime-heading">
          <div className="stage2-section-heading">
            <div>
              <h3 id="mcp-runtime-heading">{tr("本机 MCP 服务")}</h3>
              <p>{mcp?.status === 'listening' ? tr("仅监听 127.0.0.1；没有客户端连接时不会显示“已连接”。") : tr("当前工作区没有可用的 MCP 监听端点。")}</p>
            </div>
            <Bot size={18} aria-hidden="true" />
          </div>
          <dl className="approval-details">
            <div><dt>{tr("状态")}</dt><dd>{connectionLabel}</dd></div>
            <div><dt>{tr("地址")}</dt><dd><code>{mcp?.url ?? '—'}</code></dd></div>
            <div><dt>{tr("工作区")}</dt><dd><code>{mcp?.workspaceId || '—'}</code></dd></div>
            <div><dt>{tr("权限")}</dt><dd>{valueLabel(mcp?.permissionProfile ?? 'workspace')}</dd></div>
            <div><dt>{tr("令牌到期")}</dt><dd>{mcp?.expiresAt ?? '—'}</dd></div>
          </dl>
          {mcp?.failure && <div className="stage2-status" role="status">{mcp.failure}</div>}
          <div className="approval-actions">
            <button className="btn-secondary" type="button" disabled={!mcp?.url}
              onClick={() => void copyText(mcp?.url ?? '', tr("已复制 MCP 地址"))}>
              <Clipboard size={15} aria-hidden="true" />{tr("复制 URL")}</button>
            <button className="btn-secondary" type="button" disabled={!mcp?.tokenAvailable}
              onClick={() => void revealToken()}>
              <KeyRound size={15} aria-hidden="true" />{tr("显示一次令牌")}</button>
            <button className="btn-secondary" type="button" disabled={!configSnippet}
              onClick={() => void copyText(configSnippet, tr("已复制 MCP 配置信息"))}>
              <Clipboard size={15} aria-hidden="true" />{tr("复制配置")}</button>
          </div>
          {token && <div className="stage2-status" role="status"><code>{token}</code></div>}
        </section>

        <section className="permission-panel" aria-labelledby="permission-heading">
          <div className="stage2-section-heading">
            <div>
              <h3 id="permission-heading">{tr("权限档位")}</h3>
              <p>{tr("显示当前 MCP 连接的实际权限；更改任务授权不会扩大连接权限。")}</p>
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
                  disabled
                >
                  <span>{item.title}</span>
                  <small>{item.desc}</small>
                  {active && <Check size={15} aria-hidden="true" />}
                </button>
              );
            })}
          </div>
        </section>

        <TaskAuthorizationPanel />

        <section className="approval-panel" data-testid="approval-queue" aria-labelledby="approval-heading" tabIndex={-1}>
          <div className="stage2-section-heading">
            <div>
              <h3 id="approval-heading">{tr("待处理审批")}</h3>
              <p>{state.operationApprovals.length} {tr(" 项受保护操作")}</p>
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
                  <span>{valueLabel(approval.requestedBy)} · {approval.affectedPaths[0]}</span>
                  {!approval.canApprove && (
                    <span className="approval-blocked" data-testid="approval-blocked">{tr("AI 无权批准，必须在插件中心由用户操作")}</span>
                  )}
                </div>
                {approval.canApprove ? (
                  <button
                    className="icon-button"
                    type="button"
                    data-testid="review-approval"
                    aria-label={tr("审查 {0}", [t(approval.title)])}
                    onClick={() => setSelected(approval)}
                  >
                    <ChevronRight size={16} />
                  </button>
                ) : (
                  <span className="policy-badge">{tr("策略阻止")}</span>
                )}
              </div>
            ))}
            {state.operationApprovals.length === 0 && <div className="stage2-empty">{tr("没有待处理审批")}</div>}
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
                <h3 id="approval-dialog-title">{tr("确认受保护操作")}</h3>
              </div>
              <button className="icon-button" type="button" aria-label={tr("关闭")} onClick={() => setSelected(null)}>
                <X size={16} />
              </button>
            </div>
            <div className="modal-body">
              <strong>{t(selected.title)}</strong>
              <dl className="approval-details">
                <div><dt>{tr("请求方")}</dt><dd>{valueLabel(selected.requestedBy)}</dd></div>
                <div><dt>{tr("风险")}</dt><dd>{selected.risk === 'critical' ? tr("严重") : tr("高")}</dd></div>
                <div><dt>{tr("策略")}</dt><dd>{selected.policyCode}</dd></div>
              </dl>
              <div className="dialog-impact-list">
                {selected.affectedPaths.map((path) => <code key={path}>{path}</code>)}
              </div>
            </div>
            <div className="modal-footer approval-actions">
              <button className="btn-danger" type="button" data-testid="deny-approval" onClick={() => void resolve('deny')}>
                <X size={15} aria-hidden="true" />
                {tr("拒绝")}</button>
              <button className="btn-primary" type="button" data-testid="approve-operation" onClick={() => void resolve('approve')}>
                <Check size={15} aria-hidden="true" />
                {tr("明确批准")}</button>
            </div>
          </div>
        </div>
      )}
    </section>
  );
};
