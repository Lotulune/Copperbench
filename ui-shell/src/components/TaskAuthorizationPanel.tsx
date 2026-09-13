import React, { useEffect, useRef, useState } from 'react';
import { ShieldCheck, X } from 'lucide-react';
import { useWorkbench } from '../context/WorkbenchContext';
import { useDialogA11y } from '../hooks/useDialogA11y';
import { mcpRuntimeBridge } from '../bridge/mcpRuntimeBridge';
import { t } from '../i18n';
import type { TaskAuthorization, TaskAuthorizationRequest, TaskCapability } from '../types/contract';
import './taskAuthorization.css';

const capabilities: { id: TaskCapability; label: string }[] = [
  { id: 'create', label: '创建工作区' }, { id: 'edit', label: '修改内容' }, { id: 'build', label: '生成与构建' },
  { id: 'test', label: '校验与测试' }, { id: 'run_client', label: '运行客户端' },
  { id: 'run_server', label: '运行专用服务器' }, { id: 'restore', label: '恢复历史版本' }
];
const names = (items: TaskCapability[]) => capabilities.filter(item => items.includes(item.id)).map(item => item.label).join('、');

export const TaskAuthorizationPanel: React.FC = () => {
  const { listTaskAuthorizations, getWorkspaceRoot, createTaskAuthorization, revokeTaskAuthorization } = useWorkbench();
  const [grants, setGrants] = useState<TaskAuthorization[]>([]);
  const [label, setLabel] = useState('模组开发与验收');
  const [root, setRoot] = useState('');
  const [selected, setSelected] = useState<TaskCapability[]>(['create', 'edit', 'build', 'test', 'run_client']);
  const [ttl, setTtl] = useState(7200);
  const [eula, setEula] = useState(false);
  const [review, setReview] = useState<TaskAuthorizationRequest | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [status, setStatus] = useState('');
  const [now, setNow] = useState(Date.now());
  const errorRef = useRef<HTMLDivElement>(null);
  const dialogRef = useDialogA11y(!!review, () => { if (!busy) setReview(null); });

  useEffect(() => {
    let active = true;
    void Promise.all([listTaskAuthorizations(), getWorkspaceRoot()]).then(([items, path]) => {
      if (active) { setGrants(items); setRoot(path); }
    }).catch(reason => { if (active) setError(reason instanceof Error ? reason.message : '无法读取任务授权'); });
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => { active = false; window.clearInterval(timer); };
  }, [listTaskAuthorizations, getWorkspaceRoot]);
  useEffect(() => { if (error) errorRef.current?.focus(); }, [error]);

  const issue = async () => {
    if (!review) return;
    setBusy(true); setError('');
    try {
      const result = await createTaskAuthorization(review);
      if (result.status !== 'completed') throw new Error(t(result.diagnostics[0]?.message ?? '任务授权未创建'));
      const grant = result.data as unknown as TaskAuthorization;
      setGrants(items => [grant, ...items.filter(item => item.id !== grant.id)]);
      setReview(null); setStatus('任务授权已创建；将授权 ID 提供给 agent。');
    } catch (reason) { setReview(null); setError(reason instanceof Error ? reason.message : '任务授权未创建'); }
    finally { setBusy(false); }
  };

  const revoke = async (id: string) => {
    setBusy(true); setError('');
    try {
      const result = await revokeTaskAuthorization(id);
      if (result.status !== 'completed') throw new Error(t(result.diagnostics[0]?.message ?? '撤销失败'));
      setGrants(items => items.map(item => item.id === id ? { ...item, revoked: true, active: false } : item));
      setStatus('授权已撤销；后续使用此授权的请求会被拒绝。');
    } catch (reason) { setError(reason instanceof Error ? reason.message : '撤销失败'); }
    finally { setBusy(false); }
  };

  return <section className="permission-panel task-authority-panel" aria-labelledby="task-authority-heading">
    <div className="stage2-section-heading"><div>
      <h3 id="task-authority-heading">任务授权</h3>
      <p>一次确认任务目录、操作范围与期限，供 CLI 和 MCP 连续执行。授权不会扩大 MCP 连接本身的权限。</p>
    </div><ShieldCheck size={18} aria-hidden="true" /></div>
    <form className="task-authority-form" onSubmit={event => {
      event.preventDefault(); setError('');
      if (!selected.length) { setError('请至少选择一项允许的操作。'); return; }
      setReview({ label: label.trim(), root: root.trim(), capabilities: [...selected], ttlSeconds: ttl, serverEulaAccepted: eula });
    }}>
      <label htmlFor="task-authority-label">任务名称</label>
      <input id="task-authority-label" required maxLength={120} value={label} onChange={event => setLabel(event.target.value)} disabled={busy} />
      <label htmlFor="task-authority-root">授权目录（绝对路径，包含子目录）</label>
      <input id="task-authority-root" required maxLength={1024} value={root} onChange={event => setRoot(event.target.value)} disabled={busy} />
      <label htmlFor="task-authority-ttl">有效期</label>
      <select id="task-authority-ttl" value={ttl} onChange={event => setTtl(Number(event.target.value))} disabled={busy}>
        <option value={3600}>1 小时</option><option value={7200}>2 小时</option><option value={28800}>8 小时</option><option value={86400}>24 小时</option>
      </select>
      <fieldset disabled={busy}><legend>允许的操作</legend><div className="task-authority-capabilities">
        {capabilities.map(item => <label key={item.id}><input type="checkbox" checked={selected.includes(item.id)}
          onChange={event => setSelected(items => event.target.checked ? [...items, item.id] : items.filter(id => id !== item.id))} />{item.label}</label>)}
      </div></fieldset>
      {selected.includes('run_server') && <label className="task-authority-eula"><input type="checkbox" required checked={eula}
        onChange={event => setEula(event.target.checked)} />我已阅读并接受 <a href="https://www.minecraft.net/eula" target="_blank" rel="noreferrer">Minecraft EULA</a>，允许此任务运行专用测试服务器。</label>}
      <p className="task-authority-note">目录范围用于 Copperbench 操作校验；Gradle 构建脚本仍以当前系统用户运行。到期或撤销会阻止新请求，已启动的任务需在任务面板取消。</p>
      <button type="submit" className="btn-primary" disabled={busy || !label.trim() || !root.trim()}>审查并创建授权</button>
    </form>
    {error && <div ref={errorRef} role="alert" tabIndex={-1} className="task-authority-error">{error}</div>}
    <div role="status" className="stage2-status">{status}</div>
    <ul className="task-authority-list" aria-label="已签发的任务授权">
      {grants.map(grant => {
        const active = !grant.revoked && Date.parse(grant.expiresAt) > now;
        return <li key={grant.id}><strong>{grant.label}</strong><span>{grant.revoked ? '已撤销' : active ? '有效' : '已到期'}</span>
          <code>{grant.root}</code><small>{names(grant.capabilities)}</small>
          <small>到期：<time dateTime={grant.expiresAt}>{new Date(grant.expiresAt).toLocaleString()}</time></small>
          <code aria-label="授权 ID">{grant.id}</code>
          <div className="approval-actions"><button type="button" className="btn-secondary" disabled={!active} onClick={() => {
            void mcpRuntimeBridge.copyText(grant.id).then(() => setStatus('已复制授权 ID')).catch(() => setError('复制失败，请手动选择授权 ID。'));
          }}>复制授权 ID</button><button type="button" className="btn-danger" disabled={busy || grant.revoked} onClick={() => void revoke(grant.id)}>撤销授权</button></div>
        </li>;
      })}
      {!grants.length && <li>暂无任务授权。</li>}
    </ul>
    {review && <div className="modal-overlay"><div className="modal-card stage2-dialog" role="dialog" aria-modal="true"
      aria-labelledby="task-authority-review" ref={dialogRef}>
      <div className="modal-header"><h3 id="task-authority-review">确认任务授权范围</h3>
        <button type="button" className="icon-button" aria-label="关闭" disabled={busy} onClick={() => setReview(null)}><X size={16} /></button></div>
      <div className="modal-body"><strong>{review.label}</strong><dl className="approval-details">
        <div><dt>目录</dt><dd><code>{review.root}</code></dd></div><div><dt>操作</dt><dd>{names(review.capabilities)}</dd></div>
        <div><dt>有效期</dt><dd>{review.ttlSeconds / 3600} 小时</dd></div>
        {review.capabilities.includes('run_server') && <div><dt>Minecraft EULA</dt><dd>已明确接受</dd></div>}
      </dl></div><div className="modal-footer approval-actions">
        <button type="button" className="btn-secondary" disabled={busy} onClick={() => setReview(null)}>取消</button>
        <button type="button" className="btn-primary" disabled={busy} onClick={() => void issue()}>{busy ? '正在签发…' : '确认授权'}</button>
      </div></div></div>}
  </section>;
};
