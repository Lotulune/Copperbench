import { useState } from 'react';
import { Download, ExternalLink, RefreshCw } from 'lucide-react';
import { AGENT_PROVIDERS, agentText, type AgentProvider, type AgentProviderId, type AgentRequest, type AgentSnapshot } from '../bridge/agentBridge';
import { uiText } from '../i18n';
import { Dialog } from './Dialog';
import { AgentProviderIcon, AGENT_PROVIDER_NAMES } from './AgentProviderIcon';
import { CopyTextButton } from './AgentMessageBody';
import { AgentProviderSelect } from './AgentProviderSelect';

export function providerStatus(provider?: AgentProvider) {
  if (!provider?.installed) return uiText('未安装', 'Not installed');
  if (provider.error) return uiText('版本检测失败', 'Version check failed');
  if (provider.connection?.state === 'checking') return uiText('正在检查连接…', 'Checking connection…');
  if (provider.connection?.state === 'failed') return uiText('连接检查失败', 'Connection check failed');
  if (provider.connection?.state === 'verified') return provider.connection.scope === 'response'
    ? uiText('最近一次模型请求成功', 'Last model request succeeded') : uiText('CLI 通道可用 · 模型待验证', 'CLI connected · model unverified');
  return uiText('已安装 · 连接待验证', 'Installed · connection unverified');
}

// Official upstream commands. Deliberately displayed for user execution, never silently run.
const installCommands: Record<AgentProviderId, { windows?: string; linux?: string }> = {
  codex: { windows: 'npm install -g @openai/codex', linux: 'npm install -g @openai/codex' },
  claude: { windows: 'irm https://claude.ai/install.ps1 | iex', linux: 'curl -fsSL https://claude.ai/install.sh | bash' },
  grok: { windows: 'irm https://x.ai/cli/install.ps1 | iex', linux: 'curl -fsSL https://x.ai/cli/install.sh | bash' },
  antigravity: {},
  deepseek: { windows: 'npm install -g @deepseek-ai/dsh', linux: 'npm install -g @deepseek-ai/dsh' }
};
const setupUrls: Record<AgentProviderId, string> = {
  codex: 'https://developers.openai.com/codex/cli/', claude: 'https://code.claude.com/docs/en/setup',
  grok: 'https://docs.x.ai/build/overview', antigravity: 'https://antigravity.google/docs/cli/install/',
  deepseek: 'https://github.com/deepseek-ai/deepseek-harness'
};

