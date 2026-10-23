import { useEffect, useMemo, useRef, useState, type FormEvent, type ReactNode } from 'react';
import { ArrowLeft, ArrowUp, Box, Brain, Check, ChevronDown, ExternalLink, FileArchive, FolderOpen, HelpCircle, History, Image, LoaderCircle, Paperclip, Plus, RefreshCw, Settings, ShieldCheck, Square, Play } from 'lucide-react';
import { AGENT_EFFORTS, AGENT_PROVIDERS, agentIsActive, agentText, validModelId, type AgentEffort, type AgentPermission, type AgentProviderId, type AgentTurnOptions } from '../bridge/agentBridge';
import { useAgent } from '../context/AgentContext';
import { useAgentAttachments } from '../hooks/useAgentAttachments';
import { hasUnsavedDrafts, setUnsavedDraftCount } from '../hooks/unsavedDraftGuard';
import { useWorkbench } from '../context/WorkbenchContext';
import { useWorkbenchMode } from '../context/WorkbenchModeContext';
import { useFirstCreation } from '../onboarding/FirstCreationContext';
import { assetRecordsFromProjection, type AssetRecord } from '../types/assets';
import type { EditorField, FieldChange, ModElementEditorProjection } from '../types/contract';
import { elementShortLabel } from '../i18n/labels';
import { renderUiMessage, t, uiMessage, uiText, useUiLocale } from '../i18n';
import { confirmAction } from '../feedback/feedback';
import { AssetPreview } from './AssetPreview';
import { ElementVisualPreview, hasElementVisualPreview } from './ElementVisualPreview';
import { workbenchNavigation } from './workbenchNavigation';
import { newestTasks } from './taskPresentation';
import { AgentProviderIcon, AGENT_PROVIDER_NAMES as providerNames } from './AgentProviderIcon';
import { AgentProviderSelect } from './AgentProviderSelect';
import { AgentModelSelect } from './AgentModelSelect';
import { AgentOptionSelect } from './AgentOptionSelect';
import { AgentAttachments } from './AgentAttachments';
import { AgentApprovalPrompt } from './AgentApprovalPrompt';
import { AgentMessageBody } from './AgentMessageBody';
import { AgentSetupDialog, providerStatus } from './AgentSetupDialog';
import { AgentHistoryDialog } from './AgentHistoryDialog';
import { useWorkbenchTour } from '../onboarding/WorkbenchTour';
import './agentExperience.css';

const PROVIDER_PREFERENCE = 'copperbench.agent.provider';
const MODEL_PREFERENCES = 'copperbench.agent.models';
function savedProvider(): AgentProviderId | null {
  try { const id = localStorage.getItem(PROVIDER_PREFERENCE); return AGENT_PROVIDERS.includes(id as AgentProviderId) ? id as AgentProviderId : null; }
  catch { return null; }
}
function savedModels(): Partial<Record<AgentProviderId, string>> {
  try {
    const value = JSON.parse(localStorage.getItem(MODEL_PREFERENCES) ?? '{}');
    return Object.fromEntries(AGENT_PROVIDERS.flatMap(id => typeof value?.[id] === 'string' && (value[id] === '' || validModelId(value[id])) ? [[id, value[id]]] : []));
  } catch { return {}; }
}


type ParameterSession = { workspaceId: string; editor: ModElementEditorProjection; revision: number; values: Record<string, string | boolean>; saving?: boolean; saveUnknown?: boolean; error?: string };
const basicFields = new Set(['displayname', 'hardness', 'resistance', 'stacksize', 'maxstacksize', 'luminance', 'lightlevel']);
function editableFields(editor: ModElementEditorProjection): EditorField[] {
  return editor.sections.flatMap(section => section.fields).filter(field => !field.readOnly && !field.condition
    && ['text', 'number', 'toggle'].includes(field.control)
    && basicFields.has((field.path.split('/').at(-1) ?? '').toLowerCase())).slice(0, 4);
}
function parameterChanges(session?: ParameterSession): FieldChange[] {
  if (!session) return [];
  return editableFields(session.editor).flatMap(field => {
    const raw = session.values[field.path];
    const value = field.control === 'number' && raw !== '' ? Number(raw) : raw;
    const baseline = field.control === 'text' ? String(field.value ?? '') : field.value;
    return value === baseline ? [] : [{ path: field.path, value }];
  });
}

