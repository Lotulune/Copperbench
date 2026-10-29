import { uiText } from '../i18n';

export const AGENT_PROVIDERS = ['codex', 'claude', 'grok', 'antigravity', 'deepseek'] as const;
export type AgentProviderId = typeof AGENT_PROVIDERS[number];
export type AgentState = 'idle' | 'starting' | 'running' | 'waiting_approval' | 'stopping' | 'failed';
export interface AgentProvider {
  id: AgentProviderId;
  label: string;
  installed: boolean;
  executable: string | null;
  version?: string;
  setupUrl: string;
  loginAvailable: boolean;
  error?: string;
  availability?: 'not_found' | 'unavailable' | 'unchecked' | 'detected';
  authentication?: string;
  capabilities?: { structuredEvents: boolean; interactivePermission: boolean; sessionResume: boolean; cancellation: string; usageReporting: boolean; permissionProfile: string; runSettings?: boolean };
}
export type AgentPermissionProfile = 'workspace' | 'read-only' | 'full-access';
export interface AgentRunSettings { model: string; effort: string; permissionProfile: AgentPermissionProfile }
export const DEFAULT_AGENT_SETTINGS: AgentRunSettings = { model: '', effort: '', permissionProfile: 'workspace' };
export interface AgentModelCatalog { state: 'loading' | 'ready' | 'error'; error?: string;
  models: { id: string; label: string; efforts: string[]; defaultEffort: string }[]; permissionProfiles: AgentPermissionProfile[] }
export interface AgentAttachment { source: string; kind: string; text: string; truncated?: boolean }
export interface AgentFileVersion { text?: string; hash?: string; size?: number; omitted?: string }
export interface AgentRunRecord {
  runId: string; conversationId: string; state: 'preparing' | 'running' | 'waiting_for_input' | 'cancelling' | 'finalizing' | 'completed' | 'failed' | 'cancelled' | 'interrupted';
  agentId: AgentProviderId; createdAt: number; endedAt?: number; cliVersion?: string | null; adapterVersion: string;
  canonicalRoot: string; executionTargetId: string; error?: string; agentStatement?: string;
  execution: { cleanup: string; exitCode?: number; pid?: number; processStartedAt?: string; endReason?: string; stopMethod?: string };
  context?: { task: string; attachments: AgentAttachment[]; permissionProfile: string; settings?: AgentRunSettings };
  changes?: { coverage: string; attribution?: string; capturedAt?: number; scope?: string; files: { path: string; kind: string; before: AgentFileVersion; after: AgentFileVersion }[] };
  verification?: { status: 'not_run' | 'running' | 'passed' | 'failed' | 'blocked' | 'cancelled' | 'interrupted'; requestId?: string; executionTaskId?: string;
    command?: string; directory?: string; startedAt?: number; endedAt?: number; stale?: boolean; cancelRequestedAt?: number; cleanup?: string; evidence?: unknown; startReceipt?: unknown };
}
export interface AgentMessage { id: string; role: 'user' | 'assistant' | 'system' | 'tool'; text: string; at: string | number }
export interface AgentConversation { id: string; provider: AgentProviderId; title: string; updatedAt: string | number }
export interface AgentApproval {
  id: string; title: string; detail: string;
  choices: { id: string; label: string; description?: string }[];
  kind?: 'permission' | 'question'; multiSelect?: boolean; allowCustom?: boolean;
}
export type AgentApprovalAnswer = { choice: string } | { choices: string[]; answer?: string };
export interface AgentSnapshot {
  state: AgentState;
  conversationId: string | null;
  provider: AgentProviderId | null;
  sessions: AgentConversation[];
  messages: AgentMessage[];
  approvals: AgentApproval[];
  providers: AgentProvider[];
  error?: string;
  detecting?: boolean;
  sequence: number;
  project?: { id: string; canonicalRoot: string; executionTargetId: string };
  runs?: AgentRunRecord[];
  run?: AgentRunRecord;
  enabled?: boolean;
  writeBlocked?: boolean;
  verificationAvailable?: boolean;
  activeVerification?: { runId: string; cancelling: boolean };
  exportedRun?: { runId: string; path: string };
  settings?: AgentRunSettings;
  modelCatalogs?: Partial<Record<AgentProviderId, AgentModelCatalog>>;
}
export type AgentRequest = (
  | { operation: 'status' | 'detect' | 'stop' | 'refreshRun' }
  | { operation: 'new' | 'login' | 'openSetup' | 'models'; provider: AgentProviderId }
  | { operation: 'select'; conversationId: string }
  | { operation: 'updateSettings'; conversationId: string; settings: AgentRunSettings }
  | { operation: 'send'; provider: AgentProviderId; conversationId?: string; text: string; requestId?: string; context?: AgentAttachment[]; settings?: AgentRunSettings }
  | { operation: 'selectRun' | 'stopVerification' | 'exportRun'; runId: string }
  | { operation: 'verify'; runId: string; kind: 'validate' | 'build'; requestId: string }
  | ({ operation: 'approve'; id: string } & AgentApprovalAnswer)) & { projectId?: string };

