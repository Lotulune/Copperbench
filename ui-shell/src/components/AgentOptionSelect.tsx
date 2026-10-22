import { useEffect, useId, useLayoutEffect, useRef, useState, type KeyboardEvent, type ReactNode } from 'react';
import { createPortal } from 'react-dom';
import { Check, ChevronDown } from 'lucide-react';
import './agentModels.css';

/** Compact turn settings share the model picker's presentation and open above the composer. */
export function AgentOptionSelect<T extends string>({ value, options, icon, label, describedBy, title, testId, disabled, active, onChange }: {
  value: T; options: readonly { value: T; label: string }[]; icon: ReactNode; label: string; describedBy?: string; title?: string;
  testId: string; disabled: boolean; active: boolean; onChange: (value: T) => void;
}) {
  const [open, setOpen] = useState(false);
  const [highlight, setHighlight] = useState(0);
  const [position, setPosition] = useState({ left: 0, bottom: 0, width: 240, maxHeight: 350 });
  const trigger = useRef<HTMLButtonElement>(null), menu = useRef<HTMLDivElement>(null);
  const id = useId();
  const optionId = (index: number) => `${id}-option-${index}`;
  const chosen = options.find(option => option.value === value);
  const expand = () => {
    if (disabled || !active) return;
    setHighlight(Math.max(0, options.findIndex(option => option.value === value)));
    setOpen(true);
  };
  const choose = (next: T) => {
    setOpen(false);
    trigger.current?.focus({ preventScroll: true });
    if (next !== value) onChange(next);
  };

  useEffect(() => { if (disabled || !active) setOpen(false); }, [disabled, active]);
  useLayoutEffect(() => {
    if (!open) return;
    const place = () => {
      const bounds = trigger.current?.getBoundingClientRect();
      if (!bounds) return;
      const width = Math.min(240, window.innerWidth - 24);
      setPosition({ left: Math.max(12, Math.min(bounds.left, window.innerWidth - width - 12)),
        bottom: window.innerHeight - bounds.top + 6, width, maxHeight: Math.max(0, Math.min(350, bounds.top - 18)) });
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
  useEffect(() => {
    if (open) document.getElementById(`${id}-option-${highlight}`)?.scrollIntoView({ block: 'nearest' });
  }, [open, highlight, id]);

  const keyDown = (event: KeyboardEvent<HTMLButtonElement>) => {
    if (event.key === 'Escape' && open) { event.preventDefault(); event.stopPropagation(); setOpen(false); }
    else if (event.key === 'Tab') setOpen(false);
    else if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault();
      if (!open) expand();
      else setHighlight(index => (index + (event.key === 'ArrowDown' ? 1 : -1) + options.length) % options.length);
    } else if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      if (open) choose(options[highlight].value); else expand();
    } else if (open && (event.key === 'Home' || event.key === 'End')) {
      event.preventDefault(); setHighlight(event.key === 'Home' ? 0 : options.length - 1);
    } else if (open && /^[a-z]$/i.test(event.key)) {
      const next = options.map((_, index) => (highlight + index + 1) % options.length)
        .find(index => options[index].label.toLowerCase().startsWith(event.key.toLowerCase()));
      if (next !== undefined) { event.preventDefault(); setHighlight(next); }
    }
  };

  return <>
    <button type="button" ref={trigger} className="creation-select-trigger" data-testid={testId} data-option-value={value}
      role="combobox" aria-haspopup="listbox" aria-expanded={open} aria-controls={open ? id : undefined}
      aria-activedescendant={open ? optionId(highlight) : undefined} aria-label={`${label}: ${chosen?.label ?? value}`}
      aria-describedby={describedBy} title={title} disabled={disabled} onKeyDown={keyDown}
      onClick={() => open ? setOpen(false) : expand()} onBlur={() => setOpen(false)}>
      {icon}<span>{chosen?.label ?? value}</span><ChevronDown size={13} aria-hidden="true" />
    </button>
    {open && createPortal(<div ref={menu} id={id} role="listbox" aria-label={label}
      className="creation-select-menu creation-select-options" style={position}>
      {options.map((option, index) => <div key={option.value} id={optionId(index)} role="option"
        aria-selected={option.value === value} data-highlighted={index === highlight}
        onMouseEnter={() => setHighlight(index)} onMouseDown={event => event.preventDefault()} onClick={() => choose(option.value)}>
        <span>{option.label}</span>{option.value === value && <Check size={14} aria-hidden="true" />}
      </div>)}
    </div>, document.body)}
  </>;
}
