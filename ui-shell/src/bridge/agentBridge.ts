import { formatTemplate, renderUiMessage, uiMessage, uiText, type UiMessage } from '../i18n';

export interface AgentText { key: string; zh: string; en: string; args: Record<string, string> }
export const agentText = (fallback: string | undefined, text?: AgentText) => text
  ? formatTemplate(uiText(text.zh, text.en), text.args) : fallback ?? '';

export const AGENT_PROVIDERS = ['codex', 'claude', 'grok', 'antigravity', 'deepseek'] as const;
export type AgentProviderId = typeof AGENT_PROVIDERS[number];
export type AgentState = 'idle' | 'starting' | 'running' | 'waiting_approval' | 'stopping' | 'failed';
export const AGENT_EFFORTS = ['low', 'medium', 'high', 'xhigh', 'max'] as const;
export type AgentEffort = string;
export type AgentPermission = 'default' | 'auto-approve' | 'read-only' | 'full-access';
export interface AgentTurnOptions { effort?: AgentEffort; permission?: AgentPermission }
export interface AgentAttachment { id: string; name: string; size: number; mediaType: 'application/octet-stream' | 'image/png' | 'image/jpeg' | 'image/gif' }
export interface AgentModel { id: string; label: string; efforts?: string[]; defaultEffort?: string }
export type AgentPermissionProfile = 'workspace' | 'read-only' | 'full-access' | 'auto-approve';
export interface AgentRunSettings { model: string; effort: string; permissionProfile: AgentPermissionProfile }
export const DEFAULT_AGENT_SETTINGS: AgentRunSettings = { model: '', effort: '', permissionProfile: 'workspace' };
export interface AgentSettingsCatalog { state: 'loading' | 'ready' | 'error'; error?: string;
  models: { id: string; label: string; efforts: string[]; defaultEffort: string }[]; permissionProfiles: AgentPermissionProfile[] }
