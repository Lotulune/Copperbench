import { useState } from 'react';
import type { AgentApproval, AgentApprovalAnswer, AgentProvider } from '../bridge/agentBridge';
import { uiText } from '../i18n';
import { AGENT_PROVIDER_NAMES } from './AgentProviderIcon';

/** CLIs without interactive approval report denials as errors; both workbenches point to the CLI's own configuration. */
export function permissionDeniedHint(provider: AgentProvider | undefined, error: string | null | undefined): string | null {
  if (!provider || (provider.capabilities?.interactivePermission ?? provider.id !== 'antigravity')) return null;
  if (!/permission|denied|approval|权限|拒绝/i.test(error ?? '')) return null;
  const name = AGENT_PROVIDER_NAMES[provider.id];
  return uiText(`请在 ${name} 的配置中允许所需操作，然后重新发送请求。`, `Allow the required operation in ${name} settings, then send your request again.`);
}

export function AgentApprovalPrompt({ approval, disabled, onRespond, variant = 'simple' }: {
  approval: AgentApproval; disabled: boolean; onRespond: (answer: AgentApprovalAnswer) => void;
  variant?: 'simple' | 'professional';
}) {
  const [selected, setSelected] = useState<string[]>([]);
  const [custom, setCustom] = useState('');
  const question = approval.kind === 'question' || (!approval.kind && approval.choices.length === 0);
  const multiSelect = approval.multiSelect === true;
  const allowCustom = approval.allowCustom ?? approval.choices.length === 0;
  const answer = custom.trim();
  const canSubmit = selected.length > 0 || (allowCustom && answer.length > 0);
  const titleId = `${variant}-approval-${approval.id}`;
  return <section className="creation-approval" aria-labelledby={titleId} data-testid={`${variant}-agent-approval`}>
    <h3 id={titleId}>{approval.title}</h3><p>{approval.detail}</p>
    {!question ? <div>{approval.choices.map(choice => <button type="button" className="btn-secondary" key={choice.id} disabled={disabled}
      onClick={() => onRespond({ choice: choice.id })}>{choice.label}</button>)}</div>
      : <form className="creation-approval-answer" onSubmit={event => {
        event.preventDefault();
        if (canSubmit && !disabled) onRespond({ choices: selected, ...(allowCustom && answer ? { answer } : {}) });
      }}>
        {approval.choices.length > 0 && <fieldset className="creation-question-options" disabled={disabled}>
          <legend className="sr-only">{multiSelect ? uiText('可选择多项', 'Select one or more options') : uiText('选择一项', 'Select one option')}</legend>
          {approval.choices.map(choice => <label key={choice.id} className="creation-question-option">
            <input type={multiSelect ? 'checkbox' : 'radio'} name={`${variant}-question-${approval.id}`} value={choice.id} checked={selected.includes(choice.id)}
              onChange={() => {
                setSelected(current => multiSelect ? current.includes(choice.id) ? current.filter(id => id !== choice.id) : [...current, choice.id] : [choice.id]);
                if (!multiSelect) setCustom('');
              }} />
            <span>{choice.label}{choice.description && <small>{choice.description}</small>}</span>
          </label>)}
        </fieldset>}
        {allowCustom && <label className="creation-question-custom" htmlFor={`${variant}-answer-${approval.id}`}>
          <span>{approval.choices.length ? uiText('其他回答', 'Custom answer') : uiText('你的回答', 'Your answer')}</span>
          <input id={`${variant}-answer-${approval.id}`} data-testid={`${variant}-approval-answer`} maxLength={4000} autoComplete="off" value={custom} disabled={disabled}
            onChange={event => { setCustom(event.target.value); if (!multiSelect && event.target.value) setSelected([]); }}
            onKeyDown={event => { if (event.key === 'Enter' && (event.nativeEvent.isComposing || event.keyCode === 229)) event.preventDefault(); }} />
        </label>}
        <button type="submit" className="btn-secondary" disabled={!canSubmit || disabled}>{uiText('提交回答', 'Send answer')}</button>
      </form>}
  </section>;
}
