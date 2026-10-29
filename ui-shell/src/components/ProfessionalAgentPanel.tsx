import { useEffect, useRef, useState } from 'react';
import { ArrowUp, Bot, Download, Plus, RefreshCw, Square, X } from 'lucide-react';
import { useAgent } from '../context/AgentContext';
import { useWorkbench } from '../context/WorkbenchContext';
import { useWorkbenchMode } from '../context/WorkbenchModeContext';
import { agentIsActive, DEFAULT_AGENT_SETTINGS, type AgentAttachment, type AgentProviderId, type AgentRunRecord, type AgentRunSettings } from '../bridge/agentBridge';
import { hasUnsavedDrafts, setUnsavedDraftCount } from '../hooks/unsavedDraftGuard';
import { uiText, useUiLocale } from '../i18n';
import { formatDateTime } from '../i18n/dateTime';
import { AgentProviderSelect } from './AgentProviderSelect';
import { AgentProviderIcon } from './AgentProviderIcon';
import { AgentApprovalPrompt, permissionDeniedHint } from './AgentApprovalPrompt';
import { AgentRunSettings as AgentRunSettingsControl } from './AgentRunSettings';
import { agentAttachment } from './agentEvidence';
import './professionalAgent.css';

export function agentRunLabel(run?: AgentRunRecord): string {
  switch (run?.state) {
    case 'preparing': return uiText('正在准备任务', 'Preparing task');
    case 'running': return uiText('正在运行', 'Running');
    case 'waiting_for_input': return uiText('等待你的处理', 'Waiting for your input');
    case 'cancelling': return uiText('正在取消', 'Cancelling');
    case 'finalizing': return uiText('正在保存结果', 'Saving results');
    case 'completed': return uiText('Agent 已结束', 'Agent finished');
    case 'failed': return uiText('执行失败', 'Execution failed');
    case 'cancelled': return uiText('已取消', 'Cancelled');
    case 'interrupted': return uiText('任务已中断，停止未确认', 'Interrupted; stop not confirmed');
    default: return uiText('等待任务', 'Waiting for a task');
  }
}
function verificationLabel(run: AgentRunRecord): string {
  switch (run.verification?.status) {
    case 'running': return run.verification.cancelRequestedAt ? uiText('正在停止验证', 'Stopping verification') : uiText('正在验证', 'Verifying');
    case 'passed': return uiText('所选验证通过', 'Selected verification passed');
    case 'failed': return uiText('验证未通过', 'Verification failed');
    case 'cancelled': return uiText('验证已取消', 'Verification cancelled');
    case 'interrupted': return uiText('验证中断，状态待核对', 'Verification interrupted; check its status');
    case 'blocked': return uiText('验证未能启动', 'Verification could not start');
    default: return uiText('尚未验证', 'Not yet verified');
  }
}
export function AgentPanelToggle({ activity = false }: { activity?: boolean }) {
  const agent = useAgent();
  const { state } = useWorkbench();
  const { mode } = useWorkbenchMode();
  const busy = agentIsActive(agent.snapshot?.state) || Boolean(agent.snapshot?.activeVerification);
  const waiting = agent.snapshot?.state === 'waiting_approval';
  if (activity && (!busy || agent.panelOpen && mode === 'professional')) return null;
  return <button type="button" className={activity ? 'agent-activity btn-secondary' : 'agent-panel-toggle btn-secondary'}
    data-testid={activity ? 'agent-activity' : 'professional-agent-toggle'} disabled={!state.workbench}
    aria-expanded={agent.panelOpen && mode === 'professional'} aria-controls="professional-agent-panel" onClick={() => agent.panelOpen && mode === 'professional' ? agent.closePanel() : agent.openPanel()}>
    <Bot size={16} aria-hidden="true" />{waiting ? uiText('Agent · 等待你的处理', 'Agent · Waiting for your input') : busy ? uiText('Agent · 运行中', 'Agent · Running') : 'Agent'}
  </button>;
}