export interface AgentContextAttachment { source: string; kind: string; text: string; truncated?: boolean }
export interface AgentFileVersion { text?: string; hash?: string; size?: number; omitted?: string }
export interface AgentRunRecord {
  runId: string; requestId?: string; conversationId: string; state: 'preparing' | 'running' | 'waiting_for_input' | 'cancelling' | 'finalizing' | 'completed' | 'failed' | 'cancelled' | 'interrupted';
  agentId: AgentProviderId; createdAt: number; endedAt?: number; cliVersion?: string | null; adapterVersion: string;
  canonicalRoot: string; executionTargetId: string; error?: string; agentStatement?: string;
  execution: { cleanup: string; exitCode?: number; pid?: number; processStartedAt?: string; endReason?: string; stopMethod?: string };
  context?: { task: string; attachments: AgentContextAttachment[]; permissionProfile: string; settings?: AgentRunSettings };
  changes?: { coverage: string; attribution?: string; capturedAt?: number; scope?: string; files: { path: string; kind: string; before: AgentFileVersion; after: AgentFileVersion }[] };
  verification?: { status: 'not_run' | 'running' | 'passed' | 'failed' | 'blocked' | 'cancelled' | 'interrupted'; requestId?: string; executionTaskId?: string;
    command?: string; directory?: string; startedAt?: number; endedAt?: number; stale?: boolean; fingerprintError?: string; cancelRequestedAt?: number; cleanup?: string; evidence?: unknown; startReceipt?: unknown };
}
export interface AgentModelCatalog {
  state: 'loading' | 'ready' | 'failed'; models: AgentModel[]; source?: 'cli' | 'service';
  defaultModel?: string; requiresSelection?: boolean; at?: number; error?: string; errorText?: AgentText;
}
export const validModelId = (value: string) => /^[A-Za-z0-9][A-Za-z0-9._:/@+\-]{0,199}$/.test(value);
export interface AgentProvider {
  id: AgentProviderId;
  label: string;
  installed: boolean;
  executable: string | null;
  version?: string;
  setupUrl: string;
  loginAvailable: boolean;
  error?: string;
  errorText?: AgentText;
  detection?: 'missing' | 'found' | 'ready' | 'version_failed';
  checkAvailable?: boolean;
  modelCatalog?: AgentModelCatalog;
  capabilities?: { structuredEvents: boolean; interactivePermission: boolean; sessionResume: boolean; cancellation: string; usageReporting: boolean; permissionProfile: string; runSettings?: boolean };
  connection?: { state: 'checking' | 'verified' | 'failed'; scope: 'protocol' | 'response'; at?: number; error?: string; errorText?: AgentText };
}
export interface AgentMessage { id: string; role: 'user' | 'assistant' | 'system' | 'tool'; text: string; textText?: AgentText; at: string | number; attachments?: AgentAttachment[]; delivery?: 'pending' | 'sent' | 'failed' }
export interface AgentConversation extends AgentTurnOptions { id: string; provider: AgentProviderId; model?: string | null; title: string; titleText?: AgentText; updatedAt: string | number }
export interface AgentApproval {
  id: string; title: string; detail: string;
  titleText?: AgentText;
  choices: { id: string; label: string; labelText?: AgentText; description?: string }[];
  kind?: 'permission' | 'question'; multiSelect?: boolean; allowCustom?: boolean;
}
export type AgentApprovalAnswer = { choice: string } | { choices: string[]; answer?: string };
export interface AgentSnapshot extends AgentTurnOptions {
  state: AgentState;
  conversationId: string | null;
  provider: AgentProviderId | null;
  model?: string | null;
  usesSessionModel?: boolean;
  sessions: AgentConversation[];
  messages: AgentMessage[];
  approvals: AgentApproval[];
  providers: AgentProvider[];
  error?: string;
  errorText?: AgentText;
  detecting?: boolean;
  checking?: boolean;
  linkOpened?: boolean;
  storageBlocked?: boolean;
  history?: { count: number; maxCount: number; bytes: number; maxBytes: number; level: 'ok' | 'near' | 'full' | 'unreadable' };
  historyResult?: { operation: string; conversationId?: string; path?: string };
  sequence: number;
  project?: { id: string; canonicalRoot: string; executionTargetId: string };
  runs?: AgentRunRecord[];
  run?: AgentRunRecord;
  activeRunPhase?: AgentRunRecord['state'];
  runHistoryCapacity?: { count: number; maxCount: number; level: 'ok' | 'near' | 'full' };
  enabled?: boolean;
  writeBlocked?: boolean;
  verificationAvailable?: boolean;
  activeVerification?: { runId: string; cancelling: boolean };
  exportedRun?: { runId: string; path: string };
  settings?: AgentRunSettings;
  settingsExplicit?: boolean;
  modelCatalogs?: Partial<Record<AgentProviderId, AgentSettingsCatalog>>;
}
export type AgentRequest = (
  | { operation: 'status' | 'detect' | 'stop' | 'retryHistory' | 'refreshRun' }
  | ({ operation: 'new'; provider: AgentProviderId; model?: string } & AgentTurnOptions)
  | { operation: 'login' | 'openSetup' | 'checkConnection' | 'models'; provider: AgentProviderId }
  | { operation: 'exportConversation'; conversationId: string; includeRuns?: boolean }
  | { operation: 'deleteConversation'; conversationId: string; confirmed: boolean }
  | { operation: 'deleteConversations'; conversationIds: string[]; confirmed: boolean }
  | { operation: 'recoverHistory'; confirmed: boolean }
  | { operation: 'openLink'; url: string }
  | { operation: 'select'; conversationId: string }
  | ({ operation: 'send'; provider: AgentProviderId; conversationId?: string; model?: string; text: string; attachments?: string[]; requestId?: string; context?: AgentContextAttachment[]; settings?: AgentRunSettings } & AgentTurnOptions)
  | { operation: 'selectRun' | 'stopVerification' | 'exportRun'; runId: string }
  | { operation: 'verify'; runId: string; kind: 'validate' | 'build'; requestId: string }
  | { operation: 'settingsCatalog'; provider: AgentProviderId }
  | ({ operation: 'approve'; id: string } & AgentApprovalAnswer)) & { projectId?: string };

export type AgentAttachmentRequest =
  | { operation: 'attachmentSelect' }
  | { operation: 'attachmentTarget'; target: string }
  | { operation: 'attachmentBegin'; name: string; size: number }
  | { operation: 'attachmentChunk'; id: string; offset: number; data: string }
  | { operation: 'attachmentCommit' | 'attachmentDiscard' | 'attachmentPreview'; id: string };