export function AgentSetupDialog({ snapshot, initialProvider, busy, available, pending, error, onClose, invoke, onChoose }: {
  snapshot: AgentSnapshot | null; initialProvider: AgentProviderId; busy: boolean; available: boolean; pending: boolean;
  error?: string | null;
  onClose: () => void; invoke: (request: AgentRequest) => Promise<AgentSnapshot | null>; onChoose: (id: AgentProviderId) => void;
}) {
  const [selected, setSelected] = useState(initialProvider);
  const [platform, setPlatform] = useState<'windows' | 'linux'>(navigator.platform.toLowerCase().includes('win') ? 'windows' : 'linux');
  const info = snapshot?.providers.find(item => item.id === selected);
  const command = installCommands[selected][platform];
  const blocked = !available || pending || busy || snapshot?.checking;
  const openGuide = () => {
    if (available) void invoke({ operation: 'openLink', url: setupUrls[selected] });
    else window.open(setupUrls[selected], '_blank', 'noopener,noreferrer');
  };
  return <Dialog open title={uiText('安装与连接助手', 'Install and connect assistants')} onClose={onClose} testId="agent-setup-dialog"
    footer={<><button type="button" className="btn-secondary" onClick={onClose}>{uiText('完成', 'Done')}</button>
      <button type="button" className="btn-primary" disabled={Boolean(blocked) || !info?.installed} onClick={() => { onChoose(selected); onClose(); }}>{uiText('使用此助手', 'Use this assistant')}</button></>}>
    <div className="agent-setup-toolbar"><p>{uiText('安装、配置账号，然后回到这里检查。', 'Install, configure your account, then return here to check.')}</p>
      <button type="button" className="btn-secondary" data-testid="agent-detect" disabled={!available || pending || snapshot?.detecting} onClick={() => void invoke({ operation: 'detect' })}>
        <RefreshCw size={14} aria-hidden="true" className={snapshot?.detecting ? 'creation-spinner' : ''} />{snapshot?.detecting ? uiText('检测中…', 'Detecting…') : uiText('重新检测全部', 'Detect all again')}</button></div>
    {(error || snapshot?.error) && <p className="agent-inline-error" role="alert">{error || agentText(snapshot?.error, snapshot?.errorText)}</p>}
    <div className="agent-setup-layout"><div className="agent-setup-mobile"><AgentProviderSelect id="setup-provider" testId="setup-provider-trigger" value={selected} providers={snapshot?.providers ?? []}
      disabled={false} active detecting={snapshot?.detecting} onChange={setSelected} /></div><nav className="agent-setup-list" aria-label={uiText('创作助手', 'Assistants')}>
      {AGENT_PROVIDERS.map(id => <button type="button" key={id} aria-pressed={selected === id} onClick={() => setSelected(id)} data-testid={`setup-provider-${id}`}>
        <AgentProviderIcon provider={id} size={24} /><span><strong>{AGENT_PROVIDER_NAMES[id]}</strong><small>{snapshot?.detecting ? uiText('检测中…', 'Detecting…')
          : !available ? uiText('在桌面应用中检测', 'Detect in the desktop app') : providerStatus(snapshot?.providers.find(item => item.id === id))}</small></span></button>)}
    </nav><section className="agent-setup-detail" aria-labelledby="agent-setup-name">
      <h3 id="agent-setup-name"><AgentProviderIcon provider={selected} size={25} />{AGENT_PROVIDER_NAMES[selected]}</h3>
      <p role="status">{providerStatus(info)}</p>
      {info?.version && <p className="agent-cli-version">{info.version}</p>}
      {info?.executable && <details><summary>{uiText('程序位置', 'Executable location')}</summary><code>{info.executable}</code></details>}
      {info?.error && <p className="agent-inline-error" role="alert">{agentText(info.error, info.errorText)}</p>}
      <h4><Download size={15} aria-hidden="true" />{uiText('安装', 'Install')}</h4>
      <label className="agent-platform-label">{uiText('系统', 'System')}<select value={platform} onChange={event => setPlatform(event.target.value as 'windows' | 'linux')}><option value="windows">{uiText('Windows · PowerShell', 'Windows · PowerShell')}</option><option value="linux">{uiText('Linux · 终端', 'Linux · Terminal')}</option></select></label>
      {command ? <><p>{selected === 'codex' || selected === 'deepseek' ? uiText('先安装 Node.js，再在终端粘贴并运行：', 'Install Node.js first, then paste and run this in a terminal:')
        : uiText('在终端粘贴并运行官方安装命令：', 'Paste and run the official installer in a terminal:')}</p>
        <div className="agent-install-command"><code>{command}</code><CopyTextButton text={command} code /></div></>
        : <p>{uiText('打开官方安装页，选择当前系统的安装方式。', 'Open the official installation page and follow the steps for your system.')}</p>}
      <button type="button" className="btn-secondary" onClick={openGuide}><ExternalLink size={14} aria-hidden="true" />{uiText('官方安装指南', 'Official installation guide')}</button>
      <h4>{uiText('配置与验证', 'Configure and verify')}</h4>
      <p>{selected === 'deepseek' ? uiText('运行 dsh web，在 Settings → Models 配置模型。', 'Run dsh web and configure your model in Settings → Models.')
        : selected === 'claude' ? uiText('沿用本机 Claude Code 的模型、服务和凭据；已有配置可直接使用。', 'Uses the model, service and credentials already configured in your host Claude Code.')
          : uiText('完成官方 CLI 的登录或模型配置后，返回重新检测。', 'Complete the official CLI sign-in or model setup, then detect it again here.')}</p>
      <div className="agent-detail-actions">{info?.loginAvailable && <button type="button" className="btn-secondary" disabled={Boolean(blocked)} onClick={() => void invoke({ operation: 'login', provider: selected })}>{uiText('打开登录', 'Open sign-in')}</button>}
        {info?.checkAvailable !== false && <button type="button" className="btn-secondary" data-testid="agent-check-connection" disabled={Boolean(blocked) || !info?.installed} onClick={() => void invoke({ operation: 'checkConnection', provider: selected })}>{uiText('检查 CLI 连接', 'Check CLI connection')}</button>}
        {snapshot?.checking && <button type="button" className="btn-secondary" onClick={() => void invoke({ operation: 'stop' })}>{uiText('取消检查', 'Cancel check')}</button>}</div>
      <p className="agent-note">{selected === 'antigravity' ? uiText('此 CLI 没有独立的协议检查。发送消息后以实际回复验证模型连接；其非交互模式使用已有权限规则。', 'This CLI has no standalone handshake. A successful reply verifies its model connection. Headless mode uses existing permission rules.')
        : uiText('连接检查只验证 CLI 协议通道，不请求模型或修改作品。一次成功回复才代表当次模型配置可用。', 'The check verifies the CLI protocol without requesting a model or changing your work. A successful reply verifies the model configuration for that request.')}</p>
      {info?.connection?.at && <p>{uiText('最近检查：', 'Last check: ')}{new Date(info.connection.at).toLocaleString()}</p>}
      {info?.connection?.error && <p role="alert" className="agent-inline-error">{agentText(info.connection.error, info.connection.errorText)}</p>}
      <p className="agent-note">{uiText('安装完成后点击“重新检测全部”。仍未发现时，重启 Copperbench 以读取新 PATH。', 'After installing, select “Detect all again”. If it is still missing, restart Copperbench to read the updated PATH.')}</p>
    </section></div>
  </Dialog>;
}
