import { useState } from 'react';
import { agentText, type AgentApproval, type AgentApprovalAnswer } from '../bridge/agentBridge';
import { uiText } from '../i18n';

function PermissionDetail({ detail }: { detail: string }) {
  let command: string | undefined, reason: string | undefined, cwd: string | undefined;
  try {
    const value: unknown = JSON.parse(detail);
    if (value && typeof value === 'object' && !Array.isArray(value)) {
      const row = value as Record<string, unknown>;
      if (typeof row.command === 'string' && row.command.trim()) {
        command = row.command;
        if (typeof row.reason === 'string') reason = row.reason;
        if (typeof row.cwd === 'string') cwd = row.cwd;
      }
    }
  } catch { /* Non-JSON CLI details retain their original text. */ }
  if (!command) return <p className="creation-approval-detail" tabIndex={0}>{detail}</p>;
  return <>
    {reason && <p className="creation-approval-reason">{reason}</p>}
    {cwd && <p className="creation-approval-location">{uiText('位置', 'Location')}: {cwd}</p>}
    <pre className="creation-approval-detail" tabIndex={0} aria-label={uiText('待运行命令', 'Command to run')}>{command}</pre>
    <details className="creation-approval-metadata"><summary>{uiText('查看完整操作', 'View full operation')}</summary>
      <pre className="creation-approval-detail" tabIndex={0}>{detail}</pre></details>
  </>;
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
  const titleId = `${variant === 'simple' ? '' : 'professional-'}approval-${approval.id}`;
  return <section className="creation-approval" aria-labelledby={titleId} data-testid={`${variant}-agent-approval`}>
    <h3 id={titleId}>{agentText(approval.title, approval.titleText)}</h3>
    {question ? <p>{approval.detail}</p> : <PermissionDetail detail={approval.detail} />}
    {!question ? <div>{approval.choices.map(choice => <button type="button" className="btn-secondary" key={choice.id} disabled={disabled}
      onClick={() => onRespond({ choice: choice.id })}>{agentText(choice.label, choice.labelText)}</button>)}</div>
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
            <span>{agentText(choice.label, choice.labelText)}{choice.description && <small>{choice.description}</small>}</span>
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