export interface AgentAttachmentReply { ok: true; id?: string; offset?: number; attachment?: AgentAttachment; attachments?: AgentAttachment[]; dataUrl?: string; target?: string }

declare global {
  interface Window {
    __COPPERBENCH_AGENT_HOST__?: { nativeFileSelection?: boolean; invoke(payload: AgentRequest | AgentAttachmentRequest): Promise<unknown> };
  }
}

const record = (value: unknown): value is Record<string, unknown> => Boolean(value && typeof value === 'object' && !Array.isArray(value));
const providerId = (value: unknown): value is AgentProviderId => AGENT_PROVIDERS.includes(value as AgentProviderId);
const strings = (value: Record<string, unknown>, keys: string[]) => keys.every(key => typeof value[key] === 'string');
const timestamp = (value: unknown) => (typeof value === 'number' && Number.isFinite(value)) || (typeof value === 'string' && Number.isFinite(Date.parse(value)));
const localized = (value: unknown) => value === undefined || (record(value) && strings(value, ['key', 'zh', 'en'])
  && record(value.args) && Object.values(value.args).every(item => typeof item === 'string'));
const optionalString = (value: unknown) => value === undefined || typeof value === 'string';
const modelId = (value: unknown) => value === undefined || value === null || typeof value === 'string' && validModelId(value);
const validEffort = (value: unknown) => typeof value === 'string' && /^[a-z][a-z0-9_-]{0,63}$/.test(value);
const turnOptions = (value: Record<string, unknown>) => (value.effort === undefined || validEffort(value.effort))
  && (value.permission === undefined || ['default', 'auto-approve', 'read-only', 'full-access'].includes(String(value.permission)));
const permissionProfile = (value: unknown) => ['workspace', 'read-only', 'full-access', 'auto-approve'].includes(String(value));
const settingsRecord = (value: unknown) => record(value) && strings(value, ['model', 'effort', 'permissionProfile']) && permissionProfile(value.permissionProfile);
const settingsCatalogRecord = (value: unknown) => record(value) && ['loading', 'ready', 'error'].includes(String(value.state))
  && (value.error === undefined || typeof value.error === 'string') && Array.isArray(value.permissionProfiles) && value.permissionProfiles.every(permissionProfile)
  && Array.isArray(value.models) && value.models.every(model => record(model) && strings(model, ['id', 'label', 'defaultEffort'])
    && Array.isArray(model.efforts) && model.efforts.every(validEffort));
const runRecord = (value: unknown): boolean => record(value) && strings(value, ['runId', 'conversationId', 'state', 'adapterVersion', 'canonicalRoot', 'executionTargetId'])
  && providerId(value.agentId) && timestamp(value.createdAt) && record(value.execution) && typeof value.execution.cleanup === 'string'
  && ['preparing', 'running', 'waiting_for_input', 'cancelling', 'finalizing', 'completed', 'failed', 'cancelled', 'interrupted'].includes(String(value.state))
  && (value.changes === undefined || (record(value.changes) && typeof value.changes.coverage === 'string' && Array.isArray(value.changes.files)
    && value.changes.files.every(file => record(file) && strings(file, ['path', 'kind']) && record(file.before) && record(file.after)
      && [file.before, file.after].every(version => version.text === undefined || typeof version.text === 'string'))))
  && (value.context === undefined || (record(value.context) && typeof value.context.task === 'string' && Array.isArray(value.context.attachments)
    && value.context.attachments.every(item => record(item) && strings(item, ['source', 'kind', 'text']))))
  && (value.verification === undefined || (record(value.verification) && ['not_run', 'running', 'passed', 'failed', 'blocked', 'cancelled', 'interrupted'].includes(String(value.verification.status))));
const attachment = (value: unknown): value is AgentAttachment => record(value) && strings(value, ['id', 'name', 'mediaType'])
  && /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(String(value.id))
  && String(value.name).length <= 180 && Number.isSafeInteger(value.size) && Number(value.size) > 0 && Number(value.size) <= 10 * 1024 * 1024
  && ['application/octet-stream', 'image/png', 'image/jpeg', 'image/gif'].includes(String(value.mediaType));
const attachmentList = (value: unknown) => value === undefined || Array.isArray(value) && value.length <= 8 && value.every(attachment);

