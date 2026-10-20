import type { AgentProviderId } from '../bridge/agentBridge';
import codex from '../assets/cli/codex.svg';
import claude from '../assets/cli/claude.svg';
import grok from '../assets/cli/grok.svg';
import antigravity from '../assets/cli/antigravity.svg';
import deepseek from '../assets/cli/deepseek.svg';
import './agentProvider.css';

export const AGENT_PROVIDER_NAMES: Record<AgentProviderId, string> = {
  codex: 'Codex CLI', claude: 'Claude Code', grok: 'Grok Build', antigravity: 'Antigravity CLI', deepseek: 'DeepSeek Harness'
};
const icons = { codex, claude, grok, antigravity, deepseek };

/** The adjacent provider name supplies the accessible label. */
export function AgentProviderIcon({ provider, size = 18 }: { provider: AgentProviderId; size?: number }) {
  return <img src={icons[provider]} width={size} height={size} alt="" aria-hidden="true" draggable={false}
    data-provider-icon={provider} className={`agent-provider-icon${provider === 'codex' || provider === 'grok' ? ' is-monochrome' : ''}`} />;
}
