import { forwardRef, useEffect, useId, useImperativeHandle, useLayoutEffect, useRef, useState, type CSSProperties, type SelectHTMLAttributes } from 'react';
import { createPortal } from 'react-dom';
import { Check } from 'lucide-react';

type Choice = { label: string; disabled: boolean; selected: boolean; group: string };

/** Keeps native form/label/change semantics; the popup is shared across desktop platforms. */
export const Select = forwardRef<HTMLSelectElement, SelectHTMLAttributes<HTMLSelectElement>>(function Select(
  { children, className = '', style, onMouseDown, onClick, onKeyDown, onBlur, ...props }, forwardedRef,
) {
  const control = useRef<HTMLSelectElement>(null);
  const popup = useRef<HTMLDivElement>(null);
  const id = useId();
  const [open, setOpen] = useState(false);
  const [choices, setChoices] = useState<Choice[]>([]);
  const [active, setActive] = useState(-1);
  const [position, setPosition] = useState<CSSProperties>({});
  const search = useRef({ text: '', time: 0 });
  useImperativeHandle(forwardedRef, () => control.current!, []);
  const nativeList = props.multiple || (props.size ?? 0) > 1;

  useLayoutEffect(() => {
    if (!open) return;
    const select = control.current!;
    if (select.matches(':disabled')) { setOpen(false); return; }
    const options = Array.from(select.options, option => ({
      label: option.label, selected: option.selected,
      disabled: option.disabled || (option.parentElement instanceof HTMLOptGroupElement && option.parentElement.disabled),
      group: option.parentElement instanceof HTMLOptGroupElement ? option.parentElement.label : '',
    }));
    setChoices(options);
    setActive(index => options[index] && !options[index].disabled ? index
      : options.findIndex(option => option.selected && !option.disabled));
    const rect = select.getBoundingClientRect();
    const below = window.innerHeight - rect.bottom - 14;
    const above = rect.top - 14;
    const upwards = below < Math.min(options.length * 36 + 12, 280) && above > below;
    const width = Math.min(Math.max(rect.width, 192), window.innerWidth - 24);
    setPosition({ width, left: Math.max(12, Math.min(rect.left, window.innerWidth - width - 12)),
      maxHeight: Math.max(36, Math.min(320, upwards ? above : below)),
      ...(upwards ? { bottom: window.innerHeight - rect.top + 6 } : { top: rect.bottom + 6 }),
    });
  }, [open, children, props.value, props.disabled]);

  useLayoutEffect(() => {
    if (open && active >= 0) popup.current?.querySelector(`[data-index="${active}"]`)?.scrollIntoView({ block: 'nearest' });
  }, [open, active, choices]);

  useEffect(() => {
    if (!open) return;
    const outside = (event: Event) => {
      if (!control.current?.contains(event.target as Node) && !popup.current?.contains(event.target as Node)) setOpen(false);
    };
    const resize = () => setOpen(false);
    document.addEventListener('pointerdown', outside, true);
    document.addEventListener('scroll', outside, true);
    window.addEventListener('resize', resize);
    return () => {
      document.removeEventListener('pointerdown', outside, true);
      document.removeEventListener('scroll', outside, true);
      window.removeEventListener('resize', resize);
    };
  }, [open]);

  function show() {
    if (control.current!.matches(':disabled')) return;
    search.current = { text: '', time: 0 };
    setActive(control.current!.selectedIndex);
    setOpen(true);
  }

  function choose(index: number) {
    const select = control.current!;
    if (select.matches(':disabled') || !choices[index] || choices[index].disabled) return;
    setOpen(false);
    if (select.selectedIndex !== index) {
      select.selectedIndex = index;
      select.dispatchEvent(new Event('change', { bubbles: true }));
    }
  }

  return <>
    <select {...props} ref={control} className={`${nativeList ? '' : 'cb-select'} ${className}`.trim()}
      style={nativeList ? style : { ...style, paddingInlineEnd: '32px' }}
      aria-expanded={nativeList ? undefined : open} aria-controls={open ? id : undefined}
      aria-activedescendant={open && active >= 0 ? `${id}-${active}` : undefined}
      onMouseDown={event => {
        onMouseDown?.(event);
        if (!nativeList && event.button === 0) event.preventDefault();
      }}
      onClick={event => {
        onClick?.(event);
        if (nativeList || event.defaultPrevented) return;
        control.current!.focus();
        if (open) setOpen(false); else show();
      }}
      onBlur={event => { onBlur?.(event); setOpen(false); }}
      onKeyDown={event => {
        onKeyDown?.(event);
        if (nativeList || event.defaultPrevented || event.nativeEvent.isComposing) return;
        if (event.key === 'Tab') { setOpen(false); return; }
        if (event.key === 'Escape' && open) { event.preventDefault(); event.stopPropagation(); setOpen(false); return; }
        if (['Enter', ' ', 'ArrowDown', 'ArrowUp', 'F4'].includes(event.key)) {
          event.preventDefault();
          event.stopPropagation();
          if (!open) { show(); return; }
          if (event.key === 'Enter' || event.key === ' ') { choose(active); return; }
          if (event.key === 'F4' || (event.altKey && event.key === 'ArrowUp')) { setOpen(false); return; }
        } else if (!open || event.ctrlKey || event.metaKey || event.altKey) return;
        const enabled = choices.map((choice, index) => choice.disabled ? -1 : index).filter(index => index >= 0);
        if (!enabled.length) return;
        const current = enabled.indexOf(active);
        if (['ArrowDown', 'ArrowUp', 'Home', 'End', 'PageDown', 'PageUp'].includes(event.key)) {
          event.preventDefault();
          const next = event.key === 'Home' ? 0 : event.key === 'End' ? enabled.length - 1
            : Math.max(0, Math.min(enabled.length - 1, current + (event.key === 'PageDown' ? 7 : event.key === 'PageUp' ? -7 : event.key === 'ArrowDown' ? 1 : -1)));
          setActive(enabled[next]);
        } else if (event.key.length === 1) {
          event.preventDefault();
          const now = Date.now();
          const text = (now - search.current.time < 700 ? search.current.text : '') + event.key.toLocaleLowerCase();
          search.current = { text, time: now };
          const prefix = [...text].every(letter => letter === text[0]) ? text[0] : text;
          const ordered = [...enabled.slice(current + 1), ...enabled.slice(0, current + 1)];
          const match = ordered.find(index => choices[index].label.toLocaleLowerCase().startsWith(prefix));
          if (match !== undefined) setActive(match);
        }
      }}>{children}</select>
    {open && createPortal(<div ref={popup} id={id} role="listbox" className="cb-select-menu" style={position}
      aria-label={props['aria-label']} aria-labelledby={props['aria-labelledby'] ?? (props['aria-label'] ? undefined : props.id)}
      onMouseDown={event => event.preventDefault()}>
      {choices.map((choice, index) => <div key={index}>
        {choice.group && choice.group !== choices[index - 1]?.group && <div className="cb-select-group">{choice.group}</div>}
        <div id={`${id}-${index}`} role="option" aria-selected={choice.selected} aria-disabled={choice.disabled || undefined}
          data-index={index} data-active={active === index || undefined} className="cb-select-option"
          onPointerMove={() => { if (!choice.disabled) setActive(index); }} onClick={() => choose(index)}>
          <span>{choice.label || '\u00a0'}</span><Check size={15} aria-hidden="true" style={{ visibility: choice.selected ? 'visible' : 'hidden' }} />
        </div>
      </div>)}
    </div>, document.body)}
  </>;
});