/** Preserve authored translations across asynchronous failures and later locale changes. */
export class AgentAttachmentError extends Error {
  readonly localizedMessage: UiMessage;
  constructor(message: UiMessage) {
    super(renderUiMessage(message));
    this.localizedMessage = message;
  }
}
export const attachmentErrorMessage = (error: unknown): UiMessage => error instanceof AgentAttachmentError
  ? error.localizedMessage : error instanceof Error ? error.message : String(error);

export function parseAttachmentReply(value: unknown): AgentAttachmentReply {
  if (record(value) && value.ok === false && typeof value.error === 'string' && localized(value.errorText)) {
    const text = value.errorText as AgentText | undefined;
    throw new AgentAttachmentError(text ? uiMessage(formatTemplate(text.zh, text.args), formatTemplate(text.en, text.args)) : value.error);
  }
  if (!record(value) || value.ok !== true || value.attachment !== undefined && !attachment(value.attachment)
    || !attachmentList(value.attachments) || !optionalString(value.id) || !optionalString(value.target)
    || value.offset !== undefined && (!Number.isSafeInteger(value.offset) || Number(value.offset) < 0)
    || value.dataUrl !== undefined && (typeof value.dataUrl !== 'string' || value.dataUrl.length > 400000 || !/^data:image\/png;base64,[A-Za-z0-9+/=]+$/.test(value.dataUrl)))
    throw new AgentAttachmentError(uiMessage('附件响应无效，请重新添加。', 'Invalid attachment response. Add the file again.'));
  return value as unknown as AgentAttachmentReply;
}

export async function attachmentRequest(request: AgentAttachmentRequest, host = window.__COPPERBENCH_AGENT_HOST__): Promise<AgentAttachmentReply> {
  if (!host) throw new AgentAttachmentError(uiMessage('附件需要桌面助手连接。', 'Attachments require the desktop assistant connection.'));
  return parseAttachmentReply(await host.invoke(request));
}
const catalog = (value: unknown) => value === undefined || record(value)
  && ['loading', 'ready', 'failed'].includes(String(value.state)) && Array.isArray(value.models) && value.models.length <= 1000
  && value.models.every(model => record(model) && strings(model, ['id', 'label']) && validModelId(String(model.id))
    && (model.efforts === undefined || Array.isArray(model.efforts) && model.efforts.every(validEffort)) && optionalString(model.defaultEffort))
  && (value.source === undefined || value.source === 'cli' || value.source === 'service')
  && (value.requiresSelection === undefined || typeof value.requiresSelection === 'boolean')
  && modelId(value.defaultModel) && (value.at === undefined || typeof value.at === 'number' && Number.isFinite(value.at))
  && optionalString(value.error) && localized(value.errorText);

