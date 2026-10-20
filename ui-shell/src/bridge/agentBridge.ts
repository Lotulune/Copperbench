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
}
export type AgentRequest =
  | { operation: 'status' | 'detect' | 'stop' }
  | { operation: 'new' | 'login' | 'openSetup'; provider: AgentProviderId }
  | { operation: 'select'; conversationId: string }
  | { operation: 'send'; provider: AgentProviderId; conversationId?: string; text: string }
  | ({ operation: 'approve'; id: string } & AgentApprovalAnswer);

declare global {
  interface Window {
    __COPPERBENCH_AGENT_HOST__?: { invoke(payload: AgentRequest): Promise<unknown> };
  }
}

const record = (value: unknown): value is Record<string, unknown> => Boolean(value && typeof value === 'object' && !Array.isArray(value));
const providerId = (value: unknown): value is AgentProviderId => AGENT_PROVIDERS.includes(value as AgentProviderId);
const strings = (value: Record<string, unknown>, keys: string[]) => keys.every(key => typeof value[key] === 'string');
const timestamp = (value: unknown) => (typeof value === 'number' && Number.isFinite(value)) || (typeof value === 'string' && Number.isFinite(Date.parse(value)));

/** Validate the native boundary before rendering or addressing a conversation. */
export function parseAgentSnapshot(value: unknown): AgentSnapshot {
  if (!record(value)
    || !['idle', 'starting', 'running', 'waiting_approval', 'stopping', 'failed'].includes(String(value.state))
    || !(value.conversationId === null || typeof value.conversationId === 'string')
    || !(value.provider === null || providerId(value.provider))
    || !Number.isSafeInteger(value.sequence) || Number(value.sequence) < 0
    || !(value.error === undefined || value.error === null || typeof value.error === 'string')
    || !(value.detecting === undefined || typeof value.detecting === 'boolean')
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