declare global {
  interface Window {
    __COPPERBENCH_AGENT_HOST__?: { invoke(payload: AgentRequest): Promise<unknown> };
  }
}

const record = (value: unknown): value is Record<string, unknown> => Boolean(value && typeof value === 'object' && !Array.isArray(value));
const providerId = (value: unknown): value is AgentProviderId => AGENT_PROVIDERS.includes(value as AgentProviderId);
const strings = (value: Record<string, unknown>, keys: string[]) => keys.every(key => typeof value[key] === 'string');
const timestamp = (value: unknown) => (typeof value === 'number' && Number.isFinite(value)) || (typeof value === 'string' && Number.isFinite(Date.parse(value)));
const permissionProfile = (value: unknown) => ['workspace', 'read-only', 'full-access'].includes(String(value));
const settingsRecord = (value: unknown) => record(value) && strings(value, ['model', 'effort', 'permissionProfile']) && permissionProfile(value.permissionProfile);
const modelCatalog = (value: unknown) => record(value) && ['loading', 'ready', 'error'].includes(String(value.state))
  && (value.error === undefined || typeof value.error === 'string') && Array.isArray(value.permissionProfiles) && value.permissionProfiles.every(permissionProfile)
  && Array.isArray(value.models) && value.models.every(model => record(model) && strings(model, ['id', 'label', 'defaultEffort'])
    && Array.isArray(model.efforts) && model.efforts.every(effort => typeof effort === 'string' && effort.length > 0));
const runRecord = (value: unknown): boolean => record(value) && strings(value, ['runId', 'conversationId', 'state', 'adapterVersion', 'canonicalRoot', 'executionTargetId'])
  && providerId(value.agentId) && timestamp(value.createdAt) && record(value.execution) && typeof value.execution.cleanup === 'string'
  && ['preparing', 'running', 'waiting_for_input', 'cancelling', 'finalizing', 'completed', 'failed', 'cancelled', 'interrupted'].includes(String(value.state))
  && (value.changes === undefined || (record(value.changes) && typeof value.changes.coverage === 'string' && Array.isArray(value.changes.files)
    && value.changes.files.every(file => record(file) && strings(file, ['path', 'kind']) && record(file.before) && record(file.after)
      && [file.before, file.after].every(version => version.text === undefined || typeof version.text === 'string'))))
  && (value.context === undefined || (record(value.context) && typeof value.context.task === 'string' && Array.isArray(value.context.attachments)
    && value.context.attachments.every(item => record(item) && strings(item, ['source', 'kind', 'text']))))
  && (value.verification === undefined || (record(value.verification) && ['not_run', 'running', 'passed', 'failed', 'blocked', 'cancelled', 'interrupted'].includes(String(value.verification.status))));

/** Validate the native boundary before rendering or addressing a conversation. */
export function parseAgentSnapshot(value: unknown): AgentSnapshot {
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
    || !(value.modelCatalogs === undefined || (record(value.modelCatalogs) && Object.entries(value.modelCatalogs).every(([id, catalog]) => providerId(id) && modelCatalog(catalog))))
    || !Array.isArray(value.sessions) || !value.sessions.every(item => record(item) && strings(item, ['id', 'title']) && timestamp(item.updatedAt) && providerId(item.provider))
    || !Array.isArray(value.messages) || !value.messages.every(item => record(item) && strings(item, ['id', 'text']) && timestamp(item.at) && ['user', 'assistant', 'system', 'tool'].includes(String(item.role)))
    || !Array.isArray(value.approvals) || !value.approvals.every(item => record(item) && strings(item, ['id', 'title', 'detail']) && Array.isArray(item.choices)
      && (item.kind === undefined || item.kind === 'permission' || item.kind === 'question')
      && (item.multiSelect === undefined || typeof item.multiSelect === 'boolean') && (item.allowCustom === undefined || typeof item.allowCustom === 'boolean')
      && item.choices.every(choice => record(choice) && strings(choice, ['id', 'label']) && (choice.description === undefined || typeof choice.description === 'string')))
    || !Array.isArray(value.providers) || !value.providers.every(item => record(item) && providerId(item.id) && strings(item, ['label', 'setupUrl'])
      && typeof item.installed === 'boolean' && typeof item.loginAvailable === 'boolean'
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