/** Validate the native boundary before rendering or addressing a conversation. */
export function parseAgentSnapshot(value: unknown): AgentSnapshot {
  if (record(value) && (!localized(value.errorText)
    || !turnOptions(value)
    || !modelId(value.model)
    || (value.usesSessionModel !== undefined && (typeof value.usesSessionModel !== 'boolean'
      || value.usesSessionModel && (typeof value.conversationId !== 'string' || !value.conversationId || value.model != null)))
    || (value.checking !== undefined && typeof value.checking !== 'boolean')
    || (value.linkOpened !== undefined && typeof value.linkOpened !== 'boolean')
    || (value.storageBlocked !== undefined && typeof value.storageBlocked !== 'boolean')
    || (value.history !== undefined && (!record(value.history) || !['ok', 'near', 'full', 'unreadable'].includes(String(value.history.level))
      || !['count', 'maxCount', 'bytes', 'maxBytes'].every(key => Number.isSafeInteger(value.history && (value.history as Record<string, unknown>)[key]) && Number((value.history as Record<string, unknown>)[key]) >= 0)))
    || (value.historyResult !== undefined && (!record(value.historyResult) || typeof value.historyResult.operation !== 'string'
      || !optionalString(value.historyResult.path) || !optionalString(value.historyResult.conversationId))))) {
    throw new Error(uiText('助手返回了无效的历史或连接状态。', 'The assistant returned invalid history or connection status.'));
  }
  if (!record(value)
    || !['idle', 'starting', 'running', 'waiting_approval', 'stopping', 'failed'].includes(String(value.state))
    || !(value.conversationId === null || typeof value.conversationId === 'string')
    || !(value.provider === null || providerId(value.provider))
    || !Number.isSafeInteger(value.sequence) || Number(value.sequence) < 0
    || !(value.error === undefined || value.error === null || typeof value.error === 'string')
    || !(value.detecting === undefined || typeof value.detecting === 'boolean')
    || !(value.project === undefined || (record(value.project) && strings(value.project, ['id', 'canonicalRoot', 'executionTargetId'])))
    || !(value.runs === undefined || (Array.isArray(value.runs) && value.runs.every(runRecord)))
    || !(value.run === undefined || runRecord(value.run))
    || !(value.activeVerification === undefined || (record(value.activeVerification) && typeof value.activeVerification.runId === 'string' && typeof value.activeVerification.cancelling === 'boolean'))
    || !(value.exportedRun === undefined || (record(value.exportedRun) && strings(value.exportedRun, ['runId', 'path'])))
    || !(value.settings === undefined || settingsRecord(value.settings))
    || !(value.modelCatalogs === undefined || (record(value.modelCatalogs) && Object.entries(value.modelCatalogs).every(([id, value]) => providerId(id) && settingsCatalogRecord(value))))
    || !Array.isArray(value.sessions) || !value.sessions.every(item => record(item) && strings(item, ['id', 'title']) && turnOptions(item) && modelId(item.model) && localized(item.titleText) && timestamp(item.updatedAt) && providerId(item.provider))
    || !Array.isArray(value.messages) || !value.messages.every(item => record(item) && strings(item, ['id', 'text']) && localized(item.textText) && timestamp(item.at) && attachmentList(item.attachments)
      && (item.delivery === undefined || ['pending', 'sent', 'failed'].includes(String(item.delivery))) && ['user', 'assistant', 'system', 'tool'].includes(String(item.role)))
    || !Array.isArray(value.approvals) || !value.approvals.every(item => record(item) && strings(item, ['id', 'title', 'detail']) && Array.isArray(item.choices)
      && (item.kind === undefined || item.kind === 'permission' || item.kind === 'question')
      && (item.multiSelect === undefined || typeof item.multiSelect === 'boolean') && (item.allowCustom === undefined || typeof item.allowCustom === 'boolean')
      && localized(item.titleText) && item.choices.every(choice => record(choice) && strings(choice, ['id', 'label']) && localized(choice.labelText) && (choice.description === undefined || typeof choice.description === 'string')))
    || !Array.isArray(value.providers) || !value.providers.every(item => record(item) && providerId(item.id) && strings(item, ['label', 'setupUrl'])
      && typeof item.installed === 'boolean' && typeof item.loginAvailable === 'boolean'
      && optionalString(item.error) && localized(item.errorText)
      && catalog(item.modelCatalog)
      && (item.checkAvailable === undefined || typeof item.checkAvailable === 'boolean')
      && (item.detection === undefined || ['missing', 'found', 'ready', 'version_failed'].includes(String(item.detection)))
      && (item.connection === undefined || (record(item.connection) && ['checking', 'verified', 'failed'].includes(String(item.connection.state))
        && ['protocol', 'response'].includes(String(item.connection.scope)) && (item.connection.at === undefined || timestamp(item.connection.at))
        && optionalString(item.connection.error) && localized(item.connection.errorText)))
      && (item.executable === null || typeof item.executable === 'string') && (item.version === undefined || typeof item.version === 'string'))) {
    throw new Error(uiText('助手返回了无法读取的状态。请更新应用后重试。', 'The assistant returned an unsupported status. Update the app and try again.'));
  }
  return value as unknown as AgentSnapshot;
}

export const agentBridge = {
  get available() { return typeof window !== 'undefined' && Boolean(window.__COPPERBENCH_AGENT_HOST__); },
  async invoke(request: AgentRequest): Promise<AgentSnapshot> {
    const host = window.__COPPERBENCH_AGENT_HOST__;
    if (!host) throw new Error(uiText('请在支持创作助手的桌面应用中使用此功能。', 'Use a desktop app that supports creation assistants.'));
    return parseAgentSnapshot(await host.invoke(request));
  }
};

export const agentIsActive = (state?: AgentState) => state === 'starting' || state === 'running' || state === 'waiting_approval' || state === 'stopping';
