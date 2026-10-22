import { useEffect, useRef, useState, type KeyboardEvent } from 'react';
import { useWorkbenchMode, type WorkbenchMode } from '../context/WorkbenchModeContext';
import { uiText } from '../i18n';
import './workbenchModeSwitch.css';

const modes: WorkbenchMode[] = ['simple', 'professional'];

/** A stable title-bar slot with a locally expanding, equal-width segmented control. */
export function WorkbenchModeSwitch() {
  const { mode, setMode } = useWorkbenchMode();
  const [expanded, setExpanded] = useState(false);
  const control = useRef<HTMLDivElement>(null);
  const buttons = useRef<Partial<Record<WorkbenchMode, HTMLButtonElement | null>>>({});
  const closeTimer = useRef<number | undefined>(undefined);
  const hovered = useRef(false), keyboardFocus = useRef(false), suppressFocus = useRef(false);
  const clearClose = () => { window.clearTimeout(closeTimer.current); closeTimer.current = undefined; };
  const open = () => { clearClose(); setExpanded(true); };
  const scheduleClose = () => {
    clearClose();
    if (hovered.current || keyboardFocus.current) return;
    closeTimer.current = window.setTimeout(() => { closeTimer.current = undefined; setExpanded(false); }, 1000);
  };
  useEffect(() => () => window.clearTimeout(closeTimer.current), []);
  useEffect(() => {
    if (!expanded) return;
    const outside = (event: PointerEvent) => {
      if (event.target instanceof Node && !control.current?.contains(event.target)) {
        hovered.current = false; keyboardFocus.current = false; scheduleClose();
      }
    };
    document.addEventListener('pointerdown', outside);
    return () => document.removeEventListener('pointerdown', outside);
  }, [expanded]);

  const keyDown = (event: KeyboardEvent<HTMLDivElement>) => {
    if (event.key === 'Escape') {
      event.preventDefault(); event.stopPropagation(); clearClose(); keyboardFocus.current = false;
      suppressFocus.current = true; buttons.current[mode]?.focus({ preventScroll: true }); suppressFocus.current = false;
      setExpanded(false);
    } else if (['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) {
      event.preventDefault(); keyboardFocus.current = true; open();
      const focused = event.target === buttons.current.professional ? 1 : 0;
      const index = event.key === 'Home' ? 0 : event.key === 'End' ? 1 : (focused + 1) % modes.length;
      // The other option becomes focusable after React exposes the expanded control.
      requestAnimationFrame(() => buttons.current[modes[index]]?.focus({ preventScroll: true }));
    }
  };
  return <div className="workbench-mode-slot">
    <div ref={control} className="workbench-mode-switch" role="group" aria-label={uiText('工作模式', 'Workbench mode')}
      data-testid="workbench-mode-control" data-expanded={expanded} data-mode={mode}
      data-window-chrome-kind="client" data-window-chrome-id="workbench-mode"
      onPointerEnter={event => { if (event.pointerType !== 'touch') { hovered.current = true; open(); } }}
      onPointerLeave={() => { hovered.current = false; scheduleClose(); }}
      onPointerDownCapture={event => { keyboardFocus.current = false; if (event.pointerType !== 'touch') open(); }}
      onFocusCapture={event => {
        if (!suppressFocus.current && event.target.matches(':focus-visible')) { keyboardFocus.current = true; open(); }
      }}
      onBlur={event => {
        if (!event.currentTarget.contains(event.relatedTarget as Node | null)) { keyboardFocus.current = false; scheduleClose(); }
      }} onKeyDown={keyDown}>
      <div className="workbench-mode-track">
        <span className="workbench-mode-slider" aria-hidden="true" />
        {modes.map(value => <button key={value} type="button" ref={element => { buttons.current[value] = element; }}
          data-testid={`mode-${value}`} aria-pressed={mode === value} aria-hidden={!expanded && mode !== value}
          aria-expanded={mode === value ? expanded : undefined} tabIndex={expanded || mode === value ? 0 : -1}
          onClick={() => {
            open(); setMode(value);
            if (value !== mode && !hovered.current && !keyboardFocus.current) scheduleClose();
          }}>{value === 'simple' ? uiText('简洁创作', 'Simple creation') : uiText('专业工作台', 'Professional')}</button>)}
      </div>
    </div>
  </div>;
}
