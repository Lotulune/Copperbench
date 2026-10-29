import { useEffect, useId, useLayoutEffect, useRef, useState, type KeyboardEvent } from 'react';
import { createPortal } from 'react-dom';
import { Check, ChevronDown } from 'lucide-react';
import { AGENT_PROVIDERS, type AgentProvider, type AgentProviderId } from '../bridge/agentBridge';
import { uiText } from '../i18n';
import { AgentProviderIcon, AGENT_PROVIDER_NAMES } from './AgentProviderIcon';

export function AgentProviderSelect({ value, providers, disabled, active, detecting, onChange, variant = 'simple' }: {
  value: AgentProviderId;
  providers: AgentProvider[];
  disabled: boolean;
  active: boolean;
  detecting?: boolean;
  onChange: (provider: AgentProviderId) => void;
  variant?: 'simple' | 'professional';
}) {
  const [open, setOpen] = useState(false);
  const [highlight, setHighlight] = useState(0);
  const [position, setPosition] = useState({ top: 0, left: 0, width: 272, maxHeight: 240 });
  const trigger = useRef<HTMLButtonElement>(null);
  const menu = useRef<HTMLDivElement>(null);
  const menuId = useId();
  const optionId = (index: number) => `${menuId}-${index}`;
  const expand = () => { if (!disabled) { setHighlight(AGENT_PROVIDERS.indexOf(value)); setOpen(true); } };
  const choose = (provider: AgentProviderId) => {
    setOpen(false); trigger.current?.focus();
    if (provider !== value) onChange(provider);
  };

  useEffect(() => { if (disabled || !active) setOpen(false); }, [active, disabled]);
  useLayoutEffect(() => {
    if (!open) return;
    const place = () => {
      const bounds = trigger.current?.getBoundingClientRect();
      if (!bounds) return;
      const width = Math.min(272, window.innerWidth - 16);
      const above = bounds.top - 8, below = window.innerHeight - bounds.bottom - 8;
      const height = Math.min(menu.current?.scrollHeight || 208, Math.max(above, below));
      setPosition({ left: Math.max(8, Math.min(bounds.left, window.innerWidth - width - 8)), width,
        top: above >= height ? bounds.top - height - 5 : bounds.bottom + 5,
        maxHeight: Math.max(80, Math.max(above, below) - 5) });
    };
    const outside = (event: PointerEvent) => {
      if (event.target instanceof Node && !trigger.current?.contains(event.target) && !menu.current?.contains(event.target)) setOpen(false);
    };
    place();
    window.addEventListener('resize', place); document.addEventListener('scroll', place, true);
    document.addEventListener('pointerdown', outside);
    return () => {
      window.removeEventListener('resize', place); document.removeEventListener('scroll', place, true);
      document.removeEventListener('pointerdown', outside);
    };
  }, [open]);

  const keyDown = (event: KeyboardEvent<HTMLButtonElement>) => {
    if (event.key === 'Escape' && open) { event.preventDefault(); event.stopPropagation(); setOpen(false); return; }
    if (event.key === 'Tab') { setOpen(false); return; }
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault();
      if (!open) expand();
      else setHighlight(index => (index + (event.key === 'ArrowDown' ? 1 : -1) + AGENT_PROVIDERS.length) % AGENT_PROVIDERS.length);
    } else if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault(); if (open) choose(AGENT_PROVIDERS[highlight]); else expand();
    } else if (open && (event.key === 'Home' || event.key === 'End')) {
      event.preventDefault(); setHighlight(event.key === 'Home' ? 0 : AGENT_PROVIDERS.length - 1);
    } else if (open && /^[a-z]$/i.test(event.key)) {
      const start = highlight + 1;
      const next = AGENT_PROVIDERS.map((_, index) => (start + index) % AGENT_PROVIDERS.length)
        .find(index => AGENT_PROVIDER_NAMES[AGENT_PROVIDERS[index]].toLowerCase().startsWith(event.key.toLowerCase()));
      if (next !== undefined) { event.preventDefault(); setHighlight(next); }
    }
  };

  return <>
    <button type="button" ref={trigger} id={variant === 'simple' ? 'creation-provider' : 'professional-provider'} className="creation-provider-trigger" data-testid={`${variant}-provider`}
      data-provider-value={value} data-variant={variant} role="combobox" aria-haspopup="listbox" aria-expanded={open} aria-controls={open ? menuId : undefined}
      aria-activedescendant={open ? optionId(highlight) : undefined} aria-label={`${uiText('创作助手', 'Assistant')}: ${AGENT_PROVIDER_NAMES[value]}`}
      disabled={disabled} onClick={() => open ? setOpen(false) : expand()} onKeyDown={keyDown}
      onBlur={event => { if (!menu.current?.contains(event.relatedTarget as Node | null)) setOpen(false); }}>
      <AgentProviderIcon provider={value} /><span>{AGENT_PROVIDER_NAMES[value]}</span><ChevronDown size={13} aria-hidden="true" />
    </button>
    {open && createPortal(<div ref={menu} id={menuId} className="creation-provider-listbox" role="listbox"
      aria-label={uiText('选择创作助手', 'Choose a creation assistant')} style={position}>
      {AGENT_PROVIDERS.map((provider, index) => <div key={provider} id={optionId(index)} role="option"
        data-testid={`provider-option-${provider}`} aria-selected={provider === value} data-highlighted={index === highlight}
        className="creation-provider-option" onMouseEnter={() => setHighlight(index)}
        onMouseDown={event => event.preventDefault()} onClick={() => choose(provider)}>
        <AgentProviderIcon provider={provider} size={20} /><span className="creation-provider-option-name">{AGENT_PROVIDER_NAMES[provider]}</span>
        {!detecting && providers.find(item => item.id === provider)?.installed === false && <small>{uiText('未安装', 'Not installed')}</small>}
        {provider === value && <Check size={15} aria-hidden="true" />}
      </div>)}
    </div>, document.body)}
  </>;
}
