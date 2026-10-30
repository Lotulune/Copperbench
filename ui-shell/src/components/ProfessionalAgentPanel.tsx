import { useEffect, useRef, useState } from 'react';
import { ArrowUp, Bot, Brain, Download, History, Paperclip, Plus, RefreshCw, ShieldCheck, Square, X } from 'lucide-react';
import { useAgent } from '../context/AgentContext';
import { useWorkbench } from '../context/WorkbenchContext';
import { useWorkbenchMode } from '../context/WorkbenchModeContext';
import { agentIsActive, agentText, AGENT_EFFORTS, DEFAULT_AGENT_SETTINGS, type AgentContextAttachment, type AgentPermission, type AgentProviderId, type AgentRunRecord, type AgentRunSettings } from '../bridge/agentBridge';
import { hasUnsavedDrafts, setUnsavedDraftCount } from '../hooks/unsavedDraftGuard';
import { renderUiMessage, uiMessage, uiText, useUiLocale } from '../i18n';
import { formatDateTime } from '../i18n/dateTime';
import { AgentProviderSelect } from './AgentProviderSelect';
import { AgentProviderIcon } from './AgentProviderIcon';
import { AgentApprovalPrompt, permissionDeniedHint } from './AgentApprovalPrompt';
import { AgentModelSelect } from './AgentModelSelect';
import { AgentOptionSelect } from './AgentOptionSelect';
import { AgentMessageBody } from './AgentMessageBody';
import { AgentAttachments } from './AgentAttachments';
import { AgentHistoryDialog } from './AgentHistoryDialog';
import { useAgentAttachments } from '../hooks/useAgentAttachments';
import { confirmAction } from '../feedback/feedback';
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
    case 'interrupted': return run.execution.cleanup === 'unconfirmed' ? uiText('任务已中断，停止未确认', 'Interrupted; stop not confirmed') : uiText('结果采集中断', 'Result collection interrupted');
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
  const busy = agentIsActive(agent.snapshot?.state) || Boolean(agent.snapshot?.activeVerification) || Boolean(agent.snapshot?.checking);
  if (activity && (!busy || agent.panelOpen && mode === 'professional')) return null;
  // A collapsed panel still says when the agent is blocked on the user rather than merely running.
  const waiting = agent.snapshot?.state === 'waiting_approval';
  const label = waiting ? uiText('Agent · 等待你的处理', 'Agent · Waiting for your input') : busy ? uiText('Agent · 运行中', 'Agent · Running') : 'Agent';
  return <button type="button" className={activity ? 'agent-activity btn-secondary' : 'agent-panel-toggle btn-secondary'}
    data-testid={activity ? 'agent-activity' : 'professional-agent-toggle'} disabled={!state.workbench}
    aria-label={label} title={label}
    aria-expanded={agent.panelOpen && mode === 'professional'} aria-controls="professional-agent-panel" onClick={() => agent.panelOpen && mode === 'professional' ? agent.closePanel() : agent.openPanel()}>
    <Bot size={16} aria-hidden="true" /><span className="agent-toggle-label">{label}</span>
  </button>;
}

