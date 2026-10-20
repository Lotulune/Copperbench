import { tr } from '../i18n/locale';
import { valueLabel } from '../i18n/labels';
import React, { useEffect, useMemo, useRef, useState, useSyncExternalStore } from 'react';
import { Bot, Check, ChevronRight, Clipboard, KeyRound, LockKeyhole, RefreshCw, ShieldAlert, ShieldCheck, X } from 'lucide-react';
import { mcpRuntimeBridge, subscribeMcpRuntime } from '../bridge/mcpRuntimeBridge';
import { useWorkbench } from '../context/WorkbenchContext';
import { useDialogA11y } from '../hooks/useDialogA11y';
import { useMcpRuntimeState } from '../hooks/useMcpRuntimeState';
import { t, uiText } from '../i18n';
import type { OperationApproval, PermissionProfile } from '../types/contract';
import { TaskAuthorizationPanel } from './TaskAuthorizationPanel';
import { BlockbenchSetupPanel } from './BlockbenchSetupPanel';

const profiles = (): { id: PermissionProfile; title: string; desc: string }[] => [
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
  const tokenRequest = useRef(0);
  const [runtimeChange, setRuntimeChange] = useState<'permission' | 'pairing' | null>(null);
  const runtimeActivity = useSyncExternalStore(subscribeMcpRuntime, () =>
    mcpRuntimeBridge.changing ? 'changing' : mcpRuntimeBridge.revealingToken ? 'revealing' : 'idle');
  const changingPermission = runtimeChange !== null || runtimeActivity === 'changing';
  const revealingToken = runtimeActivity === 'revealing';
  const runtimeBusy = changingPermission || revealingToken;
  const [pairingOpen, setPairingOpen] = useState(false);
  const permissionPending = useRef(false);
  const [permissionStatus, setPermissionStatus] = useState('');
  const [permissionError, setPermissionError] = useState(false);
  const dialogRef = useDialogA11y(!!selected, () => setSelected(null));
  const pairingDialogRef = useDialogA11y(pairingOpen, () => {
    if (!permissionPending.current && !mcpRuntimeBridge.changing && !mcpRuntimeBridge.revealingToken) setPairingOpen(false);
  });

  useEffect(() => { ++tokenRequest.current; setToken(null); }, [mcp?.url, mcp?.permissionProfile, mcp?.expiresAt]);
  useEffect(() => () => { ++tokenRequest.current; }, []);

  const changePermission = async (next: PermissionProfile) => {
    if (permissionPending.current || mcpRuntimeBridge.changing || mcpRuntimeBridge.revealingToken || (mcp?.status === 'listening' && profile === next)) return;
    permissionPending.current = true;
    setRuntimeChange('permission');
    setPermissionError(false);
    setPermissionStatus('');
    setStatus('');
    ++tokenRequest.current;
    setToken(null);
    try {
      const result = await mcpRuntimeBridge.setPermissionProfile(next);
      if (result.status !== 'listening' || result.permissionProfile !== next) {
        throw new Error(result.failure || tr("权限切换失败，请重新选择"));
      }
      setPermissionStatus(tr("已切换为{0}，请获取新令牌并重新连接", [valueLabel(next)]));
    } catch (error) {
      setPermissionError(true);
      setPermissionStatus(error instanceof Error ? error.message : tr("权限切换失败，请重新选择"));
    } finally {
      await refreshMcp();
      permissionPending.current = false;
      setRuntimeChange(null);
    }
  };

  const resetPairing = async () => {
    if (permissionPending.current || mcpRuntimeBridge.changing || mcpRuntimeBridge.revealingToken) return;
    // An empty workspace ID belongs to the unavailable-state placeholder, whose
    // default profile is not evidence of the host's current permission profile.
    const expectedProfile = mcp?.workspaceId ? mcp.permissionProfile : null;
    let paired = false;
    permissionPending.current = true;
    setRuntimeChange('pairing');
    setPermissionError(false);
    setPermissionStatus('');
    setStatus('');
    ++tokenRequest.current;
    setToken(null);
    try {
      const result = await mcpRuntimeBridge.resetPairing();
      if (result.status !== 'listening' || (expectedProfile !== null && result.permissionProfile !== expectedProfile)) {
        throw new Error(result.failure || uiText('重新配对失败，请重试。', 'Pairing reset failed. Please retry.'));
      }
      paired = true;
      setPermissionStatus(uiText('已重新配对，请显示新令牌并更新客户端配置。', 'Pairing reset. Reveal the new token and update your client configuration.'));
    } catch (error) {
      setPermissionError(true);
      setPermissionStatus(error instanceof Error ? error.message : uiText('重新配对失败，请重试。', 'Pairing reset failed. Please retry.'));
    } finally {
      await refreshMcp();
      permissionPending.current = false;
      setRuntimeChange(null);
      // Restore focus only after the invoker is enabled in the same commit.
      if (paired) setPairingOpen(false);
    }
  };

  const connectionLabel = mcp?.status === 'listening' ? tr("服务已启动") : tr("未启动");
  const configSnippet = useMemo(() => {
    if (!mcp?.url || !token) return '';
    return `URL: ${mcp.url}\nAuthorization: Bearer ${token}\nworkspaceId: ${mcp.workspaceId}`;
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
    if (permissionPending.current || mcpRuntimeBridge.changing || mcpRuntimeBridge.revealingToken) return;
    const request = ++tokenRequest.current;
    try {
      const response = await mcpRuntimeBridge.revealTokenOnce();
      if (request !== tokenRequest.current) return;
      setToken(response.token);
      await refreshMcp();
      if (request === tokenRequest.current) setStatus(tr("令牌已显示一次；请勿粘贴到聊天或日志"));
    } catch (error) {
      if (request === tokenRequest.current) setStatus(error instanceof Error ? error.message : tr("令牌不可用"));
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
          </div>
        </div>
        <span className={`connection-state${mcp?.status === 'listening' ? '' : ' is-offline'}`}>
          <span aria-hidden="true" />{connectionLabel}
        </span>
      </header>

      <details className="ai-integration-settings"><summary>{uiText('Blockbench', 'Blockbench')}</summary><BlockbenchSetupPanel /></details>
      <div className="ai-control-layout">
        <section className="permission-panel" aria-labelledby="mcp-runtime-heading" aria-busy={runtimeBusy}>
          <div className="stage2-section-heading">
            <div>
              <h3 id="mcp-runtime-heading">{tr("本机 MCP 服务")}</h3>
            </div>
            <Bot size={18} aria-hidden="true" />
          </div>
          <dl className="approval-details">
            <div><dt>{tr("状态")}</dt><dd>{connectionLabel}</dd></div>
            <div><dt>{tr("地址")}</dt><dd><code>{mcp?.url ?? '—'}</code></dd></div>
            <div><dt>{tr("工作区")}</dt><dd><code>{mcp?.workspaceId || '—'}</code></dd></div>
            <div><dt>{tr("权限")}</dt><dd>{mcp?.status === 'listening' ? valueLabel(mcp.permissionProfile) : '—'}</dd></div>
            <div><dt>{tr("令牌到期")}</dt><dd>{mcp?.expiresAt ?? '—'}</dd></div>
          </dl>
          {mcp?.failure && <div className="stage2-status" role="status">{mcp.failure}</div>}
          <div className="approval-actions">
            <button className="btn-secondary" type="button" disabled={!mcp?.url}
              onClick={() => void copyText(mcp?.url ?? '', tr("已复制 MCP 地址"))}>
              <Clipboard size={15} aria-hidden="true" />{tr("复制 URL")}</button>
            <button className="btn-secondary" type="button" disabled={runtimeBusy || !mcp?.tokenAvailable}
              onClick={() => void revealToken()}>
              <KeyRound size={15} aria-hidden="true" />{tr("显示一次令牌")}</button>
            <button className="btn-secondary" type="button" disabled={runtimeBusy || !configSnippet}
              onClick={() => void copyText(configSnippet, tr("已复制 MCP 配置信息"))}>
              <Clipboard size={15} aria-hidden="true" />{tr("复制配置")}</button>
            <button className="btn-secondary" type="button"
              disabled={runtimeBusy || !mcp || !mcpRuntimeBridge.canResetPairing}
              onClick={() => { setPermissionError(false); setPermissionStatus(''); setPairingOpen(true); }}>
              <RefreshCw size={15} aria-hidden="true" />{uiText('重新配对', 'Reset pairing')}</button>
          </div>
          {!token && mcp?.url && <p className="stage2-status" data-testid="mcp-configuration-guidance">
            {mcp.tokenAvailable
              ? uiText('先显示一次令牌，再复制完整配置。', 'Reveal the one-time token before copying the complete configuration.')
              : mcpRuntimeBridge.canResetPairing
                ? uiText('令牌已显示过；如未保存，请重新配对后获取新令牌。', 'The token was already revealed. Reset pairing to obtain a new token if you did not save it.')
                : uiText('当前桌面版本请重新打开工作区以获取新令牌。', 'Reopen this workspace to obtain a new token in this desktop version.')}
          </p>}
          {token && <div className="stage2-status" role="status"><code>{token}</code></div>}
        </section>

        <section className="permission-panel" aria-labelledby="permission-heading" aria-busy={runtimeBusy}>
          <div className="stage2-section-heading">
            <div>
              <h3 id="permission-heading">{tr("权限档位")}</h3>
              <p>{uiText('切换后，需用新令牌重新连接。', 'Reconnect with a new token after switching.')}</p>
            </div>
            <ShieldCheck size={18} aria-hidden="true" />
          </div>
          <div className="permission-options" role="group" aria-labelledby="permission-heading">
            {profiles().map((item) => {
              const active = mcp?.status === 'listening' && profile === item.id;
              return (
                <button
                  key={item.id}
                  type="button"
                  className={`permission-option${active ? ' is-active' : ''}`}
                  aria-pressed={active}
                  disabled={!mcpRuntimeBridge.available || !mcp || runtimeBusy}
                  onClick={() => void changePermission(item.id)}
                >
                  <span>{item.title}</span>
                  <small>{item.desc}</small>
                  {active && <Check size={15} aria-hidden="true" />}
                </button>
              );
            })}
          </div>
          <div className="stage2-status" data-testid="permission-status" role={permissionError ? 'alert' : 'status'}>
            {runtimeBusy
              ? revealingToken ? uiText('正在显示 MCP 令牌…', 'Revealing the MCP token…')
                : runtimeChange === 'permission' ? tr("正在切换权限…") : uiText('正在更新 MCP 连接…', 'Updating the MCP connection…')
              : permissionStatus}
          </div>
        </section>

        <TaskAuthorizationPanel />

        <section className="approval-panel" data-testid="approval-queue" aria-labelledby="approval-heading" tabIndex={-1}>
          <div className="stage2-section-heading">
            <div>
              <h3 id="approval-heading">{tr("待处理审批")}</h3>
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

      {pairingOpen && (
        <div className="modal-overlay">
          <div className="modal-card stage2-dialog" role="dialog" aria-modal="true"
            aria-labelledby="mcp-pairing-title" aria-describedby="mcp-pairing-impact"
            aria-busy={runtimeBusy} data-testid="mcp-pairing-dialog" ref={pairingDialogRef}>
            <div className="modal-header">
              <h3 id="mcp-pairing-title">{uiText('重新配对 MCP', 'Reset MCP pairing')}</h3>
              <button className="icon-button" type="button" aria-label={tr("关闭")} disabled={runtimeBusy}
                onClick={() => setPairingOpen(false)}><X size={16} aria-hidden="true" /></button>
            </div>
            <div className="modal-body">
              <p id="mcp-pairing-impact">{uiText(
                '旧令牌和 MCP 连接将失效。权限档位保持不变，已启动的工作区任务不会被此操作取消。请用新令牌重新连接客户端。',
                'Old tokens and MCP connections will stop working. Your permission profile stays the same, and this action does not cancel workspace tasks already started. Reconnect your client with the new token.')}</p>
              {permissionError && <div className="stage2-status" role="alert">{permissionStatus}</div>}
              {runtimeBusy && <div className="stage2-status" role="status">{revealingToken
                ? uiText('正在显示 MCP 令牌…', 'Revealing the MCP token…')
                : uiText('正在更新 MCP 连接…', 'Updating the MCP connection…')}</div>}
            </div>
            <div className="modal-footer approval-actions">
              <button className="btn-secondary" type="button" disabled={runtimeBusy} onClick={() => setPairingOpen(false)}>{tr("取消")}</button>
              <button className="btn-primary" type="button" disabled={runtimeBusy} onClick={() => void resetPairing()}>
                {uiText('确认重新配对', 'Confirm pairing reset')}</button>
            </div>
          </div>
        </div>
      )}

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