/** The creation surface and professional editors share one mounted workspace. */
export function SimpleCreationView({ active, children }: { active: boolean; children: ReactNode }) {
  useUiLocale();
  const { state, activeView, setActiveView, selectedElementId, setSelectedElementId, listAssets, getModElementEditor,
    previewModElementChange, updateModElement, refreshWorkspace, runClient, setActiveTaskId, setIsTaskDrawerOpen } = useWorkbench();
  const { setMode } = useWorkbenchMode();
  const { openTour } = useWorkbenchTour();
  const { openDelivery, openWorkspaceOpen } = useFirstCreation();
  const workspace = state.workbench?.workspace;
  const workspaceId = workspace?.id;
  const revision = workspace?.revision ?? 0;
  const home = activeView === 'hub';
  const agent = useAgent();
  const snapshot = agent.snapshot;
  const running = agentIsActive(snapshot?.state) || Boolean(snapshot?.activeVerification);
  const finalizing = snapshot?.activeRunPhase === 'finalizing';
  const busy = running || Boolean(snapshot?.checking) || finalizing;
  const [provider, setProvider] = useState<AgentProviderId>(() => savedProvider() ?? 'codex');
  const [modelPreferences, setModelPreferences] = useState(savedModels);
  const requestedCatalogs = useRef(new Set<string>());
  const explicitProvider = useRef(savedProvider());
  const [setupOpen, setSetupOpen] = useState(false);
  const [historyOpen, setHistoryOpen] = useState(false);
  const [drafts, setDrafts] = useState<Record<string, string>>({});
  const [turnPreferences, setTurnPreferences] = useState<Record<string, AgentTurnOptions>>({});
  const [dragging, setDragging] = useState(false);
  const fileInput = useRef<HTMLInputElement>(null);
  const submission = useRef<{ key: string; draft: string; previousMessages: Set<string>; attachments: string[]; accepted?: boolean } | null>(null);
  const [mobileNavigationOpen, setMobileNavigationOpen] = useState(false);
  const [workSearch, setWorkSearch] = useState('');
  const [previewTab, setPreviewTab] = useState<'appearance' | 'settings'>('appearance');
  const [assets, setAssets] = useState<AssetRecord[]>([]);
  const [assetId, setAssetId] = useState('');
  const [appearanceSource, setAppearanceSource] = useState<'element' | 'asset'>('element');
  const [assetsLoading, setAssetsLoading] = useState(false);
  const [assetsError, setAssetsError] = useState<string | null>(null);
  const [reloadAssets, setReloadAssets] = useState(0);
  const [parameters, setParameters] = useState<Record<string, ParameterSession>>({});
  const [parameterLoading, setParameterLoading] = useState(false);
  const [parameterError, setParameterError] = useState<string | null>(null);
  const [reloadParameters, setReloadParameters] = useState(0);
  const [playPending, setPlayPending] = useState(false);
  const [playError, setPlayError] = useState<string | null>(null);
  const messagesRef = useRef<HTMLDivElement>(null);
  const composerRef = useRef<HTMLTextAreaElement>(null);
  const homeRef = useRef<HTMLDivElement>(null);
  const previewRef = useRef<HTMLElement>(null);
  const navigationToggleRef = useRef<HTMLButtonElement>(null);
  const following = useRef(true);
  const composing = useRef(false);
  const parametersRef = useRef(parameters);
  parametersRef.current = parameters;
  const actions = useRef({ listAssets, getModElementEditor, refreshWorkspace });
  actions.current = { listAssets, getModElementEditor, refreshWorkspace };
  const previousAgentState = useRef(snapshot?.state);
  const selected = state.elements.find(element => element.id === selectedElementId) ?? null;
  const parameterKey = `${workspaceId ?? ''}:${selected?.id ?? ''}`;
  const parameter = parameters[parameterKey];
  const changes = parameterChanges(parameter);
  const currentParameters = Object.entries(parameters).filter(([, session]) => session.workspaceId === workspaceId);
  const hasParameterDrafts = currentParameters.some(([, session]) => parameterChanges(session).length > 0 || session.saveUnknown);
  const orphanedDrafts = currentParameters.filter(([, session]) => (parameterChanges(session).length > 0 || session.saveUnknown)
    && !state.elements.some(element => element.id === session.editor.element.id));
  const draftKey = `${workspaceId ?? ''}:${snapshot?.conversationId ?? 'new'}`;
  const draft = drafts[draftKey] ?? '';
  const attached = useAgentAttachments(workspaceId, draftKey, active && home);
  const permission = turnPreferences[draftKey]?.permission ?? snapshot?.permission ?? 'default';
  const effectiveProvider = snapshot?.provider ?? provider;
  const providerInfo = snapshot?.providers.find(item => item.id === effectiveProvider);
  const modelCatalog = providerInfo?.modelCatalog;
  const effectiveModel = snapshot?.conversationId ? snapshot.model ?? '' : modelPreferences[effectiveProvider] ?? modelCatalog?.defaultModel ?? '';
  const selectedModel = modelCatalog?.models.find(model => model.id === effectiveModel);
  const efforts: readonly string[] = effectiveProvider === 'codex' && selectedModel?.efforts?.length ? selectedModel.efforts : AGENT_EFFORTS;
  const preferredEffort = turnPreferences[draftKey]?.effort ?? snapshot?.effort;
  const effort = preferredEffort && efforts.includes(preferredEffort) ? preferredEffort
    : selectedModel?.defaultEffort && efforts.includes(selectedModel.defaultEffort) ? selectedModel.defaultEffort
    : efforts.includes('medium') ? 'medium' : efforts[0];
  const permissionCatalog = snapshot?.modelCatalogs?.[effectiveProvider];
  const permissionOptions: { value: AgentPermission; label: string }[] = [
    { value: 'default', label: uiText('默认权限', 'Default permissions') },
    { value: 'auto-approve', label: uiText('自动批准', 'Automatic approval') }
  ];
  if (effectiveProvider === 'codex') for (const profile of ['read-only', 'full-access'] as const) {
    if (permission === profile || permissionCatalog?.state === 'ready' && permissionCatalog.permissionProfiles.includes(profile))
      permissionOptions.push({ value: profile, label: profile === 'read-only' ? uiText('只读沙箱', 'Read-only sandbox') : uiText('完全访问', 'Full access') });
  }
  const usesSessionModel = Boolean(snapshot?.usesSessionModel);
  const needsModel = !effectiveModel && !usesSessionModel;
  const visibleAssets = assets.filter(asset => asset.category === 'texture' || asset.category === 'model');
  const previewAsset = visibleAssets.find(asset => asset.id === assetId);
  const previewElement = selected && hasElementVisualPreview(selected.type) ? selected : null;
  useEffect(() => { setAppearanceSource('element'); }, [selected?.id]);
  const recentTask = newestTasks(Object.values(state.tasks))[0];
  const taskRunning = Object.values(state.tasks).some(task => task.state === 'queued' || task.state === 'running');
  // Keep session/draft/task ownership mounted across modes, but do not build thousands
  // of hidden sidebar buttons on every Core query notification in Professional mode.
  const works = useMemo(() => active
    ? state.elements.filter(element => `${element.displayName} ${element.name}`.toLowerCase().includes(workSearch.trim().toLowerCase()))
    : [], [active, state.elements, workSearch]);

  useEffect(() => {
    if (snapshot?.provider) { setProvider(snapshot.provider); return; }
    if (!snapshot || snapshot.detecting || snapshot.sessions.length || explicitProvider.current) return;
    const installed = snapshot.providers.filter(item => item.installed && !item.error);
    if (installed.length) setProvider(installed[0].id);
  }, [snapshot?.provider, snapshot?.detecting, snapshot?.providers, snapshot?.sessions.length]);
  useEffect(() => {
    if (!active || !home || !workspaceId || !providerInfo?.installed || agent.loading || agent.pending || snapshot?.detecting || busy) return;
    const key = `${workspaceId}:${effectiveProvider}`;
    if (requestedCatalogs.current.has(key) || modelCatalog) return;
    requestedCatalogs.current.add(key);
    void agent.invoke({ operation: 'models', provider: effectiveProvider });
  }, [active, home, workspaceId, providerInfo?.installed, agent.loading, agent.pending, snapshot?.detecting, busy, effectiveProvider, modelCatalog, agent.invoke]);
  useEffect(() => {
    if (agentIsActive(previousAgentState.current) && snapshot && !agentIsActive(snapshot.state)) {
      void actions.current.refreshWorkspace(); setReloadAssets(value => value + 1); setReloadParameters(value => value + 1);
    }
    previousAgentState.current = snapshot?.state;
  }, [snapshot?.state]);
  useEffect(() => {
    if (active && home && workspaceId && effectiveProvider === 'codex' && providerInfo?.installed && providerInfo.capabilities?.runSettings
      && !permissionCatalog && !agent.pending && !agent.loading && !busy)
      void agent.invoke({ operation: 'settingsCatalog', provider: 'codex' });
  }, [active, home, workspaceId, effectiveProvider, providerInfo?.installed, providerInfo?.capabilities?.runSettings, permissionCatalog, agent.pending, agent.loading, busy, agent.invoke]);
  useEffect(() => {
    const list = messagesRef.current;
    if (list && following.current && active && home) list.scrollTop = list.scrollHeight;
  }, [snapshot?.sequence, active, home]);
  useEffect(() => {
    if (!active || !home || !workspaceId) return;
    let cancelled = false;
    setAssetsLoading(true); setAssetsError(null);
    void actions.current.listAssets().then(projection => {
      if (cancelled) return;
      if (!projection) throw new Error(uiText('暂时无法读取工作区资产。', 'Workspace assets could not be loaded.'));
      setAssets(assetRecordsFromProjection(projection));
    }).catch(failure => { if (!cancelled) setAssetsError(failure instanceof Error ? failure.message : String(failure)); })
      .finally(() => { if (!cancelled) setAssetsLoading(false); });
    return () => { cancelled = true; };
  }, [active, home, workspaceId, revision, reloadAssets]);
  useEffect(() => { setAssets([]); setAssetId(''); setWorkSearch(''); }, [workspaceId]);
  useEffect(() => {
    if (!selected) { setAssetId(''); return; }
    const name = selected.name.toLowerCase();
    const related = assets.find(asset => ['texture', 'model'].includes(asset.category) && asset.references.some(path => {
      const file = path.replaceAll('\\', '/').split('/').at(-1)?.toLowerCase();
      return file === `${name}.mod.json` || file === `${name}.json`;
    }));
    setAssetId(related?.id ?? '');
  }, [selected?.id, assets]);
  useEffect(() => {
    if (!active || !home || !selected || !workspaceId) return;
    const existing = parametersRef.current[parameterKey];
    if (existing && (parameterChanges(existing).length || existing.saveUnknown || existing.saving)) return;
    let cancelled = false;
    setParameterLoading(true); setParameterError(null);
    void actions.current.getModElementEditor(selected.id).then(editor => {
      if (cancelled) return;
      if (!editor) throw new Error(uiText('暂时无法读取作品设置。', 'Creation settings could not be loaded.'));
      const values = Object.fromEntries(editableFields(editor).map(field => [field.path, field.control === 'toggle' ? Boolean(field.value) : String(field.value ?? '')]));
      setParameters(current => ({ ...current, [parameterKey]: { workspaceId, editor, revision, values } }));
    }).catch(failure => { if (!cancelled) setParameterError(failure instanceof Error ? failure.message : String(failure)); })
      .finally(() => { if (!cancelled) setParameterLoading(false); });
    return () => { cancelled = true; };
  }, [active, home, workspaceId, selected?.id, revision, parameterKey, reloadParameters]);
  useEffect(() => {
    setUnsavedDraftCount('simple-parameters', Object.values(parameters).filter(session => parameterChanges(session).length || session.saveUnknown).length);
    return () => setUnsavedDraftCount('simple-parameters', 0);
  }, [parameters]);
  useEffect(() => {
    setUnsavedDraftCount('simple-chat', Object.values(drafts).filter(text => text.trim()).length + attached.draftCount);
    return () => setUnsavedDraftCount('simple-chat', 0);
  }, [drafts, attached.draftCount]);
  useEffect(() => {
    const pending = submission.current;
    if (!pending || !snapshot) return;
    if (pending.key !== `${workspaceId}:${snapshot.conversationId ?? 'new'}` && pending.key !== `${workspaceId}:new`) {
      submission.current = null;
      return;
    }
    const sent = snapshot.messages.find(message => message.role === 'user' && !pending.previousMessages.has(message.id)
      && message.delivery !== 'pending' && message.delivery !== 'failed');
    if (sent) {
      pending.accepted = true;
      setDrafts(current => current[pending.key] === pending.draft ? { ...current, [pending.key]: '' } : current);
      attached.accepted(pending.key, pending.attachments);
      submission.current = null;
    } else if (!agentIsActive(snapshot.state)) submission.current = null;
  }, [snapshot?.sequence]);

  const changeDraft = (value: string) => setDrafts(current => ({ ...current, [draftKey]: value }));
  const discardConversationDrafts = (ids: string[]) => {
    const keys = new Set(ids.map(id => `${workspaceId}:${id}`));
    setDrafts(current => Object.fromEntries(Object.entries(current).filter(([key]) => !keys.has(key))));
    setTurnPreferences(current => Object.fromEntries(Object.entries(current).filter(([key]) => !keys.has(key))));
    keys.forEach(key => attached.discardDraft(key));
    if (submission.current && keys.has(submission.current.key)) submission.current = null;
  };
  const send = async () => {
    const text = draft.trim();
    if ((!text && !attached.files.length) || attached.blocked || !workspaceId || !providerInfo?.installed || busy || needsModel || agent.pending || snapshot?.storageBlocked || snapshot?.writeBlocked || snapshot?.enabled === false || hasParameterDrafts) return;
    const submittedKey = draftKey;
    const previousMessages = new Set(snapshot?.messages.map(message => message.id) ?? []);
    const attachmentIds = attached.files.flatMap(file => file.item ? [file.item.id] : []);
    const submitted = { key: submittedKey, draft, previousMessages, attachments: attachmentIds, accepted: false };
    submission.current = submitted;
    following.current = true;
    const result = await agent.invoke({ operation: 'send', provider: effectiveProvider,
      ...(snapshot?.conversationId ? { conversationId: snapshot.conversationId } : {}),
      ...(effectiveModel ? { model: effectiveModel } : {}), effort, permission, attachments: attachmentIds, text });
    if (!snapshot?.conversationId && result?.conversationId && !submitted.accepted) {
      const nextKey = `${workspaceId}:${result.conversationId}`;
      submitted.key = nextKey;
      attached.move(submittedKey, nextKey);
      setDrafts(current => {
        const next = { ...current, [nextKey]: current[submittedKey] ?? draft }; delete next[submittedKey]; return next;
      });
      setTurnPreferences(current => ({ ...current, [nextKey]: { effort, permission } }));
    }
    if (!result || result.error && !result.messages.some(message => message.role === 'user' && !previousMessages.has(message.id))) submission.current = null;
  };
  const newConversation = async (next = effectiveProvider, model = next === effectiveProvider ? effectiveModel : modelPreferences[next] ?? '', carryDraft = false) => {
    if (busy || agent.pending || attached.uploading) return;
    explicitProvider.current = next;
    try { localStorage.setItem(PROVIDER_PREFERENCE, next); } catch { /* Keep this window's selection. */ }
    setProvider(next); setActiveView('hub'); following.current = true;
    const result = await agent.invoke({ operation: 'new', provider: next, ...(model ? { model } : {}), effort, permission: 'default' });
    if (!result?.error && (!snapshot?.conversationId || carryDraft) && result?.conversationId && result.conversationId !== snapshot?.conversationId) {
      attached.move(draftKey, `${workspaceId}:${result.conversationId}`);
      setTurnPreferences(current => ({ ...current, [`${workspaceId}:${result.conversationId}`]: { effort, permission: 'default' } }));
      setDrafts(current => {
      if (!current[draftKey]) return current;
      const nextDrafts = { ...current, [`${workspaceId}:${result.conversationId}`]: current[draftKey] };
      delete nextDrafts[draftKey];
      return nextDrafts;
      });
    }
    if (result) requestAnimationFrame(() => composerRef.current?.focus());
    return result;
  };
  const chooseModel = async (model: string) => {
    const result = await newConversation(effectiveProvider, model, true);
    if (!result?.error && result?.conversationId && (result.model ?? '') === model) {
      setModelPreferences(current => {
        const next = { ...current, [effectiveProvider]: model };
        try { localStorage.setItem(MODEL_PREFERENCES, JSON.stringify(next)); } catch { /* Keep the current window's selection. */ }
        return next;
      });
    }
  };
  const choosePermission = async (next: AgentPermission) => {
    if (next === 'auto-approve' && !await confirmAction({
      title: uiMessage('允许此对话自动批准操作？', 'Automatically approve actions in this conversation?'),
      message: uiMessage('CLI 可以自动执行命令、修改文件和访问网络。Codex 会同时取消工作区沙箱限制。此设置仅用于本应用内的当前对话，不改写主机配置。',
        'The CLI can run commands, change files and access the network without asking. Codex also removes its workspace sandbox. This applies only to this in-app conversation and does not change host settings.'),
      confirmLabel: uiMessage('启用自动批准', 'Enable automatic approval')
    })) return;
    setTurnPreferences(current => ({ ...current, [draftKey]: { effort, permission: next } }));
  };
  const updateParameter = (patch: Partial<ParameterSession>) => setParameters(current => current[parameterKey]
    ? { ...current, [parameterKey]: { ...current[parameterKey], ...patch } } : current);
  const saveParameters = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!parameter || !selected || !changes.length || running || parameter.saving || parameter.saveUnknown || parameter.revision !== revision) return;
    const key = parameterKey, elementId = selected.id, expectedRevision = parameter.revision;
    const patch = (value: Partial<ParameterSession>) => setParameters(current => current[key] ? { ...current, [key]: { ...current[key], ...value } } : current);
    patch({ saving: true, error: undefined });
    let submitted = false;
    try {
      const preview = await previewModElementChange(elementId, changes);
      if (!preview?.canApply || preview.baseRevision !== expectedRevision) {
        patch({ error: preview?.diagnostics.map(item => t(item.message)).join('\n') || uiText('内容已变化，请重新读取设置后再应用。', 'The content changed. Reload settings before applying changes.') });
        return;
      }
      submitted = true;
      const result = await updateModElement(elementId, changes, expectedRevision);
      if (result.status !== 'committed') {
        patch({ error: result.diagnostics.map(item => t(item.message)).join('\n') || uiText('修改未应用，请检查作品设置。', 'Changes were not applied. Review the creation settings.') });
        return;
      }
      setParameters(current => { const next = { ...current }; delete next[key]; return next; });
      await refreshWorkspace(); setReloadParameters(value => value + 1);
    } catch (failure) {
      patch({ saveUnknown: submitted, error: failure instanceof Error ? failure.message : String(failure) });
    } finally { patch({ saving: false }); }
  };
  const reloadParameter = () => {
    setParameters(current => { const next = { ...current }; delete next[parameterKey]; return next; });
    setReloadParameters(value => value + 1);
  };
  const discardOrphanedDrafts = () => setParameters(current => {
    const next = { ...current };
    for (const [key, session] of orphanedDrafts) if (!session.saving) delete next[key];
    return next;
  });
  const playtest = async () => {
    if (!workspace || playPending || taskRunning || running || hasParameterDrafts) return;
    setPlayPending(true); setPlayError(null);
    try {
      if (hasUnsavedDrafts(['simple-chat', 'professional-agent']) && !await confirmAction({
        title: uiMessage('还有未保存的作品修改', 'Unsaved creation changes'),
        message: uiMessage('试玩只会使用已保存的内容。你可以取消并应用修改，或继续试玩已保存内容。', 'Playtesting uses saved content. Cancel to apply your changes, or continue with the saved content.'),
        confirmLabel: uiMessage('试玩已保存内容', 'Playtest saved content')
      })) return;
      await runClient();
    } catch (failure) { setPlayError(failure instanceof Error ? failure.message : String(failure)); }
    finally { setPlayPending(false); }
  };
  const openElementEditor = () => { setMode('professional'); setActiveView('elements'); };
  const openTask = (id: string) => { setActiveTaskId(id); setIsTaskDrawerOpen(true); };
  const showCompactPreview = () => {
    const viewport = homeRef.current, preview = previewRef.current;
    if (!viewport || !preview) return;
    viewport.scrollTop += preview.getBoundingClientRect().top - viewport.getBoundingClientRect().top;
    document.getElementById(`creation-${previewTab}-tab`)?.focus({ preventScroll: true });
  };
  const showCompactChat = () => { if (homeRef.current) homeRef.current.scrollTop = 0; composerRef.current?.focus({ preventScroll: true }); };
  const status = agent.loading || snapshot?.detecting ? uiText('正在检测创作助手…', 'Detecting creation assistants…')
    : finalizing ? uiText('正在保存结果…', 'Saving results…')
    : snapshot?.activeVerification ? snapshot.activeVerification.cancelling ? uiText('正在停止验证…', 'Stopping verification…') : uiText('正在验证…', 'Verifying…')
    : snapshot?.state === 'starting' ? uiText('正在启动…', 'Starting…')
      : snapshot?.state === 'waiting_approval' ? uiText('等待你的选择', 'Waiting for your choice')
        : snapshot?.state === 'stopping' ? uiText('正在停止…', 'Stopping…')
          : snapshot?.state === 'running' ? uiText('正在创作…', 'Working…')
            : snapshot?.state === 'failed' ? uiText('本次任务未完成', 'This task did not complete')
              : providerStatus(providerInfo);
  const antigravityDenied = effectiveProvider === 'antigravity' && /permission|denied|approval|权限|拒绝/i.test(snapshot?.error ?? agent.error ?? '');

  return <div className={`creation-shell${active ? ' is-simple' : ''}`} data-testid="creation-shell" data-active={active}>
    <aside className="creation-sidebar" hidden={!active} data-expanded={mobileNavigationOpen} aria-label={uiText('创作导航', 'Creation navigation')}
      onKeyDown={event => { if (event.key === 'Escape' && mobileNavigationOpen) { event.preventDefault(); setMobileNavigationOpen(false); navigationToggleRef.current?.focus(); } }}>
      <button type="button" className="creation-sidebar-toggle" ref={navigationToggleRef} data-testid="simple-navigation-toggle" aria-expanded={mobileNavigationOpen} aria-controls="creation-sidebar-content" onClick={() => setMobileNavigationOpen(open => !open)}>
        <Box size={16} aria-hidden="true" /><span>{uiText('内容与对话', 'Content and conversations')}</span><ChevronDown size={15} aria-hidden="true" />
      </button>
      <div className="creation-sidebar-content" id="creation-sidebar-content" onClick={event => { if ((event.target as HTMLElement).closest('button')) setMobileNavigationOpen(false); }}>
      <button type="button" className="creation-new-work" data-testid="simple-new-workspace" onClick={() => setActiveView('new-workspace')}><Plus size={18} />{uiText('新建作品', 'New creation')}</button>
      <button type="button" className="creation-open-work" onClick={openWorkspaceOpen}><FolderOpen size={15} />{uiText('打开作品', 'Open creation')}</button>
      <div className="creation-sidebar-workspace"><Box size={17} aria-hidden="true" /><span title={workspace?.name}>{workspace?.name ?? uiText('尚未打开作品', 'No creation open')}</span></div>
      <div className="creation-works-heading"><h2>{uiText('作品内容', 'Creation content')}</h2><span>{state.elements.length}</span></div>
      {state.elements.length > 8 && <input className="creation-work-search" type="search" aria-label={uiText('查找作品内容', 'Find creation content')} placeholder={uiText('查找内容…', 'Find content…')} value={workSearch} onChange={event => setWorkSearch(event.target.value)} />}
      <div className="creation-works" role="group" aria-label={uiText('作品内容', 'Creation content')}>
        {works.map(element => <button type="button" key={element.id} className="creation-work" aria-pressed={selected?.id === element.id}
          data-testid={`simple-element-${element.id}`} onClick={() => { setSelectedElementId(element.id); setActiveView('hub'); }}>
          <Box size={19} aria-hidden="true" /><span><strong>{element.displayName}</strong><small>{elementShortLabel(element.type)}</small></span>
        </button>)}
        {!works.length && <p className="creation-sidebar-empty">{state.elements.length ? uiText('没有匹配的内容', 'No matching content') : uiText('描述想法，开始第一件作品。', 'Describe an idea to start creating.')}</p>}
      </div>
      <section className="creation-conversations" aria-labelledby="creation-conversations-title">
        <div className="creation-works-heading"><h2 id="creation-conversations-title">{uiText('对话', 'Conversations')}</h2>
          <button type="button" aria-label={uiText('新对话', 'New conversation')} title={uiText('新对话', 'New conversation')} data-testid="simple-new-chat" disabled={!agent.available || !workspaceId || running || Boolean(agent.pending)} onClick={() => void newConversation()}><Plus size={16} /></button></div>
        <div className="creation-conversation-list">{snapshot?.sessions.map(session => <button type="button" key={session.id}
          aria-pressed={snapshot.conversationId === session.id} disabled={running || Boolean(agent.pending)} data-testid={`simple-conversation-${session.id}`}
          title={`${agentText(session.title, session.titleText)} · ${providerNames[session.provider]}`} onClick={() => { setActiveView('hub'); following.current = true; void agent.invoke({ operation: 'select', conversationId: session.id }); }}>
          <AgentProviderIcon provider={session.provider} size={16} /><span>{agentText(session.title, session.titleText) || uiText('新对话', 'New conversation')}</span>
        </button>)}</div>
        <button type="button" className="creation-history-manage" data-testid="simple-manage-conversations" disabled={!agent.available || !workspaceId} onClick={() => setHistoryOpen(true)}>{uiText('整理对话记录', 'Manage conversations')}</button>
        {snapshot?.history && snapshot.history.level !== 'ok' && <p className="creation-capacity-note">{uiText('对话空间需要整理', 'Conversation storage needs attention')}</p>}
      </section>
      <nav className="creation-utilities" aria-label={uiText('作品工具', 'Creation tools')}>
        <button type="button" onClick={() => setActiveView('history')} data-testid="simple-history"><History size={17} />{uiText('恢复点', 'Recovery points')}</button>
        <button type="button" onClick={() => setActiveView('assets')}><Image size={17} />{uiText('素材', 'Assets')}</button>
        <button type="button" onClick={() => setActiveView('help')}><HelpCircle size={17} />{uiText('帮助', 'Help')}</button>
        <button type="button" onClick={() => setActiveView('settings')}><Settings size={17} />{uiText('设置', 'Settings')}</button>
      </nav>
      </div>
    </aside>
    <div className="creation-content">
      <header className="creation-header" hidden={!active}>
        <div className="creation-heading">{!home && <button type="button" className="creation-back" onClick={() => setActiveView('hub')} aria-label={uiText('返回创作', 'Back to creation')} data-testid="simple-back"><ArrowLeft size={18} /></button>}
          <div><h1>{home ? workspace?.name ?? uiText('开始创作', 'Start creating') : workbenchNavigation().find(item => item.id === activeView)?.label}</h1>
            {home && workspace && <span className="creation-version">{workspace.generator.displayName}</span>}</div>
        </div>
        <div className="creation-header-actions">
          <button type="button" className="creation-secondary" data-testid="simple-tour" onClick={openTour}><HelpCircle size={16} />{uiText('快速教程', 'Quick tour')}</button>
          <button type="button" className="creation-primary" data-testid="simple-playtest" disabled={!workspace || playPending || taskRunning || running || hasParameterDrafts}
            onClick={() => void playtest()}><Play size={16} />{uiText('在游戏中试玩', 'Playtest in game')}</button>
          <button type="button" className="creation-secondary" data-testid="simple-export" disabled={!workspace || running || taskRunning} onClick={openDelivery}><FileArchive size={16} />{uiText('导出模组', 'Export mod')}</button>
        </div>
      </header>
      <div className="creation-home" ref={homeRef} hidden={!active || !home}>
        <section className="creation-chat" aria-label={uiText('创作对话', 'Creation chat')}>
          <div className="creation-chat-heading"><h2>{uiText('一起把想法做出来', 'Bring your idea to life')}</h2><span className="creation-agent-state" role="status" aria-live="polite">{running && <LoaderCircle size={13} className="creation-spinner" aria-hidden="true" />}{status}</span>
            <button type="button" className="creation-compact-preview" data-testid="simple-show-preview" onClick={showCompactPreview}><Image size={14} aria-hidden="true" />{uiText('预览与设置', 'Preview and settings')}</button>
          </div>
          {(playError || recentTask) && <div className={`creation-task-strip${playError || recentTask?.state === 'failed' ? ' has-error' : ''}`}>
            {playError ? <span role="alert">{playError}</span> : recentTask && <><span>{t(recentTask.stage)}</span><button type="button" onClick={() => openTask(recentTask.id)}>{uiText('查看进度', 'View task')}</button></>}
          </div>}
          <div className="creation-messages" ref={messagesRef} role="log" aria-label={uiText('对话记录', 'Conversation messages')} aria-live="off"
            onScroll={event => { const list = event.currentTarget; following.current = list.scrollHeight - list.scrollTop - list.clientHeight < 72; }}>
            {!snapshot?.messages.length && <div className="creation-empty-chat"><Box size={30} strokeWidth={1.4} aria-hidden="true" /><h3>{uiText('你想在 Minecraft 里添加什么？', 'What would you add to Minecraft?')}</h3>
              <p>{uiText('说说它的外观和玩法，创作助手会与你一起完成。', 'Describe how it looks and works, then build it with your creation assistant.')}</p>
              {workspace ? <div className="creation-ideas">{[
                uiText('做一块会发光的矿石', 'Create a glowing ore'), uiText('做一把有特殊效果的剑', 'Create a sword with a special effect')
              ].map(text => <button type="button" key={text} onClick={() => { changeDraft(text); composerRef.current?.focus(); }}>{text}<ArrowUp size={13} aria-hidden="true" /></button>)}</div>
                : <button type="button" className="creation-primary" onClick={() => setActiveView('new-workspace')}>{uiText('创建第一件作品', 'Create your first project')}</button>}
            </div>}
            {snapshot?.messages.map(message => message.role === 'tool' ? <details key={message.id} className="creation-tool-message"><summary>{uiText('操作详情', 'Action details')}<ChevronDown size={13} aria-hidden="true" /></summary><pre>{agentText(message.text, message.textText)}</pre></details>
              : <div key={message.id} className={`creation-message is-${message.role}`} data-message-role={message.role}>
                {message.role === 'assistant' && <span className="creation-avatar" aria-hidden="true"><AgentProviderIcon provider={effectiveProvider} size={21} /></span>}
                <div><span className="sr-only">{message.role === 'user' ? uiText('你：', 'You:') : message.role === 'assistant' ? `${providerNames[effectiveProvider]}:` : uiText('状态：', 'Status:')}</span>{message.role === 'assistant' ? <AgentMessageBody text={message.text} /> : <p>{message.role === 'system' ? agentText(message.text, message.textText) : message.text}</p>}
                  <AgentAttachments key={`${workspaceId}:${message.id}`} files={message.attachments ?? []} />
                  {message.delivery === 'failed' && <span className="creation-delivery-note">{uiText('未发送给模型，可在输入框修改后重试。', 'Not sent to the model. Edit the draft and try again.')}</span>}
                </div>
              </div>)}
            {running && snapshot?.state !== 'waiting_approval' && <p className="creation-working"><LoaderCircle size={14} className="creation-spinner" aria-hidden="true" />{status}</p>}
            {snapshot?.approvals.map(approval => <AgentApprovalPrompt key={approval.id} approval={approval} disabled={Boolean(agent.pending)}
              onRespond={answer => void agent.invoke({ operation: 'approve', id: approval.id, ...answer })} />)}
          </div>
          <div className="creation-compose-area">
            {!agent.available ? <div className="creation-connection-note" role="status" data-testid="simple-agent-unavailable"><strong>{uiText('桌面创作助手尚未连接', 'Desktop assistant is unavailable')}</strong><p>{uiText('在支持创作助手的桌面应用中打开作品，再选择已安装的助手。', 'Open the project in a desktop app with assistant support, then select an installed assistant.')}</p></div>
              : !agent.loading && !snapshot?.detecting && workspace && !providerInfo?.installed && <div className="creation-connection-note" role="status" data-testid="simple-provider-setup"><strong>{uiText('连接创作助手', 'Connect a creation assistant')}</strong><p>{uiText(`尚未检测到 ${providerNames[effectiveProvider]}。安装并完成配置后，点击重新检测。`, `${providerNames[effectiveProvider]} was not detected. Install and configure it, then check again.`)}</p><div>
                <button type="button" disabled={Boolean(agent.pending)} onClick={() => setSetupOpen(true)}><ExternalLink size={14} />{uiText('安装指南', 'Setup guide')}</button>
                <button type="button" disabled={Boolean(agent.pending)} onClick={() => void agent.invoke({ operation: 'detect' })}><RefreshCw size={14} />{uiText('重新检测', 'Check again')}</button>
              </div></div>}
            {(agent.error || snapshot?.error) && <div className="creation-chat-error" role="alert" data-testid="simple-agent-error"><p>{agent.error || agentText(snapshot?.error, snapshot?.errorText)}</p>
              {antigravityDenied && <p>{uiText('请在 Antigravity 的配置中允许所需操作，然后重新发送请求。', 'Allow the required operation in Antigravity settings, then send your request again.')}</p>}
              <button type="button" disabled={Boolean(agent.pending)} onClick={() => void agent.invoke({ operation: 'status' })}>{uiText('刷新状态', 'Refresh status')}</button>
            </div>}
            {providerInfo?.error && <p className="creation-chat-error" role="alert">{agentText(providerInfo.error, providerInfo.errorText)}</p>}
            {snapshot?.storageBlocked && <button type="button" className="btn-secondary" onClick={() => setHistoryOpen(true)}>{uiText('整理对话并恢复', 'Manage history and recover')}</button>}
            {orphanedDrafts.length > 0 && <div className="creation-connection-note" role="status" data-testid="simple-orphan-drafts">
              <p>{uiText('这些内容已移除，仍保留未应用的输入：', 'Removed content still has unapplied input: ')}{orphanedDrafts.map(([, session]) => session.editor.element.displayName).join('、')}</p>
              <button type="button" disabled={orphanedDrafts.some(([, session]) => session.saving)} onClick={discardOrphanedDrafts}>{uiText('放弃已移除内容的输入', 'Discard input for removed content')}</button>
            </div>}
            {hasParameterDrafts && <p className="creation-compose-hint" role="status">{uiText('先应用或放弃作品设置中的输入，再继续对话或试玩。', 'Apply or discard the settings draft before chatting or playtesting.')}</p>}
            <div className="creation-assistant-entry">{providerInfo?.installed && providerInfo.loginAvailable && <button type="button" className="creation-login" disabled={running || Boolean(agent.pending)} onClick={() => void agent.invoke({ operation: 'login', provider: effectiveProvider })}>{uiText('登录', 'Sign in')}</button>}{providerInfo?.installed && !providerInfo.loginAvailable && <button type="button" className="creation-login" disabled={running || Boolean(agent.pending)} onClick={() => void agent.invoke({ operation: 'openSetup', provider: effectiveProvider })}>{uiText('配置指南', 'Setup guide')}</button>}<button type="button" data-testid="simple-agent-setup" onClick={() => setSetupOpen(true)}><Settings size={14} aria-hidden="true" />{uiText('安装与连接助手', 'Install and connect assistants')}</button></div>
            {attached.error && <p className="creation-chat-error" role="alert">{renderUiMessage(attached.error)}</p>}
            <form className={`creation-composer${dragging ? ' is-dragging' : ''}`} onSubmit={event => { event.preventDefault(); void send(); }}
              onDragOver={event => { if (event.dataTransfer.types.includes('Files')) { event.preventDefault(); event.dataTransfer.dropEffect = 'copy'; setDragging(true); } }}
              onDragLeave={event => { if (!event.currentTarget.contains(event.relatedTarget as Node | null)) setDragging(false); }}
              onDrop={event => { if (event.dataTransfer.files.length) { event.preventDefault(); setDragging(false); void attached.addFiles(Array.from(event.dataTransfer.files)); } }}>
              <AgentAttachments files={attached.files} onRemove={attached.remove} />
              <input className="creation-file-input" data-testid="agent-file-input" ref={fileInput} type="file" multiple tabIndex={-1}
                aria-label={uiText('选择文件和图片', 'Choose files and images')} onChange={event => { void attached.addFiles(Array.from(event.target.files ?? [])); event.target.value = ''; }} />
              <label className="sr-only" htmlFor="creation-prompt">{uiText('描述想添加或修改的内容', 'Describe what to add or change')}</label>
              <textarea id="creation-prompt" ref={composerRef} data-testid="simple-prompt" value={draft} maxLength={64000} rows={3}
                placeholder={uiText('描述你想添加或修改的内容…', 'Describe what you want to add or change…')}
                onChange={event => changeDraft(event.target.value)} onCompositionStart={() => { composing.current = true; }} onCompositionEnd={() => { composing.current = false; }}
                onPaste={event => { const files = Array.from(event.clipboardData.files); if (files.length) { event.preventDefault(); void attached.addFiles(files); } }}
                onKeyDown={event => { if (event.key === 'Enter' && !event.shiftKey && !event.altKey && !event.ctrlKey && !event.metaKey && !event.nativeEvent.isComposing && !composing.current && event.keyCode !== 229) { event.preventDefault(); void send(); } }} />
              <div className="creation-composer-tools">
                <button type="button" className="creation-attach-button" aria-label={uiText('添加文件和图片', 'Add files and images')}
                  title={uiText('添加、拖入文件或粘贴图片；最多 8 个，总计 20 MiB。图片支持 PNG、JPEG、GIF，单张不超过 5 MiB。', 'Add or drop files, or paste images. Up to 8 files, 20 MiB total. PNG, JPEG and GIF images, up to 5 MiB each.')}
                  disabled={!workspaceId || !agent.available || attached.uploading} onClick={() => window.__COPPERBENCH_AGENT_HOST__?.nativeFileSelection ? void attached.selectNative() : fileInput.current?.click()}><Paperclip size={17} aria-hidden="true" /></button>
                <div className="creation-provider-choice"><label htmlFor="creation-provider">{uiText('创作助手', 'Assistant')}</label>
                <AgentProviderSelect value={effectiveProvider} providers={snapshot?.providers ?? []} active={active && home} detecting={snapshot?.detecting}
                  disabled={busy || attached.uploading || Boolean(agent.pending) || agent.loading || !agent.available || !workspaceId}
                  onChange={next => void newConversation(next)} />
                {providerInfo?.installed && <Check size={13} className="creation-provider-ready" aria-label={uiText('已检测到安装', 'Installation detected')} />}
              </div>
                <div className="creation-model-settings"><AgentModelSelect value={effectiveModel} usesSessionModel={usesSessionModel} catalog={modelCatalog} active={active && home}
                  disabled={busy || attached.uploading || Boolean(agent.pending && agent.pending !== 'models') || agent.loading || !agent.available || !workspaceId}
                  onChange={model => void chooseModel(model)} onRefresh={() => void agent.invoke({ operation: 'models', provider: effectiveProvider })} />
                  <AgentOptionSelect<AgentEffort> value={effort} options={efforts.map(level => ({ value: level, label: level }))}
                    icon={<Brain size={14} aria-hidden="true" />} label={uiText('思考强度', 'Reasoning effort')} testId="simple-effort"
                    describedBy="creation-effort-hint" active={active && home} disabled={busy || Boolean(agent.pending)}
                    onChange={next => setTurnPreferences(current => ({ ...current, [draftKey]: { effort: next, permission } }))} />
                </div>
                <AgentOptionSelect<AgentPermission> value={permission} options={permissionOptions} icon={<ShieldCheck size={14} aria-hidden="true" />} label={uiText('执行权限', 'Execution permissions')} testId="simple-permission"
                  title={effectiveProvider === 'codex'
                    ? uiText('默认权限允许工作区写入并按需批准；只读沙箱限制文件写入；完全访问仍按需批准；自动批准跳过确认。', 'Default permissions allow workspace writes with approval when needed. Read-only restricts file writes. Full access still requests approval; automatic approval skips confirmation.')
                    : uiText('默认权限沿用 CLI 的审批规则，需要批准时在对话中确认。', 'Default permissions follow CLI rules. Confirm requests in the conversation when approval is needed.')}
                  active={active && home} disabled={busy || Boolean(agent.pending)} onChange={next => void choosePermission(next)} />
                {/* A fast stop reply can reuse this node as Submit before the click default action. */}
                {running || finalizing ? <button type="button" className="creation-stop" data-testid="simple-stop" aria-label={finalizing ? uiText('正在保存结果', 'Saving results') : snapshot?.activeVerification ? uiText('停止验证', 'Stop verification') : uiText('停止当前任务', 'Stop current task')} disabled={finalizing || snapshot?.state === 'stopping' || snapshot?.activeVerification?.cancelling || Boolean(agent.pending)} onClick={event => { event.preventDefault(); void agent.invoke(snapshot?.activeVerification ? { operation: 'stopVerification', runId: snapshot.activeVerification.runId } : { operation: 'stop' }); }}>{finalizing ? <LoaderCircle size={15} className="creation-spinner" /> : <Square size={15} fill="currentColor" />}</button>
                  : <button type="submit" className="creation-send" data-testid="simple-send" aria-label={uiText('发送', 'Send')} disabled={(!draft.trim() && !attached.files.length) || attached.blocked || !workspaceId || !providerInfo?.installed || !agent.available || agent.loading || busy || needsModel || Boolean(snapshot?.storageBlocked || snapshot?.writeBlocked) || snapshot?.enabled === false || Boolean(agent.pending) || hasParameterDrafts}><ArrowUp size={21} /></button>}
              </div>
            </form>
            <p id="creation-effort-hint" className="creation-effort-hint">{uiText('请按模型选择思考强度，并非所有模型都支持全部档位；CLI 或当前源可能拒绝或调整所选强度。', 'Choose an effort supported by your model. Not all models support every level; the CLI or service may reject or adjust it.')}</p>
            <p className="creation-composer-footnote">{uiText('Enter 发送 · Shift + Enter 换行', 'Enter to send · Shift + Enter for a new line')}{snapshot?.conversationId && <span>{uiText('切换助手或模型会新建对话', 'Changing assistants or models starts a new conversation')}</span>}</p>
          </div>
        </section>
        <aside className="creation-preview" ref={previewRef} aria-label={uiText('作品预览', 'Creation preview')}>
          <header><h2>{selected?.displayName ?? uiText('作品预览', 'Creation preview')}</h2>{selected && <span>{elementShortLabel(selected.type)}</span>}
            <button type="button" className="creation-compact-back" data-testid="simple-back-to-chat" onClick={showCompactChat}><ArrowLeft size={14} aria-hidden="true" />{uiText('返回对话', 'Back to chat')}</button>
          </header>
          <div className="creation-preview-tabs" role="tablist" aria-label={uiText('预览内容', 'Preview content')}
            onKeyDown={event => { if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return; event.preventDefault(); const next = event.key === 'Home' ? 'appearance' : event.key === 'End' ? 'settings' : previewTab === 'appearance' ? 'settings' : 'appearance'; setPreviewTab(next); document.getElementById(`creation-${next}-tab`)?.focus(); }}>
            <button type="button" role="tab" id="creation-appearance-tab" tabIndex={previewTab === 'appearance' ? 0 : -1} aria-selected={previewTab === 'appearance'} aria-controls="creation-appearance" onClick={() => setPreviewTab('appearance')}>{uiText('外观', 'Appearance')}</button>
            <button type="button" role="tab" id="creation-settings-tab" tabIndex={previewTab === 'settings' ? 0 : -1} aria-selected={previewTab === 'settings'} aria-controls="creation-settings" onClick={() => setPreviewTab('settings')}>{uiText('设置', 'Settings')}</button>
          </div>
          <div className="creation-preview-scroll">
            <section id="creation-appearance" role="tabpanel" aria-labelledby="creation-appearance-tab" hidden={previewTab !== 'appearance'}>
              {previewElement && <div className="creation-appearance-source" role="group" aria-label={uiText('外观来源', 'Appearance source')}>
                <button type="button" aria-pressed={appearanceSource === 'element'} onClick={() => setAppearanceSource('element')}>{uiText('作品外观', 'Element appearance')}</button>
                <button type="button" aria-pressed={appearanceSource === 'asset'} onClick={() => setAppearanceSource('asset')}>{uiText('纹理与模型', 'Textures and models')}</button>
              </div>}
              {previewElement && appearanceSource === 'element' && workspaceId ? active && home && previewTab === 'appearance' &&
                <ElementVisualPreview workspaceId={workspaceId} element={previewElement} changes={changes} /> : <>
              <label className="creation-asset-label" htmlFor="creation-asset">{uiText('工作区纹理与模型', 'Workspace textures and models')}</label>
              <select id="creation-asset" value={previewAsset?.id ?? ''} disabled={assetsLoading || !visibleAssets.length} onChange={event => setAssetId(event.target.value)}><option value="">{uiText('选择要查看的资产', 'Choose an asset to view')}</option>{visibleAssets.map(asset => <option key={asset.id} value={asset.id}>{asset.name} · {asset.format}</option>)}</select>
              {assetsLoading && <p className="creation-panel-note" role="status">{uiText('正在读取素材…', 'Loading assets…')}</p>}
              {assetsError && <div className="creation-panel-error" role="alert"><p>{assetsError}</p><button type="button" onClick={() => setReloadAssets(value => value + 1)}>{uiText('重试', 'Retry')}</button></div>}
              {previewAsset ? <AssetPreview workspaceId={workspaceId} asset={previewAsset} /> : !assetsLoading && <div className="creation-preview-empty"><Image size={35} strokeWidth={1.25} aria-hidden="true" /><p>{visibleAssets.length ? uiText('选择资产，查看实际纹理。', 'Choose an asset to see its texture.') : uiText('添加纹理后，在这里查看外观。', 'Add a texture to preview its appearance here.')}</p><button type="button" onClick={() => setActiveView('assets')}>{uiText('打开素材', 'Open assets')}</button></div>}
              </>}
            </section>
            <section id="creation-settings" role="tabpanel" aria-labelledby="creation-settings-tab" hidden={previewTab !== 'settings'}>
              {!selected ? <p className="creation-panel-note">{uiText('从左侧选择内容，调整它的设置。', 'Select content on the left to adjust its settings.')}</p>
                : parameterLoading && !parameter ? <p className="creation-panel-note" role="status">{uiText('正在读取设置…', 'Loading settings…')}</p>
                  : parameterError ? <div className="creation-panel-error" role="alert"><p>{parameterError}</p><button type="button" onClick={reloadParameter}>{uiText('重试', 'Retry')}</button></div>
                    : parameter && <form className="creation-parameters" onSubmit={event => void saveParameters(event)}>
                      {editableFields(parameter.editor).map(field => <label key={field.path} className={field.control === 'toggle' ? 'creation-parameter-toggle' : ''}><span>{t(field.label)}</span>
                        <input data-testid={`simple-parameter-${field.path.split('/').at(-1)}`} type={field.control === 'toggle' ? 'checkbox' : field.control === 'number' ? 'number' : 'text'}
                          {...(field.control === 'toggle' ? { checked: Boolean(parameter.values[field.path]) } : { value: String(parameter.values[field.path] ?? '') })}
                          required={field.control !== 'toggle' && field.required} min={field.constraints?.min} max={field.constraints?.max} step={field.constraints?.step ?? 'any'} minLength={field.constraints?.minLength} maxLength={field.constraints?.maxLength} pattern={field.constraints?.pattern}
                          disabled={running || parameter.saving} onChange={event => updateParameter({ values: { ...parameter.values, [field.path]: field.control === 'toggle' ? event.target.checked : event.target.value } })} />
                      </label>)}
                      {!editableFields(parameter.editor).length && <p className="creation-panel-note">{uiText('这类内容可在专业工作台中调整。', 'Adjust this content in the professional workbench.')}</p>}
                      {(parameter.error || parameter.saveUnknown || parameter.revision !== revision && changes.length > 0) && <div className="creation-panel-error" role="alert"><p>{parameter.saveUnknown ? uiText('保存结果待确认，请先重读内容并核对。', 'The save result is unknown. Reload and check the content.') : parameter.error || uiText('作品已更新，请重新读取设置。', 'The creation changed. Reload its settings.')}</p></div>}
                      {editableFields(parameter.editor).length > 0 && <div className="creation-parameter-actions"><button type="submit" className="creation-primary" data-testid="simple-apply-parameters" disabled={!changes.length || running || parameter.saving || parameter.saveUnknown || parameter.revision !== revision}>{parameter.saving ? uiText('正在应用…', 'Applying…') : uiText('应用修改', 'Apply changes')}</button>
                        {(changes.length > 0 || parameter.saveUnknown) && <button type="button" disabled={parameter.saving} onClick={reloadParameter}>{uiText('放弃输入并重读', 'Discard draft and reload')}</button>}</div>}
                      <button type="button" className="creation-more-settings" onClick={openElementEditor}>{uiText('在专业工作台中打开', 'Open in professional workbench')}<ExternalLink size={13} /></button>
                    </form>}
            </section>
          </div>
          <button type="button" className="creation-recovery-link" onClick={() => setActiveView('history')}><History size={17} />{uiText('查看恢复点', 'View recovery points')}</button>
        </aside>
      </div>
      <div className="creation-existing-view" hidden={active && home}>{children}</div>
    </div>
    {active && setupOpen && <AgentSetupDialog snapshot={snapshot} initialProvider={effectiveProvider} busy={running} available={agent.available} pending={Boolean(agent.pending)} error={agent.error}
      onClose={() => setSetupOpen(false)} invoke={agent.invoke} onChoose={next => { if (next !== effectiveProvider || !snapshot?.conversationId) void newConversation(next); }} />}
    {active && historyOpen && <AgentHistoryDialog snapshot={snapshot} busy={busy} pending={Boolean(agent.pending)} invoke={agent.invoke} onClose={() => setHistoryOpen(false)}
      draftConversationIds={snapshot?.sessions.filter(session => drafts[`${workspaceId}:${session.id}`]?.trim() || attached.draftKeys.includes(`${workspaceId}:${session.id}`)).map(session => session.id) ?? []}
      onDeleted={discardConversationDrafts} />}
  </div>;
}
