import { useEffect, useLayoutEffect, useRef, useState } from 'react';
import { ChevronUp, RefreshCw, SlidersHorizontal } from 'lucide-react';
import { useAgent } from '../context/AgentContext';
import { uiText } from '../i18n';
import { DEFAULT_AGENT_SETTINGS, type AgentPermissionProfile, type AgentProvider, type AgentRunSettings as Settings } from '../bridge/agentBridge';

export function permissionLabel(profile: AgentPermissionProfile) {
  return profile === 'read-only' ? uiText('只读沙箱', 'Read-only sandbox') : profile === 'full-access' ? uiText('完全访问', 'Full access') : uiText('工作区写入', 'Workspace write');
}
export function effortLabel(effort: string) {
  const labels: Record<string, string> = { none: uiText('不启用', 'None'), minimal: uiText('最低', 'Minimal'), low: uiText('低', 'Low'), medium: uiText('中', 'Medium'), high: uiText('高', 'High'), xhigh: uiText('更高', 'Extra high') };
  return effort ? labels[effort] ?? effort : uiText('沿用默认', 'Use default');
}

/** A compact, non-modal picker. Selections apply only when the next message is sent. */
export function AgentRunSettings({ provider, settings, disabled, onChange }: { provider?: AgentProvider; settings: Settings; disabled: boolean; onChange: (settings: Settings) => void }) {
  const agent = useAgent(); const [open, setOpen] = useState(false);
  const [maxHeight, setMaxHeight] = useState(360);
  const container = useRef<HTMLDivElement>(null), trigger = useRef<HTMLButtonElement>(null), popover = useRef<HTMLDivElement>(null);
  const supported = provider?.capabilities?.runSettings === true;
  const catalog = provider ? agent.snapshot?.modelCatalogs?.[provider.id] : undefined;
  const model = catalog?.models.find(item => item.id === settings.model);
  const permissionProfiles = catalog?.state === 'ready' ? catalog.permissionProfiles : [settings.permissionProfile];
  const close = (restore = false) => { setOpen(false); if (restore) requestAnimationFrame(() => trigger.current?.focus()); };
  useEffect(() => { setOpen(false); }, [provider?.id, agent.snapshot?.project?.id]);
  useLayoutEffect(() => {
    if (!open) return;
    const place = () => setMaxHeight(Math.max(120, Math.min(420, (container.current?.getBoundingClientRect().top ?? 420) - 60)));
    place(); window.addEventListener('resize', place); return () => window.removeEventListener('resize', place);
  }, [open]);
  useEffect(() => {
    if (open && supported && provider?.installed && !catalog) void agent.invoke({ operation: 'models', provider: provider.id });
  }, [open, supported, provider?.installed, provider?.id, catalog, agent.invoke]);
  useEffect(() => {
    if (!open) return;
    popover.current?.focus();
    const outside = (event: PointerEvent) => { if (event.target instanceof Node && !container.current?.contains(event.target)) close(); };
    document.addEventListener('pointerdown', outside);
    return () => document.removeEventListener('pointerdown', outside);
  }, [open]);
  const defaultLabel = uiText('沿用 CLI / 会话默认', 'Use CLI / conversation default');
  return <div className="professional-agent-settings" ref={container} onBlur={event => { if (event.relatedTarget instanceof Node && !event.currentTarget.contains(event.relatedTarget)) close(); }}>
    <button type="button" className="agent-settings-toggle" ref={trigger} data-testid="agent-settings-toggle" aria-expanded={open} aria-controls="agent-settings-popover" aria-haspopup="dialog" onClick={() => setOpen(value => !value)}>
      <SlidersHorizontal size={14} aria-hidden="true" /><span>{uiText('模型、思考与权限', 'Model, reasoning & permissions')}</span>
      {supported && settings.permissionProfile !== 'workspace' && <span className={`agent-settings-permission is-${settings.permissionProfile}`} data-testid="agent-permission-badge">{permissionLabel(settings.permissionProfile)}</span>}
      <ChevronUp size={14} aria-hidden="true" />
    </button>
    {open && <div className="agent-settings-popover" id="agent-settings-popover" style={{ maxHeight }} role="dialog" aria-modal="false" aria-label={uiText('Agent 运行设置', 'Agent run settings')} ref={popover} tabIndex={-1}
      onKeyDown={event => { if (event.key === 'Escape' && !event.defaultPrevented) { event.preventDefault(); event.stopPropagation(); close(true); } }}>
      <header><h3>{uiText('运行设置', 'Run settings')}</h3>{supported && <button type="button" className="btn-icon" disabled={catalog?.state === 'loading' || !provider?.installed} aria-label={uiText('重新读取模型', 'Refresh models')} onClick={() => provider && void agent.invoke({ operation: 'models', provider: provider.id })}><RefreshCw size={14} aria-hidden="true" /></button>}</header>
      <div className="agent-settings-scroll">
      {supported ? <>
        <div className="agent-settings-fields">
          <label className="agent-settings-model">{uiText('模型', 'Model')}<select data-testid="agent-model" value={settings.model} disabled={disabled || !provider?.installed}
            onChange={event => onChange({ ...settings, model: event.target.value, effort: '' })}>
            <option value="">{defaultLabel}</option>{settings.model && !model && <option value={settings.model}>{settings.model} · {uiText('待核对', 'Needs checking')}</option>}
            {catalog?.models.map(item => <option key={item.id} value={item.id}>{item.label}</option>)}</select></label>
          <label>{uiText('思考强度', 'Reasoning effort')}<select data-testid="agent-effort" value={settings.effort} disabled={disabled || !model}
            onChange={event => onChange({ ...settings, effort: event.target.value })}><option value="">{settings.model ? uiText('模型默认', 'Model default') + (model?.defaultEffort ? ` · ${effortLabel(model.defaultEffort)}` : '') : uiText('沿用会话默认', 'Use conversation default')}</option>
            {settings.effort && !model?.efforts.includes(settings.effort) && <option value={settings.effort}>{settings.effort} · {uiText('待核对', 'Needs checking')}</option>}
            {model?.efforts.map(effort => <option key={effort} value={effort}>{effortLabel(effort)}</option>)}</select></label>
          <label>{uiText('权限', 'Permissions')}<select data-testid="agent-permissions" value={settings.permissionProfile} disabled={disabled || catalog?.state !== 'ready'}
            onChange={event => onChange({ ...settings, permissionProfile: event.target.value as AgentPermissionProfile })}>
            {!permissionProfiles.includes(settings.permissionProfile) && <option value={settings.permissionProfile} disabled>{permissionLabel(settings.permissionProfile)} · {uiText('不可用', 'Unavailable')}</option>}
            {permissionProfiles.map(profile => <option key={profile} value={profile}>{permissionLabel(profile)}</option>)}</select></label>
        </div>
        {catalog?.state === 'loading' && <p role="status">{uiText('正在读取 CLI 支持的模型与设置…', 'Reading models and settings supported by the CLI…')}</p>}
        {catalog?.error && <p role="alert">{catalog.error}</p>}
        {!settings.model && <p>{uiText('先选择模型，才能选择它支持的思考强度。留空时沿用会话设置；新会话使用 CLI 默认值。', 'Choose a model to select its supported reasoning effort. Defaults retain the current conversation settings; new conversations use CLI defaults.')}</p>}
        <p className={settings.permissionProfile === 'full-access' ? 'agent-settings-consequence' : undefined}>
          {settings.permissionProfile === 'full-access' ? uiText('允许读写工作区之外的文件，仍按需请求批准。', 'Allows reading and writing outside the workspace; approvals are still requested when needed.')
            : settings.permissionProfile === 'read-only' ? uiText('使用 Codex 只读沙箱，拒绝扩大文件访问权限。外部工具仍使用自身权限。', 'Uses the Codex read-only sandbox and declines file-access escalation. External tools retain their own permissions.')
              : uiText('可修改工作区文件，需要额外权限时询问。', 'Can modify workspace files and asks when additional permissions are needed.')}</p>
        <p>{disabled ? uiText('当前任务进行中，结束后可更改设置。', 'Wait for the current task to finish before changing settings.') : uiText('发送后记住本会话的选择，不修改 CLI 全局配置。', 'Selections are remembered for this conversation after sending; CLI global configuration stays unchanged.')}</p>
      </> : <p>{uiText('此工具的模型、思考强度和权限由 CLI 自身配置管理。', 'This tool manages its model, reasoning effort and permissions in its own CLI configuration.')}</p>}
      <details className="agent-settings-connection"><summary>{uiText('连接信息', 'Connection details')}</summary><p>{provider?.version ?? uiText('尚未检测版本', 'Version not yet detected')}</p><p>{provider?.executable}</p><p>{agent.snapshot?.project?.canonicalRoot}</p>
        {provider?.loginAvailable && <button type="button" className="btn-secondary" disabled={disabled} onClick={() => void agent.invoke({ operation: 'login', provider: provider.id })}>{uiText('打开 CLI 登录', 'Open CLI sign-in')}</button>}</details></div>
      <footer>{supported && <button type="button" disabled={disabled} className="btn-secondary" onClick={() => onChange({ ...DEFAULT_AGENT_SETTINGS })}>{uiText('使用默认', 'Use defaults')}</button>}<button type="button" className="btn-primary" onClick={() => close(true)}>{uiText('完成', 'Done')}</button></footer>
    </div>}
  </div>;
}