type Draft = { text: string; attachments: AgentContextAttachment[]; requestId?: string };
const emptyDraft: Draft = { text: '', attachments: [] };
export function ProfessionalAgentPanel() {
  useUiLocale();
  const agent = useAgent(); const { mode } = useWorkbenchMode();
  const { state, setActiveTaskId, setIsTaskDrawerOpen, openSource } = useWorkbench();
  const openTask = (id: string) => { setActiveTaskId(id); setIsTaskDrawerOpen(true); };
  const workspaceId = state.workbench?.workspace.id ?? '';
  const visible = agent.panelOpen && mode === 'professional';
  const snapshot = agent.snapshot;
  const [provider, setProvider] = useState<AgentProviderId>('codex');
  const [drafts, setDrafts] = useState<Record<string, Draft>>({});
  const [settingsDrafts, setSettingsDrafts] = useState<Record<string, AgentRunSettings>>({});
  const [historyOpen, setHistoryOpen] = useState(false);
  const fileInput = useRef<HTMLInputElement>(null);
  const submission = useRef<{ key: string; requestId: string; previous: Set<string>; attachments: string[] } | null>(null);
  const [tab, setTab] = useState<'conversation' | 'results'>('conversation');
  const [verification, setVerification] = useState<'validate' | 'build'>('build');
  const composer = useRef<HTMLTextAreaElement>(null), stream = useRef<HTMLDivElement>(null);
  const following = useRef(true), composing = useRef(false);
  const appliedEntry = useRef(0);
  const draftKey = `${workspaceId}:${snapshot?.conversationId ?? 'new'}`;
  const draft = drafts[draftKey] ?? emptyDraft;
  const attached = useAgentAttachments(workspaceId || undefined, draftKey, visible);
  const effectiveProvider = snapshot?.provider ?? provider;
  const info = snapshot?.providers.find(item => item.id === effectiveProvider);
  const permissionHint = permissionDeniedHint(info, agent.error || snapshot?.error);
  const settingsKey = `${draftKey}:${effectiveProvider}`;
  const settings = settingsDrafts[settingsKey] ?? snapshot?.settings ?? DEFAULT_AGENT_SETTINGS;
  const run = snapshot?.run;
  const verifying = Boolean(snapshot?.activeVerification);
  const busy = agentIsActive(snapshot?.state) || verifying || Boolean(snapshot?.checking);
  const coreBusy = Object.values(state.tasks).some(task => task.state === 'queued' || task.state === 'running');
  const unsaved = hasUnsavedDrafts(['professional-agent', 'simple-chat']);
  const disabled = busy || Boolean(agent.pending) || snapshot?.enabled === false || snapshot?.writeBlocked || snapshot?.storageBlocked;
  const needsModel = effectiveProvider !== 'codex' && !snapshot?.model && !snapshot?.usesSessionModel;
  const resumeUnavailable = info?.capabilities?.sessionResume === false && Boolean(snapshot?.messages.some(message => message.role === 'user'));
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
    setUnsavedDraftCount('professional-agent', Object.values(drafts).filter(value => value.text.trim() || value.attachments.length).length + attached.draftCount);
    return () => setUnsavedDraftCount('professional-agent', 0);
  }, [drafts, attached.draftCount]);
  useEffect(() => {
    if (visible && effectiveProvider !== 'codex' && info?.installed && !info.modelCatalog && !agent.pending && !busy)
      void agent.invoke({ operation: 'models', provider: effectiveProvider });
  }, [visible, effectiveProvider, info?.installed, info?.modelCatalog, agent.pending, busy, agent.invoke]);
  useEffect(() => {
    const pending = submission.current;
    if (!pending || !snapshot) return;
    const sent = snapshot.messages.some(message => message.role === 'user' && !pending.previous.has(message.id) && message.delivery !== 'pending' && message.delivery !== 'failed');
    if (sent) {
      setDrafts(current => current[pending.key]?.requestId === pending.requestId ? { ...current, [pending.key]: emptyDraft } : current);
      attached.accepted(pending.key, pending.attachments); submission.current = null;
    } else if (!agentIsActive(snapshot.state) && snapshot.run?.requestId === pending.requestId) {
      setDrafts(current => current[pending.key]?.requestId === pending.requestId ? { ...current, [pending.key]: { ...current[pending.key], requestId: undefined } } : current);
      submission.current = null;
    }
  }, [snapshot?.sequence]);
  useEffect(() => { if (visible && tab === 'conversation' && following.current && stream.current) stream.current.scrollTop = stream.current.scrollHeight; }, [snapshot?.sequence, visible, tab]);
  useEffect(() => { if (tab === 'results' && stream.current) stream.current.scrollTop = 0; }, [tab, run?.runId]);

  const send = async () => {
    if ((!draft.text.trim() && !attached.files.length) || disabled || attached.blocked || needsModel || resumeUnavailable || !info?.installed || !agent.available || unsaved || draft.text.length > 65536) return;
    const submittedKey = draftKey, submitted = draft;
    const requestId = draft.requestId ?? crypto.randomUUID();
    setTab('conversation');
    setDrafts(current => ({ ...current, [submittedKey]: { ...submitted, requestId } }));
    const before = new Set(snapshot?.messages.map(message => message.id)); following.current = true;
    const attachmentIds = attached.files.flatMap(file => file.item ? [file.item.id] : []);
    const pending = { key: submittedKey, requestId, previous: before, attachments: attachmentIds }; submission.current = pending;
    const next = await agent.invoke({ operation: 'send', provider: effectiveProvider, text: draft.text,
      conversationId: snapshot?.conversationId ?? undefined, requestId, context: draft.attachments, attachments: attachmentIds,
      ...(info?.capabilities?.runSettings && (settingsDrafts[settingsKey] || snapshot?.settingsExplicit) ? { settings } : {}),
      ...(!info?.capabilities?.runSettings ? { effort: settings.effort || undefined, permission: settings.permissionProfile === 'workspace' ? 'default' : settings.permissionProfile } : {}) });
    if (!snapshot?.conversationId && next?.conversationId && submission.current === pending) {
      const nextKey = `${workspaceId}:${next.conversationId}`; pending.key = nextKey; attached.move(submittedKey, nextKey);
      setDrafts(current => { const moved = { ...current, [nextKey]: current[submittedKey] ?? { ...submitted, requestId } }; delete moved[submittedKey]; return moved; });
      setSettingsDrafts(current => ({ ...current, [`${nextKey}:${effectiveProvider}`]: settings }));
    }
    if (next?.error && !next.messages.some(message => message.role === 'user' && !before.has(message.id))) {
      submission.current = null;
      setDrafts(current => current[submittedKey]?.requestId === requestId ? { ...current, [submittedKey]: { ...current[submittedKey], requestId: undefined } } : current);
    }
  };
  const newConversation = async (nextProvider = effectiveProvider, model?: string, carryDraft = false, nextSettings?: AgentRunSettings) => {
    if (busy || agent.pending || attached.uploading) return null;
    setProvider(nextProvider);
    const desired = nextSettings ?? { ...DEFAULT_AGENT_SETTINGS, model: model ?? '' };
    const next = await agent.invoke({ operation: 'new', provider: nextProvider, ...(model ? { model } : {}),
      ...(desired.effort ? { effort: desired.effort } : {}), permission: desired.permissionProfile === 'workspace' ? 'default' : desired.permissionProfile });
    if (!next?.error && next?.conversationId && (!snapshot?.conversationId || carryDraft)) {
      const nextKey = `${workspaceId}:${next.conversationId}`; attached.move(draftKey, nextKey);
      setDrafts(current => ({ ...current, [draftKey]: emptyDraft, [nextKey]: current[draftKey] ?? emptyDraft }));
      setSettingsDrafts(current => ({ ...current, [`${nextKey}:${nextProvider}`]: desired }));
    }
    setTab('conversation'); composer.current?.focus();
    return next;
  };
  const changeSettings = async (next: AgentRunSettings) => {
    if (busy || agent.pending) return;
    if (next.permissionProfile === 'auto-approve' && settings.permissionProfile !== 'auto-approve' && !await confirmAction({
      title: uiMessage('允许此对话自动批准操作？', 'Automatically approve actions in this conversation?'),
      message: uiMessage('CLI 可以自动执行命令、修改文件和访问网络。Codex 会同时取消工作区沙箱限制。此设置仅用于本应用内的当前对话，不改写主机配置。', 'The CLI can run commands, change files and access the network without asking. Codex also removes its workspace sandbox. This applies only to this in-app conversation and does not change host settings.'),
      confirmLabel: uiMessage('启用自动批准', 'Enable automatic approval')
    })) return;
    if (snapshot?.conversationId && next.model !== (snapshot.model ?? '')) { await newConversation(effectiveProvider, next.model, true, next); return; }
    setSettingsDrafts(current => ({ ...current, [settingsKey]: next })); patchDraft({});
  };
  const discardHistoryDrafts = (ids: string[]) => {
    const keys = new Set(ids.map(id => `${workspaceId}:${id}`)); keys.forEach(key => attached.discardDraft(key));
    setDrafts(current => Object.fromEntries(Object.entries(current).filter(([key]) => !keys.has(key))));
    setSettingsDrafts(current => Object.fromEntries(Object.entries(current).filter(([key]) => ![...keys].some(prefix => key.startsWith(`${prefix}:`)))));
    if (submission.current && keys.has(submission.current.key)) submission.current = null;
  };
  const liveStatus = snapshot?.activeRunPhase === 'finalizing' ? uiText('正在保存结果', 'Saving results') : snapshot?.checking ? uiText('正在检查连接', 'Checking connection') : snapshot?.state === 'waiting_approval' ? uiText('等待你的处理', 'Waiting for your input')
    : snapshot?.state === 'stopping' ? uiText('正在取消', 'Cancelling') : agentIsActive(snapshot?.state) ? uiText('正在运行', 'Running') : agentRunLabel(run);

  if (!visible) return null;
  return <aside id="professional-agent-panel" className="professional-agent-panel" hidden={!visible} aria-label={uiText('项目 Agent', 'Project Agent')}
    data-testid="professional-agent-panel" onKeyDown={event => { if (event.key === 'Escape' && !event.defaultPrevented) { event.preventDefault(); agent.closePanel(); } }}>
    <header className="professional-agent-header"><div><h2><Bot size={18} aria-hidden="true" />{uiText('项目 Agent', 'Project Agent')}</h2>
      <p title={snapshot?.project?.canonicalRoot}>{state.workbench?.workspace.name ?? uiText('未打开项目', 'No open project')} · {snapshot?.project ? uiText('本机', 'This computer') : uiText('未连接', 'Not connected')}</p></div>
      <button type="button" className="btn-icon" aria-label={uiText('收起 Agent 面板', 'Close Agent panel')} onClick={agent.closePanel}><X size={17} aria-hidden="true" /></button>
    </header>
    <div className="professional-agent-controls">
      <AgentProviderSelect id="professional-provider" testId="professional-provider" value={effectiveProvider} providers={snapshot?.providers ?? []} disabled={Boolean(disabled || attached.uploading)} active={visible} detecting={snapshot?.detecting} onChange={value => void newConversation(value)} />
      <button type="button" className="btn-icon" aria-label={uiText('整理对话记录', 'Manage conversation history')} onClick={() => setHistoryOpen(true)}><History size={16} aria-hidden="true" /></button>
      <button type="button" className="btn-icon" disabled={Boolean(disabled) || !agent.available} aria-label={uiText('新建 Agent 会话', 'New Agent conversation')} onClick={() => void newConversation()}><Plus size={16} aria-hidden="true" /></button>
      <button type="button" className="btn-icon" disabled={Boolean(agent.pending) || snapshot?.detecting || !agent.available} aria-label={uiText('重新检测 CLI', 'Detect CLI again')} onClick={() => void agent.invoke({ operation: 'detect' })}><RefreshCw size={15} aria-hidden="true" /></button>
    </div>
    {info && !info.capabilities?.runSettings && <div className="professional-agent-model-options">
      <AgentModelSelect testId="professional-model" value={snapshot?.model ?? ''} usesSessionModel={snapshot?.usesSessionModel} catalog={info.modelCatalog} disabled={Boolean(disabled || attached.uploading)} active={visible}
        onChange={model => void newConversation(effectiveProvider, model, true, { ...settings, model })} onRefresh={() => void agent.invoke({ operation: 'models', provider: effectiveProvider })} />
      <AgentOptionSelect value={settings.effort || snapshot?.effort || 'medium'} options={AGENT_EFFORTS.map(effort => ({ value: effort, label: effort }))} icon={<Brain size={14} aria-hidden="true" />} label={uiText('思考强度', 'Reasoning effort')} testId="professional-effort" disabled={Boolean(disabled)} active={visible}
        onChange={effort => void changeSettings({ ...settings, effort })} />
      <AgentOptionSelect<AgentPermission> value={settings.permissionProfile === 'workspace' ? 'default' : settings.permissionProfile} options={[{ value: 'default', label: uiText('默认权限', 'Default permissions') }, { value: 'auto-approve', label: uiText('自动批准', 'Auto-approve') }]}
        icon={<ShieldCheck size={14} aria-hidden="true" />} label={uiText('执行权限', 'Execution permissions')} testId="professional-permission" disabled={Boolean(disabled)} active={visible}
        onChange={permission => void changeSettings({ ...settings, permissionProfile: permission === 'default' ? 'workspace' : 'auto-approve' })} />
    </div>}
    {Boolean(snapshot?.sessions.length) && <label className="professional-agent-history"><span>{uiText('会话', 'Conversation')}</span><select aria-label={uiText('Agent 会话历史', 'Agent conversation history')}
      value={snapshot?.conversationId ?? ''} disabled={Boolean(busy || agent.pending)} onChange={event => void agent.invoke({ operation: 'select', conversationId: event.target.value })}>
      {snapshot?.sessions.map(session => <option key={session.id} value={session.id}>{agentText(session.title, session.titleText)}</option>)}</select></label>}
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
      {(agent.error || snapshot?.error || info?.error) && <p role="alert" className="professional-agent-error">{agent.error || agentText(snapshot?.error, snapshot?.errorText) || agentText(info?.error, info?.errorText)}</p>}
      {permissionHint && <p className="professional-agent-error">{permissionHint}</p>}
      {snapshot?.runHistoryCapacity?.level === 'full' && <p role="status">{uiText('运行记录已满，请在会话历史中导出并删除已结束的会话。', 'Run history is full. Export and delete finished conversations in conversation history.')}<button type="button" className="btn-secondary" onClick={() => setHistoryOpen(true)}>{uiText('整理记录', 'Manage history')}</button></p>}
      {snapshot?.writeBlocked && <p role="alert">{uiText('上次任务的停止尚未确认。请核对执行详情中的进程及其工具活动；当前项目暂不接受新写入。', 'The previous task has not confirmed stopping. Check the recorded process and its tool activity; new writes to this project are blocked.')}</p>}
      {snapshot?.enabled === false && <p>{uiText('新任务已停用；历史和停止操作仍可用。', 'New tasks are disabled; history and stop controls remain available.')}</p>}
      {tab === 'conversation' ? <>
        {!snapshot?.messages.length && info?.installed && <div className="professional-agent-empty"><h3>{uiText('从一个具体问题开始', 'Start with a specific task')}</h3><p>{uiText('描述要完成的修改，或从任务日志、源码附带上下文。', 'Describe a change, or attach context from task logs or source files.')}</p></div>}
        <div className="professional-agent-transcript" role="log" aria-live="off" aria-label={uiText('Agent 对话记录', 'Agent conversation messages')}>
          {snapshot?.messages.map(message => message.role === 'tool' ? <details className="professional-agent-tool" key={message.id}><summary>{uiText('工具操作', 'Tool activity')}</summary><pre tabIndex={0}>{message.text}</pre></details>
            : <article className={`professional-agent-message role-${message.role}`} key={message.id}><div className="professional-agent-message-heading">
              {message.role === 'assistant' && <AgentProviderIcon provider={effectiveProvider} size={16} />}<strong>{message.role === 'user' ? uiText('你', 'You') : message.role === 'assistant' ? 'Agent' : 'Copperbench'}</strong></div>
              <div className="professional-agent-message-content">{message.role === 'assistant' ? <AgentMessageBody text={message.text} /> : <p>{message.role === 'system' ? agentText(message.text, message.textText) : message.text}</p>}
              <AgentAttachments key={`${workspaceId}:${message.id}`} files={message.attachments ?? []} />
              {message.delivery === 'pending' && <small>{uiText('正在发送…', 'Sending…')}</small>}{message.delivery === 'failed' && <small>{uiText('未发送给模型，草稿已保留。', 'Not sent to the model. The draft is retained.')}</small>}</div></article>)}
        </div>
      </> : run ? <div className="professional-agent-results">
        <label>{uiText('执行记录', 'Run history')}<select aria-label={uiText('Agent 执行记录', 'Agent run history')} value={run.runId} onChange={event => void agent.invoke({ operation: 'selectRun', runId: event.target.value })}>
          {snapshot?.runs?.map(item => <option key={item.runId} value={item.runId}>{formatDateTime(new Date(item.createdAt).toISOString())} · {agentRunLabel(item)}</option>)}</select></label>
        <h3>{agentRunLabel(run)}</h3><p className="professional-agent-verification" data-testid="agent-verification-status">{verificationLabel(run)}</p>
        {run.verification?.stale && <p role="status">{run.verification.fingerprintError
          ? uiText('无法核对验证后的文件状态，请重新验证。', 'The final file state could not be checked. Run verification again.')
          : uiText('验证后文件已有变化，请重新验证。', 'Files changed after verification. Run the check again.')}</p>}
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
    <AgentRunSettingsControl provider={info} settings={settings} disabled={Boolean(disabled || attached.uploading)} onChange={next => void changeSettings(next)} />
    <form className="professional-agent-composer" onSubmit={event => { event.preventDefault(); void send(); }}>
      <input ref={fileInput} type="file" multiple hidden onChange={event => { void attached.addFiles(Array.from(event.currentTarget.files ?? [])); event.currentTarget.value = ''; }} />
      <AgentAttachments files={attached.files} onRemove={attached.remove} />
      {attached.error && <p role="alert">{renderUiMessage(attached.error)}</p>}
      {draft.attachments.map((item, index) => <details className="professional-agent-attachment" key={`${item.source}:${index}`}><summary>{item.source}{item.truncated ? uiText('（已截断）', ' (truncated)') : ''}</summary>
        <textarea aria-label={uiText('附带内容', 'Attached content')} value={item.text} onChange={event => patchDraft({ attachments: draft.attachments.map((value, position) => position === index ? { ...value, text: event.target.value } : value) })} />
        <button type="button" onClick={() => patchDraft({ attachments: draft.attachments.filter((_, position) => position !== index) })}>{uiText('移除附件', 'Remove attachment')}</button></details>)}
      <label htmlFor="professional-agent-prompt">{uiText('与 Agent 对话', 'Message Agent')}</label>
      <div className="professional-agent-compose-box">
      <textarea id="professional-agent-prompt" data-testid="professional-agent-prompt" ref={composer} value={draft.text} rows={3} placeholder={uiText('描述要分析或修改的内容…', 'Describe what to analyze or change…')}
        onChange={event => patchDraft({ text: event.target.value })} onCompositionStart={() => { composing.current = true; }} onCompositionEnd={() => { composing.current = false; }}
        onPaste={event => { if (event.clipboardData.files.length) { event.preventDefault(); void attached.addFiles(Array.from(event.clipboardData.files)); } }}
        onDragOver={event => { if (event.dataTransfer.types.includes('Files')) event.preventDefault(); }}
        onDrop={event => { if (event.dataTransfer.files.length) { event.preventDefault(); void attached.addFiles(Array.from(event.dataTransfer.files)); } }}
        onKeyDown={event => { if ((event.ctrlKey || event.metaKey) && event.key === 'Enter' && !composing.current && !event.nativeEvent.isComposing) { event.preventDefault(); void send(); } }} />
      <div className="professional-agent-send"><button type="button" className="btn-icon" aria-label={uiText('添加文件和图片', 'Add files and images')} disabled={!agent.available || attached.uploading} onClick={() => window.__COPPERBENCH_AGENT_HOST__?.nativeFileSelection ? void attached.selectNative() : fileInput.current?.click()}><Paperclip size={16} aria-hidden="true" /></button><small>{'Ctrl / ⌘ + Enter'}</small>
        {(agentIsActive(snapshot?.state) || verifying || snapshot?.checking) ? <button type="button" className="btn-danger" data-testid="professional-agent-stop"
      disabled={Boolean(agent.pending) || snapshot?.state === 'stopping' || snapshot?.activeRunPhase === 'finalizing' || Boolean(snapshot?.activeVerification?.cancelling)}
      onClick={() => void agent.invoke(snapshot?.activeVerification ? { operation: 'stopVerification', runId: snapshot.activeVerification.runId } : { operation: 'stop' })}>
          <Square size={14} aria-hidden="true" />{verifying ? uiText('停止验证', 'Stop verification') : snapshot?.checking ? uiText('停止连接检查', 'Stop connection check') : uiText('停止 Agent', 'Stop Agent')}</button>
          : <button type="submit" className="btn-primary" data-testid="professional-agent-send"
            disabled={Boolean(disabled || attached.blocked || needsModel || resumeUnavailable || unsaved || !agent.available || !info?.installed || (!draft.text.trim() && !attached.files.length) || draft.text.length > 65536)}><ArrowUp size={15} aria-hidden="true" />{uiText('发送', 'Send')}</button>}
      </div></div>
      {unsaved && <p>{uiText('先保存或放弃编辑器中的修改，再交给 Agent。', 'Save or discard editor changes before handing the task to Agent.')}</p>}
      {needsModel && <p>{uiText('先选择或输入要使用的模型。', 'Choose or enter the model to use first.')}</p>}
      {resumeUnavailable && <p>{uiText('此 Agent 不支持继续会话，请新建会话后发送。', 'This agent does not support session continuation. Start a new conversation to send another task.')}</p>}
      {draft.text.length > 65536 && <p role="alert">{uiText('任务说明超过 65,536 字符，请缩短后发送。', 'Task instructions exceed 65,536 characters. Shorten them before sending.')}</p>}
    </form>
    {historyOpen && <AgentHistoryDialog snapshot={snapshot} busy={Boolean(busy)} pending={Boolean(agent.pending)} invoke={agent.invoke} onClose={() => setHistoryOpen(false)} onDeleted={discardHistoryDrafts}
      draftConversationIds={[...new Set([...Object.keys(drafts).filter(key => drafts[key].text.trim() || drafts[key].attachments.length), ...attached.draftKeys].filter(key => key.startsWith(`${workspaceId}:`)).map(key => key.slice(workspaceId.length + 1)))]} />}
  </aside>;
}