type Draft = { text: string; attachments: AgentAttachment[]; requestId?: string };
const emptyDraft: Draft = { text: '', attachments: [] };
export function ProfessionalAgentPanel() {
  useUiLocale();
  const agent = useAgent(); const { mode } = useWorkbenchMode();
  const { state, openTask, openSource } = useWorkbench();
  const workspaceId = state.workbench?.workspace.id ?? '';
  const visible = agent.panelOpen && mode === 'professional';
  const snapshot = agent.snapshot;
  const [provider, setProvider] = useState<AgentProviderId>('codex');
  const [drafts, setDrafts] = useState<Record<string, Draft>>({});
  const [settingsDrafts, setSettingsDrafts] = useState<Record<string, AgentRunSettings>>({});
  const [tab, setTab] = useState<'conversation' | 'results'>('conversation');
  const [verification, setVerification] = useState<'validate' | 'build'>('build');
  const composer = useRef<HTMLTextAreaElement>(null), stream = useRef<HTMLDivElement>(null);
  const following = useRef(true), composing = useRef(false);
  const appliedEntry = useRef(0);
  const draftKey = `${workspaceId}:${snapshot?.conversationId ?? 'new'}`;
  const draft = drafts[draftKey] ?? emptyDraft;
  const effectiveProvider = snapshot?.provider ?? provider;
  const info = snapshot?.providers.find(item => item.id === effectiveProvider);
  const settingsKey = `${draftKey}:${effectiveProvider}`;
  const settings = settingsDrafts[settingsKey] ?? snapshot?.settings ?? DEFAULT_AGENT_SETTINGS;
  const run = snapshot?.run;
  const verifying = Boolean(snapshot?.activeVerification);
  const busy = agentIsActive(snapshot?.state) || verifying;
  const coreBusy = Object.values(state.tasks).some(task => task.state === 'queued' || task.state === 'running');
  const unsaved = hasUnsavedDrafts(['professional-agent', 'simple-chat']);
  const disabled = busy || Boolean(agent.pending) || snapshot?.enabled === false || snapshot?.writeBlocked;
  const resumeUnavailable = info?.capabilities?.sessionResume === false && Boolean(snapshot?.messages.some(message => message.role === 'user'));
  const permissionHint = permissionDeniedHint(info, agent.error || snapshot?.error);
  const patchDraft = (value: Partial<Draft>) => setDrafts(current => ({ ...current, [draftKey]: { ...(current[draftKey] ?? emptyDraft), ...value, requestId: undefined } }));
  useEffect(() => {
    if (visible) requestAnimationFrame(() => composer.current?.focus());
  }, [visible]);
  useEffect(() => {
    const entry = agent.entry;
    if (!entry || entry.id === appliedEntry.current || entry.workspaceId !== workspaceId) return;
    appliedEntry.current = entry.id; setTab('conversation');
    if (entry.attachment || entry.prompt) setDrafts(current => {
      const previous = current[draftKey] ?? emptyDraft;
      return { ...current, [draftKey]: { text: previous.text || entry.prompt || '', attachments: entry.attachment
        ? [...previous.attachments.filter(item => item.source !== entry.attachment?.source), entry.attachment].slice(-8) : previous.attachments } };
    });
    requestAnimationFrame(() => composer.current?.focus());
  }, [agent.entry, workspaceId, draftKey]);
  useEffect(() => {
    setUnsavedDraftCount('professional-agent', Object.values(drafts).filter(value => value.text.trim() || value.attachments.length).length);
    return () => setUnsavedDraftCount('professional-agent', 0);
  }, [drafts]);
  useEffect(() => { if (visible && tab === 'conversation' && following.current && stream.current) stream.current.scrollTop = stream.current.scrollHeight; }, [snapshot?.sequence, visible, tab]);
  useEffect(() => { if (tab === 'results' && stream.current) stream.current.scrollTop = 0; }, [tab, run?.runId]);

  const send = async () => {
    if (!draft.text.trim() || disabled || resumeUnavailable || !info?.installed || !agent.available || unsaved || draft.text.length > 65536) return;
    const submittedKey = draftKey, submitted = draft;
    const requestId = draft.requestId ?? crypto.randomUUID();
    setTab('conversation');
    setDrafts(current => ({ ...current, [submittedKey]: { ...submitted, requestId } }));
    const before = new Set(snapshot?.messages.map(message => message.id)); following.current = true;
    const next = await agent.invoke({ operation: 'send', provider: effectiveProvider, text: draft.text,
      conversationId: snapshot?.conversationId ?? undefined, requestId, context: draft.attachments,
      ...(info?.capabilities?.runSettings ? { settings } : {}) });
    if (next?.messages.some(message => message.role === 'user' && !before.has(message.id))) setDrafts(current => {
      if (current[submittedKey]?.requestId !== requestId) return current;
      return { ...current, [submittedKey]: emptyDraft };
    });
  };
  const newConversation = async (nextProvider = effectiveProvider) => {
    setProvider(nextProvider);
    const next = await agent.invoke({ operation: 'new', provider: nextProvider });
    if (!snapshot?.conversationId && next?.conversationId) setDrafts(current => ({ ...current, [draftKey]: emptyDraft, [`${workspaceId}:${next.conversationId}`]: current[draftKey] ?? emptyDraft }));
    setTab('conversation'); composer.current?.focus();
  };
  const liveStatus = snapshot?.state === 'waiting_approval' ? uiText('等待你的处理', 'Waiting for your input')
    : snapshot?.state === 'stopping' ? uiText('正在取消', 'Cancelling') : agentIsActive(snapshot?.state) ? uiText('正在运行', 'Running') : agentRunLabel(run);

  if (!visible) return null;
  return <aside id="professional-agent-panel" className="professional-agent-panel" hidden={!visible} aria-label={uiText('项目 Agent', 'Project Agent')}
    data-testid="professional-agent-panel" onKeyDown={event => { if (event.key === 'Escape' && !event.defaultPrevented) { event.preventDefault(); agent.closePanel(); } }}>
    <header className="professional-agent-header"><div><h2><Bot size={18} aria-hidden="true" />{uiText('项目 Agent', 'Project Agent')}</h2>
      <p title={snapshot?.project?.canonicalRoot}>{state.workbench?.workspace.name ?? uiText('未打开项目', 'No open project')} · {snapshot?.project ? uiText('本机', 'This computer') : uiText('未连接', 'Not connected')}</p></div>
      <button type="button" className="btn-icon" aria-label={uiText('收起 Agent 面板', 'Close Agent panel')} onClick={agent.closePanel}><X size={17} aria-hidden="true" /></button>
    </header>
    <div className="professional-agent-controls">
      <AgentProviderSelect variant="professional" value={effectiveProvider} providers={snapshot?.providers ?? []} disabled={Boolean(disabled)} active={visible} detecting={snapshot?.detecting} onChange={value => void newConversation(value)} />
      <button type="button" className="btn-icon" disabled={Boolean(disabled) || !agent.available} aria-label={uiText('新建 Agent 会话', 'New Agent conversation')} onClick={() => void newConversation()}><Plus size={16} aria-hidden="true" /></button>
      <button type="button" className="btn-icon" disabled={Boolean(agent.pending) || snapshot?.detecting || !agent.available} aria-label={uiText('重新检测 CLI', 'Detect CLI again')} onClick={() => void agent.invoke({ operation: 'detect' })}><RefreshCw size={15} aria-hidden="true" /></button>
    </div>
    {Boolean(snapshot?.sessions.length) && <label className="professional-agent-history"><span>{uiText('会话', 'Conversation')}</span><select aria-label={uiText('Agent 会话历史', 'Agent conversation history')}
      value={snapshot?.conversationId ?? ''} disabled={Boolean(busy || agent.pending)} onChange={event => void agent.invoke({ operation: 'select', conversationId: event.target.value })}>
      {snapshot?.sessions.map(session => <option key={session.id} value={session.id}>{session.title}</option>)}</select></label>}
    <div className="professional-agent-status" role="status">{agent.loading || snapshot?.detecting ? uiText('正在检测 CLI…', 'Detecting CLI…') : liveStatus}</div>
    <div className="professional-agent-tabs" role="tablist" aria-label={uiText('Agent 内容', 'Agent content')}>
      {(['conversation', 'results'] as const).map(value => <button type="button" key={value} role="tab" id={`agent-tab-${value}`} aria-selected={tab === value} aria-controls={`agent-view-${value}`}
        tabIndex={tab === value ? 0 : -1} onKeyDown={event => { if (event.key === 'ArrowLeft' || event.key === 'ArrowRight') { const next = value === 'conversation' ? 'results' : 'conversation'; setTab(next); document.getElementById(`agent-tab-${next}`)?.focus(); } }}
        onClick={() => setTab(value)}>{value === 'conversation' ? uiText('对话', 'Conversation') : uiText('变化与验证', 'Changes & verification')}</button>)}
    </div>
    <div className="professional-agent-body" id={`agent-view-${tab}`} role="tabpanel" aria-labelledby={`agent-tab-${tab}`} ref={stream}
      onScroll={event => { const element = event.currentTarget; following.current = element.scrollHeight - element.scrollTop - element.clientHeight < 64; }}>
      {!agent.available && <p data-testid="professional-agent-unavailable">{uiText('请在桌面应用中连接本机 CLI。', 'Connect your local CLI in the desktop app.')}</p>}
      {agent.available && !info?.installed && !agent.loading && <div className="professional-agent-empty"><h3>{uiText('连接你的 CLI Agent', 'Connect your CLI Agent')}</h3>
        <p>{uiText('使用本机已安装的工具和已有登录。项目编辑功能可照常使用。', 'Use an installed tool and its existing sign-in. Project editing remains available.')}</p>
        <button type="button" className="btn-secondary" onClick={() => void agent.invoke({ operation: 'openSetup', provider: effectiveProvider })}>{uiText('官方安装指引', 'Official installation guide')}</button></div>}
      {(agent.error || snapshot?.error || info?.error) && <p role="alert" className="professional-agent-error">{agent.error || snapshot?.error || info?.error}</p>}
      {permissionHint && <p>{permissionHint}</p>}
      {snapshot?.writeBlocked && <p role="alert">{uiText('上次任务的停止尚未确认。请核对执行详情中的进程及其工具活动；当前项目暂不接受新写入。', 'The previous task has not confirmed stopping. Check the recorded process and its tool activity; new writes to this project are blocked.')}</p>}
      {snapshot?.enabled === false && <p>{uiText('新任务已停用；历史和停止操作仍可用。', 'New tasks are disabled; history and stop controls remain available.')}</p>}
      {tab === 'conversation' ? <>
        {!snapshot?.messages.length && info?.installed && <div className="professional-agent-empty"><h3>{uiText('从一个具体问题开始', 'Start with a specific task')}</h3><p>{uiText('描述要完成的修改，或从任务日志、源码附带上下文。', 'Describe a change, or attach context from task logs or source files.')}</p></div>}
        <div className="professional-agent-transcript" role="log" aria-live="off" aria-label={uiText('Agent 对话记录', 'Agent conversation messages')}>
          {snapshot?.messages.map(message => message.role === 'tool' ? <details className="professional-agent-tool" key={message.id}><summary>{uiText('工具操作', 'Tool activity')}</summary><pre tabIndex={0}>{message.text}</pre></details>
            : <article className={`professional-agent-message role-${message.role}`} key={message.id}><div className="professional-agent-message-heading">
              {message.role === 'assistant' && <AgentProviderIcon provider={effectiveProvider} size={16} />}<strong>{message.role === 'user' ? uiText('你', 'You') : message.role === 'assistant' ? 'Agent' : 'Copperbench'}</strong></div><p>{message.text}</p></article>)}
        </div>
      </> : run ? <div className="professional-agent-results">
        <label>{uiText('执行记录', 'Run history')}<select aria-label={uiText('Agent 执行记录', 'Agent run history')} value={run.runId} onChange={event => void agent.invoke({ operation: 'selectRun', runId: event.target.value })}>
          {snapshot?.runs?.map(item => <option key={item.runId} value={item.runId}>{formatDateTime(new Date(item.createdAt).toISOString())} · {agentRunLabel(item)}</option>)}</select></label>
        <h3>{agentRunLabel(run)}</h3><p className="professional-agent-verification" data-testid="agent-verification-status">{verificationLabel(run)}</p>
        {run.verification?.stale && <p role="status">{uiText('验证后文件已有变化，请重新验证。', 'Files changed after verification. Run the check again.')}</p>}
        {run.error && <p className="professional-agent-error">{run.error}</p>}
        {run.agentStatement && <details><summary>{uiText('Agent 的说明', 'Agent statement')}</summary><p>{run.agentStatement}</p></details>}
        <section><h3>{uiText('任务期间的文件变化', 'File changes during this run')}</h3>
          <p>{uiText('比较启动前与结束时的工作区，可能包含你的编辑或其他程序写入。', 'Compares the workspace before and after the run; changes may include your edits or other programs.')}</p>
          {run.changes ? <><p>{run.changes.coverage === 'complete' ? uiText('已采集声明范围内的全部文件。', 'All files in the declared scope were captured.') : uiText('仅采集了部分范围；请查看范围说明。', 'Coverage is partial; review the scope details.')}</p>
            {!run.changes.files.length && <p>{uiText('采集范围内未发现文件变化。', 'No file changes found within the captured scope.')}</p>}
            {run.changes.files.map(file => <details className="professional-agent-file" key={file.path}><summary>{file.path} · {file.kind === 'added' ? uiText('新增', 'Added') : file.kind === 'deleted' ? uiText('删除', 'Deleted') : file.kind === 'unobserved' ? uiText('未完整采集', 'Partially captured') : uiText('修改', 'Modified')}</summary>
              <div className="professional-agent-diff">{(['before', 'after'] as const).map(side => <div key={side}><h4>{side === 'before' ? uiText('任务开始前', 'Before this run') : uiText('结束采集时', 'At final capture')}</h4>
                <pre tabIndex={0}>{file[side].text ?? (file[side].omitted ? uiText('文本未采集：', 'Text unavailable: ') + file[side].omitted : uiText('文件不存在', 'File absent'))}</pre></div>)}</div>
              {file.kind !== 'deleted' && <button type="button" className="btn-secondary" onClick={() => openSource(file.path)}>{uiText('打开当前文件', 'Open current file')}</button>}
              <button type="button" className="btn-secondary" onClick={() => { agent.openPanel(agentAttachment(file.path, 'run-diff', JSON.stringify(file)), uiText('请审查这次文件变化。', 'Review these file changes.')); }}>{uiText('让 Agent 审查', 'Review with Agent')}</button>
            </details>)}
            <details><summary>{uiText('采集范围', 'Capture scope')}</summary><p>{run.changes.scope}</p></details></> : <p>{uiText('执行结束后采集差异。', 'Changes are captured when execution finishes.')}</p>}
        </section>
        <section className="professional-agent-check"><h3>{uiText('验证', 'Verification')}</h3>
          <select aria-label={uiText('选择验证命令', 'Choose verification command')} value={verification} disabled={Boolean(busy)} onChange={event => setVerification(event.target.value as 'validate' | 'build')}>
            <option value="build">{uiText('构建工作区', 'Build workspace')}</option><option value="validate">{uiText('验证工作区配置', 'Validate workspace configuration')}</option></select>
          <code>{verification === 'build' ? 'build_workspace' : 'validate_workspace'}</code><p className="professional-agent-path">{run.canonicalRoot}</p>
          <button type="button" className="btn-secondary" disabled={Boolean(disabled || coreBusy || !snapshot?.verificationAvailable || !['completed', 'failed', 'cancelled'].includes(run.state))}
            onClick={() => void agent.invoke({ operation: 'verify', runId: run.runId, kind: verification, requestId: crypto.randomUUID() })}>{uiText('运行所选验证', 'Run selected verification')}</button>
          {run.verification?.executionTaskId && <button type="button" className="btn-secondary" onClick={() => openTask(run.verification!.executionTaskId!)}>{uiText('查看验证任务', 'View verification task')}</button>}
          {run.verification?.evidence !== undefined && <details><summary>{uiText('验证执行记录', 'Verification execution record')}</summary><pre tabIndex={0}>{JSON.stringify(run.verification.evidence, null, 2)}</pre></details>}
        </section>
        <details><summary>{uiText('本次附带内容', 'Attached context')}</summary><p>{run.context?.task}</p>{run.context?.attachments.map((item, index) => <div key={index}><strong>{item.source}{item.truncated ? uiText('（已截断）', ' (truncated)') : ''}</strong><pre tabIndex={0}>{item.text}</pre></div>)}</details>
        <details><summary>{uiText('执行详情', 'Execution details')}</summary><dl><dt>{'CLI'}</dt><dd>{run.agentId} · {run.cliVersion ?? uiText('版本未检测', 'Version not detected')}</dd>
          <dt>{uiText('运行位置', 'Execution location')}</dt><dd>{run.executionTargetId}<br />{run.canonicalRoot}</dd>
          <dt>{uiText('执行结果', 'Execution result')}</dt><dd><pre>{JSON.stringify(run.execution, null, 2)}</pre></dd><dt>{uiText('运行 ID', 'Run ID')}</dt><dd>{run.runId}</dd></dl></details>
        <button type="button" className="btn-secondary" disabled={Boolean(agent.pending)} onClick={() => void agent.invoke({ operation: 'exportRun', runId: run.runId })}><Download size={14} aria-hidden="true" />{uiText('导出本次脱敏记录', 'Export redacted run record')}</button>
        {snapshot?.exportedRun?.runId === run.runId && <p role="status" className="professional-agent-path">{uiText('已保存：', 'Saved: ')}{snapshot.exportedRun.path}</p>}
      </div> : <p>{uiText('提交任务后，这里会保存变化和验证结果。', 'After you submit a task, its changes and verification results appear here.')}</p>}
    </div>
    {/* As in the simple workbench, pending approvals stay beside the composer on every tab. */}
    {Boolean(snapshot?.approvals.length) && <div className="professional-agent-approvals">
      {snapshot?.approvals.map(approval => <AgentApprovalPrompt variant="professional" key={approval.id} approval={approval} disabled={Boolean(agent.pending)} onRespond={answer => void agent.invoke({ operation: 'approve', id: approval.id, ...answer })} />)}
    </div>}
    <AgentRunSettingsControl provider={info} settings={settings} disabled={Boolean(disabled)} onChange={next => {
      setSettingsDrafts(current => ({ ...current, [settingsKey]: next })); patchDraft({});
    }} />
    <form className="professional-agent-composer" onSubmit={event => { event.preventDefault(); void send(); }}>
      {draft.attachments.map((item, index) => <details className="professional-agent-attachment" key={`${item.source}:${index}`}><summary>{item.source}{item.truncated ? uiText('（已截断）', ' (truncated)') : ''}</summary>
        <textarea aria-label={uiText('附带内容', 'Attached content')} value={item.text} onChange={event => patchDraft({ attachments: draft.attachments.map((value, position) => position === index ? { ...value, text: event.target.value } : value) })} />
        <button type="button" onClick={() => patchDraft({ attachments: draft.attachments.filter((_, position) => position !== index) })}>{uiText('移除附件', 'Remove attachment')}</button></details>)}
      <label htmlFor="professional-agent-prompt">{uiText('与 Agent 对话', 'Message Agent')}</label>
      <div className="professional-agent-compose-box">
      <textarea id="professional-agent-prompt" data-testid="professional-agent-prompt" ref={composer} value={draft.text} rows={3} placeholder={uiText('描述要分析或修改的内容…', 'Describe what to analyze or change…')}
        onChange={event => patchDraft({ text: event.target.value })} onCompositionStart={() => { composing.current = true; }} onCompositionEnd={() => { composing.current = false; }}
        onKeyDown={event => { if ((event.ctrlKey || event.metaKey) && event.key === 'Enter' && !composing.current && !event.nativeEvent.isComposing) { event.preventDefault(); void send(); } }} />
      <div className="professional-agent-send"><small>{'Ctrl / ⌘ + Enter'}</small>
        {(agentIsActive(snapshot?.state) || verifying) ? <button type="button" className="btn-danger" data-testid="professional-agent-stop"
      disabled={Boolean(agent.pending) || snapshot?.state === 'stopping' || Boolean(snapshot?.activeVerification?.cancelling)}
      onClick={() => void agent.invoke(snapshot?.activeVerification ? { operation: 'stopVerification', runId: snapshot.activeVerification.runId } : { operation: 'stop' })}>
          <Square size={14} aria-hidden="true" />{verifying ? uiText('停止验证', 'Stop verification') : uiText('停止 Agent', 'Stop Agent')}</button>
          : <button type="submit" className="btn-primary" data-testid="professional-agent-send"
            disabled={Boolean(disabled || resumeUnavailable || unsaved || !agent.available || !info?.installed || !draft.text.trim() || draft.text.length > 65536)}><ArrowUp size={15} aria-hidden="true" />{uiText('发送', 'Send')}</button>}
      </div></div>
      {unsaved && <p>{uiText('先保存或放弃编辑器中的修改，再交给 Agent。', 'Save or discard editor changes before handing the task to Agent.')}</p>}
      {resumeUnavailable && <p>{uiText('此 Agent 不支持继续会话，请新建会话后发送。', 'This agent does not support session continuation. Start a new conversation to send another task.')}</p>}
      {draft.text.length > 65536 && <p role="alert">{uiText('任务说明超过 65,536 字符，请缩短后发送。', 'Task instructions exceed 65,536 characters. Shorten them before sending.')}</p>}
    </form>
  </aside>;
}
